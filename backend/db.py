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
 created_s BIGINT NOT NULL, updated_s BIGINT NOT NULL,
 priority TEXT NOT NULL DEFAULT 'normal', first_reply_s BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cm_site_ticket_messages (
 id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL REFERENCES cm_site_tickets(id) ON DELETE CASCADE,
 actor_id TEXT NOT NULL, actor_name TEXT NOT NULL, actor_role TEXT NOT NULL,
 body TEXT NOT NULL, created_s BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS cm_site_ticket_messages_ticket_created ON cm_site_ticket_messages(ticket_id,created_s,id);
CREATE TABLE IF NOT EXISTS cm_site_notifications (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, href TEXT NOT NULL DEFAULT '',
 created_s BIGINT NOT NULL, read_s BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS cm_site_notifications_user_created ON cm_site_notifications(user_id,created_s DESC);
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
);
CREATE TABLE IF NOT EXISTS cm_site_analytics_events (
 id TEXT PRIMARY KEY, event_type TEXT NOT NULL, page TEXT NOT NULL,
 visitor_hash TEXT NOT NULL, item_id TEXT NOT NULL DEFAULT '', quantity INTEGER NOT NULL DEFAULT 0,
 created_s BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS cm_site_analytics_created ON cm_site_analytics_events(created_s);
CREATE INDEX IF NOT EXISTS cm_site_analytics_page_created ON cm_site_analytics_events(event_type,page,created_s);
CREATE INDEX IF NOT EXISTS cm_site_analytics_visitor_created ON cm_site_analytics_events(visitor_hash,created_s)
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
            if self.sqlite:
                columns = {row["name"] for row in db.execute("PRAGMA table_info(cm_site_notifications)").fetchall()}
                if "href" not in columns:
                    db.execute("ALTER TABLE cm_site_notifications ADD COLUMN href TEXT NOT NULL DEFAULT ''")
                ticket_columns = {row["name"] for row in db.execute("PRAGMA table_info(cm_site_tickets)").fetchall()}
                if "priority" not in ticket_columns:
                    db.execute("ALTER TABLE cm_site_tickets ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'")
                if "first_reply_s" not in ticket_columns:
                    db.execute("ALTER TABLE cm_site_tickets ADD COLUMN first_reply_s BIGINT NOT NULL DEFAULT 0")
            else:
                db.execute("ALTER TABLE cm_site_notifications ADD COLUMN IF NOT EXISTS href TEXT NOT NULL DEFAULT ''")
                db.execute("ALTER TABLE cm_site_tickets ADD COLUMN IF NOT EXISTS priority TEXT NOT NULL DEFAULT 'normal'")
                db.execute("ALTER TABLE cm_site_tickets ADD COLUMN IF NOT EXISTS first_reply_s BIGINT NOT NULL DEFAULT 0")
            db.execute("UPDATE cm_site_ticket_messages SET created_s=created_s*1000 WHERE created_s<1000000000000")
            legacy_tickets = db.execute("SELECT t.*,u.name AS actor_name FROM cm_site_tickets t JOIN cm_site_users u ON u.id=t.user_id ORDER BY t.created_s").fetchall()
            for ticket in legacy_tickets:
                if not db.execute("SELECT id FROM cm_site_ticket_messages WHERE ticket_id=%s LIMIT 1", (ticket["id"],)).fetchone():
                    first = str(ticket["body"] or "").strip()
                    if first:
                        db.execute("INSERT INTO cm_site_ticket_messages(id,ticket_id,actor_id,actor_name,actor_role,body,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s)", ("initial-" + ticket["id"], ticket["id"], ticket["user_id"], ticket["actor_name"], "player", first, ticket["created_s"] * 1000))
                if ticket["reply"] and not db.execute("SELECT id FROM cm_site_ticket_messages WHERE ticket_id=%s AND actor_role<>'player' LIMIT 1", (ticket["id"],)).fetchone():
                    reply_at = ticket["first_reply_s"] or ticket["updated_s"]
                    db.execute("INSERT INTO cm_site_ticket_messages(id,ticket_id,actor_id,actor_name,actor_role,body,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s)", ("legacy-reply-" + ticket["id"], ticket["id"], "system", "Команда", "owner", ticket["reply"], reply_at * 1000))
                    db.execute("UPDATE cm_site_tickets SET first_reply_s=%s WHERE id=%s AND first_reply_s=0", (reply_at, ticket["id"]))

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
