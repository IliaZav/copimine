"""SQLite transactions serialize money, limits, idempotency, and audit."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import time
import uuid

def now_ms():
    return int(time.time() * 1000)

def identifier():
    return str(uuid.uuid4())

def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)

class Connection(sqlite3.Connection):
    def __exit__(self, *args):
        try:
            return super().__exit__(*args)
        finally:
            self.close()

SCHEMA = """
CREATE TABLE IF NOT EXISTS users(
 id TEXT PRIMARY KEY, name TEXT UNIQUE COLLATE NOCASE NOT NULL, email TEXT UNIQUE COLLATE NOCASE NOT NULL,
 password_hash TEXT NOT NULL, role TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
 auth_epoch INTEGER NOT NULL DEFAULT 0, ar INTEGER NOT NULL DEFAULT 0 CHECK(ar>=0),
 cm INTEGER NOT NULL DEFAULT 0 CHECK(cm>=0), game_uuid TEXT UNIQUE, game_name TEXT,
 game_link_verified INTEGER NOT NULL DEFAULT 0, totp_secret TEXT, totp_last_step INTEGER NOT NULL DEFAULT -1,
 created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS sessions(
 token_hash TEXT PRIMARY KEY, user_id TEXT REFERENCES users(id), csrf TEXT NOT NULL,
 created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, mfa_at INTEGER, auth_epoch INTEGER,
 revoked_at INTEGER);
CREATE TABLE IF NOT EXISTS pending_totp(user_id TEXT PRIMARY KEY REFERENCES users(id), secret TEXT NOT NULL, expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS rate_events(key TEXT NOT NULL, at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS rate_index ON rate_events(key,at);
CREATE TABLE IF NOT EXISTS catalog(
 id TEXT PRIMARY KEY,payload TEXT NOT NULL,price INTEGER NOT NULL CHECK(price>=0),currency TEXT NOT NULL,
 visibility TEXT NOT NULL,enabled INTEGER NOT NULL,per_player_limit INTEGER NOT NULL DEFAULT 0,
 supply_limit INTEGER NOT NULL DEFAULT 0,version INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS audit(
 sequence INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL,actor_id TEXT,kind TEXT NOT NULL,
 target_id TEXT,reason TEXT NOT NULL,payload TEXT NOT NULL,previous_hash TEXT NOT NULL,entry_hash TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS actions(
 id TEXT PRIMARY KEY,actor_id TEXT NOT NULL,target_id TEXT NOT NULL,action TEXT NOT NULL,params TEXT NOT NULL,
 reason TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
 idempotency_key TEXT NOT NULL,request_hash TEXT NOT NULL,order_id TEXT,ack_id TEXT UNIQUE,ack TEXT,
 attempts INTEGER NOT NULL DEFAULT 0,lease_until INTEGER,UNIQUE(actor_id,idempotency_key));
CREATE TABLE IF NOT EXISTS sanctions(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,kind TEXT NOT NULL,channel TEXT,reason TEXT NOT NULL,
 actor_id TEXT NOT NULL,created_at INTEGER NOT NULL,expires_at INTEGER,status TEXT NOT NULL,
 action_id TEXT NOT NULL,revoke_action_id TEXT,revoked_at INTEGER,revoke_reason TEXT);
CREATE TABLE IF NOT EXISTS inventory_snapshots(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,captured_at INTEGER NOT NULL,source TEXT NOT NULL,
 inventory TEXT NOT NULL,ender TEXT NOT NULL,profile TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS snapshot_user ON inventory_snapshots(user_id,captured_at DESC);
CREATE TABLE IF NOT EXISTS orders(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,currency TEXT NOT NULL,total INTEGER NOT NULL,
 status TEXT NOT NULL,created_at INTEGER NOT NULL,sequence INTEGER NOT NULL UNIQUE,
 idempotency_key TEXT NOT NULL,request_hash TEXT NOT NULL,UNIQUE(user_id,idempotency_key));
CREATE TABLE IF NOT EXISTS order_items(
 order_id TEXT NOT NULL REFERENCES orders(id),item_id TEXT NOT NULL,quantity INTEGER NOT NULL,price INTEGER NOT NULL,name TEXT NOT NULL,
 PRIMARY KEY(order_id,item_id));
CREATE TABLE IF NOT EXISTS purchase_policies(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,item_id TEXT,maximum INTEGER NOT NULL,period TEXT NOT NULL,
 expires_at INTEGER,reset_sequence INTEGER NOT NULL DEFAULT 0,enabled INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS ledger(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,currency TEXT NOT NULL,amount INTEGER NOT NULL,balance_after INTEGER NOT NULL,
 kind TEXT NOT NULL,reference_id TEXT,actor_id TEXT,reason TEXT NOT NULL,created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS payments(
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL,provider_reference TEXT UNIQUE,amount INTEGER NOT NULL,currency TEXT NOT NULL,
 status TEXT NOT NULL,created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS analytics(day TEXT NOT NULL,event TEXT NOT NULL,path TEXT NOT NULL,count INTEGER NOT NULL,PRIMARY KEY(day,event,path));
CREATE TABLE IF NOT EXISTS analytics_visitors(day TEXT NOT NULL,visitor_hash TEXT NOT NULL,PRIMARY KEY(day,visitor_hash));
CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,kind TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL,
 updated_at INTEGER NOT NULL,payload TEXT NOT NULL,error TEXT);
CREATE TABLE IF NOT EXISTS map_publications(id TEXT PRIMARY KEY,captured_at INTEGER NOT NULL,published_at INTEGER NOT NULL,
 manifest TEXT NOT NULL,directory TEXT NOT NULL,policy_version INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS adapter_nonces(nonce TEXT PRIMARY KEY,at INTEGER NOT NULL);
"""

class Store:
    def __init__(self, data_dir, site_dir):
        self.directory = Path(data_dir).resolve()
        self.site = Path(site_dir).resolve()
        if self.directory == self.site or self.site in self.directory.parents:
            raise ValueError("The state directory must be outside the public site directory.")
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.path = self.directory / "copimine.sqlite3"
        with self.connect() as conn:
            conn.executescript(SCHEMA)
        try:
            os.chmod(self.directory, 0o700)
            os.chmod(self.path, 0o600)
        except OSError:
            pass

    def connect(self):
        conn = sqlite3.connect(self.path, timeout=15, isolation_level=None, factory=Connection)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=FULL")
        conn.execute("PRAGMA busy_timeout=15000")
        return conn

    @contextmanager
    def transaction(self):
        conn = self.connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.commit()
        except BaseException:
            conn.rollback()
            raise
        finally:
            conn.close()

    def setting(self, key, default=None, conn=None):
        if conn is None:
            with self.connect() as db:
                return self.setting(key, default, db)
        row = conn.execute("SELECT value FROM settings WHERE key=?", (key,)).fetchone()
        return json.loads(row["value"]) if row else default

    def set_setting(self, conn, key, value):
        conn.execute("INSERT INTO settings VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, encoded(value)))

    def audit(self, conn, actor, kind, target, reason, payload):
        previous = conn.execute("SELECT entry_hash FROM audit ORDER BY sequence DESC LIMIT 1").fetchone()
        previous_hash = previous[0] if previous else "0" * 64
        event = {"at":now_ms(), "actor":actor, "kind":kind, "target":target, "reason":reason, "payload":payload}
        entry_hash = hashlib.sha256((previous_hash + encoded(event)).encode()).hexdigest()
        conn.execute("INSERT INTO audit(at,actor_id,kind,target_id,reason,payload,previous_hash,entry_hash) VALUES(?,?,?,?,?,?,?,?)",
            (event["at"], actor, kind, target, reason, encoded(payload), previous_hash, entry_hash))

    def rate(self, key, maximum, window_ms):
        at = now_ms()
        with self.transaction() as conn:
            count = conn.execute("SELECT count(*) FROM rate_events WHERE key=? AND at>?", (key,at-window_ms)).fetchone()[0]
            conn.execute("DELETE FROM rate_events WHERE at<?", (at-86400000,))
            if count >= maximum:
                return False
            conn.execute("INSERT INTO rate_events VALUES(?,?)", (key,at))
        return True
