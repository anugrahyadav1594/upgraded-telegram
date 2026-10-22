# Migrations

**The canonical copy lives in `backend/src/main/resources/db/migration/`.** This directory mirrors it
for infrastructure tooling workflows (for example, applying migrations with `psql` in a pipeline that
does not start the application).

| File | Contents |
| --- | --- |
| `V1__extensions_and_functions.sql` | Extensions (guarded), the `messages_sequence` sequence, `set_updated_at()` trigger function |
| `V2__identity.sql` | `users`, `profiles`, `user_privacy_settings`, `user_blocks`, `user_sessions`, `password_reset_tokens` |
| `V3__conversations_and_blocks.sql` | `conversations`, `conversation_participants` |
| `V4__messaging.sql` | `messages`, `message_receipts`, `message_reactions` |
| `V5__notifications.sql` | `notifications`, `notification_settings` |
| `V6__moderation.sql` | `reports`, `report_status_history` |
| `V7__search_indexes.sql` | Search and unread indexes |

Rules:

- **Never edit a migration that has been applied.** Flyway checksums it, and a changed checksum fails
  start-up for everybody. Add a new version instead.
- The mirror is updated by copying the canonical files; `tools/verify/sql_verify.py` checks both copies
  parse and that the entities match the columns.
- This sandbox's PostgreSQL has no contrib modules, which is why `V1` guards `CREATE EXTENSION pg_trgm`
  in a `DO` block that tolerates failure.
