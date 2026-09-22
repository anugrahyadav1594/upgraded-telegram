-- =====================================================================================
-- V6 · Moderation: reports and their audit trail
-- =====================================================================================
-- Reports are evidence: rows are never hard-deleted, every status change is appended to
-- report_status_history, and the reported user is never told about the report.
-- =====================================================================================

CREATE TABLE reports (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type        text        NOT NULL,
    target_user_id     uuid        REFERENCES users (id) ON DELETE SET NULL,
    target_message_id  uuid        REFERENCES messages (id) ON DELETE SET NULL,
    reason             text        NOT NULL,
    description        text,
    status             text        NOT NULL DEFAULT 'OPEN',
    resolution_note    text,
    reviewed_by        uuid        REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at        timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT reports_target_type_valid CHECK (target_type IN ('USER', 'MESSAGE')),
    CONSTRAINT reports_status_valid CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT reports_reason_valid CHECK (reason IN (
        'SPAM', 'HARASSMENT', 'HATE_SPEECH', 'IMPERSONATION',
        'SEXUAL_CONTENT', 'VIOLENCE', 'SELF_HARM', 'OTHER'
    )),
    CONSTRAINT reports_description_length CHECK (description IS NULL OR char_length(description) <= 1000),
    CONSTRAINT reports_resolution_note_length CHECK (resolution_note IS NULL OR char_length(resolution_note) <= 1000),
    -- The target must match the declared type.
    CONSTRAINT reports_target_consistent CHECK (
        (target_type = 'USER' AND target_user_id IS NOT NULL)
        OR (target_type = 'MESSAGE' AND target_message_id IS NOT NULL)
    ),
    -- A reviewed report must record who reviewed it and when.
    CONSTRAINT reports_review_fields CHECK (
        status IN ('OPEN', 'REVIEWING') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

-- Prevents report-spamming the same target while an earlier report is still pending.
-- COALESCE (rather than NULLS NOT DISTINCT) keeps this portable across PostgreSQL 14+.
CREATE UNIQUE INDEX reports_one_active_per_target_uniq
    ON reports (
        reporter_id,
        target_type,
        COALESCE(target_user_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(target_message_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE status IN ('OPEN', 'REVIEWING');

-- Moderation queue: "oldest open reports first".
CREATE INDEX reports_status_created_idx ON reports (status, created_at);

-- Per-target history ("how often has this account been reported?").
CREATE INDEX reports_target_user_idx ON reports (target_user_id, created_at DESC)
    WHERE target_user_id IS NOT NULL;

CREATE INDEX reports_target_message_idx ON reports (target_message_id)
    WHERE target_message_id IS NOT NULL;

CREATE TRIGGER reports_set_updated_at
    BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE reports IS
    'User/message reports with a moderation workflow. Never deleted (evidence); the reporter is anonymised when their account is deleted.';

-- -------------------------------------------------------------------------------------
-- report_status_history — append-only audit trail
-- -------------------------------------------------------------------------------------
CREATE TABLE report_status_history (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id   uuid        NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    from_status text,
    to_status   text        NOT NULL,
    actor_id    uuid        REFERENCES users (id) ON DELETE SET NULL,
    note        text,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT report_status_history_to_status_valid
        CHECK (to_status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT report_status_history_from_status_valid
        CHECK (from_status IS NULL OR from_status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT report_status_history_note_length CHECK (note IS NULL OR char_length(note) <= 1000)
);

CREATE INDEX report_status_history_report_idx ON report_status_history (report_id, created_at);

COMMENT ON TABLE report_status_history IS
    'Append-only audit trail of moderation decisions: who changed a report, from what to what, when and why.';
