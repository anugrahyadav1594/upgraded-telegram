-- =====================================================================================
-- V1 · Extensions, shared functions and global sequences
-- =====================================================================================
-- Every following migration depends on this one. It is intentionally small: extensions
-- and the generic helpers that keep invariants (updated_at, ordering tokens) in the
-- database instead of hoping every code path remembers them.
-- =====================================================================================

-- IMPORTANT (portability): gen_random_uuid() is part of core PostgreSQL since 13, so pgcrypto is
-- deliberately NOT required. This keeps the schema installable on managed/minimal PostgreSQL
-- builds that do not ship contrib modules.
--
-- pg_trgm (also contrib) is used for fuzzy username/display-name search. It is enabled when the
-- server provides it and skipped with a notice otherwise; search then falls back to
-- prefix/substring matching without a trigram index. The full-text/blob-free access paths,
-- uniqueness constraints and all integrity guarantees of this schema work in either case.
-- See V7__search_indexes.sql for the conditional index creation and
-- docs/database-design.md#indexes for the documented degradation.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_trgm') THEN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
        RAISE NOTICE 'pg_trgm enabled: fuzzy username/display-name search indexes will be created (V7).';
    ELSE
        RAISE NOTICE 'pg_trgm is not available on this server: search falls back to prefix/substring matching (no trigram index).';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- updated_at maintenance
-- -------------------------------------------------------------------------------------
-- A single trigger function, attached to every mutable table, so that application code
-- cannot forget to bump updated_at (and so audit reads are trustworthy).
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION set_updated_at() IS
    'Trigger function: sets NEW.updated_at = now(). Attached to all mutable tables.';

-- -------------------------------------------------------------------------------------
-- Global message ordering token
-- -------------------------------------------------------------------------------------
-- messages.sequence is a globally monotonic value (not per conversation). Clients use it as
-- a resynchronisation watermark: "give me every message of this conversation with
-- sequence > <lastKnownSequence>". A single shared sequence avoids the row-lock contention of
-- a per-conversation counter while preserving the property that matters (monotonicity), because
-- gaps caused by other conversations are meaningless for a conversation-scoped "greater than" query.
CREATE SEQUENCE IF NOT EXISTS messages_sequence
    AS bigint
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

COMMENT ON SEQUENCE messages_sequence IS
    'Monotonic ordering token for messages.sequence; used for resync watermarks.';

-- -------------------------------------------------------------------------------------
-- Reserved identifiers
-- -------------------------------------------------------------------------------------
-- Usernames that must never be claimable: operational roles, product names and the
-- tombstone account used when a user deletes their account.
CREATE OR REPLACE FUNCTION is_reserved_username(candidate text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT lower(candidate) = ANY (ARRAY[
        'admin', 'administrator', 'moderator', 'support', 'help', 'security',
        'system', 'root', 'official', 'staff', 'team', 'billing', 'abuse',
        'realtimechat', 'realchat', 'deleted_account', 'deleted', 'anonymous',
        'me', 'you', 'null', 'undefined'
    ]);
$$;

COMMENT ON FUNCTION is_reserved_username(text) IS
    'True when a username is reserved and must not be claimable by a user.';
