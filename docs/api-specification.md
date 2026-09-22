# API Specification (v1)

Base URL: `/api/v1` · Content type: `application/json; charset=utf-8` · Timestamps: ISO-8601 UTC with
milliseconds (`2026-09-22T10:41:07.812Z`) · Identifiers: UUID v4 strings.

Interactive documentation (development only): `GET /swagger-ui/index.html`, raw spec at
`GET /v3/api-docs`.

---

## 1. Conventions

### Authentication

Session-based. `POST /auth/login` (and `/auth/register`) return a `Set-Cookie`:

```
Set-Cookie: session=<token>; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000
Set-Cookie: XSRF-TOKEN=<token>; Path=/; SameSite=Lax
```

- `session` is **HttpOnly** — JavaScript must never read it.
- `XSRF-TOKEN` is deliberately readable and MUST be echoed for every unsafe method
  (`POST`, `PUT`, `PATCH`, `DELETE`) in the header `X-XSRF-TOKEN`.
- Missing/expired session on a protected route → `401 UNAUTHENTICATED`.
- Valid session but insufficient permission → `403 FORBIDDEN`.
- Unauthenticated `POST`/`PATCH`/`DELETE` without a valid CSRF token → `403 CSRF_INVALID`.

### Requests

- Unknown JSON fields are rejected (`FAIL_ON_UNKNOWN_PROPERTIES`) → `400 INVALID_REQUEST`.
- `limit` defaults to `25`, maximum `100` for lists and `50` (max `100`) for message pages.
- Cursor is an opaque Base64URL string returned as `nextCursor`; `null` means "no more pages".

### Responses — paginated envelope

```json
{
  "items": [ … ],
  "nextCursor": "MjAyNi0wOS0yMlQxMDo0MTowNy44MTJafDRhZj…",
  "hasMore": true,
  "limit": 25
}
```

### Error envelope (all failures)

```json
{
  "timestamp": "2026-09-22T10:41:07.812Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "The request could not be processed",
  "path": "/api/v1/auth/register",
  "requestId": "b0d1e6f2-6f2e-4e1a-9a3d-2a86d2a1f0aa",
  "details": [
    { "field": "username", "code": "PATTERN", "message": "must contain only lowercase letters, digits and underscores" }
  ]
}
```

### Error codes

| `code` | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Bean Validation failure (details per field) |
| `INVALID_REQUEST` | 400 | Malformed JSON, unknown field, bad cursor, bad UUID |
| `UNAUTHENTICATED` | 401 | No/expired session |
| `FORBIDDEN` | 403 | Authenticated but not allowed |
| `CSRF_INVALID` | 403 | Missing/invalid CSRF token |
| `BLOCKED` | 403 | Interaction denied by a block in either direction |
| `NOT_FOUND` | 404 | Resource absent, or hidden by privacy/block rules |
| `USERNAME_TAKEN` | 409 | Username already exists |
| `EMAIL_TAKEN` | 409 | Email already registered |
| `CONFLICT` | 409 | Generic state conflict (e.g. duplicate report) |
| `UNSUPPORTED_MESSAGE_TYPE` | 422 | `IMAGE`/`FILE` not enabled in this version |
| `MESSAGE_NOT_EDITABLE` | 422 | Edit window exceeded or message deleted |
| `PRIVACY_RESTRICTED` | 422 | `allowMessagesFrom` denied conversation creation |
| `RATE_LIMITED` | 429 | Rate limit tripped (`Retry-After` header set) |
| `PAYLOAD_TOO_LARGE` | 413 | Body/message exceeded limits |
| `INTERNAL_ERROR` | 500 | Unexpected (no internals leaked) |
| `SERVICE_UNAVAILABLE` | 503 | Dependency unavailable (DB down) |

### Rate limits (response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`)

| Route | Limit |
| --- | --- |
| `POST /auth/register` | 5 / hour / IP |
| `POST /auth/login` | 10 / 15 min / IP **and** 5 / 15 min / account |
| `POST /auth/forgot-password` | 3 / hour / email + 10 / hour / IP |
| `POST /auth/reset-password` | 5 / hour / IP |
| `POST /auth/change-password` | 5 / hour / user |
| `GET /users/search` | 60 / min / user |
| `POST /conversations` | 30 / hour / user |
| `POST /conversations/{id}/messages` | 30 / min / user, 600 / day / user |
| `POST /reports` | 10 / day / user |

---

## 2. Authentication

### `POST /auth/register`

Request:

```json
{
  "email": "ghost@example.test",
  "password": "correct-horse-battery-staple",
  "username": "ghost",
  "displayName": "Ghost"
}
```

Validation: `email` valid, ≤ 254 chars; `password` 12–128 chars, not in the common-password list;
`username` matches `^[a-z0-9](?:[a-z0-9_]{1,30}[a-z0-9])$` after normalisation, not reserved;
`displayName` 1–64 chars, no control characters.

`201 Created` + `Location: /api/v1/users/ghost` + session cookies:

```json
{
  "user": {
    "id": "8f1a4d3e-7c2b-4a6f-9d3e-2b1c5a7e9f10",
    "email": "ghost@example.test",
    "username": "ghost",
    "displayName": "Ghost",
    "avatarUrl": null,
    "bio": null,
    "emailVerified": false,
    "profileComplete": true,
    "createdAt": "2026-09-22T10:41:07.812Z"
  },
  "session": { "expiresAt": "2026-10-22T10:41:07.812Z" }
}
```

Errors: `409 EMAIL_TAKEN`, `409 USERNAME_TAKEN`, `400 VALIDATION_FAILED`, `429 RATE_LIMITED`.

### `POST /auth/login`

```json
{ "identifier": "ghost@example.test", "password": "…" }
```

`identifier` accepts an email address or `@username`/`username`. `200 OK` with the same body shape as
register (plus `lastLoginAt`). Failures always return `401 UNAUTHENTICATED` with the generic message
`"Email, username or password is incorrect"` — never revealing which factor was wrong. Progressive
delay: after 5 failures within 15 minutes the account is soft-locked for 15 minutes
(`429 RATE_LIMITED` with `Retry-After`).

### `POST /auth/logout` → `204 No Content`

Deletes the current session and clears cookies. Idempotent.

### `POST /auth/logout-all` → `204 No Content`

Deletes every session of the user and publishes `session.revoked` to all their connected sockets.

### `GET /auth/me` → `200 OK`

```json
{
  "id": "8f1a4d3e-…",
  "email": "ghost@example.test",
  "username": "ghost",
  "displayName": "Ghost",
  "avatarUrl": null,
  "bio": null,
  "emailVerified": false,
  "profileComplete": true,
  "usernamePublic": true,
  "privacy": { "showOnlineStatus": true, "showLastSeen": true, "showProfileInSearch": true,
               "allowMessagesFrom": "EVERYONE", "readReceipts": true },
  "unreadNotifications": 3,
  "createdAt": "2026-09-22T10:41:07.812Z",
  "lastLoginAt": "2026-09-22T12:02:00.000Z"
}
```

### `POST /auth/forgot-password`

```json
{ "email": "ghost@example.test" }
```

Always `202 Accepted` with `{ "message": "If that email is registered, we've sent a reset link." }`.
A single-use token (30 min TTL, SHA-256 hashed at rest) is emailed. Never returns whether the address
exists.

### `POST /auth/reset-password`

```json
{ "token": "…", "newPassword": "…" }
```

`204 No Content`. Invalid/expired/reused token → `400 INVALID_REQUEST`. Success revokes all sessions.

### `POST /auth/change-password`

```json
{ "currentPassword": "…", "newPassword": "…" }
```

`204 No Content`. Wrong current password → `401 UNAUTHENTICATED`. Success revokes all **other**
sessions and rotates the current one.

### `GET /auth/sessions` → `200 OK`

```json
{ "items": [
  { "id": "b1c2…", "current": true,  "userAgent": "Mozilla/5.0 (Macintosh…)",
    "ipAddress": "203.0.113.7", "createdAt": "…", "lastUsedAt": "…", "expiresAt": "…" },
  { "id": "d4e5…", "current": false, "userAgent": "Mozilla/5.0 (iPhone…)", … }
] }
```

### `DELETE /auth/sessions/{sessionId}` → `204 No Content`

`403 FORBIDDEN` if the session belongs to another user.

### `DELETE /users/me` → `204 No Content`

Account deletion: hard-deletes email, profile, privacy/notification settings, sessions, reset tokens,
blocks and notifications; anonymises authored messages (`sender_id` → tombstone user, content
replaced with `"[deleted account]"`); deletes conversations where the user was the only participant.

---

## 3. Users, profiles, privacy

### `GET /users/search?q={query}&limit=&cursor=`

Query: 2–32 chars (usernames are matched prefix + trigram similarity; display names substring,
case-insensitive). Only `showProfileInSearch = true` users, excluding self, blocked and blockers.

```json
{
  "items": [
    { "id": "…", "username": "ghost", "displayName": "Ghost", "avatarUrl": null,
      "bio": "Talks about distributed systems", "online": true, "lastSeenAt": null,
      "usernamePublic": true, "existingConversationId": "e9f1…" }
  ],
  "nextCursor": null, "hasMore": false, "limit": 25
}
```

### `GET /users/{username}` → `200 OK` (`PublicProfileResponse`)

```json
{
  "id": "…",
  "username": "ghost",
  "usernamePublic": true,
  "displayName": "Ghost",
  "avatarUrl": null,
  "bio": "Talks about distributed systems",
  "online": true,
  "lastSeenAt": null,
  "memberSince": "2026-03-01T00:00:00.000Z",
  "existingConversationId": "e9f1…",
  "blockedByMe": false,
  "canMessage": true
}
```

Rules: **never** returns `email`, `passwordHash`, session data or moderation data. If the target
blocked the requester (or privacy forbids), returns `404 NOT_FOUND` (existence is not disclosed).
`online`/`lastSeenAt` are `null` when hidden. If the user set `usernamePublic = false`, `username`
is `null` and the profile is only reachable via `id` from an existing conversation.

### `GET /users/me` → `200 OK`

Full private view (as in `GET /auth/me`, without session data) including `privacy`,
`notificationSettings`, `stats { conversationCount, messageCount }`.

### `PATCH /users/me`

```json
{ "displayName": "Ghost 👻", "bio": "…", "avatarUrl": "https://…", "username": "ghost_42" }
```

All fields optional; only provided fields change. `200 OK` with the private view. `409 USERNAME_TAKEN`
(username change limited to once per 7 days → `429 RATE_LIMITED` otherwise).

### `GET /privacy` → `200 OK`

```json
{ "showOnlineStatus": true, "showLastSeen": true, "showProfileInSearch": true,
  "allowMessagesFrom": "EVERYONE", "readReceipts": true }
```

### `PATCH /privacy`

Partial update; `showLastSeen` may not be `true` while `showOnlineStatus` is `false` (→ `422`).
`allowMessagesFrom ∈ {EVERYONE, CONTACTS}`. `200 OK` with the full object. Changes are audited in logs
and applied to subsequent presence events immediately.

### `GET /users/{userId}/presence` → `200 OK` (REST fallback for socket-less clients)

```json
{ "userId": "…", "status": "ONLINE", "lastSeenAt": null, "visible": true }
```

`visible: false` (with `status: null`) when privacy suppresses it; `404` if blocked.

### `POST /users/{userId}/block` → `204 No Content`

Idempotent. Effects (enforced server-side): neither side can start a conversation; existing
conversation stops accepting messages in both directions (`403 BLOCKED`); the blocked user's profile
returns `404` to the blocker; presence/typing between them is suppressed.

### `DELETE /users/{userId}/block` → `204 No Content`

Idempotent unblock. The conversation list entry reappears if messages already exist.

### `GET /blocks` → `200 OK`

```json
{ "items": [ { "userId": "…", "username": "spammer", "displayName": "Spam", "blockedAt": "…" } ],
  "nextCursor": null, "hasMore": false, "limit": 25 }
```

---

## 4. Conversations

`ConversationType ∈ {DIRECT}` today; `GROUP`/`CHANNEL` are reserved in the schema and the API accepts
(and rejects with `422`) other types rather than ignoring them.

### `GET /conversations?limit=&cursor=&archived=false`

```json
{
  "items": [
    {
      "id": "e9f1…",
      "type": "DIRECT",
      "peer": { "id": "…", "username": "ghost", "displayName": "Ghost", "avatarUrl": null,
                "online": true, "lastSeenAt": null },
      "lastMessage": { "id": "…", "senderId": "…", "preview": "See you tomorrow",
                       "messageType": "TEXT", "createdAt": "2026-09-22T10:42:11.000Z",
                       "deleted": false },
      "unreadCount": 2,
      "muted": false,
      "archived": false,
      "lastActivityAt": "2026-09-22T10:42:11.000Z",
      "createdAt": "2026-09-20T09:00:00.000Z"
    }
  ],
  "nextCursor": "…", "hasMore": true, "limit": 25
}
```

Ordering: `last_activity_at DESC, id DESC`. Implemented as a single SQL statement (LATERAL joins) —
no N+1.

### `POST /conversations`

```json
{ "peerUserId": "…" }
```

Idempotent for DIRECT: if a conversation exists — including a soft-deleted one, which is
un-deleted — it returns `200 OK`; otherwise `201 Created` with `Location`. Errors:
`403 BLOCKED`, `422 PRIVACY_RESTRICTED`, `404 NOT_FOUND` (unknown user), `409 CONFLICT` (self),
`429 RATE_LIMITED`.

### `GET /conversations/{conversationId}` → `200 OK` (same shape as a list item)

`404` when the caller is not a participant (existence is not disclosed).

### `DELETE /conversations/{conversationId}` → `204 No Content`

Removes the conversation from the caller's list (`deleted_at` on the participant row); the other
participant keeps their copy. For DIRECT, sending a new message by either side revives both sides.

### `PATCH /conversations/{conversationId}`

```json
{ "muted": true, "archived": false }
```

`200 OK` with the updated summary. Only the caller's participant row changes.

### `POST /conversations/{conversationId}/read`

```json
{ "lastMessageId": "…" }
```

`204 No Content`. Sets `last_read_message_id`, marks all unread receipts in that conversation as read
in one `UPDATE`, and broadcasts `message.read.updated` to the other participants (unless
`readReceipts` is off).

---

## 5. Messages

### `GET /conversations/{conversationId}/messages?limit=50&cursor=&direction=older`

Ordering: newest first (`created_at DESC, id DESC`); clients reverse for display. `direction=older`
(default) walks back in time; `direction=newer&after=<messageId or cursor>` is used after reconnect
resync.

```json
{
  "items": [
    {
      "id": "c3d4…",
      "conversationId": "e9f1…",
      "senderId": "…",
      "content": "See you tomorrow",
      "messageType": "TEXT",
      "clientMessageId": "7b2f…",
      "replyTo": { "id": "…", "senderId": "…", "preview": "Hey!", "deleted": false },
      "reactions": [ { "emoji": "😂", "count": 2, "reactedByMe": true } ],
      "receipt": { "status": "READ", "deliveredAt": "…", "readAt": "…" },
      "editedAt": null,
      "deletedAt": null,
      "createdAt": "2026-09-22T10:42:11.000Z",
      "updatedAt": "2026-09-22T10:42:11.000Z"
    }
  ],
  "nextCursor": "…", "hasMore": true, "limit": 50
}
```

Deleted messages are returned as tombstones: `deletedAt` set, `content` replaced by `null` (the UI
renders "This message was deleted"), `replyTo` preserved.

Cursor is `(created_at, id)`; two messages sharing a timestamp are ordered deterministically by `id`.
`limit > 100` → `400`. Message pages never use `OFFSET`.

### `GET /conversations/{conversationId}/messages/resync?afterSequence=&limit=`

Reconnect resynchronisation: every message with `sequence > afterSequence`, oldest first, same item
shape, `{ "items": [...], "nextCursor": null, "hasMore": bool, "limit": n }`. Clients drain it page by
page until `hasMore` is `false`, then continue with `presence` and their local outbox. `sequence` is
assigned by the database, so this works even if the client's clock is wrong — unlike a
"messages since <timestamp>" query. A non-participant gets `404`.

### `POST /conversations/{conversationId}/messages`

```json
{
  "content": "Hey!",
  "clientMessageId": "7b2f…",
  "messageType": "TEXT",
  "replyToMessageId": null
}
```

`201 Created`:

```json
{ "id": "c3d4…", "clientMessageId": "7b2f…", "status": "SENT",
  "createdAt": "2026-09-22T10:42:11.000Z", "receipt": { "status": "SENT" },
  "conversationId": "e9f1…", "senderId": "…", "content": "Hey!", "messageType": "TEXT",
  "replyTo": null, "reactions": [], "editedAt": null, "deletedAt": null }
```

Idempotency: repeating the same `clientMessageId` returns the stored message with `200 OK` (and
`Idempotent-Replay: true` header) instead of creating a duplicate. Errors: `403 BLOCKED`, `404`
(not a participant / unknown conversation), `422 UNSUPPORTED_MESSAGE_TYPE`,
`400 VALIDATION_FAILED` (1–4000 chars, no control characters other than newline/tab),
`429 RATE_LIMITED`.

### `PATCH /messages/{messageId}`

```json
{ "content": "See you tomorrow at 10" }
```

`200 OK` with the updated message. Only the **sender** may edit; `422 MESSAGE_NOT_EDITABLE` when the
message is deleted, older than 48 hours, or not `TEXT`. Edits set `edited_at` and broadcast
`message.updated`.

### `DELETE /messages/{messageId}` → `204 No Content`

Only the sender may delete. Soft delete (`deleted_at`, content nulled); broadcasts
`message.deleted`; replies keep working (the parent renders as a tombstone).

### `POST /messages/{messageId}/read` → `204 No Content`

Marks a single message read (recipient only). Equivalent to conversation-level read for one message;
idempotent.

### `POST /messages/{messageId}/reaction`

```json
{ "emoji": "😂" }
```

Toggles the reaction for the authenticated user. `200 OK` with the updated message including
aggregated reactions. Allow-list: 32 curated emoji; anything else → `400 VALIDATION_FAILED`.
Non-participants → `404`; blocked pairs → `403 BLOCKED`.

### `DELETE /messages/{messageId}/reaction?emoji=😂` → `200 OK` (updated message)

Removing a non-existent reaction is a no-op (idempotent).

---

## 6. Notifications

### `GET /notifications?limit=&cursor=&unreadOnly=false`

```json
{ "items": [
    { "id": "…", "type": "NEW_MESSAGE", "conversationId": "e9f1…",
      "actor": { "id": "…", "username": "ghost", "displayName": "Ghost", "avatarUrl": null },
      "preview": "See you tomorrow", "read": false, "createdAt": "…" }
  ], "nextCursor": null, "hasMore": false, "limit": 25,
  "unreadCount": 3 }
```

### `POST /notifications/read`

```json
{ "ids": ["…", "…"] }   // omitted or empty → mark all read
```

`200 OK` with `{ "unreadCount": 0 }`. Only the owner's rows can be affected.

### `DELETE /notifications/{notificationId}` → `204 No Content`
### `DELETE /notifications` → `204 No Content` (clear all)

### `GET /notifications/settings` → `200 OK`

```json
{ "newMessage": true, "mentions": true, "conversationUpdates": false, "moderationUpdates": true,
  "emailDigest": false }
```

### `PATCH /notifications/settings` → `200 OK` (same shape)

---

## 7. Moderation

### `POST /reports`

```json
{
  "targetType": "MESSAGE",            // USER | MESSAGE
  "targetUserId": "…",                // required for USER; optional context for MESSAGE
  "targetMessageId": "…",             // required for MESSAGE
  "reason": "HARASSMENT",             // SPAM | HARASSMENT | HATE_SPEECH | IMPERSONATION | SEXUAL_CONTENT | VIOLENCE | SELF_HARM | OTHER
  "description": "Optional, ≤ 1000 chars"
}
```

`201 Created`: `{ "id": "…", "status": "OPEN", "createdAt": "…" }`.

Rules: cannot report yourself (`400`); `targetMessageId` must belong to a conversation the reporter
participates in (`403`); a second active report of the same target by the same reporter →
`409 CONFLICT`; rate limited to 10/day. The reported user is **never** notified.

### Admin endpoints (role `ADMIN`, not exposed to regular users)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/reports?status=OPEN&cursor=` | Moderation queue |
| `PATCH` | `/admin/reports/{reportId}` | `{ "status": "REVIEWING" \| "RESOLVED" \| "DISMISSED", "note": "…" }` |
| `GET` | `/admin/reports/{reportId}/history` | Audit trail of state changes |
| `POST` | `/admin/users/{userId}/suspend` | `{ "reason": "…", "until": "…" }` |

Every admin action is written to `report_status_history` with actor, from/to status, note and time.

---

## 8. Health & observability

| Path | Auth | Purpose |
| --- | --- | --- |
| `GET /actuator/health` | public (aggregate status only) | Liveness/readiness for orchestrators |
| `GET /actuator/health/liveness` | public | JVM alive |
| `GET /actuator/health/readiness` | public | DB + Redis reachable |
| `GET /actuator/info` | public | Build/version metadata |
| `GET /actuator/metrics/**`, `GET /actuator/prometheus` | `ADMIN` or monitoring network | Metrics scrape |

---

## 9. Implementation notes / mapping

| Concern | Component |
| --- | --- |
| Request/response DTOs | `*.dto` packages, Java records, no Jackson-specific annotations |
| Validation | `jakarta.validation` annotations + `common/validation` custom constraints |
| Mapping | explicit mappers (`*Mapper` classes) — no reflection-based mappers |
| Errors | typed exceptions → `common/error/GlobalExceptionHandler` |
| Cursors | `common/pagination/CursorCodec` (Base64URL `sortKey|id`, HMAC-signed in production) |
| OpenAPI | `config/OpenApiConfig` (bearer-less cookie scheme documented, tags per module) |
| Auth | `auth/security/SessionAuthenticationFilter` + `SecurityConfig` authorization matrix |
| Rate limits | `common/ratelimit/RateLimitInterceptor` + `@RateLimited` annotations |
