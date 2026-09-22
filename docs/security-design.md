# Security Design

Threat-model-driven: what an attacker can try, and exactly what stops them. Every control listed here
has a corresponding implementation and test (see the traceability column).

---

## 1. Assets & trust boundaries

| Asset | Where it lives | Impact if compromised |
| --- | --- | --- |
| Password | Argon2id hash in `users.password_hash` | Account takeover across services (credential stuffing) |
| Session token | `HttpOnly` cookie; SHA-256 hash in `user_sessions` | Full account impersonation |
| Password-reset token | Email link (raw), SHA-256 hash in DB | Account takeover |
| Private messages | `messages.content` (plaintext at rest) | Privacy breach — accepted limitation, documented |
| Email address | `users.email` | Identification of a pseudonymous user — must never leak to other users |
| Privacy settings | `user_privacy_settings` | Unwanted exposure of activity |
| Block relationships | `user_blocks` | Harassment if reversed |
| Moderation data | `reports` | Retaliation against reporters |

Trust boundaries: **browser → nginx → backend** (all untrusted input crosses here), **backend →
PostgreSQL/Redis** (trusted network in compose; TLS required in production), **backend → SMTP**
(authentication required).

---

## 2. STRIDE-style control matrix

| Threat | Attack | Control | Code / test |
| --- | --- | --- | --- |
| **Spoofing** | Credential stuffing | Argon2id + per-account progressive lockout + per-IP and per-account rate limits + identical error wording/timing | `AuthService#login`, `LoginAttemptGuard`, `RedisRateLimiter` |
| Spoofing | Session hijack via XSS | Session token is `HttpOnly` (not readable by JS), `Secure`, `SameSite=Lax`, rotated on privilege change; strict CSP | `SecurityConfig`, `SessionCookieFactory`, `CspHeaderFilter` |
| Spoofing | Forged `userId` in a request/frame | Identity always from the authenticated principal; client-supplied ids are validated against participation | `ConversationAccessService`, `ChatWebSocketController` |
| **Tampering** | Modifying someone else's message | `sender_id = currentUser` checked in the same transaction; edit/delete require ownership | `MessageService#edit/delete` |
| Tampering | CSRF via a third-party site | Double-submit cookie (`XSRF-TOKEN` + `X-XSRF-TOKEN`) on all unsafe methods; `SameSite=Lax`; Origin/Referer checks | `SecurityConfig#csrf`, `CsrfCookieFilter` |
| Tampering | Cross-site WebSocket hijacking | Handshake requires the session cookie (`SameSite=Lax` blocks cross-site sending) **and** an allow-listed `Origin` | `WebSocketAuthHandshakeInterceptor` |
| Tampering | Mass assignment (setting `role`, `emailVerified`) | Explicit DTOs; unknown JSON fields rejected; no entity binding | `JacksonConfig` (`FAIL_ON_UNKNOWN_PROPERTIES`) |
| **Repudiation** | User denies sending/harassing | Messages carry server timestamps and immutable ids; moderation history is append-only | `messages`, `report_status_history` |
| **Information disclosure** | Email/ID leak through profile or search | `PublicProfileResponse` has no email field; mappers are explicit; tests assert the projection | `ProfileMapperTest`, `ProfileSearchIT` |
| Information disclosure | Enumerating accounts via login/reset | Identical responses and comparable timing for unknown accounts; reset always returns 202 | `AuthService#login`, `PasswordResetService#request` |
| Information disclosure | Reading a conversation you are not in | Every read path requires a participant row (`404` otherwise, to avoid existence disclosure) | `ConversationAccessService`, `MessageAuthorizationIT` |
| Information disclosure | Stack traces / SQL errors | Global handler with a fixed error contract; details logged with `X-Request-Id` | `GlobalExceptionHandlerIT` |
| Information disclosure | Redis cache poisoning of sessions | Cache stores only `userId`+`sessionId` and is written only after a DB-backed validation | `SessionService` |
| **Denial of service** | Message flood / login brute force / report spam | Redis token buckets per IP, per account, per user; request body caps; STOMP frame caps; slow-consumer disconnect | `RateLimitInterceptor`, `WebSocketConfig` (`configureWebSocketTransport`) |
| DoS | Oversized payload | 64 KiB HTTP body limit, 16 KiB STOMP frame limit, 4 000-char message cap, 280-char bio | `application.yml`, validation |
| DoS | Reconnect storm after an outage | Exponential backoff with jitter; server-side handshake rate limit per IP | `stompClient.js`, `HandshakeRateLimiter` |
| **Elevation of privilege** | Calling admin endpoints | `/admin/**` requires `ROLE_ADMIN`; roles are never settable through the API | `SecurityConfig#authorizeHttpRequests` |
| Elevation | SUBSCRIBE to another user's topic | Subscription interceptor validates participation before registering | `WebSocketSubscriptionInterceptor` |

---

## 3. Authentication & session security

### Password storage

```
Argon2id — Spring Security's Argon2PasswordEncoder
parameters: m = 19456 KiB (19 MiB), t = 2, p = 1, salt = 16 bytes, hash = 32 bytes
upgrades: auto-rehash on successful login when parameters or the algorithm change
never: plaintext, reversible encryption, MD5/SHA-1/SHA-256, unsalted hashes, "pepper" stored in code
```

Verified dependency note: Spring Security's `Argon2PasswordEncoder` delegates to
**BouncyCastle** (`org.bouncycastle.crypto.generators.Argon2BytesGenerator`). The provider
(`bcprov-jdk18on`) is therefore an explicit runtime dependency of the backend — a missing provider
would fail at first registration.

### Password policy

- 12–128 characters, at least 10 distinct characters, not in a bundled common-password deny list
  (top-10k), must not contain the username or email local part.
- Strength feedback in the UI; the policy is enforced server-side.

### Sessions

| Property | Value |
| --- | --- |
| Token | 32 bytes from `SecureRandom`, Base64URL (256 bits of entropy) |
| Storage | SHA-256 hash in `user_sessions.token_hash`; raw value only in the cookie |
| Cookie | `session=<token>; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000` |
| Sliding expiry | extended on use at most once per hour; idle timeout 7 days |
| Absolute expiry | 30 days from creation |
| Rotation | on login, on password change/reset; a rotated session keeps its device label |
| Revocation | logout (one), logout-all (all), password change (all others), admin suspend (all) |
| Revocation propagation | `session.revoked` event to connected sockets → the client clears in-memory state and routes to `/login` |
| Cache | `session:{sha256(token)}` → `{userId, sessionId}` TTL 5 min; DB is authoritative; revoked sessions are removed from the cache immediately |

Why not JWT: revocation ("log out all devices", admin suspension) and rotation are first-class
requirements here, and a stolen cookie cannot be replayed across services if the token is opaque.
The trade-off (a per-request cache/DB lookup) is mitigated by the Redis cache and is negligible at
this scale.

### CSRF

Double-submit cookie: `XSRF-TOKEN` (readable by JS, `SameSite=Lax`) must match the
`X-XSRF-TOKEN` header on every `POST`/`PATCH`/`DELETE`. WebSocket handshakes additionally require a
non-`null`, allow-listed `Origin`. Login/register are CSRF-protected too (they create state), while
`/actuator/health` is exempt (read-only, no state change).

### Password reset & account recovery

- Token: 32 random bytes, Base64URL, stored as SHA-256, single use, 30-minute TTL.
- The response to `forgot-password` never reveals account existence.
- Consuming a token revokes **all** sessions and invalidates every other outstanding token for the user.
- The email contains the link and an expiry notice but no user-supplied content (no HTML injection).
- Requesting a reset for an account that is soft-locked does not unlock it.

### Account deletion

`DELETE /users/me` erases email, profile, privacy/notification settings, sessions, reset tokens,
blocks, notifications and (optionally) uploads, and anonymises authored messages. The `users` row is
kept as a tombstone (`status = 'DELETED'`, email replaced with a non-routable placeholder) so that
referential integrity and moderation records survive.

---

## 4. Authorization model

| Rule | Enforcement |
| --- | --- |
| Deny by default | `authorizeHttpRequests` requires authentication for everything except `/auth/register`, `/auth/login`, `/auth/forgot-password`, `/auth/reset-password`, `/actuator/health*`, `/v3/api-docs*`, `/swagger-ui*` (dev only) |
| Conversation read/write | `ConversationAccessService.requireParticipant(userId, conversationId)` throws `NotFound` (not `Forbidden`) when absent — no existence disclosure |
| Message edit/delete | `sender_id == currentUser` and (for edit) `now - created_at < 48h` and `deleted_at IS NULL` |
| Reactions/read | Participant of the message's conversation |
| Profile visibility | Blocked-by-target ⇒ `404`; `showProfileInSearch=false` ⇒ excluded from search only |
| Privacy of presence | Checked before emitting any presence/typing frame to another user |
| Admin | `ROLE_ADMIN` for `/admin/**`; roles are set only through migrations/ops tooling |

Authorization failures are logged with `WARN` including `requestId`, `userId`, resource type and id
(never message content) and produce `403 FORBIDDEN` / `404 NOT_FOUND` per the rules above.

---

## 5. Input validation & output safety

| Input | Rules |
| --- | --- |
| `email` | RFC-5322-ish pattern, ≤ 254 chars, lowercased, no leading/trailing whitespace |
| `username` | `^[a-z0-9](?:[a-z0-9_]{1,30})[a-z0-9]$`, no `__`, not reserved, profanity-filtered |
| `displayName` | 1–64 chars, no control characters, trimmed, HTML-escaped by the client renderer (React escapes by default — `dangerouslySetInnerHTML` is never used) |
| `bio` | ≤ 280 chars, no control characters |
| `avatarUrl` | `https://` only, ≤ 512 chars, no `javascript:`/`data:` — validated server-side *and* restricted by CSP `img-src` |
| `content` | 1–4 000 chars, control characters other than `\n`/`\t` rejected |
| `emoji` | Allow-list of 32 |
| UUIDs | Parsed with `UUID.fromString`; failures → `400 INVALID_REQUEST` |
| Cursors | Base64URL, decoded into `(timestamp, uuid)`, HMAC-verified, bounded length |
| Pagination | `limit ≤ 100` (messages ≤ 100), negative/zero rejected |
| Filenames (future attachments) | basename only, `[A-Za-z0-9._-]{1,255}`, extension vs MIME cross-checked, size caps, stored under a generated key |

Output safety: JSON only (no HTML rendering of user content server-side), explicit DTO projections,
`Content-Type: application/json`, and `X-Content-Type-Options: nosniff`.

---

## 6. Transport, headers & deployment hardening

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
    img-src 'self' https: data:; connect-src 'self' wss: https:; font-src 'self';
    object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()
Cross-Origin-Opener-Policy: same-origin
```

- TLS 1.2+ (1.3 preferred) terminated at the edge; HTTP redirected to HTTPS.
- Cookies `Secure`; WebSocket only over `wss://` in production (mixed content is blocked by CSP).
- CORS: **no wildcard with credentials**. `app.cors.allowed-origins` is an explicit list; credentials
  allowed; preflight cached 1 hour.
- nginx terminates TLS, limits body size (`client_max_body_size 1m`), sets proxy timeouts, and
  upgrades `/ws` with `proxy_set_header Upgrade/Connection`.
- Secrets come from the environment (`DATABASE_PASSWORD`, `SESSION_SECRET`, `MAIL_PASSWORD`,
  `ADMIN_PASSWORD_HASH`); the `prod` profile fails fast if `SESSION_SECRET` is missing or default.
- Least privilege in the database: the application user owns the schema but has no `SUPERUSER`,
  no `CREATEDB`; migrations run under the same user (documented exception) or with a separate
  migration user in strict environments.

---

## 7. Logging, monitoring & abuse response

**Logged** (structured, with `requestId`): registration, login success/failure (+ reason category),
logout, password change/reset, session revocation, authorization failures, WebSocket handshake
rejections, rate-limit trips, moderation state changes, unexpected exceptions, DB errors.

**Never logged**: passwords, password hashes, session/reset tokens, cookie values, message content,
email addresses in URL paths, or full request bodies of auth endpoints.

**Abuse response playbook**

| Signal | Automatic action | Manual action |
| --- | --- | --- |
| > 5 failed logins / 15 min for an account | soft lock 15 min, `429` | review login logs |
| > 20 registrations / hour / IP | `429` for that IP | block IP at the edge |
| Report burst on one target (≥ 3 reporters / 24 h) | none (no auto-ban) | moderation review, possible suspension via `/admin/users/{id}/suspend` |
| Slow-consumer sockets | disconnect `1008` | capacity review |
| Suspicious message rate (> 600/day/user) | `429 RATE_LIMITED` | review, suspend |

---

## 8. End-to-end encryption roadmap (not implemented)

There is **no E2EE** in this version, and no custom cryptography is invented anywhere in the codebase.

Threats that E2EE would address: a compromised server, a database dump, and a subpoena against the
operator. Threats it would *not* address: a compromised endpoint, metadata analysis, and abuse
reporting of content (which becomes impossible without a client-side disclosure mechanism).

The future work is a *research and integration* track, using only established, reviewed protocols:

1. **Protocol**: Signal's **X3DH** for initial key agreement + **Double Ratchet** for forward secrecy
   and post-compromise security — implemented exclusively through a maintained library
   (e.g. `libsignal`), never hand-rolled.
2. **Identity keys**: per-device long-term keys, verified out-of-band via safety numbers/QR.
3. **Multi-device**: per-device sessions with sender-key distribution for groups.
4. **Metadata**: sealed sender for envelope metadata; this project would additionally need to keep
   server-side delivery receipts without leaking content (receipts stay, content disappears).
5. **Key transparency**: auditable key-directory commitments to detect server-side MITM.
6. **Migration path**: versioned message envelopes (`message_type`/`cipher_suite` fields), capability
   negotiation, and dual-write during rollout.
7. **Consequences to design for**: server-side search disappears, moderation needs client-side
   reporting of specific messages, backups must be re-thought, and account recovery conflicts with
   key loss — all of which is why this is a documented roadmap rather than a feature flag.

Until then, the product must not use the words "encrypted", "private" (in the cryptographic sense) or
"anonymous" in the UI without the qualifier documented in
[product-design.md](product-design.md#pseudonymity-not-anonymity--and-we-say-so).

---

## 9. Security testing

| Area | Test |
| --- | --- |
| Password hashing | `PasswordEncoderTest` asserts Argon2id PHC format, uniqueness of salts, verification, rehash-on-upgrade |
| Session lifecycle | `SessionServiceIT`: create/validate/expire/revoke/rotate, sliding window, cache invalidation |
| CSRF | `CsrfProtectionIT`: missing/mismatched token rejected, valid token accepted, GET exempt |
| Authorization | `MessageAuthorizationIT`, `ConversationAccessIT`: outsider cannot read/send/edit; response is `404` for hidden resources |
| Blocking | `BlockEnforcementIT`: send in both directions, conversation creation, profile fetch, presence |
| Rate limiting | `RedisRateLimiterTest` (unit, fake clock) + `AuthRateLimitIT` (real Redis) |
| WebSocket auth | `WebSocketAuthIT`: no cookie ⇒ 401 upgrade; bad origin ⇒ 403; foreign topic ⇒ ERROR |
| Input validation | `ValidationIT`: oversized content, malformed cursor, unknown JSON field, bad UUID |
| Leakage | `ErrorContractIT`: 500 responses contain no stack trace/SQL; profile JSON has no `email` key |
| Static review | `tools/verify/java_static_check.py` flags hand-rolled crypto/`Math.random`, plaintext password fields, `localStorage` usage in security paths |
