from __future__ import annotations

import asyncio
import atexit
import hashlib
import json
import os
import re
import socket
import subprocess
import time
import uuid
from pathlib import Path
from typing import Any

from .envfile import load_env_file_to_os, resolve_env_file

try:
    import fcntl
except Exception:
    fcntl = None

try:
    import psycopg
    from psycopg.rows import dict_row
except Exception as exc:
    print(f"psycopg is required for CopiMine PostgreSQL storage: {type(exc).__name__}: {exc}", flush=True)
    raise

APP_ROOT = Path("/opt/copimine/admin-web")
ENV_FILE = resolve_env_file(APP_ROOT / ".env")
STATE_FILE = APP_ROOT / "data" / "discord_bot_state.json"
LOCK_FILE = APP_ROOT / "data" / "discord_bot.lock"
DISCORD_BOT_STATE_KEY = "discord_bot_runtime_state"
_BOT_LOCK_HANDLE = None

DEFAULT_STATUS_CHANNEL_ID = "1501623987468370161"

load_env_file_to_os(ENV_FILE)

TOKEN = os.getenv("DISCORD_BOT_TOKEN", "").strip()
APP_CH = os.getenv("DISCORD_APPLICATIONS_CHANNEL_ID", "").strip()
APP_ADMIN_CH = os.getenv("DISCORD_APPLICATIONS_ADMIN_CHANNEL_ID", "").strip() or APP_CH
REPORT_CH = os.getenv("DISCORD_REPORTS_CHANNEL_ID", "").strip()
REPORT_ADMIN_CH = os.getenv("DISCORD_REPORTS_ADMIN_CHANNEL_ID", "").strip() or REPORT_CH
WHITELIST_CH = os.getenv("DISCORD_WHITELIST_CHANNEL_ID", "1499841407400284271").strip()
ADMIN_ROLE_ID = os.getenv("DISCORD_ADMIN_ROLE_ID", "").strip()
ADMIN_ALLOWLIST = {x.strip() for x in os.getenv("DISCORD_ADMIN_ALLOWLIST", "").split(",") if x.strip()}
WHITELIST_APPROVER_ROLE_IDS = {
    value.strip()
    for value in os.getenv("DISCORD_WHITELIST_APPROVER_ROLE_IDS", "").split(",")
    if value.strip().isdigit()
}
# Kept as an explicitly empty compatibility marker for older deployments.  Role
# names are intentionally never evaluated: Discord names are mutable and the
# approval gate below accepts only numeric role IDs (or the explicit admin
# allowlist/administrator permission).
WHITELIST_APPROVER_ROLE_NAMES: set[str] = set()
STATUS_CHANNEL_ID = os.getenv("DISCORD_SERVER_STATUS_CHANNEL_ID", "").strip() or DEFAULT_STATUS_CHANNEL_ID
ELECTIONS_STATUS_CHANNEL_ID = os.getenv("DISCORD_ELECTIONS_STATUS_CHANNEL_ID", "").strip()
ADMIN_ALERTS_CHANNEL_ID = os.getenv("DISCORD_ADMIN_ALERTS_CHANNEL_ID", "").strip() or APP_ADMIN_CH
PUBLIC_PANEL_URL = os.getenv("PUBLIC_PANEL_URL", os.getenv("ADMIN_WEB_PUBLIC_URL", "")).strip()
ELECTION_DIGEST_V2 = "station-ballot-application-digest"
PUBLIC_ELECTION_SHOWCASE_V3 = "candidates-applications-only"
ALLOW_LEGACY_ELECTION_FALLBACK = False

MC_HOST = os.getenv("MC_HOST", "127.0.0.1").strip() or "127.0.0.1"
MC_PORT = int(os.getenv("MC_PORT", "25565") or "25565")
MINECRAFT_SERVICE = os.getenv("MINECRAFT_SERVICE", "copimine-minecraft").strip() or "copimine-minecraft"
MC_SERVER_DIR = Path("/opt/copimine/minecraft/server")

POLL_SECONDS = max(10.0, float(os.getenv("DISCORD_BOT_POLL_SECONDS", "15") or "15"))
STATUS_EDIT_TIMEOUT_SECONDS = 20
STATUS_ONLINE_CONFIRM_SECONDS = max(10, int(os.getenv("DISCORD_STATUS_ONLINE_CONFIRM_SECONDS", "15") or "15"))
DISCORD_MESSAGE_HISTORY_ADOPT_LIMIT = max(20, min(120, int(os.getenv("DISCORD_MESSAGE_HISTORY_ADOPT_LIMIT", "80") or "80")))
CHANNEL_RENAME_MIN_SECONDS = max(300, int(os.getenv("DISCORD_CHANNEL_RENAME_MIN_SECONDS", "600") or "600"))
STATUS_OFFLINE_CONFIRM_SECONDS = max(90, int(os.getenv("DISCORD_STATUS_OFFLINE_CONFIRM_SECONDS", "180") or "180"))
DISCORD_RECONNECT_INITIAL_SECONDS = max(60, int(os.getenv("DISCORD_RECONNECT_INITIAL_SECONDS", "300") or "300"))
DISCORD_RECONNECT_MAX_SECONDS = max(DISCORD_RECONNECT_INITIAL_SECONDS, int(os.getenv("DISCORD_RECONNECT_MAX_SECONDS", "3600") or "3600"))

POSTGRES_HOST = os.getenv("POSTGRES_HOST", os.getenv("PGHOST", "127.0.0.1")).strip() or "127.0.0.1"
POSTGRES_PORT = int(os.getenv("POSTGRES_PORT", os.getenv("PGPORT", "5432")) or "5432")
POSTGRES_DB = os.getenv("POSTGRES_DB", os.getenv("PGDATABASE", "copimine")).strip() or "copimine"
POSTGRES_USER = os.getenv("POSTGRES_USER", os.getenv("PGUSER", "copimine")).strip() or "copimine"
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", os.getenv("PGPASSWORD", "")).strip()
POSTGRES_SCHEMA = os.getenv("POSTGRES_SCHEMA", os.getenv("PGSCHEMA", "copimine")).strip() or "copimine"
POSTGRES_CONNECT_TIMEOUT = int(os.getenv("POSTGRES_CONNECT_TIMEOUT", "5") or "5")

def pg_ident(value: str) -> str:
    return '"' + str(value).replace('"', '""') + '"'

def pg_connect():
    if not POSTGRES_PASSWORD:
        raise RuntimeError("POSTGRES_PASSWORD is required in /opt/copimine/admin-web/.env")
    con = psycopg.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        dbname=POSTGRES_DB,
        user=POSTGRES_USER,
        password=POSTGRES_PASSWORD,
        connect_timeout=POSTGRES_CONNECT_TIMEOUT,
        row_factory=dict_row,
    )
    con.execute(f"CREATE SCHEMA IF NOT EXISTS {pg_ident(POSTGRES_SCHEMA)}")
    con.execute(f"SET search_path TO {pg_ident(POSTGRES_SCHEMA)}, public")
    con.execute("SET statement_timeout = '5s'")
    con.execute("SET lock_timeout = '2s'")
    return con

def pg_sql(sql: str) -> str:
    text = str(sql)
    inserted_ignore = bool(re.search(r"\bINSERT\s+OR\s+IGNORE\s+INTO\b", text, re.I))
    text = re.sub(r"\bINSERT\s+OR\s+IGNORE\s+INTO\b", "INSERT INTO", text, flags=re.I)
    text = re.sub(r"\browid\b", "id", text, flags=re.I)
    text = re.sub(r"\bCOLLATE\s+NOCASE\b", "", text, flags=re.I)
    text = text.replace("?", "%s")
    if inserted_ignore and "ON CONFLICT" not in text.upper():
        text = text.rstrip().rstrip(";") + " ON CONFLICT DO NOTHING"
    return text

def acquire_single_instance_lock() -> bool:
    global _BOT_LOCK_HANDLE
    if fcntl is None:
        return True
    LOCK_FILE.parent.mkdir(parents=True, exist_ok=True)
    handle = open(LOCK_FILE, "a+", encoding="utf-8")
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        handle.close()
        return False
    handle.seek(0)
    handle.truncate()
    handle.write(str(os.getpid()))
    handle.flush()
    _BOT_LOCK_HANDLE = handle
    atexit.register(release_single_instance_lock)
    return True

def release_single_instance_lock() -> None:
    global _BOT_LOCK_HANDLE
    handle = _BOT_LOCK_HANDLE
    if handle is None:
        return
    try:
        if fcntl is not None:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    except Exception:
        pass
    try:
        handle.close()
    except Exception:
        pass
    _BOT_LOCK_HANDLE = None

class PgCompatConnection:
    def __init__(self) -> None:
        self._con = pg_connect()

    def execute(self, sql: str, args: tuple[Any, ...] | list[Any] = ()):
        return self._con.execute(pg_sql(sql), args)

    def commit(self) -> None:
        self._con.commit()

    def rollback(self) -> None:
        self._con.rollback()

    def close(self) -> None:
        self._con.close()

    def __enter__(self) -> "PgCompatConnection":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        try:
            if exc_type is None:
                self.commit()
            else:
                self.rollback()
        finally:
            self.close()
        return False

def db() -> PgCompatConnection:
    return PgCompatConnection()

def now_ms() -> int:
    return int(time.time() * 1000)


def read_json_file(path: Path, default: Any) -> Any:
    try:
        if not path.exists():
            return default
        return json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return default


def write_json_file(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)

def load_state() -> dict[str, Any]:
    try:
        raw = json.loads(STATE_FILE.read_text(encoding="utf-8"))
        if not isinstance(raw, dict):
            raw = {}
    except Exception:
        raw = {}

    raw.setdefault("applications", [])
    raw.setdefault("reports", [])
    raw.setdefault("app_messages", {})
    raw.setdefault("public_app_messages", {})
    raw.setdefault("report_messages", {})
    raw.setdefault("whitelist_messages", {})
    raw.setdefault("server_status_channel", {})
    raw.setdefault("elections_status_channel", {})
    raw.setdefault("admin_alerts_channel", {})

    if not isinstance(raw.get("applications"), list):
        raw["applications"] = []
    if not isinstance(raw.get("reports"), list):
        raw["reports"] = []
    if not isinstance(raw.get("app_messages"), dict):
        raw["app_messages"] = {}
    if not isinstance(raw.get("public_app_messages"), dict):
        raw["public_app_messages"] = {}
    if not isinstance(raw.get("report_messages"), dict):
        raw["report_messages"] = {}
    if not isinstance(raw.get("whitelist_messages"), dict):
        raw["whitelist_messages"] = {}
    if not isinstance(raw.get("server_status_channel"), dict):
        raw["server_status_channel"] = {}
    if not isinstance(raw.get("elections_status_channel"), dict):
        raw["elections_status_channel"] = {}
    if not isinstance(raw.get("admin_alerts_channel"), dict):
        raw["admin_alerts_channel"] = {}

    return raw

def normalize_state(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raw = {}
    raw.setdefault("applications", [])
    raw.setdefault("reports", [])
    raw.setdefault("app_messages", {})
    raw.setdefault("public_app_messages", {})
    raw.setdefault("report_messages", {})
    raw.setdefault("whitelist_messages", {})
    raw.setdefault("server_status_channel", {})
    raw.setdefault("elections_status_channel", {})
    raw.setdefault("admin_alerts_channel", {})

    if not isinstance(raw.get("applications"), list):
        raw["applications"] = []
    if not isinstance(raw.get("reports"), list):
        raw["reports"] = []
    if not isinstance(raw.get("app_messages"), dict):
        raw["app_messages"] = {}
    if not isinstance(raw.get("public_app_messages"), dict):
        raw["public_app_messages"] = {}
    if not isinstance(raw.get("report_messages"), dict):
        raw["report_messages"] = {}
    if not isinstance(raw.get("whitelist_messages"), dict):
        raw["whitelist_messages"] = {}
    if not isinstance(raw.get("server_status_channel"), dict):
        raw["server_status_channel"] = {}
    if not isinstance(raw.get("elections_status_channel"), dict):
        raw["elections_status_channel"] = {}
    if not isinstance(raw.get("admin_alerts_channel"), dict):
        raw["admin_alerts_channel"] = {}
    return raw

def save_state(state: dict[str, Any]) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(STATE_FILE)

def read_state_from_pg() -> dict[str, Any]:
    with db() as con:
        if not table_exists(con, "discord_status_state"):
            return {}
        row = con.execute("SELECT value FROM discord_status_state WHERE key=?", (DISCORD_BOT_STATE_KEY,)).fetchone()
    if not row:
        return {}
    try:
        return normalize_state(json.loads(str(row["value"] or "")))
    except Exception:
        return {}

def log_bridge_event(source: str, event_type: str, details: dict[str, Any] | None = None) -> None:
    try:
        with db() as con:
            if table_exists(con, "bridge_events"):
                con.execute(
                    "INSERT INTO bridge_events(source,event_type,created_at,details) VALUES(?,?,?,?)",
                    (source, event_type, now_ms(), json.dumps(details or {}, ensure_ascii=False, sort_keys=True)),
                )
    except Exception:
        pass

def log_notification(channel_id: str, object_type: str, object_id: str, status: str) -> None:
    try:
        with db() as con:
            if table_exists(con, "discord_notifications_log"):
                con.execute(
                    "INSERT INTO discord_notifications_log(channel_id,object_type,object_id,sent_at,status) VALUES(?,?,?,?,?)",
                    (str(channel_id or ""), str(object_type or ""), str(object_id or ""), now_ms(), str(status or "")),
                )
    except Exception:
        pass

def save_status_snapshot(channel_id: str, payload: dict[str, Any]) -> None:
    try:
        with db() as con:
            if table_exists(con, "status_channel_snapshots"):
                con.execute(
                    "INSERT INTO status_channel_snapshots(channel_id,created_at,payload) VALUES(?,?,?)",
                    (str(channel_id or ""), now_ms(), json.dumps(payload or {}, ensure_ascii=False, sort_keys=True)),
                )
    except Exception:
        pass

def save_state_sync(state: dict[str, Any]) -> None:
    normalized = normalize_state(state)
    save_state(normalized)
    try:
        with db() as con:
            if table_exists(con, "discord_status_state"):
                con.execute(
                    """
                    INSERT INTO discord_status_state(key,value,updated_at)
                    VALUES(?,?,?)
                    ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at
                    """,
                    (DISCORD_BOT_STATE_KEY, json.dumps(normalized, ensure_ascii=False, sort_keys=True), now_ms()),
                )
    except Exception:
        pass

def load_runtime_state() -> dict[str, Any]:
    try:
        state = read_state_from_pg()
        if state:
            return state
    except Exception:
        pass
    return normalize_state(load_state())

def short(s: Any, n: int = 1000) -> str:
    text = str(s or "").strip()
    if not text:
        return "—"
    return text[: max(1, n - 1)] + "…" if len(text) > n else text

def ts(ms: Any) -> str:
    try:
        sec = int(ms) // 1000
        if sec <= 0:
            return "—"
        return f"<t:{sec}:f>"
    except Exception:
        return "—"

def norm_status(value: Any) -> str:
    text = str(value or "").strip().upper()
    if not text:
        return "PENDING"
    aliases = {
        "APPROVE": "APPROVED",
        "ACCEPTED": "APPROVED",
        "OK": "APPROVED",
        "DENY": "DENIED",
        "REJECTED": "DENIED",
        "DECLINED": "DENIED",
        "DELETE": "DELETED",
        "REMOVED": "DELETED",
        "ARCHIVE": "ARCHIVED",
    }
    return aliases.get(text, text)

def is_final_status(status: str) -> bool:
    return norm_status(status) in {"APPROVED", "DENIED", "ARCHIVED", "DELETED", "REMOVED", "WITHDRAWN"}

def is_deleted_status(status: str) -> bool:
    return norm_status(status) in {"ARCHIVED", "DELETED", "REMOVED"}

def status_ru(status: Any) -> str:
    s = norm_status(status)
    return {
        "PENDING": "Ожидает решения",
        "APPROVED": "Одобрена",
        "DENIED": "Отклонена",
        "ARCHIVED": "Удалена / архив",
        "DELETED": "Удалена",
        "REMOVED": "Удалена",
        "WITHDRAWN": "Отозвана",
    }.get(s, s)

def status_color(status: Any) -> int:
    s = norm_status(status)
    if s == "PENDING":
        return 0xFEE75C
    if s == "APPROVED":
        return 0x57F287
    if s == "DENIED":
        return 0xED4245
    if is_deleted_status(s):
        return 0x2B2D31
    return 0x99AAB5

def is_admin(member: Any) -> bool:
    if member is None:
        return False
    uid = str(getattr(member, "id", ""))
    if uid in ADMIN_ALLOWLIST:
        return True
    if ADMIN_ROLE_ID.isdigit():
        rid = int(ADMIN_ROLE_ID)
        for role in getattr(member, "roles", []) or []:
            if getattr(role, "id", None) == rid:
                return True
    perms = getattr(member, "guild_permissions", None)
    return bool(getattr(perms, "administrator", False))


def can_approve_whitelist(member: Any) -> bool:
    if is_admin(member):
        return True
    for role in getattr(member, "roles", []) or []:
        role_id = str(getattr(role, "id", "") or "")
        if role_id in WHITELIST_APPROVER_ROLE_IDS:
            return True
    return False

try:
    import discord
except Exception as exc:
    print(f"discord.py не установлен или не импортируется: {type(exc).__name__}: {exc}", flush=True)
    raise

def row_to_dict(row: Any | dict[str, Any] | None) -> dict[str, Any]:
    if row is None:
        return {}
    if isinstance(row, dict):
        return dict(row)
    return {k: row[k] for k in row.keys()}

def get_col(row: Any | dict[str, Any], *names: str, default: Any = None) -> Any:
    d = row_to_dict(row)
    lower = {str(k).lower(): k for k in d.keys()}
    for name in names:
        key = lower.get(name.lower())
        if key is not None:
            return d.get(key)
    return default

def table_exists(con: PgCompatConnection, table: str) -> bool:
    row = con.execute(
        """
        SELECT COUNT(*) AS n
        FROM information_schema.tables
        WHERE table_schema=%s AND table_name=%s AND table_type IN ('BASE TABLE','VIEW')
        """,
        (POSTGRES_SCHEMA, table),
    ).fetchone()
    return bool(row and int(row["n"]) > 0)

def scalar(con: PgCompatConnection, sql: str, args: tuple[Any, ...] = ()) -> int:
    try:
        row = con.execute(sql, args).fetchone()
        if row is None:
            return 0
        value = next(iter(row.values())) if isinstance(row, dict) else row[0]
        return int(value or 0)
    except Exception:
        return 0

def election_overview_snapshot() -> dict[str, Any]:
    return election_overview_snapshot_v2()

def election_risk_lines(snap: dict[str, Any]) -> list[str]:
    lines: list[str] = []
    if snap.get("status") == "NO_ELECTION":
        return ["Выборный цикл не найден."]
    if int(snap.get("active_stations") or 0) <= 0:
        lines.append("Нет активных участков ЦИК.")
    if int(snap.get("candidates") or 0) <= 0:
        lines.append("Нет утверждённых кандидатов.")
    if int(snap.get("unused_ballots") or 0) <= 0 and int(snap.get("votes") or 0) <= 0:
        lines.append("Нет свободных бюллетеней.")
    if int(snap.get("pending_apps") or 0) > 0 and str(snap.get("status")) == "ACTIVE":
        lines.append(f"Неразобранные заявки: {snap.get('pending_apps')}.")
    if int(snap.get("station_deposits") or 0) < int(snap.get("votes") or 0):
        lines.append("Есть голоса без station_id, проверь старые записи.")
    return lines[:5] or ["Критичных рисков не найдено."]

# Legacy election tables remain documented only; runtime overview always comes from ElectionCore V2.
def election_overview_snapshot_v2() -> dict[str, Any]:
    with db() as con:
        if not table_exists(con, "elections"):
            return {
                "election_id": "",
                "status": "NO_ELECTION",
                "stage_title": "Нет выборов",
                "winner": "",
                "current_round": 1,
                "candidate_limit": 0,
                "president": "",
                "laws": [],
                "pending_apps": 0,
                "approved_apps": 0,
                "application_issues": 0,
                "unsigned_application_issues": 0,
                "candidates": 0,
                "ballots": 0,
                "unused_ballots": 0,
                "used_ballots": 0,
                "votes": 0,
                "active_stations": 0,
                "archived_stations": 0,
                "station_deposits": 0,
                "ar_total": 0,
                "active_checks": 0,
                "top_candidates": [],
                "top_stations": [],
                "generated_at": now_ms(),
            }

        election = con.execute(
            """
            SELECT *
            FROM elections
            ORDER BY CASE WHEN COALESCE(active,0)=1 THEN 0 ELSE 1 END,
                     COALESCE(updated_at, started_at, 0) DESC
            LIMIT 1
            """
        ).fetchone()
        if not election:
            return {
                "election_id": "",
                "status": "NO_ELECTION",
                "stage_title": "Нет выборов",
                "winner": "",
                "current_round": 1,
                "candidate_limit": 0,
                "president": "",
                "laws": [],
                "pending_apps": 0,
                "approved_apps": 0,
                "application_issues": 0,
                "unsigned_application_issues": 0,
                "candidates": 0,
                "ballots": 0,
                "unused_ballots": 0,
                "used_ballots": 0,
                "votes": 0,
                "active_stations": 0,
                "archived_stations": 0,
                "station_deposits": 0,
                "ar_total": 0,
                "active_checks": 0,
                "top_candidates": [],
                "top_stations": [],
                "generated_at": now_ms(),
            }

        election_id = str(get_col(election, "id", default="") or "")
        status = str(get_col(election, "current_stage", "status", default="NONE") or "NONE").upper()
        current_round = int(get_col(election, "current_round", default=1) or 1)
        candidate_limit = int(get_col(election, "candidate_limit", default=4) or 4)
        stage_title = {
            "NONE": "Нет выборов",
            "PREPARATION": "Подготовка",
            "APPLICATIONS": "Приём заявок",
            "REVIEW": "Проверка заявок",
            "DEBATES": "Дебаты",
            "VOTING": "Голосование",
            "COUNTING": "Подсчёт",
            "SECOND_ROUND": "Второй тур",
            "FINISHED": "Завершено",
            "PRESIDENT_TERM": "Президентский срок",
        }.get(status, status)

        president = ""
        if table_exists(con, "president_terms"):
            row = con.execute(
                """
                SELECT president_name
                FROM president_terms
                WHERE status='ACTIVE'
                ORDER BY started_at DESC
                LIMIT 1
                """
            ).fetchone()
            president = str(get_col(row, "president_name", default="") or "") if row else ""

        pending_apps = approved_apps = candidates = ballots = unused_ballots = used_ballots = votes = 0
        active_stations = archived_stations = station_deposits = application_issues = unsigned_application_issues = 0
        top_candidates: list[dict[str, Any]] = []
        published_laws: list[str] = []

        if election_id and table_exists(con, "candidate_applications"):
            pending_apps = scalar(con, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND admin_status='PENDING' AND COALESCE(submitted_at,0)>0", (election_id,))
            approved_apps = scalar(con, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND admin_status='APPROVED'", (election_id,))
            application_issues = scalar(con, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=?", (election_id,))
            unsigned_application_issues = scalar(con, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND status='ISSUED'", (election_id,))

        if election_id and table_exists(con, "candidates"):
            candidates = scalar(con, "SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(active,1)=1", (election_id,))
            if table_exists(con, "votes"):
                top_candidates = [
                    row_to_dict(r)
                    for r in con.execute(
                        """
                        SELECT c.player_uuid AS uuid,
                               c.player_name AS name,
                               COALESCE(COUNT(v.id),0) AS total
                        FROM candidates c
                        LEFT JOIN votes v
                          ON v.election_id=c.election_id
                         AND v.round_no=?
                         AND v.candidate_uuid=c.player_uuid
                        WHERE c.election_id=? AND COALESCE(c.active,1)=1
                        GROUP BY c.player_uuid, c.player_name, c.last_result
                        ORDER BY total DESC, c.player_name ASC
                        LIMIT 5
                        """,
                        (current_round, election_id),
                    ).fetchall()
                ]
            else:
                top_candidates = [
                    row_to_dict(r)
                    for r in con.execute(
                        """
                        SELECT player_uuid AS uuid, player_name AS name, COALESCE(last_result,0) AS total
                        FROM candidates
                        WHERE election_id=? AND COALESCE(active,1)=1
                        ORDER BY total DESC, player_name ASC
                        LIMIT 5
                        """,
                        (election_id,),
                    ).fetchall()
                ]

        if election_id and table_exists(con, "ballots"):
            ballots = scalar(con, "SELECT COUNT(*) FROM ballots WHERE election_id=?", (election_id,))
            unused_ballots = scalar(con, "SELECT COUNT(*) FROM ballots WHERE election_id=? AND status IN ('ISSUED','CONFIRMED')", (election_id,))
            used_ballots = scalar(con, "SELECT COUNT(*) FROM ballots WHERE election_id=? AND status='DEPOSITED'", (election_id,))

        if election_id and table_exists(con, "votes"):
            votes = scalar(con, "SELECT COUNT(*) FROM votes WHERE election_id=?", (election_id,))
            station_deposits = scalar(con, "SELECT COUNT(*) FROM votes WHERE election_id=? AND COALESCE(station_id,'')<>''", (election_id,))

        if election_id and table_exists(con, "polling_stations"):
            active_stations = scalar(con, "SELECT COUNT(*) FROM polling_stations WHERE election_id=? AND COALESCE(active,1)=1", (election_id,))
            archived_stations = scalar(con, "SELECT COUNT(*) FROM polling_stations WHERE election_id=? AND COALESCE(active,1)=0", (election_id,))

        if table_exists(con, "president_laws"):
            published_laws = [
                str(get_col(r, "text", default="") or "")
                for r in con.execute(
                    """
                    SELECT text
                    FROM president_laws
                    WHERE status='PUBLISHED'
                    ORDER BY slot_no ASC, published_at DESC
                    LIMIT 5
                    """
                ).fetchall()
                if str(get_col(r, "text", default="") or "").strip()
            ]

        return {
            "election_id": election_id,
            "status": status,
            "stage_title": stage_title,
            "winner": str(get_col(election, "manual_winner_name", "winner_name", default="") or ""),
            "current_round": current_round,
            "candidate_limit": candidate_limit,
            "president": president,
            "laws": published_laws,
            "pending_apps": pending_apps,
            "approved_apps": approved_apps,
            "application_issues": application_issues,
            "unsigned_application_issues": unsigned_application_issues,
            "candidates": candidates,
            "ballots": ballots,
            "unused_ballots": unused_ballots,
            "used_ballots": used_ballots,
            "votes": votes,
            "active_stations": active_stations,
            "archived_stations": archived_stations,
            "station_deposits": station_deposits,
            "ar_total": 0,
            "active_checks": 0,
            "top_candidates": top_candidates,
            "top_stations": [],
            "generated_at": now_ms(),
        }

def candidate_application_summaries_v2(election_id: str, top_candidates: list[dict[str, Any]] | None = None, limit: int = 8) -> list[dict[str, Any]]:
    if not election_id:
        return []
    with db() as con:
        if table_exists(con, "candidates") and table_exists(con, "candidate_applications"):
            try:
                rows = con.execute(
                    """
                    SELECT c.player_uuid AS uuid,
                           c.player_name AS name,
                           COALESCE(a.answers, '') AS statement,
                           COALESCE(a.submitted_at, a.issued_at, 0) AS submitted_at
                    FROM candidates c
                    LEFT JOIN candidate_applications a ON a.id=c.application_id
                    WHERE c.election_id=? AND COALESCE(c.active,1)=1
                    ORDER BY c.player_name ASC
                    LIMIT ?
                    """,
                    (election_id, limit),
                ).fetchall()
                return [row_to_dict(r) for r in rows]
            except Exception:
                pass
    return [
        {
            "uuid": str(row.get("uuid") or ""),
            "name": str(row.get("name") or "Неизвестный кандидат"),
            "statement": "",
            "submitted_at": 0,
        }
        for row in (top_candidates or [])[:limit]
    ]

def application_snapshot_v2(row: Any | dict[str, Any]) -> dict[str, Any]:
    d = row_to_dict(row)
    return {
        "id": str(get_col(d, "id", default="")),
        "election_id": str(get_col(d, "election_id", "election", default="")),
        "applicant_uuid": str(get_col(d, "applicant_uuid", "uuid", "player_uuid", default="")),
        "applicant_name": str(get_col(d, "applicant_name", "name", "player", "player_name", default="unknown")),
        "statement": str(get_col(d, "answers", "statement", "text", "message", "application", default="")),
        "status": norm_status(get_col(d, "admin_status", "status", default="PENDING")),
        "submitted_at": get_col(d, "submitted_at", "created_at", "time", "issued_at", default=0),
        "reviewed_by": str(get_col(d, "reviewed_by", "admin", default="") or ""),
        "reviewed_at": get_col(d, "reviewed_at", default=0),
        "verdict_reason": str(get_col(d, "admin_note", "verdict_reason", "reason", default="") or ""),
        "visible_in_game": get_col(d, "visible_in_game", default=1),
        "deleted_by": str(get_col(d, "deleted_by", default="") or ""),
        "deleted_at": get_col(d, "deleted_at", default=0),
        "station_id": str(get_col(d, "station_id", default="") or ""),
        "chair_recommendation": str(get_col(d, "chair_recommendation", default="") or ""),
        "_raw": d,
    }

def candidate_application_summaries(election_id: str, top_candidates: list[dict[str, Any]] | None = None, limit: int = 8) -> list[dict[str, Any]]:
    if not election_id:
        return []
    with db() as con:
        if table_exists(con, "candidates") and table_exists(con, "applications"):
            rows = con.execute("""
                SELECT c.uuid,
                       COALESCE(NULLIF(c.display_name,''), NULLIF(c.name,''), a.applicant_name, c.uuid) AS name,
                       COALESCE(a.statement, '') AS statement,
                       COALESCE(a.submitted_at, 0) AS submitted_at
                FROM candidates c
                LEFT JOIN applications a
                  ON a.election_id=c.election_id
                 AND a.applicant_uuid=c.uuid
                 AND a.status='APPROVED'
                 AND COALESCE(a.deleted_at,0)=0
                 AND COALESCE(a.visible_in_game,1)=1
                WHERE c.election_id=? AND COALESCE(c.removed,0)=0
                ORDER BY COALESCE(NULLIF(c.display_name,''), NULLIF(c.name,''), a.applicant_name, c.uuid) ASC
                LIMIT ?
            """, (election_id, limit)).fetchall()
            return [row_to_dict(r) for r in rows]
    fallback = []
    for row in (top_candidates or [])[:limit]:
        fallback.append({
            "uuid": str(row.get("uuid") or ""),
            "name": str(row.get("name") or "Кандидат"),
            "statement": "",
            "submitted_at": 0,
        })
    return fallback

def whitelist_requests_snapshot(limit: int = 25) -> list[dict[str, Any]]:
    with db() as con:
        if not table_exists(con, "whitelist_requests"):
            return []
        rows = con.execute(
            """
            SELECT wr.id,wr.site_account_id,wr.minecraft_uuid,wr.minecraft_name,wr.request_ip,wr.status,
                   wr.created_at,wr.updated_at,wr.approved_at,wr.approved_by,wr.note,
                   sa.username
            FROM whitelist_requests wr
            LEFT JOIN site_accounts sa ON sa.id=wr.site_account_id
            WHERE wr.status='PENDING'
            ORDER BY wr.created_at ASC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
        return [row_to_dict(r) for r in rows]


def approve_whitelist_request(request_id: str, actor: str) -> dict[str, Any]:
    with db() as con:
        row = con.execute("SELECT * FROM whitelist_requests WHERE id=? FOR UPDATE", (request_id,)).fetchone()
        if not row:
            raise RuntimeError("whitelist_request_not_found")
        item = row_to_dict(row)
        status = str(item.get("status") or "").upper()
        if str(item.get("status") or "").upper() == "APPROVED":
            status = "APPROVED"
        if status == "APPROVED":
            return item
        if status != "PENDING":
            raise RuntimeError("whitelist_request_not_pending")
        minecraft_name = str(item.get("minecraft_name") or "")
        minecraft_uuid = str(item.get("minecraft_uuid") or "")
        if minecraft_name and not minecraft_uuid:
            source = ("OfflinePlayer:" + minecraft_name).encode("utf-8")
            digest = bytearray(hashlib.md5(source).digest())
            digest[6] = (digest[6] & 0x0F) | 0x30
            digest[8] = (digest[8] & 0x3F) | 0x80
            minecraft_uuid = str(uuid.UUID(bytes=bytes(digest)))
        if not minecraft_name or not minecraft_uuid:
            raise RuntimeError("whitelist_request_missing_identity")
        whitelist_path = MC_SERVER_DIR / "whitelist.json"
        current = read_json_file(whitelist_path, [])
        entries = current if isinstance(current, list) else []
        lower_name = minecraft_name.lower()
        lower_uuid = minecraft_uuid.lower()
        exists = any(
            isinstance(entry, dict) and (
                str(entry.get("name") or "").lower() == lower_name
                or str(entry.get("uuid") or "").lower() == lower_uuid
            )
            for entry in entries
        )
        if not exists:
            entries.append({"uuid": minecraft_uuid, "name": minecraft_name})
            write_json_file(whitelist_path, entries)
        now = now_ms()
        con.execute(
            "UPDATE whitelist_requests SET minecraft_uuid=?,status='APPROVED',updated_at=?,approved_at=?,approved_by=?,note=? WHERE id=?",
            (minecraft_uuid, now, now, actor, "Одобрено через Discord", request_id),
        )
        con.execute(
            """
            INSERT INTO whitelist_account_links(minecraft_uuid,minecraft_name,site_account_id,whitelisted,synced_at)
            VALUES(?,?,?,?,?)
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                minecraft_name=excluded.minecraft_name,
                site_account_id=excluded.site_account_id,
                whitelisted=1,
                synced_at=excluded.synced_at
            """,
            (minecraft_uuid, minecraft_name, str(item.get("site_account_id") or ""), 1, now),
        )
        con.commit()
        item["status"] = "APPROVED"
        item["approved_at"] = now
        item["approved_by"] = actor
        item["note"] = "Одобрено через Discord"
        return item


def active_admin_alerts_snapshot() -> list[dict[str, Any]]:
    with db() as con:
        if not table_exists(con, "cmv7_player_checks"):
            return []
        rows = con.execute("""
            SELECT id, time, admin_name, player_name, action, active, details
            FROM cmv7_player_checks
            WHERE active=1
            ORDER BY time DESC
            LIMIT 10
        """).fetchall()
        return [row_to_dict(r) for r in rows]

def application_snapshot(row: Any | dict[str, Any]) -> dict[str, Any]:
    d = row_to_dict(row)
    return {
        "id": str(get_col(d, "id", default="")),
        "election_id": str(get_col(d, "election_id", "election", default="")),
        "applicant_uuid": str(get_col(d, "applicant_uuid", "uuid", "player_uuid", default="")),
        "applicant_name": str(get_col(d, "applicant_name", "name", "player", default="unknown")),
        "statement": str(get_col(d, "statement", "text", "message", "application", default="")),
        "status": norm_status(get_col(d, "status", default="PENDING")),
        "submitted_at": get_col(d, "submitted_at", "created_at", "time", default=0),
        "reviewed_by": str(get_col(d, "reviewed_by", "admin", default="") or ""),
        "reviewed_at": get_col(d, "reviewed_at", default=0),
        "verdict_reason": str(get_col(d, "verdict_reason", "reason", default="") or ""),
        "visible_in_game": get_col(d, "visible_in_game", default=None),
        "deleted_by": str(get_col(d, "deleted_by", default="") or ""),
        "deleted_at": get_col(d, "deleted_at", default=0),
        "_raw": d,
    }

def is_public_application(snap: dict[str, Any]) -> bool:
    if is_deleted_status(snap.get("status")):
        return False
    if str(snap.get("status") or "").upper() != "APPROVED":
        return False
    visible = snap.get("visible_in_game")
    if visible in (0, "0", False):
        return False
    try:
        if int(snap.get("deleted_at") or 0) > 0:
            return False
    except Exception:
        return False
    return bool(str(snap.get("applicant_name") or "").strip())

def snapshot_hash(snapshot: dict[str, Any]) -> str:
    compact = {
        "id": snapshot.get("id"),
        "status": snapshot.get("status"),
        "statement": snapshot.get("statement"),
        "applicant_name": snapshot.get("applicant_name"),
        "election_id": snapshot.get("election_id"),
        "visible_in_game": snapshot.get("visible_in_game"),
        "reviewed_by": snapshot.get("reviewed_by"),
        "reviewed_at": snapshot.get("reviewed_at"),
        "verdict_reason": snapshot.get("verdict_reason"),
        "deleted_by": snapshot.get("deleted_by"),
        "deleted_at": snapshot.get("deleted_at"),
    }
    return json.dumps(compact, ensure_ascii=False, sort_keys=True)

class AppView(discord.ui.View):
    def __init__(self, app_id: str, enabled: bool = True):
        super().__init__(timeout=None)
        return
        if enabled:
            self.add_item(discord.ui.Button(label="Одобрить", style=discord.ButtonStyle.success, custom_id=f"cmu_app_approve:{app_id}"))
            self.add_item(discord.ui.Button(label="Отклонить", style=discord.ButtonStyle.danger, custom_id=f"cmu_app_deny:{app_id}"))
            self.add_item(discord.ui.Button(label="Скрыть из игры", style=discord.ButtonStyle.secondary, custom_id=f"cmu_app_hide:{app_id}"))
            self.add_item(discord.ui.Button(label="Удалить мягко", style=discord.ButtonStyle.danger, custom_id=f"cmu_app_delete:{app_id}"))

class ReportView(discord.ui.View):
    def __init__(self, report_id: str, enabled: bool = True):
        super().__init__(timeout=None)
        if enabled:
            self.add_item(discord.ui.Button(label="Взять в работу", style=discord.ButtonStyle.primary, custom_id=f"cmu_report_take:{report_id}"))
            self.add_item(discord.ui.Button(label="Закрыть", style=discord.ButtonStyle.success, custom_id=f"cmu_report_close:{report_id}"))

def check_server_online_sync() -> tuple[bool, str]:
    failures = []
    service_active = False

    try:
        proc = subprocess.run(
            ["systemctl", "is-active", "--quiet", MINECRAFT_SERVICE],
            text=True,
            capture_output=True,
            timeout=1.5,
        )
        service_active = proc.returncode == 0
    except Exception as exc:
        failures.append(f"systemctl={type(exc).__name__}")

    targets = []
    if MC_HOST:
        targets.append((MC_HOST, MC_PORT))
    targets.append(("127.0.0.1", MC_PORT))
    targets.append(("localhost", MC_PORT))

    seen = set()
    tcp_ok = False
    tcp_ok_target = ""

    for host, port in targets:
        key = (str(host), int(port))
        if key in seen:
            continue
        seen.add(key)
        try:
            with socket.create_connection((host, int(port)), timeout=1.0):
                tcp_ok = True
                tcp_ok_target = f"{host}:{port}"
                break
        except Exception as exc:
            failures.append(f"{host}:{port}={type(exc).__name__}")

    if service_active and tcp_ok:
        return True, f"service_active_and_tcp_ok:{tcp_ok_target}"
    if (not service_active) and tcp_ok:
        return False, f"tcp_open_but_service_inactive:{tcp_ok_target}"
    if service_active and (not tcp_ok):
        return False, "service_active_but_tcp_closed:" + ",".join(failures)
    return False, "service_inactive_and_tcp_closed:" + ",".join(failures)

class Bot(discord.Client):
    def __init__(self) -> None:
        intents = discord.Intents.default()
        intents.guilds = True
        intents.guild_messages = True
        intents.guild_reactions = True
        # Member lookup is performed explicitly through ``guild.fetch_member``
        # when a reaction/button is handled.  Requesting the members gateway
        # intent here makes the bot fail at login unless the privileged intent
        # is enabled in Discord Developer Portal, even though the bot does not
        # consume the member cache.  Keep the gateway contract minimal.
        super().__init__(intents=intents)
        self.state: dict[str, Any] = load_runtime_state()
        self._last_status_online: bool | None = None
        self._last_channel_rename_attempt = 0.0
        self._adopted_messages = False

    async def setup_hook(self) -> None:
        self.bg = asyncio.create_task(self.main_loop())

    async def on_ready(self) -> None:
        print(f"CopiMine Discord bot connected as {self.user}", flush=True)
        if not getattr(self, "_status_loop_started", False):
            self._status_loop_started = True
            asyncio.create_task(self.status_loop())

    async def on_interaction(self, interaction: discord.Interaction) -> None:
        data = getattr(interaction, "data", None) or {}
        custom_id = str(data.get("custom_id", ""))

        if custom_id.startswith("cmu_app_"):
            if not interaction.response.is_done():
                await interaction.response.send_message(
                    "Заявки на выборах теперь рассматриваются только в игровом GUI: /cadm -> Выборы.",
                    ephemeral=True,
                )
            return
            try:
                prefix, app_id = custom_id.split(":", 1)
                action = prefix.replace("cmu_app_", "", 1)
                await self.handle_application_action(interaction, app_id, action)
            except Exception as exc:
                if not interaction.response.is_done():
                    await interaction.response.send_message(f"Ошибка кнопки: {type(exc).__name__}: {exc}", ephemeral=True)
            return

        if custom_id.startswith("cmu_report_"):
            try:
                prefix, report_id = custom_id.split(":", 1)
                action = prefix.replace("cmu_report_", "", 1)
                await self.handle_report_action(interaction, report_id, action)
            except Exception as exc:
                if not interaction.response.is_done():
                    await interaction.response.send_message(f"Ошибка кнопки: {type(exc).__name__}: {exc}", ephemeral=True)
            return

    async def on_raw_reaction_add(self, payload: discord.RawReactionActionEvent) -> None:
        if payload.user_id == getattr(self.user, "id", None):
            return
        if str(payload.emoji) != "✅":
            return
        target_request_id = ""
        for request_id, rec in (self.state.get("whitelist_messages") or {}).items():
            if str(rec.get("message_id") or "") == str(payload.message_id):
                target_request_id = request_id
                break
        if not target_request_id:
            return
        guild = self.get_guild(payload.guild_id) if payload.guild_id else None
        member = payload.member
        if guild and member is None:
            try:
                member = await guild.fetch_member(payload.user_id)
            except Exception:
                member = None
        if not can_approve_whitelist(member):
            return
        actor = str(getattr(member, "display_name", None) or getattr(member, "name", None) or payload.user_id)
        try:
            row = await asyncio.to_thread(approve_whitelist_request, target_request_id, actor)
        except Exception as exc:
            print(f"Whitelist reaction approve failed {target_request_id}: {type(exc).__name__}: {exc}", flush=True)
            return
        rec = (self.state.get("whitelist_messages") or {}).get(target_request_id) or {}
        msg = await self.fetch_message_safe(str(rec.get("channel_id") or WHITELIST_CH), str(rec.get("message_id") or payload.message_id))
        if msg:
            try:
                await msg.edit(embed=self.whitelist_embed(row))
                await asyncio.to_thread(log_notification, str(msg.channel.id), "whitelist_request", target_request_id, "approved")
            except Exception as exc:
                print(f"Whitelist message edit failed {target_request_id}: {type(exc).__name__}: {exc}", flush=True)
        self.state.setdefault("whitelist_messages", {})[target_request_id] = {
            "channel_id": str(rec.get("channel_id") or WHITELIST_CH),
            "message_id": str(rec.get("message_id") or payload.message_id),
            "status": "APPROVED",
            "updated_at": now_ms(),
        }
        await asyncio.to_thread(save_state_sync, self.state)

    async def channel(self, cid: str):
        if not cid or not cid.isdigit():
            return None
        try:
            return self.get_channel(int(cid)) or await self.fetch_channel(int(cid))
        except Exception as exc:
            print(f"Не могу открыть канал {cid}: {type(exc).__name__}: {exc}", flush=True)
            return None

    async def fetch_message_safe(self, channel_id: str, message_id: str):
        ch = await self.channel(channel_id)
        if not ch or not message_id:
            return None
        try:
            return await ch.fetch_message(int(message_id))
        except Exception as exc:
            print(f"Не могу открыть Discord сообщение {channel_id}/{message_id}: {type(exc).__name__}: {exc}", flush=True)
            return None

    def app_embed(self, row_or_snapshot: Any | dict[str, Any], *, admin: bool = True, missing: bool = False) -> discord.Embed:
        snap = application_snapshot_v2(row_or_snapshot)
        status = "DELETED" if missing else snap["status"]
        if not admin:
            return self.public_application_embed(snap)

        if status == "PENDING":
            title = "🟡 Новая заявка кандидата"
        elif status == "APPROVED":
            title = "✅ Заявка одобрена"
        elif status == "DENIED":
            title = "❌ Заявка отклонена"
        elif is_deleted_status(status):
            title = "🗑️ Заявка удалена"
        else:
            title = f"Заявка: {status_ru(status)}"

        # Главное: текст заявки всегда остаётся в embed после одобрения/отклонения/удаления.
        statement = snap.get("statement") or "Текст заявки не найден в БД/состоянии бота."
        e = discord.Embed(
            title=title,
            description=short(statement, 3600),
            color=status_color(status),
        )
        e.add_field(name="Игрок", value=short(snap.get("applicant_name"), 256), inline=True)
        e.add_field(name="Статус", value=short(status_ru(status), 256), inline=True)
        e.add_field(name="Дата подачи", value=ts(snap.get("submitted_at")), inline=True)
        e.add_field(name="ID", value=f"`{snap.get('id')}`", inline=False)
        e.add_field(name="Выборы", value=short(snap.get("election_id"), 256), inline=True)
        if snap.get("station_id"):
            e.add_field(name="Участок", value=short(snap.get("station_id"), 256), inline=True)
        if snap.get("chair_recommendation"):
            e.add_field(name="Пометка ЦИК", value=short(snap.get("chair_recommendation"), 256), inline=True)

        if snap.get("reviewed_by"):
            e.add_field(name="Решил", value=short(snap.get("reviewed_by"), 256), inline=True)
        if snap.get("reviewed_at"):
            e.add_field(name="Дата решения", value=ts(snap.get("reviewed_at")), inline=True)
        if snap.get("verdict_reason"):
            e.add_field(name="Причина/комментарий", value=short(snap.get("verdict_reason"), 900), inline=False)
        if missing:
            e.add_field(name="Синхронизация", value="Заявка исчезла из БД Minecraft/админки. Сообщение обновлено автоматически.", inline=False)

        e.set_footer(text="CopiMine • решение по заявке принимается только в игровом GUI")
        return e

    def public_application_embed(self, snap: dict[str, Any]) -> discord.Embed:
        name = short(snap.get("applicant_name") or "Кандидат", 180)
        statement = short(snap.get("statement") or "Заявка опубликована ЦИК, текст программы пока пуст.", 3600)
        e = discord.Embed(
            title=f"Кандидат: {name}",
            description=statement,
            color=0xFEE75C,
            timestamp=discord.utils.utcnow(),
        )
        e.add_field(name="Как участвовать", value="Изучи программу в Discord, затем голосуй на сервере через официальный бюллетень.", inline=False)
        e.set_footer(text="Публичная витрина ЦИК • только кандидаты и опубликованные заявки")
        return e

    def report_embed(self, row: Any | dict[str, Any], admin: bool = True) -> discord.Embed:
        d = row_to_dict(row)
        title = "Новый репорт / обращение"
        status = str(get_col(d, "status", default="OPEN"))
        e = discord.Embed(
            title=title,
            description=short(get_col(d, "message", default=""), 3500),
            color=0xED4245 if status.upper() == "OPEN" else 0x5865F2,
        )
        e.add_field(name="Игрок", value=short(get_col(d, "player_name", "reporter", default="unknown"), 256), inline=True)
        e.add_field(name="Статус", value=short(status, 256), inline=True)
        e.add_field(name="Дата", value=ts(get_col(d, "created_at", default=0)), inline=True)
        if not admin:
            e.set_footer(text="CopiMine • публичная версия без технических данных")
            return e
        e.add_field(name="ID", value=f"`{get_col(d, 'id', default='')}`", inline=False)
        if get_col(d, "snapshot", default=""):
            e.add_field(name="Snapshot", value=short(get_col(d, "snapshot", default=""), 1024), inline=False)
        e.set_footer(text="CopiMine Ultra Admin")
        return e

    def report_has_technical_context(self, row: Any | dict[str, Any]) -> bool:
        item = row_to_dict(row)
        message = str(get_col(item, "message", default="") or "").strip().upper()
        snapshot = str(get_col(item, "snapshot", default="") or "").lower()
        return message.startswith("[BUG ") or any(marker in snapshot for marker in ("error=", "exception", "stacktrace", "technical"))

    def masked_ip(self, value: Any) -> str:
        raw = str(value or "").strip()
        if not raw:
            return ""
        if ":" in raw:
            parts = [part for part in raw.split(":") if part]
            if len(parts) <= 2:
                return "****:****"
            return ":".join(parts[:2] + ["****", "****"])
        parts = raw.split(".")
        if len(parts) == 4:
            return ".".join(parts[:2] + ["***", "***"])
        return raw[:3] + "***"

    def whitelist_embed(self, row: dict[str, Any]) -> discord.Embed:
        status = str(row.get("status") or "PENDING").upper()
        title = "Whitelist одобрен" if status == "APPROVED" else "Новый запрос в whitelist"
        color = 0x57F287 if status == "APPROVED" else 0xFEE75C
        embed = discord.Embed(title=title, color=color, timestamp=discord.utils.utcnow())
        embed.add_field(name="Minecraft-ник", value=short(row.get("minecraft_name") or "unknown", 256), inline=True)
        embed.add_field(name="UUID", value=f"`{short(row.get('minecraft_uuid') or '', 64)}`", inline=False)
        embed.add_field(name="Сайт", value=short(row.get("username") or row.get("site_account_id") or "unknown", 256), inline=True)
        embed.add_field(name="Статус", value=short(status, 64), inline=True)
        if row.get("request_ip"):
            embed.add_field(name="IP", value=self.masked_ip(row.get("request_ip")), inline=True)
        if row.get("approved_by"):
            embed.add_field(name="Одобрил", value=short(row.get("approved_by"), 128), inline=True)
        if row.get("approved_at"):
            embed.add_field(name="Одобрено", value=ts(row.get("approved_at")), inline=True)
        embed.set_footer(text="Нажми реакцию approve, если у тебя есть роль администратора или назначенная роль по ID")
        return embed

    async def sync_whitelist_requests(self) -> None:
        if not WHITELIST_CH:
            return
        ch = await self.channel(WHITELIST_CH)
        if not ch:
            return
        rows = await asyncio.to_thread(whitelist_requests_snapshot, 25)
        state_rows = self.state.setdefault("whitelist_messages", {})
        for row in rows:
            request_id = str(row.get("id") or "")
            if not request_id:
                continue
            rec = state_rows.get(request_id) or {}
            msg = None
            if rec.get("message_id"):
                msg = await self.fetch_message_safe(str(rec.get("channel_id") or WHITELIST_CH), str(rec.get("message_id") or ""))
            if msg is None:
                msg = await ch.send(embed=self.whitelist_embed(row))
                try:
                    await msg.add_reaction("✅")
                except Exception:
                    pass
                await asyncio.to_thread(log_notification, str(msg.channel.id), "whitelist_request", request_id, "created")
            state_rows[request_id] = {
                "channel_id": str(msg.channel.id),
                "message_id": str(msg.id),
                "status": str(row.get("status") or "PENDING"),
                "updated_at": now_ms(),
            }

    async def status_loop(self) -> None:
        await self.wait_until_ready()
        while not self.is_closed():
            try:
                await self.update_server_status_channel()
                await self.update_elections_status_channel()
                await self.update_admin_alerts_channel()
                await asyncio.to_thread(save_state_sync, self.state)
            except Exception as exc:
                print(f"Ошибка status_loop/update_server_status_channel: {type(exc).__name__}: {exc}", flush=True)
            await asyncio.sleep(POLL_SECONDS)

    async def main_loop(self) -> None:
        await self.wait_until_ready()

        if not self._adopted_messages:
            await self.adopt_recent_application_messages()
            self._adopted_messages = True

        while not self.is_closed():
            try:
                await self.sync_applications()
            except Exception as exc:
                print(f"Ошибка sync_applications: {type(exc).__name__}: {exc}", flush=True)

            try:
                await self.sync_whitelist_requests()
            except Exception as exc:
                print(f"Whitelist sync failed: {type(exc).__name__}: {exc}", flush=True)

            try:
                await self.sync_reports_new_only()
            except Exception as exc:
                print(f"Ошибка sync_reports: {type(exc).__name__}: {exc}", flush=True)

            await asyncio.to_thread(save_state_sync, self.state)
            await asyncio.sleep(POLL_SECONDS)

    async def adopt_recent_application_messages(self) -> None:
        ch = await self.channel(APP_ADMIN_CH)
        if not ch:
            return

        app_messages = self.state.setdefault("app_messages", {})
        adopted = 0

        try:
            async for msg in ch.history(limit=DISCORD_MESSAGE_HISTORY_ADOPT_LIMIT):
                if not msg.embeds:
                    continue

                for emb in msg.embeds:
                    app_id = None
                    for field in emb.fields:
                        if str(field.name).strip().lower() == "id":
                            val = str(field.value).strip().strip("`")
                            if val:
                                app_id = val
                                break

                    if not app_id:
                        # fallback: parse from text
                        joined = " ".join([
                            str(emb.title or ""),
                            str(emb.description or ""),
                            " ".join(f"{f.name} {f.value}" for f in emb.fields),
                        ])
                        m = re.search(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", joined, re.I)
                        if m:
                            app_id = m.group(0)

                    if app_id and app_id not in app_messages:
                        app_messages[app_id] = {
                            "channel_id": str(msg.channel.id),
                            "message_id": str(msg.id),
                            "adopted": True,
                            "last_hash": "",
                        }
                        adopted += 1
                        break

            if adopted:
                await asyncio.to_thread(save_state_sync, self.state)
                print(f"Adopted old Discord application messages: {adopted}", flush=True)
        except Exception as exc:
            print(f"Не смог прочитать историю заявок Discord: {type(exc).__name__}: {exc}", flush=True)

    async def sync_applications(self) -> None:
        app_messages = self.state.setdefault("app_messages", {})
        seen_list = self.state.setdefault("applications", [])

        with db() as con:
            if table_exists(con, "candidate_applications"):
                rows = con.execute("""
                    SELECT * FROM candidate_applications
                    ORDER BY COALESCE(submitted_at, issued_at, 0) DESC
                    LIMIT 120
                """).fetchall()
            else:
                rows = con.execute("""
                    SELECT * FROM applications
                    ORDER BY submitted_at DESC
                    LIMIT 120
                """).fetchall()

        by_id: dict[str, Any] = {}
        for row in rows:
            app_id = str(get_col(row, "id", default=""))
            if app_id:
                by_id[app_id] = row

        # New or changed rows.
        for row in reversed(rows):
            snap = application_snapshot_v2(row)
            app_id = snap["id"]
            if not app_id:
                continue

            current_hash = snapshot_hash(snap)
            rec = app_messages.get(app_id)

            if not rec:
                # New message. If old state only had app id without message id, try adopting already happened.
                await self.send_application_message(row)
                continue

            await self.sync_public_application_message(snap)
            rec.setdefault("snapshot", snap)
            msg_id = str(rec.get("message_id", ""))
            channel_id = str(rec.get("channel_id", APP_ADMIN_CH))
            last_hash = str(rec.get("last_hash", ""))

            if msg_id and current_hash != last_hash:
                msg = await self.fetch_message_safe(channel_id, msg_id)
                if msg:
                    status = snap["status"]
                    view = AppView(app_id, enabled=(status == "PENDING"))
                    await msg.edit(embed=self.app_embed(snap, admin=True), view=view if status == "PENDING" else None)
                    await asyncio.to_thread(log_notification, channel_id, "application", app_id, f"updated:{status}")
                    print(f"Discord заявка обновлена из БД: {app_id} -> {status}", flush=True)

                rec["last_hash"] = current_hash
                rec["last_status"] = snap["status"]
                rec["snapshot"] = snap
                rec["updated_at"] = now_ms()

            if app_id not in seen_list:
                seen_list.append(app_id)

        # Rows that disappeared from DB: mark as deleted in Discord message.
        known_ids = list(app_messages.keys())
        for app_id in known_ids:
            if app_id in by_id:
                continue

            rec = app_messages.get(app_id) or {}
            if rec.get("missing_marked"):
                continue

            msg_id = str(rec.get("message_id", ""))
            channel_id = str(rec.get("channel_id", APP_ADMIN_CH))
            if not msg_id:
                continue

            snapshot = rec.get("snapshot") or {"id": app_id, "status": "DELETED", "statement": "Текст заявки не найден."}
            snapshot["status"] = "DELETED"

            msg = await self.fetch_message_safe(channel_id, msg_id)
            if msg:
                await msg.edit(embed=self.app_embed(snapshot, admin=True, missing=True), view=None)
                await asyncio.to_thread(log_notification, channel_id, "application", app_id, "missing_marked")
                print(f"Discord заявка помечена как удалённая из БД: {app_id}", flush=True)

            await self.delete_public_application_message(app_id)
            rec["missing_marked"] = True
            rec["last_status"] = "DELETED"
            rec["updated_at"] = now_ms()

        self.state["applications"] = seen_list[-500:]

    async def send_application_message(self, row: Any) -> None:
        snap = application_snapshot_v2(row)
        app_id = snap["id"]
        if not app_id:
            return

        admin_ch = await self.channel(APP_ADMIN_CH)
        await self.sync_public_application_message(snap)

        if admin_ch:
            status = snap["status"]
            view = AppView(app_id, enabled=(status == "PENDING"))
            msg = await admin_ch.send(embed=self.app_embed(snap, admin=True), view=view if status == "PENDING" else None)
            await asyncio.to_thread(log_notification, str(msg.channel.id), "application", app_id, f"created:{status}")

            self.state.setdefault("app_messages", {})[app_id] = {
                "channel_id": str(msg.channel.id),
                "message_id": str(msg.id),
                "last_hash": snapshot_hash(snap),
                "last_status": status,
                "snapshot": snap,
                "created_at": now_ms(),
            }

            apps = self.state.setdefault("applications", [])
            if app_id not in apps:
                apps.append(app_id)
            self.state["applications"] = apps[-500:]
            await asyncio.to_thread(save_state_sync, self.state)
            print(f"Отправлена заявка в Discord: {app_id}", flush=True)

    async def sync_public_application_message(self, snap: dict[str, Any]) -> None:
        app_id = str(snap.get("id") or "")
        if not app_id or not APP_CH or APP_CH == APP_ADMIN_CH:
            return
        public_messages = self.state.setdefault("public_app_messages", {})
        if not is_public_application(snap):
            await self.delete_public_application_message(app_id)
            return
        ch = await self.channel(APP_CH)
        if not ch:
            return
        public_hash = json.dumps({
            "applicant_name": snap.get("applicant_name"),
            "statement": snap.get("statement"),
            "visible_in_game": snap.get("visible_in_game"),
        }, ensure_ascii=False, sort_keys=True)
        rec = public_messages.get(app_id) or {}
        msg = None
        if rec.get("message_id"):
            msg = await self.fetch_message_safe(str(rec.get("channel_id", APP_CH)), str(rec.get("message_id", "")))
            if msg and rec.get("last_hash") == public_hash:
                return
        embed = self.public_application_embed(snap)
        if msg:
            await msg.edit(embed=embed, view=None)
            await asyncio.to_thread(log_notification, str(msg.channel.id), "application_public", app_id, "public_updated")
        else:
            msg = await ch.send(embed=embed)
            await asyncio.to_thread(log_notification, str(msg.channel.id), "application_public", app_id, "public_created")
        public_messages[app_id] = {
            "channel_id": str(msg.channel.id),
            "message_id": str(msg.id),
            "last_hash": public_hash,
            "updated_at": now_ms(),
        }

    async def delete_public_application_message(self, app_id: str) -> None:
        public_messages = self.state.setdefault("public_app_messages", {})
        rec = public_messages.pop(app_id, None)
        if not rec:
            return
        msg = await self.fetch_message_safe(str(rec.get("channel_id", APP_CH)), str(rec.get("message_id", "")))
        if msg:
            try:
                await msg.delete()
            except Exception as exc:
                print(f"Не смог удалить публичную заявку Discord {app_id}: {type(exc).__name__}: {exc}", flush=True)

    async def handle_application_action(self, interaction: discord.Interaction, app_id: str, action: str) -> None:
        if not is_admin(interaction.user):
            await interaction.response.send_message("Нет прав: кнопки заявок доступны только администрации.", ephemeral=True)
            return

        actor = str(interaction.user)

        try:
            with db() as con:
                app = con.execute("SELECT * FROM applications WHERE id=?", (app_id,)).fetchone()
                if not app:
                    await interaction.response.send_message("Заявка уже не найдена в БД.", ephemeral=True)
                    return

                old = application_snapshot(app)

                if action == "approve":
                    con.execute("""
                        UPDATE applications
                        SET status='APPROVED', reviewed_by=?, reviewed_at=?, verdict_reason='Одобрено через Discord', visible_in_game=0
                        WHERE id=?
                    """, (actor, now_ms(), app_id))
                    con.execute("""
                        INSERT OR IGNORE INTO candidates
                        (election_id, uuid, name, display_name, raw_votes, admin_adjustment, removed)
                        VALUES (?, ?, ?, ?, 0, 0, 0)
                    """, (old["election_id"], old["applicant_uuid"], old["applicant_name"], old["applicant_name"]))
                    self.audit(con, actor, "DISCORD_APPLICATION_APPROVED", app_id)

                elif action == "deny":
                    con.execute("""
                        UPDATE applications
                        SET status='DENIED', reviewed_by=?, reviewed_at=?, verdict_reason='Отклонено через Discord', visible_in_game=0
                        WHERE id=?
                    """, (actor, now_ms(), app_id))
                    self.audit(con, actor, "DISCORD_APPLICATION_DENIED", app_id)

                elif action == "hide":
                    con.execute("UPDATE applications SET visible_in_game=0 WHERE id=?", (app_id,))
                    self.audit(con, actor, "DISCORD_APPLICATION_HIDDEN", app_id)

                elif action == "delete":
                    con.execute("""
                        UPDATE applications
                        SET status='ARCHIVED', visible_in_game=0, deleted_by=?, deleted_at=?
                        WHERE id=?
                    """, (actor, now_ms(), app_id))
                    self.audit(con, actor, "DISCORD_APPLICATION_SOFT_DELETE", app_id)

                else:
                    await interaction.response.send_message("Неизвестное действие.", ephemeral=True)
                    return

                updated = con.execute("SELECT * FROM applications WHERE id=?", (app_id,)).fetchone()

            snap = application_snapshot(updated)
            rec = self.state.setdefault("app_messages", {}).setdefault(app_id, {})
            rec["last_hash"] = snapshot_hash(snap)
            rec["last_status"] = snap["status"]
            rec["snapshot"] = snap
            rec["updated_at"] = now_ms()
            await self.sync_public_application_message(snap)
            await asyncio.to_thread(log_bridge_event, "discord", f"application_{action}", {"application_id": app_id, "actor": actor, "status": snap["status"]})
            await asyncio.to_thread(save_state_sync, self.state)

            # Text remains visible here: edit to full application embed, not a tiny "done" embed.
            if not interaction.response.is_done():
                await interaction.response.edit_message(
                    embed=self.app_embed(snap, admin=True),
                    view=AppView(app_id, enabled=(snap["status"] == "PENDING")) if snap["status"] == "PENDING" else None,
                )
            else:
                msg = interaction.message
                if msg:
                    await msg.edit(embed=self.app_embed(snap, admin=True), view=None)

        except Exception as exc:
            if not interaction.response.is_done():
                await interaction.response.send_message(f"Ошибка обработки: {type(exc).__name__}: {exc}", ephemeral=True)

    def audit(self, con: PgCompatConnection, actor: str, action: str, details: str) -> None:
        try:
            con.execute(
                "INSERT INTO audit(time, actor, action, details, admin_only) VALUES (?, ?, ?, ?, 1)",
                (now_ms(), actor, action, details),
            )
        except Exception:
            pass

    async def sync_reports_new_only(self) -> None:
        reports_seen = self.state.setdefault("reports", [])
        report_messages = self.state.setdefault("report_messages", {})

        with db() as con:
            rows = con.execute("""
                SELECT * FROM admin_requests
                WHERE status IN ('OPEN', 'IN_PROGRESS')
                ORDER BY created_at DESC
                LIMIT 50
            """).fetchall()

        for row in reversed(rows):
            rid = str(get_col(row, "id", default=""))
            if not rid or rid in reports_seen or rid in report_messages:
                continue

            public_ch = await self.channel(REPORT_CH)
            admin_ch = await self.channel(REPORT_ADMIN_CH)

            if public_ch and REPORT_CH and REPORT_CH != REPORT_ADMIN_CH and not self.report_has_technical_context(row):
                public_msg = await public_ch.send(embed=self.report_embed(row, admin=False))
                await asyncio.to_thread(log_notification, str(public_msg.channel.id), "report_public", rid, "created")

            if admin_ch:
                msg = await admin_ch.send(embed=self.report_embed(row, admin=True), view=ReportView(rid, enabled=True))
                await asyncio.to_thread(log_notification, str(msg.channel.id), "report", rid, "created")
                report_messages[rid] = {"channel_id": str(msg.channel.id), "message_id": str(msg.id), "created_at": now_ms()}

            reports_seen.append(rid)
            self.state["reports"] = reports_seen[-500:]
            await asyncio.to_thread(save_state_sync, self.state)
            print(f"Отправлен репорт в Discord: {rid}", flush=True)

    async def handle_report_action(self, interaction: discord.Interaction, report_id: str, action: str) -> None:
        if not is_admin(interaction.user):
            await interaction.response.send_message("Нет прав: кнопки репортов доступны только администрации.", ephemeral=True)
            return

        try:
            actor = str(interaction.user)
            with db() as con:
                if action == "take":
                    con.execute("UPDATE admin_requests SET status='IN_PROGRESS', updated_at=?, assigned_to=? WHERE id=?",
                                (now_ms(), actor, report_id))
                    title = "Репорт взят в работу"
                    color = 0x5865F2
                    self.audit(con, actor, "DISCORD_REPORT_TAKE", report_id)
                elif action == "close":
                    con.execute("""
                        UPDATE admin_requests
                        SET status='CLOSED', updated_at=?, closed_by=?, close_reason='Закрыто через Discord'
                        WHERE id=?
                    """, (now_ms(), actor, report_id))
                    title = "Репорт закрыт"
                    color = 0x57F287
                    self.audit(con, actor, "DISCORD_REPORT_CLOSE", report_id)
                else:
                    await interaction.response.send_message("Неизвестное действие.", ephemeral=True)
                    return

            emb = discord.Embed(title=title, description=f"ID репорта: `{report_id}`\nАдмин: `{interaction.user}`", color=color)
            await asyncio.to_thread(log_bridge_event, "discord", f"report_{action}", {"report_id": report_id, "actor": actor})
            await interaction.response.edit_message(embed=emb, view=None)
        except Exception as exc:
            await interaction.response.send_message(f"Ошибка обработки: {type(exc).__name__}: {exc}", ephemeral=True)

    async def update_server_status_channel(self) -> None:
        now = time.time()

        if not STATUS_CHANNEL_ID:
            print("Discord status channel: STATUS_CHANNEL_ID is empty", flush=True)
            return

        raw_online, reason = await asyncio.to_thread(check_server_online_sync)
        status_state = self.state.setdefault("server_status_channel", {})

        try:
            ch = await self.fetch_channel(int(STATUS_CHANNEL_ID))
        except Exception as exc:
            print(f"Не могу открыть статус-канал {STATUS_CHANNEL_ID}: {type(exc).__name__}: {exc}", flush=True)
            return

        current_name = str(getattr(ch, "name", "") or "")
        current_online = current_name.startswith("🟢")
        current_offline = current_name.startswith("🔴")
        base_name = clean_status_channel_name(current_name) or "copimine.ru"

        if raw_online:
            status_state["first_offline_at"] = 0
            if not status_state.get("first_online_at"):
                status_state["first_online_at"] = now
            stable_online = (now - float(status_state.get("first_online_at", now))) >= STATUS_ONLINE_CONFIRM_SECONDS
            stable_offline = False
        else:
            status_state["first_online_at"] = 0
            if not status_state.get("first_offline_at"):
                status_state["first_offline_at"] = now
            stable_online = False
            stable_offline = (now - float(status_state.get("first_offline_at", now))) >= STATUS_OFFLINE_CONFIRM_SECONDS

        if stable_online:
            desired_online = True
        elif stable_offline:
            desired_online = False
        else:
            if current_online:
                desired_online = True
            elif current_offline:
                desired_online = False
            else:
                desired_online = bool(raw_online)

        emoji = "🟢" if desired_online else "🔴"
        wanted_name = f"{emoji}-{base_name}"

        name_wrong = current_name != wanted_name
        next_allowed = float(status_state.get("next_rename_at", 0) or 0)

        status_state["raw_online"] = bool(raw_online)
        status_state["displayed_online_wanted"] = bool(desired_online)
        status_state["last_reason"] = reason
        status_state["base_name"] = base_name
        status_state["last_check"] = now_ms()
        status_state["wanted_name"] = wanted_name
        status_state["current_name"] = current_name

        last_logged = status_state.get("last_raw_online_logged")
        should_log = name_wrong or last_logged is None or bool(last_logged) != bool(raw_online)

        if should_log:
            status_state["last_raw_online_logged"] = bool(raw_online)
            wait_left = max(0, int(next_allowed - now))
            offline_for = 0
            if not raw_online:
                offline_for = int(now - float(status_state.get("first_offline_at", now)))
            print(
                f"Discord статус check: raw_online={raw_online}; stable_online={stable_online}; "
                f"stable_offline={stable_offline}; offline_for={offline_for}s; reason={reason}; "
                f"current={current_name}; wanted={wanted_name}; wait_left={wait_left}s",
                flush=True,
            )

        if not name_wrong:
            status_state["next_rename_at"] = 0
            return

        if now < next_allowed:
            return

        try:
            await asyncio.wait_for(
                ch.edit(name=wanted_name, reason=f"CopiMine Minecraft status: {reason}"),
                timeout=STATUS_EDIT_TIMEOUT_SECONDS,
            )
            status_state["current_name"] = wanted_name
            status_state["last_rename_ok_at"] = now_ms()
            status_state["next_rename_at"] = 0
            status_state["last_rename_error"] = ""
            await asyncio.to_thread(save_status_snapshot, STATUS_CHANNEL_ID, dict(status_state))
            await asyncio.to_thread(log_bridge_event, "discord-status", "server_status_channel_renamed", {"channelId": STATUS_CHANNEL_ID, "wanted": wanted_name, "reason": reason, "online": desired_online})
            print(f"Discord статус-канал обновлён: {current_name} -> {wanted_name}; reason={reason}", flush=True)
        except asyncio.TimeoutError:
            status_state["next_rename_at"] = now + CHANNEL_RENAME_MIN_SECONDS
            status_state["last_rename_error"] = f"Timeout waiting Discord channel edit; wanted={wanted_name}"
            print(
                f"Discord timeout переименования: retry_after={CHANNEL_RENAME_MIN_SECONDS}s; "
                f"wanted={wanted_name}; reason={reason}",
                flush=True,
            )
        except Exception as exc:
            retry_after = getattr(exc, "retry_after", None)
            try:
                retry_after = float(retry_after)
            except Exception:
                retry_after = float(CHANNEL_RENAME_MIN_SECONDS)
            retry_after = max(float(CHANNEL_RENAME_MIN_SECONDS), retry_after)
            status_state["next_rename_at"] = now + retry_after
            status_state["last_rename_error"] = f"{type(exc).__name__}: {exc}"
            print(
                f"Не смог переименовать статус-канал {STATUS_CHANNEL_ID}: {type(exc).__name__}: {exc}; "
                f"retry_after={retry_after}; wanted={wanted_name}; reason={reason}",
                flush=True,
            )

    def election_status_embed(self, snap: dict[str, Any]) -> discord.Embed:
        status = str(snap.get("status") or "NONE")
        showcase_mode = PUBLIC_ELECTION_SHOWCASE_V3
        color = 0x57F287 if status == "PRESIDENT_TERM" else 0xFEE75C if status in {"VOTING", "COUNTING", "SECOND_ROUND"} else 0x5865F2
        e = discord.Embed(
            title="CopiMine Elections",
            description=(
                "Публичная сводка выборов для игроков.\n"
                f"Этап: **{short(snap.get('stage_title') or status, 80)}**\n"
                f"Тур: **{short(str(snap.get('current_round') or 1), 32)}**"
            ),
            color=color,
            timestamp=discord.utils.utcnow(),
        )
        e.add_field(name="Президент", value=short(snap.get("president") or "—", 128), inline=True)
        e.add_field(name="Кандидаты", value=short(str(snap.get("candidates") or 0), 32), inline=True)
        top_candidates = snap.get("top_candidates") or []
        public_apps = candidate_application_summaries_v2(str(snap.get("election_id") or ""), top_candidates, 8)
        if public_apps:
            for i, row in enumerate(public_apps, 1):
                name = short(row.get("name") or "Кандидат", 80)
                statement = short(row.get("statement") or "Программа кандидата пока не опубликована.", 850)
                e.add_field(name=f"{i}. {name}", value=statement, inline=False)
        else:
            e.add_field(name="Кандидаты", value="Список кандидатов ещё не опубликован.", inline=False)
        laws = [short(x, 80) for x in (snap.get("laws") or []) if str(x or "").strip()]
        if laws:
            e.add_field(name="Законы президента", value="\n".join(f"• {law}" for law in laws[:5]), inline=False)
        e.set_footer(text=f"CopiMine • Discord показывает только публичные данные выборов • {showcase_mode}")
        return e

    async def update_elections_status_channel(self) -> None:
        if not ELECTIONS_STATUS_CHANNEL_ID:
            return
        ch = await self.channel(ELECTIONS_STATUS_CHANNEL_ID)
        if not ch:
            return
        snap = await asyncio.to_thread(election_overview_snapshot_v2)
        rec = self.state.setdefault("elections_status_channel", {})
        last_hash = str(rec.get("last_hash", ""))
        current_hash = json.dumps(snap, ensure_ascii=False, sort_keys=True)
        if current_hash == last_hash and rec.get("message_id"):
            return
        msg = None
        if rec.get("message_id"):
            msg = await self.fetch_message_safe(str(rec.get("channel_id", ELECTIONS_STATUS_CHANNEL_ID)), str(rec.get("message_id", "")))
        if msg:
            await msg.edit(embed=self.election_status_embed(snap))
            await asyncio.to_thread(log_notification, str(msg.channel.id), "elections_status", str(snap.get("election_id") or ""), "updated")
        else:
            msg = await ch.send(embed=self.election_status_embed(snap))
            await asyncio.to_thread(log_notification, str(msg.channel.id), "elections_status", str(snap.get("election_id") or ""), "created")
        rec.update({
            "channel_id": str(msg.channel.id),
            "message_id": str(msg.id),
            "last_hash": current_hash,
            "updated_at": now_ms(),
        })
        await asyncio.to_thread(save_status_snapshot, ELECTIONS_STATUS_CHANNEL_ID, {"type": "elections_status", "snapshot": snap, "messageId": rec["message_id"]})

    def admin_alerts_embed(self, rows: list[dict[str, Any]]) -> discord.Embed:
        e = discord.Embed(title="CopiMine admin alerts", color=0xED4245 if rows else 0x57F287, timestamp=discord.utils.utcnow())
        if not rows:
            e.description = "No active player checks."
        else:
            lines = []
            for row in rows:
                lines.append(
                    f"`{row.get('id')}` {short(row.get('player_name'), 48)} checked by "
                    f"{short(row.get('admin_name'), 48)} at {ts(row.get('time'))}"
                )
            e.description = "\n".join(lines)
        e.set_footer(text="Admin-only server messages stay in staff channels.")
        return e

    async def update_admin_alerts_channel(self) -> None:
        if not ADMIN_ALERTS_CHANNEL_ID:
            return
        ch = await self.channel(ADMIN_ALERTS_CHANNEL_ID)
        if not ch:
            return
        rows = await asyncio.to_thread(active_admin_alerts_snapshot)
        rec = self.state.setdefault("admin_alerts_channel", {})
        current_hash = json.dumps(rows, ensure_ascii=False, sort_keys=True)
        if current_hash == str(rec.get("last_hash", "")) and rec.get("message_id"):
            return
        msg = None
        if rec.get("message_id"):
            msg = await self.fetch_message_safe(str(rec.get("channel_id", ADMIN_ALERTS_CHANNEL_ID)), str(rec.get("message_id", "")))
        if msg:
            await msg.edit(embed=self.admin_alerts_embed(rows))
            await asyncio.to_thread(log_notification, str(msg.channel.id), "admin_alerts", "active_player_checks", "updated")
        else:
            msg = await ch.send(embed=self.admin_alerts_embed(rows))
            await asyncio.to_thread(log_notification, str(msg.channel.id), "admin_alerts", "active_player_checks", "created")
        rec.update({
            "channel_id": str(msg.channel.id),
            "message_id": str(msg.id),
            "last_hash": current_hash,
            "updated_at": now_ms(),
        })
        await asyncio.to_thread(save_status_snapshot, ADMIN_ALERTS_CHANNEL_ID, {"type": "admin_alerts", "rows": rows, "messageId": rec["message_id"]})

def clean_status_channel_name(name: str) -> str:
    text = str(name or "").strip()
    text = re.sub(r"^[🟢🔴]\s*[-_ ]*", "", text)
    text = re.sub(r"^(online|offline)\s*[-_ ]*", "", text, flags=re.I)
    return text.strip("-_ ") or "copimine.ru"

async def main() -> None:
    if not TOKEN:
        print("DISCORD_BOT_TOKEN is not set in .env", flush=True)
        while True:
            await asyncio.sleep(3600)
    if not acquire_single_instance_lock():
        print("Another CopiMine Discord bot instance already owns discord_bot.lock; this process will stay idle.", flush=True)
        while True:
            await asyncio.sleep(3600)
    backoff = DISCORD_RECONNECT_INITIAL_SECONDS
    while True:
        try:
            async with Bot() as bot:
                await bot.start(TOKEN)
            print(f"Discord bot stopped without an exception; reconnect in {backoff}s", flush=True)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            kind = type(exc).__name__
            print(f"Discord bot connection failed: {kind}: {exc}; reconnect in {backoff}s", flush=True)
            if kind in {"LoginFailure", "PrivilegedIntentsRequired"}:
                backoff = max(DISCORD_RECONNECT_MAX_SECONDS, 21600)
        await asyncio.sleep(backoff)
        backoff = min(backoff * 2, DISCORD_RECONNECT_MAX_SECONDS)

if __name__ == "__main__":
    asyncio.run(main())
