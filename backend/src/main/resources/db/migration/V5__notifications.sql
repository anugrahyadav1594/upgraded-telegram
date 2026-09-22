-- =====================================================================================
-- V5 · Notifications and notification preferences
-- =====================================================================================
-- Notifications are durable (they survive a refresh) but strictly derived: losing one is a
-- lost convenience, never lost data. Creation happens after the message transaction commits
-- and must never be able to fail a send.
-- =====================================================================================

CREATE TABLE notifications (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            text        NOT NULL,
    conversation_id uuid        REFERENCES conversations (id) ON DELETE CASCADE,
    message_id      uuid        REFERENCES messages (id) ON DELETE CASCADE,
    actor_id        uuid        REFERENCES users (id) ON DELETE SET NULL,
    -- Truncated preview only: a notification must never be a second copy of the message body.
    preview         text,
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT notifications_type_valid
        CHECK (type IN ('NEW_MESSAGE', 'CONVERSATION_CREATED', 'MODERATION_UPDATE', 'SYSTEM')),
    CONSTRAINT notifications_preview_length CHECK (preview IS NULL OR char_length(preview) <= 120),
    -- A message notification without a message is meaningless; a moderation notice has neither.
    CONSTRAINT notifications_message_type_consistent CHECK (
        (type = 'NEW_MESSAGE' AND message_id IS NOT NULL AND conversation_id IS NOT NULL)
        OR (type <> 'NEW_MESSAGE')
    )
);

-- Notification centre listing (cursor paginated, newest first).
CREATE INDEX notifications_user_created_idx ON notifications (user_id, created_at DESC, id DESC);

-- Unread badge: partial index over the small unread subset.
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

-- Retention job support (prune rows older than 90 days).
CREATE INDEX notifications_created_at_idx ON notifications (created_at);

COMMENT ON TABLE notifications IS
    'Durable in-app notifications. Pruned after 90 days. Creation is asynchronous and after-commit so it can never block or fail a message send.';

-- -------------------------------------------------------------------------------------
-- user_notification_settings
-- -------------------------------------------------------------------------------------
CREATE TABLE user_notification_settings (
    user_id                uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    new_message            boolean     NOT NULL DEFAULT true,
    mentions               boolean     NOT NULL DEFAULT true,
    conversation_updates   boolean     NOT NULL DEFAULT false,
    moderation_updates     boolean     NOT NULL DEFAULT true,
    email_digest           boolean     NOT NULL DEFAULT false,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER user_notification_settings_set_updated_at
    BEFORE UPDATE ON user_notification_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN user_notification_settings.new_message IS
    'In-app notification for messages in unmuted conversations. Per-conversation mute (conversation_participants.muted) takes precedence.';
