-- =====================================================================================
-- V7 · Search indexes
-- =====================================================================================
-- Username and display-name search (GET /api/v1/users/search) has two modes:
--
--   * prefix match   — "gh" matches "@ghost"        → served by the text_pattern_ops indexes (V2)
--   * fuzzy/substring — "hos" matches "@ghost"       → requires pg_trgm trigram indexes
--
-- pg_trgm ships in PostgreSQL's contrib package, which some managed/minimal builds omit.
-- The indexes are therefore created only when the extension is actually installed. The query
-- in ProfileSearchRepository is written so that both cases stay correct, only slower when the
-- trigram indexes are absent — no schema or behaviour change is required to re-run this
-- migration later (it is idempotent and index creation is guarded).
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        -- Trigram similarity support for substring/fuzzy username + display-name search.
        CREATE INDEX IF NOT EXISTS profiles_username_trgm_idx
            ON profiles USING gin (username gin_trgm_ops);

        CREATE INDEX IF NOT EXISTS profiles_display_name_trgm_idx
            ON profiles USING gin (display_name gin_trgm_ops);

        RAISE NOTICE 'V7: trigram search indexes created.';
    ELSE
        RAISE NOTICE 'V7: pg_trgm not installed — skipping trigram search indexes (prefix search index is still active).';
    END IF;
END $$;
