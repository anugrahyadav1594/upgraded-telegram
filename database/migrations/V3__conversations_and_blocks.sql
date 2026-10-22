-- =====================================================================================
-- V3 · Conversations, participants and blocks
-- =====================================================================================
-- The model is conversation-type agnostic: DIRECT is the only type produced today, but
-- GROUP and CHANNEL are already expressible (type + title + participant roles) without a
-- destructive migration later.
-- =====================================================================================

CREATE TABLE conversations (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    type             text        NOT NULL DEFAULT 'DIRECT',
    direct_key       text,
    title            text,
    created_by       uuid        REFERENCES users (id) ON DELETE SET NULL,
    -- Denormalised pointer used for list ordering and "last message" rendering without a
    -- correlated subquery per row. The foreign key is added in V4 (messages does not exist yet).
    last_message_id  uuid,
    last_activity_at timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT conversations_type_valid CHECK (type IN ('DIRECT', 'GROUP', 'CHANNEL')),
    -- A DIRECT conversation is identified by the unordered pair of its participants, which is
    -- what makes "start a chat with this person" idempotent under concurrent requests.
    CONSTRAINT conversations_direct_key_present CHECK (
        (type = 'DIRECT' AND direct_key IS NOT NULL)
        OR (type <> 'DIRECT')
    ),
    CONSTRAINT conversations_direct_key_format CHECK (
        direct_key IS NULL OR direct_key ~ '^[0-9a-f-]{36}:[0-9a-f-]{36}$'
    ),
    CONSTRAINT conversations_title_length CHECK (title IS NULL OR char_length(title) BETWEEN 1 AND 128)
);

-- The idempotency guarantee for DIRECT conversations (two simultaneous POST /conversations
-- cannot create two chats for the same pair: the second insert violates this index).
CREATE UNIQUE INDEX conversations_direct_key_uniq
    ON conversations (direct_key)
    WHERE direct_key IS NOT NULL;

CREATE INDEX conversations_last_activity_idx ON conversations (last_activity_at DESC, id DESC);

CREATE TRIGGER conversations_set_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN conversations.direct_key IS
    'For DIRECT conversations: least(userA,userB) || '':'' || greatest(userA,userB). Unique, NULL for GROUP/CHANNEL.';

-- -------------------------------------------------------------------------------------
-- conversation_participants
-- -------------------------------------------------------------------------------------
-- Per-user conversation state (the conversation itself is shared; unread position, mute,
-- archive and "delete for me" are per participant).
CREATE TABLE conversation_participants (
    conversation_id       uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id               uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at             timestamptz NOT NULL DEFAULT now(),
    last_read_message_id  uuid,
    last_read_at          timestamptz,
    muted                 boolean     NOT NULL DEFAULT false,
    archived              boolean     NOT NULL DEFAULT false,
    deleted_at            timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (conversation_id, user_id)
);

-- Drives the conversation list query for one user (ordered by last activity, excluding
-- conversations the user removed).
CREATE INDEX conversation_participants_user_idx
    ON conversation_participants (user_id, deleted_at, archived);

-- Drives "who else is in this conversation" and the unread-count join.
CREATE INDEX conversation_participants_conversation_idx
    ON conversation_participants (conversation_id);

-- Fast lookup of the counterpart(s) for a conversation the user is active in.
CREATE INDEX conversation_participants_active_user_idx
    ON conversation_participants (user_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER conversation_participants_set_updated_at
    BEFORE UPDATE ON conversation_participants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN conversation_participants.deleted_at IS
    'Per-user "delete conversation". The other participant keeps their copy; a new message revives both sides.';

-- -------------------------------------------------------------------------------------
-- user_blocks
-- -------------------------------------------------------------------------------------
-- Blocking is a first-class relationship, enforced server-side on every interaction path
-- (send, typing, presence, profile fetch, conversation creation).
CREATE TABLE user_blocks (
    blocker_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT user_blocks_no_self_block CHECK (blocker_id <> blocked_id)
);

-- Reverse-direction lookups ("is the other user blocking me?") are as important as the
-- forward direction, so both are indexed (the primary key covers forward only).
CREATE INDEX user_blocks_blocked_idx ON user_blocks (blocked_id, blocker_id);

COMMENT ON TABLE user_blocks IS
    'Directed block relationships. A block suppresses messaging, typing, presence and profile visibility in both directions of interaction.';
