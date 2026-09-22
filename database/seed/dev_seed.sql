-- =====================================================================================
-- DEVELOPMENT SEED DATA — NEVER RUN IN PRODUCTION
-- =====================================================================================
-- Creates four clearly-fake development accounts so a reviewer can exercise the product
-- without registering by hand:
--
--     @alex       Alex Rivera    — chats a lot
--     @ghost      Ghost          — the pseudonymous-privacy demo account (avatar colour only)
--     @developer  Sam Dev        — technical account
--     @testuser   Test User      — deliberately boring, used by test suites
--
-- Every seeded profile bio starts with "[DEV ACCOUNT]" and every email address uses the
-- reserved `.test` TLD, so seeded rows can never be mistaken for real users.
--
-- PASSWORDS ARE NOT HARD-CODED HERE. The script requires the caller to supply an Argon2id
-- PHC hash for the shared development password:
--
--     HASH="$(./mvnw -q -pl backend spring-boot:run \
--              -Dspring-boot.run.arguments="--app.mode=hash-password --app.password=your-dev-password" \
--              | tail -1)"                     # or tools/generate_dev_password_hash.py
--
--     psql "$DATABASE_URL" -v seed_password_hash="$HASH" -f dev_seed.sql
--
-- The script aborts when the variable is missing or empty, and refuses to run against a
-- database that already contains real (non-seed) accounts.
-- =====================================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------------------------
-- Guard rails
-- ---------------------------------------------------------------------------------------
DO $$
DECLARE
    provided_hash text := :'seed_password_hash';
    real_accounts integer;
BEGIN
    IF provided_hash IS NULL OR btrim(provided_hash) = '' OR provided_hash = ':seed_password_hash' THEN
        RAISE EXCEPTION
            'dev_seed.sql requires -v seed_password_hash=<argon2id PHC string> (see the header of this file)';
    END IF;

    IF provided_hash NOT LIKE '$argon2id$%' THEN
        RAISE EXCEPTION 'seed_password_hash must be an Argon2id PHC string starting with $argon2id$';
    END IF;

    SELECT count(*) INTO real_accounts
    FROM users
    WHERE email NOT LIKE '%@example.test'
      AND id <> '00000000-0000-0000-0000-000000000000';

    IF real_accounts > 0 THEN
        RAISE EXCEPTION
            'refusing to seed: this database contains % non-seed account(s). Seed data is development-only.',
            real_accounts;
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------------------
BEGIN;

-- Fixed UUIDs keep the seed idempotent and make the fixtures easy to reference in tests.
CREATE TEMP TABLE seed_users (
    id       uuid PRIMARY KEY,
    email    text NOT NULL,
    username text NOT NULL,
    display  text NOT NULL,
    bio      text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_users (id, email, username, display, bio) VALUES
    ('11111111-1111-4111-8111-111111111111', 'alex@example.test',      'alex',      'Alex Rivera',
     '[DEV ACCOUNT] Product designer. Talks about typography more than is healthy.'),
    ('22222222-2222-4222-8222-222222222222', 'ghost@example.test',     'ghost',     'Ghost',
     '[DEV ACCOUNT] Pseudonymous test account. No avatar, no email, no problem.'),
    ('33333333-3333-4333-8333-333333333333', 'developer@example.test', 'developer', 'Sam Dev',
     '[DEV ACCOUNT] Backend developer. Will happily discuss indexes at length.'),
    ('44444444-4444-4444-8444-444444444444', 'testuser@example.test',  'testuser',  'Test User',
     '[DEV ACCOUNT] Automated-test fixture account.');

INSERT INTO users (id, email, password_hash, status, email_verified)
SELECT id, email, :'seed_password_hash', 'ACTIVE', true
FROM seed_users
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (user_id, username, display_name, bio, username_public, profile_completed_at)
SELECT id, username, display, bio, true, now()
FROM seed_users
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_privacy_settings (user_id, show_online_status, show_last_seen, show_profile_in_search, allow_messages_from)
SELECT id, true, true, true, 'EVERYONE'
FROM seed_users
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_notification_settings (user_id)
SELECT id FROM seed_users
ON CONFLICT (user_id) DO NOTHING;

-- @ghost demonstrates the privacy switches in their "restricted" configuration.
UPDATE user_privacy_settings
SET show_online_status = false,
    show_last_seen = false,
    allow_messages_from = 'CONTACTS'
WHERE user_id = '22222222-2222-4222-8222-222222222222';

-- ---------------------------------------------------------------------------------------
-- One conversation with a short, realistic history (alex ↔ ghost)
-- ---------------------------------------------------------------------------------------
INSERT INTO conversations (id, type, direct_key, created_by, last_activity_at)
VALUES (
    '55555555-5555-4555-8555-555555555555',
    'DIRECT',
    '11111111-1111-4111-8111-111111111111:22222222-2222-4222-8222-222222222222',
    '11111111-1111-4111-8111-111111111111',
    now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO conversation_participants (conversation_id, user_id)
VALUES
    ('55555555-5555-4555-8555-555555555555', '11111111-1111-4111-8111-111111111111'),
    ('55555555-5555-4555-8555-555555555555', '22222222-2222-4222-8222-222222222222')
ON CONFLICT DO NOTHING;

INSERT INTO messages (id, conversation_id, sender_id, content, client_message_id, created_at)
VALUES
    ('66666666-6666-4666-8666-666666666661', '55555555-5555-4555-8555-555555555555',
     '11111111-1111-4111-8111-111111111111', 'Hey! Is this the pseudonymous chat project?',
     '77777777-7777-4777-8777-777777777771', now() - interval '12 minutes'),
    ('66666666-6666-4666-8666-666666666662', '55555555-5555-4555-8555-555555555555',
     '22222222-2222-4222-8222-222222222222', 'It is. No phone number required.',
     '77777777-7777-4777-8777-777777777772', now() - interval '11 minutes'),
    ('66666666-6666-4666-8666-666666666663', '55555555-5555-4555-8555-555555555555',
     '11111111-1111-4111-8111-111111111111', 'Nice. Does it survive a page refresh?',
     '77777777-7777-4777-8777-777777777773', now() - interval '10 minutes'),
    ('66666666-6666-4666-8666-666666666664', '55555555-5555-4555-8555-555555555555',
     '22222222-2222-4222-8222-222222222222', 'Everything is in PostgreSQL — refresh away.',
     '77777777-7777-4777-8777-777777777774', now() - interval '9 minutes')
ON CONFLICT (id) DO NOTHING;

-- Receipts for the two messages sent by @alex (recipient @ghost): one read, one delivered.
INSERT INTO message_receipts (message_id, user_id, status, delivered_at, read_at)
VALUES
    ('66666666-6666-4666-8666-666666666661', '22222222-2222-4222-8222-222222222222',
     'READ', now() - interval '11 minutes', now() - interval '10 minutes'),
    ('66666666-6666-4666-8666-666666666663', '22222222-2222-4222-8222-222222222222',
     'DELIVERED', now() - interval '10 minutes', NULL)
ON CONFLICT DO NOTHING;

-- @ghost's own messages need receipts for @alex (who has read them).
INSERT INTO message_receipts (message_id, user_id, status, delivered_at, read_at)
VALUES
    ('66666666-6666-4666-8666-666666666662', '11111111-1111-4111-8111-111111111111',
     'READ', now() - interval '11 minutes', now() - interval '10 minutes'),
    ('66666666-6666-4666-8666-666666666664', '11111111-1111-4111-8111-111111111111',
     'DELIVERED', now() - interval '9 minutes', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO message_reactions (message_id, user_id, emoji)
VALUES ('66666666-6666-4666-8666-666666666662', '11111111-1111-4111-8111-111111111111', '😂')
ON CONFLICT DO NOTHING;

-- @alex has read up to the third message.
UPDATE conversation_participants
SET last_read_message_id = '66666666-6666-4666-8666-666666666663',
    last_read_at = now() - interval '10 minutes'
WHERE conversation_id = '55555555-5555-4555-8555-555555555555'
  AND user_id = '11111111-1111-4111-8111-111111111111';

UPDATE conversations
SET last_message_id = '66666666-6666-4666-8666-666666666664',
    last_activity_at = now() - interval '9 minutes'
WHERE id = '55555555-5555-4555-8555-555555555555';

COMMIT;

-- ---------------------------------------------------------------------------------------
-- Report
-- ---------------------------------------------------------------------------------------
SELECT
    (SELECT count(*) FROM users WHERE email LIKE '%@example.test')   AS seeded_accounts,
    (SELECT count(*) FROM conversations)                            AS conversations,
    (SELECT count(*) FROM messages)                                 AS messages,
    (SELECT count(*) FROM message_receipts)                         AS receipts;

\echo 'Development seed complete. Passwords use the Argon2id hash supplied via -v seed_password_hash.'
\echo 'These accounts are DEVELOPMENT ONLY and must never exist in a production database.'
