# Requirements

Requirement IDs are stable and referenced from code comments, tests and the roadmap.
**MUST** = release-blocking, **SHOULD** = strong expectation, **MAY** = optional.

Legend for status: ✅ implemented · 🟡 partial (documented gap) · ⛔ out of scope for this version.

---

## 1. Functional requirements

### 1.1 Authentication (`FR-A`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-A-1 | A visitor MUST be able to register with email, password, username and display name. | MUST | ✅ |
| FR-A-2 | Passwords MUST be ≥ 12 characters, checked against a common-password deny list, and MUST be stored with Argon2id (per-password salt). Plaintext or fast hashes (MD5/SHA-1/SHA-256) MUST NOT be used. | MUST | ✅ |
| FR-A-3 | Email addresses MUST be normalised (trim + lowercase) and unique (case-insensitive). | MUST | ✅ |
| FR-A-4 | A registered user MUST be able to log in with email **or** `@username` plus password. | MUST | ✅ |
| FR-A-5 | Login MUST create a server-side session delivered as an `HttpOnly`, `Secure`, `SameSite=Lax` cookie. Session tokens MUST NOT be stored in `localStorage`/`sessionStorage`. | MUST | ✅ |
| FR-A-6 | A user MUST be able to log out (current session) and log out of all devices. | MUST | ✅ |
| FR-A-7 | A user MUST be able to list active sessions and revoke an individual session. | SHOULD | ✅ |
| FR-A-8 | Sessions MUST expire (idle 7 days / absolute 30 days) and MUST be invalidated by password change. | MUST | ✅ |
| FR-A-9 | A user MUST be able to request a password reset by email; the response MUST NOT reveal whether the address exists. | MUST | ✅ |
| FR-A-10 | A password reset token MUST be single-use, expire in 30 minutes, be stored hashed, and revoke all sessions when consumed. | MUST | ✅ |
| FR-A-11 | A logged-in user MUST be able to change their password by proving the current one; all other sessions MUST be revoked. | MUST | ✅ |
| FR-A-12 | A user MUST be able to delete their account; this MUST hard-delete personal data and anonymise their messages. | MUST | ✅ |
| FR-A-13 | Registration, login, password-reset and password-change MUST be rate limited per IP and per account. | MUST | ✅ |
| FR-A-14 | Repeated failed logins MUST trigger progressive delays / lockout without leaking account existence. | SHOULD | ✅ |

### 1.2 User identity & pseudonymous profile (`FR-P`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-P-1 | Every user MUST have a unique username, normalised to lowercase, 3–32 chars, `[a-z0-9_]`, not starting/ending with `_`, no redundant underscores. | MUST | ✅ |
| FR-P-2 | Username lookup MUST be case-insensitive while the stored form preserves the canonical lowercase value. | MUST | ✅ |
| FR-P-3 | Usernames and display names MUST be rejected if they contain reserved words, URLs, impersonation markers (`admin`, `support`, `system`) or a minimum profanity filter. | MUST | ✅ |
| FR-P-4 | A user MUST have a display name (1–64 chars, Unicode allowed) shown in conversation UI. | MUST | ✅ |
| FR-P-5 | A user MUST be able to set a bio (≤ 280 chars, no HTML) and an avatar (`avatarUrl`), with generated-initials avatars as the default presentation. | MUST | ✅ |
| FR-P-6 | A public profile MUST expose only: username, display name, bio, avatar, online/last-seen status (subject to privacy), and join date. **Email, password hash, session data, internal IDs of other users and moderation records MUST NOT be exposed.** | MUST | ✅ |
| FR-P-7 | A user MAY hide their username from public profile responses (`usernamePublic = false`), in which case other users see only the display name. | MAY | ✅ |
| FR-P-8 | Public profiles MUST be reachable by `GET /api/v1/users/{username}` and MUST respect block and privacy rules. | MUST | ✅ |

### 1.3 Discovery (`FR-D`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-D-1 | A user MUST be able to search users by username prefix/fuzzy or display name substring, case-insensitively. | MUST | ✅ |
| FR-D-2 | Search MUST be cursor-paginated and MUST return only public profile information. | MUST | ✅ |
| FR-D-3 | Users with `showProfileInSearch = false` MUST NOT appear in results (but remain reachable if a conversation already exists). | MUST | ✅ |
| FR-D-4 | A user MUST be able to open a profile from search results and start a conversation from there. | MUST | ✅ |
| FR-D-5 | Searching MUST NOT be usable to enumerate email addresses. | MUST | ✅ |
| FR-D-6 | Search MUST be rate limited. | SHOULD | ✅ |

### 1.4 Messaging (`FR-M`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-M-1 | A participant MUST be able to send a `TEXT` message of 1–4000 characters to a conversation they participate in. | MUST | ✅ |
| FR-M-2 | Message creation MUST be transactional and the message MUST be persisted before any delivery event is emitted. | MUST | ✅ |
| FR-M-3 | Sending MUST be idempotent per `clientMessageId`; retries MUST NOT create duplicates. | MUST | ✅ |
| FR-M-4 | A user MUST be able to read conversation history using **cursor** pagination (no `OFFSET` for message history). | MUST | ✅ |
| FR-M-5 | History MUST survive server restart and browser refresh (PostgreSQL is authoritative). | MUST | ✅ |
| FR-M-6 | A sender MUST be able to edit their own message; edits MUST record `editedAt` and MUST NOT be possible for deleted messages or messages older than 48 hours. | MUST | ✅ |
| FR-M-7 | A sender MUST be able to delete their own message ("delete for everyone"); deletion MUST be a soft delete and MUST NOT break reply chains. | MUST | ✅ |
| FR-M-8 | A user MUST be able to reply to a message; replies MUST capture the parent reference server-side and render a quoted preview. | MUST | ✅ |
| FR-M-9 | A user MUST be able to add/remove emoji reactions (allow-listed emoji set) on messages in conversations they participate in. | MUST | ✅ |
| FR-M-10 | Reactions MUST be aggregated per emoji with the reacting users marked. | SHOULD | ✅ |
| FR-M-11 | Every message MUST carry server-generated `createdAt`/`updatedAt` timestamps in UTC (ISO-8601 with offset). | MUST | ✅ |
| FR-M-12 | Messages of type `IMAGE`/`FILE` MUST be rejected with a typed error until attachments ship (no silent acceptance). | MUST | ✅ |
| FR-M-13 | A participant MUST be able to mute and archive a conversation; muted conversations MUST NOT create notifications. | SHOULD | ✅ |
| FR-M-14 | Leaving/deleting a DIRECT conversation MUST remove it from that user's list without deleting the other participant's copy. | SHOULD | ✅ |

### 1.5 Delivery & read states (`FR-S`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-S-1 | The system MUST model `SENT`, `DELIVERED`, `READ` per recipient (`message_receipts`), and the **server** MUST be authoritative for those states. | MUST | ✅ |
| FR-S-2 | `SENT` MUST be set at commit; `DELIVERED` when the recipient's client acknowledges receipt; `READ` when the recipient marks the conversation read. | MUST | ✅ |
| FR-S-3 | The sender's UI MUST show a sending → sent → delivered → read progression per message. | MUST | ✅ |
| FR-S-4 | Marking a conversation read MUST be batched (single `UPDATE` per request) and MUST set `last_read_message_id` on the participant row. | SHOULD | ✅ |
| FR-S-5 | Unread counts MUST be computed server-side and returned with conversation summaries. | MUST | ✅ |
| FR-S-6 | The system MUST NOT claim stronger guarantees than it provides: no "guaranteed delivery" wording; delivery is reported only from recipient acknowledgements. | MUST | ✅ |

### 1.6 Real-time (`FR-R`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-R-1 | Clients MUST connect to `/ws` (STOMP over native WebSocket) and MUST be authenticated during the handshake. | MUST | ✅ |
| FR-R-2 | Unauthenticated or expired-session handshakes MUST be rejected with `401` before any STOMP frame is accepted. | MUST | ✅ |
| FR-R-3 | New messages MUST be delivered to online recipients without a page refresh. | MUST | ✅ |
| FR-R-4 | Clients MUST automatically reconnect with exponential backoff + jitter and MUST transition `CONNECTED → DISCONNECTED → RECONNECTING → CONNECTED | FAILED`. | MUST | ✅ |
| FR-R-5 | After reconnecting, clients MUST re-subscribe, resynchronise the open conversation and refresh presence. | MUST | ✅ |
| FR-R-6 | Typing indicators MUST be ephemeral (Redis TTL ≤ 5 s) and MUST NEVER be written to PostgreSQL. | MUST | ✅ |
| FR-R-7 | Typing events MUST only be delivered to other participants of the same conversation and MUST be suppressed when blocked. | MUST | ✅ |
| FR-R-8 | Presence (`ONLINE`/`OFFLINE`, `AWAY` derived) MUST be maintained from the WebSocket lifecycle and MUST respect privacy settings server-side. | MUST | ✅ |
| FR-R-9 | Server `SUBSCRIBE` requests to `/topic/conversation/{id}` MUST be authorised against participation. | MUST | ✅ |
| FR-R-10 | Slow consumers MUST be disconnected rather than blocking the fan-out thread. | SHOULD | ✅ |

### 1.7 Privacy (`FR-V`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-V-1 | A user MUST be able to control `showOnlineStatus`, `showLastSeen`, `showProfileInSearch`, `allowMessagesFrom` (`EVERYONE` / `CONTACTS`), and `readReceipts`. | MUST | ✅ |
| FR-V-2 | Privacy settings MUST be enforced **server-side**; the frontend MUST NOT be the enforcement point. | MUST | ✅ |
| FR-V-3 | A blocked user MUST NOT be able to start a conversation, send messages, fetch the conversation history, or view the blocker's profile details. | MUST | ✅ |
| FR-V-4 | Blocking MUST be immediate for send attempts (enforced inside the message transaction), not merely cosmetic. | MUST | ✅ |
| FR-V-5 | A user MUST be able to list and unblock blocked users. | MUST | ✅ |
| FR-V-6 | `allowMessagesFrom = CONTACTS` MUST restrict new conversation creation to users who already share a conversation. | MUST | ✅ |
| FR-V-7 | If `readReceipts` is disabled, the user's reads MUST NOT be broadcast (and their own view of others' receipts MAY be disabled symmetrically — documented in UX). | SHOULD | ✅ |
| FR-V-8 | Turning off online status MUST also hide last seen (no partial leak). | MUST | ✅ |

### 1.8 Moderation (`FR-X`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-X-1 | A user MUST be able to report another user or a specific message with a reason and optional description. | MUST | ✅ |
| FR-X-2 | Reports MUST capture reporter, target, reason, description, status and creation time; duplicate active reports of the same target by the same reporter MUST be rejected. | MUST | ✅ |
| FR-X-3 | Reports MUST support `OPEN → REVIEWING → RESOLVED | DISMISSED` states, changeable only by an administrator. | MUST | ✅ |
| FR-X-4 | Reporting MUST NOT notify the reported user (no retaliation signal). | MUST | ✅ |
| FR-X-5 | Abuse prevention: rate limits on reports, message sends, conversation creation and search; block enforcement; per-account daily message budgets. | MUST | ✅ |
| FR-X-6 | A moderation audit trail (`report_status_history`) MUST record every state change with actor and timestamp. | SHOULD | ✅ |

### 1.9 Notifications (`FR-N`)

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| FR-N-1 | The system MUST persist in-app notifications for new messages in conversations the user participates in (subject to mute/preferences). | MUST | ✅ |
| FR-N-2 | Notifications MUST be delivered in real time when the user has an active socket. | SHOULD | ✅ |
| FR-N-3 | A user MUST be able to list notifications (cursor-paginated), mark them read, and clear them. | MUST | ✅ |
| FR-N-4 | A user MUST be able to configure which notification types they receive. | SHOULD | ✅ |
| FR-N-5 | Notification creation MUST NOT block or fail message delivery. | MUST | ✅ |
| FR-N-6 | Notification rows older than 90 days MUST be pruned. | MAY | ✅ |

---

## 2. Non-functional requirements

### 2.1 Security (`NFR-S`)

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-S-1 | Password storage | Argon2id, `m=19456 KiB, t=2, p=1` (OWASP baseline), per-password salt |
| NFR-S-2 | Session storage | 256-bit CSPRNG token, SHA-256 hashed at rest, `HttpOnly`+`Secure`+`SameSite=Lax` cookie |
| NFR-S-3 | CSRF | Double-submit cookie required on all unsafe methods; WebSocket handshake requires `Origin` validation |
| NFR-S-4 | Authorization | 100 % of reads/writes authorized server-side from the authenticated identity; no client-supplied `userId` is trusted |
| NFR-S-5 | Input validation | Every request body/param validated with Bean Validation; unknown fields rejected |
| NFR-S-6 | Rate limiting | Login 10/15 min/IP + 5/15 min/account, register 5/h/IP, forgot-password 3/h/email, messages 30/min/user, reports 10/day/user |
| NFR-S-7 | Transport | TLS 1.2+ in production; HSTS; `Secure` cookies; `wss://` |
| NFR-S-8 | Headers | `Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options: DENY`, `Permissions-Policy` |
| NFR-S-9 | Request size limits | HTTP body ≤ 64 KiB; STOMP frame ≤ 16 KiB; message content ≤ 4 000 chars |
| NFR-S-10 | Secrets | No credentials in code or VCS; `.env.example` only; production profile fails fast when required secrets are absent |
| NFR-S-11 | Error leakage | No stack traces, SQL, or internal paths in responses; correlation via `X-Request-Id` |
| NFR-S-12 | Logging | Never log passwords, session tokens, reset tokens, email bodies, or message content |

### 2.2 Performance (`NFR-P`)

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-P-1 | REST read latency (p95, 1 000 concurrent users) | < 150 ms |
| NFR-P-2 | Send → recipient render (p95, same region) | < 300 ms |
| NFR-P-3 | Message history page (50 messages) | < 100 ms, index-only range scan on `(conversation_id, created_at DESC, id DESC)` |
| NFR-P-4 | Conversation list (50 rows incl. last message + unread count) | < 120 ms, single query with LATERAL joins |
| NFR-P-5 | WebSocket handshake + CONNECT | < 200 ms |
| NFR-P-6 | Frontend first contentful paint (cold, 4G) | < 2.0 s; JS bundle ≤ 350 KB gzipped |
| NFR-P-7 | Typing indicator latency | < 150 ms end-to-end |
| NFR-P-8 | Sustained load | 2 000 sockets, 200 msg/s for 10 min without unbounded memory growth |

Explicit anti-requirements: no `SELECT * FROM messages` without a conversation scope; no `OFFSET`
pagination on messages; no N+1 queries on the conversation list (verified by query-count tests).

### 2.3 Availability & reliability (`NFR-A`)

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-A-1 | Committed messages MUST NOT be lost (PostgreSQL durability, `fsync` on commit) | 100 % |
| NFR-A-2 | Redis loss MUST NOT lose durable data; worst case is degraded presence/typing | full recovery |
| NFR-A-3 | Backend restart MUST be transparent to users after automatic reconnect | ≤ 2 s of degraded state |
| NFR-A-4 | Health endpoint for orchestrators (`/actuator/health` incl. DB + Redis indicators) | 200 when healthy, 503 otherwise |
| NFR-A-5 | Database backup: nightly logical dump + WAL archiving (production) | RPO ≤ 5 min, RTO ≤ 1 h |

### 2.4 Scalability (`NFR-C`)

| ID | Requirement |
| --- | --- |
| NFR-C-1 | REST instances MUST be stateless (session state in DB+Redis) so horizontal scaling needs no sticky sessions. |
| NFR-C-2 | Realtime fan-out MUST work across instances via Redis Pub/Sub. |
| NFR-C-3 | Schema MUST support 10⁸ messages (partition-ready `messages`, index-driven access paths). |
| NFR-C-4 | New domains MUST be addable as modules without cross-module schema coupling. |

### 2.5 Maintainability (`NFR-M`)

| ID | Requirement |
| --- | --- |
| NFR-M-1 | Feature-oriented package structure; no god classes (guideline: ≤ 300 lines/class, ≤ 30 lines/method). |
| NFR-M-2 | DTOs at every boundary; entities never serialised. |
| NFR-M-3 | Centralised exception handling and one error contract. |
| NFR-M-4 | Configuration only via `@ConfigurationProperties`; no magic values in code. |
| NFR-M-5 | Every architecture-relevant decision recorded in `docs/`. |
| NFR-M-6 | OpenAPI generated from code; `/swagger-ui` available in dev. |

### 2.6 Accessibility (`NFR-X`)

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-X-1 | All interactive controls keyboard reachable, visible focus ring | WCAG 2.1 AA |
| NFR-X-2 | Semantic HTML: `main`, `nav`, `ul/li` for lists, `button`, `label`, `aria-live` for new messages/typing | WCAG 2.1 AA |
| NFR-X-3 | Colour contrast ≥ 4.5:1 for text in both themes | WCAG 2.1 AA |
| NFR-X-4 | Message list announced to screen readers (`role="log"`, `aria-live="polite"`, `aria-relevant="additions"`) with per-message author + time | manual audit |
| NFR-X-5 | `prefers-reduced-motion` disables non-essential animation | WCAG 2.3.3 |
| NFR-X-6 | Full flows operable at 200 % zoom and at 320 px width | WCAG 1.4.10 |

### 2.7 Responsiveness (`NFR-R`)

| ID | Requirement |
| --- | --- |
| NFR-R-1 | Desktop ≥ 1024 px: three columns (navigation, conversation list, active conversation). |
| NFR-R-2 | Tablet 768–1023 px: two columns (list + conversation), navigation collapses to icons. |
| NFR-R-3 | Mobile < 768 px: single pane with push navigation (list → chat → profile/settings) and swipe-back. |
| NFR-R-4 | Layouts MUST be distinct, not a scaled-down desktop grid. |
| NFR-R-5 | Touch targets ≥ 44×44 px; safe-area insets respected. |
| NFR-R-6 | The conversation list MUST remain scrollable with 5 000 conversations (virtualisation-ready list) |

### 2.8 Observability (`NFR-O`)

| ID | Requirement |
| --- | --- |
| NFR-O-1 | Structured JSON logs in production with `requestId`, `userId` (hashed into a correlation id where sensitive), event name, latency. |
| NFR-O-2 | Security events logged: registration, login success/failure, logout, password change, session revoke, authorization failure, WebSocket handshake rejection, rate-limit trip, moderation state change. |
| NFR-O-3 | Micrometer metrics for HTTP, WebSocket sessions, message throughput, fan-out latency, Redis latency; `/actuator/prometheus` exposed to the monitoring network only. |
| NFR-O-4 | Liveness vs readiness separated (`/actuator/health/liveness`, `/actuator/health/readiness`). |

### 2.9 Testability (`NFR-T`)

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-T-1 | Unit + slice + integration coverage of every module | ≥ 80 % line coverage on services, 100 % of security decisions |
| NFR-T-2 | Integration tests run against real PostgreSQL and Redis (Testcontainers) | mandatory in CI |
| NFR-T-3 | The end-to-end journey (register → search → message → realtime → read receipt) MUST be automated | mandatory |
| NFR-T-4 | Tests MUST NOT depend on execution order or on shared mutable state | enforced by Hibernate `@Transactional` isolation or per-test fixtures |

### 2.10 Compliance & data protection (`NFR-D`)

| ID | Requirement |
| --- | --- |
| NFR-D-1 | Privacy policy surface: users are told that pseudonymity ≠ anonymity and that content is stored unencrypted server-side. |
| NFR-D-2 | Account deletion erases personal data (email, profile, sessions, notifications, avatar) within the same transaction chain. |
| NFR-D-3 | Data minimisation: no IP-based analytics; IP addresses are stored on sessions only for abuse investigation and are purged with the session. |
| NFR-D-4 | User data export (`GET /api/v1/users/me/export`) is planned (see roadmap) — 🟡 partial. |

---

## 3. Traceability

| Requirement group | Primary code | Primary tests |
| --- | --- | --- |
| FR-A (auth) | `auth/**` | `auth/AuthServiceTest`, `auth/AuthControllerIT`, `security/SessionSecurityIT` |
| FR-P (profile) | `profiles/**`, `users/**` | `profiles/UsernamePolicyTest`, `profiles/ProfileControllerIT` |
| FR-D (discovery) | `profiles/ProfileSearchService` | `profiles/ProfileSearchIT` |
| FR-M (messaging) | `messaging/**`, `conversations/**` | `messaging/MessageServiceTest`, `messaging/MessagePersistenceIT`, `messaging/MessageAuthorizationIT` |
| FR-S (receipts) | `messaging/ReceiptService` | `messaging/ReceiptServiceIT` |
| FR-R (realtime) | `messaging/ws/**`, `presence/**` | `realtime/ChatWebSocketIT`, `presence/PresenceServiceIT` |
| FR-V (privacy/blocking) | `users/BlockService`, `users/PrivacyService` | `users/BlockEnforcementIT`, `users/PrivacyEnforcementIT` |
| FR-X (moderation) | `moderation/**` | `moderation/ReportServiceTest`, `moderation/ReportControllerIT` |
| FR-N (notifications) | `notifications/**` | `notifications/NotificationServiceTest`, `notifications/NotificationDeliveryIT` |
| NFR-S (security) | `config/SecurityConfig`, `auth/security/**`, `common/ratelimit/**` | `security/*IT`, `common/ratelimit/RedisRateLimiterTest` |
| NFR-T (E2E) | — | `tests/e2e/full-conversation-journey.spec.js` |
