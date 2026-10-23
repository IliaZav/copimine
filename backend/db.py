from contextlib import contextmanager
from pathlib import Path
import json
import sqlite3
import threading
import time
import uuid

SCHEMA = """
CREATE TABLE IF NOT EXISTS cm_site_users (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, name_norm TEXT NOT NULL UNIQUE,
 display_name TEXT NOT NULL, email TEXT NOT NULL DEFAULT '', email_norm TEXT UNIQUE,
 password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'player', enabled INTEGER NOT NULL DEFAULT 1,
 email_verified INTEGER NOT NULL DEFAULT 0, minecraft_uuid TEXT NOT NULL DEFAULT '',
 minecraft_name TEXT NOT NULL DEFAULT '', created_s BIGINT NOT NULL, updated_s BIGINT NOT NULL,
 last_login_s BIGINT NOT NULL DEFAULT 0, totp_secret TEXT NOT NULL DEFAULT '',
 pending_totp TEXT NOT NULL DEFAULT '', blocked_reason TEXT NOT NULL DEFAULT '',
 blocked_until_s BIGINT NOT NULL DEFAULT 0, vk_id TEXT UNIQUE, preferences TEXT NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS cm_site_sessions (
 token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES cm_site_users(id),
 csrf TEXT NOT NULL, created_s BIGINT NOT NULL, expires_s BIGINT NOT NULL,
 revoked_s BIGINT NOT NULL DEFAULT 0, reauth_s BIGINT NOT NULL DEFAULT 0,
 ip TEXT NOT NULL DEFAULT '', user_agent TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS cm_site_sessions_user ON cm_site_sessions(user_id, expires_s);
CREATE TABLE IF NOT EXISTS cm_site_challenges (
 id TEXT PRIMARY KEY, purpose TEXT NOT NULL, email TEXT NOT NULL, user_id TEXT,
 code_hash TEXT NOT NULL, payload TEXT NOT NULL DEFAULT '{}', created_s BIGINT NOT NULL,
 expires_s BIGINT NOT NULL, consumed_s BIGINT NOT NULL DEFAULT 0, attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cm_site_mail_outbox (
 id TEXT PRIMARY KEY, recipient TEXT NOT NULL, subject TEXT NOT NULL, body TEXT NOT NULL,
 created_s BIGINT NOT NULL, expires_s BIGINT NOT NULL, sent_s BIGINT NOT NULL DEFAULT 0,
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_s BIGINT NOT NULL DEFAULT 0,
 last_error TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS cm_site_attempts (
 key TEXT PRIMARY KEY, times TEXT NOT NULL DEFAULT '[]'
);
CREATE TABLE IF NOT EXISTS cm_site_settings (
 key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_s BIGINT NOT NULL, actor TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS cm_site_audit (
 id TEXT PRIMARY KEY, actor_id TEXT NOT NULL, actor_name TEXT NOT NULL,
 action TEXT NOT NULL, target TEXT NOT NULL DEFAULT '', reason TEXT NOT NULL DEFAULT '',
 details TEXT NOT NULL DEFAULT '{}', created_s BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS cm_site_audit_created ON cm_site_audit(created_s);
CREATE TABLE IF NOT EXISTS cm_site_tickets (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES cm_site_users(id),
 title TEXT NOT NULL, body TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'support',
 status TEXT NOT NULL DEFAULT 'open', reply TEXT NOT NULL DEFAULT '',
 created_s BIGINT NOT NULL, updated_s BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS cm_site_notifications (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL,
 created_s BIGINT NOT NULL, read_s BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cm_site_receipts (
 actor_id TEXT NOT NULL, key TEXT NOT NULL, operation TEXT NOT NULL, fingerprint TEXT NOT NULL,
 status TEXT NOT NULL, result TEXT NOT NULL DEFAULT '{}', created_s BIGINT NOT NULL,
 PRIMARY KEY(actor_id, key)
);
CREATE TABLE IF NOT EXISTS cm_site_oauth (
 state_hash TEXT PRIMARY KEY, verifier TEXT NOT NULL, nonce TEXT NOT NULL,
 session_hash TEXT NOT NULL, user_id TEXT, created_s BIGINT NOT NULL, consumed_s BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cm_site_map_jobs (
 id TEXT PRIMARY KEY, actor_id TEXT NOT NULL, actor_name TEXT NOT NULL, reason TEXT NOT NULL,
 request_key TEXT NOT NULL, status TEXT NOT NULL, created_s BIGINT NOT NULL,
 updated_s BIGINT NOT NULL, error TEXT NOT NULL DEFAULT '', result TEXT NOT NULL DEFAULT '{}',
 UNIQUE(actor_id, request_key)
);
CREATE TABLE IF NOT EXISTS cm_site_backup_codes (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES cm_site_users(id), code_hash TEXT NOT NULL,
 consumed_s BIGINT NOT NULL DEFAULT 0
)
"""


class Connection:
    def __init__(self, connection, sqlite):
        self.connection, self.sqlite = connection, sqlite

    def execute(self, sql, parameters=()):
        if self.sqlite:
            sql = sql.replace("%s", "?").replace(" FOR UPDATE", "")
        return self.connection.execute(sql, parameters)


class Database:
    def __init__(self, url):
        self.url = url
        self.sqlite = url.startswith("sqlite:///")
        self.lock = threading.RLock()
        if self.sqlite:
            self.path = url.removeprefix("sqlite:///")
            Path(self.path).parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def transaction(self):
        with self.lock if self.sqlite else _noop():
            if self.sqlite:
                raw = sqlite3.connect(self.path, timeout=15, isolation_level=None)
                raw.row_factory = sqlite3.Row
                raw.execute("PRAGMA foreign_keys=ON")
                raw.execute("BEGIN IMMEDIATE")
            else:
                import psycopg
                from psycopg.rows import dict_row
                raw = psycopg.connect(self.url, row_factory=dict_row, connect_timeout=5)
                raw.execute("SET LOCAL lock_timeout = '5s'")
                raw.execute("SET LOCAL statement_timeout = '15s'")
            try:
                yield Connection(raw, self.sqlite)
                raw.commit()
            except BaseException:
                raw.rollback()
                raise
            finally:
                raw.close()

    def initialize(self):
        with self.transaction() as db:
            for sql in SCHEMA.split(";"):
                if sql.strip():
                    db.execute(sql)

    def setting(self, key, default=None):
        with self.transaction() as db:
            row = db.execute("SELECT value FROM cm_site_settings WHERE key=%s", (key,)).fetchone()
        return json.loads(row["value"]) if row else default

    def set_setting(self, key, value, actor):
        with self.transaction() as db:
            db.execute("INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor", (key, json.dumps(value, ensure_ascii=False), int(time.time()), actor))

    def audit(self, actor, action, target="", reason="", details=None):
        with self.transaction() as db:
            db.execute("INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)", (uuid.uuid4().hex, actor.get("id", "system"), actor.get("name", "system"), action, target, reason, json.dumps(details or {}, ensure_ascii=False), int(time.time())))


@contextmanager
def _noop():
    yield
