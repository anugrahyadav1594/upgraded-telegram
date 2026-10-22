-- =====================================================================================
-- V4 · Messaging: messages, receipts, reactions, attachments
-- =====================================================================================
-- messages is the largest table in the system and the one with the tightest latency budget.
-- Everything here is designed around two access paths:
--   1. history page:  WHERE conversation_id = ? AND (created_at, id) < (?, ?)  ORDER BY created_at DESC, id DESC
--   2. resync:        WHERE conversation_id = ? AND sequence > ?
-- =====================================================================================

CREATE TABLE messages (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id           uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content             text,
    message_type        text        NOT NULL DEFAULT 'TEXT',
    reply_to_message_id uuid        REFERENCES messages (id) ON DELETE SET NULL,
    client_message_id   uuid,
    -- Monotonic ordering/resynchronisation token (see V1 messages_sequence).
    sequence            bigint      NOT NULL DEFAULT nextval('messages_sequence'),
    edited_at           timestamptz,
    deleted_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT messages_type_valid CHECK (message_type IN ('TEXT', 'IMAGE', 'FILE', 'SYSTEM')),
    CONSTRAINT messages_content_length CHECK (content IS NULL OR char_length(content) BETWEEN 1 AND 4000),
    -- A live TEXT message must have content; a tombstone (soft-deleted) legitimately has none.
    CONSTRAINT messages_text_content_present CHECK (
        message_type <> 'TEXT' OR content IS NOT NULL OR deleted_at IS NOT NULL
    ),
    -- Edits are only meaningful for live messages.
    CONSTRAINT messages_edit_requires_content CHECK (edited_at IS NULL OR content IS NOT NULL)
);

-- Idempotent sends: a retried POST/STOMP frame with the same clientMessageId cannot create a
-- second message. NULL client ids (allows server-generated/SYSTEM messages) are distinct in a
-- PostgreSQL unique index, which is exactly the intended behaviour.
CREATE UNIQUE INDEX messages_client_id_uniq
    ON messages (conversation_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

-- Primary history access path (cursor pagination: (created_at, id) descending).
CREATE INDEX messages_conversation_created_idx
    ON messages (conversation_id, created_at DESC, id DESC);

-- Reconnect resynchronisation ("everything after my watermark").
CREATE INDEX messages_conversation_sequence_idx
    ON messages (conversation_id, sequence);

-- "My messages" / moderation / per-sender history.
CREATE INDEX messages_sender_created_idx
    ON messages (sender_id, created_at DESC);

-- Reply lookups and tombstone rendering.
CREATE INDEX messages_reply_to_idx
    ON messages (reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;

CREATE TRIGGER messages_set_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE messages IS
    'Chat messages. Append-only in practice (edit/delete are rare UPDATEs). Partition-ready by created_at when volume grows.';
COMMENT ON COLUMN messages.sequence IS
    'Globally monotonic token assigned at insert; clients use it as a resync watermark (WHERE sequence > ?).';

-- -------------------------------------------------------------------------------------
-- Deferred foreign key for the denormalised last-message pointer
-- -------------------------------------------------------------------------------------
ALTER TABLE conversations
    ADD CONSTRAINT conversations_last_message_fk
    FOREIGN KEY (last_message_id) REFERENCES messages (id) ON DELETE SET NULL;

-- -------------------------------------------------------------------------------------
-- message_receipts — one row per (message, recipient); the server is authoritative
-- -------------------------------------------------------------------------------------
CREATE TABLE message_receipts (
    message_id   uuid        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       text        NOT NULL DEFAULT 'SENT',
    delivered_at timestamptz,
    read_at      timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (message_id, user_id),
    CONSTRAINT message_receipts_status_valid CHECK (status IN ('SENT', 'DELIVERED', 'READ')),
    -- Monotonicity expressed as data invariants: DELIVERED implies a delivery timestamp,
    -- READ implies both timestamps and cannot precede delivery.
    CONSTRAINT message_receipts_delivered_ts CHECK ((status = 'SENT') OR delivered_at IS NOT NULL),
    CONSTRAINT message_receipts_read_ts CHECK ((status <> 'READ') OR (read_at IS NOT NULL AND delivered_at IS NOT NULL)),
    CONSTRAINT message_receipts_read_after_delivered CHECK (read_at IS NULL OR read_at >= delivered_at)
);

-- Unread badge / unread counts: partial index keeps this cheap even with hundreds of millions
-- of already-read receipts.
CREATE INDEX message_receipts_unread_idx
    ON message_receipts (user_id)
    WHERE read_at IS NULL;

CREATE INDEX message_receipts_message_status_idx ON message_receipts (message_id, status);

CREATE TRIGGER message_receipts_set_updated_at
    BEFORE UPDATE ON message_receipts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE message_receipts IS
    'Per-recipient delivery state (SENT → DELIVERED → READ). Status transitions are guarded by WHERE clauses in the service so a replayed acknowledgement can never move a receipt backwards.';

-- -------------------------------------------------------------------------------------
-- message_reactions
-- -------------------------------------------------------------------------------------
CREATE TABLE message_reactions (
    message_id uuid        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    -- One reaction per user per emoji per message; toggling removes the row.
    PRIMARY KEY (message_id, user_id, emoji),
    CONSTRAINT message_reactions_emoji_length CHECK (char_length(emoji) BETWEEN 1 AND 16)
);

CREATE INDEX message_reactions_message_idx ON message_reactions (message_id);

-- -------------------------------------------------------------------------------------
-- attachments — modelled and validated, but attachment messages are not enabled yet
-- -------------------------------------------------------------------------------------
-- The table exists so the storage metadata contract (validation rules, checksum, object key)
-- is defined before the feature ships, and so `messages.message_type = 'IMAGE'|'FILE'` has a
-- real target. The API currently rejects those message types with UNSUPPORTED_MESSAGE_TYPE.
CREATE TABLE attachments (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      uuid        REFERENCES messages (id) ON DELETE CASCADE,
    uploader_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name       text        NOT NULL,
    content_type    text        NOT NULL,
    size_bytes      bigint      NOT NULL,
    storage_key     text        NOT NULL,
    checksum_sha256 text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT attachments_size_bounds CHECK (size_bytes BETWEEN 1 AND 26214400), -- 25 MiB
    CONSTRAINT attachments_file_name_safe
        CHECK (file_name !~ '[/\\]' AND char_length(file_name) BETWEEN 1 AND 255),
    CONSTRAINT attachments_checksum_hex CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX attachments_message_idx ON attachments (message_id);
CREATE INDEX attachments_uploader_idx ON attachments (uploader_id, created_at DESC);

COMMENT ON TABLE attachments IS
    'Attachment metadata (object-storage backed). Feature is modelled but not enabled in this release; see docs/roadmap.md.';
