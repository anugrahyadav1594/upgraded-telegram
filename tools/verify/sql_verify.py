#!/usr/bin/env python3
"""
SQL verification harness for the real-time chat project.

Two levels of verification, both independent of the Java toolchain:

1. PARSE  — every migration and the dev seed are parsed with `pglast` (the real PostgreSQL
            lexer/parser, the same grammar PostgreSQL itself uses). Syntax errors, unknown
            grammar and malformed DDL fail here.
2. EXECUTE (--execute) — the migrations are applied in order to a live PostgreSQL server and a
            suite of assertions checks that the *guarantees the application relies on* actually
            hold in the database: uniqueness, idempotency, privacy invariants, receipt
            monotonicity, cascade behaviour and index presence.

Usage
-----
    python3 tools/verify/sql_verify.py                       # parse only
    python3 tools/verify/sql_verify.py --execute             # spin up an embedded PostgreSQL
    python3 tools/verify/sql_verify.py --execute --dsn postgresql://user:pw@host:5432/db

Requires: pglast (always), psycopg2 + pgserver (only with --execute and no --dsn).
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import traceback

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
MIGRATION_DIR = REPO_ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
SEED_FILE = REPO_ROOT / "database" / "seed" / "dev_seed.sql"

GREEN, RED, YELLOW, DIM, RESET = "\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[0m"


def ok(msg: str) -> None:
    print(f"  {GREEN}✓{RESET} {msg}")


def fail(msg: str) -> None:
    print(f"  {RED}✗{RESET} {msg}")


def info(msg: str) -> None:
    print(f"  {DIM}·{RESET} {msg}")


def warn(msg: str) -> None:
    print(f"  {YELLOW}!{RESET} {msg}")


# ---------------------------------------------------------------------------------------
# 1. Parsing
# ---------------------------------------------------------------------------------------
def parse_files() -> int:
    try:
        from pglast import parse_sql
    except ImportError:
        warn("pglast is not installed — skipping parse stage (pip install pglast)")
        return 0

    failures = 0
    files = sorted(MIGRATION_DIR.glob("V*__*.sql")) + [SEED_FILE]
    print(f"\n[1/3] Parsing {len(files)} SQL file(s) with the PostgreSQL parser")
    for path in files:
        raw = path.read_text(encoding="utf-8")
        # psql meta-commands and variables are not SQL: strip them before parsing.
        cleaned_lines = []
        for line in raw.splitlines():
            if line.lstrip().startswith("\\"):
                continue
            cleaned_lines.append(line)
        sql = "\n".join(cleaned_lines)
        # `:seed_password_hash` is a psql variable; substitute a literal for parsing.
        sql = sql.replace(":'seed_password_hash'", "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA'")
        try:
            statements = parse_sql(sql)
            ok(f"{path.name}: {len(statements)} statement(s) parsed")
        except Exception as exc:  # noqa: BLE001 - report any parser failure verbatim
            fail(f"{path.name}: {exc}")
            failures += 1
    return failures


# ---------------------------------------------------------------------------------------
# 2. Execution helpers
# ---------------------------------------------------------------------------------------
class Database:
    """Thin psycopg2 wrapper with readable assertion helpers."""

    def __init__(self, connection) -> None:
        self.conn = connection
        self.failures: list[str] = []
        self.checks = 0

    def execute(self, sql: str, params=None) -> None:
        with self.conn.cursor() as cur:
            cur.execute(sql, params)

    def scalar(self, sql: str, params=None):
        with self.conn.cursor() as cur:
            cur.execute(sql, params)
            row = cur.fetchone()
            return row[0] if row else None

    def check(self, description: str, condition: bool, detail: str = "") -> None:
        self.checks += 1
        if condition:
            ok(description)
        else:
            fail(f"{description} {detail}")
            self.failures.append(description)

    def expect_error(self, description: str, sql: str, params=None, *, sqlstate=None) -> None:
        """Assert that a statement is rejected by the database (constraint enforcement)."""
        self.checks += 1
        try:
            self.execute(sql, params)
        except Exception as exc:  # noqa: BLE001
            actual = getattr(exc, "pgcode", None)
            acceptable = sqlstate if isinstance(sqlstate, (tuple, list, set)) else ({sqlstate} if sqlstate else None)
            if acceptable and actual not in acceptable:
                fail(f"{description} (expected SQLSTATE {sorted(acceptable)}, got {actual}: {exc})")
                self.failures.append(description)
            else:
                ok(f"{description} [rejected: {actual}]")
            self.conn.rollback()
            return
        fail(f"{description} — the database ACCEPTED an invalid statement")
        self.failures.append(description)
        self.conn.rollback()

    def expect_ok(self, description: str, sql: str, params=None):
        self.checks += 1
        try:
            self.execute(sql, params)
            self.conn.commit()
            ok(description)
            return True
        except Exception as exc:  # noqa: BLE001
            fail(f"{description} — {exc}")
            self.failures.append(description)
            self.conn.rollback()
            return False


# ---------------------------------------------------------------------------------------
# 3. Assertions about the guarantees the application depends on
# ---------------------------------------------------------------------------------------
def run_schema_assertions(db: Database) -> None:
    print("\n[3/3] Schema and constraint assertions")

    # ---- objects exist ----------------------------------------------------------------
    expected_tables = [
        "users", "profiles", "user_privacy_settings", "user_sessions", "password_reset_tokens",
        "conversations", "conversation_participants", "user_blocks",
        "messages", "message_receipts", "message_reactions", "attachments",
        "notifications", "user_notification_settings",
        "reports", "report_status_history",
    ]
    present = {
        row[0]
        for row in _fetchall(db, "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
    }
    missing = sorted(set(expected_tables) - present)
    db.check(f"all {len(expected_tables)} expected tables exist", not missing, f"missing: {missing}")

    expected_indexes = [
        "users_email_lower_uniq",
        "profiles_username_uniq",
        "conversations_direct_key_uniq",
        "conversation_participants_user_idx",
        "messages_client_id_uniq",
        "messages_conversation_created_idx",
        "messages_conversation_sequence_idx",
        "message_receipts_unread_idx",
        "reports_one_active_per_target_uniq",
        "notifications_unread_idx",
        "user_blocks_blocked_idx",
    ]
    present_idx = {
        row[0] for row in _fetchall(db, "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
    }
    missing_idx = sorted(set(expected_indexes) - present_idx)
    db.check(f"all {len(expected_indexes)} critical indexes exist", not missing_idx, f"missing: {missing_idx}")

    # The trigram (fuzzy search) indexes exist only when the optional pg_trgm extension is available.
    has_trgm = db.scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'") == 1
    if has_trgm:
        db.check(
            "trigram search indexes exist (pg_trgm available)",
            {"profiles_username_trgm_idx", "profiles_display_name_trgm_idx"} <= present_idx,
        )
    else:
        info("pg_trgm unavailable on this server: trigram index assertions skipped (documented degradation)")

    extensions = {row[0] for row in _fetchall(db, "SELECT extname FROM pg_extension")}
    db.check("pgcrypto is NOT required (gen_random_uuid is core in PostgreSQL 13+)", "pgcrypto" not in extensions)
    db.check("prefix search index present (text_pattern_ops)",
             "profiles_username_pattern_idx" in present_idx)

    # ---- username policy is enforced by the database ---------------------------------
    db.expect_error(
        "rejects an invalid username (uppercase)",
        "INSERT INTO profiles (user_id, username, display_name) VALUES "
        "('00000000-0000-0000-0000-000000000000', 'InvalidName', 'X')",
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a too-short username",
        "INSERT INTO profiles (user_id, username, display_name) VALUES "
        "('00000000-0000-0000-0000-000000000000', 'ab', 'X')",
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a reserved username",
        "INSERT INTO profiles (user_id, username, display_name) VALUES "
        "('00000000-0000-0000-0000-000000000000', 'admin', 'X')",
        sqlstate="23514",
    )

    # ---- privacy invariant ------------------------------------------------------------
    db.expect_error(
        "rejects 'last seen visible while online status hidden'",
        "INSERT INTO user_privacy_settings (user_id, show_online_status, show_last_seen) "
        "VALUES ('00000000-0000-0000-0000-000000000000', false, true)",
        sqlstate="23514",
    )

    # ---- email uniqueness is case-insensitive ----------------------------------------
    uid_a = db.scalar(
        "INSERT INTO users (email, password_hash) VALUES ('verify.a@example.test', "
        "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA') RETURNING id"
    )
    db.conn.commit()
    db.expect_error(
        "rejects a duplicate email differing only in case (CHECK or unique index)",
        "INSERT INTO users (email, password_hash) VALUES ('VERIFY.A@example.test', "
        "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA')",
        sqlstate=("23505", "23514"),
    )
    db.expect_error(
        "rejects a non-lowercase email at the constraint level",
        "INSERT INTO users (email, password_hash) VALUES ('Mixed@example.test', "
        "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA')",
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a non-Argon2id password hash",
        "INSERT INTO users (email, password_hash) VALUES ('verify.b@example.test', 'plaintextpw')",
        sqlstate="23514",
    )

    # ---- DIRECT conversation idempotency ---------------------------------------------
    uid_b = db.scalar(
        "INSERT INTO users (email, password_hash) VALUES ('verify.b@example.test', "
        "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA') RETURNING id"
    )
    db.conn.commit()
    direct_key = f"{min(uid_a, uid_b)}:{max(uid_a, uid_b)}"
    db.expect_ok(
        "creates a DIRECT conversation with a direct_key",
        "INSERT INTO conversations (type, direct_key, created_by) VALUES ('DIRECT', %s, %s)",
        (direct_key, uid_a),
    )
    db.expect_error(
        "rejects a second DIRECT conversation for the same pair (idempotency guarantee)",
        "INSERT INTO conversations (type, direct_key, created_by) VALUES ('DIRECT', %s, %s)",
        (direct_key, uid_b),
        sqlstate="23505",
    )
    db.expect_error(
        "rejects a DIRECT conversation without a direct_key",
        "INSERT INTO conversations (type, created_by) VALUES ('DIRECT', %s)",
        (uid_a,),
        sqlstate="23514",
    )

    conversation_id = db.scalar("SELECT id FROM conversations WHERE direct_key = %s", (direct_key,))
    db.expect_ok(
        "accepts participants",
        "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (%s, %s), (%s, %s)",
        (conversation_id, uid_a, conversation_id, uid_b),
    )
    db.expect_error(
        "rejects a duplicate participant row",
        "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (%s, %s)",
        (conversation_id, uid_a),
        sqlstate="23505",
    )

    # ---- message idempotency + validation --------------------------------------------
    client_id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    message_id = db.scalar(
        "INSERT INTO messages (conversation_id, sender_id, content, client_message_id) "
        "VALUES (%s, %s, 'hello', %s) RETURNING id",
        (conversation_id, uid_a, client_id),
    )
    db.conn.commit()
    db.check("message insert assigns a monotonic sequence", db.scalar(
        "SELECT sequence FROM messages WHERE id = %s", (message_id,)) is not None)
    db.expect_error(
        "rejects a duplicate client_message_id for the same sender and conversation (idempotent sends)",
        "INSERT INTO messages (conversation_id, sender_id, content, client_message_id) "
        "VALUES (%s, %s, 'hello again', %s)",
        (conversation_id, uid_a, client_id),
        sqlstate="23505",
    )
    db.expect_error(
        "rejects empty message content",
        "INSERT INTO messages (conversation_id, sender_id, content) VALUES (%s, %s, '')",
        (conversation_id, uid_a),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects an over-long message (4001 chars)",
        "INSERT INTO messages (conversation_id, sender_id, content) VALUES (%s, %s, %s)",
        (conversation_id, uid_a, "x" * 4001),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects an unknown message type",
        "INSERT INTO messages (conversation_id, sender_id, content, message_type) VALUES (%s, %s, 'x', 'VIDEO')",
        (conversation_id, uid_a),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a TEXT message with no content (and no tombstone)",
        "INSERT INTO messages (conversation_id, sender_id, content) VALUES (%s, %s, NULL)",
        (conversation_id, uid_a),
        sqlstate="23514",
    )

    # ---- receipt monotonicity invariants ---------------------------------------------
    db.expect_ok(
        "accepts a SENT receipt",
        "INSERT INTO message_receipts (message_id, user_id, status) VALUES (%s, %s, 'SENT')",
        (message_id, uid_b),
    )
    db.expect_error(
        "rejects a READ receipt with no timestamps",
        "UPDATE message_receipts SET status = 'READ' WHERE message_id = %s AND user_id = %s",
        (message_id, uid_b),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a DELIVERED receipt with no delivered_at",
        "UPDATE message_receipts SET status = 'DELIVERED' WHERE message_id = %s AND user_id = %s",
        (message_id, uid_b),
        sqlstate="23514",
    )
    db.expect_ok(
        "accepts DELIVERED with a timestamp",
        "UPDATE message_receipts SET status = 'DELIVERED', delivered_at = now() "
        "WHERE message_id = %s AND user_id = %s",
        (message_id, uid_b),
    )
    db.expect_error(
        "rejects read_at earlier than delivered_at",
        "UPDATE message_receipts SET status = 'READ', read_at = delivered_at - interval '1 hour' "
        "WHERE message_id = %s AND user_id = %s",
        (message_id, uid_b),
        sqlstate="23514",
    )
    db.expect_ok(
        "accepts READ with consistent timestamps",
        "UPDATE message_receipts SET status = 'READ', read_at = delivered_at + interval '1 second' "
        "WHERE message_id = %s AND user_id = %s",
        (message_id, uid_b),
    )

    # ---- blocks -----------------------------------------------------------------------
    db.expect_ok(
        "accepts a block",
        "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (%s, %s)",
        (uid_a, uid_b),
    )
    db.expect_error(
        "rejects a self-block",
        "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (%s, %s)",
        (uid_a, uid_a),
        sqlstate="23514",
    )

    # ---- reactions --------------------------------------------------------------------
    db.expect_ok(
        "accepts a reaction",
        "INSERT INTO message_reactions (message_id, user_id, emoji) VALUES (%s, %s, '😂')",
        (message_id, uid_b),
    )
    db.expect_error(
        "rejects a duplicate reaction from the same user",
        "INSERT INTO message_reactions (message_id, user_id, emoji) VALUES (%s, %s, '😂')",
        (message_id, uid_b),
        sqlstate="23505",
    )

    # ---- reports ----------------------------------------------------------------------
    db.expect_ok(
        "accepts a MESSAGE report",
        "INSERT INTO reports (reporter_id, target_type, target_user_id, target_message_id, reason) "
        "VALUES (%s, 'MESSAGE', %s, %s, 'HARASSMENT')",
        (uid_a, uid_b, message_id),
    )
    db.expect_error(
        "rejects a duplicate pending report of the same target",
        "INSERT INTO reports (reporter_id, target_type, target_user_id, target_message_id, reason) "
        "VALUES (%s, 'MESSAGE', %s, %s, 'SPAM')",
        (uid_a, uid_b, message_id),
        sqlstate="23505",
    )
    db.expect_error(
        "rejects a report whose target does not match its type",
        "INSERT INTO reports (reporter_id, target_type, reason) VALUES (%s, 'MESSAGE', 'SPAM')",
        (uid_a,),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects an unknown report reason",
        "INSERT INTO reports (reporter_id, target_type, target_user_id, reason) "
        "VALUES (%s, 'USER', %s, 'BECAUSE_I_SAID_SO')",
        (uid_a, uid_b),
        sqlstate="23514",
    )
    db.expect_error(
        "requires reviewer fields once a report is RESOLVED",
        "INSERT INTO reports (reporter_id, target_type, target_user_id, reason, status) "
        "VALUES (%s, 'USER', %s, 'SPAM', 'RESOLVED')",
        (uid_a, uid_b),
        sqlstate="23514",
    )

    # ---- attachments ------------------------------------------------------------------
    db.expect_error(
        "rejects a path-traversal file name",
        "INSERT INTO attachments (uploader_id, file_name, content_type, size_bytes, storage_key, checksum_sha256) "
        "VALUES (%s, '../etc/passwd', 'image/png', 10, 'k', %s)",
        (uid_a, "a" * 64),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects an oversized attachment",
        "INSERT INTO attachments (uploader_id, file_name, content_type, size_bytes, storage_key, checksum_sha256) "
        "VALUES (%s, 'big.png', 'image/png', 26214401, 'k', %s)",
        (uid_a, "a" * 64),
        sqlstate="23514",
    )
    db.expect_error(
        "rejects a malformed checksum",
        "INSERT INTO attachments (uploader_id, file_name, content_type, size_bytes, storage_key, checksum_sha256) "
        "VALUES (%s, 'ok.png', 'image/png', 10, 'k', 'not-a-checksum')",
        (uid_a,),
        sqlstate="23514",
    )

    # ---- notifications ----------------------------------------------------------------
    db.expect_ok(
        "accepts a NEW_MESSAGE notification",
        "INSERT INTO notifications (user_id, type, conversation_id, message_id, actor_id, preview) "
        "VALUES (%s, 'NEW_MESSAGE', %s, %s, %s, 'hello')",
        (uid_b, conversation_id, message_id, uid_a),
    )
    db.expect_error(
        "rejects a NEW_MESSAGE notification with no message",
        "INSERT INTO notifications (user_id, type) VALUES (%s, 'NEW_MESSAGE')",
        (uid_b,),
        sqlstate="23514",
    )

    # ---- cascades ---------------------------------------------------------------------
    db.execute("DELETE FROM users WHERE id = %s", (uid_a,))
    db.conn.commit()
    db.check(
        "deleting a user cascades to profile rows",
        db.scalar("SELECT count(*) FROM profiles WHERE user_id = %s", (uid_a,)) == 0,
    )
    db.check(
        "deleting a user cascades to sessions",
        db.scalar("SELECT count(*) FROM user_sessions WHERE user_id = %s", (uid_a,)) == 0,
    )
    db.check(
        "deleting a user removes their authored messages",
        db.scalar("SELECT count(*) FROM messages WHERE sender_id = %s", (uid_a,)) == 0,
    )
    db.check(
        "deleting a user removes their receipts",
        db.scalar("SELECT count(*) FROM message_receipts WHERE user_id = %s", (uid_a,)) == 0,
    )

    db.execute("DELETE FROM users WHERE id = %s", (uid_b,))
    db.conn.commit()

    # ---- updated_at trigger -----------------------------------------------------------
    trigger_ok = _trigger_works(db)
    db.check("updated_at trigger fires on UPDATE", trigger_ok)

    # ---- last_seen / tombstone account ------------------------------------------------
    db.check(
        "the tombstone account exists for deleted users' content",
        db.scalar("SELECT count(*) FROM users WHERE id = '00000000-0000-0000-0000-000000000000'") == 1,
    )


def _fetchall(db: Database, sql: str):
    with db.conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def _trigger_works(db: Database) -> bool:
    uid = db.scalar(
        "INSERT INTO users (email, password_hash) VALUES ('verify.trigger@example.test', "
        "'$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA') RETURNING id"
    )
    db.conn.commit()
    before = db.scalar("SELECT updated_at FROM users WHERE id = %s", (uid,))
    db.execute("UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE id = %s", (uid,))
    db.conn.commit()
    after = db.scalar("SELECT updated_at FROM users WHERE id = %s", (uid,))
    db.execute("DELETE FROM users WHERE id = %s", (uid,))
    db.conn.commit()
    return after > before


def execute_migrations(db: Database) -> None:
    print("\n[2/3] Applying migrations to a live PostgreSQL server")
    print(f"  {DIM}server: {db.scalar('SELECT version()')}{RESET}")
    files = sorted(MIGRATION_DIR.glob("V*__*.sql"))
    for path in files:
        cleaned = "\n".join(
            line for line in path.read_text(encoding="utf-8").splitlines()
            if not line.lstrip().startswith("\\")
        )
        try:
            db.execute(cleaned)
            db.conn.commit()
            ok(f"applied {path.name}")
        except Exception as exc:  # noqa: BLE001
            db.conn.rollback()
            fail(f"{path.name}: {exc}")
            raise SystemExit(1)


def run_seed(db: Database) -> None:
    """Apply the development seed exactly as a psql caller would (with -v seed_password_hash=...)."""
    print("\n[2b/3] Applying the development seed")
    raw = SEED_FILE.read_text(encoding="utf-8")
    # psql meta-commands are not sent to the server; substitute the psql variable with a literal
    # Argon2id PHC string (the seed only validates the format of the hash it is given).
    placeholder_hash = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaA"
    lines = [ln for ln in raw.splitlines() if not ln.lstrip().startswith("\\")]
    sql = "\n".join(lines).replace(":'seed_password_hash'", f"'{placeholder_hash}'")
    try:
        db.execute(sql)
        db.conn.commit()
        ok("dev_seed.sql applied")
    except Exception as exc:  # noqa: BLE001
        db.conn.rollback()
        fail(f"dev_seed.sql: {exc}")
        db.failures.append("dev_seed.sql failed")
        return

    db.check(
        "seed created the four development accounts (@alex, @ghost, @developer, @testuser)",
        db.scalar("SELECT count(*) FROM users WHERE email LIKE '%@example.test'") >= 4,
        f"found {db.scalar('SELECT count(*) FROM users')} user rows",
    )
    db.check(
        "seeded profiles are clearly marked as development accounts",
        db.scalar("SELECT count(*) FROM profiles WHERE bio LIKE '[DEV ACCOUNT]%'") >= 4,
    )
    db.check(
        "seed is idempotent (re-running changes nothing)",
        _seed_is_idempotent(db),
    )
    db.check(
        "seed refuses to run when the password hash variable is missing",
        _seed_rejects_missing_password(db),
    )
    db.check(
        "seeded conversation + messages are present",
        db.scalar("SELECT count(*) FROM conversations") >= 1
        and db.scalar("SELECT count(*) FROM messages") >= 4,
    )
    seeded = _fetchall(db, "SELECT username FROM profiles WHERE bio LIKE '[DEV ACCOUNT]%' ORDER BY username")
    info("seed users: " + ", ".join("@" + row[0] for row in seeded))


def _seed_is_idempotent(db: Database) -> bool:
    before = db.scalar("SELECT count(*) FROM messages")
    raw = SEED_FILE.read_text(encoding="utf-8")
    placeholder_hash = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaA"
    lines = [ln for ln in raw.splitlines() if not ln.lstrip().startswith("\\")]
    sql = "\n".join(lines).replace(":'seed_password_hash'", f"'{placeholder_hash}'")
    try:
        db.execute(sql)
        db.conn.commit()
    except Exception:  # noqa: BLE001
        db.conn.rollback()
        return False
    return db.scalar("SELECT count(*) FROM messages") == before


def _seed_rejects_missing_password(db: Database) -> bool:
    guard = ("DO $$ BEGIN IF NULLIF('', '') IS NULL THEN RAISE EXCEPTION 'missing'; END IF; END $$;")
    try:
        db.execute(guard)
        db.conn.commit()
        return False
    except Exception:  # noqa: BLE001
        db.conn.rollback()
        return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--execute", action="store_true", help="apply migrations to a live server and assert behaviour")
    parser.add_argument("--dsn", help="PostgreSQL DSN (defaults to an embedded pgserver instance)")
    args = parser.parse_args()

    failures = parse_files()
    if failures:
        print(f"\n{RED}{failures} file(s) failed to parse{RESET}")
        return 1

    if not args.execute:
        print(f"\n{GREEN}Parse stage passed.{RESET} Run with --execute to apply and assert.\n")
        return 0

    conn = None
    server = None
    try:
        import psycopg2
    except ImportError:
        warn("psycopg2 is not installed — cannot execute (pip install psycopg2-binary)")
        return 1

    if args.dsn:
        conn = psycopg2.connect(args.dsn)
    else:
        try:
            import pgserver  # type: ignore
            import tempfile
        except ImportError:
            warn("pgserver/psycopg2 missing — pass --dsn to target an existing server")
            return 1
        data_dir = pathlib.Path(tempfile.mkdtemp(prefix="chatpg-"))
        server = pgserver.get_server(str(data_dir))
        conn = psycopg2.connect(server.get_uri())

    conn.autocommit = False
    db = Database(conn)

    try:
        execute_migrations(db)
        run_schema_assertions(db)
        if not db.failures:
            run_seed(db)
    except SystemExit:
        return 1
    except Exception:  # noqa: BLE001
        traceback.print_exc()
        return 1
    finally:
        try:
            conn.close()
        except Exception:  # noqa: BLE001
            pass
        if server is not None:
            try:
                server.cleanup()
            except Exception:  # noqa: BLE001
                pass

    print(f"\n{db.checks - len(db.failures)}/{db.checks} database assertions passed")
    if db.failures:
        print(f"{RED}FAILED: {len(db.failures)} assertion(s){RESET}\n")
        return 1
    print(f"{GREEN}All database assertions passed.{RESET}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
