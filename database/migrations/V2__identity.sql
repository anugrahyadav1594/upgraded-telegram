-- =====================================================================================
-- V2 · Identity: users, pseudonymous profiles, privacy settings, sessions, reset tokens
-- =====================================================================================
-- The split between `users` (private, contains the email address) and `profiles` (public,
-- pseudonymous) is the core of the product's privacy model: a profile can be read and
-- serialised without ever touching a table that holds an email address.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- users — private account identity. NEVER exposes email to other users.
-- -------------------------------------------------------------------------------------
CREATE TABLE users (
    id                      uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   text        NOT NULL,
    password_hash           text        NOT NULL,
    status                  text        NOT NULL DEFAULT 'ACTIVE',
    role                    text        NOT NULL DEFAULT 'USER',
    email_verified          boolean     NOT NULL DEFAULT false,
    failed_login_attempts   integer     NOT NULL DEFAULT 0,
    locked_until            timestamptz,
    last_login_at           timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT users_email_format      CHECK (email = lower(email) AND position('@' IN email) > 1),
    CONSTRAINT users_email_length      CHECK (char_length(email) <= 254),
    CONSTRAINT users_status_valid      CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT users_role_valid        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_failed_attempts   CHECK (failed_login_attempts >= 0),
    -- Argon2id PHC strings only. A defensive constraint: a bug that writes a plaintext or
    -- legacy hash is rejected by the database rather than silently accepted.
    CONSTRAINT users_password_is_argon2id
        CHECK (password_hash = '$argon2id-placeholder$' OR password_hash LIKE '$argon2id$%')
);

-- Case-insensitive uniqueness of the email address (the canonical form is stored lowercased).
CREATE UNIQUE INDEX users_email_lower_uniq ON users (lower(email));

-- Operational queries: "which accounts are locked/suspended".
CREATE INDEX users_status_idx ON users (status) WHERE status <> 'ACTIVE';
CREATE INDEX users_locked_until_idx ON users (locked_until) WHERE locked_until IS NOT NULL;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE users IS
    'Private account identity. Contains the email address, which must never be exposed to other users.';
COMMENT ON COLUMN users.password_hash IS
    'Argon2id PHC string ($argon2id$v=19$m=19456,t=2,p=1$salt$hash). Plaintext or fast hashes are rejected by a CHECK constraint.';

-- -------------------------------------------------------------------------------------
-- Tombstone account
-- -------------------------------------------------------------------------------------
-- Account deletion keeps referential integrity (and moderation history) intact by moving
-- authored content to a single, clearly-marked tombstone identity. A fixed UUID makes the
-- value deterministic in tests and migrations.
INSERT INTO users (id, email, password_hash, status, role, email_verified)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'deleted-account@invalid.example',
    '$argon2id-placeholder$',
    'DELETED',
    'USER',
    false
);

-- -------------------------------------------------------------------------------------
-- profiles — the pseudonymous public identity
-- -------------------------------------------------------------------------------------
CREATE TABLE profiles (
    user_id             uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    username            text        NOT NULL,
    display_name        text        NOT NULL,
    bio                 text,
    avatar_url          text,
    username_public     boolean     NOT NULL DEFAULT true,
    last_seen_at        timestamptz,
    profile_completed_at timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),

    -- 3..32 chars, lowercase letters/digits/underscore, no leading or trailing underscore.
    CONSTRAINT profiles_username_format
        CHECK (username ~ '^[a-z0-9](?:[a-z0-9_]{1,30})[a-z0-9]$'),
    CONSTRAINT profiles_username_no_double_underscore CHECK (username !~ '__'),
    CONSTRAINT profiles_username_not_reserved CHECK (NOT is_reserved_username(username)),
    CONSTRAINT profiles_username_is_lower CHECK (username = lower(username)),
    CONSTRAINT profiles_display_name_length CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 64),
    CONSTRAINT profiles_bio_length CHECK (bio IS NULL OR char_length(bio) <= 280),
    CONSTRAINT profiles_avatar_url_https
        CHECK (avatar_url IS NULL OR (avatar_url LIKE 'https://%' AND char_length(avatar_url) <= 512))
);

CREATE UNIQUE INDEX profiles_username_uniq ON profiles (lower(username));

-- Search access paths (GET /api/v1/users/search): case-insensitive *prefix* matching uses the
-- text pattern operator indexes below. The fuzzy/substring (trigram) indexes are created
-- conditionally in V7 because pg_trgm is an optional contrib extension.
CREATE INDEX profiles_username_pattern_idx ON profiles (username text_pattern_ops);
CREATE INDEX profiles_display_name_pattern_idx ON profiles (display_name text_pattern_ops);
CREATE INDEX profiles_searchable_idx ON profiles (user_id);

CREATE TRIGGER profiles_set_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE profiles IS
    'Public pseudonymous profile. Contains no email address by design: public API responses are projected from this table only.';
COMMENT ON COLUMN profiles.last_seen_at IS
    'Coarse presence hint written at most once per 5 minutes per user on last disconnect (never updated by heartbeats).';

-- -------------------------------------------------------------------------------------
-- user_privacy_settings
-- -------------------------------------------------------------------------------------
CREATE TABLE user_privacy_settings (
    user_id                 uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    show_online_status      boolean     NOT NULL DEFAULT true,
    show_last_seen          boolean     NOT NULL DEFAULT true,
    show_profile_in_search  boolean     NOT NULL DEFAULT true,
    allow_messages_from     text        NOT NULL DEFAULT 'EVERYONE',
    read_receipts           boolean     NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT privacy_allow_messages_from_valid
        CHECK (allow_messages_from IN ('EVERYONE', 'CONTACTS')),
    -- Privacy invariant enforced by the database: hiding online status must also hide last seen,
    -- otherwise "offline" would still leak when the user was last active.
    CONSTRAINT privacy_last_seen_implies_online
        CHECK (show_online_status OR NOT show_last_seen)
);

CREATE TRIGGER user_privacy_settings_set_updated_at
    BEFORE UPDATE ON user_privacy_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE user_privacy_settings IS
    'Per-user privacy switches. Extensible: new independent flags are added as columns with defaults.';

-- -------------------------------------------------------------------------------------
-- user_sessions — opaque server-managed sessions (cookie auth, never localStorage)
-- -------------------------------------------------------------------------------------
CREATE TABLE user_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      text        NOT NULL,
    ip_address      inet,
    user_agent      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    last_used_at    timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,

    CONSTRAINT user_sessions_token_hash_length CHECK (char_length(token_hash) = 64), -- sha-256 hex
    CONSTRAINT user_sessions_user_agent_length CHECK (user_agent IS NULL OR char_length(user_agent) <= 256),
    CONSTRAINT user_sessions_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX user_sessions_token_hash_uniq ON user_sessions (token_hash);
CREATE INDEX user_sessions_active_by_user_idx ON user_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX user_sessions_expires_at_idx ON user_sessions (expires_at);

COMMENT ON TABLE user_sessions IS
    'Server-side sessions. The raw token exists only in the HttpOnly cookie; only its SHA-256 hash is stored.';

-- -------------------------------------------------------------------------------------
-- password_reset_tokens — single-use, 30 minute, stored hashed
-- -------------------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,

    CONSTRAINT password_reset_tokens_hash_length CHECK (char_length(token_hash) = 64),
    CONSTRAINT password_reset_tokens_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX password_reset_tokens_hash_uniq ON password_reset_tokens (token_hash);
CREATE INDEX password_reset_tokens_active_by_user_idx ON password_reset_tokens (user_id) WHERE consumed_at IS NULL;
CREATE INDEX password_reset_tokens_expires_at_idx ON password_reset_tokens (expires_at);

COMMENT ON TABLE password_reset_tokens IS
    'Single-use password reset tokens. The raw value is only ever emailed; the database stores its hash and a 30 minute expiry.';
