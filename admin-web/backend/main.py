from __future__ import annotations

import asyncio
import base64
from contextvars import ContextVar
import csv
import gzip
import hashlib
import hmac
import io
import json
import math
import os
import re
import secrets
import socket
import sqlite3
import struct
import subprocess
import threading
import time
import uuid
import zlib
import zipfile
import shutil
from contextlib import contextmanager
from datetime import datetime, timezone
from dataclasses import dataclass
from pathlib import Path
from queue import Empty, Queue
from typing import Any, Optional, Callable, Mapping
from urllib.parse import quote

VOTER_UUID_COL = "voter_" "uuid"
VOTER_NAME_COL = "voter_" "name"

try:
    import psycopg  # type: ignore
    from psycopg.rows import dict_row  # type: ignore
except Exception:  # pragma: no cover
    psycopg = None
    dict_row = None

try:
    import psutil  # type: ignore
except Exception:  # pragma: no cover
    psutil = None

try:
    import nbtlib  # type: ignore
except Exception:  # pragma: no cover
    nbtlib = None

try:
    import qrcode  # type: ignore
except Exception:  # pragma: no cover
    qrcode = None

try:
    import httpx  # type: ignore
except Exception:  # pragma: no cover
    httpx = None

try:
    import yaml  # type: ignore
except Exception:  # pragma: no cover
    yaml = None

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool
from starlette.exceptions import HTTPException as StarletteHTTPException
from .commerce_catalog import admin_gift_catalog_snapshot, ar_catalog_snapshot, donation_catalog_snapshot
from .download_manager import artifact_file_response, artifact_metadata
from .deploy_runtime import runtime_snapshot as managed_runtime_snapshot
from .envfile import load_env_file_to_os, resolve_env_file
from .startup_checks import run_startup_checks
from .yookassa_gateway import YooKassaGateway, YooKassaGatewayError, YooKassaSettings
from .plugin_registry import (
    PluginRegistryError,
    apply_registry_values,
    backup_registry_config,
    list_registry_plugins,
    read_registry_config,
    registry_schema,
    registry_status,
    require_registry_plugin,
    validate_registry_values,
)

APP_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = APP_ROOT.parent
FRONTEND_DIR = APP_ROOT / "frontend"
load_env_file_to_os(resolve_env_file(APP_ROOT / ".env"))
DATA_DIR = Path(os.getenv("COPIMINE_ADMIN_DATA", APP_ROOT / "data"))
DATA_DIR.mkdir(parents=True, exist_ok=True)
THIRDPARTY_DIR = PROJECT_ROOT / "thirdparty"
MODPACK_ZIP = THIRDPARTY_DIR / "CopiMineMods.zip"
MODPACK_SHA1_FILE = THIRDPARTY_DIR / "CopiMineMods.sha1"
MODPACK_MANIFEST = THIRDPARTY_DIR / "modpack_manifest.json"
THIRDPARTY_MANIFEST = THIRDPARTY_DIR / "thirdparty_manifest.json"
ARTIFACTS_ITEMS_FILE = PROJECT_ROOT / "copimine-artifacts" / "items.yml"
NARCOTICS_SOURCE_CONFIG_FILE = PROJECT_ROOT / "copimine-narcotics" / "config.yml"
DONATION_FIXED_PACKS = (50, 100, 250, 500, 1000)
YOOKASSA_SETTINGS = YooKassaSettings.from_env()
DONATION_PROVIDER = "YOOKASSA" if YOOKASSA_SETTINGS.enabled else "MOCK_SBP"
DONATION_SESSION_TTL_SECONDS = int(os.getenv("DONATION_SESSION_TTL_SECONDS", str(15 * 60)))
DONATION_SESSION_TTL_MS = DONATION_SESSION_TTL_SECONDS * 1000
DONATION_EPOCH_MS_THRESHOLD = 100_000_000_000
MANAGED_RESOURCEPACK_URL = os.getenv(
    "COPIMINE_RESOURCEPACK_URL",
    "http://admin.copimine.ru:18080/resourcepacks/CopiMineResourcePack.zip",
).strip()
MANAGED_RESOURCEPACK_ZIP = PROJECT_ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"
MANAGED_RESOURCEPACK_SHA1_FILE = PROJECT_ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.sha1"
OWNER_ONLY_SERVER_PROPERTY_KEYS = {
    "resource-pack",
    "resource-pack-sha1",
    "require-resource-pack",
    "resource-pack-prompt",
    "enable-rcon",
    "rcon.port",
    "server-port",
    "online-mode",
    "enable-command-block",
}
GENERAL_RATE_BUCKETS: dict[str, list[int]] = {}
PLUGIN_REGISTRY_MANIFEST = APP_ROOT / "backend" / "plugin_registry_manifest.json"
APP_VERSION = "2.2.0"
STARTUP_STRICT = os.getenv("COPIMINE_STARTUP_STRICT", "1").lower() in {"1", "true", "yes", "on"}


def default_world_dir(server_dir: Path) -> Path:
    configured_level = str(os.getenv("MC_LEVEL_NAME", "")).strip()
    if configured_level:
        return server_dir / configured_level
    props_path = server_dir / "server.properties"
    if props_path.exists():
        try:
            for raw in props_path.read_text(encoding="utf-8", errors="replace").splitlines():
                line = raw.strip()
                if line.startswith("level-name="):
                    level_name = line.split("=", 1)[1].strip()
                    if level_name:
                        return server_dir / level_name
        except Exception:
            pass
    return server_dir / "world"

# No built-in real admins: production access is loaded from .env or data/admin_users.json.
# Runtime roles are owner / admin / junior_admin / player.
DEFAULT_ADMIN_USERS: dict[str, dict[str, str]] = {}
SECRET_KEY = os.getenv("SECRET_KEY", secrets.token_hex(32))
PLUGIN_API_KEY = os.getenv("PLUGIN_API_KEY", "")
REQUIRE_OP_FOR_LOGIN = os.getenv("REQUIRE_OP_FOR_LOGIN", "1").lower() in {"1", "true", "yes", "on"}
REQUIRE_WHITELIST_FOR_LOGIN = os.getenv("REQUIRE_WHITELIST_FOR_LOGIN", "1").lower() in {"1", "true", "yes", "on"}
SESSION_TTL_SECONDS = int(os.getenv("SESSION_TTL_SECONDS", str(60 * 60 * 8)))
ACCESS_TOKEN_TTL_SECONDS = int(os.getenv("ACCESS_TOKEN_TTL_SECONDS", str(15 * 60)))
REFRESH_TOKEN_TTL_SECONDS = int(os.getenv("REFRESH_TOKEN_TTL_SECONDS", str(60 * 60 * 24 * 30)))
LOGIN_MAX_ATTEMPTS = int(os.getenv("LOGIN_MAX_ATTEMPTS", "5"))
LOGIN_LOCK_SECONDS = int(os.getenv("LOGIN_LOCK_SECONDS", "900"))
PIN_MAX_ATTEMPTS = int(os.getenv("PIN_MAX_ATTEMPTS", "5"))
PIN_LOCK_SECONDS = int(os.getenv("PIN_LOCK_SECONDS", "900"))
PIN_ATTEMPT_WINDOW_SECONDS = int(os.getenv("PIN_ATTEMPT_WINDOW_SECONDS", "900"))
TEMP_PIN_TTL_SECONDS = int(os.getenv("TEMP_PIN_TTL_SECONDS", str(60 * 60 * 24 * 7)))
TEMP_PIN_LENGTH = int(os.getenv("TEMP_PIN_LENGTH", "6"))
# Preserve the legacy account id for data compatibility while treating it as a
# dedicated treasury account rather than a president-personal account.
TREASURY_ACCOUNT_ID = "PRESIDENT_BUDGET"
TREASURY_ACCOUNT_TYPE = "TREASURY"
TREASURY_ACCOUNT_LABEL = "Казна CopiMine"
ALLOWED_ORIGINS = [x.strip() for x in os.getenv("ALLOWED_ORIGINS", "").split(",") if x.strip()]
DB_WRITE_ENABLED = os.getenv("DB_WRITE_ENABLED", "0").lower() in {"1", "true", "yes", "on"}
ADMIN_DB_WRITE_ALLOWLIST = {x.strip() for x in os.getenv("ADMIN_DB_WRITE_ALLOWLIST", "").split(",") if x.strip()}
DB_WRITE_PROTECTED_TABLE_PATTERNS: tuple[tuple[str, str], ...] = (
    ("cmv731_votes", "голоса считаются только через участок и журнал голосования"),
    ("cmv731_vote_sessions", "запечатанные бюллетени закрываются только election recovery API"),
    ("cmv7_ballot_issues", "бюллетени выдаются и аннулируются через отдельные инструменты ЦИК"),
    ("cmv7_application_issues", "книги заявок выдаются и аннулируются через отдельные инструменты ЦИК"),
    ("elections", "ядро выборов управляется только через CopiMineElectionCore"),
    ("candidate_applications", "заявки кандидатов выдаются и рассматриваются только через workflow выборов"),
    ("candidates", "кандидаты формируются только после одобрения заявок"),
    ("ballots", "бюллетени выдаются, подтверждаются и сдаются только через участок ЦИК"),
    ("votes", "тайна голосования и подсчёт защищены election runtime"),
    ("polling_stations", "участки ЦИК создаются и снимаются только через игровой GUI"),
    ("cik_chairs", "председатели ЦИК назначаются только через игровой GUI"),
    ("cik_seals", "печати ЦИК выпускаются только через реестр выборов"),
    ("president_terms", "президентский срок меняется только через выборы и мандат"),
    ("president_laws", "законы президента проходят только через workflow мандата и одобрения"),
    ("president_taxes", "налоги президента настраиваются только через выборный workflow"),
    ("president_tax_payments", "оплаты налога записываются только через игровые и сайтовые платёжные потоки"),
    ("protected_blocks", "защищённые блоки меняются только через игровые сервисы"),
    ("protected_block_visuals", "визуальные модели специальных блоков восстанавливаются только сервером"),
    ("text_display_links", "надписи специальных блоков привязываются только сервером"),
    ("round_candidates", "кандидаты тура обновляются только election runtime"),
    ("donation_accounts", "донат-баланс меняется только через EconomyCore и безопасные admin endpoints"),
    ("donation_balance_ledger", "журнал донат-баланса является финансовым аудитом"),
    ("donation_payment_sessions", "сессии оплаты создаются только через mock donation workflow"),
    ("donation_purchases", "донат-покупки создаются только через mock donation workflow"),
    ("donation_item_claims", "выдача донатных предметов ведётся только через claims workflow"),
    ("cmv7_ar_", "АР-экономика защищена от ручных правок баланса, активов и транзакций"),
    ("cmv7_audit", "аудит является неизменяемым журналом"),
    ("audit", "аудит является неизменяемым журналом"),
    ("cmv7_official_item_bindings", "официальные предметы перевыпускаются только через реестр"),
    ("cmv7_president_state", "мандат президента меняется через выборы или президентский workflow"),
    ("cmv7_election_curators", "председатель ЦИК и кураторы назначаются через GUI/API"),
    ("cmv7_inventory_snapshots", "live-снимки инвентаря пишет только серверный плагин"),
    ("cmv7_player_activity", "timeline игроков пишет только серверный плагин"),
    ("cmv8_startup_checks", "startup-чек пишет только серверный плагин"),
)
MC_SERVER_DIR = Path(os.getenv("MC_SERVER_DIR", "/opt/copimine/minecraft/server"))
MC_HOST = os.getenv("MC_HOST", "127.0.0.1")
MC_PORT = int(os.getenv("MC_PORT", "25565"))
MC_PUBLIC_ADDRESS = os.getenv("MC_PUBLIC_ADDRESS", "").strip()
MC_PUBLIC_VERSION = os.getenv("MC_PUBLIC_VERSION", "").strip()
RCON_HOST = os.getenv("RCON_HOST", "127.0.0.1")
RCON_PORT = int(os.getenv("RCON_PORT", "25575"))
RCON_PASSWORD = os.getenv("RCON_PASSWORD", "")
MINECRAFT_SERVICE = os.getenv("MINECRAFT_SERVICE", "copimine-minecraft")
COREPROTECT_DB = os.getenv("COREPROTECT_DB", str(MC_SERVER_DIR / "plugins" / "CoreProtect" / "database.db"))
ADMIN_PLUGIN_DB = os.getenv("ADMIN_PLUGIN_DB", str(MC_SERVER_DIR / "plugins" / "CopiMineUltimateAdmin" / "copimine_ultimate.db"))
AR_ITEM_IDS = [x.strip() for x in os.getenv("AR_ITEM_IDS", "minecraft:amethyst_shard,AMETHYST_SHARD").split(",") if x.strip()]
LOG_FILE = Path(os.getenv("MC_LOG_FILE", MC_SERVER_DIR / "logs" / "latest.log"))
WORLD_DIR = Path(os.getenv("MC_WORLD_DIR", default_world_dir(MC_SERVER_DIR)))
MAX_WORLD_REGION_FILES = int(os.getenv("MAX_WORLD_REGION_FILES", "24"))
MAX_WORLD_CHUNKS = int(os.getenv("MAX_WORLD_CHUNKS", "1200"))
BACKUPS_DIR = Path(os.getenv("COPIMINE_BACKUPS_DIR", APP_ROOT / "backups"))
PLUGIN_REGISTRY_BACKUPS_DIR = BACKUPS_DIR / "plugin-registry"
BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
MINECRAFT_ASSETS_VERSION = os.getenv("MINECRAFT_ASSETS_VERSION", "1.21.1")
FAILED_LOGINS: dict[str, list[int]] = {}
LOCKED_UNTIL: dict[str, int] = {}
SESSIONS_FILE = DATA_DIR / "sessions.json"
ADMIN_USERS_FILE = DATA_DIR / "admin_users.json"
AUTH_DB_FILE = Path(os.getenv("COPIMINE_AUTH_DB", DATA_DIR / "admin_auth.db"))
AUTH_STORAGE_MODE_RAW = os.getenv("COPIMINE_AUTH_STORAGE", "").strip().lower()
POSTGRES_HOST = os.getenv("POSTGRES_HOST", os.getenv("PGHOST", "127.0.0.1"))
POSTGRES_PORT = int(os.getenv("POSTGRES_PORT", os.getenv("PGPORT", "5432")))
POSTGRES_DB = os.getenv("POSTGRES_DB", os.getenv("PGDATABASE", "copimine"))
POSTGRES_USER = os.getenv("POSTGRES_USER", os.getenv("PGUSER", "copimine"))
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", os.getenv("PGPASSWORD", ""))
POSTGRES_SCHEMA = os.getenv("POSTGRES_SCHEMA", os.getenv("PGSCHEMA", "copimine"))
POSTGRES_CONNECT_TIMEOUT = int(os.getenv("POSTGRES_CONNECT_TIMEOUT", "5"))
POSTGRES_POOL_MIN_SIZE = max(1, int(os.getenv("POSTGRES_POOL_MIN_SIZE", "1")))
POSTGRES_POOL_MAX_SIZE = max(POSTGRES_POOL_MIN_SIZE, int(os.getenv("POSTGRES_POOL_MAX_SIZE", "8")))
# A local installation created before PostgreSQL support already has this SQLite
# database. Do not make that installation unusable merely because its .env has
# not yet been migrated. Production instances explicitly select PostgreSQL.
AUTH_STORAGE_MODE = (
    AUTH_STORAGE_MODE_RAW
    if AUTH_STORAGE_MODE_RAW in {"postgresql", "sqlite"}
    else ("postgresql" if POSTGRES_PASSWORD else "sqlite")
)
AUTH_COOKIE_NAME = os.getenv("AUTH_COOKIE_NAME", "cm_session")
AUTH_REFRESH_COOKIE_NAME = os.getenv("AUTH_REFRESH_COOKIE_NAME", "cm_refresh")
ADMIN_PUBLIC_BASE_URL = os.getenv("ADMIN_PUBLIC_BASE_URL", "http://admin.copimine.ru:18080").rstrip("/")
AUTH_COOKIE_SECURE_RAW = os.getenv("AUTH_COOKIE_SECURE", "").strip().lower()
AUTH_COOKIE_SECURE = (
    AUTH_COOKIE_SECURE_RAW in {"1", "true", "yes", "on"}
    if AUTH_COOKIE_SECURE_RAW
    else ADMIN_PUBLIC_BASE_URL.lower().startswith("https://")
)


def resolve_http_auth_setting(raw_value: Optional[str], public_base_url: str) -> bool:
    """Resolve HTTP login from an explicit setting or the configured scheme.

    The public panel is currently served over HTTP in a number of supported
    installations.  In that mode an omitted flag must not make login
    unusable: the URL itself is the operator's explicit transport choice.
    Once the public URL is HTTPS, the safe default is to reject HTTP login.
    An explicit value always wins so deployments can stage a migration.
    """
    raw = str(raw_value or "").strip().lower()
    if raw:
        return raw in {"1", "true", "yes", "on"}
    return not str(public_base_url or "").strip().lower().startswith("https://")


# Public HTTP remains available for the site, files and status endpoints.  The
# login transport follows ADMIN_PUBLIC_BASE_URL when the setting is omitted;
# explicit ALLOW_INSECURE_HTTP_AUTH values still override it.
ALLOW_INSECURE_HTTP_AUTH = resolve_http_auth_setting(os.getenv("ALLOW_INSECURE_HTTP_AUTH"), ADMIN_PUBLIC_BASE_URL)
AUTH_BEARER_FALLBACK_ENABLED = os.getenv("AUTH_BEARER_FALLBACK_ENABLED", "0").lower() in {"1", "true", "yes", "on"}
CSRF_COOKIE_NAME = os.getenv("CSRF_COOKIE_NAME", "cm_csrf")
CSRF_HEADER_NAME = "X-CSRF-Token"
SENSITIVE_CONFIRM_HEADER = "X-Copimine-Confirm"
TOKEN_ISSUER = os.getenv("AUTH_TOKEN_ISSUER", "copimine-admin-web")
AUDIT_LOG_FILE = DATA_DIR / "audit_log.jsonl"
EVENT_LOG_FILE = DATA_DIR / "plugin_events.jsonl"
ECONOMY_SNAPSHOTS_FILE = DATA_DIR / "economy_snapshots.jsonl"
INVENTORY_SNAPSHOTS_DIR = DATA_DIR / "inventory_snapshots"
DISCORD_STATE_FILE = DATA_DIR / "discord_state.json"
DISCORD_OUTBOX_FILE = DATA_DIR / "discord_outbox.json"
DISCORD_ACTIONS_FILE = DATA_DIR / "discord_actions.jsonl"
DISCORD_APPLICATIONS_FILE = DATA_DIR / "discord_applications.json"
DISCORD_REPORTS_FILE = DATA_DIR / "discord_reports.json"
DISCORD_BOT_TOKEN_CONFIGURED = bool(os.getenv("DISCORD_BOT_TOKEN", "").strip())
DISCORD_GUILD_ID = os.getenv("DISCORD_GUILD_ID", "").strip()
DISCORD_APPLICATIONS_CHANNEL_ID = os.getenv("DISCORD_APPLICATIONS_CHANNEL_ID", "").strip()
DISCORD_REPORTS_CHANNEL_ID = os.getenv("DISCORD_REPORTS_CHANNEL_ID", "").strip()
DISCORD_ADMIN_ROLE_ID = os.getenv("DISCORD_ADMIN_ROLE_ID", "").strip()
DISCORD_ADMIN_ALLOWLIST = {x.strip() for x in os.getenv("DISCORD_ADMIN_ALLOWLIST", "").split(",") if x.strip()}
DISCORD_BOT_API_KEY = os.getenv("DISCORD_BOT_API_KEY", "")
DISCORD_RATE_BUCKETS: dict[str, list[int]] = {}
TRUSTED_PROXY_IPS = {x.strip() for x in os.getenv("TRUSTED_PROXY_IPS", "127.0.0.1,::1").split(",") if x.strip()}
RCON_WEB_COMMAND_ALLOWLIST = [
    x.strip().lower()
    for x in os.getenv("RCON_WEB_COMMAND_ALLOWLIST", "list,tps,mspt,say,save-all,time query,weather query").split(",")
    if x.strip()
]
SYSTEMD_SERVICES = [
    x.strip()
    for x in os.getenv(
        "COPIMINE_SYSTEMD_SERVICES",
        "copimine-minecraft,copimine-admin,copimine-discord-bot,nginx,tailscaled,ssh",
    ).split(",")
    if x.strip()
]
ALLOWED_ADMIN_ROLES = {"admin"}
_SQLITE_ADD_COLUMN_IF_NOT_EXISTS_RE = re.compile(
    r"^\s*ALTER\s+TABLE\s+(?P<table>[A-Za-z_][A-Za-z0-9_]*)\s+ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS\s+(?P<column>[A-Za-z_][A-Za-z0-9_]*)\s+(?P<definition>.+?)\s*;?\s*$",
    re.I | re.S,
)


class _PooledPgConnection:
    def __init__(self, pool: Optional["SimplePgPool"], conn: Any) -> None:
        self._pool = pool
        self._conn = conn
        self._closed = False

    def __getattr__(self, name: str) -> Any:
        return getattr(self._conn, name)

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        if self._pool is not None:
            self._pool.release(self._conn)
        else:
            self._conn.close()

    def __enter__(self) -> "_PooledPgConnection":
        return self

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> bool:
        try:
            if exc_type is None:
                self._conn.commit()
            else:
                self._conn.rollback()
        finally:
            self.close()
        return False


class SimplePgPool:
    def __init__(self, min_size: int, max_size: int) -> None:
        self.min_size = max(1, min_size)
        self.max_size = max(self.min_size, max_size)
        self._queue: Queue[Any] = Queue(maxsize=self.max_size)
        self._lock = threading.Lock()
        self._created = 0
        self._closed = False
        for _ in range(self.min_size):
            self._queue.put(self._create())

    def _create(self) -> Any:
        conn = _new_pg_connection()
        self._created += 1
        return conn

    def acquire(self) -> _PooledPgConnection:
        if self._closed:
            raise RuntimeError("PostgreSQL pool is closed")
        try:
            conn = self._queue.get_nowait()
            return _PooledPgConnection(self, conn)
        except Empty:
            with self._lock:
                if self._created < self.max_size:
                    return _PooledPgConnection(self, self._create())
            conn = self._queue.get(timeout=max(1, POSTGRES_CONNECT_TIMEOUT))
            return _PooledPgConnection(self, conn)

    def release(self, conn: Any) -> None:
        try:
            conn.rollback()
        except Exception:
            try:
                conn.close()
            finally:
                with self._lock:
                    self._created = max(0, self._created - 1)
            return
        if self._closed:
            try:
                conn.close()
            finally:
                with self._lock:
                    self._created = max(0, self._created - 1)
            return
        try:
            self._queue.put_nowait(conn)
        except Exception:
            try:
                conn.close()
            finally:
                with self._lock:
                    self._created = max(0, self._created - 1)

    def close(self) -> None:
        self._closed = True
        while True:
            try:
                conn = self._queue.get_nowait()
            except Empty:
                break
            try:
                conn.close()
            finally:
                with self._lock:
                    self._created = max(0, self._created - 1)


_PG_POOL: Optional[SimplePgPool] = None
_STARTUP_REPORT: dict[str, Any] = {}


class LoginIn(BaseModel):
    username: str
    password: str
    remember_me: bool = False


class TicketIn(BaseModel):
    player: str = Field(default="unknown", max_length=64)
    message: str = Field(max_length=4000)
    kind: str = Field(default="report", max_length=32)
    uuid: Optional[str] = None
    target: Optional[str] = Field(default=None, max_length=64)
    world: Optional[str] = None
    x: Optional[float] = None
    y: Optional[float] = None
    z: Optional[float] = None
    severity: str = Field(default="normal", max_length=40)
    attached_events: list[dict[str, Any]] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class CommandIn(BaseModel):
    command: str = Field(max_length=500)


class PlayerActionIn(BaseModel):
    player: Optional[str] = None
    reason: Optional[str] = None
    duration: Optional[str] = None
    target: Optional[str] = None
    x: Optional[float] = None
    y: Optional[float] = None
    z: Optional[float] = None


class ResolveTicketIn(BaseModel):
    status: str = "closed"
    admin_note: str = ""


class ServerControlIn(BaseModel):
    action: str
    message: Optional[str] = None


class ElectionControlIn(BaseModel):
    action: str = Field(max_length=80)
    message: Optional[str] = Field(default=None, max_length=500)


class ElectionEmergencyIn(BaseModel):
    player: Optional[str] = Field(default=None, max_length=32)
    id: Optional[str] = Field(default=None, max_length=96)
    reason: Optional[str] = Field(default="web-emergency", max_length=180)


class ElectionApplicationReviewIn(BaseModel):
    decision: str = Field(max_length=24)
    note: str = Field(default="", max_length=240)
    create_candidate: bool = True


class ElectionDecreeIn(BaseModel):
    title: str = Field(min_length=3, max_length=180)
    body: str = Field(min_length=3, max_length=6000)
    president_uuid: str = Field(default="", max_length=64)
    status: str = Field(default="PUBLISHED", max_length=32)


class ElectionPetitionIn(BaseModel):
    title: str = Field(min_length=3, max_length=180)
    body: str = Field(min_length=3, max_length=6000)
    creator_uuid: str = Field(default="", max_length=64)
    status: str = Field(default="OPEN", max_length=32)


class PlayerRegisterIn(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=8, max_length=128)
    minecraft_name: Optional[str] = Field(default=None, max_length=16)
    remember_me: bool = False


class PlayerLoginIn(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=1, max_length=128)
    remember_me: bool = False


class PlayerLinkRequestIn(BaseModel):
    minecraft_name: str = Field(min_length=3, max_length=16)


class PlayerLinkConfirmIn(BaseModel):
    code: str = Field(min_length=6, max_length=16)


class PlayerRecoveryStartIn(BaseModel):
    minecraft_name: str = Field(min_length=3, max_length=16)


class PlayerRecoveryConfirmIn(BaseModel):
    minecraft_name: str = Field(min_length=3, max_length=16)
    code: str = Field(min_length=6, max_length=16)
    new_password: str = Field(min_length=8, max_length=128)
    remember_me: bool = False


class PlayerPinSetIn(BaseModel):
    old_pin: Optional[str] = Field(default=None, min_length=4, max_length=8)
    new_pin: str = Field(min_length=4, max_length=8)
    account_scope: str = Field(default="PERSONAL", min_length=4, max_length=32)


class PlayerBankTransferIn(BaseModel):
    recipient: str = Field(min_length=3, max_length=64)
    amount: int = Field(gt=0, le=1000000000)
    pin: str = Field(min_length=4, max_length=8)
    note: Optional[str] = Field(default="", max_length=160)
    from_account: str = Field(default="PERSONAL", min_length=4, max_length=32)
    idempotency_key: str = Field(min_length=8, max_length=120)


class PlayerElectionTaxPayIn(BaseModel):
    amount: int = Field(gt=0, le=1000000000)
    pin: str = Field(min_length=4, max_length=8)


class PlayerDonationClaimIn(BaseModel):
    claim_id: str = Field(min_length=8, max_length=120)


class PlayerDonationSessionCreateIn(BaseModel):
    amount: int
    idempotency_key: str = Field(min_length=8, max_length=120)


class PlayerDonationPurchaseIntentIn(BaseModel):
    item_id: str = Field(min_length=2, max_length=120)
    pin: str = Field(min_length=4, max_length=8)
    idempotency_key: str = Field(min_length=8, max_length=120)


class PlayerArPurchaseIntentIn(BaseModel):
    item_id: str = Field(min_length=2, max_length=120)
    pin: str = Field(min_length=4, max_length=8)
    idempotency_key: str = Field(min_length=8, max_length=120)


class PlayerCartCheckoutIn(BaseModel):
    item_ids: list[str] = Field(min_length=1, max_length=12)
    pin: str = Field(min_length=4, max_length=8)
    expected_total: int = Field(gt=0, le=1000000000)
    idempotency_key: str = Field(min_length=8, max_length=120)


class PlayerSupportReportIn(BaseModel):
    target: Optional[str] = Field(default="", max_length=64)
    message: str = Field(min_length=8, max_length=5000)
    world: Optional[str] = Field(default="", max_length=80)
    severity: str = Field(default="normal", max_length=40)
    attached_events: list[dict[str, Any]] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class PlayerUsernameChangeIn(BaseModel):
    new_username: str = Field(min_length=3, max_length=32)
    current_password: str = Field(min_length=1, max_length=128)


class PlayerPasswordChangeIn(BaseModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=8, max_length=128)


class AdminPlayerAccountUpdateIn(BaseModel):
    username: Optional[str] = Field(default=None, min_length=3, max_length=32)
    new_password: Optional[str] = Field(default=None, min_length=8, max_length=128)


class AdminWhitelistApproveIn(BaseModel):
    request_id: str = Field(min_length=8, max_length=120)
    note: str = Field(default="", max_length=240)


class AdminArBalanceIn(BaseModel):
    minecraft_uuid: str = Field(min_length=32, max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    amount: int = Field(gt=0, le=1000000000)
    reason: str = Field(min_length=2, max_length=160)
    idempotency_key: str = Field(min_length=8, max_length=120)


class AdminDonationBalanceIn(BaseModel):
    minecraft_uuid: str = Field(min_length=32, max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    amount: int = Field(gt=0, le=1000000000)
    reason: str = Field(min_length=2, max_length=160)
    idempotency_key: str = Field(min_length=8, max_length=120)


class AdminArBalanceSetIn(BaseModel):
    minecraft_uuid: str = Field(min_length=32, max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    balance: int = Field(ge=0, le=1000000000)
    reason: str = Field(min_length=2, max_length=160)
    idempotency_key: str = Field(min_length=8, max_length=120)


class AdminDonationBalanceSetIn(BaseModel):
    minecraft_uuid: str = Field(min_length=32, max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    balance: int = Field(ge=0, le=1000000000)
    reason: str = Field(min_length=2, max_length=160)
    idempotency_key: str = Field(min_length=8, max_length=120)


class AdminDonationTestPurchaseIn(BaseModel):
    minecraft_uuid: str = Field(min_length=32, max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    item_id: str = Field(min_length=2, max_length=120)


class AdminArtifactGiftIn(BaseModel):
    minecraft_uuid: Optional[str] = Field(default="", max_length=64)
    minecraft_name: str = Field(min_length=3, max_length=16)
    item_id: str = Field(min_length=2, max_length=120)
    category: str = Field(default="AR", min_length=2, max_length=20)
    note: str = Field(default="admin-player-page", min_length=2, max_length=160)
    idempotency_key: str = Field(min_length=8, max_length=120)


class AdminDonationSessionActionIn(BaseModel):
    note: str = Field(default="", max_length=240)


class SiteCmsEntryIn(BaseModel):
    entry_key: str = Field(min_length=2, max_length=80)
    section: str = Field(min_length=2, max_length=40)
    title: str = Field(min_length=1, max_length=160)
    body: str = Field(default="", max_length=3000)
    image_path: str = Field(default="", max_length=240)
    link_url: str = Field(default="", max_length=240)
    sort_order: int = Field(default=100, ge=0, le=100000)
    enabled: bool = True


class PluginRegistryConfigIn(BaseModel):
    values: dict[str, Any] = Field(default_factory=dict)


class NarcoticsRecipesIn(BaseModel):
    recipes: dict[str, list[str]] = Field(default_factory=dict)
    apply_mode: str = Field(default="save", min_length=4, max_length=24)


class PropertiesPatchIn(BaseModel):
    values: dict[str, str]


class AdminAccessIn(BaseModel):
    username: str = Field(min_length=3, max_length=16)
    password: str = Field(min_length=8, max_length=128)
    role: str = Field(default="admin", max_length=32)
    ensure_op: bool = False
    ensure_whitelist: bool = False


class AdminUpdateIn(BaseModel):
    password: Optional[str] = Field(default=None, min_length=8, max_length=128)
    role: Optional[str] = Field(default=None, max_length=32)
    enabled: Optional[bool] = None
    ensure_op: bool = False
    ensure_whitelist: bool = False


class AccessListActionIn(BaseModel):
    action: str
    player: str = Field(min_length=3, max_length=16)
    reason: Optional[str] = None


class BackupCreateIn(BaseModel):
    scope: str = "configs"
    include_logs: bool = False
    include_world: bool = False


class ResourcePackApplyIn(BaseModel):
    url: str
    sha1: str = ""
    required: bool = True
    prompt: str = "Для игры на CopiMine нужен ресурспак сервера."


class PluginEventIn(BaseModel):
    source: str = Field(default="unknown", max_length=80)
    event_type: str = Field(max_length=120)
    actor: Optional[str] = Field(default=None, max_length=120)
    target: Optional[str] = Field(default=None, max_length=120)
    world: Optional[str] = Field(default=None, max_length=80)
    x: Optional[float] = None
    y: Optional[float] = None
    z: Optional[float] = None
    item: Optional[str] = Field(default=None, max_length=160)
    block: Optional[str] = Field(default=None, max_length=160)
    severity: str = Field(default="info", max_length=24)
    tags: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)
    timestamp: Optional[int] = None


class DiscordHeartbeatIn(BaseModel):
    status: str = "online"
    guild_id: Optional[str] = None
    guild_name: Optional[str] = None
    latency_ms: Optional[float] = None
    application_channel_ok: bool = False
    reports_channel_ok: bool = False
    details: dict[str, Any] = Field(default_factory=dict)


class DiscordOutboxPatchIn(BaseModel):
    status: str = "sent"
    message_id: Optional[str] = None
    channel_id: Optional[str] = None
    thread_id: Optional[str] = None
    error: Optional[str] = None
    retry_count: Optional[int] = None
    next_retry_at: Optional[int] = None


class DiscordActionIn(BaseModel):
    action: str = Field(max_length=80)
    object_type: str = Field(max_length=40)
    object_id: str = Field(max_length=80)
    discord_user_id: str = Field(max_length=80)
    discord_username: Optional[str] = Field(default=None, max_length=120)
    note: Optional[str] = Field(default=None, max_length=1000)
    metadata: dict[str, Any] = Field(default_factory=dict)


class DiscordApplicationIn(BaseModel):
    player: str = Field(min_length=1, max_length=64)
    uuid: Optional[str] = Field(default=None, max_length=80)
    age: Optional[str] = Field(default=None, max_length=80)
    experience: Optional[str] = Field(default=None, max_length=1200)
    questionnaire: Optional[str] = Field(default=None, max_length=4000)
    why: Optional[str] = Field(default=None, max_length=4000)
    discord_user_id: Optional[str] = Field(default=None, max_length=80)
    discord_username: Optional[str] = Field(default=None, max_length=120)
    status: str = Field(default="pending", max_length=40)
    metadata: dict[str, Any] = Field(default_factory=dict)


class DiscordReportIn(BaseModel):
    reporter: str = Field(min_length=1, max_length=64)
    reporter_uuid: Optional[str] = Field(default=None, max_length=80)
    target: Optional[str] = Field(default=None, max_length=64)
    target_uuid: Optional[str] = Field(default=None, max_length=80)
    message: str = Field(min_length=1, max_length=5000)
    world: Optional[str] = Field(default=None, max_length=80)
    x: Optional[float] = None
    y: Optional[float] = None
    z: Optional[float] = None
    severity: str = Field(default="normal", max_length=40)
    attached_events: list[dict[str, Any]] = Field(default_factory=list)
    discord_user_id: Optional[str] = Field(default=None, max_length=80)
    discord_username: Optional[str] = Field(default=None, max_length=120)
    investigation_id: Optional[str] = Field(default=None, max_length=80)
    status: str = Field(default="open", max_length=40)
    metadata: dict[str, Any] = Field(default_factory=dict)


class DiscordObjectStatusIn(BaseModel):
    status: str = Field(max_length=40)
    reason: Optional[str] = Field(default=None, max_length=1200)
    response: Optional[str] = Field(default=None, max_length=2000)
    discord_user_id: Optional[str] = Field(default=None, max_length=80)
    discord_username: Optional[str] = Field(default=None, max_length=120)
    metadata: dict[str, Any] = Field(default_factory=dict)


class DiscordReplyIn(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    author: Optional[str] = Field(default=None, max_length=120)
    discord_user_id: Optional[str] = Field(default=None, max_length=80)
    visibility: str = Field(default="internal", max_length=40)
    metadata: dict[str, Any] = Field(default_factory=dict)


def safe_mapping(value: Any) -> dict[str, Any]:
    return dict(value) if isinstance(value, dict) else {}


def clip_text(value: Any, limit: int = 800) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 3)] + "..."


TECHNICAL_REPORT_SOURCES = {"plugin"}
BUG_REPORT_MESSAGE_RE = re.compile(r"^\s*\[BUG\s+([A-Z0-9-]{4,80})\]", re.IGNORECASE)


def report_metadata_without_technical_context(metadata: Any) -> dict[str, Any]:
    """Keep only non-technical provenance that a normal player may submit."""
    raw = safe_mapping(metadata)
    allowed: dict[str, Any] = {}
    source_page = clip_text(raw.get("sourcePage"), 120)
    if source_page:
        allowed["sourcePage"] = source_page
    site_account_id = clip_text(raw.get("siteAccountId"), 80)
    if site_account_id:
        allowed["siteAccountId"] = site_account_id
    site_username = clip_text(raw.get("siteUsername"), 64)
    if site_username:
        allowed["siteUsername"] = site_username
    if raw.get("legacyTicket") is True:
        allowed["legacyTicket"] = True
    return allowed


def technical_bug_report_is_correlated(metadata: Any, message: str) -> bool:
    """Accept a plugin bug snapshot only when it matches the visible bug token."""
    raw = safe_mapping(metadata)
    bug = safe_mapping(raw.get("bugReport"))
    code = clip_text(bug.get("errorCode") or raw.get("errorCode") or bug.get("token") or raw.get("token"), 80).upper()
    match = BUG_REPORT_MESSAGE_RE.match(str(message or ""))
    diagnostics = safe_mapping(bug.get("diagnostics"))
    request_id = clip_text(diagnostics.get("requestId") or raw.get("requestId"), 160)
    if not code or not re.fullmatch(r"[A-Z0-9-]{4,80}", code):
        return False
    if match is None or not hmac.compare_digest(match.group(1).upper(), code):
        return False
    return bool(request_id)


def normalize_bug_report_metadata(metadata: dict[str, Any]) -> dict[str, Any]:
    raw = safe_mapping(metadata)
    bug = safe_mapping(raw.get("bugReport"))
    if not bug and any(raw.get(key) for key in ("errorCode", "errorSummary", "technical", "diagnostics", "stackTrace")):
        bug = dict(raw)
    if not bug:
        return raw
    technical = safe_mapping(bug.get("technical"))
    diagnostics = safe_mapping(bug.get("diagnostics"))
    context = safe_mapping(bug.get("context"))
    if not technical:
        technical = {
            "exceptionClass": clip_text(bug.get("exceptionClass") or raw.get("exceptionClass"), 160),
            "stackTrace": clip_text(bug.get("stackTrace") or raw.get("stackTrace"), 6000),
            "details": clip_text(bug.get("details") or raw.get("details"), 6000),
            "logLines": bug.get("logLines") or raw.get("logLines") or [],
        }
        technical = {key: value for key, value in technical.items() if value not in ("", [], {}, None)}
    if not diagnostics:
        diagnostics = {
            "pluginVersion": clip_text(bug.get("pluginVersion") or raw.get("pluginVersion"), 160),
            "serverVersion": clip_text(bug.get("serverVersion") or raw.get("serverVersion"), 160),
            "requestId": clip_text(bug.get("requestId") or raw.get("requestId"), 160),
            "actionId": clip_text(bug.get("actionId") or raw.get("actionId"), 160),
        }
        diagnostics = {key: value for key, value in diagnostics.items() if value not in ("", None)}
    if not context:
        context = {
            "source": clip_text(bug.get("source") or raw.get("source"), 160),
            "action": clip_text(bug.get("action") or raw.get("action"), 200),
            "world": clip_text(bug.get("world") or raw.get("world"), 120),
            "x": bug.get("x") if bug.get("x") is not None else raw.get("x"),
            "y": bug.get("y") if bug.get("y") is not None else raw.get("y"),
            "z": bug.get("z") if bug.get("z") is not None else raw.get("z"),
            "itemType": clip_text(bug.get("itemType") or raw.get("itemType"), 120),
        }
        context = {key: value for key, value in context.items() if value not in ("", None)}
    normalized = {
        **raw,
        "reportKind": "bug",
        "errorCode": clip_text(bug.get("errorCode") or bug.get("token") or raw.get("errorCode") or raw.get("token"), 80),
        "errorSummary": clip_text(
            bug.get("errorSummary")
            or raw.get("errorSummary")
            or bug.get("message")
            or raw.get("message")
            or bug.get("exceptionClass")
            or "Unexpected server error",
            240,
        ),
        "bugReport": {
            "reportKind": "bug",
            "errorCode": clip_text(bug.get("errorCode") or bug.get("token") or raw.get("errorCode") or raw.get("token"), 80),
            "errorSummary": clip_text(
                bug.get("errorSummary")
                or raw.get("errorSummary")
                or bug.get("message")
                or raw.get("message")
                or bug.get("exceptionClass")
                or "Unexpected server error",
                240,
            ),
            "capturedAt": int(bug.get("capturedAt") or raw.get("capturedAt") or now_ts()),
            "technical": technical,
            "diagnostics": diagnostics,
            "context": context,
        },
    }
    return normalized


def normalize_report_metadata_for_source(metadata: Any, source: str, message: str) -> dict[str, Any]:
    raw = safe_mapping(metadata)
    report_kind = str(raw.get("reportKind") or raw.get("kind") or "").strip().lower()
    trusted_source = str(source or "").strip().lower() in TECHNICAL_REPORT_SOURCES
    if (
        trusted_source
        and (report_kind == "bug" or raw.get("bugReport") or raw.get("errorCode") or raw.get("errorSummary"))
        and technical_bug_report_is_correlated(raw, message)
    ):
        return normalize_bug_report_metadata(raw)
    return report_metadata_without_technical_context(raw)


def normalize_report_metadata(metadata: Any) -> dict[str, Any]:
    """Compatibility helper for untrusted or legacy rows without a source marker."""
    return normalize_report_metadata_for_source(metadata, "", "")


def public_player_report(item: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    row = dict(item or {})
    metadata = normalize_report_metadata_for_source(row.get("metadata"), str(row.get("source") or ""), str(row.get("message") or ""))
    public_metadata: dict[str, Any] = {}
    for key in ("sourcePage", "siteAccountId", "siteUsername", "reportKind", "errorCode", "errorSummary"):
        if metadata.get(key) not in ("", None, {}, []):
            public_metadata[key] = metadata.get(key)
    bug = safe_mapping(metadata.get("bugReport"))
    if bug:
        public_bug = {}
        for key in ("reportKind", "errorCode", "errorSummary", "capturedAt"):
            if bug.get(key) not in ("", None):
                public_bug[key] = bug.get(key)
        if public_bug:
            public_metadata["bugReport"] = public_bug
    row["metadata"] = public_metadata
    row["attached_events"] = []
    row["timeline"] = []
    replies = []
    for reply in row.get("replies", []) if isinstance(row.get("replies"), list) else []:
        reply_item = safe_mapping(reply)
        visibility = str(reply_item.get("visibility") or "internal").lower()
        if visibility not in {"public", "player"}:
            continue
        replies.append(
            {
                "id": reply_item.get("id"),
                "message": clip_text(reply_item.get("message"), 1200),
                "author": clip_text(reply_item.get("author"), 120),
                "createdAt": reply_item.get("createdAt"),
                "visibility": visibility,
            }
        )
    row["replies"] = replies
    return redact_value(row)


def normalized_report_row(item: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    row = dict(item or {})
    row["metadata"] = normalize_report_metadata_for_source(row.get("metadata"), str(row.get("source") or ""), str(row.get("message") or ""))
    row["reportType"] = str(row.get("reportType") or row["metadata"].get("reportKind") or "report")
    row["errorCode"] = clip_text(row.get("errorCode") or row["metadata"].get("errorCode"), 80)
    row["errorSummary"] = clip_text(row.get("errorSummary") or row["metadata"].get("errorSummary"), 240)
    return row


def player_identity_variants(player_name: str, player_uuid: str) -> set[str]:
    variants = {
        str(player_name or "").strip().lower(),
        str(player_uuid or "").strip().lower(),
    }
    if player_uuid:
        variants.add(str(uuid_to_name().get(player_uuid, "")).strip().lower())
    return {value for value in variants if value}


def value_mentions_player(value: Any, variants: set[str]) -> bool:
    if not variants:
        return False
    if isinstance(value, (dict, list, tuple)):
        text = json.dumps(value, ensure_ascii=False, sort_keys=True)
    else:
        text = str(value or "")
    lowered = text.strip().lower()
    return any(variant and variant in lowered for variant in variants)


def report_involves_player(item: Mapping[str, Any] | dict[str, Any], player_name: str, player_uuid: str) -> bool:
    row = dict(item or {})
    variants = player_identity_variants(player_name, player_uuid)
    metadata = safe_mapping(row.get("metadata"))
    return any(
        value_mentions_player(candidate, variants)
        for candidate in (
            row.get("reporter_uuid"),
            row.get("reporter"),
            row.get("target_uuid"),
            row.get("target"),
            metadata.get("playerUuid"),
            metadata.get("targetUuid"),
            metadata.get("playerName"),
            metadata.get("targetName"),
        )
    )


def application_involves_player(item: Mapping[str, Any] | dict[str, Any], player_name: str, player_uuid: str) -> bool:
    row = dict(item or {})
    variants = player_identity_variants(player_name, player_uuid)
    metadata = safe_mapping(row.get("metadata"))
    return any(
        value_mentions_player(candidate, variants)
        for candidate in (
            row.get("uuid"),
            row.get("player"),
            row.get("contact"),
            metadata.get("playerUuid"),
            metadata.get("minecraftUuid"),
            metadata.get("minecraftName"),
        )
    )


def public_error_message(message: object) -> str:
    text = str(message)
    sensitive = [
        "ops.json",
        "whitelist.json",
        "usercache.json",
        "server.properties",
        ".env",
        "banned-players.json",
        "banned-ips.json",
        "latest.log",
        "CoreProtect",
        "database.db",
        "plugins/",
        "/opt/copimine/",
        "/var/www/",
        "/etc/",
        "RCON_PASSWORD",
        "SECRET_KEY",
        "PLUGIN_API_KEY",
        "DISCORD_BOT_TOKEN",
        "DISCORD_BOT_API_KEY",
    ]
    for item in sensitive:
        text = text.replace(item, "серверный файл")
    text = re.sub(r"(?i)(token|password|secret|api[_-]?key)=([^\s]+)", r"\1=<redacted>", text)
    return text


def json_safe_value(value):
    from pathlib import Path as _Path
    import math as _math

    if value is None or isinstance(value, (str, int, bool)):
        return value

    if isinstance(value, float):
        if _math.isnan(value) or _math.isinf(value):
            return None
        return value

    if isinstance(value, bytes):
        try:
            return value.decode("utf-8")
        except Exception:
            return value.hex()

    if isinstance(value, bytearray):
        try:
            return bytes(value).decode("utf-8")
        except Exception:
            return bytes(value).hex()

    if isinstance(value, _Path):
        return str(value)

    if hasattr(value, "tolist"):
        try:
            return json_safe_value(value.tolist())
        except Exception:
            pass

    if hasattr(value, "unpack"):
        try:
            return json_safe_value(value.unpack())
        except Exception:
            pass

    if hasattr(value, "value"):
        try:
            return json_safe_value(value.value)
        except Exception:
            pass

    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            if isinstance(k, bytes):
                try:
                    key = k.decode("utf-8")
                except Exception:
                    key = k.hex()
            else:
                key = str(k)
            out[key] = json_safe_value(v)
        return out

    if isinstance(value, (list, tuple, set)):
        return [json_safe_value(x) for x in value]

    try:
        return str(value)
    except Exception:
        return repr(value)


def clean_api_payload(payload):
    return json_safe_value(payload)

# Global JSON sanitizer for all FastAPI responses.
# Fixes Minecraft NBT/bytes values that cannot be serialized as UTF-8.
import fastapi.routing as _copimine_fastapi_routing

_copimine_original_serialize_response = _copimine_fastapi_routing.serialize_response

async def _copimine_serialize_response_safe(**kwargs):
    if "response_content" in kwargs:
        kwargs["response_content"] = json_safe_value(kwargs["response_content"])
    return await _copimine_original_serialize_response(**kwargs)

_copimine_fastapi_routing.serialize_response = _copimine_serialize_response_safe




class FrontendStaticFiles(StaticFiles):
    async def get_response(self, path: str, scope: Mapping[str, Any]) -> Response:
        try:
            return await super().get_response(path, scope)
        except StarletteHTTPException as exc:
            if exc.status_code == 404:
                fallback = FRONTEND_DIR / "404.html"
                if fallback.exists():
                    return FileResponse(fallback, status_code=404)
            raise


def wants_frontend_html(request: Request) -> bool:
    if request.method not in {"GET", "HEAD"}:
        return False
    accept = str(request.headers.get("accept") or "").lower()
    return "text/html" in accept or "*/*" in accept or not accept


def frontend_file_response(filename: str, status_code: int) -> Optional[FileResponse]:
    path = FRONTEND_DIR / filename
    if not path.exists():
        return None
    return FileResponse(path, status_code=status_code)


app = FastAPI(title="CopiMine Ultimate Admin", version=APP_VERSION)
if ALLOWED_ORIGINS:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=ALLOWED_ORIGINS,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-API-Key", SENSITIVE_CONFIRM_HEADER, CSRF_HEADER_NAME],
    )


@app.exception_handler(StarletteHTTPException)
async def copimine_http_exception_handler(request: Request, exc: StarletteHTTPException) -> Response:
    if not request.url.path.startswith("/api") and wants_frontend_html(request):
        if exc.status_code == 404:
            fallback = frontend_file_response("404.html", 404)
            if fallback is not None:
                return fallback
        fallback = frontend_file_response("error.html", exc.status_code)
        if fallback is not None:
            return fallback
    detail = exc.detail if isinstance(exc.detail, (dict, list, str)) else "HTTP error"
    return JSONResponse({"detail": detail}, status_code=exc.status_code)


@app.exception_handler(Exception)
async def copimine_unhandled_exception_handler(request: Request, _: Exception) -> Response:
    if not request.url.path.startswith("/api") and wants_frontend_html(request):
        fallback = frontend_file_response("error.html", 500)
        if fallback is not None:
            return fallback
    return JSONResponse({"detail": "Internal server error"}, status_code=500)


@app.on_event("startup")
async def on_startup() -> None:
    global _STARTUP_REPORT
    _STARTUP_REPORT = run_startup_checks()
    if STARTUP_STRICT and not _STARTUP_REPORT.get("ok", False):
        failed = [
            f"{item.get('key')}: {item.get('summary')}"
            for item in _STARTUP_REPORT.get("checks", [])
            if item.get("status") == "fail" and item.get("required", True)
        ]
        raise RuntimeError("CopiMine startup self-check failed: " + "; ".join(failed[:12]))
    if auth_storage_ready():
        try:
            with auth_conn() as conn:
                ensure_v4_schema(conn)
                conn.commit()
            if pg_ready():
                pg_record_auth_state("runtime_startup", {"startedAt": now_ts(), "poolMin": POSTGRES_POOL_MIN_SIZE, "poolMax": POSTGRES_POOL_MAX_SIZE})
        except Exception:
            pass


@app.on_event("shutdown")
async def on_shutdown() -> None:
    global _PG_POOL
    if _PG_POOL is not None:
        _PG_POOL.close()
        _PG_POOL = None


@app.middleware("http")
async def security_headers(request: Request, call_next: Callable[..., Any]) -> Response:
    request_path = normalized_request_path(request)
    if request_path.startswith("/api/") and request.method.upper() in {"POST", "PUT", "PATCH", "DELETE"}:
        if not is_plugin_key_request(request):
            origin = (request.headers.get("origin") or "").strip()
            if origin and not origin_allowed(request, origin):
                return JSONResponse(status_code=403, content={"detail": "Недопустимый Origin для state-changing запроса"})
            sec_fetch_site = (request.headers.get("sec-fetch-site") or "").strip().lower()
            if sec_fetch_site and sec_fetch_site not in {"same-origin", "same-site", "none"}:
                return JSONResponse(status_code=403, content={"detail": "Cross-site запрос отклонён политикой безопасности"})
            if request_path not in csrf_exempt_paths():
                cookie_token = (request.cookies.get(CSRF_COOKIE_NAME) or "").strip()
                header_token = (request.headers.get(CSRF_HEADER_NAME) or "").strip()
                if not cookie_token or not header_token or cookie_token != header_token or not verify_csrf_token(cookie_token):
                    return JSONResponse(status_code=403, content={"detail": "CSRF-подтверждение отсутствует или недействительно"})
    response = await call_next(request)
    if request_path.startswith("/api/"):
        response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
        response.headers["Pragma"] = "no-cache"
        response.headers["Expires"] = "0"
        response.headers.setdefault("Vary", "Origin, Sec-Fetch-Site")
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "same-origin")
    response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    response.headers.setdefault("Cross-Origin-Opener-Policy", "same-origin")
    response.headers.setdefault("Cross-Origin-Resource-Policy", "same-origin")
    if request_transport_is_secure(request):
        response.headers.setdefault("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    response.headers.setdefault(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' data: https://mc-heads.net; style-src 'self'; script-src 'self'; connect-src 'self'; font-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'; manifest-src 'self'",
    )
    return response


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _unb64(data: str) -> bytes:
    pad = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + pad)


def token_header_b64() -> str:
    return _b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode("utf-8"))


def sign_token_parts(header_b64: str, body_b64: str) -> str:
    signed = f"{header_b64}.{body_b64}".encode("ascii")
    return _b64(hmac.new(SECRET_KEY.encode("utf-8"), signed, hashlib.sha256).digest())


def encode_signed_token(payload: dict[str, Any]) -> str:
    header_b64 = token_header_b64()
    body_b64 = _b64(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = sign_token_parts(header_b64, body_b64)
    return f"{header_b64}.{body_b64}.{signature}"


def decode_signed_token(token: str) -> dict[str, Any]:
    raw = str(token or "").strip()
    parts = raw.split(".")
    if len(parts) == 3:
        header_b64, body_b64, signature = parts
        expected = sign_token_parts(header_b64, body_b64)
        if not hmac.compare_digest(signature, expected):
            raise ValueError("bad signature")
        header = json.loads(_unb64(header_b64).decode("utf-8"))
        if str(header.get("alg") or "").upper() != "HS256":
            raise ValueError("unsupported algorithm")
        payload = json.loads(_unb64(body_b64).decode("utf-8"))
        if str(payload.get("iss") or "") != TOKEN_ISSUER:
            raise ValueError("unexpected issuer")
        if int(payload.get("exp", 0)) < now_ts():
            raise ValueError("expired")
        return payload
    if len(parts) == 2:
        body_b64, signature = parts
        expected = _b64(hmac.new(SECRET_KEY.encode("utf-8"), body_b64.encode("ascii"), hashlib.sha256).digest())
        if not hmac.compare_digest(signature, expected):
            raise ValueError("bad legacy signature")
        payload = json.loads(_unb64(body_b64).decode("utf-8"))
        if int(payload.get("exp", 0)) < now_ts():
            raise ValueError("expired")
        payload.setdefault("typ", "access")
        payload.setdefault("iss", TOKEN_ISSUER)
        return payload
    raise ValueError("unsupported token format")


def make_csrf_token() -> str:
    nonce = _b64(secrets.token_bytes(24))
    payload = f"csrf:{nonce}"
    signature = _b64(hmac.new(SECRET_KEY.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest())
    return f"{nonce}.{signature}"


def verify_csrf_token(token: str) -> bool:
    raw = str(token or "").strip()
    if "." not in raw:
        return False
    nonce, signature = raw.split(".", 1)
    if not nonce or not signature:
        return False
    expected = _b64(hmac.new(SECRET_KEY.encode("utf-8"), f"csrf:{nonce}".encode("utf-8"), hashlib.sha256).digest())
    return hmac.compare_digest(signature, expected)


def direct_request_host(request: Optional[Request]) -> str:
    return str(request.client.host if request and request.client else "").strip().lower()


def request_uses_trusted_proxy(request: Optional[Request]) -> bool:
    return direct_request_host(request) in {entry.lower() for entry in TRUSTED_PROXY_IPS}


def request_transport_is_secure(request: Optional[Request]) -> bool:
    if request is None:
        return bool(AUTH_COOKIE_SECURE)
    if str(request.url.scheme or "").lower() == "https":
        return True
    if request_uses_trusted_proxy(request):
        forwarded_proto = _first_forwarded_value(request.headers.get("x-forwarded-proto") or "").lower()
        return forwarded_proto == "https"
    return False


def request_is_direct_loopback(request: Request) -> bool:
    """Allow local HTTP only when the request bypassed every proxy header."""
    if not is_loopback_request(request):
        return False
    forwarding_headers = ("forwarded", "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-origin", "x-real-ip")
    return not any(str(request.headers.get(header) or "").strip() for header in forwarding_headers)


def auth_transport_is_allowed(request: Request) -> bool:
    return request_transport_is_secure(request) or request_is_direct_loopback(request) or ALLOW_INSECURE_HTTP_AUTH


def require_secure_auth_transport(request: Request) -> None:
    if auth_transport_is_allowed(request):
        return
    raise HTTPException(
        status_code=426,
        detail="Для входа через публичную сеть нужен HTTPS. HTTP оставлен для публичных страниц и загрузок; "
        "в доверенной локальной сети администратор может временно включить ALLOW_INSECURE_HTTP_AUTH=1.",
    )


def cookie_secure_for_request(request: Optional[Request]) -> bool:
    return request_transport_is_secure(request)


_AUTH_COOKIE_REQUEST: ContextVar[Optional[Request]] = ContextVar("copimine_auth_cookie_request", default=None)


def set_csrf_cookie(
    response: Response,
    request: Optional[Request] = None,
    token: Optional[str] = None,
    max_age: Optional[int] = SESSION_TTL_SECONDS,
) -> str:
    value = token or make_csrf_token()
    response.set_cookie(
        CSRF_COOKIE_NAME,
        value,
        max_age=max_age,
        httponly=False,
        secure=cookie_secure_for_request(request),
        samesite="lax",
        path="/",
    )
    return value


def clear_csrf_cookie(response: Response, request: Optional[Request] = None) -> None:
    response.delete_cookie(CSRF_COOKIE_NAME, path="/", secure=cookie_secure_for_request(request), samesite="lax")


def set_access_cookie(
    response: Response,
    token: str,
    request: Optional[Request] = None,
    max_age: Optional[int] = ACCESS_TOKEN_TTL_SECONDS,
) -> None:
    response.set_cookie(
        AUTH_COOKIE_NAME,
        token,
        max_age=max_age,
        httponly=True,
        secure=cookie_secure_for_request(request),
        samesite="lax",
        path="/",
    )


def set_refresh_cookie(response: Response, token: str, max_age: Optional[int] = REFRESH_TOKEN_TTL_SECONDS) -> None:
    request = _AUTH_COOKIE_REQUEST.get()
    response.set_cookie(
        AUTH_REFRESH_COOKIE_NAME,
        token,
        max_age=max_age,
        httponly=True,
        secure=cookie_secure_for_request(request),
        samesite="lax",
        path="/",
    )


def set_auth_cookies(response: Response, access_token: str, refresh_token: str, remember_me: bool = False) -> None:
    request = _AUTH_COOKIE_REQUEST.get()
    access_max_age = ACCESS_TOKEN_TTL_SECONDS if remember_me else None
    refresh_max_age = REFRESH_TOKEN_TTL_SECONDS if remember_me else None
    csrf_max_age = REFRESH_TOKEN_TTL_SECONDS if remember_me else None
    set_access_cookie(response, access_token, request, access_max_age)
    set_refresh_cookie(response, refresh_token, refresh_max_age)
    set_csrf_cookie(response, request, max_age=csrf_max_age)


def set_auth_cookies_for_request(
    response: Response,
    request: Request,
    access_token: str,
    refresh_token: str,
    remember_me: bool = False,
) -> None:
    context_token = _AUTH_COOKIE_REQUEST.set(request)
    try:
        set_auth_cookies(response, access_token, refresh_token, remember_me)
    finally:
        _AUTH_COOKIE_REQUEST.reset(context_token)


def clear_auth_cookies(response: Response, request: Optional[Request] = None) -> None:
    secure = cookie_secure_for_request(request)
    response.delete_cookie(AUTH_COOKIE_NAME, path="/", secure=secure, samesite="lax", httponly=True)
    response.delete_cookie(AUTH_REFRESH_COOKIE_NAME, path="/", secure=secure, samesite="lax", httponly=True)
    clear_csrf_cookie(response, request)


def csrf_exempt_paths() -> set[str]:
    return {
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/session/login",
        "/api/session/logout",
        "/api/player/login",
        "/api/player/refresh",
        "/api/player/register",
    }


def normalized_request_path(request: Request) -> str:
    path = str(request.url.path or "").strip()
    if not path:
        return "/"
    if path != "/" and path.endswith("/"):
        path = path.rstrip("/")
    return path or "/"


def _first_forwarded_value(raw_value: str) -> str:
    return str(raw_value or "").split(",", 1)[0].strip()


def origin_allowed(request: Request, origin: str) -> bool:
    allowed = {
        str(request.base_url).rstrip("/"),
        ADMIN_PUBLIC_BASE_URL.rstrip("/"),
    }
    public_panel_url = str(os.getenv("PUBLIC_PANEL_URL", "")).strip()
    backend_internal = str(os.getenv("BACKEND_INTERNAL_BASE_URL", "")).strip()
    if public_panel_url:
        allowed.add(public_panel_url.rstrip("/"))
    if backend_internal:
        allowed.add(backend_internal.rstrip("/"))
    if request_uses_trusted_proxy(request):
        forwarded_host = _first_forwarded_value(request.headers.get("x-forwarded-host") or request.headers.get("host") or "")
        forwarded_proto = _first_forwarded_value(request.headers.get("x-forwarded-proto") or request.url.scheme or "http").lower()
        if forwarded_host:
            allowed.add(f"{forwarded_proto}://{forwarded_host}".rstrip("/"))
            allowed.add(f"http://{forwarded_host}".rstrip("/"))
            allowed.add(f"https://{forwarded_host}".rstrip("/"))
        forwarded_origin = _first_forwarded_value(request.headers.get("x-forwarded-origin") or "")
        if forwarded_origin:
            allowed.add(forwarded_origin.rstrip("/"))
    allowed.update(ALLOWED_ORIGINS)
    return origin.rstrip("/") in {item.rstrip("/") for item in allowed if item}


def is_plugin_key_request(request: Request) -> bool:
    api_key = (request.headers.get("x-api-key") or "").strip()
    return bool(
        api_key
        and (
            (PLUGIN_API_KEY and hmac.compare_digest(api_key, PLUGIN_API_KEY))
            or (DISCORD_BOT_API_KEY and hmac.compare_digest(api_key, DISCORD_BOT_API_KEY))
        )
    )


def valid_minecraft_name(username: str) -> bool:
    return bool(re.fullmatch(r"[A-Za-z0-9_]{3,16}", username or ""))


def offline_uuid_for_name(username: str) -> str:
    source = ("OfflinePlayer:" + str(username or "")).encode("utf-8")
    digest = bytearray(hashlib.md5(source).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def resolve_minecraft_uuid(minecraft_uuid: str, minecraft_name: str) -> str:
    raw_uuid = str(minecraft_uuid or "").strip()
    if raw_uuid:
        return raw_uuid
    raw_name = str(minecraft_name or "").strip()
    if valid_minecraft_name(raw_name):
        return offline_uuid_for_name(raw_name)
    return ""


def normalize_donation_player_target(minecraft_uuid: str, minecraft_name: str) -> tuple[str, str]:
    raw_name = str(minecraft_name or "").strip()
    if not valid_minecraft_name(raw_name):
        raise HTTPException(status_code=400, detail="Укажи корректный Minecraft-ник")
    raw_uuid = str(minecraft_uuid or "").strip()
    if raw_uuid:
        try:
            return str(uuid.UUID(raw_uuid)), raw_name
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Укажи корректный Minecraft UUID") from exc
    resolved = resolve_minecraft_uuid("", raw_name)
    if not resolved:
        raise HTTPException(status_code=400, detail="Не удалось определить Minecraft UUID для этого игрока")
    return resolved, raw_name


def get_client_ip(request: Optional[Request]) -> str:
    if request is None:
        return "unknown"
    direct_host = str(request.client.host if request.client else "").strip()
    forwarded_for = str(request.headers.get("x-forwarded-for") or "").strip()
    if direct_host in TRUSTED_PROXY_IPS and forwarded_for:
        for candidate in forwarded_for.split(","):
            value = candidate.strip()
            if value:
                return value[:128]
    real_ip = str(request.headers.get("x-real-ip") or "").strip()
    if direct_host in TRUSTED_PROXY_IPS and real_ip:
        return real_ip[:128]
    return (direct_host or "unknown")[:128]


def mask_ip(value: str) -> str:
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


def normalize_admin_users(data: Any) -> dict[str, dict[str, Any]]:
    normalized: dict[str, dict[str, Any]] = {}
    if not isinstance(data, dict):
        return normalized
    for name, meta in data.items():
        username = str(name).strip()
        if not valid_minecraft_name(username):
            continue
        if isinstance(meta, str):
            normalized[username] = {"password_hash": meta, "role": "admin", "enabled": True}
        elif isinstance(meta, dict):
            password_hash = str(meta.get("password_hash") or meta.get("password") or "")
            if password_hash:
                item = dict(meta)
                item["password_hash"] = password_hash
                item["role"] = normalize_admin_role(meta.get("role"))
                item["enabled"] = bool(meta.get("enabled", True))
                normalized[username] = item
    return normalized


def read_admin_overlay() -> dict[str, dict[str, Any]]:
    return normalize_admin_users(read_json(ADMIN_USERS_FILE, {}))


def write_admin_overlay(data: dict[str, dict[str, Any]]) -> None:
    write_json(ADMIN_USERS_FILE, data)


def pg_ident(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value or ""):
        raise RuntimeError("Unsafe POSTGRES_SCHEMA")
    return '"' + value.replace('"', '""') + '"'


def _configure_pg_connection(conn: Any) -> Any:
    conn.execute(f"CREATE SCHEMA IF NOT EXISTS {pg_ident(POSTGRES_SCHEMA)}")
    conn.execute(f"SET search_path TO {pg_ident(POSTGRES_SCHEMA)}")
    conn.execute("SET statement_timeout TO 5000")
    conn.execute("SET lock_timeout TO 3000")
    return conn


def _new_pg_connection() -> Any:
    if psycopg is None:
        raise RuntimeError("psycopg is required for CopiMine PostgreSQL auth storage")
    if not POSTGRES_PASSWORD:
        raise RuntimeError("POSTGRES_PASSWORD is required for CopiMine PostgreSQL auth storage")
    conn = psycopg.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        dbname=POSTGRES_DB,
        user=POSTGRES_USER,
        password=POSTGRES_PASSWORD,
        connect_timeout=POSTGRES_CONNECT_TIMEOUT,
        row_factory=dict_row,
    )
    return _configure_pg_connection(conn)


class _SqliteNoopCursor:
    rowcount = 0

    def fetchone(self) -> None:
        return None

    def fetchall(self) -> list[Any]:
        return []


class _SqliteAuthConnection:
    def __init__(self, conn: sqlite3.Connection) -> None:
        self._conn = conn

    def _apply_compat_transforms(self, sql: str) -> tuple[str, bool]:
        text = str(sql or "").strip()
        if not text:
            return "", True
        normalized = re.sub(r"\s+", " ", text).strip().upper()
        if (
            normalized.startswith("CREATE SCHEMA IF NOT EXISTS ")
            or normalized.startswith("SET SEARCH_PATH TO ")
            or normalized.startswith("SET STATEMENT_TIMEOUT TO ")
            or normalized.startswith("SET LOCK_TIMEOUT TO ")
            or normalized.startswith("SET TRANSACTION READ ONLY")
        ):
            return "", True
        match = _SQLITE_ADD_COLUMN_IF_NOT_EXISTS_RE.match(text)
        if match:
            table = str(match.group("table") or "")
            column = str(match.group("column") or "")
            definition = str(match.group("definition") or "").strip()
            existing = {
                str(row["name"]).lower()
                for row in self._conn.execute(f"PRAGMA table_info({table})").fetchall()
            }
            if column.lower() in existing:
                return "", True
            text = f"ALTER TABLE {table} ADD COLUMN {column} {definition}"
        text = re.sub(r"\bFOR\s+UPDATE\b", "", text, flags=re.I)
        text = text.replace("%s", "?")
        return text, False

    def execute(self, sql: str, args: Optional[list[Any] | tuple[Any, ...]] = None) -> Any:
        text, noop = self._apply_compat_transforms(sql)
        if noop:
            return _SqliteNoopCursor()
        return self._conn.execute(text, tuple(args or ()))

    def commit(self) -> None:
        self._conn.commit()

    def rollback(self) -> None:
        self._conn.rollback()

    def close(self) -> None:
        self._conn.close()

    def __getattr__(self, name: str) -> Any:
        return getattr(self._conn, name)


def auth_storage_backend() -> str:
    return "sqlite" if AUTH_STORAGE_MODE == "sqlite" else "postgresql"


def auth_storage_ready() -> bool:
    return auth_storage_backend() == "sqlite" or pg_ready()


def auth_storage_location() -> str:
    if auth_storage_backend() == "sqlite":
        return safe_location(AUTH_DB_FILE)
    return f"postgresql://{POSTGRES_HOST}:{POSTGRES_PORT}/{POSTGRES_DB}?schema={POSTGRES_SCHEMA}"


def _new_sqlite_auth_connection() -> Any:
    AUTH_DB_FILE.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(AUTH_DB_FILE), timeout=8)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    return _SqliteAuthConnection(conn)


def pg_pool() -> Optional[SimplePgPool]:
    global _PG_POOL
    if not pg_ready():
        return None
    if _PG_POOL is None:
        _PG_POOL = SimplePgPool(POSTGRES_POOL_MIN_SIZE, POSTGRES_POOL_MAX_SIZE)
    return _PG_POOL


def auth_conn() -> Any:
    if auth_storage_backend() == "sqlite":
        return _PooledPgConnection(None, _new_sqlite_auth_connection())
    pool = pg_pool()
    if pool is not None:
        return pool.acquire()
    return _PooledPgConnection(None, _new_pg_connection())


class PgCompatConnection:
    def __init__(self, readonly: bool = True) -> None:
        self.readonly = readonly
        self._conn = auth_conn()
        self._conn.commit()
        if readonly:
            self._conn.execute("SET TRANSACTION READ ONLY")

    def execute(self, sql: str, args: Optional[list[Any] | tuple[Any, ...]] = None) -> Any:
        return self._conn.execute(pg_compat_sql(sql), tuple(args or ()))

    def commit(self) -> None:
        self._conn.commit()

    def rollback(self) -> None:
        self._conn.rollback()

    def close(self) -> None:
        self._conn.close()

    def __enter__(self) -> "PgCompatConnection":
        return self

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> bool:
        try:
            if exc_type is None:
                self.commit()
            else:
                self.rollback()
        finally:
            self.close()
        return False


def pg_ready() -> bool:
    return psycopg is not None and bool(POSTGRES_PASSWORD)


def ensure_v4_schema(conn: Any) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cmv4_schema_migrations(
            version TEXT PRIMARY KEY,
            applied_at BIGINT NOT NULL,
            component TEXT NOT NULL DEFAULT 'admin-web'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS site_accounts(
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL UNIQUE,
            username_norm TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT 'player',
            enabled INTEGER NOT NULL DEFAULT 1,
            minecraft_uuid TEXT NOT NULL DEFAULT '',
            minecraft_name TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            last_login_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute("ALTER TABLE site_accounts ADD COLUMN IF NOT EXISTS registration_ip TEXT NOT NULL DEFAULT ''")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS minecraft_account_links(
            minecraft_uuid TEXT PRIMARY KEY,
            minecraft_name TEXT NOT NULL DEFAULT '',
            site_account_id TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            linked_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS whitelist_account_links(
            minecraft_uuid TEXT PRIMARY KEY,
            minecraft_name TEXT NOT NULL DEFAULT '',
            site_account_id TEXT NOT NULL,
            whitelisted INTEGER NOT NULL DEFAULT 1,
            synced_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS whitelist_requests(
            id TEXT PRIMARY KEY,
            site_account_id TEXT NOT NULL,
            minecraft_uuid TEXT NOT NULL DEFAULT '',
            minecraft_name TEXT NOT NULL DEFAULT '',
            request_ip TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'PENDING',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            approved_at BIGINT NOT NULL DEFAULT 0,
            approved_by TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            discord_channel_id TEXT NOT NULL DEFAULT '',
            discord_message_id TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS security_ip_alerts(
            id TEXT PRIMARY KEY,
            ip TEXT NOT NULL DEFAULT '',
            username TEXT NOT NULL DEFAULT '',
            minecraft_name TEXT NOT NULL DEFAULT '',
            reason TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'OPEN',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS one_time_link_codes(
            id TEXT PRIMARY KEY,
            site_account_id TEXT NOT NULL,
            minecraft_name TEXT NOT NULL,
            minecraft_uuid TEXT NOT NULL DEFAULT '',
            code_hash TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'PENDING',
            created_at BIGINT NOT NULL,
            expires_at BIGINT NOT NULL,
            used_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS player_profile_cache(
            minecraft_uuid TEXT PRIMARY KEY,
            minecraft_name TEXT NOT NULL DEFAULT '',
            display_name TEXT NOT NULL DEFAULT '',
            online INTEGER NOT NULL DEFAULT 0,
            last_seen BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            profile_json TEXT NOT NULL DEFAULT '{}'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS player_settings(
            minecraft_uuid TEXT PRIMARY KEY,
            site_account_id TEXT NOT NULL DEFAULT '',
            settings_json TEXT NOT NULL DEFAULT '{}',
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS bank_pin_hashes(
            minecraft_uuid TEXT PRIMARY KEY,
            site_account_id TEXT NOT NULL DEFAULT '',
            pin_hash TEXT NOT NULL,
            pin_sealed TEXT NOT NULL DEFAULT '',
            must_change INTEGER NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS bank_account_pins(
            account_id TEXT PRIMARY KEY,
            pin_hash TEXT NOT NULL,
            pin_sealed TEXT NOT NULL DEFAULT '',
            must_change INTEGER NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            updated_by TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS temporary_pin_resets(
            id TEXT PRIMARY KEY,
            minecraft_uuid TEXT NOT NULL,
            site_account_id TEXT NOT NULL DEFAULT '',
            pin_hash TEXT NOT NULL,
            delivery_blob TEXT NOT NULL DEFAULT '',
            expires_at BIGINT NOT NULL,
            used_at BIGINT NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS pin_reset_audit(
            id BIGSERIAL PRIMARY KEY,
            minecraft_uuid TEXT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS failed_pin_attempts(
            id BIGSERIAL PRIMARY KEY,
            minecraft_uuid TEXT NOT NULL,
            site_account_id TEXT NOT NULL DEFAULT '',
            attempted_at BIGINT NOT NULL,
            source TEXT NOT NULL DEFAULT 'site'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS account_lockouts(
            account_id TEXT PRIMARY KEY,
            locked_until BIGINT NOT NULL DEFAULT 0,
            reason TEXT NOT NULL DEFAULT '',
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS security_events(
            id BIGSERIAL PRIMARY KEY,
            time BIGINT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL,
            details TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT 'site'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS site_cms_entries(
            entry_key TEXT PRIMARY KEY,
            section TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL DEFAULT '',
            image_path TEXT NOT NULL DEFAULT '',
            link_url TEXT NOT NULL DEFAULT '',
            sort_order INTEGER NOT NULL DEFAULT 100,
            enabled INTEGER NOT NULL DEFAULT 1,
            updated_at BIGINT NOT NULL DEFAULT 0,
            updated_by TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cmv4_bank_accounts(
            account_id TEXT PRIMARY KEY,
            owner_uuid TEXT NOT NULL,
            owner_name TEXT NOT NULL DEFAULT '',
            account_type TEXT NOT NULL DEFAULT 'PLAYER',
            currency TEXT NOT NULL DEFAULT 'AR',
            balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            version BIGINT NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cmv4_bank_ledger(
            tx_id TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            counterparty_account_id TEXT NOT NULL DEFAULT '',
            player_uuid TEXT NOT NULL DEFAULT '',
            tx_type TEXT NOT NULL,
            amount BIGINT NOT NULL CHECK(amount>=0),
            balance_after BIGINT NOT NULL DEFAULT 0,
            idempotency_key TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'COMMITTED',
            created_at BIGINT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cmv4_bank_transfers(
            tx_id TEXT PRIMARY KEY,
            from_account_id TEXT NOT NULL,
            to_account_id TEXT NOT NULL,
            amount BIGINT NOT NULL CHECK(amount>0),
            currency TEXT NOT NULL DEFAULT 'AR',
            status TEXT NOT NULL DEFAULT 'COMMITTED',
            idempotency_key TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS ar_money_supply_snapshots(
            id BIGSERIAL PRIMARY KEY,
            created_at BIGINT NOT NULL,
            total_accounts BIGINT NOT NULL DEFAULT 0,
            total_physical BIGINT NOT NULL DEFAULT 0,
            total_supply BIGINT NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS elections(
            id TEXT PRIMARY KEY,
            status TEXT NOT NULL DEFAULT 'DRAFT',
            started_at BIGINT NOT NULL DEFAULT 0,
            ended_at BIGINT NOT NULL DEFAULT 0,
            scheduled_end_at BIGINT NOT NULL DEFAULT 0,
            started_by TEXT NOT NULL DEFAULT '',
            ended_by TEXT NOT NULL DEFAULT '',
            winner_uuid TEXT NOT NULL DEFAULT '',
            winner_name TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS election_presidents(
            id BIGSERIAL PRIMARY KEY,
            election_id TEXT NOT NULL DEFAULT '',
            minecraft_uuid TEXT NOT NULL DEFAULT '',
            minecraft_name TEXT NOT NULL DEFAULT '',
            active INTEGER NOT NULL DEFAULT 1,
            assigned_at BIGINT NOT NULL DEFAULT 0,
            removed_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS election_decrees(
            id TEXT PRIMARY KEY,
            president_uuid TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'PUBLISHED'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS election_petitions(
            id TEXT PRIMARY KEY,
            creator_uuid TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'OPEN',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_stage TEXT NOT NULL DEFAULT 'NONE'")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_round INTEGER NOT NULL DEFAULT 1")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS candidate_limit INTEGER NOT NULL DEFAULT 4")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_term_days INTEGER NOT NULL DEFAULT 7")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_uuid TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_uuid TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE elections ADD COLUMN IF NOT EXISTS second_round_needed INTEGER NOT NULL DEFAULT 0")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS election_stages(
            id BIGSERIAL PRIMARY KEY,
            election_id TEXT NOT NULL,
            stage TEXT NOT NULL,
            round_no INTEGER NOT NULL DEFAULT 1,
            actor TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS polling_stations(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL DEFAULT '',
            world TEXT NOT NULL DEFAULT '',
            x INTEGER NOT NULL DEFAULT 0,
            y INTEGER NOT NULL DEFAULT 0,
            z INTEGER NOT NULL DEFAULT 0,
            chair_uuid TEXT NOT NULL DEFAULT '',
            chair_name TEXT NOT NULL DEFAULT '',
            active INTEGER NOT NULL DEFAULT 1,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            text_display_uuid TEXT NOT NULL DEFAULT '',
            applications_issued INTEGER NOT NULL DEFAULT 0,
            ballots_issued INTEGER NOT NULL DEFAULT 0,
            ballots_submitted INTEGER NOT NULL DEFAULT 0,
            ballots_annulled INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cik_chairs(
            id BIGSERIAL PRIMARY KEY,
            station_id TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            assigned_at BIGINT NOT NULL DEFAULT 0,
            assigned_by TEXT NOT NULL DEFAULT '',
            active INTEGER NOT NULL DEFAULT 1
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cik_seals(
            id TEXT PRIMARY KEY,
            station_id TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            election_id TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            issued_at BIGINT NOT NULL DEFAULT 0,
            issued_by TEXT NOT NULL DEFAULT '',
            revoked_at BIGINT NOT NULL DEFAULT 0,
            revoked_by TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS candidate_applications(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            station_id TEXT NOT NULL DEFAULT '',
            answers TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ISSUED',
            chair_recommendation TEXT NOT NULL DEFAULT '',
            chair_note TEXT NOT NULL DEFAULT '',
            admin_status TEXT NOT NULL DEFAULT 'PENDING',
            admin_note TEXT NOT NULL DEFAULT '',
            book_signed_at BIGINT NOT NULL DEFAULT 0,
            submitted_at BIGINT NOT NULL DEFAULT 0,
            reviewed_at BIGINT NOT NULL DEFAULT 0,
            reviewed_by TEXT NOT NULL DEFAULT '',
            issued_at BIGINT NOT NULL DEFAULT 0,
            issued_by TEXT NOT NULL DEFAULT '',
            book_token TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS candidates(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            application_id TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            active INTEGER NOT NULL DEFAULT 1,
            last_result INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS station_id TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS answers TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ISSUED'")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS chair_recommendation TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS chair_note TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS admin_status TEXT NOT NULL DEFAULT 'PENDING'")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS admin_note TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS book_signed_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS submitted_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS reviewed_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS reviewed_by TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS issued_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS issued_by TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS book_token TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS id TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS application_id TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1")
    conn.execute("ALTER TABLE candidates ADD COLUMN IF NOT EXISTS last_result INTEGER NOT NULL DEFAULT 0")
    conn.execute(
        "UPDATE candidates SET id='candidate_' || election_id || '_' || player_uuid "
        "WHERE COALESCE(id,'')='' AND COALESCE(election_id,'')<>'' AND COALESCE(player_uuid,'')<>''"
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS ballots(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            round_no INTEGER NOT NULL DEFAULT 1,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            station_id TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ISSUED',
            issued_at BIGINT NOT NULL DEFAULT 0,
            issued_by TEXT NOT NULL DEFAULT '',
            confirmed_candidate_uuid TEXT NOT NULL DEFAULT '',
            confirmed_candidate_name TEXT NOT NULL DEFAULT '',
            confirmed_at BIGINT NOT NULL DEFAULT 0,
            submitted_at BIGINT NOT NULL DEFAULT 0,
            submitted_station_id TEXT NOT NULL DEFAULT '',
            annulled_at BIGINT NOT NULL DEFAULT 0,
            annulled_by TEXT NOT NULL DEFAULT '',
            annul_reason TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        f"""
        CREATE TABLE IF NOT EXISTS votes(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            round_no INTEGER NOT NULL DEFAULT 1,
            ballot_id TEXT NOT NULL,
            {VOTER_UUID_COL} TEXT NOT NULL,
            {VOTER_NAME_COL} TEXT NOT NULL DEFAULT '',
            candidate_uuid TEXT NOT NULL,
            candidate_name TEXT NOT NULL DEFAULT '',
            station_id TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS rounds(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            round_no INTEGER NOT NULL DEFAULT 1,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            started_at BIGINT NOT NULL DEFAULT 0,
            ended_at BIGINT NOT NULL DEFAULT 0,
            winner_uuid TEXT NOT NULL DEFAULT '',
            winner_name TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_terms(
            id TEXT PRIMARY KEY,
            election_id TEXT NOT NULL,
            president_uuid TEXT NOT NULL,
            president_name TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            started_at BIGINT NOT NULL DEFAULT 0,
            ends_at BIGINT NOT NULL DEFAULT 0,
            removed_at BIGINT NOT NULL DEFAULT 0,
            removed_by TEXT NOT NULL DEFAULT '',
            last_broadcast_at BIGINT NOT NULL DEFAULT 0,
            last_law_replace_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_laws(
            id TEXT PRIMARY KEY,
            term_id TEXT NOT NULL,
            president_uuid TEXT NOT NULL,
            text TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'PENDING',
            created_at BIGINT NOT NULL DEFAULT 0,
            published_at BIGINT NOT NULL DEFAULT 0,
            replaced_law_id TEXT NOT NULL DEFAULT '',
            slot_no INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_law_reviews(
            id BIGSERIAL PRIMARY KEY,
            law_id TEXT NOT NULL,
            reviewer TEXT NOT NULL DEFAULT '',
            decision TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_broadcasts(
            id TEXT PRIMARY KEY,
            term_id TEXT NOT NULL,
            president_uuid TEXT NOT NULL,
            format TEXT NOT NULL DEFAULT 'CHAT',
            text TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_taxes(
            id TEXT PRIMARY KEY,
            term_id TEXT NOT NULL,
            amount INTEGER NOT NULL DEFAULT 0,
            period_hours INTEGER NOT NULL DEFAULT 24,
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            created_at BIGINT NOT NULL DEFAULT 0,
            created_by TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_tax_payments(
            id TEXT PRIMARY KEY,
            tax_id TEXT NOT NULL,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            amount BIGINT NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS protected_blocks(
            id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            world TEXT NOT NULL,
            x INTEGER NOT NULL,
            y INTEGER NOT NULL,
            z INTEGER NOT NULL,
            linked_id TEXT NOT NULL DEFAULT '',
            active INTEGER NOT NULL DEFAULT 1,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS text_display_links(
            id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            linked_id TEXT NOT NULL,
            world TEXT NOT NULL DEFAULT '',
            entity_uuid TEXT NOT NULL DEFAULT '',
            text TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            active INTEGER NOT NULL DEFAULT 1
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS admin_actions(
            id BIGSERIAL PRIMARY KEY,
            actor TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL,
            target TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS plugin_events(
            id BIGSERIAL PRIMARY KEY,
            source TEXT NOT NULL DEFAULT '',
            event_type TEXT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            target TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS site_audit(
            id BIGSERIAL PRIMARY KEY,
            actor TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL,
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS discord_status_state(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL DEFAULT '',
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS discord_notifications_log(
            id BIGSERIAL PRIMARY KEY,
            channel_id TEXT NOT NULL DEFAULT '',
            object_type TEXT NOT NULL DEFAULT '',
            object_id TEXT NOT NULL DEFAULT '',
            sent_at BIGINT NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS bridge_events(
            id BIGSERIAL PRIMARY KEY,
            source TEXT NOT NULL DEFAULT '',
            event_type TEXT NOT NULL,
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS status_channel_snapshots(
            id BIGSERIAL PRIMARY KEY,
            channel_id TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            payload TEXT NOT NULL DEFAULT '{}'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS auth_migration_state(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL DEFAULT '',
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS auth_users_imported(
            minecraft_uuid TEXT PRIMARY KEY,
            minecraft_name TEXT NOT NULL DEFAULT '',
            imported_at BIGINT NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS auth_whitelist_sync(
            minecraft_uuid TEXT PRIMARY KEY,
            minecraft_name TEXT NOT NULL DEFAULT '',
            synced_at BIGINT NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS auth_login_checks(
            id BIGSERIAL PRIMARY KEY,
            minecraft_uuid TEXT NOT NULL DEFAULT '',
            minecraft_name TEXT NOT NULL DEFAULT '',
            checked_at BIGINT NOT NULL,
            ok INTEGER NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS auth_effects_disable_audit(
            id BIGSERIAL PRIMARY KEY,
            actor TEXT NOT NULL DEFAULT '',
            target_uuid TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_items_catalog(
            item_id TEXT PRIMARY KEY,
            category TEXT NOT NULL DEFAULT '',
            material TEXT NOT NULL DEFAULT '',
            display_name TEXT NOT NULL DEFAULT '',
            rarity TEXT NOT NULL DEFAULT '',
            price_ar BIGINT NOT NULL DEFAULT 0,
            cooldown_seconds INTEGER NOT NULL DEFAULT 0,
            effect_name TEXT NOT NULL DEFAULT 'NONE',
            custom_model_data INTEGER NOT NULL DEFAULT 0,
            effect_chance_percent INTEGER NOT NULL DEFAULT 100,
            visual_effect_id TEXT NOT NULL DEFAULT '',
            lore_json TEXT NOT NULL DEFAULT '[]',
            enabled INTEGER NOT NULL DEFAULT 1,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data INTEGER NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent INTEGER NOT NULL DEFAULT 100")
    conn.execute("ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id TEXT NOT NULL DEFAULT ''")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_item_instances(
            unique_item_id TEXT PRIMARY KEY,
            item_id TEXT NOT NULL DEFAULT '',
            owner_uuid TEXT NOT NULL DEFAULT '',
            purchase_id TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT '',
            repaired_count INTEGER NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_shops(
            shop_id TEXT PRIMARY KEY,
            world_name TEXT NOT NULL DEFAULT '',
            block_x INTEGER NOT NULL DEFAULT 0,
            block_y INTEGER NOT NULL DEFAULT 0,
            block_z INTEGER NOT NULL DEFAULT 0,
            title TEXT NOT NULL DEFAULT '',
            enabled INTEGER NOT NULL DEFAULT 1,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_purchases(
            purchase_id TEXT PRIMARY KEY,
            unique_item_id TEXT NOT NULL DEFAULT '',
            player_uuid TEXT NOT NULL DEFAULT '',
            player_name TEXT NOT NULL DEFAULT '',
            item_id TEXT NOT NULL DEFAULT '',
            shop_id TEXT NOT NULL DEFAULT '',
            price_ar BIGINT NOT NULL DEFAULT 0,
            bank_tx_id TEXT NOT NULL DEFAULT '',
            idempotency_key TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT '',
            delivery_mode TEXT NOT NULL DEFAULT 'DIRECT',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_repairs(
            repair_id TEXT PRIMARY KEY,
            unique_item_id TEXT NOT NULL DEFAULT '',
            player_uuid TEXT NOT NULL DEFAULT '',
            player_name TEXT NOT NULL DEFAULT '',
            item_id TEXT NOT NULL DEFAULT '',
            repair_cost_ar BIGINT NOT NULL DEFAULT 0,
            bank_tx_id TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_suspicious_events(
            event_id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL DEFAULT '',
            player_name TEXT NOT NULL DEFAULT '',
            event_type TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_audit_log(
            audit_id TEXT PRIMARY KEY,
            actor TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL DEFAULT '',
            target_id TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_pending_deliveries(
            delivery_id TEXT PRIMARY KEY,
            purchase_id TEXT NOT NULL DEFAULT '',
            unique_item_id TEXT NOT NULL DEFAULT '',
            player_uuid TEXT NOT NULL DEFAULT '',
            item_id TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS president_tax_exemptions(
            id TEXT PRIMARY KEY,
            tax_id TEXT NOT NULL DEFAULT '',
            term_id TEXT NOT NULL DEFAULT '',
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            artifact_instance_id TEXT NOT NULL,
            idempotency_key TEXT NOT NULL,
            source TEXT NOT NULL DEFAULT 'TAX_CLOCK_EXEMPTION',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            created_at BIGINT NOT NULL DEFAULT 0,
            expires_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS artifact_revenue_payouts(
            purchase_id TEXT PRIMARY KEY,
            president_uuid TEXT NOT NULL DEFAULT '',
            president_name TEXT NOT NULL DEFAULT '',
            recipient_account_id TEXT NOT NULL DEFAULT 'PRESIDENT_BUDGET',
            buyer_uuid TEXT NOT NULL DEFAULT '',
            buyer_name TEXT NOT NULL DEFAULT '',
            item_id TEXT NOT NULL DEFAULT '',
            shop_id TEXT NOT NULL DEFAULT '',
            amount_ar BIGINT NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'PENDING',
            bank_tx_id TEXT NOT NULL DEFAULT '',
            idempotency_key TEXT NOT NULL DEFAULT '',
            last_error TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS donation_accounts(
            player_uuid TEXT PRIMARY KEY,
            player_name TEXT NOT NULL DEFAULT '',
            balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS donation_balance_ledger(
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL,
            delta BIGINT NOT NULL,
            balance_after BIGINT NOT NULL DEFAULT 0,
            reason TEXT NOT NULL DEFAULT '',
            actor TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT '',
            idempotency_key TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS donation_payment_sessions(
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL DEFAULT '',
            player_name TEXT NOT NULL DEFAULT '',
            provider TEXT NOT NULL DEFAULT 'MOCK_SBP',
            amount BIGINT NOT NULL DEFAULT 0,
            amount_rub BIGINT NOT NULL DEFAULT 0,
            donation_units BIGINT NOT NULL DEFAULT 0,
            currency TEXT NOT NULL DEFAULT 'RUB',
            status TEXT NOT NULL DEFAULT 'CREATED',
            qr_payload TEXT NOT NULL DEFAULT '',
            qr_image_path TEXT NOT NULL DEFAULT '',
            provider_payment_id TEXT NOT NULL DEFAULT '',
            provider_confirmation_url TEXT NOT NULL DEFAULT '',
            callback_payload_json TEXT NOT NULL DEFAULT '',
            idempotency_key TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            expires_at BIGINT NOT NULL DEFAULT 0,
            paid_at BIGINT NOT NULL DEFAULT 0,
            cancelled_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS donation_purchases(
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL DEFAULT '',
            item_id TEXT NOT NULL,
            price BIGINT NOT NULL DEFAULT 0,
            price_donation BIGINT NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'CREATED',
            source TEXT NOT NULL DEFAULT '',
            idempotency_key TEXT NOT NULL DEFAULT '',
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS donation_item_claims(
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL,
            item_id TEXT NOT NULL,
            amount BIGINT NOT NULL DEFAULT 1,
            status TEXT NOT NULL DEFAULT 'UNCLAIMED',
            claimed_at BIGINT NOT NULL DEFAULT 0,
            created_at BIGINT NOT NULL DEFAULT 0,
            updated_at BIGINT NOT NULL DEFAULT 0,
            purchase_id TEXT NOT NULL DEFAULT '',
            actor TEXT NOT NULL DEFAULT ''
        )
        """
    )
    conn.execute("ALTER TABLE temporary_pin_resets ADD COLUMN IF NOT EXISTS delivery_blob TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE bank_pin_hashes ADD COLUMN IF NOT EXISTS pin_sealed TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS amount_rub BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS donation_units BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'RUB'")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS qr_image_path TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS provider_payment_id TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS provider_confirmation_url TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS callback_payload_json TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS paid_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE donation_payment_sessions ADD COLUMN IF NOT EXISTS cancelled_at BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS price_donation BIGINT NOT NULL DEFAULT 0")
    conn.execute("ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT ''")
    conn.execute("ALTER TABLE donation_purchases ADD COLUMN IF NOT EXISTS idempotency_key TEXT NOT NULL DEFAULT ''")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_site_accounts_minecraft ON site_accounts(minecraft_uuid)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_site_accounts_registration_ip ON site_accounts(registration_ip,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_whitelist_requests_account_status ON whitelist_requests(site_account_id,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_whitelist_requests_name_status ON whitelist_requests(minecraft_name,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_security_ip_alerts_status_time ON security_ip_alerts(status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_link_codes_account_status ON one_time_link_codes(site_account_id,status,expires_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_temporary_pin_resets_uuid_time ON temporary_pin_resets(minecraft_uuid,expires_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_pin_reset_audit_uuid_time ON pin_reset_audit(minecraft_uuid,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_failed_pin_attempts_uuid_time ON failed_pin_attempts(minecraft_uuid,attempted_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_account_lockouts_until ON account_lockouts(locked_until DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_security_events_time ON security_events(time DESC,action)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_site_cms_section_order ON site_cms_entries(section,enabled,sort_order,entry_key)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_owner_type_active ON cmv4_bank_accounts(owner_uuid,account_type,currency) WHERE status='ACTIVE'")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_accounts_owner ON cmv4_bank_accounts(owner_uuid,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_ledger_account_time ON cmv4_bank_ledger(account_id,created_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency ON cmv4_bank_ledger(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_from_time ON cmv4_bank_transfers(from_account_id,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_to_time ON cmv4_bank_transfers(to_account_id,created_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency ON cmv4_bank_transfers(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_balance_ledger_idempotency ON donation_balance_ledger(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_sessions_idempotency ON donation_payment_sessions(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_sessions_provider_payment ON donation_payment_sessions(provider_payment_id) WHERE provider_payment_id<>''")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_purchases_idempotency ON donation_purchases(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_donation_balance_ledger_player_time ON donation_balance_ledger(player_uuid,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_donation_sessions_player_status ON donation_payment_sessions(player_uuid,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_donation_purchases_player_status ON donation_purchases(player_uuid,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_donation_claims_player_status ON donation_item_claims(player_uuid,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_ar_money_supply_snapshots_created ON ar_money_supply_snapshots(created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_election_decrees_created ON election_decrees(created_at DESC,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_election_petitions_created ON election_petitions(created_at DESC,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_polling_stations_active ON polling_stations(active,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_candidate_applications_status ON candidate_applications(election_id,admin_status,submitted_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_candidate_applications_election_player ON candidate_applications(election_id,player_uuid)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player ON candidates(election_id,player_uuid)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_ballots_player ON ballots(election_id,round_no,player_uuid,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_votes_round_candidate ON votes(election_id,round_no,candidate_uuid)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_president_laws_status ON president_laws(status,published_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_tax_payments_tax_player ON president_tax_payments(tax_id,player_uuid,created_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_exemptions_player ON president_tax_exemptions(player_uuid)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_tax_exemptions_active ON president_tax_exemptions(status,expires_at DESC)")
    conn.execute("ALTER TABLE president_taxes ADD COLUMN IF NOT EXISTS period_hours INTEGER NOT NULL DEFAULT 24")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_protected_blocks_coords ON protected_blocks(world,x,y,z,active)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_admin_actions_created ON admin_actions(created_at DESC,action)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_plugin_events_created ON plugin_events(created_at DESC,event_type)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_site_audit_created ON site_audit(created_at DESC,action)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_discord_notifications_object ON discord_notifications_log(object_type,object_id,sent_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_bridge_events_created ON bridge_events(created_at DESC,event_type)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_status_channel_snapshots_created ON status_channel_snapshots(channel_id,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_auth_users_imported_time ON auth_users_imported(imported_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_auth_whitelist_sync_time ON auth_whitelist_sync(synced_at DESC,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_auth_login_checks_time ON auth_login_checks(checked_at DESC,ok)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_auth_effects_disable_audit_time ON auth_effects_disable_audit(created_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact_shops_block ON artifact_shops(world_name,block_x,block_y,block_z)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_purchases_player_time ON artifact_purchases(player_uuid,created_at DESC)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_artifact_purchases_idempotency ON artifact_purchases(idempotency_key) WHERE idempotency_key<>''")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_instances_item ON artifact_item_instances(item_id,status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_instances_owner_item_status ON artifact_item_instances(owner_uuid,item_id,status)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_artifact_instances_owner_item_live ON artifact_item_instances(owner_uuid,item_id) WHERE status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_pending_player ON artifact_pending_deliveries(player_uuid,status,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_repairs_player ON artifact_repairs(player_uuid,created_at DESC)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_artifact_suspicious_time ON artifact_suspicious_events(created_at DESC,event_type)")
    conn.execute(
        "INSERT INTO cmv4_schema_migrations(version,applied_at,component) VALUES(%s,%s,%s) ON CONFLICT(version) DO NOTHING",
        ("admin_web_v4_runtime_schema", now_ts() * 1000, "admin-web"),
    )
    conn.execute(
        "INSERT INTO cmv4_schema_migrations(version,applied_at,component) VALUES(%s,%s,%s) ON CONFLICT(version) DO NOTHING",
        ("20260621_007_copimine_election_core_rebuild", now_ts() * 1000, "admin-web"),
    )


def is_pg_conn(conn: Any) -> bool:
    return isinstance(conn, PgCompatConnection)


def pg_table_exists(conn: Any, table: str) -> bool:
    if auth_storage_backend() == "sqlite":
        row = conn.execute(
            "SELECT COUNT(*) AS c FROM sqlite_master WHERE type IN ('table','view') AND name=%s",
            (table,),
        ).fetchone()
        return bool(row and int(row["c"] or 0) > 0)
    row = conn.execute(
        "SELECT COUNT(*) AS c FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=%s",
        (table,),
    ).fetchone()
    return bool(row and int(row["c"] or 0) > 0)


def pg_compat_sql(sql: str) -> str:
    text = str(sql)
    insert_or_ignore = bool(re.search(r"\bINSERT\s+OR\s+IGNORE\s+INTO\b", text, re.I))
    text = re.sub(r"\bINSERT\s+OR\s+IGNORE\s+INTO\b", "INSERT INTO", text, flags=re.I)
    text = re.sub(r"\browid\b", "id", text, flags=re.I)
    text = re.sub(r"\bCOLLATE\s+NOCASE\b", "", text, flags=re.I)
    text = text.replace("?", "%s")
    if insert_or_ignore and "ON CONFLICT" not in text.upper():
        text = text.rstrip().rstrip(";") + " ON CONFLICT DO NOTHING"
    return text


def admin_plugin_db_requested(path: str | Path) -> bool:
    p = Path(path)
    text = str(p).replace("\\", "/").lower()
    return (
        p == Path(ADMIN_PLUGIN_DB)
        or p == admin_plugin_db_path()
        or "/copimineultimateadmin/" in text
        or "/copimineultimateadminplus/" in text
        or p.name.lower() in {"copimine_ultimate.db", "data.db"}
    )


def admin_plugin_db_available(path: str | Path) -> bool:
    return pg_ready() or Path(path).exists()


def admin_plugin_db_location(path: str | Path) -> str:
    if pg_ready():
        return f"postgresql://{POSTGRES_HOST}:{POSTGRES_PORT}/{POSTGRES_DB}?schema={POSTGRES_SCHEMA}"
    return safe_location(Path(path))


def ensure_auth_db() -> None:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS cm_admin_users(
                username TEXT PRIMARY KEY,
                username_norm TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'admin',
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL DEFAULT 0,
                created_by TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0,
                updated_by TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'db'
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS cm_admin_sessions(
                jti TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'admin',
                token_hash TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                ip TEXT NOT NULL DEFAULT '',
                user_agent TEXT NOT NULL DEFAULT '',
                revoked_at INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_cm_admin_sessions_user_exp ON cm_admin_sessions(username,expires_at,revoked_at)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_cm_admin_sessions_exp ON cm_admin_sessions(expires_at,revoked_at)")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS cm_refresh_sessions(
                jti TEXT PRIMARY KEY,
                subject_id TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'player',
                token_hash TEXT NOT NULL DEFAULT '',
                family_id TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                replaced_by TEXT NOT NULL DEFAULT '',
                revoked_at INTEGER NOT NULL DEFAULT 0,
                ip TEXT NOT NULL DEFAULT '',
                user_agent TEXT NOT NULL DEFAULT ''
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_cm_refresh_sessions_subject_exp ON cm_refresh_sessions(subject_id,expires_at,revoked_at)")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_cm_refresh_sessions_family_exp ON cm_refresh_sessions(family_id,expires_at,revoked_at)")
        conn.commit()


def auth_db_user_rows() -> dict[str, dict[str, Any]]:
    ensure_auth_db()
    out: dict[str, dict[str, Any]] = {}
    with auth_conn() as conn:
        for row in conn.execute("SELECT * FROM cm_admin_users ORDER BY lower(username)").fetchall():
            out[str(row["username"])] = {
                "username": str(row["username"]),
                "password_hash": str(row["password_hash"]),
                "role": str(row["role"] or "admin"),
                "enabled": bool(int(row["enabled"] or 0)),
                "createdAt": int(row["created_at"] or 0),
                "createdBy": str(row["created_by"] or "db"),
                "updatedAt": int(row["updated_at"] or 0),
                "updatedBy": str(row["updated_by"] or ""),
                "source": str(row["source"] or "db"),
            }
    return out


def upsert_auth_user(username: str, meta: dict[str, Any]) -> None:
    ensure_auth_db()
    now = int(time.time())
    with auth_conn() as conn:
        conn.execute(
            """
            INSERT INTO cm_admin_users(username,username_norm,password_hash,role,enabled,created_at,created_by,updated_at,updated_by,source)
            VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON CONFLICT(username_norm) DO UPDATE SET
                username=excluded.username,
                password_hash=excluded.password_hash,
                role=excluded.role,
                enabled=excluded.enabled,
                updated_at=excluded.updated_at,
                updated_by=excluded.updated_by,
                source=excluded.source
            """,
            (
                username,
                username.lower(),
                str(meta.get("password_hash", "")),
                str(meta.get("role") or "admin"),
                1 if bool(meta.get("enabled", True)) else 0,
                int(meta.get("createdAt") or now),
                str(meta.get("createdBy") or "migration"),
                int(meta.get("updatedAt") or now),
                str(meta.get("updatedBy") or meta.get("createdBy") or "system"),
                str(meta.get("source") or "db"),
            ),
        )
        conn.commit()


def load_legacy_admin_users() -> dict[str, dict[str, Any]]:
    base: dict[str, dict[str, Any]] = {}
    raw = os.getenv("ADMIN_USERS_JSON", "").strip()
    if raw:
        try:
            base = normalize_admin_users(json.loads(raw))
        except Exception:
            base = {}
    # Backward compatibility, but only if old variables are explicitly configured.
    old_user = os.getenv("ADMIN_USERNAME")
    old_pass = os.getenv("ADMIN_PASSWORD")
    if not base and old_user and old_pass and old_pass != "change-me":
        if old_pass.startswith(("sha256:", "pbkdf2_sha256$")):
            base = {old_user: {"password_hash": old_pass, "role": "admin", "enabled": True}}
        else:
            base = {old_user: {"password_hash": "plain:" + old_pass, "role": "admin", "enabled": True}}
    if not base:
        base = normalize_admin_users(DEFAULT_ADMIN_USERS)
    overlay = read_admin_overlay()
    # Overlay can add users or disable/change default users without editing .env.
    base.update(overlay)
    return base


def migrate_legacy_admins_to_auth_db() -> None:
    legacy = load_legacy_admin_users()
    for username, meta in legacy.items():
        if not valid_minecraft_name(username):
            continue
        item = dict(meta)
        item.setdefault("createdAt", int(time.time()))
        item.setdefault("createdBy", "legacy-json")
        item.setdefault("updatedAt", int(time.time()))
        item.setdefault("updatedBy", "legacy-json")
        item.setdefault("source", "legacy-json")
        upsert_auth_user(username, item)


def load_admin_users() -> dict[str, dict[str, Any]]:
    ensure_auth_db()
    users = auth_db_user_rows()
    if not users:
        migrate_legacy_admins_to_auth_db()
        users = auth_db_user_rows()
    return users


def current_admin_users_nonblocking() -> dict[str, dict[str, Any]]:
    if auth_storage_backend() != "sqlite":
        return load_admin_users()
    try:
        out: dict[str, dict[str, Any]] = {}
        with auth_conn() as conn:
            if not pg_table_exists(conn, "cm_admin_users"):
                return load_legacy_admin_users()
            for row in conn.execute("SELECT * FROM cm_admin_users ORDER BY lower(username)").fetchall():
                out[str(row["username"])] = {
                    "username": str(row["username"]),
                    "password_hash": str(row["password_hash"]),
                    "role": str(row["role"] or "admin"),
                    "enabled": bool(int(row["enabled"] or 0)),
                    "createdAt": int(row["created_at"] or 0),
                    "createdBy": str(row["created_by"] or "db"),
                    "updatedAt": int(row["updated_at"] or 0),
                    "updatedBy": str(row["updated_by"] or ""),
                    "source": str(row["source"] or "db"),
                }
        return out or load_legacy_admin_users()
    except sqlite3.OperationalError:
        return load_legacy_admin_users()


def current_admin_users() -> dict[str, dict[str, Any]]:
    return load_admin_users()


ADMIN_USERS = DEFAULT_ADMIN_USERS  # legacy snapshot; runtime checks use current_admin_users()


def is_reserved_admin_username(username: str) -> bool:
    """Prevent player accounts from colliding with enabled panel accounts."""
    normalized = str(username or "").strip().casefold()
    if not normalized:
        return False
    try:
        admins = current_admin_users_nonblocking()
    except Exception:
        # Fail closed if the admin directory cannot be read.
        return True
    return any(
        str(name).strip().casefold() == normalized and bool(meta.get("enabled", True))
        for name, meta in admins.items()
    )
def now_ts() -> int:
    return int(time.time())


def donation_now_ms() -> int:
    return int(time.time() * 1000)


def donation_epoch_ms(value: Any) -> int:
    try:
        parsed = int(value or 0)
    except (TypeError, ValueError):
        return 0
    if parsed <= 0:
        return 0
    return parsed * 1000 if parsed < DONATION_EPOCH_MS_THRESHOLD else parsed


def normalize_donation_row_timestamps(item: dict[str, Any], *fields: str) -> dict[str, Any]:
    for field in fields:
        if field in item:
            item[field] = donation_epoch_ms(item.get(field))
    return item


def sha256_hex(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def make_password_hash(raw: str) -> str:
    salt = secrets.token_hex(16)
    iterations = 260000
    digest = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt.encode("utf-8"), iterations).hex()
    return f"pbkdf2_sha256${iterations}${salt}${digest}"


def password_policy_ok(raw: str) -> tuple[bool, str]:
    if len(raw) < 8:
        return False, "Пароль должен быть минимум 8 символов"
    if len(raw) > 128:
        return False, "Пароль слишком длинный"
    return True, ""


def verify_password_hash(stored: str, raw: str) -> bool:
    if stored.startswith("sha256:"):
        return hmac.compare_digest(stored.split(":", 1)[1], sha256_hex(raw))
    if stored.startswith("pbkdf2_sha256$"):
        try:
            _, iterations, salt, digest = stored.split("$", 3)
            check = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt.encode("utf-8"), int(iterations)).hex()
            return hmac.compare_digest(check, digest)
        except Exception:
            return False
    if stored.startswith("plain:") and os.getenv("ALLOW_PLAIN_ADMIN_PASSWORDS", "0") == "1":
        return hmac.compare_digest(stored.split(":", 1)[1], raw)
    return False


def resolve_admin_user(username: str) -> tuple[str, dict[str, Any]]:
    users = current_admin_users()
    meta = users.get(username)
    if meta is not None:
        return username, meta
    username_l = username.lower()
    for real, item in users.items():
        if real.lower() == username_l:
            return real, item
    return "", {}


def read_sessions() -> dict[str, Any]:
    ensure_auth_db()
    current = now_ts()
    out: dict[str, Any] = {}
    with auth_conn() as conn:
        conn.execute("DELETE FROM cm_admin_sessions WHERE expires_at<=%s OR revoked_at>0", (current,))
        for row in conn.execute("SELECT * FROM cm_admin_sessions WHERE expires_at>%s AND revoked_at=0", (current,)).fetchall():
            out[str(row["jti"])] = {
                "username": str(row["username"]),
                "role": str(row["role"] or "admin"),
                "createdAt": int(row["created_at"] or 0),
                "expiresAt": int(row["expires_at"] or 0),
                "ip": str(row["ip"] or ""),
                "userAgent": str(row["user_agent"] or ""),
            }
        conn.commit()
    return out


def write_sessions(data: dict[str, Any]) -> None:
    ensure_auth_db()
    with auth_conn() as conn:
        conn.execute("DELETE FROM cm_admin_sessions")
        for jti, item in data.items():
            conn.execute(
                """
                INSERT INTO cm_admin_sessions(jti,username,role,token_hash,created_at,expires_at,ip,user_agent,revoked_at)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,0)
                ON CONFLICT(jti) DO UPDATE SET
                    username=excluded.username,
                    role=excluded.role,
                    token_hash=excluded.token_hash,
                    created_at=excluded.created_at,
                    expires_at=excluded.expires_at,
                    ip=excluded.ip,
                    user_agent=excluded.user_agent,
                    revoked_at=0
                """,
                (
                    str(jti),
                    str(item.get("username", "")),
                    str(item.get("role", "admin")),
                    "",
                    int(item.get("createdAt", now_ts())),
                    int(item.get("expiresAt", now_ts())),
                    str(item.get("ip", "")),
                    str(item.get("userAgent", "")),
                ),
            )
        conn.commit()


def save_session(jti: str, token: str, username: str, role: str, request: Optional[Request], issued: int, expires: int) -> None:
    ensure_auth_db()
    with auth_conn() as conn:
        conn.execute(
            """
            INSERT INTO cm_admin_sessions(jti,username,role,token_hash,created_at,expires_at,ip,user_agent,revoked_at)
            VALUES(%s,%s,%s,%s,%s,%s,%s,%s,0)
            ON CONFLICT(jti) DO UPDATE SET
                username=excluded.username,
                role=excluded.role,
                token_hash=excluded.token_hash,
                created_at=excluded.created_at,
                expires_at=excluded.expires_at,
                ip=excluded.ip,
                user_agent=excluded.user_agent,
                revoked_at=0
            """,
            (
                jti,
                username,
                role,
                sha256_hex(token),
                issued,
                expires,
                get_client_ip(request),
                request.headers.get("user-agent", "")[:300] if request else "",
            ),
        )
        conn.execute("DELETE FROM cm_admin_sessions WHERE expires_at<=%s OR revoked_at>0", (issued,))
        conn.commit()


def revoke_session(jti: str) -> None:
    if not jti:
        return
    ensure_auth_db()
    with auth_conn() as conn:
        conn.execute("UPDATE cm_admin_sessions SET revoked_at=%s WHERE jti=%s", (now_ts(), jti))
        conn.commit()


def save_refresh_session(jti: str, subject_id: str, role: str, family_id: str, token: str, request: Optional[Request], issued: int, expires: int) -> None:
    ensure_auth_db()
    with auth_conn() as conn:
        conn.execute(
            """
            INSERT INTO cm_refresh_sessions(jti,subject_id,role,token_hash,family_id,created_at,expires_at,replaced_by,revoked_at,ip,user_agent)
            VALUES(%s,%s,%s,%s,%s,%s,%s,'',0,%s,%s)
            ON CONFLICT(jti) DO UPDATE SET
                subject_id=excluded.subject_id,
                role=excluded.role,
                token_hash=excluded.token_hash,
                family_id=excluded.family_id,
                created_at=excluded.created_at,
                expires_at=excluded.expires_at,
                replaced_by='',
                revoked_at=0,
                ip=excluded.ip,
                user_agent=excluded.user_agent
            """,
            (
                jti,
                subject_id,
                role,
                sha256_hex(token),
                family_id,
                issued,
                expires,
                get_client_ip(request),
                request.headers.get("user-agent", "")[:300] if request else "",
            ),
        )
        conn.execute("DELETE FROM cm_refresh_sessions WHERE expires_at<=%s OR revoked_at>0", (issued,))
        conn.commit()


def read_refresh_session(jti: str) -> Optional[dict[str, Any]]:
    if not jti:
        return None
    ensure_auth_db()
    with auth_conn() as conn:
        row = conn.execute("SELECT * FROM cm_refresh_sessions WHERE jti=%s", (jti,)).fetchone()
        conn.commit()
    return dict(row) if row else None


def revoke_refresh_session(jti: str, replaced_by: str = "") -> None:
    if not jti:
        return
    ensure_auth_db()
    with auth_conn() as conn:
        conn.execute(
            "UPDATE cm_refresh_sessions SET revoked_at=%s, replaced_by=COALESCE(NULLIF(%s,''), replaced_by) WHERE jti=%s",
            (now_ts(), replaced_by, jti),
        )
        conn.commit()


def make_token(username: str, role: str, request: Optional[Request] = None, ttl: int = ACCESS_TOKEN_TTL_SECONDS, extra_claims: Optional[dict[str, Any]] = None) -> str:
    issued = now_ts()
    expires = issued + ttl
    jti = secrets.token_urlsafe(18)
    payload = {
        "iss": TOKEN_ISSUER,
        "sub": username,
        "role": role,
        "jti": jti,
        "typ": "access",
        "token_version": 1,
        "exp": expires,
        "iat": issued,
    }
    if extra_claims:
        payload.update(extra_claims)
    token = encode_signed_token(payload)
    save_session(jti, token, username, role, request, issued, expires)
    return token


def make_refresh_token(
    subject_id: str,
    role: str,
    request: Optional[Request] = None,
    ttl: int = REFRESH_TOKEN_TTL_SECONDS,
    family_id: str = "",
    extra_claims: Optional[dict[str, Any]] = None,
) -> str:
    issued = now_ts()
    expires = issued + ttl
    jti = secrets.token_urlsafe(18)
    family = family_id or secrets.token_urlsafe(18)
    payload = {
        "iss": TOKEN_ISSUER,
        "sub": subject_id,
        "role": role,
        "jti": jti,
        "family": family,
        "typ": "refresh",
        "token_version": 1,
        "exp": expires,
        "iat": issued,
    }
    if extra_claims:
        payload.update(extra_claims)
    token = encode_signed_token(payload)
    save_refresh_session(jti, subject_id, role, family, token, request, issued, expires)
    return token


def verify_token(token: str) -> dict[str, Any]:
    try:
        payload = decode_signed_token(token)
        if str(payload.get("typ") or "access") != "access":
            raise ValueError("wrong token type")
        sessions = read_sessions()
        jti = str(payload.get("jti", ""))
        if not jti or jti not in sessions:
            raise ValueError("session revoked")
        session = sessions[jti]
        if int(session.get("expiresAt", 0)) < now_ts():
            sessions.pop(jti, None)
            write_sessions(sessions)
            raise ValueError("session expired")
        return payload
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Сессия недействительна или истекла") from exc


def verify_refresh_token(token: str) -> tuple[dict[str, Any], dict[str, Any]]:
    try:
        payload = decode_signed_token(token)
        if str(payload.get("typ") or "") != "refresh":
            raise ValueError("wrong token type")
        jti = str(payload.get("jti") or "")
        row = read_refresh_session(jti)
        if not row:
            raise ValueError("refresh session missing")
        if int(row.get("revoked_at") or 0) > 0:
            raise ValueError("refresh revoked")
        if str(row.get("replaced_by") or "").strip():
            raise ValueError("refresh replaced")
        if int(row.get("expires_at") or 0) < now_ts():
            raise ValueError("refresh expired")
        if not hmac.compare_digest(str(row.get("token_hash") or ""), sha256_hex(token)):
            raise ValueError("refresh hash mismatch")
        return payload, row
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Refresh-сессия недействительна или истекла") from exc


def minecraft_access_lists() -> dict[str, Any]:
    ops_raw = read_json(MC_SERVER_DIR / "ops.json", [])
    whitelist_raw = read_json(MC_SERVER_DIR / "whitelist.json", [])
    ops = ops_raw if isinstance(ops_raw, list) else []
    whitelist = whitelist_raw if isinstance(whitelist_raw, list) else []
    data = {
        "ops": ops,
        "whitelist": whitelist,
        "opNames": {str(x.get("name", "")).lower() for x in ops if isinstance(x, dict) and x.get("name")},
        "opUuids": {str(x.get("uuid", "")).lower() for x in ops if isinstance(x, dict) and x.get("uuid")},
        "whitelistNames": {str(x.get("name", "")).lower() for x in whitelist if isinstance(x, dict) and x.get("name")},
        "whitelistUuids": {str(x.get("uuid", "")).lower() for x in whitelist if isinstance(x, dict) and x.get("uuid")},
    }
    if pg_ready():
        try:
            sync_auth_whitelist_state()
        except Exception:
            pass
    return data


def minecraft_access_ok(username: str) -> tuple[bool, list[str]]:
    errors: list[str] = []
    username_l = username.lower()
    uuid = name_to_uuid().get(username_l, "").lower()
    lists = minecraft_access_lists()
    if REQUIRE_OP_FOR_LOGIN and username_l not in lists["opNames"] and (not uuid or uuid not in lists["opUuids"]):
        errors.append("ник не найден в списке операторов сервера")
    if REQUIRE_WHITELIST_FOR_LOGIN and username_l not in lists["whitelistNames"] and (not uuid or uuid not in lists["whitelistUuids"]):
        errors.append("ник не найден в вайтлисте сервера")
    ok = not errors
    if pg_ready():
        try:
            log_auth_login_check(uuid, username, ok, {"errors": errors, "requireOp": REQUIRE_OP_FOR_LOGIN, "requireWhitelist": REQUIRE_WHITELIST_FOR_LOGIN})
        except Exception:
            pass
    return ok, errors


def login_key(request: Request, username: str) -> str:
    ip = get_client_ip(request)
    return f"{ip}:{username.lower()}"


def register_failed_login(request: Request, username: str) -> None:
    key = login_key(request, username)
    current = now_ts()
    attempts = [x for x in FAILED_LOGINS.get(key, []) if current - x < LOGIN_LOCK_SECONDS]
    attempts.append(current)
    FAILED_LOGINS[key] = attempts
    if len(attempts) >= LOGIN_MAX_ATTEMPTS:
        LOCKED_UNTIL[key] = current + LOGIN_LOCK_SECONDS


def clear_failed_login(request: Request, username: str) -> None:
    key = login_key(request, username)
    FAILED_LOGINS.pop(key, None)
    LOCKED_UNTIL.pop(key, None)


def assert_not_locked(request: Request, username: str) -> None:
    key = login_key(request, username)
    until = LOCKED_UNTIL.get(key, 0)
    if until > now_ts():
        raise HTTPException(status_code=429, detail=f"Слишком много попыток входа. Повтори через {until - now_ts()} сек.")


def request_auth_token(request: Request, authorization: str = "") -> str:
    if request.cookies.get(AUTH_COOKIE_NAME):
        return str(request.cookies.get(AUTH_COOKIE_NAME))
    if AUTH_BEARER_FALLBACK_ENABLED and authorization.startswith("Bearer "):
        return authorization.removeprefix("Bearer ").strip()
    return ""


def request_refresh_token(request: Request) -> str:
    return str(request.cookies.get(AUTH_REFRESH_COOKIE_NAME) or "").strip()


def normalize_admin_role(value: Any) -> str:
    role = str(value or "admin").strip().lower()
    if role in {"owner", "admin", "junior_admin"}:
        return role
    return "admin"


def is_panel_admin_role(role: str) -> bool:
    return normalize_admin_role(role) in {"owner", "admin", "junior_admin"}


def is_full_admin_role(role: str) -> bool:
    return normalize_admin_role(role) in {"owner", "admin"}


def require_panel_admin_context(request: Request, authorization: str = Header(default="")) -> dict[str, Any]:
    token = request_auth_token(request, authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Нужна авторизация")
    payload = verify_token(token)
    username = str(payload.get("sub", ""))
    real_username, meta = resolve_admin_user(username)
    if not meta or not bool(meta.get("enabled", True)):
        raise HTTPException(status_code=403, detail="Доступ к админке отозван")
    role = normalize_admin_role(meta.get("role"))
    if not is_panel_admin_role(role):
        raise HTTPException(status_code=403, detail="Недостаточно прав для панели")
    access_ok, access_errors = minecraft_access_ok(real_username)
    if not access_ok:
        raise HTTPException(status_code=403, detail="Minecraft-доступ отозван: " + "; ".join(access_errors))
    return {
        "username": real_username,
        "role": role,
        "fullAccess": is_full_admin_role(role),
    }


def require_panel_admin(request: Request, authorization: str = Header(default="")) -> str:
    return str(require_panel_admin_context(request, authorization).get("username") or "")


def require_admin(request: Request, authorization: str = Header(default="")) -> str:
    context = require_panel_admin_context(request, authorization)
    if not bool(context.get("fullAccess")):
        raise HTTPException(status_code=403, detail="Нужны полные права администратора")
    return str(context.get("username") or "")


def require_owner(request: Request, authorization: str = Header(default="")) -> str:
    context = require_panel_admin_context(request, authorization)
    if normalize_admin_role(context.get("role")) != "owner":
        raise HTTPException(status_code=403, detail="Доступно только владельцу панели")
    return str(context.get("username") or "")


def admin_role_for_username(username: str) -> str:
    real_username, meta = resolve_admin_user(username)
    if not real_username or not meta:
        return ""
    return normalize_admin_role(meta.get("role"))


def ensure_admin_account_create_allowed(actor_username: str, target_username: str, requested_role: str) -> None:
    actor_role = admin_role_for_username(actor_username)
    existing_target_role = admin_role_for_username(target_username)
    target_role = normalize_admin_role(requested_role)
    if not is_full_admin_role(actor_role):
        raise HTTPException(status_code=403, detail="Нужны полные права администратора")
    if target_role == "owner" and actor_role != "owner":
        raise HTTPException(status_code=403, detail="Только владелец панели может создавать owner-аккаунты")
    if existing_target_role == "owner" and actor_role != "owner":
        raise HTTPException(status_code=403, detail="Только владелец панели может изменять существующий owner-аккаунт")


def ensure_admin_account_owner_mutation_allowed(actor_username: str, target_username: str, requested_role: Optional[str] = None) -> None:
    actor_role = admin_role_for_username(actor_username)
    target_role = admin_role_for_username(target_username)
    next_role = normalize_admin_role(requested_role) if requested_role is not None else target_role
    if (target_role == "owner" or next_role == "owner") and actor_role != "owner":
        raise HTTPException(status_code=403, detail="Только владелец панели может менять или отключать owner-аккаунты")


def valid_site_username(username: str) -> bool:
    return bool(re.fullmatch(r"[A-Za-z0-9_]{3,32}", username or ""))


def normalize_pin(pin: str) -> str:
    value = str(pin or "").strip()
    if not re.fullmatch(r"\d{4,8}", value):
        raise HTTPException(status_code=400, detail="PIN must contain 4-8 digits")
    return value


def row_get(row: Any, key: str, default: Any = None) -> Any:
    if row is None:
        return default
    if isinstance(row, dict):
        return row.get(key, default)
    try:
        return row[key]
    except Exception:
        try:
            return dict(row).get(key, default)
        except Exception:
            return default


def player_account_by_id(conn: Any, account_id: str) -> dict[str, Any]:
    row = conn.execute("SELECT * FROM site_accounts WHERE id=%s AND enabled=1", (account_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="Player account is disabled or missing")
    return dict(row)


def player_account_by_username(conn: Any, username: str) -> Optional[dict[str, Any]]:
    row = conn.execute("SELECT * FROM site_accounts WHERE username_norm=%s", (username.lower(),)).fetchone()
    return dict(row) if row else None


def player_account_by_minecraft_name(conn: Any, minecraft_name: str) -> Optional[dict[str, Any]]:
    normalized = str(minecraft_name or "").strip().lower()
    if not normalized:
        return None
    row = conn.execute("SELECT * FROM site_accounts WHERE LOWER(minecraft_name)=%s ORDER BY updated_at DESC LIMIT 1", (normalized,)).fetchone()
    if row:
        return dict(row)
    row = conn.execute(
        """
        SELECT sa.*
        FROM whitelist_account_links wal
        JOIN site_accounts sa ON sa.id=wal.site_account_id
        WHERE LOWER(wal.minecraft_name)=%s
        ORDER BY sa.updated_at DESC
        LIMIT 1
        """,
        (normalized,),
    ).fetchone()
    return dict(row) if row else None


def require_player(request: Request, authorization: str = Header(default="")) -> dict[str, Any]:
    token = request_auth_token(request, authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Player authorization is required")
    payload = verify_token(token)
    if str(payload.get("role") or "") != "player":
        raise HTTPException(status_code=403, detail="Player session is required")
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        account = player_account_by_id(conn, str(payload.get("sub") or ""))
        if not account:
            raise HTTPException(status_code=401, detail="Player account is missing")
        if not bool(int(account.get("enabled") or 0)):
            raise HTTPException(status_code=403, detail="Player account is disabled")
        return account


def is_loopback_request(request: Request) -> bool:
    host = str(request.client.host if request.client else "").strip().lower()
    if not host:
        return False
    if host.startswith("::ffff:"):
        host = host.split("::ffff:", 1)[1]
    return host in {"127.0.0.1", "::1", "localhost"}


def public_player_account(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": row.get("id"),
        "username": row.get("username"),
        "role": "player",
        "minecraftUuid": row.get("minecraft_uuid") or "",
        "minecraftName": row.get("minecraft_name") or "",
        "linked": bool(row.get("minecraft_uuid")),
        "createdAt": int(row.get("created_at") or 0),
        "lastLoginAt": int(row.get("last_login_at") or 0),
    }


def issue_admin_auth_pair(
    username: str,
    role: str,
    request: Optional[Request],
    remember_me: bool = False,
) -> tuple[str, str]:
    access = make_token(username, role, request, extra_claims={"display_name": username, "remember": remember_me})
    refresh = make_refresh_token(username, role, request, extra_claims={"remember": remember_me})
    return access, refresh


def issue_player_auth_pair(
    account: Mapping[str, Any] | dict[str, Any],
    request: Optional[Request],
    family_id: str = "",
    remember_me: bool = False,
) -> tuple[str, str]:
    account_id = str(account.get("id") or "")
    access = make_token(
        account_id,
        "player",
        request,
        extra_claims={
            "display_name": str(account.get("username") or ""),
            "player_uuid": str(account.get("minecraft_uuid") or ""),
            "remember": remember_me,
        },
    )
    refresh = make_refresh_token(account_id, "player", request, family_id=family_id, extra_claims={"remember": remember_me})
    return access, refresh


def rotate_auth_pair_from_refresh_sync(refresh_token: str, request: Optional[Request], audience: str) -> dict[str, Any]:
    payload, row = verify_refresh_token(refresh_token)
    role = str(payload.get("role") or "")
    subject = str(payload.get("sub") or "")
    family_id = str(payload.get("family") or row.get("family_id") or "")
    remember_me = bool(payload.get("remember"))
    if audience == "player":
        if role != "player":
            raise HTTPException(status_code=403, detail="Эта refresh-сессия не относится к кабинету игрока")
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            account = player_account_by_id(conn, subject)
            if not account:
                revoke_refresh_session(str(payload.get("jti") or ""))
                raise HTTPException(status_code=401, detail="Аккаунт игрока не найден")
            if not bool(int(account.get("enabled") or 0)):
                revoke_refresh_session(str(payload.get("jti") or ""))
                raise HTTPException(status_code=403, detail="Аккаунт игрока отключён")
            conn.commit()
        access_token, refresh_token_new = issue_player_auth_pair(account, request, family_id=family_id, remember_me=remember_me)
        refresh_payload = decode_signed_token(refresh_token_new)
        revoke_refresh_session(str(payload.get("jti") or ""), str(refresh_payload.get("jti") or ""))
        return {
            "role": "player",
            "fullAccess": False,
            "owner": False,
            "cookieAuth": True,
            "expiresIn": ACCESS_TOKEN_TTL_SECONDS,
            "account": public_player_account(account),
            "accessToken": access_token,
            "refreshToken": refresh_token_new,
            "rememberMe": remember_me,
        }
    if role == "player":
        raise HTTPException(status_code=403, detail="Эта refresh-сессия не относится к админ-панели")
    real_username, meta = resolve_admin_user(subject)
    if not meta or not bool(meta.get("enabled", True)):
        revoke_refresh_session(str(payload.get("jti") or ""))
        raise HTTPException(status_code=403, detail="Доступ к админке отозван")
    current_role = normalize_admin_role(meta.get("role"))
    if not is_panel_admin_role(current_role):
        revoke_refresh_session(str(payload.get("jti") or ""))
        raise HTTPException(status_code=403, detail="Недостаточно прав для панели")
    access_ok, access_errors = minecraft_access_ok(real_username)
    if not access_ok:
        raise HTTPException(status_code=403, detail="Minecraft-доступ отозван: " + "; ".join(access_errors))
    access_token = make_token(real_username, current_role, request, extra_claims={"display_name": real_username, "remember": remember_me})
    refresh_token_new = make_refresh_token(real_username, current_role, request, family_id=family_id, extra_claims={"remember": remember_me})
    refresh_payload = decode_signed_token(refresh_token_new)
    revoke_refresh_session(str(payload.get("jti") or ""), str(refresh_payload.get("jti") or ""))
    return {
        "username": real_username,
        "role": current_role,
        "fullAccess": is_full_admin_role(current_role),
        "owner": current_role == "owner",
        "cookieAuth": True,
        "expiresIn": ACCESS_TOKEN_TTL_SECONDS,
        "accessToken": access_token,
        "refreshToken": refresh_token_new,
        "rememberMe": remember_me,
    }


def create_ip_alert_sync(ip: str, username: str, minecraft_name: str, reason: str, details: dict[str, Any] | None = None) -> dict[str, Any]:
    alert = {
        "id": f"ip-alert-{secrets.token_hex(8)}",
        "ip": str(ip or "").strip(),
        "username": str(username or "").strip(),
        "minecraftName": str(minecraft_name or "").strip(),
        "reason": str(reason or "").strip(),
        "status": "OPEN",
        "createdAt": now_ts(),
        "updatedAt": now_ts(),
        "details": redact_value(details or {}),
    }
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            """
            INSERT INTO security_ip_alerts(id,ip,username,minecraft_name,reason,status,created_at,updated_at,details)
            VALUES(%s,%s,%s,%s,%s,'OPEN',%s,%s,%s)
            """,
            (
                alert["id"],
                alert["ip"],
                alert["username"],
                alert["minecraftName"],
                alert["reason"],
                alert["createdAt"],
                alert["updatedAt"],
                pg_json_dumps(alert["details"]),
            ),
        )
        conn.commit()
    append_panel_event("security", "ip_alert", actor=username, target=ip, metadata={"minecraftName": minecraft_name, "reason": reason}, severity="warn", tags=["security", "ip"])
    return alert


def read_ip_alerts_sync(limit: int = 100) -> list[dict[str, Any]]:
    safe_limit = max(1, min(limit, 300))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            """
            SELECT id,ip,username,minecraft_name,reason,status,created_at,updated_at,details
            FROM security_ip_alerts
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        conn.commit()
    out: list[dict[str, Any]] = []
    for row in rows:
        item = dict(row)
        item["details"] = pg_json_loads(item.get("details"), {})
        out.append(item)
    return out


def read_player_whitelist_state_sync(site_account_id: str, minecraft_uuid: str, minecraft_name: str) -> dict[str, Any]:
    effective_uuid = resolve_minecraft_uuid(minecraft_uuid, minecraft_name)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        whitelisted = False
        if effective_uuid:
            row = conn.execute("SELECT whitelisted,synced_at FROM whitelist_account_links WHERE minecraft_uuid=%s LIMIT 1", (effective_uuid,)).fetchone()
            whitelisted = bool(int(row["whitelisted"] or 0)) if row else False
        request = conn.execute(
            """
            SELECT id,minecraft_uuid,minecraft_name,status,created_at,updated_at,approved_at,approved_by,note
            FROM whitelist_requests
            WHERE site_account_id=%s
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (site_account_id,),
        ).fetchone()
        conn.commit()
    latest = dict(request) if request else {}
    automatic_registration = str(latest.get("status") or "").upper() == "AUTO_APPROVED"
    if automatic_registration:
        whitelisted = True
    return {
        "whitelisted": whitelisted,
        "whitelistRequest": {
            "id": str(latest.get("id") or ""),
            "status": str(latest.get("status") or ""),
            "createdAt": int(latest.get("created_at") or 0),
            "updatedAt": int(latest.get("updated_at") or 0),
            "approvedAt": int(latest.get("approved_at") or 0),
            "approvedBy": str(latest.get("approved_by") or ""),
            "note": str(latest.get("note") or ""),
        } if latest else None,
        "minecraftName": minecraft_name or str(latest.get("minecraft_name") or ""),
        "minecraftUuid": effective_uuid or str(latest.get("minecraft_uuid") or ""),
    }


def minecraft_identity_seen_on_server(minecraft_name: str, minecraft_uuid: str) -> bool:
    """Return true for an identity that has already left a server-side footprint."""
    name = str(minecraft_name or "").strip().lower()
    resolved_uuid = str(find_player_uuid(minecraft_name) or minecraft_uuid or "").strip().lower()
    for path in (MC_SERVER_DIR / "usercache.json", MC_SERVER_DIR / "whitelist.json", MC_SERVER_DIR / "ops.json"):
        rows = read_json(path, [])
        if not isinstance(rows, list):
            continue
        for row in rows:
            if not isinstance(row, dict):
                continue
            row_name = str(row.get("name") or "").strip().lower()
            row_uuid = str(row.get("uuid") or "").strip().lower()
            if (name and row_name == name) or (resolved_uuid and row_uuid == resolved_uuid):
                return True
    if resolved_uuid:
        for directory, suffix in ((WORLD_DIR / "playerdata", ".dat"), (WORLD_DIR / "stats", ".json"), (WORLD_DIR / "advancements", ".json")):
            if (directory / f"{resolved_uuid}{suffix}").exists():
                return True
    return False


def minecraft_identity_is_bound_to_site(conn: Any, minecraft_uuid: str, minecraft_name: str) -> bool:
    name = str(minecraft_name or "").strip()
    uuid_value = str(minecraft_uuid or "").strip()
    checks = [
        ("SELECT 1 FROM minecraft_account_links WHERE minecraft_uuid=%s LIMIT 1", (uuid_value,)),
        ("SELECT 1 FROM whitelist_account_links WHERE minecraft_uuid=%s LIMIT 1", (uuid_value,)),
        (
            "SELECT 1 FROM site_accounts WHERE minecraft_uuid=%s OR LOWER(minecraft_name)=LOWER(%s) LIMIT 1",
            (uuid_value, name),
        ),
    ]
    for query, params in checks:
        if conn.execute(query, params).fetchone():
            return True
    return False


def ensure_minecraft_identity_linkable(conn: Any, site_account_id: str, minecraft_uuid: str, minecraft_name: str) -> None:
    """Prevent a confirmation code from moving a live identity to another account."""
    account_id = str(site_account_id or "").strip()
    uuid_value = str(minecraft_uuid or "").strip()
    name = str(minecraft_name or "").strip()
    if not account_id or not uuid_value or not valid_minecraft_name(name):
        raise HTTPException(status_code=400, detail="Некорректные данные привязки Minecraft")
    checks = (
        ("minecraft_account_links", "site_account_id", "minecraft_uuid=%s", (uuid_value,)),
        ("whitelist_account_links", "site_account_id", "minecraft_uuid=%s", (uuid_value,)),
        ("site_accounts", "id", "(minecraft_uuid=%s OR LOWER(minecraft_name)=LOWER(%s))", (uuid_value, name)),
    )
    for table, owner_column, predicate, params in checks:
        rows = conn.execute(f"SELECT {owner_column} FROM {table} WHERE {predicate}", params).fetchall()
        for row in rows:
            owner = str(row_get(row, owner_column, "") or "")
            if owner and owner != account_id:
                raise HTTPException(status_code=409, detail="Этот Minecraft-ник уже привязан к другому аккаунту. Используйте восстановление доступа.")
    current = conn.execute("SELECT minecraft_uuid FROM site_accounts WHERE id=%s", (account_id,)).fetchone()
    current_uuid = str(row_get(current, "minecraft_uuid", "") or "")
    if current_uuid and current_uuid != uuid_value:
        raise HTTPException(status_code=409, detail="К этому аккаунту уже привязан другой Minecraft-ник")


def create_whitelist_request_sync(account: dict[str, Any], request_ip: str) -> dict[str, Any]:
    site_account_id = str(account.get("id") or "")
    minecraft_uuid = resolve_minecraft_uuid(str(account.get("minecraft_uuid") or ""), str(account.get("minecraft_name") or ""))
    minecraft_name = str(account.get("minecraft_name") or "")
    if not site_account_id or not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Сначала привяжи корректный Minecraft-ник")
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        row = conn.execute("SELECT whitelisted FROM whitelist_account_links WHERE minecraft_uuid=%s LIMIT 1", (minecraft_uuid,)).fetchone()
        if row and int(row["whitelisted"] or 0) > 0:
            raise HTTPException(status_code=409, detail="Игрок уже находится в whitelist")
        active = conn.execute(
            """
            SELECT id,status,created_at
            FROM whitelist_requests
            WHERE site_account_id=%s AND status IN ('PENDING','APPROVED')
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (site_account_id,),
        ).fetchone()
        if active:
            existing = dict(active)
            return {
                "id": str(existing.get("id") or ""),
                "status": str(existing.get("status") or "PENDING"),
                "createdAt": int(existing.get("created_at") or 0),
                "minecraftName": minecraft_name,
                "minecraftUuid": minecraft_uuid,
                "alreadyExists": True,
            }
        request_id = f"wl-{secrets.token_hex(10)}"
        conn.execute(
            """
            INSERT INTO whitelist_requests(id,site_account_id,minecraft_uuid,minecraft_name,request_ip,status,created_at,updated_at)
            VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)
            """,
            (request_id, site_account_id, minecraft_uuid, minecraft_name, str(request_ip or ""), now, now),
        )
        conn.commit()
    append_panel_event("whitelist", "request_created", actor=str(account.get("username") or ""), target=minecraft_name, metadata={"requestId": request_id}, tags=["whitelist", "player"])
    return {
        "id": request_id,
        "status": "PENDING",
        "createdAt": now,
        "minecraftName": minecraft_name,
        "minecraftUuid": minecraft_uuid,
        "alreadyExists": False,
    }


def read_whitelist_requests_sync(limit: int = 100) -> list[dict[str, Any]]:
    safe_limit = max(1, min(limit, 300))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            """
            SELECT wr.id,wr.site_account_id,wr.minecraft_uuid,wr.minecraft_name,wr.request_ip,wr.status,
                   wr.created_at,wr.updated_at,wr.approved_at,wr.approved_by,wr.note,
                   sa.username
            FROM whitelist_requests wr
            LEFT JOIN site_accounts sa ON sa.id=wr.site_account_id
            ORDER BY wr.created_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        conn.commit()
    return [dict(row) for row in rows]


def add_player_to_whitelist_sync(minecraft_uuid: str, minecraft_name: str) -> dict[str, Any]:
    if not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Некорректный Minecraft-ник")
    whitelist_path = MC_SERVER_DIR / "whitelist.json"
    rows = read_json(whitelist_path, [])
    entries = rows if isinstance(rows, list) else []
    lower_name = minecraft_name.lower()
    lower_uuid = minecraft_uuid.lower()
    exists = any(
        isinstance(item, dict) and (
            str(item.get("name") or "").lower() == lower_name
            or (minecraft_uuid and str(item.get("uuid") or "").lower() == lower_uuid)
        )
        for item in entries
    )
    if not exists:
        entries.append({"uuid": minecraft_uuid, "name": minecraft_name})
        write_json(whitelist_path, entries)
    rcon_state = "FILE_ONLY"
    if RCON_PASSWORD:
        try:
            rcon_quick(f"whitelist add {minecraft_name}")
            rcon_quick("whitelist reload")
            rcon_state = "RCON_AND_FILE"
        except Exception:
            rcon_state = "FILE_ONLY"
    return {"ok": True, "whitelisted": True, "rconState": rcon_state}


def approve_whitelist_request_sync(request_id: str, actor: str, note: str = "", source: str = "web") -> dict[str, Any]:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        row = conn.execute(
            """
            SELECT wr.*,sa.username
            FROM whitelist_requests wr
            LEFT JOIN site_accounts sa ON sa.id=wr.site_account_id
            WHERE wr.id=%s
            FOR UPDATE
            """,
            (request_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Whitelist request not found")
        item = dict(row)
        status = str(item.get("status") or "PENDING").upper()
        if status not in {"PENDING", "APPROVED"}:
            raise HTTPException(status_code=409, detail="Этот запрос whitelist нельзя одобрить повторно")
        minecraft_uuid = resolve_minecraft_uuid(str(item.get("minecraft_uuid") or ""), str(item.get("minecraft_name") or ""))
        minecraft_name = str(item.get("minecraft_name") or "")
        if not minecraft_uuid:
            raise HTTPException(status_code=400, detail="Не удалось вычислить UUID для этого ника")
        if status == "APPROVED":
            conn.commit()
            return {
                "id": request_id,
                "status": "APPROVED",
                "minecraftName": minecraft_name,
                "minecraftUuid": minecraft_uuid,
                "approvedBy": str(item.get("approved_by") or ""),
                "approvedAt": int(item.get("approved_at") or 0),
                "idempotent": True,
            }
        result = add_player_to_whitelist_sync(minecraft_uuid, minecraft_name)
        now = now_ts()
        conn.execute(
            "UPDATE whitelist_requests SET minecraft_uuid=%s,status='APPROVED',updated_at=%s,approved_at=%s,approved_by=%s,note=%s WHERE id=%s",
            (minecraft_uuid, now, now, actor, note, request_id),
        )
        conn.execute(
            """
            INSERT INTO whitelist_account_links(minecraft_uuid,minecraft_name,site_account_id,whitelisted,synced_at)
            VALUES(%s,%s,%s,1,%s)
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                minecraft_name=excluded.minecraft_name,
                site_account_id=excluded.site_account_id,
                whitelisted=1,
                synced_at=excluded.synced_at
            """,
            (minecraft_uuid, minecraft_name, str(item.get("site_account_id") or ""), now),
        )
        conn.commit()
    append_panel_event("whitelist", "approved", actor=actor, target=minecraft_name, metadata={"requestId": request_id, "source": source}, tags=["whitelist", "admin"])
    return {
        "id": request_id,
        "status": "APPROVED",
        "minecraftName": minecraft_name,
        "minecraftUuid": minecraft_uuid,
        "approvedBy": actor,
        "approvedAt": now,
        "idempotent": False,
        "rconState": result.get("rconState", "FILE_ONLY"),
    }


def live_plugin_ar_balance_sync(minecraft_uuid: str, minecraft_name: str = "") -> dict[str, Any]:
    safe_uuid = str(minecraft_uuid or "").strip()
    safe_name = str(minecraft_name or "").strip()
    path = admin_plugin_db_path()
    if not safe_uuid or not admin_plugin_db_available(path):
        return {}
    try:
        with open_sqlite_readonly(str(path)) as sqlite_conn:
            if not sqlite_has_table(sqlite_conn, "cmv7_ar_balances"):
                return {}
            row = sqlite_conn.execute(
                """
                SELECT uuid,name,balance,inventory_balance,ender_balance,updated_at
                FROM cmv7_ar_balances
                WHERE uuid=? OR (?<>'' AND lower(name)=lower(?))
                ORDER BY CASE WHEN uuid=? THEN 0 ELSE 1 END, updated_at DESC
                LIMIT 1
                """,
                (safe_uuid, safe_name, safe_name, safe_uuid),
            ).fetchone()
            return dict(row or {})
    except Exception:
        return {}


def ensure_player_bank_account(conn: Any, minecraft_uuid: str, minecraft_name: str) -> dict[str, Any]:
    if not minecraft_uuid:
        raise HTTPException(status_code=409, detail="Minecraft account is not linked")
    account_id = f"ar:{minecraft_uuid}"
    now = donation_now_ms()
    conn.execute(
        """
        INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at)
        VALUES(%s,%s,%s,'PLAYER','AR',0,'ACTIVE',0,%s,%s)
        ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at
        """,
        (account_id, minecraft_uuid, minecraft_name or "", now, now),
    )
    row = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s", (account_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=500, detail="Bank account was not created")
    bank = dict(row)
    ledger_count_row = conn.execute("SELECT COUNT(*) AS c FROM cmv4_bank_ledger WHERE account_id=%s", (account_id,)).fetchone()
    ledger_count = int(row_get(ledger_count_row, "c", 0) or 0)
    if int(bank.get("balance") or 0) <= 0 and ledger_count == 0:
        live_balance = live_plugin_ar_balance_sync(minecraft_uuid, minecraft_name)
        if int(live_balance.get("balance") or 0) > 0:
            synced_balance = int(live_balance.get("balance") or 0)
            updated_at = int(live_balance.get("updated_at") or now)
            conn.execute(
                "UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s,owner_name=%s WHERE account_id=%s",
                (synced_balance, updated_at, minecraft_name or bank.get("owner_name") or "", account_id),
            )
            row = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s", (account_id,)).fetchone()
            bank = dict(row or bank)
    return bank


def current_treasury_owner(conn: Any) -> tuple[str, str]:
    return "", TREASURY_ACCOUNT_LABEL


def ensure_treasury_bank_account(conn: Any) -> dict[str, Any]:
    owner_uuid, owner_name = current_treasury_owner(conn)
    now = donation_now_ms()
    conn.execute(
        """
        INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at)
        VALUES(%s,%s,%s,%s,'AR',0,'ACTIVE',0,%s,%s)
        ON CONFLICT(account_id) DO UPDATE SET owner_uuid=excluded.owner_uuid,owner_name=excluded.owner_name,updated_at=excluded.updated_at
        """,
        (TREASURY_ACCOUNT_ID, owner_uuid, owner_name, TREASURY_ACCOUNT_TYPE, now, now),
    )
    row = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s", (TREASURY_ACCOUNT_ID,)).fetchone()
    if not row:
        raise HTTPException(status_code=500, detail="Treasury account was not created")
    return dict(row)


def player_is_site_admin(account: dict[str, Any]) -> bool:
    username = str(account.get("username") or "").strip()
    if not username:
        return False
    users = current_admin_users_nonblocking()
    real_username = next((name for name in users if name.lower() == username.lower()), username)
    meta = users.get(real_username)
    if not meta or not bool(meta.get("enabled", True)):
        return False
    role = normalize_admin_role(meta.get("role"))
    if not is_panel_admin_role(role):
        return False
    access_ok, _ = minecraft_access_ok(real_username)
    return access_ok


def player_is_active_president(conn: Any, account: dict[str, Any]) -> bool:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        return False
    term = active_president_term(conn)
    return bool(term) and str(term.get("president_uuid") or "") == uuid


def has_treasury_access(conn: Any, account: dict[str, Any]) -> bool:
    return player_is_site_admin(account) or player_is_active_president(conn, account)


def temporary_pin_length() -> int:
    return max(4, min(8, TEMP_PIN_LENGTH))


def temp_pin_secret() -> bytes:
    return hashlib.sha256((SECRET_KEY + ":copimine-temp-pin:v1").encode("utf-8")).digest()


def seal_temporary_pin(minecraft_uuid: str, pin: str) -> str:
    nonce = secrets.token_bytes(16)
    purpose = b"copimine-temp-pin:v1:" + minecraft_uuid.encode("utf-8")
    mask = hmac.new(temp_pin_secret(), purpose + nonce, hashlib.sha256).digest()
    raw = pin.encode("utf-8")
    cipher = bytes(raw[i] ^ mask[i % len(mask)] for i in range(len(raw)))
    body = nonce + cipher
    sig = hmac.new(temp_pin_secret(), body + purpose, hashlib.sha256).digest()
    return _b64(body + sig)


def reveal_temporary_pin(minecraft_uuid: str, delivery_blob: str) -> str:
    if not delivery_blob:
        return ""
    try:
        raw = _unb64(delivery_blob)
    except Exception:
        return ""
    if len(raw) < 48:
        return ""
    nonce = raw[:16]
    cipher = raw[16:-32]
    sig = raw[-32:]
    purpose = b"copimine-temp-pin:v1:" + minecraft_uuid.encode("utf-8")
    expected = hmac.new(temp_pin_secret(), nonce + cipher + purpose, hashlib.sha256).digest()
    if not hmac.compare_digest(sig, expected):
        return ""
    mask = hmac.new(temp_pin_secret(), purpose + nonce, hashlib.sha256).digest()
    try:
        pin = bytes(cipher[i] ^ mask[i % len(mask)] for i in range(len(cipher))).decode("utf-8")
    except Exception:
        return ""
    return pin if re.fullmatch(r"\d{4,8}", pin or "") else ""


def persistent_pin_secret() -> bytes:
    return hashlib.sha256((SECRET_KEY + ":copimine-bank-pin:v1").encode("utf-8")).digest()


def seal_persistent_pin(pin_key: str, pin: str) -> str:
    nonce = secrets.token_bytes(16)
    purpose = b"copimine-bank-pin:v1:" + str(pin_key or "").encode("utf-8")
    mask = hmac.new(persistent_pin_secret(), purpose + nonce, hashlib.sha256).digest()
    raw = pin.encode("utf-8")
    cipher = bytes(raw[i] ^ mask[i % len(mask)] for i in range(len(raw)))
    body = nonce + cipher
    sig = hmac.new(persistent_pin_secret(), body + purpose, hashlib.sha256).digest()
    return _b64(body + sig)


def reveal_persistent_pin(pin_key: str, sealed: str) -> str:
    if not sealed:
        return ""
    try:
        raw = _unb64(sealed)
    except Exception:
        return ""
    if len(raw) < 48:
        return ""
    nonce = raw[:16]
    cipher = raw[16:-32]
    sig = raw[-32:]
    purpose = b"copimine-bank-pin:v1:" + str(pin_key or "").encode("utf-8")
    expected = hmac.new(persistent_pin_secret(), nonce + cipher + purpose, hashlib.sha256).digest()
    if not hmac.compare_digest(sig, expected):
        return ""
    mask = hmac.new(persistent_pin_secret(), purpose + nonce, hashlib.sha256).digest()
    try:
        pin = bytes(cipher[i] ^ mask[i % len(mask)] for i in range(len(cipher))).decode("utf-8")
    except Exception:
        return ""
    return pin if re.fullmatch(r"\d{4,8}", pin or "") else ""


def expire_temporary_pin_resets(conn: Any, minecraft_uuid: str = "") -> None:
    current = now_ts()
    if minecraft_uuid:
        conn.execute(
            """
            UPDATE temporary_pin_resets
            SET used_at=CASE WHEN used_at=0 THEN expires_at ELSE used_at END,
                delivery_blob=''
            WHERE minecraft_uuid=%s AND used_at=0 AND expires_at<=%s
            """,
            (minecraft_uuid, current),
        )
        return
    conn.execute(
        """
        UPDATE temporary_pin_resets
        SET used_at=CASE WHEN used_at=0 THEN expires_at ELSE used_at END,
            delivery_blob=''
        WHERE used_at=0 AND expires_at<=%s
        """,
        (current,),
    )


def active_temporary_pin_reset(conn: Any, site_account_id: str, minecraft_uuid: str, reveal: bool = False) -> dict[str, Any]:
    if not minecraft_uuid:
        return {}
    expire_temporary_pin_resets(conn, minecraft_uuid)
    row = conn.execute(
        """
        SELECT id,site_account_id,delivery_blob,expires_at,used_at,created_at
        FROM temporary_pin_resets
        WHERE minecraft_uuid=%s
          AND used_at=0
          AND expires_at>%s
          AND (site_account_id=%s OR site_account_id='')
        ORDER BY created_at DESC
        LIMIT 1
        """,
        (minecraft_uuid, now_ts(), site_account_id),
    ).fetchone()
    if not row:
        return {}
    result = {
        "pending": True,
        "id": str(row["id"] or ""),
        "siteAccountId": str(row["site_account_id"] or ""),
        "createdAt": int(row["created_at"] or 0),
        "expiresAt": int(row["expires_at"] or 0),
    }
    if reveal:
        code = reveal_temporary_pin(minecraft_uuid, str(row["delivery_blob"] or ""))
        if code:
            result["code"] = code
    return result


def clear_temporary_pin_resets(conn: Any, minecraft_uuid: str, used_at: Optional[int] = None) -> None:
    if not minecraft_uuid:
        return
    stamp = int(used_at or now_ts())
    conn.execute(
        """
        UPDATE temporary_pin_resets
        SET used_at=CASE WHEN used_at=0 THEN %s ELSE used_at END,
            delivery_blob=''
        WHERE minecraft_uuid=%s AND used_at=0
        """,
        (stamp, minecraft_uuid),
    )


def bank_pin_status(conn: Any, site_account_id: str, minecraft_uuid: str) -> dict[str, Any]:
    row = conn.execute("SELECT must_change,updated_at FROM bank_pin_hashes WHERE minecraft_uuid=%s", (minecraft_uuid,)).fetchone()
    locked_until = bank_pin_locked_until(conn, minecraft_uuid)
    temp_reset = active_temporary_pin_reset(conn, site_account_id, minecraft_uuid, reveal=False)
    must_change = bool(int(row["must_change"] or 0)) if row else False
    status = "missing"
    if row:
        status = "temporary" if must_change else "configured"
    if must_change and not temp_reset:
        status = "temporary-expired"
    if locked_until > now_ts():
        status = "locked"
    return {
        "set": bool(row),
        "mustChange": must_change,
        "updatedAt": int(row["updated_at"] or 0) if row else 0,
        "lockedUntil": locked_until,
        "locked": locked_until > now_ts(),
        "status": status,
        "temporaryPending": bool(temp_reset),
        "temporaryIssuedAt": int(temp_reset.get("createdAt") or 0),
        "temporaryExpiresAt": int(temp_reset.get("expiresAt") or 0),
    }


def bank_pin_lockout_key(minecraft_uuid: str) -> str:
    return f"bank-pin:{minecraft_uuid}"


def account_pin_lockout_key(account_id: str) -> str:
    return f"bank-pin:account:{account_id}"


def bank_pin_locked_until(conn: Any, minecraft_uuid: str) -> int:
    row = conn.execute("SELECT locked_until FROM account_lockouts WHERE account_id=%s", (bank_pin_lockout_key(minecraft_uuid),)).fetchone()
    return int(row["locked_until"] or 0) if row else 0


def account_pin_locked_until(conn: Any, account_id: str) -> int:
    row = conn.execute("SELECT locked_until FROM account_lockouts WHERE account_id=%s", (account_pin_lockout_key(account_id),)).fetchone()
    return int(row["locked_until"] or 0) if row else 0


def enforce_bank_pin_lockout(conn: Any, minecraft_uuid: str) -> None:
    locked_until = bank_pin_locked_until(conn, minecraft_uuid)
    current = now_ts()
    if locked_until > current:
        raise HTTPException(status_code=429, detail=f"PIN temporarily locked. Try again in {locked_until - current} seconds")


def record_failed_bank_pin(conn: Any, account: dict[str, Any], source: str) -> None:
    minecraft_uuid = str(account.get("minecraft_uuid") or "")
    site_account_id = str(account.get("id") or "")
    current = now_ts()
    conn.execute(
        "INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(%s,%s,%s,%s)",
        (minecraft_uuid, site_account_id, current, source),
    )
    recent = conn.execute(
        "SELECT COUNT(*) AS attempts FROM failed_pin_attempts WHERE minecraft_uuid=%s AND attempted_at>=%s",
        (minecraft_uuid, current - PIN_ATTEMPT_WINDOW_SECONDS),
    ).fetchone()
    if int(recent["attempts"] or 0) >= PIN_MAX_ATTEMPTS:
        locked_until = current + PIN_LOCK_SECONDS
        key = bank_pin_lockout_key(minecraft_uuid)
        conn.execute(
            """
            INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at)
            VALUES(%s,%s,'bank-pin',%s)
            ON CONFLICT(account_id) DO UPDATE SET
                locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),
                reason=excluded.reason,
                updated_at=excluded.updated_at
            """,
            (key, locked_until, current),
        )
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PIN_LOCKOUT',%s,%s)",
            (current, site_account_id, f"minecraft_uuid={minecraft_uuid} until={locked_until}", source),
        )


def clear_bank_pin_lockout(conn: Any, minecraft_uuid: str) -> None:
    conn.execute("DELETE FROM account_lockouts WHERE account_id=%s", (bank_pin_lockout_key(minecraft_uuid),))


def clear_account_pin_lockout(conn: Any, account_id: str) -> None:
    conn.execute("DELETE FROM account_lockouts WHERE account_id=%s", (account_pin_lockout_key(account_id),))


def verify_bank_pin(conn: Any, account: dict[str, Any], pin: str) -> None:
    pin = normalize_pin(pin)
    enforce_bank_pin_lockout(conn, str(account.get("minecraft_uuid") or ""))
    row = conn.execute("SELECT pin_hash,must_change FROM bank_pin_hashes WHERE minecraft_uuid=%s", (account.get("minecraft_uuid"),)).fetchone()
    if not row or not verify_password_hash(str(row["pin_hash"] or ""), pin):
        record_failed_bank_pin(conn, account, "site")
        conn.commit()
        raise HTTPException(status_code=403, detail="Invalid PIN")
    if int(row["must_change"] or 0) > 0:
        raise HTTPException(status_code=403, detail="Temporary PIN must be changed first")
    clear_bank_pin_lockout(conn, str(account.get("minecraft_uuid") or ""))


def verify_account_pin(conn: Any, account_id: str, pin: str) -> None:
    normalized = normalize_pin(pin)
    locked_until = account_pin_locked_until(conn, account_id)
    current = now_ts()
    if locked_until > current:
        raise HTTPException(status_code=429, detail=f"PIN temporarily locked. Try again in {locked_until - current} seconds")
    row = conn.execute("SELECT pin_hash,must_change FROM bank_account_pins WHERE account_id=%s", (account_id,)).fetchone()
    if not row or not verify_password_hash(str(row["pin_hash"] or ""), normalized):
        conn.execute(
            "INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(%s,%s,%s,%s)",
            ("", account_id, current, "site-account"),
        )
        recent = conn.execute(
            "SELECT COUNT(*) AS attempts FROM failed_pin_attempts WHERE site_account_id=%s AND attempted_at>=%s",
            (account_id, current - PIN_ATTEMPT_WINDOW_SECONDS),
        ).fetchone()
        if int(row_get(recent, "attempts", 0) or 0) >= PIN_MAX_ATTEMPTS:
            conn.execute(
                """
                INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at)
                VALUES(%s,%s,'bank-account-pin',%s)
                ON CONFLICT(account_id) DO UPDATE SET
                    locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),
                    reason=excluded.reason,
                    updated_at=excluded.updated_at
                """,
                (account_pin_lockout_key(account_id), current + PIN_LOCK_SECONDS, current),
            )
        conn.commit()
        raise HTTPException(status_code=403, detail="Invalid PIN")
    if int(row["must_change"] or 0) > 0:
        raise HTTPException(status_code=403, detail="Temporary PIN must be changed first")
    clear_account_pin_lockout(conn, account_id)


def visible_personal_pin(conn: Any, minecraft_uuid: str) -> str:
    row = conn.execute("SELECT pin_sealed FROM bank_pin_hashes WHERE minecraft_uuid=%s", (minecraft_uuid,)).fetchone()
    return reveal_persistent_pin(f"personal:{minecraft_uuid}", str(row_get(row, "pin_sealed", "") or ""))


def visible_account_pin(conn: Any, account_id: str) -> str:
    row = conn.execute("SELECT pin_sealed FROM bank_account_pins WHERE account_id=%s", (account_id,)).fetchone()
    return reveal_persistent_pin(f"account:{account_id}", str(row_get(row, "pin_sealed", "") or ""))


def resolve_bank_recipient(conn: Any, recipient: str) -> tuple[str, str]:
    value = str(recipient or "").strip()
    row = conn.execute(
        "SELECT minecraft_uuid,minecraft_name FROM site_accounts WHERE enabled=1 AND (lower(username)=lower(%s) OR lower(minecraft_name)=lower(%s) OR minecraft_uuid=%s) LIMIT 1",
        (value, value, value),
    ).fetchone()
    if row and row["minecraft_uuid"]:
        return str(row["minecraft_uuid"]), str(row["minecraft_name"] or value)
    uuid = find_player_uuid(value) or ""
    if uuid:
        return uuid, uuid_to_name().get(uuid, value)
    raise HTTPException(status_code=404, detail="Recipient is not linked to CopiMine")


def list_player_bank_recipients_sync(account: dict[str, Any], q: str = "", limit: int = 40) -> dict[str, Any]:
    account_uuid = str(account.get("minecraft_uuid") or "").strip()
    query = str(q or "").strip().lower()
    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    capped_limit = max(1, min(int(limit or 40), 100))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        candidates = conn.execute(
            """
            SELECT minecraft_uuid,minecraft_name,username,last_login_at,updated_at,created_at
            FROM site_accounts
            WHERE enabled=1 AND minecraft_uuid<>'' AND minecraft_name<>''
            ORDER BY COALESCE(last_login_at, updated_at, created_at) DESC
            LIMIT 400
            """
        ).fetchall()
        for row in candidates:
            minecraft_uuid = str(row["minecraft_uuid"] or "").strip()
            minecraft_name = str(row["minecraft_name"] or "").strip()
            username = str(row["username"] or "").strip()
            if not minecraft_uuid or not minecraft_name or minecraft_uuid == account_uuid:
                continue
            haystack = f"{minecraft_name} {username} {minecraft_uuid}".lower()
            if query and query not in haystack:
                continue
            lowered_name = minecraft_name.lower()
            if lowered_name in seen:
                continue
            seen.add(lowered_name)
            rows.append({
                "uuid": minecraft_uuid,
                "name": minecraft_name,
                "username": username,
                "bankLinked": True,
            })
            if len(rows) >= capped_limit:
                break
        conn.commit()
    return {"recipients": rows, "count": len(rows)}


def lock_bank_accounts_ordered(conn: Any, first_account_id: str, second_account_id: str) -> tuple[dict[str, Any], dict[str, Any]]:
    safe_first = str(first_account_id or "")
    safe_second = str(second_account_id or "")
    if not safe_first or not safe_second:
        raise HTTPException(status_code=500, detail="Bank account lock target is missing")
    if safe_first == safe_second:
        row = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE", (safe_first,)).fetchone()
        locked = dict(row or {})
        return locked, dict(locked)
    ordered = sorted((safe_first, safe_second))
    locked_rows: dict[str, dict[str, Any]] = {}
    for account_id in ordered:
        row = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE", (account_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Bank account not found")
        locked_rows[account_id] = dict(row)
    return locked_rows[safe_first], locked_rows[safe_second]


def player_bank_overview_sync(account: dict[str, Any], limit: int = 80) -> dict[str, Any]:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        bank = ensure_player_bank_account(conn, str(account.get("minecraft_uuid") or ""), str(account.get("minecraft_name") or ""))
        ledger = conn.execute(
            """
            SELECT tx_id,tx_type,amount,balance_after,counterparty_account_id,created_at,details
            FROM cmv4_bank_ledger
            WHERE account_id=%s
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (bank["account_id"], max(1, min(limit, 200))),
        ).fetchall()
        pin = bank_pin_status(conn, str(account.get("id") or ""), str(account.get("minecraft_uuid") or ""))
        temp_reset = active_temporary_pin_reset(conn, str(account.get("id") or ""), str(account.get("minecraft_uuid") or ""), reveal=True)
        can_treasury = has_treasury_access(conn, account)
        treasury_account = {}
        treasury_ledger: list[dict[str, Any]] = []
        treasury_pin: dict[str, Any] = {}
        if can_treasury:
            treasury_account = ensure_treasury_bank_account(conn)
            treasury_ledger = [dict(x) for x in conn.execute(
                """
                SELECT tx_id,tx_type,amount,balance_after,counterparty_account_id,created_at,details
                FROM cmv4_bank_ledger
                WHERE account_id=%s
                ORDER BY created_at DESC
                LIMIT %s
                """,
                (TREASURY_ACCOUNT_ID, max(1, min(limit, 200))),
            ).fetchall()]
            treasury_pin = {
                "set": bool(conn.execute("SELECT 1 FROM bank_account_pins WHERE account_id=%s", (TREASURY_ACCOUNT_ID,)).fetchone()),
                # Never return the treasury PIN in a regular player response.
                "visiblePin": "",
                "status": "configured",
            }
        conn.commit()
    return {
        "account": {
            "accountId": bank["account_id"],
            "ownerUuid": bank["owner_uuid"],
            "ownerName": bank["owner_name"],
            "currency": bank["currency"],
            "balance": int(bank["balance"] or 0),
            "status": bank["status"],
        },
        "pin": pin,
        "temporaryPin": temp_reset,
        "ledger": [dict(x) for x in ledger],
        "canAccessTreasury": can_treasury,
        "accounts": [
            {
                "scope": "PERSONAL",
                "label": "Личный счёт",
                "accountId": bank["account_id"],
                "balance": int(bank["balance"] or 0),
                "currency": bank["currency"],
                "ledger": [dict(x) for x in ledger],
            },
            *([
                {
                    "scope": "TREASURY",
                    "label": TREASURY_ACCOUNT_LABEL,
                    "accountId": treasury_account.get("account_id") or TREASURY_ACCOUNT_ID,
                    "balance": int(treasury_account.get("balance") or 0),
                    "currency": str(treasury_account.get("currency") or "AR"),
                    "ledger": treasury_ledger,
                }
            ] if can_treasury else []),
        ],
        "treasuryAccount": treasury_account,
        "treasuryPin": treasury_pin,
    }


def set_player_pin_sync(account: dict[str, Any], data: PlayerPinSetIn) -> dict[str, Any]:
    new_pin = normalize_pin(data.new_pin)
    scope = str(data.account_scope or "PERSONAL").strip().upper()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        now = now_ts()
        if scope == "TREASURY":
            if not has_treasury_access(conn, account):
                raise HTTPException(status_code=403, detail="Treasury account is not available")
            ensure_treasury_bank_account(conn)
            current = conn.execute("SELECT pin_hash,must_change FROM bank_account_pins WHERE account_id=%s", (TREASURY_ACCOUNT_ID,)).fetchone()
            if current:
                verify_account_pin(conn, TREASURY_ACCOUNT_ID, data.old_pin or "")
            conn.execute(
                """
                INSERT INTO bank_account_pins(account_id,pin_hash,pin_sealed,must_change,created_at,updated_at,updated_by)
                VALUES(%s,%s,%s,0,%s,%s,%s)
                ON CONFLICT(account_id) DO UPDATE SET
                    pin_hash=excluded.pin_hash,
                    pin_sealed=excluded.pin_sealed,
                    must_change=0,
                    updated_at=excluded.updated_at,
                    updated_by=excluded.updated_by
                """,
                (TREASURY_ACCOUNT_ID, make_password_hash(new_pin), seal_persistent_pin(f"account:{TREASURY_ACCOUNT_ID}", new_pin), now, now, account.get("username") or ""),
            )
            clear_account_pin_lockout(conn, TREASURY_ACCOUNT_ID)
            conn.execute(
                "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'TREASURY_PIN_SET',%s,'site')",
                (now, account.get("username") or "", TREASURY_ACCOUNT_ID),
            )
        else:
            current = conn.execute("SELECT pin_hash,must_change FROM bank_pin_hashes WHERE minecraft_uuid=%s", (account.get("minecraft_uuid") or "",)).fetchone()
            if current:
                enforce_bank_pin_lockout(conn, str(account.get("minecraft_uuid") or ""))
                if not data.old_pin or not verify_password_hash(str(current["pin_hash"] or ""), normalize_pin(data.old_pin)):
                    record_failed_bank_pin(conn, account, "site-pin-change")
                    conn.commit()
                    raise HTTPException(status_code=403, detail="Old PIN is invalid")
            conn.execute(
                """
                INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed,must_change,created_at,updated_at)
                VALUES(%s,%s,%s,%s,0,%s,%s)
                ON CONFLICT(minecraft_uuid) DO UPDATE SET
                    site_account_id=excluded.site_account_id,
                    pin_hash=excluded.pin_hash,
                    pin_sealed=excluded.pin_sealed,
                    must_change=0,
                    updated_at=excluded.updated_at
                """,
                (account.get("minecraft_uuid") or "", account.get("id") or "", make_password_hash(new_pin), seal_persistent_pin(f"personal:{account.get('minecraft_uuid') or ''}", new_pin), now, now),
            )
            conn.execute(
                "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PIN_SET','minecraft_uuid=' || %s,'site')",
                (now, account.get("username") or "", account.get("minecraft_uuid") or ""),
            )
            clear_bank_pin_lockout(conn, str(account.get("minecraft_uuid") or ""))
            clear_temporary_pin_resets(conn, str(account.get("minecraft_uuid") or ""), now)
            if current and int(current["must_change"] or 0) > 0:
                conn.execute(
                    "INSERT INTO pin_reset_audit(minecraft_uuid,actor,created_at,details) VALUES(%s,%s,%s,%s)",
                    (
                        account.get("minecraft_uuid") or "",
                        account.get("username") or "",
                        now,
                        "temporary_pin_completed_via_site",
                    ),
                )
        conn.commit()
    return {"ok": True, "pinSet": True, "mustChange": False, "scope": scope}


def player_site_bank_profile_sync(minecraft_uuid: str, minecraft_name: str) -> dict[str, Any]:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        expire_temporary_pin_resets(conn, minecraft_uuid)
        site_row = conn.execute(
            """
            SELECT id,username,enabled,created_at,updated_at,last_login_at,minecraft_name
            FROM site_accounts
            WHERE minecraft_uuid=%s
            ORDER BY enabled DESC,last_login_at DESC,created_at DESC
            LIMIT 1
            """,
            (minecraft_uuid,),
        ).fetchone()
        site = dict(site_row) if site_row else {}
        bank = ensure_player_bank_account(conn, minecraft_uuid, minecraft_name)
        ensure_donation_account_row(conn, minecraft_uuid, minecraft_name)
        donation_row = conn.execute(
            "SELECT player_uuid,player_name,balance,created_at,updated_at FROM donation_accounts WHERE player_uuid=%s LIMIT 1",
            (minecraft_uuid,),
        ).fetchone()
        donation = dict(donation_row) if donation_row else {}
        live_balance = live_plugin_ar_balance_sync(minecraft_uuid, minecraft_name)
        pin = bank_pin_status(conn, str(site.get("id") or ""), minecraft_uuid)
        pin["visiblePin"] = visible_personal_pin(conn, minecraft_uuid)
        conn.commit()
    return {
        "siteAccount": {
            "id": str(site.get("id") or ""),
            "username": str(site.get("username") or ""),
            "enabled": bool(int(site.get("enabled") or 0)) if site else False,
            "linked": bool(site),
            "minecraftName": str(site.get("minecraft_name") or minecraft_name or ""),
            "createdAt": int(site.get("created_at") or 0),
            "updatedAt": int(site.get("updated_at") or 0),
            "lastLoginAt": int(site.get("last_login_at") or 0),
        },
        "bank": {
            "accountId": str(bank.get("account_id") or ""),
            "ownerUuid": str(bank.get("owner_uuid") or minecraft_uuid),
            "ownerName": str(bank.get("owner_name") or minecraft_name or ""),
            "currency": str(bank.get("currency") or "AR"),
            "balance": int(bank.get("balance") or 0),
            "status": str(bank.get("status") or ("ACTIVE" if bank else "")),
            "updatedAt": int(bank.get("updated_at") or 0),
            "livePluginBalance": int(live_balance.get("balance") or 0),
            "livePluginUpdatedAt": int(live_balance.get("updated_at") or 0),
        },
        "donation": {
            "playerUuid": str(donation.get("player_uuid") or minecraft_uuid),
            "playerName": str(donation.get("player_name") or minecraft_name or ""),
            "balance": int(donation.get("balance") or 0),
            "createdAt": int(donation.get("created_at") or 0),
            "updatedAt": int(donation.get("updated_at") or 0),
        },
        "pin": pin,
    }


def admin_update_player_account_sync(player: str, actor: str, data: AdminPlayerAccountUpdateIn) -> dict[str, Any]:
    """Change a linked player's site credentials without ever returning secrets."""
    requested_username = str(data.username or "").strip()
    requested_password = data.new_password
    if not requested_username and requested_password is None:
        raise HTTPException(status_code=400, detail="Укажи новый логин или новый пароль")
    if requested_username and not valid_site_username(requested_username):
        raise HTTPException(status_code=400, detail="Укажи корректный логин")
    if requested_password is not None:
        ok, reason = password_policy_ok(requested_password)
        if not ok:
            raise HTTPException(status_code=400, detail=reason)
    uuid = find_player_uuid(player) or ""
    if not uuid:
        raise HTTPException(status_code=404, detail="Player was not found")
    now = now_ts()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        row = conn.execute(
            """
            SELECT id,username,username_norm,minecraft_uuid,minecraft_name,enabled
            FROM site_accounts
            WHERE minecraft_uuid=%s AND role='player'
            ORDER BY enabled DESC,last_login_at DESC,created_at DESC
            LIMIT 1
            """,
            (uuid,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=409, detail="У игрока нет привязанного аккаунта сайта")
        current = dict(row)
        username_changed = bool(requested_username and requested_username.lower() != str(current.get("username") or "").lower())
        if username_changed:
            collision = conn.execute(
                "SELECT id FROM site_accounts WHERE username_norm=%s AND id<>%s LIMIT 1",
                (requested_username.lower(), current["id"]),
            ).fetchone()
            if collision:
                raise HTTPException(status_code=409, detail="Такой логин уже занят")
        password_changed = requested_password is not None
        assignments: list[str] = []
        params: list[Any] = []
        if username_changed:
            assignments.extend(["username=%s", "username_norm=%s"])
            params.extend([requested_username, requested_username.lower()])
        if password_changed:
            assignments.append("password_hash=%s")
            params.append(make_password_hash(requested_password or ""))
        assignments.append("updated_at=%s")
        params.extend([now, current["id"]])
        conn.execute(f"UPDATE site_accounts SET {', '.join(assignments)} WHERE id=%s", params)
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PLAYER_SITE_ACCOUNT_UPDATE',%s,'admin-web')",
            (now, actor, f"minecraft_uuid={uuid} username_changed={int(username_changed)} password_changed={int(password_changed)}"),
        )
        conn.commit()
    return {
        "ok": True,
        "minecraftUuid": uuid,
        "minecraftName": uuid_to_name().get(uuid, player),
        "siteAccountId": str(current.get("id") or ""),
        "username": requested_username if username_changed else str(current.get("username") or ""),
        "usernameChanged": username_changed,
        "passwordChanged": password_changed,
    }


def generate_temporary_pin() -> str:
    return "".join(secrets.choice("0123456789") for _ in range(temporary_pin_length()))


def reset_player_bank_pin_sync(player: str, actor: str) -> dict[str, Any]:
    uuid = find_player_uuid(player) or ""
    if not uuid:
        raise HTTPException(status_code=404, detail="Player was not found")
    minecraft_name = uuid_to_name().get(uuid, player)
    temp_pin = generate_temporary_pin()
    pin_hash = make_password_hash(temp_pin)
    now = donation_now_ms()
    expires = now + max(60, TEMP_PIN_TTL_SECONDS)
    reset_id = secrets.token_hex(12)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        site_row = conn.execute(
            """
            SELECT id,username,minecraft_name
            FROM site_accounts
            WHERE minecraft_uuid=%s AND enabled=1
            ORDER BY last_login_at DESC,created_at DESC
            LIMIT 1
            """,
            (uuid,),
        ).fetchone()
        if not site_row:
            raise HTTPException(status_code=409, detail="Player has no linked site account")
        site = dict(site_row)
        conn.execute(
            """
            INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed,must_change,created_at,updated_at)
            VALUES(%s,%s,%s,%s,1,%s,%s)
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                site_account_id=excluded.site_account_id,
                pin_hash=excluded.pin_hash,
                pin_sealed=excluded.pin_sealed,
                must_change=1,
                updated_at=excluded.updated_at
            """,
            (uuid, site["id"], pin_hash, seal_persistent_pin(f"personal:{uuid}", temp_pin), now, now),
        )
        clear_bank_pin_lockout(conn, uuid)
        clear_temporary_pin_resets(conn, uuid, now)
        conn.execute(
            """
            INSERT INTO temporary_pin_resets(id,minecraft_uuid,site_account_id,pin_hash,delivery_blob,expires_at,used_at,created_at)
            VALUES(%s,%s,%s,%s,%s,%s,0,%s)
            """,
            (reset_id, uuid, site["id"], pin_hash, seal_temporary_pin(uuid, temp_pin), expires, now),
        )
        conn.execute(
            "INSERT INTO pin_reset_audit(minecraft_uuid,actor,created_at,details) VALUES(%s,%s,%s,%s)",
            (
                uuid,
                actor,
                now,
                f"site_account_id={site['id']} expires_at={expires}",
            ),
        )
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PIN_RESET',%s,'admin-web')",
            (now, actor, f"minecraft_uuid={uuid} expires_at={expires}"),
        )
        conn.commit()
    return {
        "ok": True,
        "minecraftUuid": uuid,
        "minecraftName": minecraft_name,
        "siteAccountId": str(site["id"] or ""),
        "siteUsername": str(site.get("username") or ""),
        "expiresAt": expires,
        "temporaryPin": temp_pin,
    }


def randomize_player_bank_pin_sync(player: str, actor: str) -> dict[str, Any]:
    uuid = find_player_uuid(player) or ""
    if not uuid:
        raise HTTPException(status_code=404, detail="Player was not found")
    minecraft_name = uuid_to_name().get(uuid, player)
    new_pin = generate_temporary_pin()
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        site_row = conn.execute(
            """
            SELECT id,username,minecraft_uuid,minecraft_name
            FROM site_accounts
            WHERE minecraft_uuid=%s AND enabled=1
            ORDER BY updated_at DESC,created_at DESC
            LIMIT 1
            """,
            (uuid,),
        ).fetchone()
        if not site_row:
            raise HTTPException(status_code=409, detail="У игрока нет активного site account")
        site = dict(site_row)
        conn.execute(
            """
            INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed,must_change,created_at,updated_at)
            VALUES(%s,%s,%s,%s,0,%s,%s)
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                site_account_id=excluded.site_account_id,
                pin_hash=excluded.pin_hash,
                pin_sealed=excluded.pin_sealed,
                must_change=0,
                updated_at=excluded.updated_at
            """,
            (uuid, site["id"], make_password_hash(new_pin), seal_persistent_pin(f"personal:{uuid}", new_pin), now, now),
        )
        clear_bank_pin_lockout(conn, uuid)
        clear_temporary_pin_resets(conn, uuid, now)
        conn.execute(
            "INSERT INTO pin_reset_audit(minecraft_uuid,actor,created_at,details) VALUES(%s,%s,%s,%s)",
            (uuid, actor, now, "randomized_permanent_pin"),
        )
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PIN_RANDOMIZED',%s,'admin-web')",
            (now, actor, f"minecraft_uuid={uuid}"),
        )
        conn.commit()
    return {
        "ok": True,
        "minecraftUuid": uuid,
        "minecraftName": minecraft_name,
        "pin": new_pin,
        "siteAccountId": str(site.get("id") or ""),
        "siteUsername": str(site.get("username") or ""),
    }


def admin_set_player_bank_pin_sync(player: str, actor: str, data: PlayerPinSetIn) -> dict[str, Any]:
    uuid = find_player_uuid(player) or ""
    if not uuid:
        raise HTTPException(status_code=404, detail="Player was not found")
    minecraft_name = uuid_to_name().get(uuid, player)
    new_pin = normalize_pin(data.new_pin)
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        site_row = conn.execute(
            """
            SELECT id,username,minecraft_uuid,minecraft_name
            FROM site_accounts
            WHERE minecraft_uuid=%s
            ORDER BY enabled DESC,updated_at DESC,created_at DESC
            LIMIT 1
            """,
            (uuid,),
        ).fetchone()
        site = dict(site_row) if site_row else {}
        conn.execute(
            """
            INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed,must_change,created_at,updated_at)
            VALUES(%s,%s,%s,%s,0,%s,%s)
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                site_account_id=excluded.site_account_id,
                pin_hash=excluded.pin_hash,
                pin_sealed=excluded.pin_sealed,
                must_change=0,
                updated_at=excluded.updated_at
            """,
            (uuid, site.get("id") or "", make_password_hash(new_pin), seal_persistent_pin(f"personal:{uuid}", new_pin), now, now),
        )
        clear_bank_pin_lockout(conn, uuid)
        clear_temporary_pin_resets(conn, uuid, now)
        conn.execute(
            "INSERT INTO pin_reset_audit(minecraft_uuid,actor,created_at,details) VALUES(%s,%s,%s,%s)",
            (uuid, actor, now, "admin_set_permanent_pin"),
        )
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'PIN_ADMIN_SET',%s,'admin-web')",
            (now, actor, f"minecraft_uuid={uuid}"),
        )
        conn.commit()
    return {
        "ok": True,
        "minecraftUuid": uuid,
        "minecraftName": minecraft_name,
        "pin": new_pin,
        "siteAccountId": str(site.get("id") or ""),
        "siteUsername": str(site.get("username") or ""),
    }


def treasury_bank_profile_sync(account: dict[str, Any]) -> dict[str, Any]:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        if not has_treasury_access(conn, account):
            raise HTTPException(status_code=403, detail="Treasury account is not available")
        treasury = ensure_treasury_bank_account(conn)
        visible_pin = visible_account_pin(conn, TREASURY_ACCOUNT_ID)
        pin_set = bool(conn.execute("SELECT 1 FROM bank_account_pins WHERE account_id=%s", (TREASURY_ACCOUNT_ID,)).fetchone())
        ledger = [dict(x) for x in conn.execute(
            """
            SELECT tx_id,tx_type,amount,balance_after,counterparty_account_id,created_at,details
            FROM cmv4_bank_ledger
            WHERE account_id=%s
            ORDER BY created_at DESC
            LIMIT 120
            """,
            (TREASURY_ACCOUNT_ID,),
        ).fetchall()]
        owner_uuid, owner_name = current_treasury_owner(conn)
        conn.commit()
    return {
        "account": treasury,
        "ownerUuid": owner_uuid,
        "ownerName": owner_name,
        "pin": {"set": pin_set, "visiblePin": visible_pin, "status": "configured"},
        "ledger": ledger,
    }


def admin_treasury_bank_profile_sync(actor: str) -> dict[str, Any]:
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        treasury = ensure_treasury_bank_account(conn)
        visible_pin = visible_account_pin(conn, TREASURY_ACCOUNT_ID)
        pin_set = bool(conn.execute("SELECT 1 FROM bank_account_pins WHERE account_id=%s", (TREASURY_ACCOUNT_ID,)).fetchone())
        owner_uuid, owner_name = current_treasury_owner(conn)
        ledger = [dict(x) for x in conn.execute(
            """
            SELECT tx_id,tx_type,amount,balance_after,counterparty_account_id,created_at,details
            FROM cmv4_bank_ledger
            WHERE account_id=%s
            ORDER BY created_at DESC
            LIMIT 120
            """,
            (TREASURY_ACCOUNT_ID,),
        ).fetchall()]
        conn.commit()
    return {
        "account": treasury,
        "ownerUuid": owner_uuid,
        "ownerName": owner_name,
        "pin": {"set": pin_set, "visiblePin": visible_pin, "status": "configured"},
        "ledger": ledger,
        "actor": actor,
    }


def admin_set_treasury_pin_sync(actor: str, data: PlayerPinSetIn) -> dict[str, Any]:
    new_pin = normalize_pin(data.new_pin)
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        ensure_treasury_bank_account(conn)
        conn.execute(
            """
            INSERT INTO bank_account_pins(account_id,pin_hash,pin_sealed,must_change,created_at,updated_at,updated_by)
            VALUES(%s,%s,%s,0,%s,%s,%s)
            ON CONFLICT(account_id) DO UPDATE SET
                pin_hash=excluded.pin_hash,
                pin_sealed=excluded.pin_sealed,
                must_change=0,
                updated_at=excluded.updated_at,
                updated_by=excluded.updated_by
            """,
            (TREASURY_ACCOUNT_ID, make_password_hash(new_pin), seal_persistent_pin(f"account:{TREASURY_ACCOUNT_ID}", new_pin), now, now, actor),
        )
        clear_account_pin_lockout(conn, TREASURY_ACCOUNT_ID)
        conn.execute(
            "INSERT INTO security_events(time,actor,action,details,source) VALUES(%s,%s,'TREASURY_PIN_ADMIN_SET',%s,'admin-web')",
            (now, actor, TREASURY_ACCOUNT_ID),
        )
        conn.commit()
    return {"ok": True, "accountId": TREASURY_ACCOUNT_ID, "pin": new_pin}


def public_treasury_overview_sync(limit: int = 30) -> dict[str, Any]:
    safe_limit = max(1, min(100, int(limit or 30)))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        treasury = ensure_treasury_bank_account(conn)
        owner_uuid, owner_name = current_treasury_owner(conn)
        rows = conn.execute(
            """
            SELECT tx_id,tx_type,amount,created_at,actor,details
            FROM cmv4_bank_ledger
            WHERE account_id=%s
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (TREASURY_ACCOUNT_ID, safe_limit),
        ).fetchall()
        conn.commit()
    history: list[dict[str, Any]] = []
    for row in rows:
        tx_type = str(row["tx_type"] or "").upper()
        label = {
            "AR_SHOP_PURCHASE": "Доход лавки",
            "AR_ITEM_REPAIR": "Ремонт AR-предмета",
            "PRESIDENT_PAYOUT": "Выплата из казны",
            "TREASURY_WITHDRAW": "Снятие AR из казны",
            "TREASURY_TRANSFER_OUT": "Перевод из казны",
        }.get(tx_type, tx_type or "TREASURY_EVENT")
        history.append({
            "txId": str(row["tx_id"] or ""),
            "type": tx_type,
            "label": label,
            "amount": int(row["amount"] or 0),
            "createdAt": int(row["created_at"] or 0),
            "actor": str(row["actor"] or ""),
        })
    return {
        "accountId": str(treasury.get("account_id") or TREASURY_ACCOUNT_ID),
        "balance": int(treasury.get("balance") or 0),
        "ownerUuid": owner_uuid,
        "ownerName": owner_name,
        "history": history,
    }


def sanitize_public_plain_text(value: Any, max_len: int = 160) -> str:
    text = re.sub(r"[<>{}\r\n\t]+", " ", str(value or ""))
    text = re.sub(r"\s+", " ", text).strip()
    return text[:max_len]


CMS_SECTIONS = {"home", "news", "faq", "rules", "shops", "banners"}
DEFAULT_CMS_ENTRIES: list[dict[str, Any]] = [
    {
        "entry_key": "home_status",
        "section": "home",
        "title": "CopiMine",
        "body": "IP, модпак, банк AR и игровые выборы собраны в одном кабинете.",
        "sort_order": 10,
    },
    {
        "entry_key": "shops_note",
        "section": "shops",
        "title": "Лавки",
        "body": "AR-покупки проходят через банковский PIN. Донат-предметы оплачиваются с отдельного donation-баланса.",
        "sort_order": 20,
    },
    {
        "entry_key": "rules_short",
        "section": "rules",
        "title": "Правила",
        "body": "Играйте честно, не дублируйте предметы, не обходите ограничения банка и выборов.",
        "sort_order": 30,
    },
]


def normalize_cms_key(value: Any) -> str:
    key = re.sub(r"[^a-z0-9_.:-]+", "-", str(value or "").strip().lower())
    key = key.strip("-._:")
    if len(key) < 2:
        raise HTTPException(status_code=400, detail="CMS key is too short")
    return key[:80]


def normalize_cms_section(value: Any) -> str:
    section = re.sub(r"[^a-z0-9_-]+", "-", str(value or "").strip().lower()).strip("-")
    if section not in CMS_SECTIONS:
        raise HTTPException(status_code=400, detail="Неизвестный раздел CMS")
    return section


def sanitize_cms_text(value: Any, max_len: int) -> str:
    text = re.sub(r"[<>{}\x00-\x08\x0b\x0c\x0e-\x1f]+", " ", str(value or ""))
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text.replace("\r\n", "\n").replace("\r", "\n"))
    return text.strip()[:max_len]


def sanitize_cms_asset_path(value: Any) -> str:
    path = str(value or "").strip()
    if not path:
        return ""
    if path.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="CMS image must be a local asset path")
    if not path.startswith("/assets/"):
        raise HTTPException(status_code=400, detail="CMS image must live under /assets/")
    if any(part in path for part in ["..", "\\", "<", ">"]):
        raise HTTPException(status_code=400, detail="CMS image path is invalid")
    return path[:240]


def sanitize_cms_link(value: Any) -> str:
    link = str(value or "").strip()
    if not link:
        return ""
    if link.startswith("/"):
        return link[:240]
    if re.fullmatch(r"https://[A-Za-z0-9.-]+(/[^\s<>]*)?", link):
        return link[:240]
    raise HTTPException(status_code=400, detail="CMS link must be relative or HTTPS")


def seed_site_cms_defaults(conn: Any) -> None:
    now = donation_now_ms()
    for entry in DEFAULT_CMS_ENTRIES:
        conn.execute(
            """
            INSERT INTO site_cms_entries(entry_key,section,title,body,image_path,link_url,sort_order,enabled,updated_at,updated_by)
            VALUES(%s,%s,%s,%s,'','',%s,1,%s,'system')
            ON CONFLICT(entry_key) DO NOTHING
            """,
            (
                normalize_cms_key(entry["entry_key"]),
                normalize_cms_section(entry["section"]),
                sanitize_cms_text(entry["title"], 160),
                sanitize_cms_text(entry["body"], 3000),
                int(entry["sort_order"]),
                now,
            ),
        )


def public_cms_entry(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "key": str(row.get("entry_key") or ""),
        "section": str(row.get("section") or ""),
        "title": sanitize_cms_text(row.get("title") or "", 160),
        "body": sanitize_cms_text(row.get("body") or "", 3000),
        "imagePath": str(row.get("image_path") or ""),
        "linkUrl": str(row.get("link_url") or ""),
        "sortOrder": int(row.get("sort_order") or 0),
        "enabled": bool(int(row.get("enabled") or 0)),
        "updatedAt": int(row.get("updated_at") or 0),
    }


def read_site_cms_sync(include_disabled: bool = False) -> dict[str, Any]:
    if not pg_ready():
        items = [public_cms_entry({**entry, "enabled": 1, "updated_at": 0}) for entry in DEFAULT_CMS_ENTRIES]
        return {"items": items, "sections": sorted(CMS_SECTIONS), "source": "defaults"}
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        seed_site_cms_defaults(conn)
        where = "" if include_disabled else "WHERE enabled=1"
        rows = conn.execute(
            f"""
            SELECT entry_key,section,title,body,image_path,link_url,sort_order,enabled,updated_at,updated_by
            FROM site_cms_entries
            {where}
            ORDER BY section ASC, sort_order ASC, entry_key ASC
            """
        ).fetchall()
        conn.commit()
    items = [public_cms_entry(dict(row)) for row in rows]
    return {"items": items, "sections": sorted(CMS_SECTIONS), "source": "postgresql"}


def upsert_site_cms_entry_sync(data: SiteCmsEntryIn, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    key = normalize_cms_key(data.entry_key)
    section = normalize_cms_section(data.section)
    title = sanitize_cms_text(data.title, 160)
    body = sanitize_cms_text(data.body, 3000)
    image_path = sanitize_cms_asset_path(data.image_path)
    link_url = sanitize_cms_link(data.link_url)
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            """
            INSERT INTO site_cms_entries(entry_key,section,title,body,image_path,link_url,sort_order,enabled,updated_at,updated_by)
            VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON CONFLICT(entry_key) DO UPDATE SET
                section=EXCLUDED.section,
                title=EXCLUDED.title,
                body=EXCLUDED.body,
                image_path=EXCLUDED.image_path,
                link_url=EXCLUDED.link_url,
                sort_order=EXCLUDED.sort_order,
                enabled=EXCLUDED.enabled,
                updated_at=EXCLUDED.updated_at,
                updated_by=EXCLUDED.updated_by
            """,
            (key, section, title, body, image_path, link_url, int(data.sort_order), 1 if data.enabled else 0, now, actor),
        )
        conn.commit()
    audit_event(actor, "cms.entry.save", target=key, details={"section": section, "enabled": data.enabled})
    return {"ok": True, "entry": {"key": key, "section": section, "title": title, "enabled": bool(data.enabled), "updatedAt": now}}


def delete_site_cms_entry_sync(entry_key: str, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    key = normalize_cms_key(entry_key)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("UPDATE site_cms_entries SET enabled=0,updated_at=%s,updated_by=%s WHERE entry_key=%s", (donation_now_ms(), actor, key))
        conn.commit()
    audit_event(actor, "cms.entry.disable", target=key, details={})
    return {"ok": True, "key": key, "enabled": False}


def public_president_budget_payload_sync() -> dict[str, Any]:
    treasury = public_treasury_overview_sync(10)
    return {
        "balance_ar": int(treasury.get("balance") or 0),
        "current_president_name": sanitize_public_plain_text(treasury.get("ownerName") or ""),
        "current_president_uuid": sanitize_public_plain_text(treasury.get("ownerUuid") or "", 64),
        "budget_locked": False,
        "updated_at": donation_now_ms(),
    }


def public_president_budget_history_sync(limit: int = 20, offset: int = 0) -> dict[str, Any]:
    safe_limit = max(1, min(100, int(limit or 20)))
    safe_offset = max(0, int(offset or 0))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            """
            SELECT tx_id,tx_type,amount,created_at,actor,details,counterparty_account_id
            FROM cmv4_bank_ledger
            WHERE account_id=%s
            ORDER BY created_at DESC
            LIMIT %s OFFSET %s
            """,
            (TREASURY_ACCOUNT_ID, safe_limit, safe_offset),
        ).fetchall()
        conn.commit()
    items: list[dict[str, Any]] = []
    for row in rows:
        tx_type = str(row["tx_type"] or "").upper()
        label = {
            "AR_SHOP_PURCHASE": "Доход AR-лавки",
            "AR_ITEM_REPAIR": "Ремонт AR-предмета",
            "PRESIDENT_PAYOUT": "Выплата из казны",
            "TREASURY_WITHDRAW": "Снятие из казны",
            "TREASURY_TRANSFER_OUT": "Перевод из казны",
        }.get(tx_type, tx_type or "TREASURY_EVENT")
        details = sanitize_public_plain_text(row["details"] or "")
        actor_name = sanitize_public_plain_text(row["actor"] or "")
        items.append({
            "type": tx_type or "TREASURY_EVENT",
            "label": label,
            "amount_ar": int(row["amount"] or 0),
            "public_actor_name": actor_name,
            "public_target_name": TREASURY_ACCOUNT_LABEL,
            "item_name": details if tx_type in {"AR_SHOP_PURCHASE", "AR_ITEM_REPAIR"} else "",
            "comment": details if tx_type not in {"AR_SHOP_PURCHASE", "AR_ITEM_REPAIR"} else "",
            "created_at": int(row["created_at"] or 0),
        })
    return {"items": items, "limit": safe_limit, "offset": safe_offset}


def public_president_profile_sync() -> dict[str, Any]:
    payload = public_president_budget_payload_sync()
    return {
        "current_president_name": payload["current_president_name"],
        "current_president_uuid": payload["current_president_uuid"],
        "skin_body_url": f"/api/public/president/skin/body?uuid={quote(payload['current_president_uuid'])}" if payload["current_president_uuid"] else "",
        "updated_at": payload["updated_at"],
    }


def transfer_player_bank_sync(account: dict[str, Any], data: PlayerBankTransferIn) -> dict[str, Any]:
    if not account.get("minecraft_uuid"):
        raise HTTPException(status_code=409, detail="Minecraft account is not linked")
    amount = int(data.amount)
    from_scope = str(data.from_account or "PERSONAL").strip().upper()
    if from_scope not in {"PERSONAL", "TREASURY"}:
        raise HTTPException(status_code=400, detail="Unknown bank account scope")
    safe_key = str(data.idempotency_key or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9-]{8,96}", safe_key):
        raise HTTPException(status_code=400, detail="Некорректный ключ операции")
    now = donation_now_ms()
    tx_id = secrets.token_hex(18)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        transfer_key = f"player-bank-transfer-{safe_key}"
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (transfer_key,))
        if from_scope == "TREASURY":
            if not has_treasury_access(conn, account):
                raise HTTPException(status_code=403, detail="Treasury account is not available")
            verify_account_pin(conn, TREASURY_ACCOUNT_ID, data.pin)
            from_bank = ensure_treasury_bank_account(conn)
        else:
            verify_bank_pin(conn, account, data.pin)
            from_bank = ensure_player_bank_account(conn, str(account.get("minecraft_uuid")), str(account.get("minecraft_name") or ""))
        to_uuid, to_name = resolve_bank_recipient(conn, data.recipient)
        existing = conn.execute(
            "SELECT tx_id,from_account_id,to_account_id,amount,status FROM cmv4_bank_transfers WHERE idempotency_key=%s LIMIT 1",
            (transfer_key,),
        ).fetchone()
        if existing:
            row = dict(existing)
            if int(row.get("amount") or 0) != amount or str(row.get("to_account_id") or "") != f"ar:{to_uuid}" or str(row.get("from_account_id") or "") != str(from_bank.get("account_id") or ""):
                raise HTTPException(status_code=409, detail="Ключ операции уже использован для другого перевода")
            balance_row = conn.execute(
                "SELECT balance_after FROM cmv4_bank_ledger WHERE tx_id=%s LIMIT 1",
                (f"{row.get('tx_id')}:out",),
            ).fetchone()
            return {
                "ok": True,
                "txId": str(row.get("tx_id") or ""),
                "amount": amount,
                "balance": int(row_get(balance_row, "balance_after", 0) or 0),
                "recipient": to_name,
                "fromScope": from_scope,
                "status": str(row.get("status") or "COMMITTED"),
                "idempotent": True,
            }
        if from_scope != "TREASURY" and to_uuid == account.get("minecraft_uuid"):
            raise HTTPException(status_code=400, detail="Cannot transfer to yourself")
        to_bank = ensure_player_bank_account(conn, to_uuid, to_name)
        from_locked, to_locked = lock_bank_accounts_ordered(conn, str(from_bank["account_id"]), str(to_bank["account_id"]))
        if int(from_locked["balance"] or 0) < amount:
            raise HTTPException(status_code=409, detail="Insufficient funds")
        from_after = int(from_locked["balance"] or 0) - amount
        to_after = int(to_locked["balance"] or 0) + amount
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (from_after, now, from_locked["account_id"]))
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (to_after, now, to_locked["account_id"]))
        note = json.dumps({"note": data.note or "", "recipient": to_name, "from_scope": from_scope}, ensure_ascii=False)
        conn.execute(
            "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR','COMMITTED',%s,%s,%s,%s)",
            (tx_id, from_locked["account_id"], to_locked["account_id"], amount, transfer_key, now, account.get("username") or "", note),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'TRANSFER_OUT',%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":out", from_locked["account_id"], to_locked["account_id"], str(from_bank.get("owner_uuid") or account.get("minecraft_uuid") or ""), amount, from_after, now, account.get("username") or "", note),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'TRANSFER_IN',%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":in", to_locked["account_id"], from_locked["account_id"], to_uuid, amount, to_after, now, account.get("username") or "", note),
        )
        conn.commit()
    return {"ok": True, "txId": tx_id, "amount": amount, "balance": from_after, "recipient": to_name, "fromScope": from_scope, "idempotent": False}


def active_president_term(conn: Any) -> dict[str, Any]:
    row = conn.execute(
        """
        SELECT id,election_id,president_uuid,president_name,status,started_at,ends_at
        FROM president_terms
        WHERE status='ACTIVE' AND ends_at>%s
        ORDER BY started_at DESC
        LIMIT 1
        """,
        (donation_now_ms(),),
    ).fetchone()
    return dict(row) if row else {}


def normalize_president_tax_period_hours(hours: int) -> int:
    hours = int(hours or 24)
    if hours in (24, 48, 72):
        return hours
    return 24


def normalize_president_tax_amount(amount: Any) -> int:
    return max(0, min(5, int(amount or 0)))


def active_president_tax(conn: Any, term_id: str = "") -> dict[str, Any]:
    current_term_id = str(term_id or "").strip()
    if not current_term_id:
        term = active_president_term(conn)
        current_term_id = str(term.get("id") or "").strip()
    if not current_term_id:
        return {}
    row = conn.execute(
        """
        SELECT id,term_id,amount,COALESCE(period_hours,24) AS period_hours,status,created_at,created_by
        FROM president_taxes
        WHERE term_id=%s AND status='ACTIVE'
        ORDER BY created_at DESC
        LIMIT 1
        """,
        (current_term_id,),
    ).fetchone()
    return dict(row) if row else {}


def president_tax_window_start(created_at: int, period_hours: int, now_ms: int) -> int:
    base = max(0, int(created_at or 0))
    interval = max(1, normalize_president_tax_period_hours(period_hours)) * 60 * 60 * 1000
    if now_ms <= base:
        return base
    offset = (now_ms - base) // interval
    return base + (offset * interval)


def president_tax_paid_amount(conn: Any, tax_id: str, player_uuid: str, window_start: int, window_end: int) -> int:
    row = conn.execute(
        """
        SELECT COALESCE(SUM(amount),0) AS total
        FROM president_tax_payments
        WHERE tax_id=%s AND player_uuid=%s AND created_at>=%s AND created_at<%s
        """,
        (tax_id, player_uuid, int(window_start or 0), int(window_end or 0)),
    ).fetchone()
    return int(row_get(row, "total", 0) or 0)


def active_president_tax_exemption(conn: Any, player_uuid: str, now_ms: int) -> dict[str, Any]:
    row = conn.execute(
        """
        SELECT id,tax_id,term_id,player_uuid,player_name,artifact_instance_id,source,status,created_at,expires_at,updated_at,details
        FROM president_tax_exemptions
        WHERE player_uuid=%s AND status='ACTIVE' AND expires_at>%s
        ORDER BY expires_at DESC
        LIMIT 1
        """,
        (str(player_uuid or "").strip(), int(now_ms or 0)),
    ).fetchone()
    return dict(row) if row else {}


def player_election_tax_profile_sync(account: dict[str, Any]) -> dict[str, Any]:
    linked = bool(account.get("minecraft_uuid"))
    minecraft_uuid = str(account.get("minecraft_uuid") or "").strip()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        term = active_president_term(conn)
        tax = active_president_tax(conn, str(term.get("id") or ""))
        laws_rows = []
        if term:
            laws_rows = conn.execute(
                """
                SELECT id,text,status,published_at,slot_no
                FROM president_laws
                WHERE term_id=%s AND status='PUBLISHED'
                ORDER BY slot_no ASC, published_at DESC
                LIMIT 6
                """,
                (str(term.get("id") or ""),),
            ).fetchall()
        payments_rows = []
        paid = 0
        due = 0
        tax_payload = None
        tax_exemption = active_president_tax_exemption(conn, minecraft_uuid, donation_now_ms()) if linked else {}
        if linked and tax:
            now_ms = donation_now_ms()
            period_hours = normalize_president_tax_period_hours(int(tax.get("period_hours") or 24))
            window_start = president_tax_window_start(int(tax.get("created_at") or 0), period_hours, now_ms)
            window_end = window_start + (period_hours * 60 * 60 * 1000)
            paid = president_tax_paid_amount(conn, str(tax.get("id") or ""), minecraft_uuid, window_start, window_end)
            amount = normalize_president_tax_amount(tax.get("amount"))
            due = max(0, amount - paid)
            tax_payload = {
                "id": str(tax.get("id") or ""),
                "amount": amount,
                "periodHours": period_hours,
                "windowStart": window_start,
                "windowEnd": window_end,
                "voluntary": True,
            }
            payments_rows = conn.execute(
                """
                SELECT id,amount,source,created_at,details
                FROM president_tax_payments
                WHERE tax_id=%s AND player_uuid=%s
                ORDER BY created_at DESC
                LIMIT 12
                """,
                (str(tax.get("id") or ""), minecraft_uuid),
            ).fetchall()
        if tax_exemption:
            exemption_until = int(tax_exemption.get("expires_at") or 0)
            paid = 0
            due = 0
            if tax_payload is None:
                tax_payload = {
                    "id": str(tax_exemption.get("tax_id") or ""),
                    "amount": 0,
                    "periodHours": 0,
                    "windowStart": int(tax_exemption.get("created_at") or 0),
                    "windowEnd": exemption_until,
                    "voluntary": True,
                }
            tax_payload.update({"exempt": True, "exemptUntil": exemption_until})
            payments_rows = [{
                "id": str(tax_exemption.get("id") or ""),
                "amount": 0,
                "source": "TAX_CLOCK_EXEMPTION",
                "created_at": int(tax_exemption.get("created_at") or 0),
                "expires_at": exemption_until,
                "details": str(tax_exemption.get("details") or ""),
            }] + [dict(row) for row in payments_rows]
    return {
        "linked": linked,
        "disabled": False,
        "president": {
            "uuid": str(term.get("president_uuid") or ""),
            "name": str(term.get("president_name") or ""),
            "termId": str(term.get("id") or ""),
        } if term else {},
        "laws": [dict(row) for row in laws_rows],
        "tax": tax_payload,
        "taxExemption": {
            "active": True,
            "expiresAt": int(tax_exemption.get("expires_at") or 0),
            "source": str(tax_exemption.get("source") or "TAX_CLOCK_EXEMPTION"),
        } if tax_exemption else {"active": False},
        "paid": paid,
        "due": due,
        "payments": [dict(row) for row in payments_rows],
    }


def pay_player_election_tax_sync(account: dict[str, Any], data: PlayerElectionTaxPayIn) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(account.get("minecraft_uuid") or "").strip()
    player_name = str(account.get("minecraft_name") or account.get("username") or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=409, detail="Сначала привяжи Minecraft-ник")
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        term = active_president_term(conn)
        if not term:
            raise HTTPException(status_code=409, detail="Сейчас нет активного президента")
        tax = active_president_tax(conn, str(term.get("id") or ""))
        if not tax:
            raise HTTPException(status_code=409, detail="Активный налог не назначен")
        tax_exemption = active_president_tax_exemption(conn, player_uuid, donation_now_ms())
        if tax_exemption:
            raise HTTPException(status_code=409, detail="Налог отключён часами до " + str(int(tax_exemption.get("expires_at") or 0)))
        verify_bank_pin(conn, account, data.pin)
        bank = ensure_player_bank_account(conn, player_uuid, player_name)
        president_bank = ensure_treasury_bank_account(conn)
        bank_locked, president_locked = lock_bank_accounts_ordered(conn, str(bank["account_id"]), str(president_bank["account_id"]))
        now_ms = donation_now_ms()
        period_hours = normalize_president_tax_period_hours(int(tax.get("period_hours") or 24))
        window_start = president_tax_window_start(int(tax.get("created_at") or 0), period_hours, now_ms)
        window_end = window_start + (period_hours * 60 * 60 * 1000)
        paid = president_tax_paid_amount(conn, str(tax.get("id") or ""), player_uuid, window_start, window_end)
        due = max(0, normalize_president_tax_amount(tax.get("amount")) - paid)
        if due <= 0:
            return {"ok": True, "amount": 0, "due": 0, "paid": paid, "balance": int(bank_locked.get("balance") or 0), "voluntary": True}
        requested_amount = int(data.amount or 0)
        if requested_amount != due:
            raise HTTPException(status_code=400, detail="Налог оплачивается только полной суммой за период")
        amount = due
        before = int(bank_locked.get("balance") or 0)
        if before < amount:
            raise HTTPException(status_code=409, detail="Недостаточно AR на банковском счёте")
        after = before - amount
        president_after = int(president_locked.get("balance") or 0) + amount
        tx_id = f"web-president-tax-{secrets.token_hex(12)}"
        payment_id = f"president-tax-payment-{secrets.token_hex(10)}"
        idem_seed = f"{tax.get('id')}:{player_uuid}:{window_start}:{amount}"
        idem = f"web-president-tax-{sha256_hex(idem_seed)[:32]}"
        details = json.dumps({
            "source": "site",
            "taxId": str(tax.get("id") or ""),
            "periodHours": period_hours,
            "windowStart": window_start,
            "voluntary": True,
        }, ensure_ascii=False)
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (after, now_ms, bank_locked["account_id"]))
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (president_after, now_ms, president_locked["account_id"]))
        conn.execute(
            "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR','COMMITTED',%s,%s,%s,%s)",
            (tx_id, bank_locked["account_id"], president_locked["account_id"], amount, idem, now_ms, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'PRESIDENT_TAX',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":out", bank_locked["account_id"], president_locked["account_id"], player_uuid, amount, after, idem + ":out", now_ms, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'PRESIDENT_TAX',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":in", president_locked["account_id"], bank_locked["account_id"], player_uuid, amount, president_after, idem + ":in", now_ms, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO president_tax_payments(id,tax_id,player_uuid,player_name,amount,source,created_at,details) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (payment_id, str(tax.get("id") or ""), player_uuid, player_name, amount, "SITE_BANK", now_ms, details),
        )
        conn.commit()
    return {
        "ok": True,
        "amount": amount,
        "paid": paid + amount,
        "due": max(0, due - amount),
        "balance": after,
        "periodHours": period_hours,
        "voluntary": True,
    }


def create_link_code_sync(account: dict[str, Any], minecraft_name: str) -> dict[str, Any]:
    if not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Укажи корректный Minecraft-ник")
    code = "".join(secrets.choice("23456789ABCDEFGHJKLMNPQRSTUVWXYZ") for _ in range(8))
    uuid = find_player_uuid(minecraft_name) or offline_uuid_for_name(minecraft_name)
    now = donation_now_ms()
    expires = now + (10 * 60 * 1000)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "UPDATE one_time_link_codes SET status='EXPIRED' WHERE site_account_id=%s AND status='PENDING'",
            (account["id"],),
        )
        conn.execute(
            "INSERT INTO one_time_link_codes(id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)",
            (secrets.token_hex(12), account["id"], minecraft_name, uuid, sha256_hex(code), now, expires),
        )
        conn.commit()
    delivered = False
    if RCON_PASSWORD:
        try:
            rcon_quick(f'tellraw {minecraft_name} {{"text":"Код привязки CopiMine: {code}","color":"gold"}}')
            delivered = True
        except Exception:
            delivered = False
    return {"ok": True, "deliveredInGame": delivered, "expiresAt": expires, "minecraftName": minecraft_name}


def confirm_link_code_sync(account: dict[str, Any], code: str) -> dict[str, Any]:
    now = donation_now_ms()
    code_hash = sha256_hex(str(code).strip().upper())
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            "SELECT * FROM one_time_link_codes WHERE site_account_id=%s AND status='PENDING' AND expires_at>%s ORDER BY created_at DESC LIMIT 8",
            (account["id"], now),
        ).fetchall()
        row = next((candidate for candidate in rows if hmac.compare_digest(str(candidate["code_hash"] or ""), code_hash)), None)
        if not row:
            raise HTTPException(status_code=403, detail="Код неверный или истёк")
        minecraft_name = str(row["minecraft_name"] or "")
        minecraft_uuid = str(row["minecraft_uuid"] or find_player_uuid(minecraft_name) or offline_uuid_for_name(minecraft_name))
        ensure_minecraft_identity_linkable(conn, str(account.get("id") or ""), minecraft_uuid, minecraft_name)
        conn.execute("UPDATE one_time_link_codes SET status='USED',used_at=%s WHERE id=%s", (now, row["id"]))
        conn.execute(
            "UPDATE site_accounts SET minecraft_uuid=%s,minecraft_name=%s,updated_at=%s WHERE id=%s",
            (minecraft_uuid, minecraft_name, now, account["id"]),
        )
        conn.execute(
            "INSERT INTO minecraft_account_links(minecraft_uuid,minecraft_name,site_account_id,status,linked_at,updated_at) VALUES(%s,%s,%s,'ACTIVE',%s,%s) ON CONFLICT(minecraft_uuid) DO UPDATE SET site_account_id=excluded.site_account_id,minecraft_name=excluded.minecraft_name,status='ACTIVE',updated_at=excluded.updated_at",
            (minecraft_uuid, minecraft_name, account["id"], now, now),
        )
        ensure_player_bank_account(conn, minecraft_uuid, minecraft_name)
        conn.execute(
            """
            INSERT INTO auth_users_imported(minecraft_uuid,minecraft_name,imported_at,source)
            VALUES(%s,%s,%s,'site-link')
            ON CONFLICT(minecraft_uuid) DO UPDATE SET
                minecraft_name=excluded.minecraft_name,
                imported_at=excluded.imported_at,
                source=excluded.source
            """,
            (minecraft_uuid, minecraft_name, now),
        )
        conn.commit()
    pg_record_auth_state("last_link_confirmation", {"minecraftUuid": minecraft_uuid, "minecraftName": minecraft_name, "at": now})
    account = dict(account)
    account.update({"minecraft_uuid": minecraft_uuid, "minecraft_name": minecraft_name})
    return {"ok": True, "account": public_player_account(account)}


def create_player_recovery_code_sync(minecraft_name: str) -> dict[str, Any]:
    if not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Укажи корректный Minecraft-ник")
    delivered = False
    now = donation_now_ms()
    expires = now + (10 * 60 * 1000)
    code = "".join(secrets.choice("23456789ABCDEFGHJKLMNPQRSTUVWXYZ") for _ in range(8))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        account = player_account_by_minecraft_name(conn, minecraft_name)
        if not account:
            raise HTTPException(status_code=404, detail="Аккаунт для этого Minecraft-ника не найден")
        conn.execute(
            "UPDATE one_time_link_codes SET status='EXPIRED' WHERE site_account_id=%s AND status='PENDING'",
            (account["id"],),
        )
        minecraft_uuid = str(account.get("minecraft_uuid") or find_player_uuid(minecraft_name) or offline_uuid_for_name(minecraft_name))
        conn.execute(
            "INSERT INTO one_time_link_codes(id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)",
            (secrets.token_hex(12), account["id"], minecraft_name, minecraft_uuid, sha256_hex(code), now, expires),
        )
        conn.commit()
        if RCON_PASSWORD:
            try:
                rcon_quick(f'tellraw {minecraft_name} {{"text":"Код восстановления CopiMine: {code}","color":"gold"}}')
                delivered = True
            except Exception:
                delivered = False
    return {"ok": True, "deliveredInGame": delivered, "expiresAt": expires, "minecraftName": minecraft_name}


def confirm_player_recovery_code_sync(minecraft_name: str, code: str, new_password: str) -> dict[str, Any]:
    if not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Укажи корректный Minecraft-ник")
    ok, reason = password_policy_ok(new_password)
    if not ok:
        raise HTTPException(status_code=400, detail=reason)
    now = donation_now_ms()
    code_hash = sha256_hex(str(code).strip().upper())
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        account = player_account_by_minecraft_name(conn, minecraft_name)
        if not account:
            raise HTTPException(status_code=404, detail="Аккаунт для этого Minecraft-ника не найден")
        rows = conn.execute(
            "SELECT * FROM one_time_link_codes WHERE site_account_id=%s AND status='PENDING' AND expires_at>%s ORDER BY created_at DESC LIMIT 8",
            (account["id"], now),
        ).fetchall()
        row = next((candidate for candidate in rows if hmac.compare_digest(str(candidate["code_hash"] or ""), code_hash)), None)
        if not row:
            raise HTTPException(status_code=403, detail="Неверный или просроченный код восстановления")
        minecraft_uuid = str(account.get("minecraft_uuid") or row["minecraft_uuid"] or find_player_uuid(minecraft_name) or offline_uuid_for_name(minecraft_name))
        updated_at = now_ts()
        conn.execute("UPDATE one_time_link_codes SET status='USED',used_at=%s WHERE id=%s", (now, row["id"]))
        conn.execute(
            "UPDATE site_accounts SET password_hash=%s,minecraft_uuid=%s,minecraft_name=%s,updated_at=%s WHERE id=%s",
            (make_password_hash(new_password), minecraft_uuid, minecraft_name, updated_at, account["id"]),
        )
        conn.execute(
            """
            INSERT INTO whitelist_account_links(minecraft_uuid,minecraft_name,site_account_id,whitelisted,synced_at)
            VALUES(%s,%s,%s,1,%s)
            ON CONFLICT (minecraft_uuid) DO UPDATE SET
                minecraft_name=EXCLUDED.minecraft_name,
                site_account_id=EXCLUDED.site_account_id,
                whitelisted=1,
                synced_at=EXCLUDED.synced_at
            """,
            (minecraft_uuid, minecraft_name, account["id"], updated_at),
        )
        conn.commit()
    account = dict(account)
    account.update({"minecraft_uuid": minecraft_uuid, "minecraft_name": minecraft_name})
    return {"ok": True, "account": public_player_account(account)}


def require_sensitive_confirm(request: Request, label: str = "CONFIRM") -> None:
    got = request.headers.get(SENSITIVE_CONFIRM_HEADER, "")
    accepted = {label, "CONFIRM", "YES", "I_UNDERSTAND"}
    if got not in accepted:
        raise HTTPException(status_code=409, detail=f"Sensitive action requires {SENSITIVE_CONFIRM_HEADER}: {label}")


def require_plugin_key(x_api_key: str = Header(default="")) -> bool:
    if not PLUGIN_API_KEY:
        raise HTTPException(status_code=403, detail="PLUGIN_API_KEY не настроен")
    if not hmac.compare_digest(x_api_key, PLUGIN_API_KEY):
        raise HTTPException(status_code=403, detail="Неверный API-ключ плагина")
    return True


def require_discord_bot_key(x_api_key: str = Header(default="")) -> bool:
    expected = DISCORD_BOT_API_KEY or PLUGIN_API_KEY
    if not expected:
        raise HTTPException(status_code=403, detail="Discord bot API key is not configured")
    if not hmac.compare_digest(x_api_key, expected):
        raise HTTPException(status_code=403, detail="Invalid Discord bot API key")
    return True


def check_discord_rate_limit(request: Request, bucket: str, limit: int = 180, window_seconds: int = 60) -> None:
    host = get_client_ip(request)
    key = f"{bucket}:{host}"
    now = now_ts()
    hits = [ts for ts in DISCORD_RATE_BUCKETS.get(key, []) if ts > now - window_seconds]
    if len(hits) >= limit:
        raise HTTPException(status_code=429, detail="Discord bot API rate limit exceeded")
    hits.append(now)
    DISCORD_RATE_BUCKETS[key] = hits


def check_rate_limit(request: Request, bucket: str, limit: int = 20, window_seconds: int = 60) -> None:
    host = get_client_ip(request)
    key = f"{bucket}:{host}"
    now = now_ts()
    hits = [ts for ts in GENERAL_RATE_BUCKETS.get(key, []) if ts > now - window_seconds]
    if len(hits) >= limit:
        raise HTTPException(status_code=429, detail="Слишком много запросов. Повтори чуть позже.")
    hits.append(now)
    GENERAL_RATE_BUCKETS[key] = hits


async def bg(fn: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
    return await run_in_threadpool(lambda: fn(*args, **kwargs))


@dataclass
class RconClient:
    host: str
    port: int
    password: str
    timeout: float = 4.0

    def _packet(self, request_id: int, packet_type: int, payload: str) -> bytes:
        body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(body)) + body

    def _recv(self, sock: socket.socket) -> tuple[int, int, str]:
        raw_len = sock.recv(4)
        if len(raw_len) < 4:
            raise OSError("RCON connection closed")
        length = struct.unpack("<i", raw_len)[0]
        data = b""
        while len(data) < length:
            part = sock.recv(length - len(data))
            if not part:
                break
            data += part
        if len(data) < 10:
            raise OSError("Bad RCON packet")
        request_id, packet_type = struct.unpack("<ii", data[:8])
        payload = data[8:-2].decode("utf-8", errors="replace")
        return request_id, packet_type, payload

    def command(self, command: str) -> str:
        if not self.password:
            raise HTTPException(status_code=503, detail="RCON_PASSWORD не настроен")
        with socket.create_connection((self.host, self.port), timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            sock.sendall(self._packet(1, 3, self.password))
            request_id, _, _ = self._recv(sock)
            if request_id == -1:
                raise HTTPException(status_code=403, detail="RCON: неверный пароль")
            sock.sendall(self._packet(2, 2, command))
            chunks: list[str] = []
            request_id, _, response = self._recv(sock)
            chunks.append(response)
            # Vanilla/Paper usually returns one packet. Drain quickly if plugin sends more.
            sock.settimeout(0.15)
            try:
                while True:
                    rid, _, part = self._recv(sock)
                    if rid != request_id:
                        break
                    chunks.append(part)
            except Exception:
                pass
            return "".join(chunks).strip()


def rcon_sync(command: str) -> str:
    return RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD).command(command)


async def rcon(command: str) -> str:
    return await bg(rcon_sync, command)


def tcp_online(host: str, port: int, timeout: float = 1.5) -> tuple[bool, Optional[float]]:
    started = time.perf_counter()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True, round((time.perf_counter() - started) * 1000, 1)
    except Exception:
        return False, None


def udp_probe(host: str, port: int, timeout: float = 0.8) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(timeout)
            sock.sendto(b"\x00", (host, port))
            try:
                sock.recvfrom(16)
                return {"reachable": True, "latencyMs": round((time.perf_counter() - started) * 1000, 1), "mode": "response"}
            except socket.timeout:
                return {"reachable": None, "latencyMs": None, "mode": "udp-no-response"}
    except OSError as exc:
        return {"reachable": False, "latencyMs": None, "mode": "error", "error": public_error_message(exc)}


def read_json(path: Path, default: Any) -> Any:
    try:
        if not path.exists():
            return default
        raw = path.read_text(encoding="utf-8", errors="replace")
        if raw.startswith("\ufeff"):
            raw = raw.lstrip("\ufeff")
        return json.loads(raw)
    except Exception:
        return default


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


SECRET_FIELD_RE = re.compile(r"(token|password|secret|api[_-]?key|rcon|session)", re.I)


def redact_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(k): ("<redacted>" if SECRET_FIELD_RE.search(str(k)) else redact_value(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact_value(v) for v in value[:200]]
    if isinstance(value, str):
        text = public_error_message(value)
        if len(text) > 1200:
            return text[:1200] + "..."
        return text
    return value


def append_jsonl(path: Path, item: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(redact_value(item), ensure_ascii=False, separators=(",", ":")) + "\n")


def read_jsonl_tail(path: Path, limit: int = 200) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    limit = max(1, min(int(limit or 200), 2000))
    try:
        with path.open("r", encoding="utf-8", errors="replace") as fh:
            lines = fh.readlines()[-limit:]
    except Exception:
        return []
    rows: list[dict[str, Any]] = []
    for line in lines:
        try:
            item = json.loads(line)
            if isinstance(item, dict):
                rows.append(item)
        except Exception:
            continue
    return rows


def pg_json_dumps(value: Any) -> str:
    return json.dumps(redact_value(value), ensure_ascii=False, sort_keys=True)


def pg_json_loads(value: Any, default: Any) -> Any:
    try:
        loaded = json.loads(str(value or ""))
    except Exception:
        return default
    return loaded if isinstance(loaded, type(default)) else default


def pg_write_key_value(table: str, key: str, value: Any) -> None:
    if not pg_ready():
        return
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            f"INSERT INTO {table}(key,value,updated_at) VALUES(%s,%s,%s) "
            f"ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at",
            (key, pg_json_dumps(value), now_ts()),
        )
        conn.commit()


def pg_read_key_values(table: str) -> dict[str, Any]:
    if not pg_ready():
        return {}
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(f"SELECT key,value FROM {table}").fetchall()
        conn.commit()
    out: dict[str, Any] = {}
    for row in rows:
        raw = str(row["value"] or "")
        try:
            out[str(row["key"])] = json.loads(raw)
        except Exception:
            out[str(row["key"])] = raw
    return out


def pg_log_bridge_event(source: str, event_type: str, details: Optional[dict[str, Any]] = None) -> None:
    if not pg_ready():
        return
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO bridge_events(source,event_type,created_at,details) VALUES(%s,%s,%s,%s)",
            (source or "system", event_type, now_ts(), pg_json_dumps(details or {})),
        )
        conn.commit()


def pg_record_auth_state(key: str, value: Any) -> None:
    if not key or not pg_ready():
        return
    pg_write_key_value("auth_migration_state", key, value)


def sync_auth_whitelist_state() -> None:
    if not pg_ready():
        return
    whitelist_raw = read_json(MC_SERVER_DIR / "whitelist.json", [])
    whitelist = whitelist_raw if isinstance(whitelist_raw, list) else []
    now = now_ts()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        for item in whitelist:
            if not isinstance(item, dict):
                continue
            uuid = str(item.get("uuid") or "").strip()
            name = str(item.get("name") or "").strip()
            if not uuid and not name:
                continue
            conn.execute(
                """
                INSERT INTO auth_whitelist_sync(minecraft_uuid,minecraft_name,synced_at,status)
                VALUES(%s,%s,%s,'present')
                ON CONFLICT(minecraft_uuid) DO UPDATE SET
                    minecraft_name=excluded.minecraft_name,
                    synced_at=excluded.synced_at,
                    status=excluded.status
                """,
                (uuid or name.lower(), name, now),
            )
            if uuid:
                linked = conn.execute(
                    "SELECT id FROM site_accounts WHERE minecraft_uuid=%s ORDER BY enabled DESC,last_login_at DESC LIMIT 1",
                    (uuid,),
                ).fetchone()
                if linked:
                    conn.execute(
                        """
                        INSERT INTO whitelist_account_links(minecraft_uuid,minecraft_name,site_account_id,whitelisted,synced_at)
                        VALUES(%s,%s,%s,1,%s)
                        ON CONFLICT(minecraft_uuid) DO UPDATE SET
                            minecraft_name=excluded.minecraft_name,
                            site_account_id=excluded.site_account_id,
                            whitelisted=1,
                            synced_at=excluded.synced_at
                        """,
                        (uuid, name, str(linked["id"]), now),
                    )
        conn.commit()
    pg_record_auth_state("whitelist_sync_last_run", {"at": now, "count": len(whitelist)})


def log_auth_login_check(minecraft_uuid: str, minecraft_name: str, ok: bool, details: dict[str, Any]) -> None:
    if not pg_ready():
        return
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO auth_login_checks(minecraft_uuid,minecraft_name,checked_at,ok,details) VALUES(%s,%s,%s,%s,%s)",
            (minecraft_uuid, minecraft_name, now_ts(), 1 if ok else 0, pg_json_dumps(details)),
        )
        conn.commit()


def read_site_audit_rows(limit: int = 200, action: str = "", actor: str = "", target: str = "") -> list[dict[str, Any]]:
    if not pg_ready():
        rows = read_jsonl_tail(AUDIT_LOG_FILE, limit)
        if target:
            wanted = target.lower()
            rows = [row for row in rows if wanted in str(row.get("target") or "").lower() or wanted in json.dumps(row.get("details") or {}, ensure_ascii=False).lower()]
        return rows
    sql = "SELECT actor,action,created_at,details FROM site_audit"
    where: list[str] = []
    args: list[Any] = []
    if action:
        where.append("action ILIKE %s")
        args.append(f"%{action}%")
    if actor:
        where.append("actor ILIKE %s")
        args.append(f"%{actor}%")
    if target:
        where.append("details::text ILIKE %s")
        args.append(f"%{target}%")
    if where:
        sql += " WHERE " + " AND ".join(where)
    sql += " ORDER BY created_at DESC LIMIT %s"
    args.append(max(1, min(limit, 2000)))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(sql, tuple(args)).fetchall()
        conn.commit()
    result = []
    for row in rows:
        details_obj = pg_json_loads(row["details"], {})
        result.append(
            {
                "timestamp": int(row["created_at"] or 0),
                "actor": row["actor"],
                "action": row["action"],
                "target": str(details_obj.get("target") or ""),
                "status": str(details_obj.get("status") or "ok"),
                "details": details_obj.get("details") if isinstance(details_obj, dict) else {},
            }
        )
    return result


def read_plugin_event_rows(limit: int = 250, source: str = "", event_type: str = "") -> list[dict[str, Any]]:
    if not pg_ready():
        return read_jsonl_tail(EVENT_LOG_FILE, limit)
    sql = "SELECT id,source,event_type,actor,target,created_at,details FROM plugin_events"
    where: list[str] = []
    args: list[Any] = []
    if source:
        where.append("source ILIKE %s")
        args.append(f"%{source}%")
    if event_type:
        where.append("event_type ILIKE %s")
        args.append(f"%{event_type}%")
    if where:
        sql += " WHERE " + " AND ".join(where)
    sql += " ORDER BY id DESC LIMIT %s"
    args.append(max(1, min(limit, 1000)))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(sql, tuple(args)).fetchall()
        conn.commit()
    result = []
    for row in rows:
        metadata = pg_json_loads(row["details"], {})
        result.append(
            {
                "id": str(row["id"]),
                "timestamp": int(row["created_at"] or 0),
                "source": row["source"],
                "eventType": row["event_type"],
                "actor": row["actor"],
                "target": row["target"],
                "severity": str(metadata.get("severity") or "info"),
                "tags": metadata.get("tags") if isinstance(metadata, dict) else [],
                "metadata": metadata.get("metadata") if isinstance(metadata, dict) else {},
                **({k: v for k, v in metadata.items() if k not in {"severity", "tags", "metadata"}} if isinstance(metadata, dict) else {}),
            }
        )
    return result


def audit_event(actor: str, action: str, target: str = "", status: str = "ok", details: Optional[dict[str, Any]] = None) -> None:
    item = {
        "timestamp": now_ts(),
        "actor": actor or "system",
        "action": action,
        "target": target,
        "status": status,
        "details": details or {},
    }
    append_jsonl(AUDIT_LOG_FILE, item)
    if pg_ready():
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            conn.execute(
                "INSERT INTO site_audit(actor,action,created_at,details) VALUES(%s,%s,%s,%s)",
                (item["actor"], action, item["timestamp"], pg_json_dumps({"target": target, "status": status, "details": item["details"]})),
            )
            if actor and actor != "system":
                conn.execute(
                    "INSERT INTO admin_actions(actor,action,target,created_at,details) VALUES(%s,%s,%s,%s,%s)",
                    (item["actor"], action, target, item["timestamp"], pg_json_dumps(item["details"])),
                )
            conn.commit()


def append_panel_event(source: str, event_type: str, actor: str = "", target: str = "", metadata: Optional[dict[str, Any]] = None, severity: str = "info", tags: Optional[list[str]] = None, **extra: Any) -> dict[str, Any]:
    item = {
        "id": secrets.token_hex(8),
        "timestamp": int(extra.pop("timestamp", 0) or now_ts()),
        "source": source or "panel",
        "eventType": event_type,
        "actor": actor,
        "target": target,
        "severity": severity or "info",
        "tags": tags or [],
        "metadata": metadata or {},
    }
    item.update({k: v for k, v in extra.items() if v is not None})
    append_jsonl(EVENT_LOG_FILE, item)
    if pg_ready():
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            conn.execute(
                "INSERT INTO plugin_events(source,event_type,actor,target,created_at,details) VALUES(%s,%s,%s,%s,%s,%s)",
                (
                    item["source"],
                    event_type,
                    actor,
                    target,
                    int(item["timestamp"] or now_ts()),
                    pg_json_dumps(
                        {
                            "severity": severity or "info",
                            "tags": tags or [],
                            "metadata": metadata or {},
                            **{k: v for k, v in extra.items() if v is not None},
                        }
                    ),
                ),
            )
            conn.commit()
    return item


def safe_location(path: Path | str) -> dict[str, Any]:
    p = Path(path)
    return {"name": p.name, "exists": p.exists(), "size": p.stat().st_size if p.exists() else 0, "modified": int(p.stat().st_mtime) if p.exists() else None}


def usercache() -> list[dict[str, Any]]:
    data = read_json(MC_SERVER_DIR / "usercache.json", [])
    return data if isinstance(data, list) else []


def uuid_to_name() -> dict[str, str]:
    mapping: dict[str, str] = {}
    for row in usercache():
        uuid = str(row.get("uuid", ""))
        name = str(row.get("name", ""))
        if uuid and name:
            mapping[uuid] = name
    return mapping


def name_to_uuid() -> dict[str, str]:
    return {name.lower(): uuid for uuid, name in uuid_to_name().items()}


def find_player_uuid(name_or_uuid: str) -> Optional[str]:
    q = name_or_uuid.lower()
    if q in name_to_uuid():
        return name_to_uuid()[q]
    for row in usercache():
        uuid = str(row.get("uuid", ""))
        if q == uuid.lower():
            return uuid
    return name_or_uuid if (WORLD_DIR / "playerdata" / f"{name_or_uuid}.dat").exists() else None


def nbt_to_plain(value: Any) -> Any:
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    if hasattr(value, "unpack"):
        try:
            return nbt_to_plain(value.unpack())
        except Exception:
            return str(value)
    if isinstance(value, dict):
        return {str(k): nbt_to_plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [nbt_to_plain(x) for x in value]
    return str(value)


def load_player_nbt(uuid: str) -> Optional[dict[str, Any]]:
    if not nbtlib:
        return None
    path = WORLD_DIR / "playerdata" / f"{uuid}.dat"
    if not path.exists():
        return None
    try:
        data = nbtlib.load(str(path))
        plain = nbt_to_plain(data)
        return plain if isinstance(plain, dict) else None
    except Exception:
        return None


def stats_for_uuid(uuid: str) -> dict[str, Any]:
    data = read_json(WORLD_DIR / "stats" / f"{uuid}.json", {})
    return data if isinstance(data, dict) else {}


def advancement_summary(uuid: str) -> dict[str, Any]:
    data = read_json(WORLD_DIR / "advancements" / f"{uuid}.json", {})
    if not isinstance(data, dict):
        return {"done": 0, "total": 0}
    total = len(data)
    done = 0
    for v in data.values():
        if isinstance(v, dict) and v.get("done"):
            done += 1
    return {"done": done, "total": total}


def normalize_item_id(raw: Any) -> str:
    item = str(raw or "").strip()
    if not item:
        return ""
    if ":" not in item and item.isupper():
        return "minecraft:" + item.lower()
    return item.lower()


def stack_count(item: dict[str, Any]) -> int:
    val = item.get("Count", item.get("count", 0))
    try:
        return int(val)
    except Exception:
        return 0


def count_items_in_list(items: list[Any]) -> dict[str, int]:
    result: dict[str, int] = {}
    for item in items:
        if not isinstance(item, dict):
            continue
        item_id = normalize_item_id(item.get("id", item.get("Id", "")))
        count = stack_count(item)
        if item_id and count:
            result[item_id] = result.get(item_id, 0) + count
    return result


def count_target_items_recursive(value: Any, targets: set[str]) -> int:
    total = 0
    if isinstance(value, dict):
        item_id = normalize_item_id(value.get("id", value.get("Id", "")))
        if item_id in targets:
            total += stack_count(value)
        for child in value.values():
            total += count_target_items_recursive(child, targets)
    elif isinstance(value, list):
        for child in value:
            total += count_target_items_recursive(child, targets)
    return total


def ar_targets() -> set[str]:
    result = set()
    for item in AR_ITEM_IDS:
        result.add(normalize_item_id(item))
        if item.isupper():
            result.add("minecraft:" + item.lower())
    return {x for x in result if x}


def inventory_summary(nbt: dict[str, Any]) -> dict[str, Any]:
    inv = nbt.get("Inventory", []) or []
    ender = nbt.get("EnderItems", []) or []
    if not isinstance(inv, list):
        inv = []
    if not isinstance(ender, list):
        ender = []
    targets = ar_targets()
    return {
        "inventory": inv,
        "enderChest": ender,
        "inventoryCounts": count_items_in_list(inv),
        "enderCounts": count_items_in_list(ender),
        "arInInventory": count_target_items_recursive(inv, targets),
        "arInEnderChest": count_target_items_recursive(ender, targets),
    }


def stat_value(stats: dict[str, Any], key: str) -> Any:
    return stats.get("stats", {}).get("minecraft:custom", {}).get(key)


def list_players_sync(q: str = "") -> dict[str, Any]:
    cached = usercache()
    names = uuid_to_name()
    result: list[dict[str, Any]] = []
    stat_dir = WORLD_DIR / "stats"
    pdata_dir = WORLD_DIR / "playerdata"
    uuids = {str(x.get("uuid")) for x in cached if x.get("uuid")}
    if pdata_dir.exists():
        uuids |= {p.stem for p in pdata_dir.glob("*.dat")}
    banned_players = {str(x.get("name", "")).lower(): x for x in read_json(MC_SERVER_DIR / "banned-players.json", []) if isinstance(x, dict)}
    ops = {str(x.get("uuid", "")): x for x in read_json(MC_SERVER_DIR / "ops.json", []) if isinstance(x, dict)}
    whitelist = {str(x.get("uuid", "")): x for x in read_json(MC_SERVER_DIR / "whitelist.json", []) if isinstance(x, dict)}
    for uuid in sorted(uuids):
        name = names.get(uuid, uuid)
        if q and q.lower() not in name.lower() and q.lower() not in uuid.lower():
            continue
        stat_path = stat_dir / f"{uuid}.json"
        dat_path = pdata_dir / f"{uuid}.dat"
        stats = stats_for_uuid(uuid) if stat_path.exists() else {}
        result.append({
            "uuid": uuid,
            "name": name,
            "op": uuid in ops,
            "whitelisted": uuid in whitelist,
            "banned": name.lower() in banned_players,
            "hasStats": stat_path.exists(),
            "hasPlayerData": dat_path.exists(),
            "playerDataModified": int(dat_path.stat().st_mtime) if dat_path.exists() else None,
            "statsModified": int(stat_path.stat().st_mtime) if stat_path.exists() else None,
            "playTimeTicks": stat_value(stats, "minecraft:play_time"),
            "deaths": stat_value(stats, "minecraft:deaths"),
        })
    return {"players": result, "count": len(result)}


def open_sqlite_readonly(path: str) -> sqlite3.Connection:
    if admin_plugin_db_requested(path) and pg_ready():
        return PgCompatConnection(readonly=True)  # type: ignore[return-value]
    db_path = Path(path)
    if not db_path.exists():
        raise HTTPException(status_code=404, detail=f"БД не найдена: {db_path}")
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=4)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=5000")
    conn.execute("PRAGMA query_only=ON")
    return conn


def open_sqlite_write(path: str) -> sqlite3.Connection:
    if admin_plugin_db_requested(path) and pg_ready():
        return PgCompatConnection(readonly=False)  # type: ignore[return-value]
    db_path = Path(path)
    if not db_path.exists():
        raise HTTPException(status_code=404, detail=f"БД не найдена: {db_path}")
    conn = sqlite3.connect(str(db_path), timeout=8)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=8000")
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA temp_store=MEMORY")
    return conn


def quote_ident(name: str) -> str:
    return '"' + name.replace('"', '""') + '"'


def tables(conn: sqlite3.Connection) -> list[str]:
    if is_pg_conn(conn):
        rows = conn.execute(
            """
            SELECT table_name AS name
            FROM information_schema.tables
            WHERE table_schema=? AND table_type IN ('BASE TABLE','VIEW')
            ORDER BY table_name
            """,
            [POSTGRES_SCHEMA],
        ).fetchall()
        return [str(r["name"]) for r in rows]
    return [r[0] for r in conn.execute("select name from sqlite_master where type='table' order by name").fetchall()]


def table_columns(conn: sqlite3.Connection, table: str) -> list[str]:
    if is_pg_conn(conn):
        rows = conn.execute(
            """
            SELECT column_name AS name
            FROM information_schema.columns
            WHERE table_schema=? AND table_name=?
            ORDER BY ordinal_position
            """,
            [POSTGRES_SCHEMA, table],
        ).fetchall()
        return [str(r["name"]) for r in rows]
    return [r[1] for r in conn.execute(f"PRAGMA table_info({quote_ident(table)})").fetchall()]


def rows_to_dicts(rows: list[sqlite3.Row]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for row in rows:
        if isinstance(row, dict):
            out.append(dict(row))
        else:
            out.append({k: row[k] for k in row.keys()})
    return out


def admin_plugin_db_path() -> Path:
    candidates = [
        Path(ADMIN_PLUGIN_DB),
        MC_SERVER_DIR / "plugins" / "CopiMineUltimateAdmin" / "copimine_ultimate.db",
        MC_SERVER_DIR / "plugins" / "CopiMineUltimateAdmin" / "data.db",
        MC_SERVER_DIR / "plugins" / "CopiMineUltimateAdminPlus" / "copimine_ultimate.db",
    ]
    for path in candidates:
        if path.exists():
            return path
    return Path(ADMIN_PLUGIN_DB)


def sqlite_has_table(conn: sqlite3.Connection, table: str) -> bool:
    if is_pg_conn(conn):
        row = conn.execute(
            """
            SELECT count(*) AS c
            FROM information_schema.tables
            WHERE table_schema=? AND table_name=? AND table_type IN ('BASE TABLE','VIEW')
            """,
            [POSTGRES_SCHEMA, table],
        ).fetchone()
        return bool(row and int(row["c"] or 0) > 0)
    row = conn.execute("select count(*) as c from sqlite_master where type in ('table','view') and name=?", [table]).fetchone()
    return bool(row and int(row["c"] or 0) > 0)


def safe_sqlite_rows(
    conn: sqlite3.Connection,
    table: str,
    where: str = "",
    params: Optional[list[Any]] = None,
    order: str = "",
    limit: int = 200,
) -> list[dict[str, Any]]:
    if not sqlite_has_table(conn, table):
        return []
    limit = max(1, min(int(limit or 200), 2000))
    sql = f"select * from {quote_ident(table)}"
    if where:
        sql += " where " + (pg_compat_sql(where) if is_pg_conn(conn) else where)
    if order:
        sql += " order by " + (pg_compat_sql(order) if is_pg_conn(conn) else order)
    sql += " limit ?"
    return rows_to_dicts(conn.execute(sql, (params or []) + [limit]).fetchall())


def plugin_player_selector(conn: sqlite3.Connection, table: str, player: str) -> tuple[str, list[Any]]:
    cols = table_columns(conn, table)
    uuid = find_player_uuid(player) or ""
    names = [player]
    if uuid:
        names.append(uuid_to_name().get(uuid, player))
    names = sorted({x for x in names if x})
    where: list[str] = []
    params: list[Any] = []
    uuid_cols = [c for c in ["player_uuid", "uuid", VOTER_UUID_COL, "applicant_uuid", "target_uuid", "actor_uuid"] if c in cols]
    name_cols = [c for c in ["player_name", "name", VOTER_NAME_COL, "applicant_name", "target_name", "actor_name"] if c in cols]
    if uuid and uuid_cols:
        where.append("(" + " or ".join(f"{quote_ident(c)}=?" for c in uuid_cols) + ")")
        params.extend([uuid] * len(uuid_cols))
    if name_cols and names:
        where.append("(" + " or ".join(f"lower({quote_ident(c)})=lower(?)" for c in name_cols for _ in names) + ")")
        for _ in name_cols:
            params.extend(names)
    if not where:
        return "1=0", []
    return "(" + " or ".join(where) + ")", params


def parse_plugin_inventory_json(raw: Any) -> list[dict[str, Any]]:
    if not raw:
        return []
    try:
        data = json.loads(str(raw))
    except Exception:
        return []
    if not isinstance(data, list):
        return []
    rows = []
    for item in data:
        if not isinstance(item, dict):
            continue
        rows.append({
            "slot": item.get("slot"),
            "id": normalize_item_id(str(item.get("type") or "")),
            "count": int(item.get("amount") or 0),
            "name": item.get("name") or "",
            "ar": int(item.get("ar") or 0),
        })
    return rows


def plugin_inventory_live_sync(player: str, limit: int = 20) -> dict[str, Any]:
    path = admin_plugin_db_path()
    if not admin_plugin_db_available(path):
        return {"player": player, "source": admin_plugin_db_location(path), "onlineSnapshots": [], "latest": None}
    with open_sqlite_readonly(str(path)) as conn:
        if not sqlite_has_table(conn, "cmv7_inventory_snapshots"):
            return {"player": player, "source": admin_plugin_db_location(path), "onlineSnapshots": [], "latest": None}
        where, params = plugin_player_selector(conn, "cmv7_inventory_snapshots", player)
        rows = safe_sqlite_rows(conn, "cmv7_inventory_snapshots", where, params, "time desc,id desc", limit)
    snapshots = []
    for row in rows:
        snapshots.append({
            "id": row.get("id"),
            "createdAt": row.get("time"),
            "uuid": row.get("player_uuid"),
            "name": row.get("player_name"),
            "source": row.get("source"),
            "inventory": parse_plugin_inventory_json(row.get("inventory_json")),
            "enderChest": parse_plugin_inventory_json(row.get("ender_json")),
            "arInInventory": row.get("ar_inventory") or 0,
            "arInEnderChest": row.get("ar_ender") or 0,
            "health": row.get("health"),
            "food": row.get("food"),
            "world": row.get("world"),
            "x": row.get("x"),
            "y": row.get("y"),
            "z": row.get("z"),
        })
    return {"player": player, "source": admin_plugin_db_location(path), "onlineSnapshots": snapshots, "latest": snapshots[0] if snapshots else None}


def plugin_player_activity_sync(player: str, limit: int = 220) -> dict[str, Any]:
    path = admin_plugin_db_path()
    if not admin_plugin_db_available(path):
        return {"player": player, "source": admin_plugin_db_location(path), "rows": []}
    rows: list[dict[str, Any]] = []
    with open_sqlite_readonly(str(path)) as conn:
        if sqlite_has_table(conn, "cmv7_player_activity"):
            where, params = plugin_player_selector(conn, "cmv7_player_activity", player)
            for row in safe_sqlite_rows(conn, "cmv7_player_activity", where, params, "time desc,id desc", limit):
                rows.append({
                    "time": row.get("time"),
                    "source": "AdminPlus",
                    "type": row.get("type"),
                    "player": row.get("player_name"),
                    "world": row.get("world"),
                    "x": row.get("x"),
                    "y": row.get("y"),
                    "z": row.get("z"),
                    "details": row.get("details"),
                    "adminOnly": bool(row.get("admin_only")),
                })
        if sqlite_has_table(conn, "cmv7_player_checks"):
            where, params = plugin_player_selector(conn, "cmv7_player_checks", player)
            for row in safe_sqlite_rows(conn, "cmv7_player_checks", where, params, "time desc,id desc", limit):
                rows.append({
                    "time": row.get("time"),
                    "source": "Проверки",
                    "type": "CHECK_" + str(row.get("action") or ""),
                    "player": row.get("player_name"),
                    "actor": row.get("admin_name"),
                    "details": row.get("details"),
                    "adminOnly": True,
                })
        if sqlite_has_table(conn, "cmv7_ar_events"):
            where, params = plugin_player_selector(conn, "cmv7_ar_events", player)
            for row in safe_sqlite_rows(conn, "cmv7_ar_events", where, params, "time desc,id desc", limit):
                rows.append({
                    "time": row.get("time"),
                    "source": "АР",
                    "type": row.get("type"),
                    "player": row.get("actor_name") or row.get("target_name"),
                    "world": row.get("world"),
                    "x": row.get("x"),
                    "y": row.get("y"),
                    "z": row.get("z"),
                    "details": row.get("details"),
                    "amount": row.get("amount"),
                    "material": row.get("material"),
                    "adminOnly": True,
                })
    rows.sort(key=lambda r: int(r.get("time") or 0), reverse=True)
    return {"player": player, "source": admin_plugin_db_location(path), "rows": rows[: max(1, min(limit, 500))]}


def sanitize_election_row_public_admin(row: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    source = dict(row or {})
    return {
        "id": source.get("id"),
        "status": source.get("status"),
        "current_stage": source.get("current_stage"),
        "current_round": source.get("current_round"),
        "president_uuid": source.get("president_uuid"),
        "president_name": source.get("president_name"),
        "manual_winner_uuid": source.get("manual_winner_uuid"),
        "started_at": source.get("started_at"),
        "updated_at": source.get("updated_at"),
    }


def sanitize_ballot_admin_row(row: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    source = dict(row or {})
    return {
        "id": source.get("id"),
        "election_id": source.get("election_id"),
        "round_no": source.get("round_no"),
        "player_name": source.get("player_name"),
        "station_id": source.get("station_id"),
        "status": source.get("status"),
        "issued_at": source.get("issued_at"),
        "submitted_at": source.get("submitted_at"),
        "submitted_station_id": source.get("submitted_station_id"),
        "annulled_at": source.get("annulled_at"),
        "annulled_by": source.get("annulled_by"),
        "annul_reason": source.get("annul_reason"),
    }


def sanitize_application_admin_row(row: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    source = dict(row or {})
    return {
        "id": source.get("id"),
        "election_id": source.get("election_id"),
        "player_name": source.get("player_name"),
        "station_id": source.get("station_id"),
        "status": source.get("status"),
        "chair_recommendation": source.get("chair_recommendation"),
        "chair_note": source.get("chair_note"),
        "admin_status": source.get("admin_status"),
        "admin_note": source.get("admin_note"),
        "submitted_at": source.get("submitted_at"),
        "reviewed_at": source.get("reviewed_at"),
        "reviewed_by": source.get("reviewed_by"),
    }


def election_detail_sync(limit: int = 500) -> dict[str, Any]:
    if pg_ready():
        try:
            with auth_conn() as conn:
                ensure_v4_schema(conn)
                election = {}
                if pg_table_exists(conn, "elections"):
                    row = conn.execute(
                        """
                        SELECT *
                        FROM elections
                        ORDER BY CASE WHEN upper(coalesce(status,'')) IN ('ACTIVE','PAUSED','DRAFT','APPLICATIONS_OPEN','VOTING_OPEN','COUNTING') THEN 0 ELSE 1 END,
                                 coalesce(started_at,0) DESC
                        LIMIT 1
                        """
                    ).fetchone()
                    election = dict(row) if row else {}
                eid = str(election.get("id") or "")
                candidates = [dict(r) for r in conn.execute("SELECT * FROM candidates WHERE election_id=%s AND active=1 ORDER BY last_result DESC,created_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "candidates") else ([dict(r) for r in conn.execute("SELECT * FROM election_candidates WHERE election_id=%s ORDER BY created_at DESC,id DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "election_candidates") else [])
                results = [dict(r) for r in conn.execute(
                    "SELECT candidate_uuid, max(candidate_name) AS name, count(*) AS votes "
                    "FROM votes WHERE election_id=%s GROUP BY candidate_uuid ORDER BY votes DESC LIMIT %s",
                    (eid, limit)
                ).fetchall()] if eid and pg_table_exists(conn, "votes") else []
                turnout = {
                    "issued_ballots": int(conn.execute("SELECT count(*) FROM ballots WHERE election_id=%s", (eid,)).fetchone()[0]) if eid and pg_table_exists(conn, "ballots") else 0,
                    "confirmed_ballots": int(conn.execute("SELECT count(*) FROM ballots WHERE election_id=%s AND status='CONFIRMED'", (eid,)).fetchone()[0]) if eid and pg_table_exists(conn, "ballots") else 0,
                    "deposited_ballots": int(conn.execute("SELECT count(*) FROM ballots WHERE election_id=%s AND status='DEPOSITED'", (eid,)).fetchone()[0]) if eid and pg_table_exists(conn, "ballots") else 0,
                }
                ballots = [dict(r) for r in conn.execute("SELECT * FROM ballots WHERE election_id=%s ORDER BY issued_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "ballots") else ([dict(r) for r in conn.execute("SELECT * FROM election_ballots WHERE election_id=%s ORDER BY issued_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "election_ballots") else [])
                applications = [dict(r) for r in conn.execute("SELECT * FROM candidate_applications WHERE election_id=%s ORDER BY submitted_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "candidate_applications") else ([dict(r) for r in conn.execute("SELECT * FROM election_applications WHERE election_id=%s ORDER BY submitted_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "election_applications") else [])
                curators = [dict(r) for r in conn.execute("SELECT * FROM cik_chairs WHERE active=1 ORDER BY assigned_at DESC LIMIT %s", (limit,)).fetchall()] if pg_table_exists(conn, "cik_chairs") else []
                polling_stations = [dict(r) for r in conn.execute("SELECT * FROM polling_stations WHERE election_id=%s AND active=1 ORDER BY created_at DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "polling_stations") else ([dict(r) for r in conn.execute("SELECT * FROM election_stations WHERE election_id=%s ORDER BY active DESC,id DESC LIMIT %s", (eid, limit)).fetchall()] if eid and pg_table_exists(conn, "election_stations") else [])
                president_rows = [dict(r) for r in conn.execute("SELECT * FROM president_terms WHERE status='ACTIVE' ORDER BY started_at DESC LIMIT 1").fetchall()] if pg_table_exists(conn, "president_terms") else [dict(r) for r in conn.execute("SELECT * FROM election_presidents ORDER BY active DESC,assigned_at DESC,id DESC LIMIT 1").fetchall()]
                active_term_id = str((president_rows[0] or {}).get("id") or "") if president_rows else ""
                laws = [dict(r) for r in conn.execute("SELECT * FROM president_laws WHERE term_id=%s AND status='PUBLISHED' ORDER BY slot_no ASC,published_at DESC LIMIT %s", (active_term_id, limit)).fetchall()] if active_term_id and pg_table_exists(conn, "president_laws") else []
                pending_laws = [dict(r) for r in conn.execute("SELECT * FROM president_laws WHERE term_id=%s AND status='PENDING' ORDER BY created_at DESC LIMIT %s", (active_term_id, limit)).fetchall()] if active_term_id and pg_table_exists(conn, "president_laws") else []
                decrees = [dict(r) for r in conn.execute("SELECT * FROM election_decrees ORDER BY created_at DESC LIMIT %s", (limit,)).fetchall()]
                petitions = [dict(r) for r in conn.execute("SELECT * FROM election_petitions ORDER BY created_at DESC LIMIT %s", (limit,)).fetchall()]
                audit = [dict(r) for r in conn.execute("SELECT actor,action,created_at,details FROM admin_actions WHERE action ILIKE 'election.%' ORDER BY created_at DESC LIMIT %s", (limit,)).fetchall()] if pg_table_exists(conn, "admin_actions") else []
                safe_election = sanitize_election_row_public_admin(election)
                safe_president = sanitize_election_row_public_admin(president_rows[0]) if president_rows else {}
                safe_ballots = [sanitize_ballot_admin_row(row) for row in ballots]
                safe_applications = [sanitize_application_admin_row(row) for row in applications]
                conn.commit()
            total_votes = sum(int(row.get("votes") or 0) for row in results)
            safe_station_rows = [{
                "id": row.get("id"),
                "world": row.get("world"),
                "x": row.get("x"),
                "y": row.get("y"),
                "z": row.get("z"),
                "active": row.get("active"),
                "applications_issued": row.get("applications_issued"),
                "ballots_issued": row.get("ballots_issued"),
                "ballots_submitted": row.get("ballots_submitted"),
                "ballots_annulled": row.get("ballots_annulled"),
            } for row in polling_stations]
            return {
                "source": {"type": "postgresql", "schema": POSTGRES_SCHEMA, "legacyFallbackDisabled": True},
                "connected": True,
                "election": safe_election,
                "settings": {},
                "president": safe_president,
                "candidates": candidates,
                "results": results,
                "turnout": turnout,
                "votes": [],
                "sessions": [],
                "ballots": safe_ballots,
                "applications": safe_applications,
                "applicationIssues": [],
                "curators": curators,
                "pollingStations": safe_station_rows,
                "voteDeposits": [],
                "audit": audit,
                "laws": laws,
                "pendingLaws": pending_laws,
                "taxes": [],
                "decrees": decrees,
                "petitions": petitions,
                "antiFraud": [],
                "summary": {
                    "electionId": eid,
                    "candidateCount": len(candidates),
                    "voteRows": turnout["deposited_ballots"],
                    "ballotsIssued": len(safe_ballots),
                    "ballotsUsed": len([x for x in safe_ballots if int(x.get("submitted_at") or 0) > 0]),
                    "applications": len(safe_applications),
                    "applicationIssues": 0,
                    "applicationIssuesOpen": 0,
                    "applicationIssuesAnnulled": 0,
                    "curators": 0,
                    "pollingStations": len(safe_station_rows),
                    "activePollingStations": len([x for x in safe_station_rows if int(x.get("active") or 0) > 0]),
                    "voteDeposits": 0,
                    "totalVotes": total_votes,
                    "suspicious": 0,
                    "laws": len(laws),
                    "pendingLaws": len(pending_laws),
                    "taxes": 0,
                    "decrees": len(decrees),
                    "petitions": len(petitions),
                },
            }
        except Exception:
            pass
    return {
        "source": {"type": "postgresql", "schema": POSTGRES_SCHEMA, "legacyFallbackDisabled": True},
        "connected": False,
        "election": {},
        "settings": {},
        "president": {},
        "candidates": [],
        "results": [],
        "turnout": {"issued_ballots": 0, "confirmed_ballots": 0, "deposited_ballots": 0},
        "votes": [],
        "sessions": [],
        "ballots": [],
        "applications": [],
        "applicationIssues": [],
        "curators": [],
        "pollingStations": [],
        "voteDeposits": [],
        "audit": [],
        "laws": [],
        "pendingLaws": [],
        "taxes": [],
        "decrees": [],
        "petitions": [],
        "antiFraud": [],
        "summary": {
            "electionId": "",
            "candidateCount": 0,
            "voteRows": 0,
            "ballotsIssued": 0,
            "ballotsUsed": 0,
            "applications": 0,
            "applicationIssues": 0,
            "applicationIssuesOpen": 0,
            "applicationIssuesAnnulled": 0,
            "curators": 0,
            "pollingStations": 0,
            "activePollingStations": 0,
            "voteDeposits": 0,
            "totalVotes": 0,
            "suspicious": 0,
            "laws": 0,
            "pendingLaws": 0,
            "taxes": 0,
            "decrees": 0,
            "petitions": 0,
        },
    }


def economy_ledger_sync(limit: int = 500) -> dict[str, Any]:
    path = admin_plugin_db_path()
    if not admin_plugin_db_available(path):
        return {"source": admin_plugin_db_location(path), "connected": False, "events": [], "balances": [], "transactions": [], "assets": [], "snapshots": []}
    with open_sqlite_readonly(str(path)) as conn:
        events = safe_sqlite_rows(conn, "cmv7_ar_events", "", [], "time desc,id desc", limit)
        balances = safe_sqlite_rows(conn, "cmv7_ar_balances", "", [], "balance desc,name collate nocase asc", limit)
        transactions = safe_sqlite_rows(conn, "cmv7_ar_transactions", "", [], "time desc,id desc", limit)
        assets = safe_sqlite_rows(conn, "cmv7_ar_assets", "", [], "updated_at desc,asset_id desc", limit)
        scans = safe_sqlite_rows(conn, "cmv7_ar_scan_reports", "", [], "time desc,id desc", limit)
        snapshots = safe_sqlite_rows(conn, "cmv7_ar_economy_snapshots", "", [], "time desc,id desc", limit)
    transfers = [x for x in transactions if "TRANSFER" in str(x.get("type") or "")]
    smelts = [x for x in transactions if "SMELT" in str(x.get("type") or "")]
    return {
        "source": admin_plugin_db_location(path),
        "connected": True,
        "events": events,
        "balances": balances,
        "transactions": transactions,
        "assets": assets,
        "scans": scans,
        "snapshots": snapshots,
        "summary": {
            "holders": len(balances),
            "totalBalance": sum(int(x.get("balance") or 0) for x in balances),
            "inventoryBalance": sum(int(x.get("inventory_balance") or 0) for x in balances),
            "enderBalance": sum(int(x.get("ender_balance") or 0) for x in balances),
            "events": len(events),
            "transactions": len(transactions),
            "assets": len(assets),
            "activeAssets": len([x for x in assets if str(x.get("status") or "").upper() == "ACTIVE"]),
            "transfers": len(transfers),
            "smelts": len(smelts),
            "scans": len(scans),
        },
    }


def list_election_documents_sync(kind: str, limit: int = 200, status: str = "") -> dict[str, Any]:
    if kind not in {"decrees", "petitions"}:
        raise HTTPException(status_code=400, detail="Unsupported election document kind")
    table = "election_decrees" if kind == "decrees" else "election_petitions"
    if not pg_ready():
        return {"rows": [], "count": 0, "storage": "legacy"}
    sql = f"SELECT * FROM {table}"
    args: list[Any] = []
    if status:
        sql += " WHERE upper(status)=upper(%s)"
        args.append(status)
    sql += " ORDER BY created_at DESC LIMIT %s"
    args.append(max(1, min(limit, 500)))
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = [dict(r) for r in conn.execute(sql, tuple(args)).fetchall()]
        conn.commit()
    return {"rows": rows, "count": len(rows), "storage": "postgresql"}


def create_election_decree_sync(data: ElectionDecreeIn, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL is required for election decrees")
    item = {
        "id": f"decree_{secrets.token_hex(8)}",
        "president_uuid": str(data.president_uuid or ""),
        "title": data.title.strip(),
        "body": data.body.strip(),
        "created_at": now_ts(),
        "status": str(data.status or "PUBLISHED").upper(),
    }
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO election_decrees(id,president_uuid,title,body,created_at,status) VALUES(%s,%s,%s,%s,%s,%s)",
            (item["id"], item["president_uuid"], item["title"], item["body"], item["created_at"], item["status"]),
        )
        conn.commit()
    audit_event(actor, "election.decree.create", target=item["id"], details={"title": item["title"], "status": item["status"]})
    return item


def create_election_petition_sync(data: ElectionPetitionIn, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL is required for election petitions")
    item = {
        "id": f"petition_{secrets.token_hex(8)}",
        "creator_uuid": str(data.creator_uuid or ""),
        "title": data.title.strip(),
        "body": data.body.strip(),
        "created_at": now_ts(),
        "status": str(data.status or "OPEN").upper(),
    }
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO election_petitions(id,creator_uuid,title,body,status,created_at) VALUES(%s,%s,%s,%s,%s,%s)",
            (item["id"], item["creator_uuid"], item["title"], item["body"], item["status"], item["created_at"]),
        )
        conn.commit()
    audit_event(actor, "election.petition.create", target=item["id"], details={"title": item["title"], "status": item["status"]})
    return item


def review_candidate_application_sync(application_id: str, data: ElectionApplicationReviewIn, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL is required for candidate applications")
    decision = str(data.decision or "").strip().lower()
    if decision not in {"approved", "rejected"}:
        raise HTTPException(status_code=400, detail="Решение должно быть approved или rejected")
    admin_status = "APPROVED" if decision == "approved" else "REJECTED"
    now = now_ts()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        row_raw = conn.execute("SELECT * FROM candidate_applications WHERE id=%s", (application_id,)).fetchone()
        if not row_raw:
            raise HTTPException(status_code=404, detail="Заявка кандидата не найдена")
        row = dict(row_raw)
        conn.execute(
            """
            UPDATE candidate_applications
               SET admin_status=%s, admin_note=%s, reviewed_at=%s, reviewed_by=%s
             WHERE id=%s
            """,
            (admin_status, data.note.strip(), now, actor, application_id),
        )
        candidate: dict[str, Any] | None = None
        if admin_status == "APPROVED" and data.create_candidate:
            existing = conn.execute(
                """
                SELECT * FROM candidates
                 WHERE election_id=%s AND (application_id=%s OR player_uuid=%s)
                 ORDER BY created_at DESC
                 LIMIT 1
                """,
                (row.get("election_id"), application_id, row.get("player_uuid")),
            ).fetchone()
            if existing:
                candidate = dict(existing)
                if int(candidate.get("active") or 0) != 1:
                    conn.execute("UPDATE candidates SET active=1 WHERE id=%s", (candidate.get("id"),))
                    candidate["active"] = 1
            else:
                candidate = {
                    "id": f"candidate_{secrets.token_hex(8)}",
                    "election_id": row.get("election_id") or "",
                    "player_uuid": row.get("player_uuid") or "",
                    "player_name": row.get("player_name") or "",
                    "application_id": application_id,
                    "created_at": now,
                    "active": 1,
                    "last_result": 0,
                }
                conn.execute(
                    """
                    INSERT INTO candidates(id,election_id,player_uuid,player_name,application_id,created_at,active,last_result)
                    VALUES(%s,%s,%s,%s,%s,%s,%s,%s)
                    """,
                    (
                        candidate["id"],
                        candidate["election_id"],
                        candidate["player_uuid"],
                        candidate["player_name"],
                        candidate["application_id"],
                        candidate["created_at"],
                        candidate["active"],
                        candidate["last_result"],
                    ),
                )
        conn.commit()
    audit_event(actor, "election.application.review", target=application_id, details={"decision": admin_status, "candidate": bool(candidate)})
    append_panel_event(
        "admin-panel",
        "election_application_reviewed",
        actor=actor,
        target=application_id,
        metadata={"decision": admin_status, "candidateId": candidate.get("id") if candidate else ""},
        tags=["elections", "application"],
    )
    return {"applicationId": application_id, "decision": admin_status, "candidate": candidate}


def sqlite_table_rows(path: str, table: str, limit: int = 200, offset: int = 0, q: str = "") -> dict[str, Any]:
    with open_sqlite_readonly(path) as conn:
        available = tables(conn)
        if table not in available:
            raise HTTPException(status_code=404, detail="Таблица не найдена")
        cols = table_columns(conn, table)
        limit = max(1, min(limit, 1000))
        offset = max(0, offset)
        where = ""
        params: list[Any] = []
        if q:
            parts = []
            for c in cols:
                parts.append(f"cast({quote_ident(c)} as text) like ?")
                params.append(f"%{q}%")
            if parts:
                where = " where " + " or ".join(parts)
        order = ""
        for c in ["created_at", "createdAt", "time", "timestamp", "id"]:
            if c in cols:
                order = f" order by {quote_ident(c)} desc"
                break
        if is_pg_conn(conn):
            pk_cols = [c["name"] for c in sqlite_column_meta(conn, table) if c.get("pk")]
            select_expr = "*"
        else:
            pk_cols = [r[1] for r in conn.execute(f"PRAGMA table_info({quote_ident(table)})").fetchall() if int(r[5] or 0) > 0]
            select_expr = "*" if pk_cols else "rowid as rowid, *"
            if not order and not pk_cols:
                order = " order by rowid desc"
        count_row = conn.execute(f"select count(*) as c from {quote_ident(table)}{where}", params).fetchone()
        count = count_row["c"] if isinstance(count_row, dict) else count_row["c"]
        rows = rows_to_dicts(conn.execute(f"select {select_expr} from {quote_ident(table)}{where}{order} limit ? offset ?", params + [limit, offset]).fetchall())
        public_cols = (["rowid"] if (not pk_cols and not is_pg_conn(conn)) else []) + cols
        return {"table": table, "columns": public_cols, "count": count, "rows": rows}


def coreprotect_maps(conn: sqlite3.Connection) -> dict[str, dict[Any, Any]]:
    names: dict[str, dict[Any, Any]] = {"users": {}, "worlds": {}, "materials": {}}
    available = tables(conn)
    if "co_user" in available:
        cols = table_columns(conn, "co_user")
        name_col = "user" if "user" in cols else ("name" if "name" in cols else None)
        if name_col:
            id_expr = "id" if "id" in cols else "rowid"
            try:
                for r in conn.execute(f"select {id_expr} as uid, {quote_ident(name_col)} as name from co_user limit 100000").fetchall():
                    names["users"][r["uid"]] = r["name"]
            except Exception:
                pass
    if "co_world" in available:
        cols = table_columns(conn, "co_world")
        name_col = "world" if "world" in cols else ("name" if "name" in cols else None)
        if name_col:
            id_expr = "id" if "id" in cols else "rowid"
            try:
                for r in conn.execute(f"select {id_expr} as wid, {quote_ident(name_col)} as name from co_world limit 10000").fetchall():
                    names["worlds"][r["wid"]] = r["name"]
            except Exception:
                pass
    for candidate in ["co_material_map", "co_material", "co_block_map"]:
        if candidate not in available:
            continue
        cols = table_columns(conn, candidate)
        id_col = next((c for c in ["id", "rowid"] if c == "rowid" or c in cols), "rowid")
        name_col = next((c for c in ["material", "name", "type"] if c in cols), None)
        if name_col:
            try:
                for r in conn.execute(f"select {id_col} as mid, {quote_ident(name_col)} as name from {quote_ident(candidate)} limit 100000").fetchall():
                    names["materials"][r["mid"]] = r["name"]
            except Exception:
                pass
    return names


def resolve_coreprotect_user_id(conn: sqlite3.Connection, player: str) -> Optional[int]:
    if not player or "co_user" not in tables(conn):
        return None
    cols = table_columns(conn, "co_user")
    name_col = "user" if "user" in cols else ("name" if "name" in cols else None)
    if not name_col:
        return None
    id_expr = "id" if "id" in cols else "rowid"
    row = conn.execute(
        f"select {id_expr} as uid from co_user where lower({quote_ident(name_col)}) = lower(?) limit 1",
        [player],
    ).fetchone()
    return int(row["uid"]) if row else None


def coreprotect_search(
    player: str = "",
    x: Optional[int] = None,
    y: Optional[int] = None,
    z: Optional[int] = None,
    radius: int = 0,
    action: str = "",
    table_filter: str = "all",
    limit: int = 250,
) -> list[dict[str, Any]]:
    with open_sqlite_readonly(COREPROTECT_DB) as conn:
        available = tables(conn)
        candidate_tables = [
            t for t in ["co_block", "co_container", "co_item", "co_entity", "co_sign", "co_chat", "co_command", "co_session"] if t in available
        ]
        if table_filter and table_filter != "all":
            candidate_tables = [t for t in candidate_tables if t == table_filter]
        maps = coreprotect_maps(conn)
        user_id = resolve_coreprotect_user_id(conn, player)
        rows: list[dict[str, Any]] = []
        limit = max(1, min(limit, 2000))
        per_table = max(25, limit // max(1, len(candidate_tables)))
        for table in candidate_tables:
            cols = table_columns(conn, table)
            where: list[str] = []
            params: list[Any] = []
            if x is not None and "x" in cols:
                if radius > 0:
                    where.append("x between ? and ?")
                    params.extend([x - radius, x + radius])
                else:
                    where.append("x = ?")
                    params.append(x)
            if y is not None and "y" in cols:
                if radius > 0:
                    where.append("y between ? and ?")
                    params.extend([y - radius, y + radius])
                else:
                    where.append("y = ?")
                    params.append(y)
            if z is not None and "z" in cols:
                if radius > 0:
                    where.append("z between ? and ?")
                    params.extend([z - radius, z + radius])
                else:
                    where.append("z = ?")
                    params.append(z)
            if user_id is not None and "user" in cols:
                where.append("user = ?")
                params.append(user_id)
            if action and "action" in cols and action.isdigit():
                where.append("action = ?")
                params.append(int(action))
            sql = f"select * from {quote_ident(table)}"
            if where:
                sql += " where " + " and ".join(where)
            if "time" in cols:
                sql += " order by time desc"
            sql += " limit ?"
            params.append(per_table)
            try:
                for row in rows_to_dicts(conn.execute(sql, params).fetchall()):
                    row["source_table"] = table
                    if "time" in row:
                        try:
                            row["datetime"] = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(int(row["time"])))
                        except Exception:
                            pass
                    if "user" in row:
                        row["player_name"] = maps["users"].get(row.get("user"), row.get("user"))
                    if "wid" in row:
                        row["world_name"] = maps["worlds"].get(row.get("wid"), row.get("wid"))
                    if "type" in row:
                        row["material_name"] = maps["materials"].get(row.get("type"), row.get("type"))
                    if "action" in row:
                        row["action_name"] = coreprotect_action_name(table, row.get("action"))
                    rows.append(row)
            except Exception as exc:
                rows.append({"source_table": table, "error": str(exc)})
        rows.sort(key=lambda r: int(r.get("time") or 0), reverse=True)
        return rows[:limit]


def coreprotect_action_name(table: str, action: Any) -> str:
    try:
        value = int(action)
    except Exception:
        return str(action)
    maps = {
        "co_block": {0: "ломал", 1: "ставил"},
        "co_container": {0: "забрал из контейнера", 1: "положил в контейнер"},
        "co_item": {0: "выбросил", 1: "поднял"},
        "co_entity": {0: "убил/сломал", 1: "создал"},
        "co_session": {0: "вышел", 1: "вошёл"},
    }
    return maps.get(table, {}).get(value, str(action))


def normalize_coreprotect_event(row: dict[str, Any], player: str = "") -> dict[str, Any]:
    """Attach readable action text and a safe block sprite path for the admin timeline."""
    item = dict(row or {})
    table = str(item.get("source_table") or item.get("type") or "").lower()
    action_label = str(item.get("action_name") or coreprotect_action_name(table, item.get("action")) or "изменил мир")
    material = str(item.get("material_name") or item.get("material") or item.get("block") or item.get("type") or "stone")
    slug = re.sub(r"[^a-z0-9_]+", "_", material.lower().replace("minecraft:", "")).strip("_") or "stone"
    sprite = FRONTEND_DIR / "assets" / "mc-icons" / "item" / f"{slug}.png"
    if not sprite.is_file():
        slug = "stone"
    item.update({
        "source": "CoreProtect",
        "actionLabel": action_label,
        "blockLabel": material,
        "blockSprite": f"/assets/mc-icons/item/{slug}.png",
        "coordinates": f"{item.get('world_name') or item.get('world') or 'world'} {item.get('x', '')} {item.get('y', '')} {item.get('z', '')}".strip(),
        "player": item.get("player_name") or item.get("player") or player,
    })
    return item


def detect_election_tables_sync() -> dict[str, Any]:
    db_path = admin_plugin_db_path()
    result: dict[str, Any] = {"db": admin_plugin_db_location(db_path), "tables": [], "groups": {}, "antiFraud": []}
    with open_sqlite_readonly(str(db_path)) as conn:
        ts = tables(conn)
        result["tables"] = ts
        keys = {
            "elections": ["elect", "election", "vote_session"],
            "candidates": ["candidate", "applicant", "application"],
            "votes": ["vote", "ballot"],
            "presidents": ["president", "winner", "history"],
            "curators": ["curator", "representative", "admin"],
            "fraud": ["fraud", "fake", "fals", "audit", "log"],
        }
        for group, words in keys.items():
            result["groups"][group] = []
            for t in ts:
                if any(w in t.lower() for w in words):
                    cols = table_columns(conn, t)
                    order = ""
                    for c in ["created_at", "createdAt", "time", "timestamp", "id"]:
                        if c in cols:
                            order = f" order by {quote_ident(c)} desc"
                            break
                    rows = rows_to_dicts(conn.execute(f"select * from {quote_ident(t)}{order} limit 100").fetchall())
                    result["groups"][group].append({"table": t, "columns": cols, "rows": rows})
        # Generic duplicate-vote detector without changing DB.
        for t in ts:
            lower = t.lower()
            if "vote" not in lower and "ballot" not in lower:
                continue
            cols = table_columns(conn, t)
            voter_col = next((c for c in ["voter", VOTER_UUID_COL, "player", "player_uuid", "uuid", "user", "name"] if c in cols), None)
            election_col = next((c for c in ["election_id", "election", "round", "session", "vote_id"] if c in cols), None)
            if voter_col:
                group_cols = [voter_col] + ([election_col] if election_col else [])
                expr = ", ".join(quote_ident(c) for c in group_cols)
                try:
                    for r in conn.execute(
                        f"select {expr}, count(*) as duplicates from {quote_ident(t)} group by {expr} having count(*) > 1 limit 100"
                    ).fetchall():
                        result["antiFraud"].append({"table": t, **{k: r[k] for k in r.keys()}})
                except Exception:
                    pass
    return result


def read_server_properties() -> dict[str, str]:
    path = MC_SERVER_DIR / "server.properties"
    result: dict[str, str] = {}
    if not path.exists():
        return result
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        result[k] = v
    return result


def sha1_file_hex(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_managed_resourcepack_sha1() -> str:
    if MANAGED_RESOURCEPACK_SHA1_FILE.exists():
        saved = MANAGED_RESOURCEPACK_SHA1_FILE.read_text(encoding="utf-8", errors="replace").strip().lower()
        if saved:
            return saved
    if not MANAGED_RESOURCEPACK_ZIP.exists():
        raise HTTPException(status_code=404, detail="Локальный CopiMineResourcePack.zip не найден")
    return sha1_file_hex(MANAGED_RESOURCEPACK_ZIP)


def validate_managed_resourcepack_apply(url: str, sha1: str) -> tuple[str, str]:
    normalized_url = (url or "").strip() or MANAGED_RESOURCEPACK_URL
    if normalized_url != MANAGED_RESOURCEPACK_URL:
        raise HTTPException(status_code=400, detail="Разрешён только официальный URL CopiMine resource pack")
    managed_sha1 = read_managed_resourcepack_sha1()
    requested_sha1 = (sha1 or "").strip().lower()
    if requested_sha1 and requested_sha1 != managed_sha1:
        raise HTTPException(status_code=400, detail="SHA1 не совпадает с локально собранным CopiMineResourcePack.zip")
    return normalized_url, managed_sha1


def public_site_config_sync() -> dict[str, Any]:
    public_host = MC_PUBLIC_ADDRESS or ("" if MC_HOST in {"127.0.0.1", "localhost", "::1"} else MC_HOST)
    props = read_server_properties()
    version = MC_PUBLIC_VERSION or "1.21.x"
    donation_enabled = bool((donation_catalog_snapshot_sync().get("items") or []))
    return {
        "serverName": "CopiMine",
        "serverAddress": public_host,
        "serverVersion": version or "1.21.x",
        "cabinetEnabled": True,
        "donationEnabled": donation_enabled,
        "resourcePackRequired": props.get("require-resource-pack", "false").lower() in {"true", "1", "yes", "on"},
        "modpackDownloadPath": "/downloads/CopiMineMods.zip",
    }


def public_site_status_sync() -> dict[str, Any]:
    online, latency = tcp_online(MC_HOST, MC_PORT)
    players_online = 0
    player_cap = 0
    online_players: list[str] = []
    rcon_available = False
    if RCON_PASSWORD:
        try:
            list_text = rcon_sync("list")
            rcon_available = True
            online_players = parse_online_players(list_text)
            match = re.search(r"There are\s+(\d+)\s+of a max(?:imum)?\s+(\d+)\s+players online", list_text)
            if match:
                players_online = int(match.group(1))
                player_cap = int(match.group(2))
            else:
                players_online = len(online_players)
        except Exception:
            online_players = []
    election = elections_plugin_web_sync()
    overview = election.get("overview") if isinstance(election, dict) else {}
    turnout = election.get("turnout") if isinstance(election, dict) else {}
    treasury = public_treasury_overview_sync(10)
    return {
        "server": {
            "online": online,
            "latencyMs": round(float(latency or 0.0), 1) if latency is not None else None,
            "playersOnline": players_online,
            "playerCap": player_cap,
            "samplePlayers": online_players[:12],
            "playerListAvailable": rcon_available,
        },
        "elections": {
            "active": bool((overview or {}).get("active")),
            "title": (overview or {}).get("title") or "Выборы CopiMine",
            "candidates": int((overview or {}).get("candidates") or 0),
            "votes": int((overview or {}).get("votes") or turnout.get("deposited_ballots") or 0),
            "stations": int((overview or {}).get("stations") or 0),
            "president": (overview or {}).get("president") or "",
        },
        "treasury": treasury,
        "generatedAt": now_ts(),
    }


def public_modpack_sync() -> dict[str, Any]:
    manifest: dict[str, Any] = {}
    if MODPACK_MANIFEST.exists():
        try:
            manifest = json.loads(MODPACK_MANIFEST.read_text(encoding="utf-8"))
        except Exception:
            manifest = {}
    metadata = artifact_metadata("downloads", "CopiMineMods.zip")
    sha1 = str(metadata.get("recordedSha1") or metadata.get("sha1") or "")
    available = bool(metadata.get("exists"))
    return {
        "available": available,
        "downloadUrl": "/downloads/CopiMineMods.zip",
        "filename": MODPACK_ZIP.name,
        "sha1": sha1,
        "sha256": str(metadata.get("sha256") or ""),
        "size": int(metadata.get("size") or 0),
        "modified": metadata.get("modified"),
        "manifest": manifest,
    }


def election_control_command(action: str) -> str:
    raise HTTPException(
        status_code=410,
        detail="Web election control отключён. Управление выборами перенесено в CopiMineElectionCore и доступно только через игровой GUI /cadm -> Выборы.",
    )


def clean_mc_player(value: str | None) -> str:
    player = (value or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9_]{1,32}", player):
        raise HTTPException(status_code=400, detail="Некорректный ник игрока")
    return player


def clean_mc_id(value: str | None) -> str:
    ident = (value or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9_.:-]{1,96}", ident):
        raise HTTPException(status_code=400, detail="Некорректный id записи")
    return ident


def clean_mc_reason(value: str | None) -> str:
    reason = re.sub(r"[\r\n;]+", " ", (value or "web-emergency")).strip()
    reason = re.sub(r"\s+", " ", reason)[:160]
    return reason or "web-emergency"


def election_emergency_command(action: str, data: ElectionEmergencyIn) -> str:
    raise HTTPException(
        status_code=410,
        detail="Аварийные действия по выборам теперь доступны только через игровой GUI участка/ЦИК.",
    )


def write_server_properties(values: dict[str, str]) -> dict[str, Any]:
    path = MC_SERVER_DIR / "server.properties"
    if not path.exists():
        raise HTTPException(status_code=404, detail="server.properties не найден")
    allowed = {
        "motd", "max-players", "view-distance", "simulation-distance", "difficulty", "gamemode", "white-list",
        "resource-pack", "resource-pack-sha1", "require-resource-pack", "resource-pack-prompt", "spawn-protection",
        "enable-rcon", "rcon.port", "server-port", "online-mode", "pvp", "allow-flight", "enable-command-block",
    }
    unknown = [k for k in values if k not in allowed]
    if unknown:
        raise HTTPException(status_code=400, detail=f"Запрещённые ключи: {', '.join(unknown)}")
    for key, value in values.items():
        text = str(value)
        if len(text) > 2048 or any(ord(char) < 0x20 or ord(char) == 0x7F for char in text):
            raise HTTPException(status_code=400, detail=f"Недопустимое значение server.properties: {key}")
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    seen = set()
    out = []
    for raw in lines:
        if "=" in raw and not raw.strip().startswith("#"):
            key = raw.split("=", 1)[0]
            if key in values:
                out.append(f"{key}={values[key]}")
                seen.add(key)
            else:
                out.append(raw)
        else:
            out.append(raw)
    for k, v in values.items():
        if k not in seen:
            out.append(f"{k}={v}")
    backup = path.with_suffix(".properties.bak")
    backup.write_text(path.read_text(encoding="utf-8", errors="replace"), encoding="utf-8")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return {"ok": True, "updated": sorted(values.keys()), "backup": safe_location(backup)}


def run_systemctl(action: str) -> dict[str, Any]:
    allowed = {"start", "stop", "restart", "status"}
    if action not in allowed:
        raise HTTPException(status_code=400, detail="Недоступное действие")
    cmd = ["sudo", "systemctl", action, MINECRAFT_SERVICE]
    if action == "status":
        cmd = ["systemctl", "status", MINECRAFT_SERVICE, "--no-pager", "-l"]
    proc = subprocess.run(cmd, text=True, capture_output=True, timeout=25)
    return {"returncode": proc.returncode, "stdout": proc.stdout[-8000:], "stderr": proc.stderr[-8000:], "command": " ".join(cmd)}


def parse_online_players(list_text: str) -> list[str]:
    if ":" not in list_text:
        return []
    tail = list_text.split(":", 1)[1].strip()
    return [p.strip() for p in tail.split(",") if p.strip()]


async def deliver_temporary_pin_in_game(minecraft_name: str, temp_pin: str) -> bool:
    if not RCON_PASSWORD or not valid_minecraft_name(minecraft_name) or not re.fullmatch(r"\d{4,8}", temp_pin or ""):
        return False
    try:
        online = parse_online_players(await rcon("list"))
        if minecraft_name not in online:
            return False
        payload = json.dumps(
            {
                "text": f"Временный PIN банка CopiMine: {temp_pin}. Открой сайт и сразу задай новый PIN.",
                "color": "gold",
            },
            ensure_ascii=False,
        )
        await rcon(f"tellraw {minecraft_name} {payload}")
        return True
    except Exception:
        return False


def clean_log_line(line: str) -> str:
    return re.sub(r"\x1b\[[0-9;]*m", "", line)


def read_log_lines(lines: int = 120, categories: str = "all") -> list[str]:
    if not LOG_FILE.exists():
        return []
    data = [clean_log_line(x) for x in LOG_FILE.read_text(encoding="utf-8", errors="replace").splitlines()]
    if categories != "all":
        needles = {
            "auth": ["auth", "login", "register", "logged in", "registered"],
            "admin": ["[copimine", "admin", "issued server command", "rcon"],
            "chat": ["[not secure]", "<", "chat"],
            "errors": ["error", "exception", "warn", "could not pass event"],
            "voice": ["voicechat"],
        }.get(categories, [categories.lower()])
        data = [x for x in data if any(n in x.lower() for n in needles)]
    return data[-max(1, min(lines, 5000)):]


def server_stats_sync() -> dict[str, Any]:
    props = read_server_properties()
    plugin_dir = MC_SERVER_DIR / "plugins"
    plugin_jars = sorted(plugin_dir.glob("*.jar")) if plugin_dir.exists() else []
    copimine_jars = [p for p in plugin_jars if "copimine" in p.name.lower()]
    db_paths = [
        admin_plugin_db_path(),
        Path(COREPROTECT_DB),
        DATA_DIR / "audit_log.jsonl",
        DATA_DIR / "plugin_events.jsonl",
    ]
    latest_lines = read_log_lines(600, "all")
    lower_lines = [x.lower() for x in latest_lines]
    warn_count = sum(1 for x in lower_lines if "warn" in x or "warning" in x)
    error_count = sum(1 for x in lower_lines if "error" in x or "exception" in x or "could not pass event" in x)
    online, latency = tcp_online(MC_HOST, MC_PORT)
    disk = shutil.disk_usage(str(MC_SERVER_DIR if MC_SERVER_DIR.exists() else APP_ROOT))
    system: dict[str, Any] = {
        "diskTotal": disk.total,
        "diskUsed": disk.used,
        "diskFree": disk.free,
    }
    if psutil:
        try:
            mem = psutil.virtual_memory()
            system.update({
                "cpuPercent": psutil.cpu_percent(interval=0.1),
                "memoryTotal": mem.total,
                "memoryUsed": mem.used,
                "memoryPercent": mem.percent,
                "bootTime": int(psutil.boot_time()),
            })
        except Exception:
            pass
    region_dir = WORLD_DIR / "region"
    region_files = sorted(region_dir.glob("*.mca")) if region_dir.exists() else []
    return {
        "time": int(time.time()),
        "minecraft": {
            "online": online,
            "latencyMs": latency,
            "host": MC_HOST,
            "port": MC_PORT,
        },
        "system": system,
        "plugins": {
            "totalJars": len(plugin_jars),
            "copimineJars": len(copimine_jars),
            "jars": [
                {"name": p.name, "size": p.stat().st_size, "modified": int(p.stat().st_mtime), "copimine": p in copimine_jars}
                for p in plugin_jars[:160]
            ],
        },
        "databases": [
            {"name": p.name, "path": str(p), "exists": p.exists(), "size": p.stat().st_size if p.exists() else 0, "modified": int(p.stat().st_mtime) if p.exists() else None}
            for p in db_paths
        ],
        "world": {
            "path": str(WORLD_DIR),
            "exists": WORLD_DIR.exists(),
            "regionFiles": len(region_files),
            "sampleRegionSize": sum(p.stat().st_size for p in region_files[:240]),
        },
        "properties": {k: props.get(k) for k in [
            "motd", "max-players", "view-distance", "simulation-distance", "network-compression-threshold",
            "entity-broadcast-range-percentage", "sync-chunk-writes", "white-list", "online-mode",
        ] if k in props},
        "logs": {
            "file": str(LOG_FILE),
            "exists": LOG_FILE.exists(),
            "size": LOG_FILE.stat().st_size if LOG_FILE.exists() else 0,
            "warnings": warn_count,
            "errors": error_count,
            "recentProblems": [x for x in latest_lines if any(n in x.lower() for n in ["warn", "error", "exception", "could not pass event"])][-80:],
        },
    }


def performance_readiness_sync() -> dict[str, Any]:
    props = read_server_properties()
    plugin_dir = MC_SERVER_DIR / "plugins"

    def plugin_row(name: str, pattern: str, config_rel: str = "") -> dict[str, Any]:
        jars = sorted(plugin_dir.glob(pattern)) if plugin_dir.exists() else []
        jar = jars[0] if jars else plugin_dir / pattern.replace("*", "")
        config_path = plugin_dir / config_rel if config_rel else Path("")
        ok = jar.exists() and (not config_rel or config_path.exists())
        return {
            "name": name,
            "ok": ok,
            "jar": jar.name if jar.exists() else "missing",
            "jarPath": safe_location(jar),
            "config": safe_location(config_path) if config_rel else "",
            "configExists": config_path.exists() if config_rel else None,
            "version": jar_plugin_info(jar).get("version", "") if jar.exists() else "",
        }

    prompt = props.get("resource-pack-prompt", "")
    resource_pack_prompt_readable = bool(prompt and "CopiMine" in prompt and "Р" not in prompt and "С" not in prompt)
    startup_rows: list[dict[str, Any]] = []
    try:
        db_path = admin_plugin_db_path()
        if admin_plugin_db_available(db_path):
            with open_sqlite_readonly(str(db_path)) as conn:
                if "cmv8_startup_checks" in tables(conn):
                    startup_rows = rows_to_dicts(conn.execute("select * from cmv8_startup_checks order by ok asc, key asc").fetchall())
    except Exception:
        startup_rows = []

    def prop_int(name: str, fallback: int) -> int:
        try:
            return int(str(props.get(name, fallback)).strip())
        except Exception:
            return fallback

    optimization_stack = [
        plugin_row("FarmControl", "FarmControl*.jar", "FarmControl/config.yml"),
        plugin_row("EntityClearer", "EntityClearer*.jar", "EntityClearer/config.yml"),
        plugin_row("GrimAC", "GrimAC*.jar", "GrimAC/config.yml"),
        plugin_row("Chunky", "Chunky-Bukkit-*.jar", "Chunky/config.yml"),
        plugin_row("SeeMore", "SeeMore-*.jar", "SeeMore/config.yml"),
    ]
    property_checks = [
        {"name": "view-distance", "ok": prop_int("view-distance", 99) <= 6, "value": props.get("view-distance", "")},
        {"name": "simulation-distance", "ok": prop_int("simulation-distance", 99) <= 4, "value": props.get("simulation-distance", "")},
        {"name": "entity-broadcast-range-percentage", "ok": prop_int("entity-broadcast-range-percentage", 100) <= 60, "value": props.get("entity-broadcast-range-percentage", "")},
        {"name": "sync-chunk-writes", "ok": props.get("sync-chunk-writes") == "false", "value": props.get("sync-chunk-writes", "")},
        {"name": "network-compression-threshold", "ok": props.get("network-compression-threshold") == "512", "value": props.get("network-compression-threshold", "")},
        {"name": "max-chained-neighbor-updates", "ok": props.get("max-chained-neighbor-updates") == "100000", "value": props.get("max-chained-neighbor-updates", "")},
        {"name": "resourcePackPromptReadable", "ok": resource_pack_prompt_readable, "value": "readable" if resource_pack_prompt_readable else "check prompt"},
    ]
    checks = [
        *[{"name": row["name"], "ok": row["ok"], "detail": row["jar"]} for row in optimization_stack],
        *property_checks,
        {"name": "startupSelfCheck", "ok": bool(startup_rows), "value": f"{sum(1 for r in startup_rows if int(r.get('ok') or 0) == 1)}/{len(startup_rows)}" if startup_rows else "not recorded"},
    ]
    ready = round((sum(1 for row in checks if row.get("ok")) / max(1, len(checks))) * 100)
    return {
        "time": now_ts(),
        "readyPercent": ready,
        "resourcePackPromptReadable": resource_pack_prompt_readable,
        "optimizationStack": optimization_stack,
        "propertyChecks": property_checks,
        "startupChecks": startup_rows,
        "checks": checks,
        "sources": {
            "serverProperties": safe_location(MC_SERVER_DIR / "server.properties"),
            "plugins": safe_location(plugin_dir),
            "adminDb": admin_plugin_db_location(admin_plugin_db_path()),
        },
    }


def jar_plugin_info(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        with zipfile.ZipFile(path) as archive:
            raw = archive.read("plugin.yml").decode("utf-8", errors="replace")
        info: dict[str, Any] = {}
        for line in raw.splitlines():
            if ":" not in line or line.startswith((" ", "-")):
                continue
            key, value = line.split(":", 1)
            if key.strip() in {"name", "version", "main", "website", "description"}:
                info[key.strip()] = value.strip().strip('"').strip("'")
        return info
    except Exception as exc:
        return {"error": public_error_message(exc)}


def anticheat_status_sync(limit: int = 160) -> dict[str, Any]:
    plugin_dir = MC_SERVER_DIR / "plugins"
    jar_candidates = []
    if plugin_dir.exists():
        jar_candidates = sorted({p.resolve() for p in plugin_dir.glob("GrimAC*.jar") if p.is_file()})
    jar_path = Path(jar_candidates[0]) if jar_candidates else plugin_dir / "GrimAC.jar"
    grim_dir = plugin_dir / "GrimAC"
    config_en = grim_dir / "config" / "en.yml"
    config_ru = grim_dir / "config" / "ru.yml"
    punish_en = grim_dir / "punishments" / "en.yml"
    punish_ru = grim_dir / "punishments" / "ru.yml"
    checks = grim_dir / "checks.yml"
    files = [config_en, config_ru, punish_en, punish_ru, checks]

    def text(path: Path) -> str:
        if not path.exists():
            return ""
        return path.read_text(encoding="utf-8", errors="replace")

    punishments = text(punish_en) + "\n" + text(punish_ru)
    config_text = text(config_en) + "\n" + text(config_ru) + "\n" + text(checks)
    banned_output_tokens = ["[alert]", "[webhook]", "[proxy]", "minecraft:say", "tellraw", "broadcast", "actionbar"]
    silent_profile = (
        "CopiMineSilentMonitor" in punishments
        and "[log]" in punishments
        and not any(token.lower() in punishments.lower() for token in banned_output_tokens)
    )
    stable_profile = "experimental-checks: false" in config_text and "check-for-updates: false" in config_text

    log_rows = []
    for line in read_log_lines(2400, "all"):
        lower = line.lower()
        if any(token in lower for token in ["grimac", "[grim", "grim anticheat", "anticheat"]):
            log_rows.append({"time": "", "source": "latest.log", "message": line, "severity": "info"})
        elif "violation" in lower and ("grim" in lower or "check" in lower):
            log_rows.append({"time": "", "source": "latest.log", "message": line, "severity": "warning"})
    event_rows = [
        row for row in read_jsonl_tail(EVENT_LOG_FILE, limit)
        if "grim" in json.dumps(row, ensure_ascii=False).lower() or "anticheat" in json.dumps(row, ensure_ascii=False).lower()
    ]
    checks_rows = [
        {"name": "Jar", "ok": jar_path.exists(), "detail": jar_path.name if jar_path.exists() else "GrimAC.jar is missing"},
        {"name": "Stable config", "ok": stable_profile, "detail": "experimental checks off, update checks off"},
        {"name": "Silent profile", "ok": silent_profile, "detail": "log-only punishments, no in-game alerts"},
        {"name": "Logs", "ok": LOG_FILE.exists(), "detail": safe_location(LOG_FILE)},
        {"name": "Website events", "ok": EVENT_LOG_FILE.exists(), "detail": safe_location(EVENT_LOG_FILE)},
    ]
    return {
        "time": now_ts(),
        "installed": jar_path.exists(),
        "jar": {
            "path": safe_location(jar_path),
            "size": jar_path.stat().st_size if jar_path.exists() else 0,
            "modified": int(jar_path.stat().st_mtime) if jar_path.exists() else None,
            "plugin": jar_plugin_info(jar_path),
        },
        "directory": safe_location(grim_dir),
        "silentProfile": silent_profile,
        "stableProfile": stable_profile,
        "checks": checks_rows,
        "files": [
            {
                "name": str(path.relative_to(grim_dir)) if grim_dir in path.parents else path.name,
                "exists": path.exists(),
                "size": path.stat().st_size if path.exists() else 0,
                "modified": int(path.stat().st_mtime) if path.exists() else None,
            }
            for path in files
        ],
        "events": (log_rows[-limit:] + event_rows)[-limit:],
        "summary": {
            "logLines": len(log_rows),
            "panelEvents": len(event_rows),
            "bannedOutputTokens": [token for token in banned_output_tokens if token.lower() in punishments.lower()],
        },
    }


def mca_chunks(path: Path, max_chunks: int) -> list[tuple[int, int, dict[str, Any]]]:
    result: list[tuple[int, int, dict[str, Any]]] = []
    if not nbtlib:
        return result
    raw = path.read_bytes()
    if len(raw) < 8192:
        return result
    for index in range(1024):
        if len(result) >= max_chunks:
            break
        off = index * 4
        location = raw[off:off + 4]
        sector_offset = int.from_bytes(location[:3], "big")
        sector_count = location[3]
        if sector_offset == 0 or sector_count == 0:
            continue
        pos = sector_offset * 4096
        if pos + 5 > len(raw):
            continue
        length = int.from_bytes(raw[pos:pos + 4], "big")
        compression = raw[pos + 4]
        payload = raw[pos + 5:pos + 4 + length]
        try:
            if compression == 1:
                payload = gzip.decompress(payload)
            elif compression == 2:
                payload = zlib.decompress(payload)
            elif compression == 3:
                pass
            else:
                continue
            nbt = nbtlib.File.parse(io.BytesIO(payload))
            plain = nbt_to_plain(nbt)
            if isinstance(plain, dict):
                local_x = index % 32
                local_z = index // 32
                result.append((local_x, local_z, plain))
        except Exception:
            continue
    return result


def extract_block_entities(chunk: dict[str, Any]) -> list[dict[str, Any]]:
    if "block_entities" in chunk and isinstance(chunk["block_entities"], list):
        return [x for x in chunk["block_entities"] if isinstance(x, dict)]
    level = chunk.get("Level")
    if isinstance(level, dict):
        for key in ["TileEntities", "block_entities"]:
            if isinstance(level.get(key), list):
                return [x for x in level[key] if isinstance(x, dict)]
    return []


def scan_world_containers_sync(max_region_files: int = MAX_WORLD_REGION_FILES, max_chunks: int = MAX_WORLD_CHUNKS) -> dict[str, Any]:
    targets = ar_targets()
    started = time.time()
    rows: list[dict[str, Any]] = []
    regions = []
    for world in [WORLD_DIR] + sorted([p for p in MC_SERVER_DIR.glob("world*") if p.is_dir() and p != WORLD_DIR]):
        regions += list((world / "region").glob("*.mca"))
    regions = sorted(set(regions), key=lambda p: p.stat().st_mtime if p.exists() else 0, reverse=True)[:max(1, max_region_files)]
    chunks_seen = 0
    for region_path in regions:
        try:
            m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca", region_path.name)
            rx, rz = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
            for lx, lz, chunk in mca_chunks(region_path, max(1, max_chunks - chunks_seen)):
                chunks_seen += 1
                for be in extract_block_entities(chunk):
                    amount = count_target_items_recursive(be, targets)
                    if amount:
                        rows.append({
                            "worldPath": str(region_path.parent.parent),
                            "region": region_path.name,
                            "chunkX": rx * 32 + lx,
                            "chunkZ": rz * 32 + lz,
                            "x": be.get("x"), "y": be.get("y"), "z": be.get("z"),
                            "blockEntity": be.get("id", be.get("Id", "container")),
                            "amount": amount,
                        })
                if chunks_seen >= max_chunks:
                    break
        except Exception:
            continue
        if chunks_seen >= max_chunks:
            break
    total = sum(int(r["amount"]) for r in rows)
    snapshot = {"createdAt": int(time.time()), "durationMs": int((time.time() - started) * 1000), "regions": len(regions), "chunks": chunks_seen, "total": total, "rows": rows}
    write_json(DATA_DIR / "ares_world_scan.json", snapshot)
    return snapshot


def plugin_json(path: Path) -> dict[str, Any]:
    data = read_json(path, {})
    return data if isinstance(data, dict) else {}


def economy_plugin_stats_sync() -> dict[str, Any]:
    path = MC_SERVER_DIR / "plugins" / "CopiMineEconomyGuard" / "web" / "ar-stats.json"
    data = plugin_json(path)
    return {"available": bool(data), "file": safe_location(path), "data": data}


def elections_plugin_web_sync() -> dict[str, Any]:
    def sanitize_vote_payload(data: Any) -> Any:
        if not isinstance(data, dict):
            return data
        cleaned = dict(data)
        cleaned.pop("votes", None)
        return cleaned

    paths = [
        MC_SERVER_DIR / "plugins" / "CopiMineElectionCore" / "web-data.json",
        MC_SERVER_DIR / "plugins" / "CopiMineElections" / "web-data.json",
        MC_SERVER_DIR / "plugins" / "CopiMineUltimateAdmin" / "web-data.json",
    ]
    payloads = []
    for path in paths:
        data = plugin_json(path)
        payloads.append({"available": bool(data), "file": safe_location(path), "data": sanitize_vote_payload(data)})
    primary = payloads[0]["data"] if payloads and payloads[0]["available"] else {}
    legacy = payloads[1]["data"] if len(payloads) > 1 and payloads[1]["available"] else {}
    source = primary or legacy
    candidates = source.get("candidates") if isinstance(source.get("candidates"), list) else []
    results = source.get("results") if isinstance(source.get("results"), list) else candidates
    turnout = source.get("turnout") if isinstance(source.get("turnout"), dict) else {}
    votes: list[dict[str, Any]] = []
    applications = source.get("applications") or source.get("currentApplications") or []
    stations = source.get("stations") if isinstance(source.get("stations"), list) else []
    audit_rows = source.get("audit") if isinstance(source.get("audit"), list) else []
    status = "not_connected"
    if source:
        if source.get("active") is True:
            status = "active"
        elif source.get("endedAt"):
            status = "finished"
        else:
            status = "configured"
    suspicious: list[dict[str, Any]] = []
    public_vote_count = int(turnout.get("deposited_ballots") or sum(int(row_get(row, "votes", 0) or 0) for row in results))
    return {
        "status": status,
        "sources": payloads,
        "overview": {
            "active": bool(source.get("active")) if source else False,
            "electionId": source.get("electionId") or source.get("id"),
            "title": source.get("title") or source.get("name") or "CopiMine Elections",
            "startedAt": source.get("startedAt"),
            "endedAt": source.get("endedAt"),
            "scheduledEndAt": source.get("scheduledEndAt"),
            "president": source.get("president"),
            "candidates": len(candidates),
            "applications": len(applications) if isinstance(applications, list) else 0,
            "votes": public_vote_count,
            "stations": len(stations),
            "audit": len(audit_rows),
            "suspiciousVotes": len(suspicious),
        },
        "candidates": candidates,
        "results": results,
        "turnout": turnout,
        "applications": applications if isinstance(applications, list) else [],
        "votes": [],
        "stations": stations,
        "audit": audit_rows,
        "antiFraud": suspicious,
        "raw": sanitize_vote_payload(source),
    }


def source_registry_sync() -> dict[str, Any]:
    rows = []
    if pg_ready():
        rows.append({
            "name": "PostgreSQL",
            "type": "database",
            "connected": True,
            "capabilities": [
                "site_accounts", "player_bank", "audit", "plugin_events", "discord_sync",
                "auth_migration", "elections_v4", "economy_snapshots",
            ],
            "status": "primary",
            "lastReadAt": now_ts(),
            "message": "Primary CopiMine V4 storage",
            "detail": {"host": POSTGRES_HOST, "port": POSTGRES_PORT, "database": POSTGRES_DB, "schema": POSTGRES_SCHEMA},
        })
    plugins = {
        "LuckPerms": ["players", "permissions"],
        "Essentials": ["players", "punishments", "teleports"],
        "CoreProtect": ["logs", "block_logs", "containers"],
        "AuthMe": ["security", "auth"],
        "Simple Voice Chat": ["voice"],
        "TAB": ["players", "display"],
        "Vault": ["economy_bridge"],
        "CopiMineUltimateAdmin": ["players", "elections", "tickets", "events"],
        "CopiMineElectionCore": ["elections", "president", "laws", "taxes", "live_panel"],
        "CopiMineEconomyGuard": ["economy", "ares", "events"],
        "CopiMineElections": ["elections", "votes", "events"],
    }
    for name, capabilities in plugins.items():
        folder = "voicechat" if name == "Simple Voice Chat" else name
        p = MC_SERVER_DIR / "plugins" / folder
        rows.append({
            "name": name,
            "type": "plugin",
            "connected": p.exists(),
            "capabilities": capabilities if p.exists() else [],
            "status": "connected" if p.exists() else "not_connected",
            "lastReadAt": now_ts() if p.exists() else None,
            "message": "Источник найден" if p.exists() else "Источник не подключен или папка плагина не найдена",
        })
    files = [
        ("usercache", MC_SERVER_DIR / "usercache.json", ["players"]),
        ("whitelist", MC_SERVER_DIR / "whitelist.json", ["security", "players"]),
        ("ops", MC_SERVER_DIR / "ops.json", ["security", "players"]),
        ("bans", MC_SERVER_DIR / "banned-players.json", ["punishments"]),
        ("latest.log", LOG_FILE, ["logs"]),
        ("CoreProtect database", Path(COREPROTECT_DB), ["block_logs", "chat", "commands", "containers"]),
        ("Admin plugin database", admin_plugin_db_path(), ["elections", "tickets", "settings"]),
    ]
    for name, path, capabilities in files:
        exists = path.exists()
        rows.append({
            "name": name,
            "type": "file",
            "connected": exists,
            "capabilities": capabilities if exists else [],
            "status": "connected" if exists else "missing",
            "file": safe_location(path),
            "lastReadAt": int(path.stat().st_mtime) if exists else None,
            "message": "Файл доступен для чтения" if exists else "Источник не найден; данные в UI будут пустыми",
        })
    return {"sources": rows, "generatedAt": now_ts()}


def service_status_sync() -> dict[str, Any]:
    rows = []
    systemctl = shutil.which("systemctl")
    for service in SYSTEMD_SERVICES:
        item = {"service": service, "available": bool(systemctl), "active": "unknown", "enabled": "unknown", "ok": False}
        if not systemctl:
            item["message"] = "systemctl недоступен в текущей среде"
            rows.append(item)
            continue
        try:
            active = subprocess.run([systemctl, "is-active", service], text=True, capture_output=True, timeout=8)
            enabled = subprocess.run([systemctl, "is-enabled", service], text=True, capture_output=True, timeout=8)
            item.update({
                "active": active.stdout.strip() or active.stderr.strip(),
                "enabled": enabled.stdout.strip() or enabled.stderr.strip(),
                "ok": active.returncode == 0,
            })
        except Exception as exc:
            item["message"] = public_error_message(exc)
        rows.append(item)
    return {"services": rows, "generatedAt": now_ts()}


def inventory_snapshot_payload(player: str) -> dict[str, Any]:
    uuid = find_player_uuid(player)
    if not uuid:
        raise HTTPException(status_code=404, detail="Игрок не найден")
    nbt = load_player_nbt(uuid)
    name = uuid_to_name().get(uuid, player)
    if nbt is None:
        return {"uuid": uuid, "name": name, "createdAt": now_ts(), "parserReady": False, "inventory": [], "enderChest": [], "message": "playerdata пока недоступен или NBT parser не установлен"}
    inv = inventory_summary(nbt)
    return {
        "uuid": uuid,
        "name": name,
        "createdAt": now_ts(),
        "parserReady": True,
        "inventory": normalize_inventory_list(inv.get("inventory", [])),
        "enderChest": normalize_inventory_list(inv.get("enderChest", [])),
        "arInInventory": inv.get("arInInventory", 0),
        "arInEnderChest": inv.get("arInEnderChest", 0),
    }


def snapshot_file_for_uuid(uuid: str) -> Path:
    safe = re.sub(r"[^A-Za-z0-9_-]", "_", uuid)
    return INVENTORY_SNAPSHOTS_DIR / f"{safe}.jsonl"


def save_inventory_snapshot_sync(player: str) -> dict[str, Any]:
    payload = inventory_snapshot_payload(player)
    append_jsonl(snapshot_file_for_uuid(payload["uuid"]), payload)
    append_panel_event("admin-panel", "inventory_snapshot_created", target=payload["name"], metadata={"uuid": payload["uuid"]}, tags=["inventory"])
    return payload


def inventory_history_sync(player: str, limit: int = 120) -> dict[str, Any]:
    uuid = find_player_uuid(player)
    if not uuid:
        raise HTTPException(status_code=404, detail="Игрок не найден")
    rows = read_jsonl_tail(snapshot_file_for_uuid(uuid), limit)
    return {
        "uuid": uuid,
        "name": uuid_to_name().get(uuid, player),
        "snapshots": rows,
        "count": len(rows),
        "message": "" if rows else "Истории инвентаря пока нет. Она начнется с момента создания snapshot-ов панели или подключения будущего плагина.",
    }


def inventory_item_counts(snapshot: dict[str, Any]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for group in ("inventory", "enderChest"):
        items = snapshot.get(group, [])
        if not isinstance(items, list):
            continue
        for item in items:
            if not isinstance(item, dict):
                continue
            key = str(item.get("id") or item.get("item") or "unknown")
            counts[key] = counts.get(key, 0) + int(item.get("count") or 0)
    return counts


def inventory_diff_sync(player: str, older: int, newer: int) -> dict[str, Any]:
    hist = inventory_history_sync(player, 500)
    rows = hist["snapshots"]
    by_ts = {int(x.get("createdAt", 0)): x for x in rows}
    left = by_ts.get(int(older))
    right = by_ts.get(int(newer))
    if not left or not right:
        raise HTTPException(status_code=404, detail="Один из snapshot-ов не найден")
    a = inventory_item_counts(left)
    b = inventory_item_counts(right)
    changes = []
    for key in sorted(set(a) | set(b)):
        delta = b.get(key, 0) - a.get(key, 0)
        if delta:
            changes.append({"item": key, "before": a.get(key, 0), "after": b.get(key, 0), "delta": delta})
    return {"player": hist["name"], "older": older, "newer": newer, "changes": changes}


def economy_snapshot_payload() -> dict[str, Any]:
    players_list = list_players_sync("")["players"]
    rows = []
    total = 0
    for p in players_list:
        uuid = p["uuid"]
        nbt = load_player_nbt(uuid)
        if not nbt:
            continue
        inv = inventory_summary(nbt)
        amount = int(inv.get("arInInventory", 0)) + int(inv.get("arInEnderChest", 0))
        if amount:
            rows.append({"player": p["name"], "uuid": uuid, "inventory": inv.get("arInInventory", 0), "enderChest": inv.get("arInEnderChest", 0), "amount": amount})
            total += amount
    rows.sort(key=lambda x: x["amount"], reverse=True)
    world_scan = read_json(DATA_DIR / "ares_world_scan.json", {})
    plugin_stats = economy_plugin_stats_sync()
    return {
        "createdAt": now_ts(),
        "itemIds": AR_ITEM_IDS,
        "totalKnownInPlayerData": total,
        "players": rows,
        "worldContainers": world_scan if isinstance(world_scan, dict) else {},
        "pluginStats": plugin_stats,
        "readOnly": True,
    }


def save_economy_snapshot_sync() -> dict[str, Any]:
    snapshot = economy_snapshot_payload()
    append_jsonl(ECONOMY_SNAPSHOTS_FILE, snapshot)
    if pg_ready():
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            conn.execute(
                """
                INSERT INTO ar_money_supply_snapshots(created_at,total_accounts,total_physical,total_supply,details)
                VALUES(%s,%s,%s,%s,%s)
                """,
                (
                    int(snapshot["createdAt"] or now_ts()),
                    len(snapshot.get("players") or []),
                    int(snapshot.get("totalKnownInPlayerData") or 0),
                    int(snapshot.get("totalKnownInPlayerData") or 0),
                    pg_json_dumps(snapshot),
                ),
            )
            conn.commit()
    append_panel_event("admin-panel", "economy_snapshot_created", metadata={"totalKnownInPlayerData": snapshot["totalKnownInPlayerData"]}, tags=["economy", "read-only"])
    return snapshot


def economy_history_sync(limit: int = 120) -> dict[str, Any]:
    if pg_ready():
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            raw_rows = conn.execute(
                "SELECT created_at,total_accounts,total_physical,total_supply,details FROM ar_money_supply_snapshots ORDER BY created_at DESC LIMIT %s",
                (max(1, min(limit, 500)),),
            ).fetchall()
            conn.commit()
        rows = []
        for row in raw_rows:
            details = pg_json_loads(row["details"], {})
            if isinstance(details, dict) and details:
                rows.append(details)
            else:
                rows.append(
                    {
                        "createdAt": int(row["created_at"] or 0),
                        "totalKnownInPlayerData": int(row["total_physical"] or 0),
                        "players": [],
                        "totalAccounts": int(row["total_accounts"] or 0),
                        "totalSupply": int(row["total_supply"] or 0),
                    }
                )
    else:
        rows = read_jsonl_tail(ECONOMY_SNAPSHOTS_FILE, limit)
    changes = []
    for prev, cur in zip(rows, rows[1:]):
        changes.append({
            "from": prev.get("createdAt"),
            "to": cur.get("createdAt"),
            "before": prev.get("totalKnownInPlayerData", 0),
            "after": cur.get("totalKnownInPlayerData", 0),
            "delta": int(cur.get("totalKnownInPlayerData", 0) or 0) - int(prev.get("totalKnownInPlayerData", 0) or 0),
        })
    return {
        "snapshots": rows,
        "changes": changes,
        "count": len(rows),
        "message": "" if rows else "Истории экономики пока нет. Она начнется с момента создания snapshot-ов панели или подключения будущего плагина.",
        "readOnly": True,
    }


def economy_history_available() -> bool:
    if pg_ready():
        try:
            with auth_conn() as conn:
                ensure_v4_schema(conn)
                row = conn.execute("SELECT COUNT(*) AS c FROM ar_money_supply_snapshots").fetchone()
                conn.commit()
            return bool(int(row["c"] or 0))
        except Exception:
            return ECONOMY_SNAPSHOTS_FILE.exists()
    return ECONOMY_SNAPSHOTS_FILE.exists()


APPLICATION_STATUSES = {"pending", "approved", "rejected", "needs_clarification", "withdrawn"}
REPORT_STATUSES = {"open", "in_progress", "answered", "closed", "rejected", "investigation"}


def normalize_status(value: str, allowed: set[str], default: str) -> str:
    status = re.sub(r"[^a-z0-9_:-]", "", (value or default).strip().lower().replace("-", "_"))
    return status if status in allowed else default


def discord_object_url(object_type: str, object_id: str = "") -> str:
    return f"{ADMIN_PUBLIC_BASE_URL}/#discord"


def load_collection(path: Path) -> list[dict[str, Any]]:
    rows = read_json(path, [])
    return rows if isinstance(rows, list) else []


def save_collection(path: Path, rows: list[dict[str, Any]], limit: int = 5000) -> None:
    write_json(path, rows[:limit])


def sync_collection_key(namespace: str, object_id: str) -> str:
    return f"{namespace}:{object_id}"


def pg_load_collection(namespace: str, limit: int = 5000) -> list[dict[str, Any]]:
    if not pg_ready():
        return []
    pattern = sync_collection_key(namespace, "%")
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            "SELECT key,value,updated_at FROM discord_status_state WHERE key LIKE %s ORDER BY updated_at DESC LIMIT %s",
            (pattern, max(1, min(limit, 10000))),
        ).fetchall()
        conn.commit()
    out: list[dict[str, Any]] = []
    for row in rows:
        item = pg_json_loads(row["value"], {})
        if isinstance(item, dict):
            if "id" not in item:
                item["id"] = str(row["key"]).split(":", 1)[1]
            out.append(item)
    return out


def pg_save_collection_item(namespace: str, item: dict[str, Any]) -> None:
    if not pg_ready():
        return
    object_id = str(item.get("id") or "")
    if not object_id:
        return
    pg_write_key_value("discord_status_state", sync_collection_key(namespace, object_id), item)


def pg_delete_collection_item(namespace: str, object_id: str) -> None:
    if not pg_ready() or not object_id:
        return
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("DELETE FROM discord_status_state WHERE key=%s", (sync_collection_key(namespace, object_id),))
        conn.commit()


def load_collection_sync(path: Path, namespace: str, limit: int = 5000) -> list[dict[str, Any]]:
    if pg_ready():
        rows = pg_load_collection(namespace, limit)
        if rows:
            return rows
    rows = read_json(path, [])
    return rows[:limit] if isinstance(rows, list) else []


def save_collection_item_sync(path: Path, namespace: str, item: dict[str, Any], limit: int = 5000) -> dict[str, Any]:
    rows = load_collection(path)
    object_id = str(item.get("id") or "")
    replaced = False
    for idx, existing in enumerate(rows):
        if str(existing.get("id") or "") == object_id:
            rows[idx] = item
            replaced = True
            break
    if not replaced:
        rows.insert(0, item)
    save_collection(path, rows, limit)
    pg_save_collection_item(namespace, item)
    return item


def save_collection_rows_sync(path: Path, namespace: str, rows: list[dict[str, Any]], limit: int = 5000) -> None:
    save_collection(path, rows, limit)
    if pg_ready():
        for item in rows[:limit]:
            if isinstance(item, dict):
                pg_save_collection_item(namespace, item)


def find_collection_item(rows: list[dict[str, Any]], object_id: str) -> dict[str, Any]:
    for item in rows:
        if str(item.get("id", "")) == str(object_id):
            return item
    raise HTTPException(status_code=404, detail="Discord object not found")


def add_timeline(item: dict[str, Any], action: str, actor: str, note: str = "", metadata: Optional[dict[str, Any]] = None) -> None:
    timeline = item.setdefault("timeline", [])
    if not isinstance(timeline, list):
        timeline = []
        item["timeline"] = timeline
    timeline.append({
        "at": now_ts(),
        "action": action,
        "actor": actor,
        "note": note[:1200],
        "metadata": redact_value(metadata or {}),
    })


def application_description(item: dict[str, Any]) -> str:
    parts = [
        f"Player: {item.get('player', 'unknown')}",
        f"Status: {item.get('status', 'pending')}",
    ]
    if item.get("uuid"):
        parts.append(f"UUID: {item.get('uuid')}")
    if item.get("age"):
        parts.append(f"Age: {item.get('age')}")
    if item.get("experience"):
        parts.append(f"Experience: {item.get('experience')}")
    if item.get("why"):
        parts.append(f"Why: {item.get('why')}")
    if item.get("questionnaire"):
        parts.append(f"Questionnaire: {item.get('questionnaire')}")
    if item.get("discord_username") or item.get("discord_user_id"):
        parts.append(f"Discord: {item.get('discord_username') or item.get('discord_user_id')}")
    parts.append(f"Application ID: {item.get('id')}")
    return "\n".join(str(x) for x in parts if x)


def report_description(item: dict[str, Any]) -> str:
    metadata = normalize_report_metadata_for_source(item.get("metadata"), str(item.get("source") or ""), str(item.get("message") or ""))
    coords = ""
    if item.get("world") or item.get("x") is not None or item.get("y") is not None or item.get("z") is not None:
        coords = f"{item.get('world') or 'world'} {item.get('x', '')} {item.get('y', '')} {item.get('z', '')}".strip()
    parts = [
        f"Reporter: {item.get('reporter', 'unknown')}",
        f"Target: {item.get('target') or 'not specified'}",
        f"Severity: {item.get('severity', 'normal')}",
        f"Status: {item.get('status', 'open')}",
        f"Message: {item.get('message', '')}",
    ]
    if str(item.get("reportType") or metadata.get("reportKind") or "").lower() == "bug":
        if item.get("errorCode") or metadata.get("errorCode"):
            parts.append(f"Error code: {item.get('errorCode') or metadata.get('errorCode')}")
        if item.get("errorSummary") or metadata.get("errorSummary"):
            parts.append(f"Summary: {item.get('errorSummary') or metadata.get('errorSummary')}")
    if coords:
        parts.append(f"Location: {coords}")
    if item.get("investigation_id"):
        parts.append(f"Investigation: {item.get('investigation_id')}")
    parts.append(f"Report ID: {item.get('id')}")
    return "\n".join(str(x) for x in parts if x)


def read_discord_state_sync() -> dict[str, Any]:
    state = read_json(DISCORD_STATE_FILE, {})
    if pg_ready():
        try:
            pg_state = pg_read_key_values("discord_status_state")
            if pg_state:
                return pg_state
        except Exception:
            pass
    return state if isinstance(state, dict) else {}


def write_discord_state_sync(state: dict[str, Any]) -> None:
    write_json(DISCORD_STATE_FILE, state)
    if pg_ready():
        for key, value in state.items():
            pg_write_key_value("discord_status_state", str(key), value)
        pg_log_bridge_event("discord-bot", "heartbeat", {"keys": sorted(state.keys())[:50]})
        if isinstance(state.get("statusChannels"), list):
            with auth_conn() as conn:
                ensure_v4_schema(conn)
                for channel in state["statusChannels"][:20]:
                    if not isinstance(channel, dict):
                        continue
                    conn.execute(
                        "INSERT INTO status_channel_snapshots(channel_id,created_at,payload) VALUES(%s,%s,%s)",
                        (str(channel.get("channelId") or channel.get("id") or "unknown"), now_ts(), pg_json_dumps(channel)),
                    )
                conn.commit()


def read_recent_bridge_events(limit: int = 100) -> list[dict[str, Any]]:
    if not pg_ready():
        return read_jsonl_tail(DISCORD_ACTIONS_FILE, limit)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        rows = conn.execute(
            "SELECT id,source,event_type,created_at,details FROM bridge_events ORDER BY id DESC LIMIT %s",
            (max(1, min(limit, 500)),),
        ).fetchall()
        conn.commit()
    return [
        {
            "id": str(row["id"]),
            "source": row["source"],
            "action": row["event_type"],
            "createdAt": int(row["created_at"] or 0),
            **(pg_json_loads(row["details"], {}) if str(row["details"] or "").startswith("{") else {"details": str(row["details"] or "")}),
        }
        for row in rows
    ]


def sync_discord_outbox_status(item: dict[str, Any]) -> None:
    if not pg_ready():
        return
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO discord_notifications_log(channel_id,object_type,object_id,sent_at,status) VALUES(%s,%s,%s,%s,%s)",
            (
                str(item.get("channelId") or item.get("channelType") or ""),
                str(item.get("objectType") or ""),
                str(item.get("objectId") or ""),
                int(item.get("updatedAt") or item.get("createdAt") or now_ts()),
                str(item.get("status") or ""),
            ),
        )
        conn.commit()


def ensure_plugin_ticket_tables(conn: Any) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cmv4_audit_events(
            id BIGSERIAL PRIMARY KEY,
            time BIGINT NOT NULL,
            actor TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL,
            details TEXT NOT NULL DEFAULT '',
            admin_only INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT 'system'
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS applications(
            id TEXT PRIMARY KEY,
            election_id TEXT,
            applicant_uuid TEXT,
            applicant_name TEXT,
            statement TEXT,
            submitted_at INTEGER,
            status TEXT DEFAULT 'PENDING',
            reviewed_by TEXT DEFAULT '',
            reviewed_at INTEGER DEFAULT 0,
            verdict_reason TEXT DEFAULT '',
            visible_in_game INTEGER DEFAULT 1,
            deleted_by TEXT DEFAULT '',
            deleted_at INTEGER DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS admin_requests(
            id TEXT PRIMARY KEY,
            player_uuid TEXT,
            player_name TEXT,
            message TEXT,
            status TEXT DEFAULT 'OPEN',
            created_at INTEGER DEFAULT 0,
            updated_at INTEGER DEFAULT 0,
            assigned_to TEXT DEFAULT '',
            closed_by TEXT DEFAULT '',
            close_reason TEXT DEFAULT '',
            snapshot TEXT DEFAULT ''
        )
        """
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv7_applications_election_status ON applications(election_id,status,deleted_at)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cmv7_admin_requests_status_created ON admin_requests(status,created_at DESC)")


def active_or_latest_election_id_pg(conn: Any) -> str:
    try:
        row = conn.execute(
            """
            SELECT id
            FROM elections
            ORDER BY CASE WHEN status IN ('DRAFT','APPLICATIONS_OPEN','APPLICATIONS_CLOSED','BALLOTS_OPEN','VOTING_OPEN','COUNTING','SECOND_ROUND_REQUIRED') THEN 0 ELSE 1 END,
                     COALESCE(started_at,0) DESC
            LIMIT 1
            """
        ).fetchone()
        return str(row["id"] or "") if row else ""
    except Exception:
        return ""


def application_status_pg(status: str) -> str:
    value = str(status or "pending").strip().lower()
    if value == "approved":
        return "APPROVED"
    if value in {"rejected", "denied"}:
        return "DENIED"
    if value in {"withdrawn", "archived", "deleted"}:
        return "ARCHIVED"
    return "PENDING"


def report_status_pg(status: str) -> str:
    value = str(status or "open").strip().lower()
    if value in {"in_progress", "investigation"}:
        return "IN_PROGRESS"
    if value in {"closed", "answered", "rejected"}:
        return "CLOSED"
    return "OPEN"


def mirror_application_to_postgres(item: dict[str, Any], actor: str) -> None:
    with auth_conn() as conn:
        ensure_plugin_ticket_tables(conn)
        submitted = int(item.get("createdAt") or now_ts())
        conn.execute(
            """
            INSERT INTO applications(id,election_id,applicant_uuid,applicant_name,statement,submitted_at,status,reviewed_by,reviewed_at,verdict_reason,visible_in_game,deleted_by,deleted_at)
            VALUES(%s,%s,%s,%s,%s,%s,%s,'',0,'',1,'',0)
            ON CONFLICT(id) DO UPDATE SET
                applicant_uuid=excluded.applicant_uuid,
                applicant_name=excluded.applicant_name,
                statement=excluded.statement,
                status=excluded.status
            """,
            (
                str(item.get("id") or ""),
                active_or_latest_election_id_pg(conn),
                str(item.get("uuid") or ""),
                str(item.get("player") or "unknown"),
                str(item.get("why") or item.get("message") or application_description(item)),
                submitted,
                application_status_pg(str(item.get("status") or "pending")),
            ),
        )
        conn.execute("INSERT INTO cmv4_audit_events(time,actor,action,details,admin_only,source) VALUES(%s,%s,%s,%s,1,'admin-web')",
                     (now_ts(), actor, "WEB_APPLICATION_MIRROR", str(item.get("id") or "")))
        conn.commit()


def mirror_report_to_postgres(item: dict[str, Any], actor: str) -> None:
    with auth_conn() as conn:
        ensure_plugin_ticket_tables(conn)
        created = int(item.get("createdAt") or now_ts())
        conn.execute(
            """
            INSERT INTO admin_requests(id,player_uuid,player_name,message,status,created_at,updated_at,assigned_to,closed_by,close_reason,snapshot)
            VALUES(%s,%s,%s,%s,%s,%s,%s,'','','',%s)
            ON CONFLICT(id) DO UPDATE SET
                player_uuid=excluded.player_uuid,
                player_name=excluded.player_name,
                message=excluded.message,
                status=excluded.status,
                updated_at=excluded.updated_at,
                snapshot=excluded.snapshot
            """,
            (
                str(item.get("id") or ""),
                str(item.get("reporter_uuid") or item.get("uuid") or ""),
                str(item.get("reporter") or item.get("player") or "unknown"),
                str(item.get("message") or report_description(item)),
                report_status_pg(str(item.get("status") or "open")),
                created,
                int(item.get("updatedAt") or created),
                json.dumps(redact_value(item), ensure_ascii=False, sort_keys=True),
            ),
        )
        conn.execute("INSERT INTO cmv4_audit_events(time,actor,action,details,admin_only,source) VALUES(%s,%s,%s,%s,1,'admin-web')",
                     (now_ts(), actor, "WEB_REPORT_MIRROR", str(item.get("id") or "")))
        conn.commit()


def add_discord_outbox(
    kind: str,
    object_id: str,
    title: str,
    description: str,
    player: str = "",
    metadata: Optional[dict[str, Any]] = None,
    *,
    object_type: Optional[str] = None,
    status: str = "pending",
    action: str = "publish",
) -> dict[str, Any]:
    resolved_type = object_type or ("application" if kind in {"application", "candidate_application"} else "report" if kind == "report" else kind)
    channel_type = "applications" if resolved_type == "application" else "reports" if resolved_type == "report" else kind
    item = {
        "id": secrets.token_hex(8),
        "kind": kind,
        "objectType": resolved_type,
        "objectId": object_id,
        "action": action,
        "channelType": channel_type,
        "title": title[:180],
        "description": description[:1800],
        "player": player,
        "metadata": redact_value(metadata or {}),
        "status": status,
        "retryCount": 0,
        "createdAt": now_ts(),
        "adminUrl": discord_object_url(resolved_type, object_id),
    }
    save_collection_item_sync(DISCORD_OUTBOX_FILE, "outbox", item, 1000)
    sync_discord_outbox_status(item)
    return item


def update_discord_outbox_sync(item_id: str, patch: DiscordOutboxPatchIn) -> dict[str, Any]:
    outbox = load_collection_sync(DISCORD_OUTBOX_FILE, "outbox", 1000)
    for item in outbox:
        if item.get("id") == item_id:
            item["status"] = patch.status
            if patch.message_id is not None:
                item["messageId"] = patch.message_id
            if patch.channel_id is not None:
                item["channelId"] = patch.channel_id
            if patch.thread_id is not None:
                item["threadId"] = patch.thread_id
            if patch.retry_count is not None:
                item["retryCount"] = max(0, int(patch.retry_count))
            if patch.next_retry_at is not None:
                item["nextRetryAt"] = int(patch.next_retry_at)
            if patch.error is not None:
                item["error"] = public_error_message(patch.error)
            elif patch.status not in {"error", "retry"}:
                item.pop("error", None)
            item["updatedAt"] = now_ts()
            save_collection_item_sync(DISCORD_OUTBOX_FILE, "outbox", item, 1000)
            sync_discord_outbox_status(item)
            return item
    raise HTTPException(status_code=404, detail="Discord outbox item not found")


def latest_discord_delivery(object_type: str, object_id: str) -> dict[str, Any]:
    if pg_ready():
        try:
            outbox_rows = pg_load_collection("outbox", 1000)
            for item in outbox_rows:
                if item.get("objectType") == object_type and str(item.get("objectId", "")) == str(object_id) and item.get("messageId"):
                    return {
                        "messageId": item.get("messageId"),
                        "channelId": item.get("channelId"),
                        "threadId": item.get("threadId"),
                        "status": item.get("status"),
                    }
            with auth_conn() as conn:
                ensure_v4_schema(conn)
                row = conn.execute(
                    """
                    SELECT channel_id,status
                    FROM discord_notifications_log
                    WHERE object_type=%s AND object_id=%s
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                    (object_type, str(object_id)),
                ).fetchone()
                conn.commit()
            if row and row["channel_id"]:
                return {"channelId": row["channel_id"], "status": row["status"]}
        except Exception:
            pass
    outbox = read_json(DISCORD_OUTBOX_FILE, [])
    if not isinstance(outbox, list):
        return {}
    for item in outbox:
        if item.get("objectType") == object_type and str(item.get("objectId", "")) == str(object_id) and item.get("messageId"):
            return {
                "messageId": item.get("messageId"),
                "channelId": item.get("channelId"),
                "threadId": item.get("threadId"),
            }
    return {}


def create_ticket_item_sync(payload: dict[str, Any], source: str) -> dict[str, Any]:
    item = dict(payload)
    item.update({"id": secrets.token_hex(6), "createdAt": int(time.time()), "status": "open", "source": source})
    save_collection_item_sync(TICKETS_FILE, "ticket", item)
    return item


def list_ticket_rows_sync(limit: int = 5000) -> list[dict[str, Any]]:
    return load_collection_sync(TICKETS_FILE, "ticket", limit)


def update_ticket_item_sync(ticket_id: str, status: str, admin_note: str) -> dict[str, Any]:
    rows = load_collection_sync(TICKETS_FILE, "ticket", 5000)
    item = find_collection_item(rows, ticket_id)
    item["status"] = status
    item["adminNote"] = admin_note
    item["updatedAt"] = int(time.time())
    save_collection_item_sync(TICKETS_FILE, "ticket", item)
    return item


def enqueue_discord_object_update(object_type: str, item: dict[str, Any], reason: str = "") -> dict[str, Any]:
    if object_type == "application":
        out = add_discord_outbox(
            "application",
            str(item.get("id", "")),
            f"Application updated: {item.get('player', 'unknown')}",
            application_description(item),
            player=str(item.get("player", "")),
            metadata={"object": item, "reason": reason},
            object_type="application",
            status="update_pending",
            action="update",
        )
    else:
        out = add_discord_outbox(
        "report",
        str(item.get("id", "")),
        f"Report updated: {item.get('reporter', 'unknown')}",
        report_description(item),
        player=str(item.get("reporter", "")),
        metadata={"object": item, "reason": reason},
        object_type="report",
        status="update_pending",
        action="update",
        )
    delivery = latest_discord_delivery(object_type, str(item.get("id", "")))
    if delivery:
        outbox = load_collection_sync(DISCORD_OUTBOX_FILE, "outbox", 1000)
        for row in outbox:
            if row.get("id") == out.get("id"):
                row.update(delivery)
                out.update(delivery)
                save_collection_item_sync(DISCORD_OUTBOX_FILE, "outbox", row, 1000)
                break
    return out


@app.post("/api/auth/login")
async def login(data: LoginIn, request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    username = data.username.strip()
    check_rate_limit(request, "auth-login-ip", limit=20, window_seconds=300)
    assert_not_locked(request, username)
    real_username, meta = resolve_admin_user(username)
    if not meta or not bool(meta.get("enabled", True)) or not verify_password_hash(str(meta.get("password_hash", "")), data.password):
        register_failed_login(request, username)
        audit_event(username, "auth.login", status="failed", details={"ip": get_client_ip(request)})
        raise HTTPException(status_code=401, detail="Неверный логин или пароль")
    access_ok, access_errors = minecraft_access_ok(real_username)
    if not access_ok:
        register_failed_login(request, username)
        audit_event(username, "auth.login", status="blocked", details={"reason": access_errors})
        raise HTTPException(status_code=403, detail="Доступ запрещён: " + "; ".join(access_errors))
    clear_failed_login(request, username)
    role = normalize_admin_role(meta.get("role"))
    if not is_panel_admin_role(role):
        raise HTTPException(status_code=403, detail="Недостаточно прав для панели")
    access_token, refresh_token = issue_admin_auth_pair(real_username, role, request, data.remember_me)
    set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
    audit_event(real_username, "auth.login", status="ok", details={"ip": get_client_ip(request)})
    append_panel_event("admin-panel", "admin_login", actor=real_username, metadata={"ip": get_client_ip(request)}, tags=["security"])
    pg_record_auth_state("admin_auth_runtime", {"mode": "postgresql-primary", "checkedAt": now_ts(), "user": real_username})
    return {
        "username": real_username,
        "role": role,
        "fullAccess": is_full_admin_role(role),
        "owner": role == "owner",
        "expiresIn": ACCESS_TOKEN_TTL_SECONDS,
        "cookieAuth": True,
    }


@app.post("/api/session/login")
async def session_login(data: PlayerLoginIn, request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    username = data.username.strip()
    check_rate_limit(request, "session-login-ip", limit=20, window_seconds=300)
    assert_not_locked(request, "session:" + username)

    real_username, meta = resolve_admin_user(username)
    if meta and bool(meta.get("enabled", True)) and verify_password_hash(str(meta.get("password_hash", "")), data.password):
        access_ok, access_errors = minecraft_access_ok(real_username)
        if not access_ok:
            register_failed_login(request, "session:" + username)
            audit_event(username, "auth.session_login", status="blocked", details={"reason": access_errors})
            raise HTTPException(status_code=403, detail="Доступ запрещён: " + "; ".join(access_errors))
        role = normalize_admin_role(meta.get("role"))
        if not is_panel_admin_role(role):
            register_failed_login(request, "session:" + username)
            raise HTTPException(status_code=403, detail="Недостаточно прав для панели")
        clear_failed_login(request, "session:" + username)
        access_token, refresh_token = issue_admin_auth_pair(real_username, role, request, data.remember_me)
        set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
        audit_event(real_username, "auth.session_login", status="ok", details={"role": role, "ip": get_client_ip(request)})
        return {
            "username": real_username,
            "role": role,
            "fullAccess": is_full_admin_role(role),
            "owner": role == "owner",
            "expiresIn": ACCESS_TOKEN_TTL_SECONDS,
            "cookieAuth": True,
        }

    with auth_conn() as conn:
        ensure_v4_schema(conn)
        account = player_account_by_username(conn, username)
        if account and bool(int(account.get("enabled") or 0)) and verify_password_hash(str(account.get("password_hash") or ""), data.password):
            conn.execute("UPDATE site_accounts SET last_login_at=%s WHERE id=%s", (now_ts(), account["id"]))
            conn.execute(
                "INSERT INTO auth_effects_disable_audit(actor,target_uuid,created_at,details) VALUES(%s,%s,%s,%s)",
                (username, str(account.get("minecraft_uuid") or ""), now_ts(), "session login passed staged auth/runtime checks"),
            )
            conn.commit()
            clear_failed_login(request, "session:" + username)
            pg_record_auth_state("player_auth_runtime", {"mode": "postgresql-primary", "checkedAt": now_ts(), "latestLoginUser": username})
            access_token, refresh_token = issue_player_auth_pair(account, request, remember_me=data.remember_me)
            set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
            account["last_login_at"] = now_ts()
            audit_event(username, "auth.session_login", status="ok", details={"role": "player", "ip": get_client_ip(request)})
            return {"role": "player", "expiresIn": ACCESS_TOKEN_TTL_SECONDS, "cookieAuth": True, "account": public_player_account(account)}

    register_failed_login(request, "session:" + username)
    audit_event(username, "auth.session_login", status="failed", details={"ip": get_client_ip(request)})
    raise HTTPException(status_code=401, detail="Неверный логин или пароль")


@app.get("/api/auth/csrf")
async def auth_csrf(request: Request, response: Response) -> dict[str, Any]:
    set_csrf_cookie(response, request)
    return {"ok": True, "cookie": CSRF_COOKIE_NAME, "header": CSRF_HEADER_NAME}


@app.get("/api/csrf")
async def csrf_alias(request: Request, response: Response) -> dict[str, Any]:
    return await auth_csrf(request, response)


@app.post("/api/auth/refresh")
async def auth_refresh(request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    refresh_token = request_refresh_token(request)
    if not refresh_token:
        raise HTTPException(status_code=401, detail="Refresh-сессия отсутствует")
    result = await bg(rotate_auth_pair_from_refresh_sync, refresh_token, request, "admin")
    set_auth_cookies_for_request(
        response,
        request,
        str(result.pop("accessToken")),
        str(result.pop("refreshToken")),
        bool(result.pop("rememberMe", False)),
    )
    return result


@app.post("/api/auth/logout")
async def logout(request: Request, response: Response, authorization: str = Header(default="")) -> dict[str, Any]:
    token = request_auth_token(request, authorization)
    refresh_token = request_refresh_token(request)
    actor = ""
    if token:
        try:
            payload = decode_signed_token(token)
            actor = str(payload.get("sub", ""))
            revoke_session(str(payload.get("jti", "")))
        except Exception:
            pass
    if refresh_token:
        try:
            payload = decode_signed_token(refresh_token)
            revoke_refresh_session(str(payload.get("jti", "")))
        except Exception:
            pass
    clear_auth_cookies(response, request)
    audit_event(actor or "unknown", "auth.logout")
    return {"ok": True}


@app.get("/api/session/me")
async def session_me(request: Request, authorization: str = Header(default="")) -> dict[str, Any]:
    token = request_auth_token(request, authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Нужна авторизация")
    payload = verify_token(token)
    role = str(payload.get("role") or "").strip().lower()
    if role == "player":
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            account = player_account_by_id(conn, str(payload.get("sub") or ""))
            if not bool(int(account.get("enabled") or 0)):
                raise HTTPException(status_code=403, detail="Аккаунт игрока отключён")
            conn.commit()
        return {
            "authenticated": True,
            "kind": "player",
            "role": "player",
            "username": str(account.get("username") or ""),
            "homeRoute": "cabinet",
            "account": public_player_account(account),
        }
    username = str(payload.get("sub") or "")
    real_username, meta = resolve_admin_user(username)
    if not meta or not bool(meta.get("enabled", True)):
        raise HTTPException(status_code=403, detail="Доступ к админке отозван")
    current_role = normalize_admin_role(meta.get("role"))
    if not is_panel_admin_role(current_role):
        raise HTTPException(status_code=403, detail="Недостаточно прав для панели")
    access_ok, access_errors = minecraft_access_ok(real_username)
    if not access_ok:
        raise HTTPException(status_code=403, detail="Minecraft-доступ отозван: " + "; ".join(access_errors))
    return {
        "authenticated": True,
        "kind": "panel",
        "role": current_role,
        "username": real_username,
        "homeRoute": "dashboard",
        "fullAccess": is_full_admin_role(current_role),
        "owner": current_role == "owner",
    }


@app.get("/api/auth/me")
async def me(context: dict[str, Any] = Depends(require_panel_admin_context)) -> dict[str, Any]:
    username = str(context.get("username") or "")
    access_ok, access_errors = minecraft_access_ok(username)
    meta = current_admin_users().get(username, {})
    role = normalize_admin_role(meta.get("role") or context.get("role"))
    return {
        "username": username,
        "role": role,
        "fullAccess": is_full_admin_role(role),
        "owner": role == "owner",
        "minecraftAccessOk": access_ok,
        "minecraftAccessErrors": access_errors,
    }


@app.get("/api/public/config")
async def public_config() -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_site_config_sync)}


@app.get("/api/public/cms")
async def public_cms() -> dict[str, Any]:
    return {"ok": True, "data": await bg(read_site_cms_sync, False)}


@app.get("/api/public/status")
async def public_status() -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_site_status_sync)}


@app.get("/api/public/president-budget")
async def public_president_budget() -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_president_budget_payload_sync)}


@app.get("/api/public/president-budget/history")
async def public_president_budget_history(limit: int = 20, offset: int = 0) -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_president_budget_history_sync, limit, offset)}


@app.get("/api/public/president")
async def public_president() -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_president_profile_sync)}


@app.get("/api/public/president/skin/body")
async def public_president_skin_body(uuid: str = Query(default="")) -> Response:
    safe_uuid = str(uuid or "").strip().lower()
    if not re.fullmatch(r"[0-9a-f-]{32,36}", safe_uuid):
        raise HTTPException(status_code=404, detail="Президентский скин недоступен")
    if httpx is None:
        raise HTTPException(status_code=503, detail="Skin proxy is unavailable")
    urls = [
        f"https://crafatar.com/renders/body/{safe_uuid}?overlay=true&scale=6",
        f"https://mc-heads.net/body/{safe_uuid}/right",
    ]
    timeout = httpx.Timeout(8.0, connect=4.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        for source_url in urls:
            try:
                remote = await client.get(source_url)
            except Exception:
                continue
            if remote.status_code != 200:
                continue
            content_type = remote.headers.get("content-type", "image/png")
            if "image" not in content_type:
                continue
            return Response(
                content=remote.content,
                media_type=content_type,
                headers={"Cache-Control": "public, max-age=600"},
            )
    raise HTTPException(status_code=404, detail="Президентский скин не найден")


@app.get("/api/public/modpack")
async def public_modpack() -> dict[str, Any]:
    return {"ok": True, "data": await bg(public_modpack_sync)}


@app.get("/api/public/shop/ar-items")
async def public_ar_shop_catalog() -> dict[str, Any]:
    catalog = await bg(ar_catalog_snapshot_sync)
    items: list[dict[str, Any]] = []
    for row in catalog.get("items", []):
        item = dict(row)
        for field in ("lore", "base_material", "effect_profile_id", "visual_effect_id", "custom_model_data", "source"):
            item.pop(field, None)
        items.append(item)
    return {"ok": True, "data": {"count": len(catalog.get("items", [])), "items": items}}


@app.get("/api/public/shop/donation-items")
async def public_donation_shop_catalog() -> dict[str, Any]:
    catalog = await bg(donation_catalog_snapshot_sync)
    items: list[dict[str, Any]] = []
    for row in catalog.get("items", []):
        item = dict(row)
        item["item_url"] = donation_item_page_url(str(item.get("item_id") or ""))
        for field in ("lore", "base_material", "effect_profile_id", "visual_effect_id", "custom_model_data", "source"):
            item.pop(field, None)
        items.append(item)
    return {
        "ok": True,
        "data": {
            "catalogVersion": catalog.get("catalogVersion", 0),
            "updatedAt": catalog.get("updatedAt", 0),
            "count": len(catalog.get("items", [])),
            "items": items,
        },
    }


@app.post("/api/player/register")
async def player_register(data: PlayerRegisterIn, request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    username = data.username.strip()
    if not valid_site_username(username):
        raise HTTPException(status_code=400, detail="Укажи корректный логин")
    if is_reserved_admin_username(username):
        raise HTTPException(status_code=409, detail="Этот логин зарезервирован администрацией")
    ok, reason = password_policy_ok(data.password)
    if not ok:
        raise HTTPException(status_code=400, detail=reason)
    minecraft_name = (data.minecraft_name or "").strip()
    if minecraft_name and not valid_minecraft_name(minecraft_name):
        raise HTTPException(status_code=400, detail="Укажи корректный Minecraft-ник")
    minecraft_uuid = offline_uuid_for_name(minecraft_name) if minecraft_name else ""
    registration_ip = get_client_ip(request)
    account_id = secrets.token_hex(16)
    now = now_ts()
    auto_whitelist = False
    known_minecraft_identity = False
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        if player_account_by_username(conn, username):
            raise HTTPException(status_code=409, detail="Такой логин уже занят")
        if minecraft_name and player_account_by_minecraft_name(conn, minecraft_name):
            raise HTTPException(status_code=409, detail="Этот Minecraft-ник уже привязан. Используйте восстановление доступа.")
        same_ip = conn.execute("SELECT COUNT(*) AS c FROM site_accounts WHERE registration_ip=%s", (registration_ip,)).fetchone()
        if int(row_get(same_ip, "c", 0) or 0) >= 5:
            conn.commit()
            await bg(create_ip_alert_sync, registration_ip, username, minecraft_name, "site-account-limit", {"limit": 5, "stage": "register"})
            raise HTTPException(status_code=400, detail="Ошибка регистрации. Проверьте данные и попробуйте позже.")
        conn.execute(
            """
            INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip)
            VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,%s,%s)
            """,
            # Registration may request automatic whitelist access, but it must
            # never grant ownership of an existing Minecraft identity or bank.
            (account_id, username, username.lower(), make_password_hash(data.password), "", "", now, now, now, registration_ip),
        )
        conn.execute(
            "INSERT INTO auth_effects_disable_audit(actor,target_uuid,created_at,details) VALUES(%s,%s,%s,%s)",
            (username, "", now, "site registration entered staged auth flow"),
        )
        if minecraft_name:
            known_minecraft_identity = minecraft_identity_seen_on_server(minecraft_name, minecraft_uuid) or minecraft_identity_is_bound_to_site(
                conn, minecraft_uuid, minecraft_name
            )
            if not known_minecraft_identity:
                auto_whitelist = False
                conn.execute(
                    """
                    INSERT INTO whitelist_requests(id,site_account_id,minecraft_uuid,minecraft_name,request_ip,status,created_at,updated_at,approved_at,approved_by,note)
                    VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s,0,'','Manual approval required; Minecraft ownership must be proven in-game')
                    """,
                    (f"wl-{secrets.token_hex(10)}", account_id, minecraft_uuid, minecraft_name, registration_ip, now, now),
                )
        conn.commit()
    whitelist_state = "PENDING" if minecraft_name and not known_minecraft_identity else "NOT_REQUESTED"
    pg_record_auth_state("player_auth_runtime", {"mode": "postgresql-primary", "checkedAt": now, "latestRegisteredUser": username})
    account_payload = {
        "id": account_id,
        "username": username,
        "minecraft_uuid": "",
        "minecraft_name": "",
        "enabled": 1,
        "created_at": now,
        "updated_at": now,
        "last_login_at": now,
    }
    access_token, refresh_token = issue_player_auth_pair(
        account_payload,
        request,
        remember_me=data.remember_me,
    )
    set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
    return {
        "role": "player",
        "expiresIn": ACCESS_TOKEN_TTL_SECONDS,
        "cookieAuth": True,
        "account": public_player_account(account_payload),
        "minecraftLinkRequired": bool(minecraft_name),
        "whitelistState": whitelist_state,
    }


@app.post("/api/player/login")
async def player_login(data: PlayerLoginIn, request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    username = data.username.strip()
    check_rate_limit(request, "player-login-ip", limit=20, window_seconds=300)
    assert_not_locked(request, "player:" + username)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        account = player_account_by_username(conn, username)
        if not account or not bool(int(account.get("enabled") or 0)) or not verify_password_hash(str(account.get("password_hash") or ""), data.password):
            register_failed_login(request, "player:" + username)
            raise HTTPException(status_code=401, detail="Неверный логин или пароль")
        conn.execute("UPDATE site_accounts SET last_login_at=%s WHERE id=%s", (now_ts(), account["id"]))
        conn.execute(
            "INSERT INTO auth_effects_disable_audit(actor,target_uuid,created_at,details) VALUES(%s,%s,%s,%s)",
            (username, str(account.get("minecraft_uuid") or ""), now_ts(), "player login passed staged auth/runtime checks"),
        )
        conn.commit()
    clear_failed_login(request, "player:" + username)
    pg_record_auth_state("player_auth_runtime", {"mode": "postgresql-primary", "checkedAt": now_ts(), "latestLoginUser": username})
    access_token, refresh_token = issue_player_auth_pair(account, request, remember_me=data.remember_me)
    set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
    account["last_login_at"] = now_ts()
    return {"role": "player", "expiresIn": ACCESS_TOKEN_TTL_SECONDS, "cookieAuth": True, "account": public_player_account(account)}


@app.post("/api/player/recovery/start")
async def player_recovery_start(data: PlayerRecoveryStartIn, request: Request) -> dict[str, Any]:
    check_rate_limit(request, "player-recovery-start", limit=6, window_seconds=300)
    return await bg(create_player_recovery_code_sync, data.minecraft_name.strip())


@app.post("/api/player/recovery/confirm")
async def player_recovery_confirm(data: PlayerRecoveryConfirmIn, request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    check_rate_limit(request, "player-recovery-confirm", limit=8, window_seconds=300)
    result = await bg(confirm_player_recovery_code_sync, data.minecraft_name.strip(), data.code.strip().upper(), data.new_password)
    account = dict(result.get("account") or {})
    access_token, refresh_token = issue_player_auth_pair(
        {
            "id": account.get("id"),
            "username": account.get("username"),
            "minecraft_uuid": account.get("minecraftUuid") or "",
            "minecraft_name": account.get("minecraftName") or "",
        },
        request,
        remember_me=data.remember_me,
    )
    set_auth_cookies_for_request(response, request, access_token, refresh_token, data.remember_me)
    return {"ok": True, "role": "player", "expiresIn": ACCESS_TOKEN_TTL_SECONDS, "cookieAuth": True, "account": account}


@app.post("/api/player/refresh")
async def player_refresh(request: Request, response: Response) -> dict[str, Any]:
    require_secure_auth_transport(request)
    refresh_token = request_refresh_token(request)
    if not refresh_token:
        raise HTTPException(status_code=401, detail="Refresh-сессия отсутствует")
    result = await bg(rotate_auth_pair_from_refresh_sync, refresh_token, request, "player")
    set_auth_cookies_for_request(
        response,
        request,
        str(result.pop("accessToken")),
        str(result.pop("refreshToken")),
        bool(result.pop("rememberMe", False)),
    )
    return result


@app.get("/api/player/me")
async def player_me(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    base = public_player_account(account)
    state = await bg(
        read_player_whitelist_state_sync,
        str(account.get("id") or ""),
        str(account.get("minecraft_uuid") or ""),
        str(account.get("minecraft_name") or ""),
    )
    base.update(state)
    return {"account": base}


@app.post("/api/player/account/username")
async def player_change_username(
    data: PlayerUsernameChangeIn,
    request: Request,
    account: dict[str, Any] = Depends(require_player),
) -> dict[str, Any]:
    check_rate_limit(request, "player-account-username", limit=6, window_seconds=300)
    new_username = data.new_username.strip()
    if not valid_site_username(new_username):
        raise HTTPException(status_code=400, detail="Укажи корректный логин")
    if is_reserved_admin_username(new_username):
        raise HTTPException(status_code=409, detail="Этот логин зарезервирован администрацией")
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        current = player_account_by_id(conn, str(account.get("id") or ""))
        if not verify_password_hash(str(current.get("password_hash") or ""), data.current_password):
            raise HTTPException(status_code=401, detail="Текущий пароль указан неверно")
        existing = player_account_by_username(conn, new_username)
        if existing and str(existing.get("id") or "") != str(current.get("id") or ""):
            raise HTTPException(status_code=409, detail="Такой логин уже занят")
        now = now_ts()
        conn.execute(
            "UPDATE site_accounts SET username=%s,username_norm=%s,updated_at=%s WHERE id=%s",
            (new_username, new_username.lower(), now, current["id"]),
        )
        conn.commit()
        current["username"] = new_username
        current["updated_at"] = now
    actor = str(account.get("username") or "player")
    audit_event(actor, "player.account.username_change", target=new_username, details={"accountId": str(account.get("id") or "")})
    append_panel_event("player", "account_username_changed", actor=actor, target=new_username, metadata={"accountId": str(account.get("id") or "")}, tags=["player", "account"])
    return {"ok": True, "account": public_player_account(current)}


@app.post("/api/player/account/password")
async def player_change_password(
    data: PlayerPasswordChangeIn,
    request: Request,
    account: dict[str, Any] = Depends(require_player),
) -> dict[str, Any]:
    check_rate_limit(request, "player-account-password", limit=8, window_seconds=300)
    ok, reason = password_policy_ok(data.new_password)
    if not ok:
        raise HTTPException(status_code=400, detail=reason)
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        current = player_account_by_id(conn, str(account.get("id") or ""))
        if not verify_password_hash(str(current.get("password_hash") or ""), data.current_password):
            raise HTTPException(status_code=401, detail="Текущий пароль указан неверно")
        now = now_ts()
        conn.execute(
            "UPDATE site_accounts SET password_hash=%s,updated_at=%s WHERE id=%s",
            (make_password_hash(data.new_password), now, current["id"]),
        )
        conn.commit()
    actor = str(account.get("username") or "player")
    audit_event(actor, "player.account.password_change", target=str(account.get("id") or ""), details={"changedAt": now_ts()})
    append_panel_event("player", "account_password_changed", actor=actor, target=str(account.get("id") or ""), metadata={"username": actor}, tags=["player", "security"])
    return {"ok": True}


@app.post("/api/player/whitelist/request")
async def player_whitelist_request(request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    row = await bg(create_whitelist_request_sync, account, get_client_ip(request))
    return {"ok": True, "request": row}


@app.post("/api/player/link/request")
async def player_link_request(request: Request, data: PlayerLinkRequestIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-link-request", limit=6, window_seconds=300)
    return await bg(create_link_code_sync, account, data.minecraft_name.strip())


@app.post("/api/player/link/confirm")
async def player_link_confirm(request: Request, data: PlayerLinkConfirmIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-link-confirm", limit=8, window_seconds=300)
    return await bg(confirm_link_code_sync, account, data.code)


@app.get("/api/player/bank")
async def player_bank(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(player_bank_overview_sync, account)


@app.get("/api/player/bank/recipients")
async def player_bank_recipients(q: str = "", account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(list_player_bank_recipients_sync, account, q)


@app.post("/api/player/bank/pin")
async def player_bank_pin(data: PlayerPinSetIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    if not account.get("minecraft_uuid"):
        raise HTTPException(status_code=409, detail="Minecraft account is not linked")
    return await bg(set_player_pin_sync, account, data)


@app.post("/api/player/bank/transfer")
async def player_bank_transfer(data: PlayerBankTransferIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(transfer_player_bank_sync, account, data)


@app.get("/api/player/bank/treasury")
async def player_bank_treasury(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(treasury_bank_profile_sync, account)


@app.get("/api/player/elections/tax")
async def player_elections_tax(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(player_election_tax_profile_sync, account)


@app.post("/api/player/elections/tax/pay")
async def player_elections_tax_pay(data: PlayerElectionTaxPayIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return await bg(pay_player_election_tax_sync, account, data)


@app.get("/api/player/reports")
async def player_reports(request: Request, status: str = "", account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-reports-list", limit=30, window_seconds=60)
    rows = await bg(load_collection_sync, DISCORD_REPORTS_FILE, "report", 5000)
    minecraft_uuid = str(account.get("minecraft_uuid") or "").strip()
    username = str(account.get("username") or "").strip().lower()
    account_id = str(account.get("id") or "").strip()
    filtered: list[dict[str, Any]] = []
    for item in rows:
        item_reporter_uuid = str(item.get("reporter_uuid") or "").strip()
        item_reporter = str(item.get("reporter") or "").strip().lower()
        metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        item_account_id = str(metadata.get("siteAccountId") or "").strip()
        if minecraft_uuid and item_reporter_uuid == minecraft_uuid:
            pass
        elif account_id and item_account_id == account_id:
            pass
        elif username and item_reporter == username:
            pass
        else:
            continue
        if status and str(item.get("status") or "").strip().lower() != status.strip().lower():
            continue
        filtered.append(public_player_report(normalized_report_row(item)))
    filtered.sort(key=lambda row: int(row.get("updatedAt") or row.get("createdAt") or 0), reverse=True)
    return {"reports": filtered, "count": len(filtered)}


@app.post("/api/player/reports")
async def player_create_report(request: Request, data: PlayerSupportReportIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-reports-create", limit=6, window_seconds=300)
    reporter_name = str(account.get("minecraft_name") or account.get("username") or "").strip()
    reporter_uuid = str(account.get("minecraft_uuid") or "").strip() or f"site:{str(account.get('id') or '').strip()}"
    payload = DiscordReportIn(
        reporter=reporter_name or "player",
        reporter_uuid=reporter_uuid[:80],
        target=str(data.target or "").strip()[:64] or None,
        message=str(data.message or "").strip(),
        world=str(data.world or "").strip()[:80] or None,
        severity=str(data.severity or "normal").strip().lower()[:40] or "normal",
        # Player-site reports intentionally contain only the player's text.
        # Technical snapshots are accepted solely from the plugin-key endpoint.
        attached_events=[],
        metadata={
            "sourcePage": "player-cabinet",
            "siteAccountId": str(account.get("id") or "").strip(),
            "siteUsername": str(account.get("username") or "").strip(),
        },
    )
    item = await bg(create_report_sync, payload, "player-site", str(account.get("username") or reporter_name or "player"))
    audit_event(str(account.get("username") or reporter_name or "player"), "player.report.create", target=item["id"], details={"severity": item.get("severity"), "target": item.get("target")})
    append_panel_event("player", "report_created", actor=str(account.get("username") or reporter_name or "player"), target=item["id"], metadata={"severity": item.get("severity"), "target": item.get("target")}, tags=["player", "report"])
    return {"ok": True, "report": item}


@app.get("/api/status")
async def status(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    online, latency = await bg(tcp_online, MC_HOST, MC_PORT)
    web_online, web_latency = await bg(tcp_online, "127.0.0.1", 18080)
    backend_online, backend_latency = await bg(tcp_online, "127.0.0.1", 8090)
    voice = await bg(udp_probe, MC_HOST, 24454)
    rcon_ok = False
    list_text = ""
    tps_text = ""
    mspt_text = ""
    if RCON_PASSWORD:
        try:
            list_text = await rcon("list")
            rcon_ok = True
        except Exception as exc:
            list_text = f"RCON error: {exc}"
        tps_text = await rcon("tps") if rcon_ok else ""
        mspt_text = await rcon("mspt") if rcon_ok else ""
    sys_info: dict[str, Any] = {}
    if psutil:
        sys_info = await bg(lambda: {
            "cpu": psutil.cpu_percent(interval=0.05),
            "ram": psutil.virtual_memory().percent,
            "disk": psutil.disk_usage(str(MC_SERVER_DIR if MC_SERVER_DIR.exists() else APP_ROOT)).percent,
            "load": os.getloadavg() if hasattr(os, "getloadavg") else None,
        })
    return {
        "minecraftOnline": online,
        "latencyMs": latency,
        "rconOk": rcon_ok,
        "list": list_text,
        "playersOnline": parse_online_players(list_text),
        "tps": tps_text,
        "mspt": mspt_text,
        "system": sys_info,
        "ports": {
            "minecraftTcp25565": {"online": online, "latencyMs": latency},
            "voiceUdp24454": voice,
            "adminHttp18080": {"online": web_online, "latencyMs": web_latency},
            "backend8090": {"online": backend_online, "latencyMs": backend_latency},
        },
        "time": int(time.time()),
    }


@app.get("/api/server/files")
async def server_files(_: str = Depends(require_admin)) -> dict[str, Any]:
    files = []
    for path in ["server.properties", "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json", "usercache.json"]:
        p = MC_SERVER_DIR / path
        files.append({"name": path, "exists": p.exists(), "size": p.stat().st_size if p.exists() else 0, "modified": int(p.stat().st_mtime) if p.exists() else None})
    return {"files": files}


@app.get("/api/server/stats")
async def server_stats(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    data = await bg(server_stats_sync)
    rcon_data: dict[str, Any] = {"configured": bool(RCON_PASSWORD), "ok": False}
    if RCON_PASSWORD:
        try:
            list_text = await rcon("list")
            rcon_data.update({
                "ok": True,
                "list": list_text,
                "playersOnline": parse_online_players(list_text),
                "tps": await rcon("tps"),
                "mspt": await rcon("mspt"),
            })
        except Exception as exc:
            rcon_data["error"] = public_error_message(exc)
    data["rcon"] = rcon_data
    data.setdefault("minecraft", {})["playersOnline"] = rcon_data.get("playersOnline", [])
    data.setdefault("minecraft", {})["tps"] = rcon_data.get("tps", "")
    data.setdefault("minecraft", {})["mspt"] = rcon_data.get("mspt", "")
    return data


@app.get("/api/performance/readiness")
async def performance_readiness(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(performance_readiness_sync)


@app.get("/api/anticheat/status")
async def anticheat_status(limit: int = 160, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(anticheat_status_sync, max(20, min(limit, 500)))


@app.get("/api/server/properties")
async def server_properties(_: str = Depends(require_admin)) -> dict[str, Any]:
    return {"properties": await bg(read_server_properties)}


@app.patch("/api/server/properties")
async def patch_server_properties(
    data: PropertiesPatchIn,
    request: Request,
    context: dict[str, Any] = Depends(require_panel_admin_context),
) -> dict[str, Any]:
    if not bool(context.get("fullAccess")):
        raise HTTPException(status_code=403, detail="Нужны полные права администратора")
    keys = {str(key) for key in data.values.keys()}
    if keys & OWNER_ONLY_SERVER_PROPERTY_KEYS and normalize_admin_role(context.get("role")) != "owner":
        raise HTTPException(status_code=403, detail="Эти ключи server.properties доступны только владельцу панели")
    username = str(context.get("username") or "")
    require_sensitive_confirm(request, "SERVER_PROPERTIES")
    result = await bg(write_server_properties, data.values)
    audit_event(username, "server.properties.patch", target="server.properties", details={"keys": sorted(data.values.keys())})
    append_panel_event("admin-panel", "server_properties_changed", actor=username, metadata={"keys": sorted(data.values.keys())}, tags=["server"])
    return result


@app.post("/api/server/control")
async def server_control(data: ServerControlIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    action = data.action.strip().lower()
    if action in {"stop", "restart", "say"}:
        require_sensitive_confirm(request, f"SERVER_{action.upper()}")
    if action in {"start", "stop", "restart", "status"}:
        result = await bg(run_systemctl, action)
        audit_event(username, "server.control", target=MINECRAFT_SERVICE, details={"action": action, "returncode": result.get("returncode")})
        append_panel_event("admin-panel", "server_control", actor=username, target=MINECRAFT_SERVICE, metadata={"action": action}, tags=["server"])
        return result
    if action == "save-all":
        response = await rcon("save-all")
        audit_event(username, "server.rcon_template", target="save-all")
        return {"response": response}
    if action == "say":
        message = data.message or "Сообщение администрации"
        response = await rcon(f"say {message}")
        audit_event(username, "server.broadcast", details={"length": len(message)})
        append_panel_event("admin-panel", "server_broadcast", actor=username, metadata={"length": len(message)}, tags=["server", "chat"])
        return {"response": response}
    raise HTTPException(status_code=400, detail="Недоступное действие сервера")




def rcon_quick(command: str) -> str:
    """Small internal RCON client for maintenance commands like save-all."""
    import os as _os
    import socket as _socket
    import struct as _struct

    host = _os.getenv("RCON_HOST", "127.0.0.1")
    port = int(_os.getenv("RCON_PORT", "25575"))
    password = _os.getenv("RCON_PASSWORD", "")

    if not password:
        return "RCON password is empty"

    def packet(req_id: int, typ: int, payload: str) -> bytes:
        body = _struct.pack("<ii", req_id, typ) + payload.encode("utf-8") + b"\x00\x00"
        return _struct.pack("<i", len(body)) + body

    def recv(sock):
        raw_len = sock.recv(4)
        if len(raw_len) < 4:
            return -1, -1, ""
        length = _struct.unpack("<i", raw_len)[0]
        data = b""
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                break
            data += chunk
        if len(data) < 8:
            return -1, -1, ""
        req_id, typ = _struct.unpack("<ii", data[:8])
        payload = data[8:-2]
        try:
            text = payload.decode("utf-8")
        except Exception:
            text = payload.decode("utf-8", "replace")
        return req_id, typ, text

    try:
        with _socket.create_connection((host, port), timeout=2.0) as sock:
            sock.settimeout(3.0)
            sock.sendall(packet(1, 3, password))
            login_id, _, login_text = recv(sock)
            if login_id == -1:
                return "RCON login failed"
            sock.sendall(packet(2, 2, command))
            _, _, response = recv(sock)
            return response
    except Exception as e:
        return f"RCON error: {e}"


@app.get("/api/players/online")
async def online_players(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    text = await rcon("list") if RCON_PASSWORD else "RCON выключен"
    players = parse_online_players(text)
    return {"raw": text, "players": players, "count": len(players)}


@app.get("/api/players")
async def players(q: str = "", _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(list_players_sync, q)


def order_clause_for_table(conn: Any, table: str, *columns: str) -> str:
    available = set(table_columns(conn, table))
    order: list[str] = []
    for column in columns:
        if column in available:
            order.append(f"{quote_ident(column)} desc")
    if "id" in available:
        order.append(f"{quote_ident('id')} desc")
    return ",".join(dict.fromkeys(order))


def player_full_detail_sync(player: str, full_access: bool = False, limit: int = 120) -> dict[str, Any]:
    safe_limit = max(10, min(int(limit or 120), 240))
    uuid = find_player_uuid(player)
    if not uuid:
        raise HTTPException(status_code=404, detail="Игрок не найден")
    name = uuid_to_name().get(uuid, player)
    nbt = load_player_nbt(uuid) or {}
    stats = stats_for_uuid(uuid)
    adv = advancement_summary(uuid)
    profile_meta = player_site_bank_profile_sync(uuid, name)
    if not full_access:
        profile_pin = dict(profile_meta.get("pin") or {})
        profile_pin["visiblePin"] = ""
        profile_pin["temporaryPin"] = {}
        profile_meta["pin"] = profile_pin
    invsum = inventory_summary(nbt) if nbt else {"arInInventory": 0, "arInEnderChest": 0}
    live_inventory = plugin_inventory_live_sync(name, 10)
    inventory_history = inventory_history_sync(name, 16)
    plugin_timeline = plugin_player_activity_sync(name, safe_limit)
    coreprotect_error = ""
    try:
        core_rows = coreprotect_search(name, None, None, None, 0, "", "all", min(safe_limit, 180))
    except Exception as exc:
        core_rows = []
        coreprotect_error = public_error_message(exc)
    timeline_rows = list(plugin_timeline.get("rows", []))
    for row in core_rows:
        core_event = normalize_coreprotect_event(row, name)
        timeline_rows.append(
            {
                "time": row.get("time"),
                "source": "CoreProtect",
                "type": core_event.get("source_table") or "coreprotect",
                "player": core_event.get("player") or name,
                "world": core_event.get("world_name") or core_event.get("world"),
                "x": core_event.get("x"),
                "y": core_event.get("y"),
                "z": core_event.get("z"),
                "actionLabel": core_event.get("actionLabel"),
                "blockLabel": core_event.get("blockLabel"),
                "blockSprite": core_event.get("blockSprite"),
                "coordinates": core_event.get("coordinates"),
                "details": core_event,
                "adminOnly": True,
            }
        )
    timeline_rows.sort(key=lambda row: int(row.get("time") or 0), reverse=True)

    report_rows = [
        normalized_report_row(row)
        for row in load_collection_sync(DISCORD_REPORTS_FILE, "report", 5000)
        if report_involves_player(row, name, uuid)
    ]
    report_rows.sort(key=lambda row: int(row.get("updatedAt") or row.get("createdAt") or 0), reverse=True)
    application_rows = [
        row
        for row in load_collection_sync(DISCORD_APPLICATIONS_FILE, "application", 5000)
        if application_involves_player(row, name, uuid)
    ]
    application_rows.sort(key=lambda row: int(row.get("updatedAt") or row.get("createdAt") or 0), reverse=True)

    variants = player_identity_variants(name, uuid)
    plugin_event_rows = [
        row
        for row in read_plugin_event_rows(max(300, safe_limit * 4))
        if value_mentions_player(row.get("actor"), variants)
        or value_mentions_player(row.get("target"), variants)
        or value_mentions_player(row.get("metadata"), variants)
    ][:safe_limit]

    admin_actions_rows: list[dict[str, Any]] = []
    audit_rows: list[dict[str, Any]] = []
    bank_ledger: list[dict[str, Any]] = []
    donation_ledger: list[dict[str, Any]] = []
    donation_sessions: list[dict[str, Any]] = []
    donation_claims: list[dict[str, Any]] = []
    donation_purchases: list[dict[str, Any]] = []
    vote_rows: list[dict[str, Any]] = []
    ballot_rows: list[dict[str, Any]] = []
    candidate_rows: list[dict[str, Any]] = []
    prank_rows: list[dict[str, Any]] = []
    narcotics_rows: list[dict[str, Any]] = []

    if pg_ready():
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            if pg_table_exists(conn, "admin_actions"):
                rows = conn.execute(
                    """
                    SELECT actor,action,target,created_at,details
                    FROM admin_actions
                    WHERE lower(target)=lower(%s) OR (%s<>'' AND lower(target)=lower(%s))
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (name, uuid, uuid, safe_limit),
                ).fetchall()
                admin_actions_rows = [normalize_donation_row_timestamps(dict(row), "created_at") for row in rows]
            if pg_table_exists(conn, "site_audit"):
                rows = conn.execute(
                    """
                    SELECT actor,action,created_at,details
                    FROM site_audit
                    WHERE details::text ILIKE %s OR (%s<>'' AND details::text ILIKE %s)
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (f"%{name}%", uuid, f"%{uuid}%", safe_limit),
                ).fetchall()
                for row in rows:
                    details_obj = pg_json_loads(row["details"], {})
                    audit_rows.append(
                        {
                            "timestamp": int(row["created_at"] or 0),
                            "actor": row["actor"],
                            "action": row["action"],
                            "target": str(details_obj.get("target") or ""),
                            "status": str(details_obj.get("status") or "ok"),
                            "details": details_obj.get("details") if isinstance(details_obj, dict) else {},
                        }
                    )
            if pg_table_exists(conn, "cmv4_bank_ledger"):
                rows = conn.execute(
                    """
                    SELECT tx_id,account_id,counterparty_account_id,tx_type,amount,balance_after,status,actor,details,created_at
                    FROM cmv4_bank_ledger
                    WHERE player_uuid=%s
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (uuid, safe_limit),
                ).fetchall()
                bank_ledger = [normalize_donation_row_timestamps(dict(row), "created_at") for row in rows]
            if pg_table_exists(conn, "donation_balance_ledger"):
                rows = conn.execute(
                    "SELECT id,delta,balance_after,reason,actor,source,created_at FROM donation_balance_ledger WHERE player_uuid=%s ORDER BY created_at DESC LIMIT %s",
                    (uuid, safe_limit),
                ).fetchall()
                donation_ledger = [normalize_donation_row_timestamps(dict(row), "created_at") for row in rows]
            if pg_table_exists(conn, "donation_payment_sessions"):
                rows = conn.execute(
                    """
                    SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,created_at,expires_at,paid_at,cancelled_at,updated_at
                    FROM donation_payment_sessions
                    WHERE player_uuid=%s
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (uuid, safe_limit),
                ).fetchall()
                donation_sessions = [normalize_donation_session_row(conn, row, donation_now_ms()) for row in rows]
            if pg_table_exists(conn, "donation_item_claims"):
                rows = conn.execute(
                    """
                    SELECT c.id,c.item_id,c.amount,c.status,c.purchase_id,c.actor,c.claimed_at,c.created_at,c.updated_at,
                           p.status AS purchase_status,p.price_donation
                    FROM donation_item_claims c
                    LEFT JOIN donation_purchases p ON p.id=c.purchase_id
                    WHERE c.player_uuid=%s
                    ORDER BY c.created_at DESC
                    LIMIT %s
                    """,
                    (uuid, safe_limit),
                ).fetchall()
                donation_claims = [normalize_donation_row_timestamps(dict(row), "claimed_at", "created_at", "updated_at") for row in rows]
            if pg_table_exists(conn, "donation_purchases"):
                rows = conn.execute(
                    """
                    SELECT id,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at
                    FROM donation_purchases
                    WHERE player_uuid=%s
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (uuid, safe_limit),
                ).fetchall()
                donation_purchases = [normalize_donation_row_timestamps(dict(row), "created_at", "updated_at") for row in rows]
            if pg_table_exists(conn, "prank_audit"):
                rows = conn.execute(
                    """
                    SELECT actor,target,prank,details,created_at
                    FROM prank_audit
                    WHERE lower(target)=lower(%s) OR (%s<>'' AND lower(target)=lower(%s))
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (name, uuid, uuid, safe_limit),
                ).fetchall()
                prank_rows = [normalize_donation_row_timestamps(dict(row), "created_at") for row in rows]
            if pg_table_exists(conn, "narcotics_admin_audit"):
                rows = conn.execute(
                    """
                    SELECT id,actor,action,details,created_at
                    FROM narcotics_admin_audit
                    WHERE details ILIKE %s OR (%s<>'' AND details ILIKE %s)
                    ORDER BY created_at DESC
                    LIMIT %s
                    """,
                    (f"%{name}%", uuid, f"%{uuid}%", safe_limit),
                ).fetchall()
                narcotics_rows = [normalize_donation_row_timestamps(dict(row), "created_at") for row in rows]

            def scoped_rows(table: str, *order_columns: str) -> list[dict[str, Any]]:
                if not sqlite_has_table(conn, table):
                    return []
                where, params = plugin_player_selector(conn, table, name)
                return safe_sqlite_rows(conn, table, where, params, order_clause_for_table(conn, table, *order_columns), safe_limit)

            candidate_rows = scoped_rows("candidate_applications", "submitted_at", "reviewed_at", "created_at")
            vote_rows = scoped_rows("votes", "created_at", "time")
            ballot_rows = scoped_rows("ballots", "submitted_at", "issued_at", "created_at")

    if not audit_rows:
        audit_rows = [
            row
            for row in read_jsonl_tail(AUDIT_LOG_FILE, max(400, safe_limit * 4))
            if value_mentions_player(row, variants)
        ][:safe_limit]

    profile = {
        "uuid": uuid,
        "name": name,
        "position": nbt.get("Pos"),
        "dimension": nbt.get("Dimension"),
        "health": nbt.get("Health"),
        "food": nbt.get("foodLevel"),
        "xpLevel": nbt.get("XpLevel"),
        "selectedItem": nbt.get("SelectedItem"),
        "abilities": nbt.get("abilities"),
        "statsSummary": {
            "playTimeTicks": stat_value(stats, "minecraft:play_time"),
            "deaths": stat_value(stats, "minecraft:deaths"),
            "mobKills": stat_value(stats, "minecraft:mob_kills"),
            "playerKills": stat_value(stats, "minecraft:player_kills"),
            "jumps": stat_value(stats, "minecraft:jump"),
            "walkOneCm": stat_value(stats, "minecraft:walk_one_cm"),
        },
        "advancements": adv,
        "ar": {"inventory": invsum.get("arInInventory", 0), "enderChest": invsum.get("arInEnderChest", 0)},
        "siteAccount": profile_meta.get("siteAccount", {}),
        "bank": profile_meta.get("bank", {}),
        "pin": profile_meta.get("pin", {}),
    }
    return {
        "player": name,
        "uuid": uuid,
        "profile": profile,
        "inventory": {
            "summary": invsum,
            "live": live_inventory.get("latest"),
            "history": inventory_history.get("snapshots", []),
            "liveSnapshots": live_inventory.get("onlineSnapshots", []),
        },
        "timeline": {
            "rows": timeline_rows[:safe_limit],
            "sources": {"plugin": plugin_timeline.get("source"), "coreprotect": safe_location(Path(COREPROTECT_DB))},
            "errors": {"coreprotect": coreprotect_error} if coreprotect_error else {},
        },
        "actions": core_rows[: min(40, safe_limit)],
        "reports": report_rows[:safe_limit],
        "applications": application_rows[:safe_limit],
        "audit": audit_rows[:safe_limit],
        "adminActions": admin_actions_rows[:safe_limit],
        "pluginEvents": plugin_event_rows[:safe_limit],
        "economy": {
            "bankLedger": bank_ledger[:safe_limit],
            "donationLedger": donation_ledger[:safe_limit],
            "donationSessions": donation_sessions[:safe_limit],
            "donationClaims": donation_claims[:safe_limit],
            "donationPurchases": donation_purchases[:safe_limit],
        },
        "elections": {
            "candidateApplications": candidate_rows[:safe_limit],
            "votes": vote_rows[:safe_limit],
            "ballots": ballot_rows[:safe_limit],
        },
        "fun": {
            "pranks": prank_rows[:safe_limit],
            "narcoticsAudit": narcotics_rows[:safe_limit],
        },
    }


@app.get("/api/players/{player}/profile")
async def player_profile(player: str, context: dict[str, Any] = Depends(require_panel_admin_context)) -> dict[str, Any]:
    uuid = await bg(find_player_uuid, player)
    if not uuid:
        raise HTTPException(status_code=404, detail="Игрок не найден")
    name = uuid_to_name().get(uuid, player)
    nbt = await bg(load_player_nbt, uuid) or {}
    stats = await bg(stats_for_uuid, uuid)
    adv = await bg(advancement_summary, uuid)
    profile_meta = await bg(player_site_bank_profile_sync, uuid, name)
    if not bool(context.get("fullAccess")):
        profile_pin = dict(profile_meta.get("pin") or {})
        profile_pin["visiblePin"] = ""
        profile_pin["temporaryPin"] = {}
        profile_meta["pin"] = profile_pin
    invsum = inventory_summary(nbt) if nbt else {"arInInventory": 0, "arInEnderChest": 0}
    return {
        "uuid": uuid,
        "name": name,
        "position": nbt.get("Pos"),
        "dimension": nbt.get("Dimension"),
        "health": nbt.get("Health"),
        "food": nbt.get("foodLevel"),
        "xpLevel": nbt.get("XpLevel"),
        "selectedItem": nbt.get("SelectedItem"),
        "abilities": nbt.get("abilities"),
        "statsSummary": {
            "playTimeTicks": stat_value(stats, "minecraft:play_time"),
            "deaths": stat_value(stats, "minecraft:deaths"),
            "mobKills": stat_value(stats, "minecraft:mob_kills"),
            "playerKills": stat_value(stats, "minecraft:player_kills"),
            "jumps": stat_value(stats, "minecraft:jump"),
            "walkOneCm": stat_value(stats, "minecraft:walk_one_cm"),
        },
        "advancements": adv,
        "ar": {"inventory": invsum.get("arInInventory", 0), "enderChest": invsum.get("arInEnderChest", 0)},
        "siteAccount": profile_meta.get("siteAccount", {}),
        "bank": profile_meta.get("bank", {}),
        "pin": profile_meta.get("pin", {}),
    }


@app.get("/api/players/{player}/full")
async def player_full_profile(
    player: str,
    limit: int = 120,
    context: dict[str, Any] = Depends(require_panel_admin_context),
) -> dict[str, Any]:
    return await bg(player_full_detail_sync, player, bool(context.get("fullAccess")), limit)


@app.post("/api/players/{player}/site-account")
async def player_site_account_update(
    player: str,
    data: AdminPlayerAccountUpdateIn,
    request: Request,
    username: str = Depends(require_admin),
) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLAYER_SITE_ACCOUNT_UPDATE")
    result = await bg(admin_update_player_account_sync, player, username, data)
    audit_event(
        username,
        "player.site_account_update",
        target=player,
        details={
            "usernameChanged": bool(result.get("usernameChanged")),
            "passwordChanged": bool(result.get("passwordChanged")),
        },
    )
    append_panel_event(
        "admin-panel",
        "player_site_account_update",
        actor=username,
        target=player,
        metadata={
            "usernameChanged": bool(result.get("usernameChanged")),
            "passwordChanged": bool(result.get("passwordChanged")),
        },
        tags=["player", "security", "account"],
    )
    return result


@app.post("/api/players/{player}/bank-pin/reset")
async def player_bank_pin_reset(player: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLAYER_BANK_PIN_RESET")
    result = await bg(reset_player_bank_pin_sync, player, username)
    delivered = await deliver_temporary_pin_in_game(str(result.get("minecraftName") or player), str(result.get("temporaryPin") or ""))
    audit_event(username, "player.bank_pin_reset", target=player, details={"expiresAt": result.get("expiresAt"), "deliveredInGame": delivered})
    append_panel_event(
        "admin-panel",
        "player_bank_pin_reset",
        actor=username,
        target=player,
        metadata={"expiresAt": result.get("expiresAt"), "deliveredInGame": delivered},
        tags=["player", "security", "bank"],
    )
    return {
        "ok": True,
        "minecraftUuid": result.get("minecraftUuid"),
        "minecraftName": result.get("minecraftName"),
        "siteAccountId": result.get("siteAccountId"),
        "siteUsername": result.get("siteUsername"),
        "expiresAt": result.get("expiresAt"),
        "deliveredInGame": delivered,
    }


@app.post("/api/players/{player}/bank-pin/randomize")
async def player_bank_pin_randomize(player: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLAYER_BANK_PIN_RANDOMIZE")
    result = await bg(randomize_player_bank_pin_sync, player, username)
    audit_event(username, "player.bank_pin_randomize", target=player, details={"minecraftUuid": result.get("minecraftUuid")})
    append_panel_event(
        "admin-panel",
        "player_bank_pin_randomize",
        actor=username,
        target=player,
        metadata={"minecraftUuid": result.get("minecraftUuid")},
        tags=["player", "security", "bank"],
    )
    return result


@app.post("/api/players/{player}/bank-pin/set")
async def player_bank_pin_set(player: str, data: PlayerPinSetIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLAYER_BANK_PIN_SET")
    result = await bg(admin_set_player_bank_pin_sync, player, username, data)
    audit_event(username, "player.bank_pin_set", target=player, details={"minecraftUuid": result.get("minecraftUuid")})
    append_panel_event(
        "admin-panel",
        "player_bank_pin_set",
        actor=username,
        target=player,
        metadata={"minecraftUuid": result.get("minecraftUuid")},
        tags=["player", "security", "bank"],
    )
    return result


@app.get("/api/admin/economy/treasury")
async def admin_treasury_profile(username: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(admin_treasury_bank_profile_sync, username)


@app.post("/api/admin/economy/treasury/pin")
async def admin_treasury_pin(data: PlayerPinSetIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "TREASURY_PIN_SET")
    result = await bg(admin_set_treasury_pin_sync, username, data)
    audit_event(username, "treasury.pin.set", target=TREASURY_ACCOUNT_ID, details={"accountId": result.get("accountId")})
    append_panel_event("donation", "treasury_pin_set", actor=username, target=TREASURY_ACCOUNT_ID, tags=["economy", "treasury", "security"])
    return result


@app.get("/api/treasury/public")
async def treasury_public(limit: int = 30) -> dict[str, Any]:
    return await bg(public_treasury_overview_sync, limit)


@app.get("/api/players/{player}/inventory")
async def player_inventory(player: str, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    # A profile read must never force a world-wide save. Live plugin snapshots
    # are preferred when the player is online; the NBT file is used as the
    # bounded offline fallback. An explicit snapshot action remains available
    # for admins who intentionally want a fresh capture.
    live = await bg(plugin_inventory_live_sync, player, 1)
    latest_live = live.get("latest")
    uuid = await bg(find_player_uuid, player)
    if not uuid:
        if latest_live:
            return {"uuid": latest_live.get("uuid") or "", "name": latest_live.get("name") or player, "parserReady": False, "liveReady": True, "live": latest_live, "inventory": latest_live.get("inventory", []), "enderChest": latest_live.get("enderChest", []), "inventoryCounts": {}, "enderCounts": {}, "arInInventory": latest_live.get("arInInventory", 0), "arInEnderChest": latest_live.get("arInEnderChest", 0)}
        raise HTTPException(status_code=404, detail="Игрок не найден")
    nbt = await bg(load_player_nbt, uuid)
    if nbt is None:
        if latest_live:
            return {"uuid": uuid, "name": uuid_to_name().get(uuid, player), "parserReady": False, "liveReady": True, "live": latest_live, "inventory": latest_live.get("inventory", []), "enderChest": latest_live.get("enderChest", []), "inventoryCounts": {}, "enderCounts": {}, "arInInventory": latest_live.get("arInInventory", 0), "arInEnderChest": latest_live.get("arInEnderChest", 0)}
        return {"uuid": uuid, "name": uuid_to_name().get(uuid, player), "parserReady": False, "liveReady": False, "inventory": [], "enderChest": [], "inventoryCounts": {}, "enderCounts": {}, "arInInventory": 0, "arInEnderChest": 0}
    data = inventory_summary(nbt)
    data["inventoryRaw"] = data.get("inventory", [])
    data["enderChestRaw"] = data.get("enderChest", [])
    data["inventory"] = normalize_inventory_list(data.get("inventory", []))
    data["enderChest"] = normalize_inventory_list(data.get("enderChest", []))
    data.update({"uuid": uuid, "name": uuid_to_name().get(uuid, player), "parserReady": True, "liveReady": bool(live.get("latest")), "live": live.get("latest")})
    return data


@app.get("/api/players/{player}/inventory/live")
async def player_live_inventory(player: str, limit: int = 20, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(plugin_inventory_live_sync, player, limit)


@app.get("/api/players/{player}/inventory/history")
async def player_inventory_history(player: str, limit: int = 120, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(inventory_history_sync, player, limit)


@app.post("/api/players/{player}/inventory/snapshots")
async def player_inventory_snapshot(player: str, username: str = Depends(require_admin)) -> dict[str, Any]:
    snapshot = await bg(save_inventory_snapshot_sync, player)
    audit_event(username, "inventory.snapshot", target=snapshot.get("name", player), details={"uuid": snapshot.get("uuid")})
    return snapshot


@app.get("/api/players/{player}/inventory/diff")
async def player_inventory_diff(player: str, older: int, newer: int, _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(inventory_diff_sync, player, older, newer)


@app.get("/api/players/{player}/stats")
async def player_stats(player: str, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    uuid = await bg(find_player_uuid, player)
    if not uuid:
        raise HTTPException(status_code=404, detail="Игрок не найден")
    return {"uuid": uuid, "name": uuid_to_name().get(uuid, player), "stats": await bg(stats_for_uuid, uuid), "advancements": await bg(advancement_summary, uuid)}


@app.get("/api/players/{player}/actions")
async def player_actions(player: str, limit: int = 200, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    rows = await bg(coreprotect_search, player, None, None, None, 0, "", "all", limit)
    return {"player": player, "rows": rows}


@app.get("/api/players/{player}/timeline")
async def player_timeline(player: str, limit: int = 260, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    plugin = await bg(plugin_player_activity_sync, player, limit)
    core_error = ""
    try:
        core = await bg(coreprotect_search, player, None, None, None, 0, "", "all", min(limit, 260))
    except HTTPException as exc:
        core = []
        core_error = public_error_message(exc.detail)
    rows = list(plugin.get("rows", []))
    for row in core:
        core_event = normalize_coreprotect_event(row, player)
        rows.append({
            "time": row.get("time"),
            "source": "CoreProtect",
            "type": core_event.get("source_table") or "coreprotect",
            "player": core_event.get("player") or player,
            "world": core_event.get("world_name") or core_event.get("world"),
            "x": core_event.get("x"),
            "y": core_event.get("y"),
            "z": core_event.get("z"),
            "actionLabel": core_event.get("actionLabel"),
            "blockLabel": core_event.get("blockLabel"),
            "blockSprite": core_event.get("blockSprite"),
            "coordinates": core_event.get("coordinates"),
            "details": core_event,
            "adminOnly": True,
        })
    rows.sort(key=lambda r: int(r.get("time") or 0), reverse=True)
    return {"player": player, "rows": rows[: max(1, min(limit, 600))], "sources": {"plugin": plugin.get("source"), "coreprotect": safe_location(Path(COREPROTECT_DB))}, "errors": {"coreprotect": core_error} if core_error else {}}


@app.post("/api/players/{player}/command/{action}")
async def player_action(player: str, action: str, data: PlayerActionIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    player = clean_mc_player(player)
    reason = clean_mc_reason(data.reason or "CopiMine Admin")
    target = clean_mc_player(data.target) if data.target else ""
    for coordinate in (data.x, data.y, data.z):
        if coordinate is not None and (not math.isfinite(float(coordinate)) or abs(float(coordinate)) > 30_000_000):
            raise HTTPException(status_code=400, detail="Некорректные координаты")
    allowed = {
        "kick": f"kick {player} {reason}",
        "ban": f"ban {player} {reason}",
        "ban_ip": f"ban-ip {target or player} {reason}",
        "pardon": f"pardon {player}",
        "pardon_ip": f"pardon-ip {target or player}",
        "op": f"op {player}",
        "deop": f"deop {player}",
        "gamemode_survival": f"gamemode survival {player}",
        "gamemode_creative": f"gamemode creative {player}",
        "spectator": f"gamemode spectator {player}",
        "adventure": f"gamemode adventure {player}",
        "heal": f"effect give {player} minecraft:instant_health 1 10 true",
        "feed": f"effect give {player} minecraft:saturation 1 10 true",
        "clear": f"clear {player}",
        "kill": f"kill {player}",
        "spawn": f"spawnpoint {player}",
        "drug_effect": f"effect give {player} minecraft:nausea 25 1 true",
        "prank_confuse": f"effect give {player} minecraft:blindness 10 0 true",
        "prank_launch": f"effect give {player} minecraft:levitation 4 3 true",
        "prank_lightning": f"execute at {player} run summon lightning_bolt ~ ~ ~",
    }
    if action == "tp_to" and target:
        allowed[action] = f"tp {player} {target}"
    if action == "tp_here" and target:
        allowed[action] = f"tp {target} {player}"
    if action == "tp_coords" and data.x is not None and data.y is not None and data.z is not None:
        allowed[action] = f"tp {player} {data.x} {data.y} {data.z}"
    if action not in allowed:
        raise HTTPException(status_code=400, detail="Действие не поддерживается или не хватает параметров")
    if action in {"ban", "ban_ip", "op", "deop", "clear", "kill", "tp_here", "tp_coords", "prank_lightning"}:
        require_sensitive_confirm(request, f"PLAYER_{action.upper()}")
    command = allowed[action]
    response = await rcon(command)
    audit_event(username, "player.action", target=player, details={"action": action, "reason": reason, "targetArg": target, "command": command, "response": response[:400]})
    append_panel_event("admin-panel", "player_action", actor=username, target=player, metadata={"action": action, "reason": reason, "targetArg": target, "command": command}, tags=["player", "rcon"])
    return {"command": command, "response": response}


@app.get("/api/economy/ares/overview")
async def ares_overview(scanWorld: bool = False, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    data = await bg(economy_snapshot_payload)
    if scanWorld:
        data["worldContainers"] = await bg(scan_world_containers_sync)
    data["lastSnapshotAt"] = int(time.time())
    data["historyAvailable"] = await bg(economy_history_available)
    return data


@app.post("/api/economy/ares/scan-world")
async def ares_scan_world(maxRegionFiles: int = MAX_WORLD_REGION_FILES, maxChunks: int = MAX_WORLD_CHUNKS, username: str = Depends(require_admin)) -> dict[str, Any]:
    result = await bg(scan_world_containers_sync, maxRegionFiles, maxChunks)
    audit_event(username, "economy.world_scan", target="ares", details={"chunks": result.get("chunks"), "total": result.get("total")})
    append_panel_event("admin-panel", "economy_world_scan", actor=username, metadata={"chunks": result.get("chunks"), "total": result.get("total")}, tags=["economy", "read-only"])
    return result


@app.get("/api/economy/ares/history")
async def ares_history(limit: int = 120, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(economy_history_sync, limit)


@app.get("/api/economy/ares/ledger")
async def ares_ledger(limit: int = 500, _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(economy_ledger_sync, limit)


@app.post("/api/economy/ares/snapshots")
async def ares_snapshot(username: str = Depends(require_admin)) -> dict[str, Any]:
    snapshot = await bg(save_economy_snapshot_sync)
    audit_event(username, "economy.snapshot", target="ares", details={"totalKnownInPlayerData": snapshot.get("totalKnownInPlayerData")})
    return snapshot


@app.get("/api/elections/overview")
async def elections_overview(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    plugin_web = await bg(elections_plugin_web_sync)
    if pg_ready():
        try:
            detail = await bg(election_detail_sync, 100)
            db_data = {
                "db": {"type": "postgresql", "schema": POSTGRES_SCHEMA, "legacyFallback": safe_location(admin_plugin_db_path())},
                "tables": [],
                "groups": {"postgresql": ["elections", "election_candidates", "election_votes", "election_ballots", "election_decrees", "election_petitions"]},
                "antiFraud": detail.get("antiFraud", []),
                "summary": detail.get("summary", {}),
            }
        except Exception as exc:
            db_data = {"db": {"type": "postgresql", "schema": POSTGRES_SCHEMA}, "tables": [], "groups": {}, "antiFraud": [], "error": public_error_message(exc)}
    else:
        try:
            db_data = await bg(detect_election_tables_sync)
        except HTTPException as exc:
            db_data = {"db": safe_location(admin_plugin_db_path()), "tables": [], "groups": {}, "antiFraud": [], "error": exc.detail}
    db_data["pluginWeb"] = plugin_web
    db_data["readOnly"] = not pg_ready()
    return db_data


@app.get("/api/elections/detail")
async def elections_detail(limit: int = 500, _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(election_detail_sync, limit)


@app.post("/api/elections/applications/{application_id}/review")
async def elections_application_review(
    application_id: str,
    data: ElectionApplicationReviewIn,
    request: Request,
    username: str = Depends(require_admin),
) -> dict[str, Any]:
    decision = str(data.decision or "").strip().lower()
    if decision not in {"approved", "rejected"}:
        raise HTTPException(status_code=400, detail="Решение должно быть approved или rejected")
    require_sensitive_confirm(request, f"ELECTION_APPLICATION_{decision.upper()}")
    result = await bg(review_candidate_application_sync, application_id, data, username)
    return {"ok": True, **result}


@app.get("/api/elections/decrees")
async def elections_decrees(limit: int = 200, status: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(list_election_documents_sync, "decrees", limit, status)


@app.post("/api/elections/decrees")
async def elections_decrees_create(data: ElectionDecreeIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(create_election_decree_sync, data, username)
    append_panel_event("admin-panel", "election_decree_created", actor=username, target=item["id"], metadata={"title": item["title"], "status": item["status"]}, tags=["elections", "decree"])
    return {"ok": True, "decree": item}


@app.get("/api/elections/petitions")
async def elections_petitions(limit: int = 200, status: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(list_election_documents_sync, "petitions", limit, status)


@app.post("/api/elections/petitions")
async def elections_petitions_create(data: ElectionPetitionIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(create_election_petition_sync, data, username)
    append_panel_event("admin-panel", "election_petition_created", actor=username, target=item["id"], metadata={"title": item["title"], "status": item["status"]}, tags=["elections", "petition"])
    return {"ok": True, "petition": item}


@app.post("/api/elections/control")
async def elections_control(data: ElectionControlIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    if data.action.strip().lower() in {"stop", "reset", "force-reset", "archive", "finish"}:
        require_sensitive_confirm(request, f"ELECTION_{data.action.strip().upper().replace('-', '_')}")
    result = await bg(election_control_command, data.action)
    audit_event(username, "election.control", target=data.action.strip().lower(), details={"action": data.action.strip().lower(), "message": data.message or ""})
    append_panel_event("admin-panel", "election_control", actor=username, target=data.action.strip().lower(), metadata={"action": data.action.strip().lower()}, tags=["elections", "control"])
    return {"ok": True, "action": data.action.strip().lower(), "output": result}


async def run_election_emergency(action: str, data: ElectionEmergencyIn, username: str) -> dict[str, Any]:
    election_emergency_command(action, data)
    raise AssertionError("unreachable")


@app.post("/api/elections/emergency/issue-application")
async def elections_emergency_issue_application(data: ElectionEmergencyIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ELECTION_ISSUE_APPLICATION")
    return await run_election_emergency("issue-application", data, username)


@app.post("/api/elections/emergency/issue-ballot")
async def elections_emergency_issue_ballot(data: ElectionEmergencyIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ELECTION_ISSUE_BALLOT")
    return await run_election_emergency("issue-ballot", data, username)


@app.post("/api/elections/emergency/annul-application")
async def elections_emergency_annul_application(data: ElectionEmergencyIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ELECTION_ANNUL_APPLICATION")
    return await run_election_emergency("annul-application", data, username)


@app.post("/api/elections/emergency/annul-ballot")
async def elections_emergency_annul_ballot(data: ElectionEmergencyIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ELECTION_ANNUL_BALLOT")
    return await run_election_emergency("annul-ballot", data, username)


@app.get("/api/db/admin/tables")
async def admin_db_tables(_: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        db_path = admin_plugin_db_path()
        with open_sqlite_readonly(str(db_path)) as conn:
            return {"db": admin_plugin_db_location(db_path), "tables": [{"name": t, "columns": table_columns(conn, t)} for t in tables(conn)]}
    except HTTPException as exc:
        return {"db": admin_plugin_db_location(admin_plugin_db_path()), "tables": [], "error": exc.detail}


@app.get("/api/db/admin/{table}/rows")
async def admin_db_rows(table: str, limit: int = 200, offset: int = 0, q: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(sqlite_table_rows, str(admin_plugin_db_path()), table, limit, offset, q)


@app.get("/api/investigations/sources")
async def investigations_sources(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    try:
        with open_sqlite_readonly(COREPROTECT_DB) as conn:
            return {"db": safe_location(Path(COREPROTECT_DB)), "tables": [{"name": t, "columns": table_columns(conn, t)} for t in tables(conn)]}
    except HTTPException as exc:
        return {"db": safe_location(Path(COREPROTECT_DB)), "tables": [], "error": exc.detail}


@app.get("/api/investigations/block-logs")
async def investigations_block_logs(
    player: str = "",
    x: Optional[int] = Query(default=None),
    y: Optional[int] = Query(default=None),
    z: Optional[int] = Query(default=None),
    radius: int = 0,
    action: str = "",
    source: str = "all",
    limit: int = 250,
    _: str = Depends(require_panel_admin),
) -> dict[str, Any]:
    rows = await bg(coreprotect_search, player, x, y, z, radius, action, source, limit)
    return {"rows": rows, "source": safe_location(Path(COREPROTECT_DB))}


@app.get("/api/investigations/export.csv")
async def investigations_export_csv(
    player: str = "",
    x: Optional[int] = Query(default=None),
    y: Optional[int] = Query(default=None),
    z: Optional[int] = Query(default=None),
    radius: int = 0,
    action: str = "",
    source: str = "all",
    limit: int = 1000,
    _: str = Depends(require_admin),
) -> StreamingResponse:
    rows = await bg(coreprotect_search, player, x, y, z, radius, action, source, limit)
    fieldnames = sorted({k for row in rows for k in row.keys()}) or ["empty"]

    def generate():
        buf = io.StringIO()
        writer = csv.DictWriter(buf, fieldnames=fieldnames)
        writer.writeheader()
        yield buf.getvalue()
        buf.seek(0); buf.truncate(0)
        for row in rows:
            writer.writerow(row)
            yield buf.getvalue()
            buf.seek(0); buf.truncate(0)

    headers = {"Content-Disposition": "attachment; filename=copimine-investigation.csv"}
    return StreamingResponse(generate(), media_type="text/csv; charset=utf-8", headers=headers)


@app.get("/api/logs/latest")
async def latest_logs(lines: int = 120, category: str = "all", _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    data = await bg(read_log_lines, lines, category)
    return {"file": safe_location(LOG_FILE), "lines": data, "category": category}


def rcon_web_allowed(command: str) -> bool:
    normalized = re.sub(r"\s+", " ", command.strip().lower())
    if not normalized:
        return False
    return any(normalized == item or normalized.startswith(item + " ") for item in RCON_WEB_COMMAND_ALLOWLIST)


@app.post("/api/rcon")
async def rcon_command(data: CommandIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    cmd = data.command.strip().lstrip("/")
    if not cmd:
        raise HTTPException(status_code=400, detail="Команда пустая")
    if not rcon_web_allowed(cmd):
        audit_event(username, "rcon.rejected", target="command-center", status="blocked", details={"commandVerb": cmd.split(" ", 1)[0]})
        raise HTTPException(status_code=403, detail="Команда не входит в allowlist безопасного RCON-центра")
    response = await rcon(cmd)
    audit_event(username, "rcon.command", target="command-center", details={"commandVerb": cmd.split(" ", 1)[0], "length": len(cmd)})
    append_panel_event("admin-panel", "rcon_command", actor=username, metadata={"commandVerb": cmd.split(" ", 1)[0], "length": len(cmd)}, tags=["rcon"])
    return {"command": cmd, "response": response}


TICKETS_FILE = DATA_DIR / "tickets.json"


def create_application_sync(data: DiscordApplicationIn, source: str, actor: str) -> dict[str, Any]:
    item = data.model_dump()
    item["status"] = normalize_status(str(item.get("status", "pending")), APPLICATION_STATUSES, "pending")
    item.update({
        "id": f"app_{secrets.token_hex(6)}",
        "createdAt": now_ts(),
        "updatedAt": now_ts(),
        "source": source,
    })
    add_timeline(item, "created", actor, metadata={"source": source})
    save_collection_item_sync(DISCORD_APPLICATIONS_FILE, "application", item)
    mirror_application_to_postgres(item, actor)
    add_discord_outbox(
        "application",
        item["id"],
        f"New application: {item.get('player', 'unknown')}",
        application_description(item),
        player=str(item.get("player", "")),
        metadata={"object": item, "source": source},
        object_type="application",
    )
    append_panel_event("discord", "application_created", actor=actor, target=item["id"], metadata={"player": item.get("player"), "source": source}, tags=["discord", "application"])
    return item


def create_report_sync(data: DiscordReportIn, source: str, actor: str) -> dict[str, Any]:
    item = data.model_dump()
    item["metadata"] = normalize_report_metadata_for_source(item.get("metadata"), source, str(item.get("message") or ""))
    item["reportType"] = str(item["metadata"].get("reportKind") or "report")
    item["errorCode"] = clip_text(item["metadata"].get("errorCode"), 80)
    item["errorSummary"] = clip_text(item["metadata"].get("errorSummary"), 240)
    item["status"] = normalize_status(str(item.get("status", "open")), REPORT_STATUSES, "open")
    item.update({
        "id": f"rep_{secrets.token_hex(6)}",
        "createdAt": now_ts(),
        "updatedAt": now_ts(),
        "source": source,
    })
    item["attached_events"] = item.get("attached_events", [])[:50]
    add_timeline(item, "created", actor, metadata={"source": source, "severity": item.get("severity")})
    save_collection_item_sync(DISCORD_REPORTS_FILE, "report", item)
    mirror_report_to_postgres(item, actor)
    add_discord_outbox(
        "report",
        item["id"],
        f"New report: {item.get('reporter', 'unknown')}",
        report_description(item),
        player=str(item.get("reporter", "")),
        metadata={"object": item, "source": source},
        object_type="report",
    )
    append_panel_event("discord", "report_created", actor=actor, target=item["id"], metadata={"reporter": item.get("reporter"), "target": item.get("target"), "source": source}, tags=["discord", "report"])
    return item


def update_application_status_sync(application_id: str, data: DiscordObjectStatusIn, actor: str, source: str) -> dict[str, Any]:
    rows = load_collection_sync(DISCORD_APPLICATIONS_FILE, "application")
    item = find_collection_item(rows, application_id)
    old_status = str(item.get("status", "pending"))
    new_status = normalize_status(data.status, APPLICATION_STATUSES, old_status if old_status in APPLICATION_STATUSES else "pending")
    item["status"] = new_status
    item["updatedAt"] = now_ts()
    if data.reason:
        item["lastReason"] = data.reason
    if data.response:
        item["lastResponse"] = data.response
    if data.discord_user_id:
        item["lastDiscordActorId"] = data.discord_user_id
    if data.discord_username:
        item["lastDiscordActor"] = data.discord_username
    add_timeline(item, f"status:{new_status}", actor, data.reason or data.response or "", {"source": source, "oldStatus": old_status, **data.metadata})
    save_collection_item_sync(DISCORD_APPLICATIONS_FILE, "application", item)
    mirror_application_to_postgres(item, actor)
    enqueue_discord_object_update("application", item, data.reason or data.response or "")
    return item


def update_report_status_sync(report_id: str, data: DiscordObjectStatusIn, actor: str, source: str) -> dict[str, Any]:
    rows = load_collection_sync(DISCORD_REPORTS_FILE, "report")
    item = find_collection_item(rows, report_id)
    old_status = str(item.get("status", "open"))
    new_status = normalize_status(data.status, REPORT_STATUSES, old_status if old_status in REPORT_STATUSES else "open")
    item["status"] = new_status
    item["updatedAt"] = now_ts()
    if data.reason:
        item["lastReason"] = data.reason
    if data.response:
        item["lastResponse"] = data.response
    if data.discord_user_id:
        item["lastDiscordActorId"] = data.discord_user_id
    if data.discord_username:
        item["lastDiscordActor"] = data.discord_username
    if data.metadata.get("investigation_id"):
        item["investigation_id"] = str(data.metadata["investigation_id"])[:80]
    add_timeline(item, f"status:{new_status}", actor, data.reason or data.response or "", {"source": source, "oldStatus": old_status, **data.metadata})
    save_collection_item_sync(DISCORD_REPORTS_FILE, "report", item)
    mirror_report_to_postgres(item, actor)
    enqueue_discord_object_update("report", item, data.reason or data.response or "")
    return item


def add_discord_reply_sync(path: Path, namespace: str, object_type: str, object_id: str, data: DiscordReplyIn, actor: str, source: str) -> dict[str, Any]:
    rows = load_collection_sync(path, namespace)
    item = find_collection_item(rows, object_id)
    reply = data.model_dump()
    reply.update({"id": secrets.token_hex(8), "createdAt": now_ts(), "source": source, "author": data.author or actor})
    replies = item.setdefault("replies", [])
    if not isinstance(replies, list):
        replies = []
        item["replies"] = replies
    replies.append(redact_value(reply))
    item["updatedAt"] = now_ts()
    add_timeline(item, "reply", actor, data.message, {"source": source, "visibility": data.visibility})
    save_collection_item_sync(path, namespace, item)
    enqueue_discord_object_update(object_type, item, data.message)
    return {"object": item, "reply": reply}


@app.post("/api/applications")
async def create_application(data: DiscordApplicationIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(create_application_sync, data, "admin", username)
    audit_event(username, "discord.application.create", target=item["id"], details={"player": item.get("player")})
    return {"ok": True, "application": item}


@app.get("/api/applications")
async def list_applications(status: str = "", player: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(load_collection_sync, DISCORD_APPLICATIONS_FILE, "application", 5000)
    if status:
        rows = [x for x in rows if x.get("status") == status]
    if player:
        rows = [x for x in rows if player.lower() in str(x.get("player", "")).lower()]
    return {"applications": rows, "count": len(rows)}


@app.patch("/api/applications/{application_id}")
async def patch_application(application_id: str, data: DiscordObjectStatusIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(update_application_status_sync, application_id, data, username, "admin")
    audit_event(username, "discord.application.update", target=application_id, details={"status": item.get("status")})
    append_panel_event("admin-panel", "application_updated", actor=username, target=application_id, metadata={"status": item.get("status")}, tags=["discord", "application"])
    return {"ok": True, "application": item}


@app.post("/api/applications/{application_id}/replies")
async def add_application_reply(application_id: str, data: DiscordReplyIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    result = await bg(add_discord_reply_sync, DISCORD_APPLICATIONS_FILE, "application", "application", application_id, data, username, "admin")
    audit_event(username, "discord.application.reply", target=application_id, details={"visibility": data.visibility})
    return {"ok": True, "application": result["object"], "reply": result["reply"]}


@app.post("/api/reports")
async def create_report(data: DiscordReportIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(create_report_sync, data, "admin", username)
    audit_event(username, "discord.report.create", target=item["id"], details={"reporter": item.get("reporter"), "target": item.get("target"), "severity": item.get("severity")})
    return {"ok": True, "report": item}


@app.get("/api/reports")
async def list_reports(status: str = "", reporter: str = "", target: str = "", kind: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = [normalized_report_row(row) for row in await bg(load_collection_sync, DISCORD_REPORTS_FILE, "report", 5000)]
    if status:
        rows = [x for x in rows if x.get("status") == status]
    if reporter:
        rows = [x for x in rows if reporter.lower() in str(x.get("reporter", "")).lower()]
    if target:
        rows = [x for x in rows if target.lower() in str(x.get("target", "")).lower()]
    if kind:
        wanted = kind.strip().lower()
        rows = [x for x in rows if str(x.get("reportType") or safe_mapping(x.get("metadata")).get("reportKind") or "report").lower() == wanted]
    return {"reports": rows, "count": len(rows)}


@app.patch("/api/reports/{report_id}")
async def patch_report(report_id: str, data: DiscordObjectStatusIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(update_report_status_sync, report_id, data, username, "admin")
    audit_event(username, "discord.report.update", target=report_id, details={"status": item.get("status")})
    append_panel_event("admin-panel", "report_updated", actor=username, target=report_id, metadata={"status": item.get("status")}, tags=["discord", "report"])
    return {"ok": True, "report": item}


@app.post("/api/reports/{report_id}/replies")
async def add_report_reply(report_id: str, data: DiscordReplyIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    result = await bg(add_discord_reply_sync, DISCORD_REPORTS_FILE, "report", "report", report_id, data, username, "admin")
    audit_event(username, "discord.report.reply", target=report_id, details={"visibility": data.visibility})
    return {"ok": True, "report": result["object"], "reply": result["reply"]}


@app.post("/api/plugin/tickets", dependencies=[Depends(require_plugin_key)])
async def plugin_create_ticket(ticket: TicketIn) -> dict[str, Any]:
    if ticket.kind == "application":
        app_data = DiscordApplicationIn(player=ticket.player, uuid=ticket.uuid, why=ticket.message, metadata={"legacyTicket": True})
        item = await bg(create_application_sync, app_data, "plugin", ticket.player)
        return {"ok": True, "application": item}
    if ticket.kind == "report":
        report_data = DiscordReportIn(
            reporter=ticket.player,
            reporter_uuid=ticket.uuid,
            target=ticket.target,
            message=ticket.message,
            world=ticket.world,
            x=ticket.x,
            y=ticket.y,
            z=ticket.z,
            severity=str(ticket.severity or "normal"),
            attached_events=list(ticket.attached_events or [])[:20],
            metadata={"legacyTicket": True, **(ticket.metadata if isinstance(ticket.metadata, dict) else {})},
        )
        item = await bg(create_report_sync, report_data, "plugin", ticket.player)
        return {"ok": True, "report": item}
    item = await bg(create_ticket_item_sync, ticket.model_dump(), "plugin")
    add_discord_outbox(item.get("kind", "report"), item["id"], f"Новое обращение: {item.get('player', 'unknown')}", item.get("message", ""), player=item.get("player", ""), metadata={"source": "plugin"})
    append_panel_event("plugin", "ticket_created", actor=item.get("player", ""), target=item["id"], metadata={"kind": item.get("kind")}, tags=["ticket", "discord"])
    return {"ok": True, "ticket": item}


@app.get("/api/tickets")
async def list_tickets(status: str = "", player: str = "", _: str = Depends(require_admin)) -> dict[str, Any]:
    tickets = await bg(list_ticket_rows_sync, 5000)
    if status:
        tickets = [t for t in tickets if t.get("status") == status]
    if player:
        tickets = [t for t in tickets if player.lower() in str(t.get("player", "")).lower()]
    return {"tickets": tickets, "count": len(tickets)}


@app.post("/api/tickets")
async def admin_create_ticket(ticket: TicketIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    if ticket.kind == "application":
        app_data = DiscordApplicationIn(player=ticket.player, uuid=ticket.uuid, why=ticket.message, metadata={"legacyTicket": True})
        item = await bg(create_application_sync, app_data, "admin", username)
        audit_event(username, "discord.application.create", target=item["id"], details={"player": item.get("player")})
        return {"ok": True, "application": item, "ticket": item}
    if ticket.kind == "report":
        report_data = DiscordReportIn(reporter=ticket.player, reporter_uuid=ticket.uuid, message=ticket.message, world=ticket.world, x=ticket.x, y=ticket.y, z=ticket.z, metadata={"legacyTicket": True})
        item = await bg(create_report_sync, report_data, "admin", username)
        audit_event(username, "discord.report.create", target=item["id"], details={"reporter": item.get("reporter")})
        return {"ok": True, "report": item, "ticket": item}
    item = await bg(create_ticket_item_sync, ticket.model_dump(), "admin")
    audit_event(username, "ticket.create", target=item["id"], details={"kind": item.get("kind"), "player": item.get("player")})
    add_discord_outbox(item.get("kind", "report"), item["id"], f"Новое обращение: {item.get('player', 'unknown')}", item.get("message", ""), player=item.get("player", ""), metadata={"source": "admin"})
    return {"ok": True, "ticket": item}


@app.patch("/api/tickets/{ticket_id}")
async def resolve_ticket(ticket_id: str, data: ResolveTicketIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    item = await bg(update_ticket_item_sync, ticket_id, data.status, data.admin_note)
    audit_event(username, "ticket.update", target=ticket_id, details={"status": data.status})
    append_panel_event("admin-panel", "ticket_updated", actor=username, target=ticket_id, metadata={"status": data.status}, tags=["ticket"])
    return {"ok": True, "ticket": item}
    tickets = await bg(read_json, TICKETS_FILE, [])
    for t in tickets:
        if t.get("id") == ticket_id:
            t["status"] = data.status
            t["adminNote"] = data.admin_note
            t["updatedAt"] = int(time.time())
            await bg(write_json, TICKETS_FILE, tickets)
            audit_event(username, "ticket.update", target=ticket_id, details={"status": data.status})
            append_panel_event("admin-panel", "ticket_updated", actor=username, target=ticket_id, metadata={"status": data.status}, tags=["ticket"])
            return {"ok": True, "ticket": t}
    raise HTTPException(status_code=404, detail="Обращение не найдено")



class DbCreateIn(BaseModel):
    values: dict[str, Any]


class DbUpdateIn(BaseModel):
    pk: dict[str, Any]
    values: dict[str, Any]


class DbDeleteIn(BaseModel):
    pk: dict[str, Any]


def sqlite_column_meta(conn: sqlite3.Connection, table: str) -> list[dict[str, Any]]:
    if is_pg_conn(conn):
        pk_rows = conn.execute(
            """
            SELECT kcu.column_name AS name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON kcu.constraint_schema=tc.constraint_schema
             AND kcu.constraint_name=tc.constraint_name
             AND kcu.table_schema=tc.table_schema
             AND kcu.table_name=tc.table_name
            WHERE tc.table_schema=? AND tc.table_name=? AND tc.constraint_type='PRIMARY KEY'
            """,
            [POSTGRES_SCHEMA, table],
        ).fetchall()
        pk = {str(r["name"]) for r in pk_rows}
        rows = conn.execute(
            """
            SELECT ordinal_position AS cid, column_name AS name, data_type AS type,
                   is_nullable AS nullable, column_default AS default
            FROM information_schema.columns
            WHERE table_schema=? AND table_name=?
            ORDER BY ordinal_position
            """,
            [POSTGRES_SCHEMA, table],
        ).fetchall()
        cols: list[dict[str, Any]] = []
        for r in rows:
            col = {
                "cid": r["cid"],
                "name": r["name"],
                "type": str(r["type"] or ""),
                "notnull": str(r["nullable"] or "").upper() == "NO",
                "default": r["default"],
                "pk": str(r["name"]) in pk,
            }
            col["control"] = infer_control(col["name"], col["type"])
            cols.append(col)
        return cols
    cols = []
    for r in conn.execute(f"PRAGMA table_info({quote_ident(table)})").fetchall():
        col = {"cid": r[0], "name": r[1], "type": str(r[2] or ""), "notnull": bool(r[3]), "default": r[4], "pk": bool(r[5])}
        col["control"] = infer_control(col["name"], col["type"])
        cols.append(col)
    return cols


def infer_control(name: str, type_name: str) -> dict[str, Any]:
    n = name.lower()
    t = type_name.lower()
    if n in {"enabled", "active", "closed", "banned", "op", "whitelisted", "verified", "done"} or n.startswith("is_") or n.startswith("has_"):
        return {"kind": "switch", "trueValue": 1, "falseValue": 0}
    if n in {"status", "state"}:
        return {"kind": "select", "options": ["open", "in_progress", "closed", "active", "disabled", "pending", "approved", "rejected"]}
    if n in {"role"}:
        return {"kind": "select", "options": ["admin"]}
    if n in {"gamemode"}:
        return {"kind": "select", "options": ["survival", "creative", "adventure", "spectator"]}
    if "int" in t or n.endswith("_id") or n == "id":
        return {"kind": "number", "step": 1}
    if any(x in t for x in ["real", "float", "double", "numeric", "decimal"]):
        return {"kind": "number", "step": "any"}
    if "time" in n or "date" in n or n.endswith("_at"):
        return {"kind": "datetime"}
    if "json" in n or "data" == n or n.endswith("_json"):
        return {"kind": "json"}
    if "text" in t or "char" in t or not t:
        return {"kind": "text"}
    if "blob" in t:
        return {"kind": "readonly"}
    return {"kind": "text"}


def sqlite_table_schema(path: str, table: str) -> dict[str, Any]:
    with open_sqlite_readonly(path) as conn:
        available = tables(conn)
        if table not in available:
            raise HTTPException(status_code=404, detail="Таблица не найдена")
        columns = sqlite_column_meta(conn, table)
        foreign = []
        if not is_pg_conn(conn):
            try:
                foreign = rows_to_dicts(conn.execute(f"PRAGMA foreign_key_list({quote_ident(table)})").fetchall())
            except Exception:
                foreign = []
        # Existing distinct values help the frontend render select controls without guessing DB schema.
        for col in columns:
            if col["control"].get("kind") == "select":
                try:
                    values = [
                        (next(iter(r.values())) if isinstance(r, dict) else r[0])
                        for r in conn.execute(f"select distinct {quote_ident(col['name'])} from {quote_ident(table)} where {quote_ident(col['name'])} is not null limit 20").fetchall()
                    ]
                    merged = list(dict.fromkeys([*col["control"].get("options", []), *[str(v) for v in values]]))
                    col["control"]["options"] = merged
                except Exception:
                    pass
        pk = [c["name"] for c in columns if c.get("pk")]
        policy = db_write_policy(table)
        db_location = admin_plugin_db_location(Path(path)) if admin_plugin_db_requested(path) else safe_location(Path(path))
        return {"db": db_location, "table": table, "columns": columns, "primaryKey": pk, "foreignKeys": foreign, "writeEnabled": DB_WRITE_ENABLED, "writeAllowed": policy["allowed"], "writePolicy": policy}


def db_write_policy(table: str) -> dict[str, Any]:
    critical_examples = ("cmv731_votes", "cmv7_ar_", "cmv7_audit")
    table_l = table.lower()
    matches = [
        {"pattern": marker, "reason": reason}
        for marker, reason in DB_WRITE_PROTECTED_TABLE_PATTERNS
        if marker in table_l
    ]
    if matches:
        return {
            "allowed": False,
            "mode": "dedicated-api-only",
            "reason": "Критичная таблица защищена от сырой записи через DB editor.",
            "matches": matches,
            "criticalExamples": critical_examples,
            "recommendedAction": "Используй специальные вкладки выборов, экономики, игроков или аварийного восстановления.",
        }
    if not DB_WRITE_ENABLED:
        return {
            "allowed": False,
            "mode": "read-only",
            "reason": "Сырые DB-записи отключены переменной DB_WRITE_ENABLED.",
            "matches": [],
            "recommendedAction": "Оставь выключенным для релиза; включай только локально и кратковременно.",
        }
    if ADMIN_DB_WRITE_ALLOWLIST and table not in ADMIN_DB_WRITE_ALLOWLIST:
        return {
            "allowed": False,
            "mode": "allowlist-required",
            "reason": "Таблица не входит в ADMIN_DB_WRITE_ALLOWLIST.",
            "matches": [],
            "recommendedAction": "Добавь таблицу в allowlist только если это не выборы, АР, аудит или live-журнал.",
        }
    return {
        "allowed": True,
        "mode": "raw-write-enabled",
        "reason": "Сырая запись разрешена текущими настройками и таблица не попала в защитные правила.",
        "matches": [],
        "recommendedAction": "Перед сохранением проверь первичный ключ и сделай backup.",
    }


def db_write_allowed(table: str) -> bool:
    return bool(db_write_policy(table).get("allowed"))


def assert_db_write_allowed(table: str) -> None:
    policy = db_write_policy(table)
    if not policy.get("allowed"):
        raise HTTPException(status_code=403, detail=f"{policy.get('reason')} {policy.get('recommendedAction')}")


def convert_sql_value(value: Any, type_name: str, control: dict[str, Any]) -> Any:
    if value == "":
        return None
    if value is None:
        return None
    kind = control.get("kind")
    t = type_name.lower()
    if kind == "switch":
        if isinstance(value, bool):
            return 1 if value else 0
        return 1 if str(value).lower() in {"1", "true", "yes", "on", "да"} else 0
    if "int" in t:
        return int(value)
    if any(x in t for x in ["real", "float", "double", "numeric", "decimal"]):
        return float(value)
    if kind == "json" and isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False)
    if "blob" in t:
        raise HTTPException(status_code=400, detail="BLOB-поля через панель не редактируются")
    return str(value)


def validate_row_values(conn: sqlite3.Connection, table: str, values: dict[str, Any], for_insert: bool) -> tuple[list[str], list[Any]]:
    meta = sqlite_column_meta(conn, table)
    by_name = {c["name"]: c for c in meta}
    columns: list[str] = []
    params: list[Any] = []
    for key, value in values.items():
        if key not in by_name:
            raise HTTPException(status_code=400, detail=f"Неизвестная колонка: {key}")
        col = by_name[key]
        if col.get("pk") and for_insert and (value is None or value == ""):
            continue
        if col["control"].get("kind") == "readonly":
            raise HTTPException(status_code=400, detail=f"Колонка {key} только для чтения")
        columns.append(key)
        params.append(convert_sql_value(value, col["type"], col["control"]))
    if not columns:
        raise HTTPException(status_code=400, detail="Нет данных для сохранения")
    return columns, params


def sqlite_insert_row(path: str, table: str, values: dict[str, Any]) -> dict[str, Any]:
    assert_db_write_allowed(table)
    db_path = Path(path)
    if not admin_plugin_db_available(db_path):
        raise HTTPException(status_code=404, detail=f"БД не найдена: {db_path}")
    with open_sqlite_write(str(db_path)) as conn:
        if table not in tables(conn):
            raise HTTPException(status_code=404, detail="Таблица не найдена")
        cols, params = validate_row_values(conn, table, values, True)
        qcols = ", ".join(quote_ident(c) for c in cols)
        marks = ", ".join("?" for _ in cols)
        cur = conn.execute(f"insert into {quote_ident(table)} ({qcols}) values ({marks})", params)
        conn.commit()
        return {"ok": True, "lastrowid": getattr(cur, "lastrowid", None), "inserted": getattr(cur, "rowcount", 0)}


def sqlite_update_row(path: str, table: str, pk: dict[str, Any], values: dict[str, Any]) -> dict[str, Any]:
    assert_db_write_allowed(table)
    db_path = Path(path)
    if not admin_plugin_db_available(db_path):
        raise HTTPException(status_code=404, detail=f"БД не найдена: {db_path}")
    with open_sqlite_write(str(db_path)) as conn:
        if table not in tables(conn):
            raise HTTPException(status_code=404, detail="Таблица не найдена")
        meta = sqlite_column_meta(conn, table)
        valid = {c["name"]: c for c in meta}
        pk_cols = [c["name"] for c in meta if c.get("pk")]
        if not pk_cols and is_pg_conn(conn):
            raise HTTPException(status_code=400, detail="PostgreSQL table has no primary key for safe raw update")
        pk_cols = pk_cols or ["rowid"]
        if not all(c in pk or c == "rowid" for c in pk_cols):
            raise HTTPException(status_code=400, detail="Не передан первичный ключ строки")
        cols, params = validate_row_values(conn, table, values, False)
        sets = ", ".join(f"{quote_ident(c)} = ?" for c in cols)
        where_parts = []
        where_params: list[Any] = []
        for c in pk_cols:
            if c == "rowid":
                where_parts.append("rowid = ?")
                where_params.append(pk.get("rowid"))
            else:
                where_parts.append(f"{quote_ident(c)} = ?")
                where_params.append(convert_sql_value(pk.get(c), valid[c]["type"], valid[c]["control"]))
        cur = conn.execute(f"update {quote_ident(table)} set {sets} where {' and '.join(where_parts)}", params + where_params)
        conn.commit()
        return {"ok": True, "updated": cur.rowcount}


def sqlite_delete_row(path: str, table: str, pk: dict[str, Any]) -> dict[str, Any]:
    assert_db_write_allowed(table)
    db_path = Path(path)
    if not admin_plugin_db_available(db_path):
        raise HTTPException(status_code=404, detail=f"БД не найдена: {db_path}")
    with open_sqlite_write(str(db_path)) as conn:
        if table not in tables(conn):
            raise HTTPException(status_code=404, detail="Таблица не найдена")
        meta = sqlite_column_meta(conn, table)
        valid = {c["name"]: c for c in meta}
        pk_cols = [c["name"] for c in meta if c.get("pk")]
        if not pk_cols and is_pg_conn(conn):
            raise HTTPException(status_code=400, detail="PostgreSQL table has no primary key for safe raw delete")
        pk_cols = pk_cols or ["rowid"]
        where_parts = []
        where_params: list[Any] = []
        for c in pk_cols:
            if c == "rowid":
                where_parts.append("rowid = ?")
                where_params.append(pk.get("rowid"))
            else:
                where_parts.append(f"{quote_ident(c)} = ?")
                where_params.append(convert_sql_value(pk.get(c), valid[c]["type"], valid[c]["control"]))
        cur = conn.execute(f"delete from {quote_ident(table)} where {' and '.join(where_parts)}", where_params)
        conn.commit()
        return {"ok": True, "deleted": cur.rowcount}


@app.get("/api/db/admin/{table}/schema")
async def admin_db_schema(table: str, _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(sqlite_table_schema, str(admin_plugin_db_path()), table)


@app.post("/api/db/admin/{table}/rows")
async def admin_db_insert(table: str, data: DbCreateIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DB_INSERT")
    result = await bg(sqlite_insert_row, str(admin_plugin_db_path()), table, data.values)
    audit_event(username, "db.insert", target=table, details={"columns": sorted(data.values.keys())})
    return result


@app.patch("/api/db/admin/{table}/rows")
async def admin_db_update(table: str, data: DbUpdateIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DB_UPDATE")
    result = await bg(sqlite_update_row, str(admin_plugin_db_path()), table, data.pk, data.values)
    audit_event(username, "db.update", target=table, details={"columns": sorted(data.values.keys())})
    return result


@app.delete("/api/db/admin/{table}/rows")
async def admin_db_delete(table: str, data: DbDeleteIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DB_DELETE")
    result = await bg(sqlite_delete_row, str(admin_plugin_db_path()), table, data.pk)
    audit_event(username, "db.delete", target=table)
    return result



def admin_public_row(username: str, meta: dict[str, Any]) -> dict[str, Any]:
    lists = minecraft_access_lists()
    username_l = username.lower()
    uuid = name_to_uuid().get(username_l, "").lower()
    is_op = username_l in lists["opNames"] or (uuid and uuid in lists["opUuids"])
    is_whitelisted = username_l in lists["whitelistNames"] or (uuid and uuid in lists["whitelistUuids"])
    can_login = bool(meta.get("enabled", True)) and (is_op or not REQUIRE_OP_FOR_LOGIN) and (is_whitelisted or not REQUIRE_WHITELIST_FOR_LOGIN)
    return {
        "username": username,
        "role": normalize_admin_role(meta.get("role")),
        "enabled": bool(meta.get("enabled", True)),
        "op": bool(is_op),
        "whitelisted": bool(is_whitelisted),
        "canLogin": bool(can_login),
        "createdAt": meta.get("createdAt"),
        "createdBy": meta.get("createdBy", "env/default"),
        "updatedAt": meta.get("updatedAt"),
    }


def ensure_minecraft_access_for_new_admin(username: str, ensure_op: bool, ensure_whitelist: bool) -> dict[str, Any]:
    lists = minecraft_access_lists()
    username_l = username.lower()
    uuid = name_to_uuid().get(username_l, "").lower()
    is_op = username_l in lists["opNames"] or (uuid and uuid in lists["opUuids"])
    is_whitelisted = username_l in lists["whitelistNames"] or (uuid and uuid in lists["whitelistUuids"])
    actions: list[str] = []
    if not is_whitelisted:
        if ensure_whitelist:
            if not RCON_PASSWORD:
                raise HTTPException(status_code=503, detail="Нельзя добавить в whitelist: RCON_PASSWORD не настроен")
            actions.append(f"whitelist add {username}")
            actions.append("whitelist reload")
            is_whitelisted = True
        else:
            raise HTTPException(status_code=400, detail="Игрока нет в whitelist. Выбери игрока из whitelist или включи 'добавить в whitelist'.")
    if not is_op and (REQUIRE_OP_FOR_LOGIN or ensure_op):
        if ensure_op:
            if not RCON_PASSWORD:
                raise HTTPException(status_code=503, detail="Нельзя выдать OP: RCON_PASSWORD не настроен")
            actions.append(f"op {username}")
            is_op = True
        elif REQUIRE_OP_FOR_LOGIN:
            raise HTTPException(status_code=400, detail="Для входа включена проверка OP, а игрок не OP. Включи 'выдать OP' или сначала выдай OP в игре.")
    responses = []
    for command in actions:
        responses.append({"command": command, "response": rcon_sync(command)})
    return {"op": is_op, "whitelisted": is_whitelisted, "rconActions": responses}


def save_admin_user(username: str, meta: dict[str, Any]) -> None:
    upsert_auth_user(username, meta)


def remove_or_disable_admin_user(username: str, current_owner: str) -> dict[str, Any]:
    real_username, found = resolve_admin_user(username)
    if not found:
        raise HTTPException(status_code=404, detail="Админ не найден")
    if real_username == current_owner:
        raise HTTPException(status_code=400, detail="Нельзя отозвать доступ у самого себя через текущую сессию")
    meta = dict(found)
    meta["enabled"] = False
    meta["updatedAt"] = int(time.time())
    meta["updatedBy"] = current_owner
    save_admin_user(real_username, meta)
    return {"ok": True, "admin": admin_public_row(real_username, meta)}


@app.get("/api/security/admins")
async def security_admins(_: str = Depends(require_admin)) -> dict[str, Any]:
    users = current_admin_users()
    rows = [admin_public_row(name, meta) for name, meta in users.items()]
    rows.sort(key=lambda r: (not r["enabled"], r["username"].lower()))
    lists = minecraft_access_lists()
    whitelist_names = sorted(lists["whitelistNames"])
    admin_names = {x.lower() for x in users.keys()}
    candidates = []
    for name_l in whitelist_names:
        name = next((x.get("name") for x in lists["whitelist"] if isinstance(x, dict) and str(x.get("name", "")).lower() == name_l), name_l)
        candidates.append({"username": name, "alreadyAdmin": name_l in admin_names, "op": name_l in lists["opNames"]})
    return {"admins": rows, "whitelistCandidates": candidates, "requireOp": REQUIRE_OP_FOR_LOGIN, "requireWhitelist": REQUIRE_WHITELIST_FOR_LOGIN}


@app.post("/api/security/admins")
async def security_add_admin(data: AdminAccessIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    actor_username = username
    require_sensitive_confirm(request, "ADMIN_CREATE")
    username = data.username.strip()
    ensure_admin_account_create_allowed(actor_username, username, data.role)
    if not valid_minecraft_name(username):
        raise HTTPException(status_code=400, detail="Ник должен быть обычным Minecraft-ником 3-16 символов: A-Z, 0-9, _")
    ok, msg = password_policy_ok(data.password)
    if not ok:
        raise HTTPException(status_code=400, detail=msg)
    mc = await bg(ensure_minecraft_access_for_new_admin, username, data.ensure_op, data.ensure_whitelist)
    now = int(time.time())
    _, existing = resolve_admin_user(username)
    meta = dict(existing)
    meta.update({
        "password_hash": make_password_hash(data.password),
        "role": normalize_admin_role(data.role),
        "enabled": True,
        "createdAt": meta.get("createdAt", now),
        "createdBy": meta.get("createdBy", actor_username),
        "updatedAt": now,
        "updatedBy": actor_username,
    })
    save_admin_user(username, meta)
    audit_event(actor_username, "security.admin.create", target=username, details={"ensureOp": data.ensure_op, "ensureWhitelist": data.ensure_whitelist, "role": normalize_admin_role(data.role)})
    append_panel_event("admin-panel", "admin_access_created", actor=actor_username, target=username, tags=["security"])
    return {"ok": True, "admin": admin_public_row(username, meta), "minecraft": mc}


@app.patch("/api/security/admins/{username}")
async def security_update_admin(username: str, data: AdminUpdateIn, request: Request, owner: str = Depends(require_owner)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ADMIN_UPDATE")
    if not valid_minecraft_name(username):
        raise HTTPException(status_code=400, detail="Некорректный ник")
    real_username, found = resolve_admin_user(username)
    if not found:
        raise HTTPException(status_code=404, detail="Админ не найден")
    username = real_username
    ensure_admin_account_owner_mutation_allowed(owner, username, data.role)
    meta = dict(found)
    if data.enabled is not None:
        if username == owner and data.enabled is False:
            raise HTTPException(status_code=400, detail="Нельзя выключить самого себя")
        meta["enabled"] = bool(data.enabled)
    if data.password:
        ok, msg = password_policy_ok(data.password)
        if not ok:
            raise HTTPException(status_code=400, detail=msg)
        meta["password_hash"] = make_password_hash(data.password)
    if data.role is not None:
        meta["role"] = normalize_admin_role(data.role)
    mc = await bg(ensure_minecraft_access_for_new_admin, username, data.ensure_op, data.ensure_whitelist)
    meta["updatedAt"] = int(time.time())
    meta["updatedBy"] = owner
    save_admin_user(username, meta)
    audit_event(owner, "security.admin.update", target=username, details={"passwordChanged": bool(data.password), "enabled": data.enabled})
    append_panel_event("admin-panel", "admin_access_updated", actor=owner, target=username, tags=["security"])
    return {"ok": True, "admin": admin_public_row(username, meta), "minecraft": mc}


@app.delete("/api/security/admins/{username}")
async def security_delete_admin(username: str, request: Request, owner: str = Depends(require_owner)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ADMIN_DISABLE")
    ensure_admin_account_owner_mutation_allowed(owner, username)
    result = await bg(remove_or_disable_admin_user, username, owner)
    audit_event(owner, "security.admin.delete", target=username)
    append_panel_event("admin-panel", "admin_access_deleted", actor=owner, target=username, tags=["security"])
    return result

@app.get("/api/security/access")
async def security_access(username: str = Depends(require_admin)) -> dict[str, Any]:
    lists = minecraft_access_lists()
    access_ok, errors = minecraft_access_ok(username)
    return {
        "username": username,
        "accessOk": access_ok,
        "errors": errors,
        "requireOp": REQUIRE_OP_FOR_LOGIN,
        "requireWhitelist": REQUIRE_WHITELIST_FOR_LOGIN,
        "adminsConfigured": sorted(current_admin_users().keys()),
        "ops": sorted(lists["opNames"]),
        "whitelist": sorted(lists["whitelistNames"]),
        "sessions": len(read_sessions()),
        "cookieAuth": True,
        "authBackend": auth_storage_backend(),
        "authDb": auth_storage_location(),
        "authDbExists": auth_storage_ready(),
        "dbWriteEnabled": DB_WRITE_ENABLED,
        "dbWriteAllowlist": sorted(ADMIN_DB_WRITE_ALLOWLIST),
        "dbWritePolicy": {
            "protectedPatterns": [{"pattern": p, "reason": r} for p, r in DB_WRITE_PROTECTED_TABLE_PATTERNS],
            "defaultMode": "read-only" if not DB_WRITE_ENABLED else "allowlist" if ADMIN_DB_WRITE_ALLOWLIST else "raw-write-enabled",
        },
    }



def item_icon_name(item_id: str) -> str:
    item = normalize_item_id(item_id)
    if not item:
        return "unknown"
    return item.split(":", 1)[1].lower().replace("/", "_") if ":" in item else item.lower().replace("/", "_")


def item_display_name(item_id: str) -> str:
    name = item_icon_name(item_id)
    return name.replace("_", " ").title() if name else "Unknown"


def normalize_inventory_slot(item: Any) -> dict[str, Any]:
    if not isinstance(item, dict):
        return {}
    item_id = normalize_item_id(item.get("id", item.get("Id", "")))
    slot = item.get("Slot", item.get("slot", ""))
    count = stack_count(item)
    icon = item_icon_name(item_id)
    plain = dict(item)
    plain.update({
        "id": item_id,
        "Slot": slot,
        "Count": count,
        "icon": icon,
        "iconUrl": f"/assets/mc-icons/item/{icon}.png",
        "displayName": item_display_name(item_id),
    })
    return plain


def normalize_inventory_list(items: Any) -> list[dict[str, Any]]:
    if not isinstance(items, list):
        return []
    rows = [normalize_inventory_slot(x) for x in items]
    return [x for x in rows if x.get("id")]


def safe_zip_name(name: str) -> str:
    name = re.sub(r"[^A-Za-z0-9_.-]+", "_", name)[:120]
    if not name.endswith(".zip"):
        name += ".zip"
    return name


def create_backup_sync(scope: str = "configs", include_logs: bool = False, include_world: bool = False) -> dict[str, Any]:
    scope = (scope or "configs").lower()
    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    name = safe_zip_name(f"copimine-{scope}-{ts}.zip")
    out = BACKUPS_DIR / name
    added: list[str] = []
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        files = [
            "server.properties", "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json",
            "usercache.json", "permissions.yml", "spigot.yml", "bukkit.yml", "paper-global.yml", "paper-world-defaults.yml",
        ]
        for rel in files:
            p = MC_SERVER_DIR / rel
            if p.exists() and p.is_file():
                zf.write(p, rel)
                added.append(rel)
        plugin_dir = MC_SERVER_DIR / "plugins"
        if plugin_dir.exists():
            for p in plugin_dir.rglob("*.yml"):
                if p.is_file():
                    arc = str(p.relative_to(MC_SERVER_DIR))
                    zf.write(p, arc)
                    added.append(arc)
        if include_logs and LOG_FILE.exists():
            zf.write(LOG_FILE, "logs/latest.log")
            added.append("logs/latest.log")
        if include_world and WORLD_DIR.exists():
            # Real backup, but bounded to region/player/config data to avoid accidentally zipping massive dynmap/cache folders.
            allowed_dirs = ["playerdata", "stats", "advancements", "region", "DIM-1/region", "DIM1/region"]
            for rel_dir in allowed_dirs:
                base = WORLD_DIR / rel_dir
                if not base.exists():
                    continue
                for p in base.rglob("*"):
                    if p.is_file():
                        arc = str(p.relative_to(MC_SERVER_DIR))
                        zf.write(p, arc)
                        added.append(arc)
    return {"ok": True, "name": name, "size": out.stat().st_size, "files": len(added), "sample": added[:60]}


def list_backups_sync() -> dict[str, Any]:
    rows = []
    for p in sorted(BACKUPS_DIR.glob("*.zip"), key=lambda x: x.stat().st_mtime, reverse=True):
        rows.append({"name": p.name, "size": p.stat().st_size, "modified": int(p.stat().st_mtime)})
    return {"dir": safe_location(BACKUPS_DIR), "backups": rows}


def delete_backup_sync(name: str) -> dict[str, Any]:
    name = safe_zip_name(name)
    path = BACKUPS_DIR / name
    if not path.exists() or path.parent != BACKUPS_DIR:
        raise HTTPException(status_code=404, detail="Бэкап не найден")
    path.unlink()
    return {"ok": True, "deleted": name}


def resourcepack_status_sync() -> dict[str, Any]:
    props = read_server_properties()
    url = props.get("resource-pack", "")
    sha1 = props.get("resource-pack-sha1", "")
    required = props.get("require-resource-pack", "false")
    metadata = artifact_metadata("resourcepacks", "CopiMineResourcePack.zip")
    local_exists = bool(metadata.get("exists"))
    local_path = str(metadata.get("path") or MANAGED_RESOURCEPACK_ZIP)
    local_sha1 = str(metadata.get("recordedSha1") or metadata.get("sha1") or "")
    return {
        "url": url,
        "sha1": sha1,
        "required": required,
        "localPath": local_path,
        "localExists": local_exists,
        "managedUrl": MANAGED_RESOURCEPACK_URL,
        "managedSha1": local_sha1,
        "managedSha256": str(metadata.get("sha256") or ""),
        "managedSize": int(metadata.get("size") or 0),
        "sha1MatchesServerProperties": bool(local_sha1) and sha1.lower() == local_sha1.lower(),
    }


def apply_resourcepack_sync(url: str, sha1: str, required: bool, prompt: str) -> dict[str, Any]:
    managed_url, managed_sha1 = validate_managed_resourcepack_apply(url, sha1)
    values = {
        "resource-pack": managed_url,
        "resource-pack-sha1": managed_sha1,
        "require-resource-pack": "true" if required else "false",
        "resource-pack-prompt": json.dumps({"text": (prompt or "CopiMine resource pack").strip()[:160], "color": "gray"}, ensure_ascii=False),
    }
    return write_server_properties(values)


@app.get("/api/minecraft/access-lists")
async def minecraft_access_list_endpoint(_: str = Depends(require_admin)) -> dict[str, Any]:
    lists = minecraft_access_lists()
    return {
        "ops": lists["ops"],
        "whitelist": lists["whitelist"],
        "bannedPlayers": read_json(MC_SERVER_DIR / "banned-players.json", []),
        "bannedIps": read_json(MC_SERVER_DIR / "banned-ips.json", []),
    }


@app.post("/api/minecraft/access-lists")
async def minecraft_access_action(data: AccessListActionIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, f"ACCESS_{data.action.strip().upper()}")
    player = data.player.strip()
    if not valid_minecraft_name(player):
        raise HTTPException(status_code=400, detail="Некорректный ник")
    reason = (data.reason or "CopiMine Admin").replace("\n", " ")[:160]
    commands = {
        "whitelist_add": [f"whitelist add {player}", "whitelist reload"],
        "whitelist_remove": [f"whitelist remove {player}", "whitelist reload"],
        "op": [f"op {player}"],
        "deop": [f"deop {player}"],
        "ban": [f"ban {player} {reason}"],
        "pardon": [f"pardon {player}"],
    }.get(data.action)
    if not commands:
        raise HTTPException(status_code=400, detail="Неизвестное действие")
    responses = []
    for command in commands:
        responses.append({"command": command, "response": await rcon(command)})
    audit_event(username, "minecraft.access_list", target=player, details={"action": data.action})
    append_panel_event("admin-panel", "minecraft_access_list_changed", actor=username, target=player, metadata={"action": data.action}, tags=["security", "minecraft"])
    return {"ok": True, "player": player, "action": data.action, "responses": responses}


@app.get("/api/backups")
async def backups_list(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(list_backups_sync)


@app.post("/api/backups")
async def backups_create(data: BackupCreateIn, username: str = Depends(require_admin)) -> dict[str, Any]:
    result = await bg(create_backup_sync, data.scope, data.include_logs, data.include_world)
    audit_event(username, "backup.create", target=result.get("name", ""), details={"scope": data.scope, "includeWorld": data.include_world, "includeLogs": data.include_logs})
    return result


@app.get("/api/backups/{name}")
async def backups_download(name: str, _: str = Depends(require_admin)) -> FileResponse:
    name = safe_zip_name(name)
    path = BACKUPS_DIR / name
    if not path.exists() or path.parent != BACKUPS_DIR:
        raise HTTPException(status_code=404, detail="Бэкап не найден")
    return FileResponse(path, filename=name, media_type="application/zip")


@app.get("/downloads/CopiMineMods.zip")
@app.head("/downloads/CopiMineMods.zip")
async def modpack_download() -> FileResponse:
    try:
        return artifact_file_response("downloads", "CopiMineMods.zip")
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Архив модов пока не подготовлен") from exc


@app.get("/downloads/{filename}")
@app.head("/downloads/{filename}")
async def managed_download(filename: str) -> FileResponse:
    try:
        return artifact_file_response("downloads", Path(filename).name)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Файл не входит в опубликованный набор CopiMine downloads") from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Файл пока не подготовлен для скачивания") from exc


@app.get("/resourcepacks/CopiMineResourcePack.zip")
@app.head("/resourcepacks/CopiMineResourcePack.zip")
async def managed_resourcepack_download() -> FileResponse:
    try:
        return artifact_file_response("resourcepacks", "CopiMineResourcePack.zip")
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Ресурс-пак пока не подготовлен") from exc


@app.get("/resourcepacks/{filename}")
@app.head("/resourcepacks/{filename}")
async def managed_resourcepack_named(filename: str) -> FileResponse:
    try:
        return artifact_file_response("resourcepacks", Path(filename).name)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Файл не входит в опубликованный набор CopiMine resourcepacks") from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Ресурс-пак пока не подготовлен") from exc


@app.delete("/api/backups/{name}")
async def backups_delete(name: str, username: str = Depends(require_admin)) -> dict[str, Any]:
    result = await bg(delete_backup_sync, name)
    audit_event(username, "backup.delete", target=safe_zip_name(name))
    return result


@app.get("/api/resourcepack/status")
async def resourcepack_status(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(resourcepack_status_sync)


@app.post("/api/resourcepack/apply")
async def resourcepack_apply(data: ResourcePackApplyIn, username: str = Depends(require_owner)) -> dict[str, Any]:
    result = await bg(apply_resourcepack_sync, data.url, data.sha1, data.required, data.prompt)
    audit_event(username, "resourcepack.apply", target="server.properties", details={"urlConfigured": bool(data.url), "sha1Configured": bool(data.sha1), "required": data.required})
    append_panel_event("admin-panel", "resourcepack_changed", actor=username, metadata={"required": data.required}, tags=["server"])
    return result


@app.get("/api/system/services")
async def system_services(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(service_status_sync)


@app.get("/api/data-sources")
async def data_sources(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(source_registry_sync)


@app.get("/api/audit")
async def audit_log(limit: int = 200, action: str = "", actor: str = "", target: str = "", _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    rows = await bg(read_site_audit_rows, limit, action, actor, target)
    return {"rows": rows, "count": len(rows)}


@app.get("/api/plugin/events")
async def plugin_events(limit: int = 250, source: str = "", event_type: str = "", _: str = Depends(require_panel_admin)) -> dict[str, Any]:
    rows = await bg(read_plugin_event_rows, limit, source, event_type)
    return {"rows": rows, "count": len(rows)}


@app.post("/api/plugin/events", dependencies=[Depends(require_plugin_key)])
async def plugin_event_ingest(event: PluginEventIn) -> dict[str, Any]:
    allowed_severity = {"debug", "info", "warning", "error", "critical"}
    severity = event.severity.lower() if event.severity.lower() in allowed_severity else "info"
    tags = [str(x)[:40] for x in event.tags[:20]]
    item = append_panel_event(
        event.source,
        event.event_type,
        actor=event.actor or "",
        target=event.target or "",
        metadata=event.metadata,
        severity=severity,
        tags=tags,
        world=event.world,
        x=event.x,
        y=event.y,
        z=event.z,
        item=event.item,
        block=event.block,
        timestamp=event.timestamp,
    )
    return {"ok": True, "event": item}


@app.get("/api/events/stream")
async def events_stream(_: str = Depends(require_panel_admin)) -> StreamingResponse:
    async def generate():
        last_seen_id = ""
        while True:
            try:
                rows = read_plugin_event_rows(20)
                current_id = str(rows[0]["id"]) if rows else ""
                if current_id != last_seen_id:
                    yield "event: events\n"
                    yield "data: " + json.dumps({"rows": rows[-10:], "time": now_ts()}, ensure_ascii=False) + "\n\n"
                    last_seen_id = current_id
                await asyncio.sleep(3)
            except asyncio.CancelledError:
                break
            except Exception:
                await asyncio.sleep(5)
    return StreamingResponse(generate(), media_type="text/event-stream")


@app.get("/api/discord/status")
async def discord_status(_: str = Depends(require_admin)) -> dict[str, Any]:
    state = await bg(read_discord_state_sync)
    outbox = await bg(load_collection_sync, DISCORD_OUTBOX_FILE, "outbox", 1000)
    actions = await bg(read_recent_bridge_events, 100)
    applications = await bg(load_collection_sync, DISCORD_APPLICATIONS_FILE, "application", 1000)
    reports = await bg(load_collection_sync, DISCORD_REPORTS_FILE, "report", 1000)
    return {
        "configured": {
            "token": DISCORD_BOT_TOKEN_CONFIGURED,
            "guildId": bool(DISCORD_GUILD_ID),
            "applicationsChannelId": bool(DISCORD_APPLICATIONS_CHANNEL_ID),
            "reportsChannelId": bool(DISCORD_REPORTS_CHANNEL_ID),
            "adminRoleId": bool(DISCORD_ADMIN_ROLE_ID),
            "botApiKey": bool(DISCORD_BOT_API_KEY or PLUGIN_API_KEY),
        },
        "state": state if isinstance(state, dict) else {},
        "outbox": outbox[:100],
        "actions": actions,
        "applications": applications[:100],
        "reports": reports[:100],
        "fallbackChannels": {"applications": "┋заявки", "reports": "┋жалобы"},
    }


@app.get("/api/discord/applications", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_applications(request: Request, status: str = "", limit: int = 100) -> dict[str, Any]:
    check_discord_rate_limit(request, "applications")
    rows = await bg(load_collection_sync, DISCORD_APPLICATIONS_FILE, "application", 5000)
    if status:
        rows = [x for x in rows if x.get("status") == status]
    return {"applications": rows[: max(1, min(limit, 250))], "count": len(rows)}


@app.patch("/api/discord/applications/{application_id}/status", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_application_status(application_id: str, data: DiscordObjectStatusIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "application-status")
    actor = f"discord:{data.discord_user_id or 'unknown'}"
    item = await bg(update_application_status_sync, application_id, data, actor, "discord")
    audit_event(actor, "discord.application.status", target=application_id, details={"status": item.get("status"), "username": data.discord_username, "reason": data.reason})
    append_panel_event("discord", "application_status_changed", actor=data.discord_username or actor, target=application_id, metadata={"status": item.get("status")}, tags=["discord", "application"])
    return {"ok": True, "application": item}


@app.post("/api/discord/applications/{application_id}/replies", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_application_reply(application_id: str, data: DiscordReplyIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "application-reply")
    actor = f"discord:{data.discord_user_id or 'unknown'}"
    result = await bg(add_discord_reply_sync, DISCORD_APPLICATIONS_FILE, "application", "application", application_id, data, actor, "discord")
    audit_event(actor, "discord.application.reply", target=application_id, details={"author": data.author, "visibility": data.visibility})
    return {"ok": True, "application": result["object"], "reply": result["reply"]}


@app.get("/api/discord/reports", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_reports(request: Request, status: str = "", limit: int = 100) -> dict[str, Any]:
    check_discord_rate_limit(request, "reports")
    rows = await bg(load_collection_sync, DISCORD_REPORTS_FILE, "report", 5000)
    if status:
        rows = [x for x in rows if x.get("status") == status]
    return {"reports": rows[: max(1, min(limit, 250))], "count": len(rows)}


@app.patch("/api/discord/reports/{report_id}/status", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_report_status(report_id: str, data: DiscordObjectStatusIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "report-status")
    actor = f"discord:{data.discord_user_id or 'unknown'}"
    item = await bg(update_report_status_sync, report_id, data, actor, "discord")
    audit_event(actor, "discord.report.status", target=report_id, details={"status": item.get("status"), "username": data.discord_username, "reason": data.reason})
    append_panel_event("discord", "report_status_changed", actor=data.discord_username or actor, target=report_id, metadata={"status": item.get("status")}, tags=["discord", "report"])
    return {"ok": True, "report": item}


@app.post("/api/discord/reports/{report_id}/replies", dependencies=[Depends(require_discord_bot_key)])
async def discord_bot_report_reply(report_id: str, data: DiscordReplyIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "report-reply")
    actor = f"discord:{data.discord_user_id or 'unknown'}"
    result = await bg(add_discord_reply_sync, DISCORD_REPORTS_FILE, "report", "report", report_id, data, actor, "discord")
    audit_event(actor, "discord.report.reply", target=report_id, details={"author": data.author, "visibility": data.visibility})
    return {"ok": True, "report": result["object"], "reply": result["reply"]}


@app.get("/api/discord/object-link", dependencies=[Depends(require_discord_bot_key)])
async def discord_object_link(request: Request, object_type: str, object_id: str) -> dict[str, Any]:
    check_discord_rate_limit(request, "object-link", limit=300)
    if object_type not in {"application", "report"}:
        raise HTTPException(status_code=400, detail="Unsupported Discord object type")
    return {"url": discord_object_url(object_type, object_id), "objectType": object_type, "objectId": object_id}


@app.get("/api/discord/outbox", dependencies=[Depends(require_discord_bot_key)])
async def discord_outbox(request: Request, status: str = "pending", limit: int = 20) -> dict[str, Any]:
    check_discord_rate_limit(request, "outbox", limit=360)
    outbox = await bg(load_collection_sync, DISCORD_OUTBOX_FILE, "outbox", 1000)
    if status:
        wanted = {s.strip() for s in status.split(",") if s.strip()}
        outbox = [x for x in outbox if x.get("status") in wanted]
    return {"items": outbox[: max(1, min(limit, 100))]}


@app.patch("/api/discord/outbox/{item_id}", dependencies=[Depends(require_discord_bot_key)])
async def discord_outbox_patch(item_id: str, patch: DiscordOutboxPatchIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "outbox-patch", limit=360)
    item = await bg(update_discord_outbox_sync, item_id, patch)
    return {"ok": True, "item": item}


@app.post("/api/discord/heartbeat", dependencies=[Depends(require_discord_bot_key)])
async def discord_heartbeat(data: DiscordHeartbeatIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "heartbeat", limit=120)
    state = data.model_dump()
    state["updatedAt"] = now_ts()
    await bg(write_discord_state_sync, state)
    return {"ok": True}


@app.post("/api/discord/actions", dependencies=[Depends(require_discord_bot_key)])
async def discord_action(data: DiscordActionIn, request: Request) -> dict[str, Any]:
    check_discord_rate_limit(request, "actions", limit=240)
    item = data.model_dump()
    item["id"] = secrets.token_hex(8)
    item["createdAt"] = now_ts()
    await bg(append_jsonl, DISCORD_ACTIONS_FILE, item)
    await bg(pg_log_bridge_event, "discord", data.action, item)
    audit_event(f"discord:{data.discord_user_id}", f"discord.{data.action}", target=f"{data.object_type}:{data.object_id}", details={"username": data.discord_username, "note": data.note})
    append_panel_event("discord", data.action, actor=data.discord_username or data.discord_user_id, target=f"{data.object_type}:{data.object_id}", metadata=data.metadata, tags=["discord", data.object_type])
    action = data.action.lower()
    if data.object_type == "application":
        status_map = {
            "approve": "approved",
            "reject": "rejected",
            "request_clarification": "needs_clarification",
            "clarify": "needs_clarification",
        }
        if action in status_map:
            payload = DiscordObjectStatusIn(status=status_map[action], reason=data.note, discord_user_id=data.discord_user_id, discord_username=data.discord_username, metadata=data.metadata)
            updated = await bg(update_application_status_sync, data.object_id, payload, f"discord:{data.discord_user_id}", "discord")
            item["object"] = updated
    elif data.object_type == "report":
        status_map = {
            "take": "in_progress",
            "close": "closed",
            "reject": "rejected",
            "create_investigation": "investigation",
            "reply": "answered",
        }
        if action in status_map:
            payload = DiscordObjectStatusIn(status=status_map[action], reason=data.note, response=data.note if action == "reply" else None, discord_user_id=data.discord_user_id, discord_username=data.discord_username, metadata=data.metadata)
            updated = await bg(update_report_status_sync, data.object_id, payload, f"discord:{data.discord_user_id}", "discord")
            item["object"] = updated
    return {"ok": True, "action": item}


def strip_minecraft_format(text: str) -> str:
    value = str(text or "")
    value = re.sub(r"(?i)&[0-9A-FK-ORX]", "", value)
    value = re.sub(r"(?i)§[0-9A-FK-ORX]", "", value)
    return value.strip()


def donation_catalog_snapshot_sync() -> dict[str, Any]:
    return donation_catalog_snapshot(ARTIFACTS_ITEMS_FILE, ADMIN_PUBLIC_BASE_URL.rstrip("/"))


def ar_catalog_snapshot_sync() -> dict[str, Any]:
    return ar_catalog_snapshot(ARTIFACTS_ITEMS_FILE)


def admin_gift_catalog_snapshot_sync() -> dict[str, Any]:
    return admin_gift_catalog_snapshot(ARTIFACTS_ITEMS_FILE)


def plugin_registry_audit_sync(plugin_id: str, limit: int = 80) -> list[dict[str, Any]]:
    rows = read_site_audit_rows(max(1, min(limit, 200)), "plugin.registry", "")
    safe_plugin_id = str(plugin_id or "").strip()
    out: list[dict[str, Any]] = []
    for row in rows:
        details = row.get("details") if isinstance(row.get("details"), dict) else {}
        if str(row.get("target") or "") == safe_plugin_id or str(details.get("pluginId") or "") == safe_plugin_id:
            out.append(row)
    return out


def donation_item_page_url(item_id: str) -> str:
    catalog = donation_catalog_snapshot_sync()
    base = str(catalog.get("websiteBaseUrl") or ADMIN_PUBLIC_BASE_URL or "").rstrip("/")
    return f"{base}/#donation-shop?item={quote(str(item_id or '').strip().lower())}"


def donation_payment_page_url(session_id: str) -> str:
    catalog = donation_catalog_snapshot_sync()
    base = str(catalog.get("websiteBaseUrl") or ADMIN_PUBLIC_BASE_URL or "").rstrip("/")
    return f"{base}/#donation-balance?session={quote(str(session_id or '').strip())}"


def generate_mock_sbp_qr_payload(player_uuid: str, amount: int, session_id: str) -> str:
    return f"SBP|MOCK|copimine|{player_uuid}|{int(amount)}|{session_id}"


def render_qr_png_bytes(payload: str) -> bytes:
    if not qrcode:
        raise HTTPException(status_code=503, detail="Локальная QR-генерация недоступна")
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(payload)
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white")
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def read_player_donation_owned_sync(player_uuid: str, limit: int = 80) -> dict[str, Any]:
    if not pg_ready() or not player_uuid:
        return {"claims": [], "instances": [], "summary": {}}
    safe_limit = max(1, min(limit, 200))
    catalog_by_id = dict((donation_catalog_snapshot_sync().get("byId") or {}))
    donation_item_ids = [str(item_id or "").strip().lower() for item_id in catalog_by_id.keys() if str(item_id or "").strip()]
    with auth_conn() as conn:
        claims = conn.execute(
            """
            SELECT c.id,c.item_id,c.amount,c.status,c.purchase_id,c.claimed_at,c.created_at,c.updated_at,
                   p.status AS purchase_status,p.price_donation,p.source AS purchase_source,p.created_at AS purchase_created_at
            FROM donation_item_claims c
            LEFT JOIN donation_purchases p ON p.id=c.purchase_id
            WHERE c.player_uuid=%s
            ORDER BY c.created_at DESC
            LIMIT %s
            """,
            (player_uuid, safe_limit),
        ).fetchall()
        instances = []
        if donation_item_ids:
            instances = conn.execute(
                """
                SELECT unique_item_id,item_id,purchase_id,status,repaired_count,created_at,updated_at
                FROM artifact_item_instances
                WHERE owner_uuid=%s AND item_id = ANY(%s)
                ORDER BY updated_at DESC
                LIMIT %s
                """,
                (player_uuid, donation_item_ids, safe_limit),
            ).fetchall()
    summary = {
        "active": sum(1 for row in instances if str(row.get("status") or "").upper() == "ACTIVE"),
        "reclaimable": sum(1 for row in instances if str(row.get("status") or "").upper() == "LOST_RECLAIMABLE"),
        "claimPending": sum(1 for row in claims if str(row.get("status") or "").upper() in {"UNCLAIMED", "RESERVED", "DELIVERING", "DELIVERY_REVIEW"}),
    }
    claim_rows: list[dict[str, Any]] = []
    for row in claims:
        item = dict(row)
        meta = catalog_by_id.get(str(item.get("item_id") or "").strip().lower(), {})
        item["display_name"] = meta.get("display_name") or item.get("item_id") or "item"
        item.pop("id", None)
        item.pop("purchase_id", None)
        claim_rows.append(item)
    instance_rows: list[dict[str, Any]] = []
    for row in instances:
        item = dict(row)
        meta = catalog_by_id.get(str(item.get("item_id") or "").strip().lower(), {})
        item["display_name"] = meta.get("display_name") or item.get("item_id") or "item"
        item.pop("purchase_id", None)
        item.pop("unique_item_id", None)
        instance_rows.append(item)
    return {"claims": claim_rows, "instances": instance_rows, "summary": summary}


def donation_entitlement_conflict_sync(conn: Any, player_uuid: str, item_id: str) -> bool:
    claim_row = conn.execute(
        """
        SELECT 1
        FROM donation_item_claims
        WHERE player_uuid=%s
          AND item_id=%s
          AND status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')
        LIMIT 1
        """,
        (player_uuid, item_id),
    ).fetchone()
    if claim_row:
        return True
    instance_row = conn.execute(
        """
        SELECT 1
        FROM artifact_item_instances
        WHERE owner_uuid=%s
          AND item_id=%s
          AND status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')
        LIMIT 1
        """,
        (player_uuid, item_id),
    ).fetchone()
    return bool(instance_row)


def lock_donation_entitlement_sync(conn: Any, player_uuid: str, item_id: str) -> None:
    conn.execute(
        "SELECT pg_advisory_xact_lock(hashtext(%s))",
        (f"donation-entitlement:{str(player_uuid or '').strip()}:{str(item_id or '').strip().lower()}",),
    ).fetchone()


def lock_donation_idempotency_sync(conn: Any, scope: str, key: str) -> None:
    normalized_scope = str(scope or "").strip().lower() or "donation"
    normalized_key = str(key or "").strip()
    conn.execute(
        "SELECT pg_advisory_xact_lock(hashtext(%s))",
        (f"{normalized_scope}:{normalized_key}",),
    ).fetchone()


def read_donation_balance_sync(player_uuid: str, player_name: str = "") -> dict[str, Any]:
    if not pg_ready() or not player_uuid:
        return {"balance": 0, "player_uuid": player_uuid, "player_name": player_name}
    now = donation_now_ms()
    with auth_conn() as conn:
        conn.execute(
            """
            INSERT INTO donation_accounts(player_uuid,player_name,balance,created_at,updated_at)
            VALUES(%s,%s,0,%s,%s)
            ON CONFLICT(player_uuid) DO UPDATE
            SET player_name=EXCLUDED.player_name,
                updated_at=EXCLUDED.updated_at
            """,
            (player_uuid, player_name or "", now, now),
        )
        row = conn.execute("SELECT player_uuid,player_name,balance,created_at,updated_at FROM donation_accounts WHERE player_uuid=%s", (player_uuid,)).fetchone()
        return normalize_donation_row_timestamps(
            dict(row or {"player_uuid": player_uuid, "player_name": player_name, "balance": 0, "created_at": now, "updated_at": now}),
            "created_at",
            "updated_at",
        )


def read_donation_history_sync(player_uuid: str, limit: int = 40) -> list[dict[str, Any]]:
    if not pg_ready() or not player_uuid:
        return []
    with auth_conn() as conn:
        rows = conn.execute(
            "SELECT id,delta,balance_after,reason,actor,source,created_at FROM donation_balance_ledger WHERE player_uuid=%s ORDER BY created_at DESC LIMIT %s",
            (player_uuid, max(1, min(limit, 200))),
        ).fetchall()
        history: list[dict[str, Any]] = []
        for row in rows:
            item = dict(row)
            item.pop("actor", None)
            item.pop("source", None)
            history.append(normalize_donation_row_timestamps(item, "created_at"))
        return history


def read_donation_claims_sync(player_uuid: str, limit: int = 40) -> list[dict[str, Any]]:
    if not pg_ready() or not player_uuid:
        return []
    with auth_conn() as conn:
        rows = conn.execute(
            """
            SELECT c.id,c.item_id,c.amount,c.status,c.purchase_id,c.claimed_at,c.created_at,c.updated_at,
                   p.status AS purchase_status,p.price_donation
            FROM donation_item_claims c
            LEFT JOIN donation_purchases p ON p.id=c.purchase_id
            WHERE c.player_uuid=%s
            ORDER BY c.created_at DESC
            LIMIT %s
            """,
            (player_uuid, max(1, min(limit, 200))),
        ).fetchall()
        return [normalize_donation_row_timestamps(dict(r), "claimed_at", "created_at", "updated_at") for r in rows]


def donation_session_is_expired(row: Mapping[str, Any] | dict[str, Any], current_ts: int | None = None) -> bool:
    status = str(row_get(row, "status", "") or "").upper()
    expires_at = donation_epoch_ms(row_get(row, "expires_at"))
    safe_now = int(current_ts or donation_now_ms())
    return status in {"CREATED", "PENDING"} and expires_at > 0 and expires_at <= safe_now


def normalize_donation_session_row(conn: Any, row: Mapping[str, Any] | dict[str, Any], current_ts: int | None = None) -> dict[str, Any]:
    item = normalize_donation_row_timestamps(dict(row or {}), "created_at", "expires_at", "paid_at", "cancelled_at", "updated_at")
    safe_now = int(current_ts or donation_now_ms())
    session_id = str(item.get("id") or "")
    if session_id and donation_session_is_expired(item, safe_now):
        item["status"] = "EXPIRED"
        item["effective_status"] = "EXPIRED"
    item["session_id"] = session_id
    item["session_code"] = session_id[-8:]
    item["payment_url"] = donation_payment_page_url(session_id)
    item["confirmation_url"] = str(item.get("provider_confirmation_url") or "").strip()
    return item


def read_donation_sessions_sync(limit: int = 100) -> list[dict[str, Any]]:
    if not pg_ready():
        return []
    with auth_conn() as conn:
        rows = conn.execute(
            """
            SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,provider_payment_id,provider_confirmation_url,created_at,expires_at,paid_at,cancelled_at,updated_at
            FROM donation_payment_sessions
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (max(1, min(limit, 200)),),
        ).fetchall()
        out = []
        safe_now = donation_now_ms()
        for row in rows:
            out.append(normalize_donation_session_row(conn, row, safe_now))
        return out


def public_player_donation_session(session: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    item = dict(session or {})
    item.pop("player_uuid", None)
    item.pop("qr_payload", None)
    item.pop("provider_payment_id", None)
    item.pop("callback_payload_json", None)
    return item


def read_donation_admin_overview_sync(limit: int = 100) -> dict[str, Any]:
    if not pg_ready():
        return {
            "summary": {"accounts": 0, "totalBalance": 0, "unclaimedItems": 0, "openSessions": 0, "paidPurchases": 0},
            "balances": [],
            "ledger": [],
            "claims": [],
            "sessions": [],
        }
    safe_limit = max(1, min(limit, 200))
    safe_now = donation_now_ms()
    catalog_by_id = dict((donation_catalog_snapshot_sync().get("byId") or {}))
    with auth_conn() as conn:
        summary_row = conn.execute(
            """
            SELECT
                (SELECT COUNT(*) FROM donation_accounts) AS accounts,
                (SELECT COALESCE(SUM(balance),0) FROM donation_accounts) AS total_balance,
                (SELECT COUNT(*) FROM donation_item_claims WHERE status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')) AS unclaimed_items,
                (SELECT COUNT(*) FROM donation_payment_sessions WHERE status IN ('CREATED','PENDING') AND (expires_at=0 OR expires_at>%s)) AS open_sessions,
                (SELECT COUNT(*) FROM donation_purchases WHERE status IN ('PAID','CLAIM_PENDING','CLAIMED','DELIVERY_REVIEW')) AS paid_purchases
            """,
            (safe_now,),
        ).fetchone() or {}
        balances = conn.execute(
            """
            SELECT player_uuid,player_name,balance,created_at,updated_at
            FROM donation_accounts
            ORDER BY balance DESC, updated_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        ledger = conn.execute(
            """
            SELECT id,player_uuid,delta,balance_after,reason,actor,source,created_at
            FROM donation_balance_ledger
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        claims = conn.execute(
            """
            SELECT id,player_uuid,item_id,amount,status,purchase_id,actor,claimed_at,created_at,updated_at
            FROM donation_item_claims
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        sessions = conn.execute(
            """
            SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,created_at,expires_at,paid_at,cancelled_at,updated_at
            FROM donation_payment_sessions
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        ).fetchall()
        normalized_sessions = [normalize_donation_session_row(conn, r, safe_now) for r in sessions]
    normalized_balances = [normalize_donation_row_timestamps(dict(r), "created_at", "updated_at") for r in balances]
    normalized_ledger = [normalize_donation_row_timestamps(dict(r), "created_at") for r in ledger]
    claim_rows: list[dict[str, Any]] = []
    for row in claims:
        item = normalize_donation_row_timestamps(dict(row), "claimed_at", "created_at", "updated_at")
        meta = catalog_by_id.get(str(item.get("item_id") or "").strip().lower(), {})
        item["display_name"] = meta.get("display_name") or item.get("item_id") or "item"
        claim_rows.append(item)
    return {
        "summary": {
            "accounts": int(summary_row.get("accounts") or 0),
            "totalBalance": int(summary_row.get("total_balance") or 0),
            "unclaimedItems": int(summary_row.get("unclaimed_items") or 0),
            "openSessions": int(summary_row.get("open_sessions") or 0),
            "paidPurchases": int(summary_row.get("paid_purchases") or 0),
        },
        "balances": normalized_balances,
        "ledger": normalized_ledger,
        "claims": claim_rows,
        "sessions": normalized_sessions,
    }


def admin_add_donation_balance_sync(player_uuid: str, player_name: str, amount: int, reason: str, actor: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    if amount <= 0:
        raise HTTPException(status_code=400, detail="Сумма должна быть больше нуля")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    safe_key = str(idempotency_key or "").strip()
    if len(safe_key) < 8 or len(safe_key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    with auth_conn() as conn:
        lock_donation_idempotency_sync(conn, "donation-admin-topup", safe_key)
        existing = conn.execute(
            """
            SELECT id,player_uuid,delta,balance_after,reason,actor,source,created_at
            FROM donation_balance_ledger
            WHERE idempotency_key=%s
            LIMIT 1
            """,
            (safe_key,),
        ).fetchone()
        if existing:
            row = dict(existing)
            if str(row.get("player_uuid") or "") != player_uuid:
                raise HTTPException(status_code=409, detail="idempotency_key уже используется другой операцией")
            if int(row.get("delta") or 0) != int(amount) or str(row.get("reason") or "") != reason or str(row.get("source") or "") != "admin-web":
                raise HTTPException(status_code=409, detail="idempotency_key уже занят операцией с другими параметрами")
            return {
                "ok": True,
                "ledgerId": str(row.get("id") or ""),
                "balanceAfter": int(row.get("balance_after") or 0),
                "idempotent": True,
            }
        conn.execute(
            """
            INSERT INTO donation_accounts(player_uuid,player_name,balance,created_at,updated_at)
            VALUES(%s,%s,0,%s,%s)
            ON CONFLICT(player_uuid) DO UPDATE
            SET player_name=EXCLUDED.player_name,
                updated_at=EXCLUDED.updated_at
            """,
            (player_uuid, player_name, now, now),
        )
        row = conn.execute("SELECT COALESCE(balance,0) AS balance FROM donation_accounts WHERE player_uuid=%s FOR UPDATE", (player_uuid,)).fetchone()
        before = int((row or {"balance": 0})["balance"] or 0)
        after = before + int(amount)
        ledger_id = f"web-don-{secrets.token_hex(8)}"
        conn.execute("UPDATE donation_accounts SET balance=%s,updated_at=%s,player_name=%s WHERE player_uuid=%s", (after, now, player_name, player_uuid))
        conn.execute(
            "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (ledger_id, player_uuid, int(amount), after, reason, actor, "admin-web", safe_key, now),
        )
        return {"ok": True, "ledgerId": ledger_id, "balanceBefore": before, "balanceAfter": after}


def admin_set_donation_balance_sync(player_uuid: str, player_name: str, balance: int, reason: str, actor: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    target_balance = int(balance or 0)
    if target_balance < 0:
        raise HTTPException(status_code=400, detail="Баланс не может быть отрицательным")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    safe_key = str(idempotency_key or "").strip()
    if len(safe_key) < 8 or len(safe_key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    with auth_conn() as conn:
        lock_donation_idempotency_sync(conn, "donation-admin-set", safe_key)
        existing = conn.execute(
            """
            SELECT id,player_uuid,delta,balance_after,reason,actor,source,created_at
            FROM donation_balance_ledger
            WHERE idempotency_key=%s
            LIMIT 1
            """,
            (f"donation-set-{safe_key}",),
        ).fetchone()
        if existing:
            row = dict(existing)
            if str(row.get("player_uuid") or "") != player_uuid:
                raise HTTPException(status_code=409, detail="idempotency_key уже используется другой операцией")
            return {
                "ok": True,
                "ledgerId": str(row.get("id") or ""),
                "balanceAfter": int(row.get("balance_after") or 0),
                "idempotent": True,
            }
        ensure_donation_account_row(conn, player_uuid, player_name)
        row = conn.execute("SELECT COALESCE(balance,0) AS balance FROM donation_accounts WHERE player_uuid=%s FOR UPDATE", (player_uuid,)).fetchone()
        before = int((row or {"balance": 0})["balance"] or 0)
        after = target_balance
        delta = after - before
        ledger_id = f"web-don-set-{secrets.token_hex(8)}"
        conn.execute("UPDATE donation_accounts SET balance=%s,updated_at=%s,player_name=%s WHERE player_uuid=%s", (after, now, player_name, player_uuid))
        conn.execute(
            "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (ledger_id, player_uuid, delta, after, reason, actor, "admin-web", f"donation-set-{safe_key}", now),
        )
        conn.commit()
    audit_event(actor, "donation.balance.set", target=player_name, details={"uuid": player_uuid, "before": before, "after": after, "delta": delta, "reason": reason})
    return {"ok": True, "ledgerId": ledger_id, "balanceBefore": before, "balanceAfter": after, "delta": delta}


def ensure_donation_account_row(conn: Any, player_uuid: str, player_name: str) -> None:
    now = donation_now_ms()
    conn.execute(
        """
        INSERT INTO donation_accounts(player_uuid,player_name,balance,created_at,updated_at)
        VALUES(%s,%s,0,%s,%s)
        ON CONFLICT(player_uuid) DO UPDATE
        SET player_name=EXCLUDED.player_name,
            updated_at=EXCLUDED.updated_at
        """,
        (player_uuid, player_name or "", now, now),
    )


def donation_yookassa_gateway() -> YooKassaGateway:
    return YooKassaGateway(YOOKASSA_SETTINGS)


def yookassa_http_error(error: YooKassaGatewayError) -> HTTPException:
    if error.code in {"not_configured", "http_client_missing"}:
        return HTTPException(status_code=503, detail="Оплата через ЮKassa пока не настроена")
    if error.code in {"invalid_amount", "invalid_idempotency_key", "invalid_session", "invalid_payment"}:
        return HTTPException(status_code=400, detail=str(error))
    if error.code == "not_paid":
        return HTTPException(status_code=409, detail=str(error))
    return HTTPException(status_code=502, detail="Не удалось подтвердить платёж через ЮKassa")


def create_donation_session_sync(player_uuid: str, player_name: str, amount: int, actor: str, source: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(player_uuid or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=409, detail="Minecraft account is not linked")
    safe_amount = int(amount or 0)
    if safe_amount not in DONATION_FIXED_PACKS:
        raise HTTPException(status_code=400, detail="Разрешены только пакеты 50 / 100 / 250 / 500 / 1000")
    key = str(idempotency_key or "").strip()
    if len(key) < 8 or len(key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    expires_at = now + DONATION_SESSION_TTL_MS
    session: dict[str, Any]
    with auth_conn() as conn:
        lock_donation_idempotency_sync(conn, "donation-session", key)
        existing = conn.execute(
            "SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,qr_payload,provider_payment_id,provider_confirmation_url,created_at,expires_at,paid_at,cancelled_at,updated_at FROM donation_payment_sessions WHERE idempotency_key=%s LIMIT 1",
            (key,),
        ).fetchone()
        if existing:
            session = dict(existing)
            if str(session.get("player_uuid") or "") != player_uuid:
                raise HTTPException(status_code=409, detail="idempotency_key уже используется другой платёжной сессией")
            if int(session.get("amount_rub") or session.get("amount") or 0) != safe_amount:
                raise HTTPException(status_code=409, detail="idempotency_key уже занят платёжной сессией с другой суммой")
        else:
            session_id = f"don-session-{secrets.token_hex(8)}"
            is_yookassa = DONATION_PROVIDER == "YOOKASSA"
            qr_payload = "" if is_yookassa else generate_mock_sbp_qr_payload(player_uuid, safe_amount, session_id)
            status = "CREATED" if is_yookassa else "PENDING"
            conn.execute(
                """
                INSERT INTO donation_payment_sessions(
                    id,player_uuid,player_name,provider,amount,amount_rub,donation_units,currency,status,
                    qr_payload,qr_image_path,provider_payment_id,provider_confirmation_url,callback_payload_json,
                    idempotency_key,created_at,expires_at,paid_at,cancelled_at,updated_at
                ) VALUES(%s,%s,%s,%s,%s,%s,%s,'RUB',%s,%s,'','','','',%s,%s,%s,0,0,%s)
                """,
                (session_id, player_uuid, player_name or "", DONATION_PROVIDER, safe_amount, safe_amount, safe_amount, status, qr_payload, key, now, expires_at, now),
            )
            session = {
                "id": session_id,
                "player_uuid": player_uuid,
                "player_name": player_name,
                "provider": DONATION_PROVIDER,
                "amount": safe_amount,
                "amount_rub": safe_amount,
                "donation_units": safe_amount,
                "status": status,
                "qr_payload": qr_payload,
                "provider_payment_id": "",
                "provider_confirmation_url": "",
                "created_at": now,
                "expires_at": expires_at,
                "paid_at": 0,
                "cancelled_at": 0,
                "updated_at": now,
            }
    session_id = str(session.get("id") or "")
    if str(session.get("provider") or "").upper() == "YOOKASSA" and not str(session.get("provider_payment_id") or ""):
        try:
            payment = donation_yookassa_gateway().create_payment(
                idempotency_key=f"copimine-{session_id}",
                amount_rub=safe_amount,
                description="Пополнение CopiMine Donation",
                session_id=session_id,
                player_uuid=player_uuid,
            )
        except YooKassaGatewayError as error:
            with auth_conn() as conn:
                conn.execute(
                    "UPDATE donation_payment_sessions SET callback_payload_json=%s,updated_at=%s WHERE id=%s AND provider='YOOKASSA'",
                    (json.dumps({"provider": "YOOKASSA", "error": error.code}, ensure_ascii=False), donation_now_ms(), session_id),
                )
            raise yookassa_http_error(error) from error
        with auth_conn() as conn:
            row = conn.execute("SELECT * FROM donation_payment_sessions WHERE id=%s FOR UPDATE", (session_id,)).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Платёжная сессия не найдена")
            session = dict(row)
            known_payment_id = str(session.get("provider_payment_id") or "")
            if known_payment_id and known_payment_id != payment.payment_id:
                raise HTTPException(status_code=409, detail="Сессия уже привязана к другому платежу. Нужна ручная проверка.")
            update_now = donation_now_ms()
            conn.execute(
                "UPDATE donation_payment_sessions SET provider_payment_id=%s,provider_confirmation_url=%s,status=%s,callback_payload_json=%s,updated_at=%s WHERE id=%s",
                (
                    payment.payment_id,
                    payment.confirmation_url,
                    "PENDING",
                    json.dumps({"provider": "YOOKASSA", "paymentId": payment.payment_id, "status": payment.status}, ensure_ascii=False),
                    update_now,
                    session_id,
                ),
            )
            session.update({
                "provider_payment_id": payment.payment_id,
                "provider_confirmation_url": payment.confirmation_url,
                "status": "PENDING",
                "updated_at": update_now,
            })
        if payment.paid and payment.status == "succeeded":
            return mark_donation_session_paid_sync(
                session_id,
                "yookassa:create",
                "provider-created-succeeded-payment",
                expected_provider_payment_id=payment.payment_id,
            )
    audit_event(actor, "donation.session.create", target=player_name or player_uuid, details={"sessionId": session_id, "amount": safe_amount, "source": source})
    append_panel_event("donation", "session_create", actor=actor, target=player_name or player_uuid, metadata={"sessionId": session_id, "amount": safe_amount, "source": source}, tags=["donation", "payment"])
    return normalize_donation_session_row(None, session, donation_now_ms())


def read_donation_session_sync(session_id: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    with auth_conn() as conn:
        row = conn.execute(
            "SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,qr_payload,provider_payment_id,provider_confirmation_url,created_at,expires_at,paid_at,cancelled_at,updated_at FROM donation_payment_sessions WHERE id=%s LIMIT 1",
            (session_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Платёжная сессия не найдена")
        return normalize_donation_session_row(conn, row)


def mark_donation_session_paid_sync(
    session_id: str,
    actor: str,
    note: str = "",
    *,
    expected_provider_payment_id: str = "",
) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    now = donation_now_ms()
    with auth_conn() as conn:
        row = conn.execute(
            "SELECT * FROM donation_payment_sessions WHERE id=%s FOR UPDATE",
            (session_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Платёжная сессия не найдена")
        data = dict(row)
        status = str(data.get("status") or "").upper()
        provider = str(data.get("provider") or "MOCK_SBP").upper()
        stored_provider_payment_id = str(data.get("provider_payment_id") or "").strip()
        verified_provider_payment_id = str(expected_provider_payment_id or "").strip()
        if provider == "YOOKASSA":
            if not verified_provider_payment_id:
                raise HTTPException(status_code=409, detail="Платёж ЮKassa подтверждается только webhook-ом провайдера")
            if stored_provider_payment_id != verified_provider_payment_id:
                raise HTTPException(status_code=409, detail="Платёж ЮKassa не совпадает с сессией")
        elif verified_provider_payment_id:
            raise HTTPException(status_code=409, detail="Провайдер не совпадает с платёжной сессией")
        if donation_session_is_expired(data, now) and not verified_provider_payment_id:
            conn.execute("UPDATE donation_payment_sessions SET status='EXPIRED',updated_at=%s WHERE id=%s AND status IN ('CREATED','PENDING')", (now, session_id))
            raise HTTPException(status_code=409, detail="Сессия уже истекла")
        if status == "CANCELLED":
            raise HTTPException(status_code=409, detail="Сессия уже отменена")
        if status == "EXPIRED":
            raise HTTPException(status_code=409, detail="Сессия уже истекла")
        player_uuid = str(data.get("player_uuid") or "")
        player_name = str(data.get("player_name") or "")
        if not player_uuid:
            audit_event(actor, "donation.session.manual_review", target=player_name or session_id, details={"sessionId": session_id, "reason": "missing_player_uuid", "note": note})
            raise HTTPException(status_code=409, detail="Платёжная сессия не привязана к Minecraft-аккаунту. Нужна ручная проверка.")
        amount = int(data.get("donation_units") or data.get("amount") or 0)
        paid_at = donation_epoch_ms(data.get("paid_at"))
        ensure_donation_account_row(conn, player_uuid, player_name)
        account = conn.execute("SELECT COALESCE(balance,0) AS balance FROM donation_accounts WHERE player_uuid=%s FOR UPDATE", (player_uuid,)).fetchone() or {"balance": 0}
        before = int(account["balance"] or 0)
        ledger_key = f"donation-session-paid-{session_id}"
        ledger = conn.execute("SELECT id,balance_after FROM donation_balance_ledger WHERE idempotency_key=%s LIMIT 1", (ledger_key,)).fetchone()
        if status == "PAID":
            if not ledger:
                audit_event(actor, "donation.session.manual_review", target=player_name or player_uuid, details={"sessionId": session_id, "reason": "paid_without_ledger", "note": note})
                raise HTTPException(status_code=409, detail="Сессия уже помечена как оплаченная, но ledger-запись отсутствует. Нужна ручная проверка.")
            after = int(ledger["balance_after"] or before)
            return {"ok": True, "sessionId": session_id, "amount": amount, "balanceAfter": after, "status": "PAID", "paidAt": paid_at}
        if ledger:
            after = int(ledger["balance_after"] or before)
        else:
            after = before + amount
            ledger_id = f"don-ledger-{secrets.token_hex(8)}"
            conn.execute("UPDATE donation_accounts SET balance=%s,updated_at=%s,player_name=%s WHERE player_uuid=%s", (after, now, player_name, player_uuid))
            conn.execute(
                "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (ledger_id, player_uuid, amount, after, "DONATION_TOPUP", actor, "yookassa" if provider == "YOOKASSA" else "mock_sbp", ledger_key, now),
            )
        if status != "PAID":
            callback = json.dumps(
                {"provider": provider, "paymentId": verified_provider_payment_id, "status": "PAID"},
                ensure_ascii=False,
            )
            conn.execute(
                "UPDATE donation_payment_sessions SET status='PAID',paid_at=%s,callback_payload_json=%s,updated_at=%s WHERE id=%s",
                (now, callback, now, session_id),
            )
    audit_event(actor, "donation.session.mark_paid", target=player_name or player_uuid, details={"sessionId": session_id, "amount": amount, "note": note})
    append_panel_event("donation", "session_paid", actor=actor, target=player_name or player_uuid, metadata={"sessionId": session_id, "amount": amount}, tags=["donation", "payment"])
    return {"ok": True, "sessionId": session_id, "amount": amount, "balanceAfter": after, "status": "PAID", "paidAt": now if status != "PAID" else paid_at}


def confirm_yookassa_payment_sync(payment_id: str, webhook_event: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    safe_payment_id = str(payment_id or "").strip()
    if len(safe_payment_id) < 8 or len(safe_payment_id) > 128:
        raise HTTPException(status_code=400, detail="Некорректный идентификатор платежа")
    with auth_conn() as conn:
        row = conn.execute(
            "SELECT id,player_uuid,amount_rub,amount,provider,provider_payment_id FROM donation_payment_sessions WHERE provider='YOOKASSA' AND provider_payment_id=%s FOR UPDATE",
            (safe_payment_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Платёж ЮKassa не связан с CopiMine-сессией")
        session = dict(row)
    session_id = str(session.get("id") or "")
    expected_amount = int(session.get("amount_rub") or session.get("amount") or 0)
    try:
        verified = donation_yookassa_gateway().verify_succeeded_payment(safe_payment_id, expected_amount, session_id)
    except YooKassaGatewayError as error:
        raise yookassa_http_error(error) from error
    with auth_conn() as conn:
        conn.execute(
            "UPDATE donation_payment_sessions SET callback_payload_json=%s,updated_at=%s WHERE id=%s AND provider_payment_id=%s",
            (
                json.dumps({"provider": "YOOKASSA", "event": str(webhook_event or ""), "paymentId": verified.payment_id, "status": verified.status}, ensure_ascii=False),
                donation_now_ms(),
                session_id,
                verified.payment_id,
            ),
        )
    return mark_donation_session_paid_sync(
        session_id,
        "yookassa:webhook",
        str(webhook_event or "payment.succeeded"),
        expected_provider_payment_id=verified.payment_id,
    )


def cancel_donation_session_sync(session_id: str, actor: str, note: str = "") -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    now = donation_now_ms()
    with auth_conn() as conn:
        row = conn.execute(
            "SELECT id,player_uuid,player_name,provider,status,expires_at,updated_at FROM donation_payment_sessions WHERE id=%s FOR UPDATE",
            (session_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Платёжная сессия не найдена")
        data = dict(row)
        status = str(data.get("status") or "").upper()
        if donation_session_is_expired(data, now):
            conn.execute("UPDATE donation_payment_sessions SET status='EXPIRED',updated_at=%s WHERE id=%s AND status IN ('CREATED','PENDING')", (now, session_id))
            raise HTTPException(status_code=409, detail="Сессия уже истекла")
        if status == "PAID":
            raise HTTPException(status_code=409, detail="Оплаченную сессию нельзя отменить")
        if str(data.get("provider") or "").upper() == "YOOKASSA":
            raise HTTPException(status_code=409, detail="Сессию ЮKassa отменяй в личном кабинете провайдера")
        if status != "CANCELLED":
            conn.execute("UPDATE donation_payment_sessions SET status='CANCELLED',cancelled_at=%s,updated_at=%s WHERE id=%s", (now, now, session_id))
    audit_event(actor, "donation.session.cancel", target=str(data.get("player_name") or data.get("player_uuid") or ""), details={"sessionId": session_id, "note": note})
    append_panel_event("donation", "session_cancel", actor=actor, target=str(data.get("player_name") or data.get("player_uuid") or ""), metadata={"sessionId": session_id}, tags=["donation", "payment"])
    return {"ok": True, "sessionId": session_id, "status": "CANCELLED"}


def purchase_donation_item_sync(player_uuid: str, player_name: str, item_id: str, pin: str, actor: str, source: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(player_uuid or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=409, detail="Minecraft account is not linked")
    catalog = donation_catalog_snapshot_sync()
    item = catalog["byId"].get(str(item_id or "").strip().lower())
    if not item or not bool(item.get("enabled")):
        raise HTTPException(status_code=404, detail="Донат-предмет недоступен")
    key = str(idempotency_key or "").strip()
    if len(key) < 8 or len(key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    price = int(item.get("price_donation") or 0)
    now = donation_now_ms()
    with auth_conn() as conn:
        verify_bank_pin(
            conn,
            {
                "minecraft_uuid": player_uuid,
                "minecraft_name": player_name,
            },
            pin,
        )
        lock_donation_idempotency_sync(conn, "donation-purchase", key)
        existing = conn.execute(
            "SELECT id,player_uuid,item_id,status,price_donation,created_at FROM donation_purchases WHERE idempotency_key=%s LIMIT 1",
            (key,),
        ).fetchone()
        if existing:
            row = dict(existing)
            if str(row.get("player_uuid") or "") != player_uuid:
                raise HTTPException(status_code=409, detail="idempotency_key уже используется другой покупкой")
            if str(row.get("item_id") or "") != item["item_id"]:
                raise HTTPException(status_code=409, detail="idempotency_key уже привязан к другой покупке")
            claim = conn.execute("SELECT status FROM donation_item_claims WHERE purchase_id=%s LIMIT 1", (row["id"],)).fetchone()
            return {
                "ok": True,
                "itemId": row.get("item_id"),
                "status": row.get("status"),
                "claimStatus": row_get(claim, "status", "UNCLAIMED") or "UNCLAIMED",
                "priceDonation": int(row.get("price_donation") or 0),
                "pickupHint": "Заберите предмет в игре через лавку доната.",
            }
        lock_donation_entitlement_sync(conn, player_uuid, item["item_id"])
        if donation_entitlement_conflict_sync(conn, player_uuid, item["item_id"]):
            raise HTTPException(status_code=409, detail="У тебя уже есть активный или незавершённый donation-экземпляр этого предмета")
        ensure_donation_account_row(conn, player_uuid, player_name)
        balance_row = conn.execute("SELECT COALESCE(balance,0) AS balance FROM donation_accounts WHERE player_uuid=%s FOR UPDATE", (player_uuid,)).fetchone() or {"balance": 0}
        before = int(balance_row["balance"] or 0)
        if before < price:
            raise HTTPException(status_code=409, detail="Недостаточно donation-баланса")
        purchase_id = f"don-purchase-{secrets.token_hex(8)}"
        claim_id = f"don-claim-{secrets.token_hex(8)}"
        after = before - price
        ledger_id = f"don-ledger-{secrets.token_hex(8)}"
        conn.execute("UPDATE donation_accounts SET balance=%s,updated_at=%s,player_name=%s WHERE player_uuid=%s", (after, now, player_name, player_uuid))
        conn.execute(
            "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (ledger_id, player_uuid, -price, after, "DONATION_PURCHASE", actor, source, f"purchase-intent-{key}", now),
        )
        conn.execute(
            """
            INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at)
            VALUES(%s,%s,%s,%s,%s,%s,'CLAIM_PENDING',%s,%s,%s,%s)
            """,
            (purchase_id, player_uuid, player_name or "", item["item_id"], price, price, source, key, now, now),
        )
        conn.execute(
            """
            INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor)
            VALUES(%s,%s,%s,1,'UNCLAIMED',0,%s,%s,%s,%s)
            """,
            (claim_id, player_uuid, item["item_id"], now, now, purchase_id, actor),
        )
    audit_event(actor, "donation.purchase.create", target=player_name or player_uuid, details={"purchaseId": purchase_id, "claimId": claim_id, "itemId": item["item_id"], "price": price})
    append_panel_event("donation", "purchase_create", actor=actor, target=player_name or player_uuid, metadata={"purchaseId": purchase_id, "claimId": claim_id, "itemId": item["item_id"], "price": price}, tags=["donation", "purchase"])
    return {
        "ok": True,
        "itemId": item["item_id"],
        "status": "CLAIM_PENDING",
        "claimStatus": "UNCLAIMED",
        "priceDonation": price,
        "balanceAfter": after,
        "pickupHint": "Заберите предмет в игре через лавку доната.",
    }


def artifact_purchase_count_sync(conn: Any, item_id: str, player_uuid: str = "") -> int:
    if player_uuid:
        row = conn.execute(
            """
            SELECT COUNT(*) AS c
            FROM artifact_purchases
            WHERE player_uuid=%s
              AND item_id=%s
              AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY')
            """,
            (player_uuid, item_id),
        ).fetchone()
    else:
        row = conn.execute(
            """
            SELECT COUNT(*) AS c
            FROM artifact_purchases
            WHERE item_id=%s
              AND status IN ('PAID','DELIVERING','DELIVERED','PENDING_DELIVERY')
            """,
            (item_id,),
        ).fetchone()
    return int(row_get(row, "c", 0) or 0)


def purchase_ar_item_sync(account: dict[str, Any], data: PlayerArPurchaseIntentIn) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(account.get("minecraft_uuid") or "").strip()
    player_name = str(account.get("minecraft_name") or account.get("username") or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    catalog = ar_catalog_snapshot_sync()
    item = dict(catalog.get("byId", {}).get(str(data.item_id or "").strip().lower()) or {})
    if not item or not bool(item.get("enabled")):
        raise HTTPException(status_code=404, detail="AR-предмет недоступен")
    if str(item.get("category") or "").upper() == "RP":
        raise HTTPException(status_code=409, detail="Эта категория пока недоступна для покупки на сайте")
    price = int(item.get("price_ar") or 0)
    if price <= 0:
        raise HTTPException(status_code=409, detail="Для предмета не задана цена AR")
    key = str(data.idempotency_key or "").strip()
    if len(key) < 8 or len(key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    purchase_key = f"web-ar-purchase-{key}"
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (purchase_key,))
        existing = conn.execute(
            "SELECT purchase_id,unique_item_id,item_id,status,price_ar FROM artifact_purchases WHERE idempotency_key=%s LIMIT 1",
            (purchase_key,),
        ).fetchone()
        if existing:
            row = dict(existing)
            return {
                "ok": True,
                "itemId": row.get("item_id"),
                "purchaseId": row.get("purchase_id"),
                "uniqueItemId": row.get("unique_item_id"),
                "status": row.get("status"),
                "priceAr": int(row.get("price_ar") or 0),
                "pickupHint": "Заберите предмет в игре через /cmartifacts claim.",
            }
        verify_bank_pin(conn, account, data.pin)
        item_id = str(item.get("item_id") or "").strip()
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"artifact-purchase-supply:{item_id.lower()}",))
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"artifact-purchase-player:{player_uuid.lower()}:{item_id.lower()}",))
        supply_limit = int(item.get("supply_limit") or 0)
        per_player_limit = int(item.get("per_player_limit") or 0)
        if supply_limit > 0 and artifact_purchase_count_sync(conn, item_id) >= supply_limit:
            raise HTTPException(status_code=409, detail="Лимит поставки для этого предмета исчерпан")
        if per_player_limit > 0 and artifact_purchase_count_sync(conn, item_id, player_uuid) >= per_player_limit:
            raise HTTPException(status_code=409, detail="Персональный лимит на этот предмет уже достигнут")
        player_bank = ensure_player_bank_account(conn, player_uuid, player_name)
        treasury_bank = ensure_treasury_bank_account(conn)
        player_locked, treasury_locked = lock_bank_accounts_ordered(conn, str(player_bank["account_id"]), str(treasury_bank["account_id"]))
        before = int(player_locked.get("balance") or 0)
        if before < price:
            raise HTTPException(status_code=409, detail="Недостаточно AR на банковском счёте")
        after = before - price
        treasury_after = int(treasury_locked.get("balance") or 0) + price
        tx_id = f"ar-web-{secrets.token_hex(12)}"
        purchase_id = f"web-ar-purchase-{secrets.token_hex(10)}"
        unique_item_id = f"web-ar-item-{uuid.uuid4()}"
        delivery_id = f"web-ar-delivery-{secrets.token_hex(10)}"
        details = json.dumps({"item": item_id, "source": "site-shop", "displayName": item.get("display_name") or item_id}, ensure_ascii=False)
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (after, now, player_locked["account_id"]))
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (treasury_after, now, treasury_locked["account_id"]))
        conn.execute(
            "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR','COMMITTED',%s,%s,%s,%s)",
            (tx_id, player_locked["account_id"], treasury_locked["account_id"], price, purchase_key, now, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR_SHOP_PURCHASE',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":out", player_locked["account_id"], treasury_locked["account_id"], player_uuid, price, after, purchase_key + ":out", now, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR_SHOP_PURCHASE',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":in", treasury_locked["account_id"], player_locked["account_id"], player_uuid, price, treasury_after, purchase_key + ":in", now, player_name or player_uuid, details),
        )
        conn.execute(
            """
            INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at)
            VALUES(%s,%s,%s,%s,%s,'site-shop',%s,%s,%s,'PENDING_DELIVERY','PENDING',%s,%s)
            """,
            (purchase_id, unique_item_id, player_uuid, player_name, item_id, price, tx_id, purchase_key, now, now),
        )
        conn.execute(
            """
            INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)
            VALUES(%s,%s,%s,%s,'PENDING_DELIVERY',0,%s,%s)
            """,
            (unique_item_id, item_id, player_uuid, purchase_id, now, now),
        )
        conn.execute(
            """
            INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at)
            VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)
            """,
            (delivery_id, purchase_id, unique_item_id, player_uuid, item_id, now, now),
        )
        conn.execute(
            """
            INSERT INTO artifact_revenue_payouts(purchase_id,president_uuid,president_name,recipient_account_id,buyer_uuid,buyer_name,item_id,shop_id,amount_ar,status,bank_tx_id,idempotency_key,last_error,created_at,updated_at)
            VALUES(%s,'',%s,%s,%s,%s,%s,'site-shop',%s,'CREDITED',%s,%s,'',%s,%s)
            ON CONFLICT(purchase_id) DO NOTHING
            """,
            (purchase_id, TREASURY_ACCOUNT_LABEL, TREASURY_ACCOUNT_ID, player_uuid, player_name, item_id, price, tx_id, f"artifact-president-budget-{purchase_id}", now, now),
        )
        conn.commit()
    audit_event(str(account.get("username") or player_name or player_uuid), "artifact.purchase.web", target=player_name or player_uuid, details={"purchaseId": purchase_id, "itemId": item_id, "price": price})
    append_panel_event("artifacts", "web_purchase", actor=str(account.get("username") or player_name or player_uuid), target=player_name or player_uuid, metadata={"purchaseId": purchase_id, "itemId": item_id, "price": price}, tags=["artifacts", "shop"])
    return {
        "ok": True,
        "itemId": item_id,
        "purchaseId": purchase_id,
        "uniqueItemId": unique_item_id,
        "deliveryId": delivery_id,
        "status": "PENDING_DELIVERY",
        "priceAr": price,
        "balanceAfter": after,
        "pickupHint": "Заберите предмет в игре через /cmartifacts claim.",
    }


CART_IDEMPOTENCY_KEY_RE = re.compile(r"[A-Za-z0-9-]{8,96}")
CART_ITEM_ID_RE = re.compile(r"[a-z0-9_-]{2,120}")


def normalize_cart_checkout_input(data: PlayerCartCheckoutIn) -> tuple[list[str], str]:
    item_ids: list[str] = []
    seen: set[str] = set()
    for raw_item_id in list(data.item_ids or []):
        item_id = str(raw_item_id or "").strip().lower()
        if not CART_ITEM_ID_RE.fullmatch(item_id):
            raise HTTPException(status_code=400, detail="В корзине указан некорректный предмет")
        if item_id in seen:
            raise HTTPException(status_code=400, detail="Один и тот же предмет нельзя добавить в корзину дважды")
        seen.add(item_id)
        item_ids.append(item_id)
    if not item_ids or len(item_ids) > 12:
        raise HTTPException(status_code=400, detail="В одной корзине можно оплатить от 1 до 12 предметов")
    key = str(data.idempotency_key or "").strip()
    if not CART_IDEMPOTENCY_KEY_RE.fullmatch(key):
        raise HTTPException(status_code=400, detail="Не удалось безопасно подтвердить оплату корзины. Обновите страницу и повторите попытку")
    return item_ids, key


def checkout_ar_cart_sync(account: dict[str, Any], data: PlayerCartCheckoutIn) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(account.get("minecraft_uuid") or "").strip()
    player_name = str(account.get("minecraft_name") or account.get("username") or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    item_ids, key = normalize_cart_checkout_input(data)
    catalog = ar_catalog_snapshot_sync()
    catalog_by_id = dict(catalog.get("byId") or {})
    selected_items: list[dict[str, Any]] = []
    for item_id in item_ids:
        item = dict(catalog_by_id.get(item_id) or {})
        if not item or not bool(item.get("enabled")):
            raise HTTPException(status_code=404, detail="Один из AR-предметов больше недоступен")
        if str(item.get("category") or "").upper() == "RP":
            raise HTTPException(status_code=409, detail="Один из выбранных предметов пока нельзя купить на сайте")
        price = int(item.get("price_ar") or 0)
        if price <= 0:
            raise HTTPException(status_code=409, detail="Для одного из выбранных предметов не задана цена AR")
        selected_items.append(item)

    now = donation_now_ms()
    cart_key = f"web-ar-cart-{key}"
    item_purchase_keys = {item_id: f"{cart_key}:{item_id}" for item_id in item_ids}
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (cart_key,))
        verify_bank_pin(conn, account, data.pin)
        existing_rows = [
            dict(row)
            for row in conn.execute(
                "SELECT purchase_id,unique_item_id,player_uuid,item_id,status,price_ar FROM artifact_purchases WHERE idempotency_key LIKE %s ORDER BY item_id",
                (f"{cart_key}:%",),
            ).fetchall()
        ]
        if existing_rows:
            existing_by_id = {str(row.get("item_id") or ""): row for row in existing_rows}
            if set(existing_by_id) != set(item_ids) or len(existing_rows) != len(item_ids) or any(str(row.get("player_uuid") or "") != player_uuid for row in existing_rows):
                raise HTTPException(status_code=409, detail="Этот ключ оплаты уже использован для другой корзины")
            existing_total = sum(int(row.get("price_ar") or 0) for row in existing_rows)
            if int(data.expected_total) != existing_total:
                raise HTTPException(status_code=409, detail="Сумма повторной оплаты не совпадает с исходной корзиной")
            balance_row = conn.execute(
                "SELECT balance FROM cmv4_bank_accounts WHERE owner_uuid=%s AND account_type='PLAYER' AND currency='AR' AND status='ACTIVE' LIMIT 1",
                (player_uuid,),
            ).fetchone()
            return {
                "ok": True,
                "items": [
                    {
                        "itemId": item_id,
                        "purchaseId": existing_by_id[item_id].get("purchase_id"),
                        "uniqueItemId": existing_by_id[item_id].get("unique_item_id"),
                        "status": existing_by_id[item_id].get("status"),
                        "priceAr": int(existing_by_id[item_id].get("price_ar") or 0),
                    }
                    for item_id in item_ids
                ],
                "totalPriceAr": existing_total,
                "balanceAfter": int(row_get(balance_row, "balance", 0) or 0),
                "pickupHint": "Все оплаченные предметы уже ждут выдачи в игре через /cmartifacts claim.",
                "idempotent": True,
            }

        total_price = sum(int(item.get("price_ar") or 0) for item in selected_items)
        if int(data.expected_total) != total_price:
            raise HTTPException(status_code=409, detail="Цена предметов изменилась. Обновите корзину перед оплатой")
        for item in sorted(selected_items, key=lambda row: str(row.get("item_id") or "")):
            item_id = str(item.get("item_id") or "")
            conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"artifact-purchase-supply:{item_id.lower()}",))
            conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"artifact-purchase-player:{player_uuid.lower()}:{item_id.lower()}",))
            supply_limit = int(item.get("supply_limit") or 0)
            per_player_limit = int(item.get("per_player_limit") or 0)
            if supply_limit > 0 and artifact_purchase_count_sync(conn, item_id) >= supply_limit:
                raise HTTPException(status_code=409, detail=f"Лимит поставки предмета «{item.get('display_name') or item_id}» исчерпан")
            if per_player_limit > 0 and artifact_purchase_count_sync(conn, item_id, player_uuid) >= per_player_limit:
                raise HTTPException(status_code=409, detail=f"Персональный лимит на предмет «{item.get('display_name') or item_id}» уже достигнут")

        player_bank = ensure_player_bank_account(conn, player_uuid, player_name)
        treasury_bank = ensure_treasury_bank_account(conn)
        player_locked, treasury_locked = lock_bank_accounts_ordered(conn, str(player_bank["account_id"]), str(treasury_bank["account_id"]))
        before = int(player_locked.get("balance") or 0)
        if before < total_price:
            raise HTTPException(status_code=409, detail="Недостаточно AR на банковском счёте для оплаты всей корзины")
        after = before - total_price
        treasury_after = int(treasury_locked.get("balance") or 0) + total_price
        tx_id = f"ar-web-cart-{secrets.token_hex(12)}"
        details = json.dumps(
            {
                "items": [
                    {"itemId": str(item.get("item_id") or ""), "displayName": item.get("display_name") or "", "priceAr": int(item.get("price_ar") or 0)}
                    for item in selected_items
                ],
                "source": "site-shop-cart",
            },
            ensure_ascii=False,
        )
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (after, now, player_locked["account_id"]))
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s WHERE account_id=%s", (treasury_after, now, treasury_locked["account_id"]))
        conn.execute(
            "INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR','COMMITTED',%s,%s,%s,%s)",
            (tx_id, player_locked["account_id"], treasury_locked["account_id"], total_price, cart_key, now, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR_SHOP_CART',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":out", player_locked["account_id"], treasury_locked["account_id"], player_uuid, total_price, after, cart_key + ":out", now, player_name or player_uuid, details),
        )
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,%s,%s,'AR_SHOP_CART',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id + ":in", treasury_locked["account_id"], player_locked["account_id"], player_uuid, total_price, treasury_after, cart_key + ":in", now, player_name or player_uuid, details),
        )

        created_items: list[dict[str, Any]] = []
        for item in selected_items:
            item_id = str(item.get("item_id") or "")
            price = int(item.get("price_ar") or 0)
            purchase_id = f"web-ar-cart-purchase-{secrets.token_hex(10)}"
            unique_item_id = f"web-ar-cart-item-{uuid.uuid4()}"
            delivery_id = f"web-ar-cart-delivery-{secrets.token_hex(10)}"
            conn.execute(
                """
                INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at)
                VALUES(%s,%s,%s,%s,%s,'site-shop',%s,%s,%s,'PENDING_DELIVERY','PENDING',%s,%s)
                """,
                (purchase_id, unique_item_id, player_uuid, player_name, item_id, price, tx_id, item_purchase_keys[item_id], now, now),
            )
            conn.execute(
                """
                INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at)
                VALUES(%s,%s,%s,%s,'PENDING_DELIVERY',0,%s,%s)
                """,
                (unique_item_id, item_id, player_uuid, purchase_id, now, now),
            )
            conn.execute(
                """
                INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at)
                VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)
                """,
                (delivery_id, purchase_id, unique_item_id, player_uuid, item_id, now, now),
            )
            conn.execute(
                """
                INSERT INTO artifact_revenue_payouts(purchase_id,president_uuid,president_name,recipient_account_id,buyer_uuid,buyer_name,item_id,shop_id,amount_ar,status,bank_tx_id,idempotency_key,last_error,created_at,updated_at)
                VALUES(%s,'',%s,%s,%s,%s,%s,'site-shop',%s,'CREDITED',%s,%s,'',%s,%s)
                ON CONFLICT(purchase_id) DO NOTHING
                """,
                (purchase_id, TREASURY_ACCOUNT_LABEL, TREASURY_ACCOUNT_ID, player_uuid, player_name, item_id, price, tx_id, f"artifact-president-budget-{purchase_id}", now, now),
            )
            created_items.append(
                {
                    "itemId": item_id,
                    "purchaseId": purchase_id,
                    "uniqueItemId": unique_item_id,
                    "deliveryId": delivery_id,
                    "status": "PENDING_DELIVERY",
                    "priceAr": price,
                }
            )
        conn.commit()
    actor = str(account.get("username") or player_name or player_uuid)
    audit_event(actor, "artifact.purchase.web_cart", target=player_name or player_uuid, details={"itemIds": item_ids, "totalPriceAr": total_price, "txId": tx_id})
    append_panel_event("artifacts", "web_cart_purchase", actor=actor, target=player_name or player_uuid, metadata={"itemIds": item_ids, "totalPriceAr": total_price, "txId": tx_id}, tags=["artifacts", "shop"])
    return {
        "ok": True,
        "items": created_items,
        "totalPriceAr": total_price,
        "balanceAfter": after,
        "pickupHint": "Все оплаченные предметы уже ждут выдачи в игре через /cmartifacts claim.",
    }


def checkout_donation_cart_sync(player_uuid: str, player_name: str, data: PlayerCartCheckoutIn) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid = str(player_uuid or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    player_name = str(player_name or "").strip()
    item_ids, key = normalize_cart_checkout_input(data)
    catalog = donation_catalog_snapshot_sync()
    catalog_by_id = dict(catalog.get("byId") or {})
    selected_items: list[dict[str, Any]] = []
    for item_id in item_ids:
        item = dict(catalog_by_id.get(item_id) or {})
        if not item or not bool(item.get("enabled")):
            raise HTTPException(status_code=404, detail="Один из donation-предметов больше недоступен")
        price = int(item.get("price_donation") or 0)
        if price <= 0:
            raise HTTPException(status_code=409, detail="Для одного из выбранных предметов не задана цена donation")
        selected_items.append(item)

    now = donation_now_ms()
    cart_key = f"donation-cart-{key}"
    item_purchase_keys = {item_id: f"{cart_key}:{item_id}" for item_id in item_ids}
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        verify_bank_pin(conn, {"minecraft_uuid": player_uuid, "minecraft_name": player_name}, data.pin)
        lock_donation_idempotency_sync(conn, "donation-cart", key)
        existing_rows = [
            dict(row)
            for row in conn.execute(
                """
                SELECT p.id,p.player_uuid,p.item_id,p.status,p.price_donation,c.status AS claim_status
                FROM donation_purchases p
                LEFT JOIN donation_item_claims c ON c.purchase_id=p.id
                WHERE p.idempotency_key LIKE %s
                ORDER BY p.item_id
                """,
                (f"{cart_key}:%",),
            ).fetchall()
        ]
        if existing_rows:
            existing_by_id = {str(row.get("item_id") or ""): row for row in existing_rows}
            if set(existing_by_id) != set(item_ids) or len(existing_rows) != len(item_ids) or any(str(row.get("player_uuid") or "") != player_uuid for row in existing_rows):
                raise HTTPException(status_code=409, detail="Этот ключ оплаты уже использован для другой корзины")
            existing_total = sum(int(row.get("price_donation") or 0) for row in existing_rows)
            if int(data.expected_total) != existing_total:
                raise HTTPException(status_code=409, detail="Сумма повторной оплаты не совпадает с исходной корзиной")
            balance_row = conn.execute("SELECT balance FROM donation_accounts WHERE player_uuid=%s LIMIT 1", (player_uuid,)).fetchone()
            return {
                "ok": True,
                "items": [
                    {
                        "itemId": item_id,
                        "purchaseId": existing_by_id[item_id].get("id"),
                        "status": existing_by_id[item_id].get("status"),
                        "claimStatus": existing_by_id[item_id].get("claim_status") or "UNCLAIMED",
                        "priceDonation": int(existing_by_id[item_id].get("price_donation") or 0),
                    }
                    for item_id in item_ids
                ],
                "totalPriceDonation": existing_total,
                "balanceAfter": int(row_get(balance_row, "balance", 0) or 0),
                "pickupHint": "Все оплаченные предметы уже ждут выдачи в игре через лавку donation.",
                "idempotent": True,
            }

        total_price = sum(int(item.get("price_donation") or 0) for item in selected_items)
        if int(data.expected_total) != total_price:
            raise HTTPException(status_code=409, detail="Цена предметов изменилась. Обновите корзину перед оплатой")
        for item in sorted(selected_items, key=lambda row: str(row.get("item_id") or "")):
            item_id = str(item.get("item_id") or "")
            lock_donation_entitlement_sync(conn, player_uuid, item_id)
            if donation_entitlement_conflict_sync(conn, player_uuid, item_id):
                raise HTTPException(status_code=409, detail=f"У тебя уже есть активный или ожидающий выдачи предмет «{item.get('display_name') or item_id}»")

        ensure_donation_account_row(conn, player_uuid, player_name)
        balance_row = conn.execute("SELECT COALESCE(balance,0) AS balance FROM donation_accounts WHERE player_uuid=%s FOR UPDATE", (player_uuid,)).fetchone() or {"balance": 0}
        before = int(row_get(balance_row, "balance", 0) or 0)
        if before < total_price:
            raise HTTPException(status_code=409, detail="Недостаточно donation-баланса для оплаты всей корзины")
        after = before - total_price
        ledger_id = f"don-cart-ledger-{secrets.token_hex(8)}"
        conn.execute("UPDATE donation_accounts SET balance=%s,updated_at=%s,player_name=%s WHERE player_uuid=%s", (after, now, player_name, player_uuid))
        conn.execute(
            "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (ledger_id, player_uuid, -total_price, after, "DONATION_CART_PURCHASE", player_name or player_uuid, "player-web-cart", cart_key, now),
        )

        created_items: list[dict[str, Any]] = []
        for item in selected_items:
            item_id = str(item.get("item_id") or "")
            price = int(item.get("price_donation") or 0)
            purchase_id = f"don-cart-purchase-{secrets.token_hex(8)}"
            claim_id = f"don-cart-claim-{secrets.token_hex(8)}"
            conn.execute(
                """
                INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at)
                VALUES(%s,%s,%s,%s,%s,%s,'CLAIM_PENDING','player-web-cart',%s,%s,%s)
                """,
                (purchase_id, player_uuid, player_name, item_id, price, price, item_purchase_keys[item_id], now, now),
            )
            conn.execute(
                """
                INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor)
                VALUES(%s,%s,%s,1,'UNCLAIMED',0,%s,%s,%s,%s)
                """,
                (claim_id, player_uuid, item_id, now, now, purchase_id, player_name or player_uuid),
            )
            created_items.append(
                {
                    "itemId": item_id,
                    "purchaseId": purchase_id,
                    "claimId": claim_id,
                    "status": "CLAIM_PENDING",
                    "claimStatus": "UNCLAIMED",
                    "priceDonation": price,
                }
            )
        conn.commit()
    audit_event(player_name or player_uuid, "donation.purchase.web_cart", target=player_name or player_uuid, details={"itemIds": item_ids, "totalPriceDonation": total_price, "ledgerId": ledger_id})
    append_panel_event("donation", "web_cart_purchase", actor=player_name or player_uuid, target=player_name or player_uuid, metadata={"itemIds": item_ids, "totalPriceDonation": total_price, "ledgerId": ledger_id}, tags=["donation", "purchase"])
    return {
        "ok": True,
        "items": created_items,
        "totalPriceDonation": total_price,
        "balanceAfter": after,
        "pickupHint": "Все оплаченные предметы уже ждут выдачи в игре через лавку donation.",
    }


def admin_add_ar_balance_sync(player_uuid: str, player_name: str, amount: int, reason: str, actor: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    safe_amount = int(amount or 0)
    if safe_amount <= 0:
        raise HTTPException(status_code=400, detail="Сумма AR должна быть больше нуля")
    safe_key = str(idempotency_key or "").strip()
    if len(safe_key) < 8 or len(safe_key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"admin-ar-topup:{safe_key}",))
        existing = conn.execute("SELECT tx_id,balance_after FROM cmv4_bank_ledger WHERE idempotency_key=%s LIMIT 1", (f"admin-ar-topup-{safe_key}",)).fetchone()
        if existing:
            return {"ok": True, "txId": existing["tx_id"], "balanceAfter": int(existing["balance_after"] or 0), "idempotent": True}
        bank = ensure_player_bank_account(conn, player_uuid, player_name)
        locked = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE", (bank["account_id"],)).fetchone()
        before = int(row_get(locked, "balance", 0) or 0)
        after = before + safe_amount
        tx_id = f"admin-ar-{secrets.token_hex(12)}"
        note = json.dumps({"reason": sanitize_public_plain_text(reason, 160), "source": "admin-web"}, ensure_ascii=False)
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s,owner_name=%s WHERE account_id=%s", (after, now, player_name, bank["account_id"]))
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,'',%s,'ADMIN_AR_TOPUP',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id, bank["account_id"], player_uuid, safe_amount, after, f"admin-ar-topup-{safe_key}", now, actor, note),
        )
        conn.commit()
    audit_event(actor, "ar.balance.add", target=player_name, details={"uuid": player_uuid, "amount": safe_amount, "reason": reason})
    return {"ok": True, "txId": tx_id, "balanceBefore": before, "balanceAfter": after}


def admin_set_ar_balance_sync(player_uuid: str, player_name: str, balance: int, reason: str, actor: str, idempotency_key: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    target_balance = int(balance or 0)
    if target_balance < 0:
        raise HTTPException(status_code=400, detail="Баланс AR не может быть отрицательным")
    safe_key = str(idempotency_key or "").strip()
    if len(safe_key) < 8 or len(safe_key) > 120:
        raise HTTPException(status_code=400, detail="idempotency_key обязателен")
    now = donation_now_ms()
    with auth_conn() as conn:
        ensure_v4_schema(conn)
        conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (f"admin-ar-set:{safe_key}",))
        existing = conn.execute("SELECT tx_id,balance_after FROM cmv4_bank_ledger WHERE idempotency_key=%s LIMIT 1", (f"admin-ar-set-{safe_key}",)).fetchone()
        if existing:
            return {"ok": True, "txId": existing["tx_id"], "balanceAfter": int(existing["balance_after"] or 0), "idempotent": True}
        bank = ensure_player_bank_account(conn, player_uuid, player_name)
        locked = conn.execute("SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE", (bank["account_id"],)).fetchone()
        before = int(row_get(locked, "balance", 0) or 0)
        after = target_balance
        delta = after - before
        tx_id = f"admin-ar-set-{secrets.token_hex(12)}"
        note = json.dumps(
            {
                "reason": sanitize_public_plain_text(reason, 160),
                "source": "admin-web",
                "mode": "set",
                "before": before,
                "after": after,
                "delta": delta,
            },
            ensure_ascii=False,
        )
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s,version=version+1,updated_at=%s,owner_name=%s WHERE account_id=%s", (after, now, player_name, bank["account_id"]))
        conn.execute(
            "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(%s,%s,'',%s,'ADMIN_AR_SET',%s,%s,%s,'COMMITTED',%s,%s,%s)",
            (tx_id, bank["account_id"], player_uuid, delta, after, f"admin-ar-set-{safe_key}", now, actor, note),
        )
        conn.commit()
    audit_event(actor, "ar.balance.set", target=player_name, details={"uuid": player_uuid, "before": before, "after": after, "delta": delta, "reason": reason})
    return {"ok": True, "txId": tx_id, "balanceBefore": before, "balanceAfter": after, "delta": delta}


def admin_create_artifact_gift_sync(
    player_uuid: str,
    player_name: str,
    item_id: str,
    category: str,
    actor: str,
    note: str,
    idempotency_key: str,
) -> dict[str, Any]:
    """Create a free, auditable pending delivery for the selected player."""
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    safe_category = str(category or "AR").strip().upper()
    if safe_category not in {"AR", "DONATION", "HIDDEN"}:
        raise HTTPException(status_code=400, detail="Неизвестная категория подарка")
    safe_item_id = str(item_id or "").strip().lower()
    safe_key = str(idempotency_key or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9-]{8,96}", safe_key):
        raise HTTPException(status_code=400, detail="Некорректный ключ операции")
    catalog = admin_gift_catalog_snapshot_sync().get("categories") or {}
    item = next((dict(row) for row in list(catalog.get(safe_category) or []) if str(row.get("item_id") or "") == safe_item_id), None)
    if not item:
        raise HTTPException(status_code=404, detail="Предмет недоступен для административной выдачи")
    if safe_category == "HIDDEN" and str(item.get("source") or "").upper() != "ADMIN_ONLY":
        raise HTTPException(status_code=403, detail="Скрытый предмет нельзя выдать из этой категории")
    if safe_category == "AR" and str(item.get("source") or "").upper() == "ADMIN_ONLY":
        raise HTTPException(status_code=403, detail="Админский предмет нельзя выдать как обычный AR-предмет")
    now = donation_now_ms()

    if safe_category == "DONATION":
        gift_key = f"admin-donation-gift-{safe_key}"
        with auth_conn() as conn:
            lock_donation_idempotency_sync(conn, "admin-donation-gift", safe_key)
            existing = conn.execute(
                "SELECT id,player_uuid,player_name,item_id,status FROM donation_purchases WHERE idempotency_key=%s LIMIT 1",
                (gift_key,),
            ).fetchone()
            if existing:
                row = dict(existing)
                if str(row.get("player_uuid") or "") != player_uuid or str(row.get("item_id") or "") != safe_item_id:
                    raise HTTPException(status_code=409, detail="idempotency_key уже используется другой выдачей")
                return {
                    "ok": True,
                    "category": safe_category,
                    "itemId": safe_item_id,
                    "purchaseId": str(row.get("id") or ""),
                    "status": str(row.get("status") or "CLAIM_PENDING"),
                    "idempotent": True,
                    "charged": 0,
                }
            lock_donation_entitlement_sync(conn, player_uuid, safe_item_id)
            if donation_entitlement_conflict_sync(conn, player_uuid, safe_item_id):
                raise HTTPException(status_code=409, detail="У игрока уже есть активный или незавершённый donation-предмет")
            purchase_id = f"admin-gift-{secrets.token_hex(10)}"
            claim_id = f"admin-gift-claim-{secrets.token_hex(10)}"
            conn.execute(
                "INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at) VALUES(%s,%s,%s,%s,0,0,'CLAIM_PENDING','ADMIN_GIFT',%s,%s,%s)",
                (purchase_id, player_uuid, player_name, safe_item_id, gift_key, now, now),
            )
            conn.execute(
                "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(%s,%s,%s,1,'UNCLAIMED',0,%s,%s,%s,%s)",
                (claim_id, player_uuid, safe_item_id, now, now, purchase_id, actor),
            )
        result = {
            "ok": True,
            "category": safe_category,
            "itemId": safe_item_id,
            "purchaseId": purchase_id,
            "claimId": claim_id,
            "status": "CLAIM_PENDING",
            "claimStatus": "UNCLAIMED",
            "charged": 0,
        }
    else:
        gift_key = f"admin-artifact-gift-{safe_key}"
        with auth_conn() as conn:
            ensure_v4_schema(conn)
            conn.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (gift_key,))
            lock_donation_entitlement_sync(conn, player_uuid, safe_item_id)
            existing = conn.execute(
                "SELECT purchase_id,unique_item_id,player_uuid,item_id,status FROM artifact_purchases WHERE idempotency_key=%s LIMIT 1",
                (gift_key,),
            ).fetchone()
            if existing:
                row = dict(existing)
                if str(row.get("player_uuid") or "") != player_uuid or str(row.get("item_id") or "") != safe_item_id:
                    raise HTTPException(status_code=409, detail="idempotency_key уже используется другой выдачей")
                return {
                    "ok": True,
                    "category": safe_category,
                    "itemId": safe_item_id,
                    "purchaseId": str(row.get("purchase_id") or ""),
                    "uniqueItemId": str(row.get("unique_item_id") or ""),
                    "status": str(row.get("status") or "PENDING_DELIVERY"),
                    "charged": 0,
                    "idempotent": True,
                }
            if donation_entitlement_conflict_sync(conn, player_uuid, safe_item_id):
                raise HTTPException(status_code=409, detail="У игрока уже есть активный или незавершённый предмет")
            purchase_id = f"admin-gift-{secrets.token_hex(10)}"
            unique_item_id = f"admin-gift-item-{uuid.uuid4()}"
            delivery_id = f"admin-gift-delivery-{secrets.token_hex(10)}"
            conn.execute(
                "INSERT INTO artifact_purchases(purchase_id,unique_item_id,player_uuid,player_name,item_id,shop_id,price_ar,bank_tx_id,idempotency_key,status,delivery_mode,created_at,updated_at) VALUES(%s,%s,%s,%s,%s,'',0,'ADMIN_GIFT',%s,'PENDING_DELIVERY','PENDING',%s,%s)",
                (purchase_id, unique_item_id, player_uuid, player_name, safe_item_id, gift_key, now, now),
            )
            conn.execute(
                "INSERT INTO artifact_item_instances(unique_item_id,item_id,owner_uuid,purchase_id,status,repaired_count,created_at,updated_at) VALUES(%s,%s,%s,%s,'PENDING_DELIVERY',0,%s,%s)",
                (unique_item_id, safe_item_id, player_uuid, purchase_id, now, now),
            )
            conn.execute(
                "INSERT INTO artifact_pending_deliveries(delivery_id,purchase_id,unique_item_id,player_uuid,item_id,status,created_at,updated_at) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)",
                (delivery_id, purchase_id, unique_item_id, player_uuid, safe_item_id, now, now),
            )
        result = {
            "ok": True,
            "category": safe_category,
            "itemId": safe_item_id,
            "purchaseId": purchase_id,
            "uniqueItemId": unique_item_id,
            "deliveryId": delivery_id,
            "status": "PENDING_DELIVERY",
            "charged": 0,
        }

    audit_event(actor, "admin.artifact.gift", target=player_name, details={"uuid": player_uuid, "itemId": safe_item_id, "category": safe_category, "note": note})
    append_panel_event("artifacts", "admin_gift", actor=actor, target=player_name, metadata={"itemId": safe_item_id, "category": safe_category, "note": note, "charged": 0}, tags=["artifacts", "admin-gift"])
    return result


def admin_create_donation_test_purchase_sync(player_uuid: str, player_name: str, item_id: str, actor: str) -> dict[str, Any]:
    if not pg_ready():
        raise HTTPException(status_code=503, detail="PostgreSQL недоступен")
    player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)
    catalog = donation_catalog_snapshot_sync()
    item = catalog["byId"].get(str(item_id or "").strip().lower())
    if not item:
        raise HTTPException(status_code=400, detail="Укажи известный donation item_id")
    now = donation_now_ms()
    purchase_id = f"don-purchase-{secrets.token_hex(8)}"
    claim_id = f"don-claim-{secrets.token_hex(8)}"
    with auth_conn() as conn:
        lock_donation_entitlement_sync(conn, player_uuid, item["item_id"])
        if donation_entitlement_conflict_sync(conn, player_uuid, item["item_id"]):
            raise HTTPException(status_code=409, detail="У игрока уже есть активный или незавершённый donation-экземпляр этого предмета")
        conn.execute(
            """
            INSERT INTO donation_purchases(id,player_uuid,player_name,item_id,price,price_donation,status,source,idempotency_key,created_at,updated_at)
            VALUES(%s,%s,%s,%s,%s,%s,'CLAIM_PENDING','admin_test','',%s,%s)
            """,
            (purchase_id, player_uuid, player_name, item["item_id"], int(item["price_donation"]), int(item["price_donation"]), now, now),
        )
        conn.execute(
            """
            INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor)
            VALUES(%s,%s,%s,%s,'UNCLAIMED',0,%s,%s,%s,%s)
            """,
            (claim_id, player_uuid, item["item_id"], 1, now, now, purchase_id, actor),
        )
    return {
        "ok": True,
        "purchaseId": purchase_id,
        "claimId": claim_id,
        "playerUuid": player_uuid,
        "playerName": player_name,
        "itemId": item["item_id"],
        "price": int(item["price_donation"]),
        "status": "CLAIM_PENDING",
    }


def read_artifact_rows(table: str, limit: int = 100, player_uuid: str = "") -> list[dict[str, Any]]:
    if not pg_ready():
        return []
    allowed = {
        "artifact_items_catalog": "SELECT * FROM artifact_items_catalog ORDER BY category ASC, price_ar ASC, item_id ASC LIMIT %s",
        "artifact_shops": "SELECT * FROM artifact_shops ORDER BY world_name ASC, block_x ASC, block_y ASC, block_z ASC LIMIT %s",
        "artifact_purchases": "SELECT * FROM artifact_purchases {where} ORDER BY created_at DESC LIMIT %s",
        "artifact_repairs": "SELECT * FROM artifact_repairs {where} ORDER BY created_at DESC LIMIT %s",
        "artifact_suspicious_events": "SELECT * FROM artifact_suspicious_events {where} ORDER BY created_at DESC LIMIT %s",
        "artifact_pending_deliveries": "SELECT * FROM artifact_pending_deliveries {where} ORDER BY created_at DESC LIMIT %s",
    }
    if table not in allowed:
        return []
    where = ""
    args: list[Any] = []
    if player_uuid and table in {"artifact_purchases", "artifact_repairs", "artifact_pending_deliveries", "artifact_suspicious_events"}:
        where = "WHERE player_uuid=%s"
        args.append(player_uuid)
    sql = allowed[table].format(where=where)
    with auth_conn() as conn:
        rows = conn.execute(sql, tuple(args + [max(1, min(limit, 500))])).fetchall()
        return [dict(r) for r in rows]


def public_artifact_row(row: dict[str, Any]) -> dict[str, Any]:
    hidden = {"player_uuid", "unique_item_id", "purchase_id", "bank_tx_id", "idempotency_key", "audit_id", "details"}
    return {k: v for k, v in row.items() if k not in hidden}


def artifact_health_sync() -> dict[str, Any]:
    plugins_dir = MC_SERVER_DIR / "plugins"
    active_jars = sorted(p.name for p in plugins_dir.glob("CopiMine*.jar")) if plugins_dir.exists() else []
    expected = ["CopiMineArtifacts.jar", "CopiMineEconomyCore.jar", "CopiMineUltimateAdminPlus.jar"]
    counts: dict[str, int] = {}
    if pg_ready():
        with auth_conn() as conn:
            for table in [
                "artifact_items_catalog",
                "artifact_shops",
                "artifact_purchases",
                "artifact_repairs",
                "artifact_suspicious_events",
                "artifact_pending_deliveries",
            ]:
                try:
                    row = conn.execute(f"SELECT COUNT(*) AS total FROM {table}").fetchone()
                    counts[table] = int(row["total"] or 0)
                except Exception:
                    counts[table] = -1
    return {
        "bridgeMode": "CopiMineEconomyCore.ArtifactsBridge",
        "postgres": pg_ready(),
        "activeJars": active_jars,
        "expectedJars": expected,
        "jarsOk": active_jars == expected,
        "counts": counts,
    }


@app.get("/api/artifacts/catalog")
async def artifacts_catalog(_: str = Depends(require_admin), limit: int = 200) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_items_catalog", limit, "")
    return {"items": rows, "count": len(rows), "source": "postgresql", "table": "artifact_items_catalog"}


@app.get("/api/artifacts/shops")
async def artifacts_shops(_: str = Depends(require_admin), limit: int = 200) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_shops", limit, "")
    return {"shops": rows, "count": len(rows), "source": "postgresql", "table": "artifact_shops"}


@app.get("/api/artifacts/purchases")
async def artifacts_purchases(player_uuid: str = "", limit: int = 200, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_purchases", limit, player_uuid)
    return {"purchases": rows, "count": len(rows), "source": "postgresql", "table": "artifact_purchases"}


@app.get("/api/artifacts/repairs")
async def artifacts_repairs(player_uuid: str = "", limit: int = 200, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_repairs", limit, player_uuid)
    return {"repairs": rows, "count": len(rows), "source": "postgresql", "table": "artifact_repairs"}


@app.get("/api/artifacts/suspicious")
async def artifacts_suspicious(player_uuid: str = "", limit: int = 200, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_suspicious_events", limit, player_uuid)
    return {"events": rows, "count": len(rows), "source": "postgresql", "table": "artifact_suspicious_events"}


@app.get("/api/artifacts/pending")
async def artifacts_pending(player_uuid: str = "", limit: int = 200, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_artifact_rows, "artifact_pending_deliveries", limit, player_uuid)
    return {"deliveries": rows, "count": len(rows), "source": "postgresql", "table": "artifact_pending_deliveries"}


@app.get("/api/artifacts/health")
async def artifacts_health(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    return await bg(artifact_health_sync)


@app.get("/api/player/artifacts")
async def player_artifacts(account: dict[str, Any] = Depends(require_player), limit: int = 80) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        return {"linked": False, "purchases": [], "pending": [], "repairs": []}
    safe_limit = max(1, min(limit, 120))
    purchases = await bg(read_artifact_rows, "artifact_purchases", safe_limit, uuid)
    pending = await bg(read_artifact_rows, "artifact_pending_deliveries", safe_limit, uuid)
    repairs = await bg(read_artifact_rows, "artifact_repairs", safe_limit, uuid)
    return {
        "linked": True,
        "purchases": [public_artifact_row(x) for x in purchases],
        "pending": [public_artifact_row(x) for x in pending],
        "repairs": [public_artifact_row(x) for x in repairs],
    }


@app.get("/api/player/donation/balance")
async def player_donation_balance(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    name = str(account.get("minecraft_name") or account.get("username") or "")
    if not uuid:
        return {"linked": False, "balance": 0}
    row = await bg(read_donation_balance_sync, uuid, name)
    return {"linked": True, "balance": int(row.get("balance") or 0), "playerUuid": uuid}


@app.get("/api/player/donation/history")
async def player_donation_history(account: dict[str, Any] = Depends(require_player), limit: int = 40) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        return {"linked": False, "history": []}
    rows = await bg(read_donation_history_sync, uuid, limit)
    return {"linked": True, "history": rows}


@app.get("/api/player/donation/items")
async def player_donation_items(account: dict[str, Any] = Depends(require_player), limit: int = 40) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        return {"linked": False, "items": [], "instances": [], "summary": {}}
    owned = await bg(read_player_donation_owned_sync, uuid, limit)
    return {"linked": True, "items": owned.get("claims", []), "instances": owned.get("instances", []), "summary": owned.get("summary", {})}


@app.get("/api/player/donation/packs")
async def player_donation_packs(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return {
        "linked": bool(account.get("minecraft_uuid")),
        "currency": "DONATION",
        "rubPerUnit": 1,
        "packs": [{"amount": amount, "rub": amount, "label": f"{amount} Donation"} for amount in DONATION_FIXED_PACKS],
        "provider": DONATION_PROVIDER,
        "providerConfigured": DONATION_PROVIDER != "YOOKASSA" or YOOKASSA_SETTINGS.configured,
    }


@app.post("/api/player/donation/sbp/session")
async def player_donation_create_session(data: PlayerDonationSessionCreateIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    name = str(account.get("minecraft_name") or account.get("username") or "")
    if not uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    check_rate_limit(request, "player-donation-session", limit=6, window_seconds=60)
    result = await bg(create_donation_session_sync, uuid, name, data.amount, name or uuid, "player-web", data.idempotency_key)
    return {
        "ok": True,
        "session": public_player_donation_session(result),
        "provider": DONATION_PROVIDER,
        "message": "Сессия оплаты создана. Баланс пополнится только после статуса PAID.",
    }


@app.post("/api/payments/yookassa/webhook")
async def yookassa_webhook(request: Request) -> dict[str, Any]:
    check_rate_limit(request, "yookassa-webhook", limit=120, window_seconds=60)
    try:
        payload = await request.json()
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Некорректный webhook ЮKassa") from exc
    if not isinstance(payload, dict):
        raise HTTPException(status_code=400, detail="Некорректный webhook ЮKassa")
    event = str(payload.get("event") or "").strip()
    if event != "payment.succeeded":
        return {"ok": True, "ignored": True}
    payment = payload.get("object")
    if not isinstance(payment, dict):
        raise HTTPException(status_code=400, detail="Webhook ЮKassa не содержит платежа")
    payment_id = str(payment.get("id") or "").strip()
    result = await bg(confirm_yookassa_payment_sync, payment_id, event)
    return {"ok": True, "sessionId": result.get("sessionId"), "status": result.get("status")}


@app.get("/api/player/donation/sbp/session/{session_id}")
async def player_donation_session(session_id: str, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    session = await bg(read_donation_session_sync, session_id)
    if str(session.get("player_uuid") or "") != uuid:
        raise HTTPException(status_code=403, detail="Это не твоя платёжная сессия")
    return {"ok": True, "session": public_player_donation_session(session)}


@app.get("/api/player/donation/sbp/session/{session_id}/qr.png")
async def player_donation_session_qr(session_id: str, account: dict[str, Any] = Depends(require_player)) -> Response:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    session = await bg(read_donation_session_sync, session_id)
    if str(session.get("player_uuid") or "") != uuid:
        raise HTTPException(status_code=403, detail="Это не твоя платёжная сессия")
    payload = str(session.get("qr_payload") or "")
    if not payload:
        raise HTTPException(status_code=404, detail="QR payload не найден")
    png = await bg(render_qr_png_bytes, payload)
    return Response(content=png, media_type="image/png")


@app.get("/api/player/shop/donation-items")
async def player_donation_shop_catalog(account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    catalog = await bg(donation_catalog_snapshot_sync)
    owned = await bg(read_player_donation_owned_sync, str(account.get("minecraft_uuid") or ""), 120) if account.get("minecraft_uuid") else {"claims": [], "instances": []}
    active_by_item = {str(row.get("item_id") or "") for row in owned.get("instances", []) if str(row.get("status") or "").upper() == "ACTIVE"}
    claimable_by_item = {str(row.get("item_id") or "") for row in owned.get("claims", []) if str(row.get("status") or "").upper() == "UNCLAIMED"}
    items = []
    for row in catalog.get("items", []):
        item = dict(row)
        item["item_url"] = donation_item_page_url(str(row.get("item_id") or ""))
        item["owned_active"] = str(row.get("item_id") or "") in active_by_item
        item["claim_available"] = str(row.get("item_id") or "") in claimable_by_item
        items.append(item)
    return {"catalogVersion": catalog.get("catalogVersion", 0), "updatedAt": catalog.get("updatedAt", 0), "items": items}


@app.get("/api/player/shop/ar-items")
async def player_ar_shop_catalog(_: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    catalog = await bg(ar_catalog_snapshot_sync)
    return {"items": catalog.get("items", []), "count": len(catalog.get("items", []))}


@app.get("/api/player/shop/ar-items/{item_id}")
async def player_ar_shop_item(item_id: str, _: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    catalog = await bg(ar_catalog_snapshot_sync)
    item = dict(catalog.get("byId", {}).get(str(item_id or "").strip().lower()) or {})
    if not item:
        raise HTTPException(status_code=404, detail="AR-предмет не найден")
    return {"item": item}


@app.get("/api/player/shop/donation-items/{item_id}")
async def player_donation_shop_item(item_id: str, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    catalog = await bg(donation_catalog_snapshot_sync)
    item = dict(catalog.get("byId", {}).get(str(item_id or "").strip().lower()) or {})
    if not item:
        raise HTTPException(status_code=404, detail="Донат-предмет не найден")
    item["item_url"] = donation_item_page_url(str(item.get("item_id") or ""))
    if account.get("minecraft_uuid"):
        owned = await bg(read_player_donation_owned_sync, str(account.get("minecraft_uuid") or ""), 120)
        item["owned_active"] = any(str(row.get("item_id") or "") == item["item_id"] and str(row.get("status") or "").upper() == "ACTIVE" for row in owned.get("instances", []))
        item["claim_available"] = any(str(row.get("item_id") or "") == item["item_id"] and str(row.get("status") or "").upper() == "UNCLAIMED" for row in owned.get("claims", []))
    return {"item": item, "catalogVersion": catalog.get("catalogVersion", 0)}


@app.get("/api/player/shop/owned")
async def player_donation_owned(account: dict[str, Any] = Depends(require_player), limit: int = 80) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        return {"linked": False, "claims": [], "instances": [], "summary": {}}
    owned = await bg(read_player_donation_owned_sync, uuid, limit)
    return {"linked": True, **owned}


@app.post("/api/player/shop/purchase-intent")
async def player_donation_purchase_intent(data: PlayerDonationPurchaseIntentIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    name = str(account.get("minecraft_name") or account.get("username") or "")
    if not uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    check_rate_limit(request, "player-donation-purchase", limit=8, window_seconds=60)
    result = await bg(purchase_donation_item_sync, uuid, name, data.item_id, data.pin, name or uuid, "player-web", data.idempotency_key)
    return result


@app.post("/api/player/shop/ar-purchase-intent")
async def player_ar_purchase_intent(data: PlayerArPurchaseIntentIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-ar-purchase", limit=8, window_seconds=60)
    return await bg(purchase_ar_item_sync, account, data)


@app.post("/api/player/shop/cart/ar/checkout")
async def player_ar_cart_checkout(data: PlayerCartCheckoutIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    check_rate_limit(request, "player-ar-cart-checkout", limit=5, window_seconds=60)
    return await bg(checkout_ar_cart_sync, account, data)


@app.post("/api/player/shop/cart/donation/checkout")
async def player_donation_cart_checkout(data: PlayerCartCheckoutIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    player_uuid = str(account.get("minecraft_uuid") or "").strip()
    player_name = str(account.get("minecraft_name") or account.get("username") or "").strip()
    if not player_uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    check_rate_limit(request, "player-donation-cart-checkout", limit=5, window_seconds=60)
    return await bg(checkout_donation_cart_sync, player_uuid, player_name, data)


@app.post("/api/player/donation/claim")
async def player_donation_claim(data: PlayerDonationClaimIn, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    uuid = str(account.get("minecraft_uuid") or "")
    if not uuid:
        raise HTTPException(status_code=400, detail="Сначала привяжи Minecraft-ник")
    raise HTTPException(status_code=410, detail="Получение донат-предметов выполняется только в игре через donation shop.")


@app.get("/api/admin/shop/donation-items")
async def admin_donation_shop_items(_: str = Depends(require_admin)) -> dict[str, Any]:
    catalog = await bg(donation_catalog_snapshot_sync)
    return {"catalogVersion": catalog.get("catalogVersion", 0), "updatedAt": catalog.get("updatedAt", 0), "items": catalog.get("items", [])}


@app.get("/api/admin/shop/ar-items")
async def admin_ar_shop_items(_: str = Depends(require_admin)) -> dict[str, Any]:
    catalog = await bg(ar_catalog_snapshot_sync)
    return {"items": catalog.get("items", []), "count": len(catalog.get("items", []))}


@app.get("/api/admin/shop/admin-gift-items")
async def admin_gift_shop_items(_: str = Depends(require_admin)) -> dict[str, Any]:
    catalog = await bg(admin_gift_catalog_snapshot_sync)
    return {"categories": catalog.get("categories", {}), "count": len(catalog.get("items", []))}


@app.post("/api/admin/artifacts/gift")
async def admin_artifact_gift(data: AdminArtifactGiftIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "ADMIN_ARTIFACT_GIFT")
    check_rate_limit(request, "admin-artifact-gift", limit=10, window_seconds=60)
    result = await bg(
        admin_create_artifact_gift_sync,
        data.minecraft_uuid or "",
        data.minecraft_name,
        data.item_id,
        data.category,
        username,
        data.note,
        data.idempotency_key,
    )
    return result


@app.post("/api/admin/economy/ar/add-balance")
async def admin_ar_add_balance(data: AdminArBalanceIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "AR_ADD_BALANCE")
    check_rate_limit(request, "admin-ar-add-balance", limit=10, window_seconds=60)
    result = await bg(admin_add_ar_balance_sync, data.minecraft_uuid, data.minecraft_name, data.amount, data.reason, username, data.idempotency_key)
    append_panel_event("economy", "ar_balance_add", actor=username, target=data.minecraft_name, metadata={"amount": data.amount, "reason": data.reason}, tags=["economy", "admin"])
    return result


@app.post("/api/admin/economy/ar/set-balance")
async def admin_ar_set_balance(data: AdminArBalanceSetIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "AR_SET_BALANCE")
    check_rate_limit(request, "admin-ar-set-balance", limit=10, window_seconds=60)
    result = await bg(admin_set_ar_balance_sync, data.minecraft_uuid, data.minecraft_name, data.balance, data.reason, username, data.idempotency_key)
    append_panel_event("economy", "ar_balance_set", actor=username, target=data.minecraft_name, metadata={"after": data.balance, "delta": result.get("delta", 0), "reason": data.reason}, tags=["economy", "admin"])
    return result


@app.get("/api/admin/donation/sessions")
async def admin_donation_sessions(limit: int = 100, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_donation_sessions_sync, limit)
    return {"sessions": rows, "count": len(rows)}


@app.get("/api/admin/donation/overview")
async def admin_donation_overview(limit: int = 100, _: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(read_donation_admin_overview_sync, limit)


@app.post("/api/admin/donation/add-balance")
async def admin_donation_add_balance(data: AdminDonationBalanceIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DONATION_ADD_BALANCE")
    check_rate_limit(request, "admin-donation-add-balance", limit=10, window_seconds=60)
    result = await bg(admin_add_donation_balance_sync, data.minecraft_uuid, data.minecraft_name, data.amount, data.reason, username, data.idempotency_key)
    audit_event(username, "donation.balance.add", target=data.minecraft_name, details={"uuid": data.minecraft_uuid, "amount": data.amount, "reason": data.reason})
    append_panel_event("donation", "balance_add", actor=username, target=data.minecraft_name, metadata={"amount": data.amount, "reason": data.reason}, tags=["donation", "admin"])
    return result


@app.post("/api/admin/donation/set-balance")
async def admin_donation_set_balance(data: AdminDonationBalanceSetIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DONATION_SET_BALANCE")
    check_rate_limit(request, "admin-donation-set-balance", limit=10, window_seconds=60)
    result = await bg(admin_set_donation_balance_sync, data.minecraft_uuid, data.minecraft_name, data.balance, data.reason, username, data.idempotency_key)
    append_panel_event("donation", "balance_set", actor=username, target=data.minecraft_name, metadata={"after": data.balance, "delta": result.get("delta", 0), "reason": data.reason}, tags=["donation", "admin"])
    return result


@app.post("/api/admin/donation/test-purchase")
async def admin_donation_test_purchase(data: AdminDonationTestPurchaseIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DONATION_TEST_PURCHASE")
    check_rate_limit(request, "admin-donation-test-purchase", limit=10, window_seconds=60)
    result = await bg(admin_create_donation_test_purchase_sync, data.minecraft_uuid, data.minecraft_name, data.item_id, username)
    audit_event(username, "donation.test_purchase", target=data.minecraft_name, details={"uuid": data.minecraft_uuid, "itemId": data.item_id, "price": result.get("price", 0)})
    append_panel_event("donation", "test_purchase", actor=username, target=data.minecraft_name, metadata={"itemId": data.item_id, "price": result.get("price", 0)}, tags=["donation", "admin"])
    return result


@app.post("/api/admin/donation/sbp/session/{session_id}/mark-paid")
async def admin_donation_mark_paid(session_id: str, data: AdminDonationSessionActionIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DONATION_MARK_PAID")
    check_rate_limit(request, "admin-donation-mark-paid", limit=12, window_seconds=60)
    result = await bg(mark_donation_session_paid_sync, session_id, username, data.note)
    return result


@app.post("/api/admin/donation/sbp/session/{session_id}/cancel")
async def admin_donation_cancel_session(session_id: str, data: AdminDonationSessionActionIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "DONATION_CANCEL_SESSION")
    check_rate_limit(request, "admin-donation-cancel", limit=12, window_seconds=60)
    result = await bg(cancel_donation_session_sync, session_id, username, data.note)
    return result


@app.get("/api/admin/plugins/registry")
async def admin_plugin_registry(_: str = Depends(require_admin)) -> dict[str, Any]:
    items = await bg(list_registry_plugins, PLUGIN_REGISTRY_MANIFEST)
    return {"plugins": items, "count": len(items)}


@app.get("/api/admin/plugins/{plugin_id}/status")
async def admin_plugin_registry_status(plugin_id: str, _: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        return await bg(registry_status, plugin_id, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/api/admin/plugins/{plugin_id}/schema")
async def admin_plugin_registry_schema(plugin_id: str, _: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        plugin = require_registry_plugin(plugin_id, PLUGIN_REGISTRY_MANIFEST)
        return {
            "pluginId": plugin_id,
            "displayName": str(plugin.get("displayName") or plugin_id),
            "editableKeys": registry_schema(plugin),
        }
    except PluginRegistryError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/api/admin/plugins/{plugin_id}/config")
async def admin_plugin_registry_config(plugin_id: str, _: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        return await bg(read_registry_config, plugin_id, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/api/admin/plugins/{plugin_id}/validate")
async def admin_plugin_registry_validate(plugin_id: str, data: PluginRegistryConfigIn, _: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        validated = await bg(validate_registry_values, plugin_id, data.values, PLUGIN_REGISTRY_MANIFEST)
        return {"ok": True, "pluginId": plugin_id, "validated": validated}
    except PluginRegistryError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/api/admin/plugins/{plugin_id}/backup")
async def admin_plugin_registry_backup(plugin_id: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLUGIN_REGISTRY_BACKUP")
    check_rate_limit(request, "plugin-registry-backup", limit=15, window_seconds=60)
    try:
        result = await bg(backup_registry_config, plugin_id, PLUGIN_REGISTRY_BACKUPS_DIR, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    audit_event(username, "plugin.registry.backup", target=plugin_id, details={"pluginId": plugin_id, "backup": result.get("name", "")})
    append_panel_event("plugin-registry", "backup", actor=username, target=plugin_id, metadata={"backup": result.get("name", "")}, tags=["plugins", "config"])
    return {"ok": True, "pluginId": plugin_id, "backup": result}


@app.post("/api/admin/plugins/{plugin_id}/apply")
async def admin_plugin_registry_apply(plugin_id: str, data: PluginRegistryConfigIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLUGIN_REGISTRY_APPLY")
    check_rate_limit(request, "plugin-registry-apply", limit=12, window_seconds=60)
    try:
        result = await bg(apply_registry_values, plugin_id, data.values, PLUGIN_REGISTRY_BACKUPS_DIR, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    audit_event(username, "plugin.registry.apply", target=plugin_id, details={"pluginId": plugin_id, "updatedKeys": result.get("updatedKeys", []), "backup": result.get("backup", {}).get("name", "")})
    append_panel_event("plugin-registry", "apply", actor=username, target=plugin_id, metadata={"updatedKeys": result.get("updatedKeys", [])}, tags=["plugins", "config"])
    return result


@app.post("/api/admin/plugins/{plugin_id}/reload")
async def admin_plugin_registry_reload(plugin_id: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "PLUGIN_REGISTRY_RELOAD")
    check_rate_limit(request, "plugin-registry-reload", limit=12, window_seconds=60)
    try:
        require_registry_plugin(plugin_id, PLUGIN_REGISTRY_MANIFEST)
        status = registry_status(plugin_id, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    reload_mode = str(status.get("reloadMode") or "none")
    reload_command = str(status.get("reloadCommand") or "").strip()
    if reload_mode == "none" or not reload_command:
        audit_event(username, "plugin.registry.reload.skipped", target=plugin_id, details={"pluginId": plugin_id, "reason": "reload-disabled"})
        return {"ok": True, "pluginId": plugin_id, "reloaded": False, "message": "Для этого плагина reload отключён. Нужен ручной рестарт или отдельная команда."}
    if reload_mode != "plugin-command":
        raise HTTPException(status_code=409, detail="Этот reloadMode пока не поддержан backend foundation")
    if not RCON_PASSWORD:
        audit_event(username, "plugin.registry.reload.manual", target=plugin_id, details={"pluginId": plugin_id, "reloadCommand": reload_command})
        return {"ok": False, "pluginId": plugin_id, "reloaded": False, "manual": True, "reloadCommand": reload_command, "message": "RCON не настроен. Выполни reload вручную на сервере."}
    response_text = await bg(rcon_quick, reload_command)
    trimmed = str(response_text or "").strip()
    audit_event(username, "plugin.registry.reload", target=plugin_id, details={"pluginId": plugin_id, "reloadCommand": reload_command, "response": trimmed[:400]})
    append_panel_event("plugin-registry", "reload", actor=username, target=plugin_id, metadata={"reloadCommand": reload_command}, tags=["plugins", "config", "rcon"])
    return {"ok": True, "pluginId": plugin_id, "reloaded": True, "reloadCommand": reload_command, "response": trimmed}


@app.get("/api/admin/plugins/{plugin_id}/audit")
async def admin_plugin_registry_audit(plugin_id: str, limit: int = 80, _: str = Depends(require_admin)) -> dict[str, Any]:
    try:
        require_registry_plugin(plugin_id, PLUGIN_REGISTRY_MANIFEST)
    except PluginRegistryError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    rows = await bg(plugin_registry_audit_sync, plugin_id, limit)
    return {"pluginId": plugin_id, "audit": rows, "count": len(rows)}


@app.get("/api/admin/whitelist/requests")
async def admin_whitelist_requests(limit: int = 100, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_whitelist_requests_sync, limit)
    return {"requests": rows, "count": len(rows)}


@app.post("/api/admin/whitelist/approve")
async def admin_whitelist_approve(data: AdminWhitelistApproveIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "WHITELIST_APPROVE")
    result = await bg(approve_whitelist_request_sync, data.request_id, username, data.note, "web")
    audit_event(username, "whitelist.approve", target=result.get("minecraftName", ""), details={"requestId": data.request_id, "note": data.note})
    return result


@app.get("/api/admin/cms")
async def admin_cms(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(read_site_cms_sync, True)


NARCOTICS_RECIPE_BLOCKED_TOKENS = {"MATERIAL:DIAMOND_ORE", "MATERIAL:DEEPSLATE_DIAMOND_ORE"}
NARCOTICS_RECIPE_TOKEN_RE = re.compile(r"^(material|potion):[A-Z0-9_]+$", re.IGNORECASE)
NARCOTICS_RECIPE_APPLY_MODES = {"save", "apply"}
NARCOTICS_RECIPE_TECHNICAL_SUFFIXES = (
    "_top",
    "_bottom",
    "_side",
    "_front",
    "_back",
    "_on",
    "_off",
    "_lit",
    "_unlit",
    "_open",
    "_closed",
    "_stem",
)
NARCOTICS_RECIPE_TECHNICAL_RE = re.compile(
    r"(^potted_|^empty_|^debug\d*$|^light_\d+$|^structure_block_|^suspicious_(?:gravel|sand)_\d+$|"
    r"^sniffer_egg_(?:not_cracked|slightly_cracked|very_cracked)_|^pointed_dripstone_.*|"
    r"^(?:fire|soul_fire|frosted_ice)_\d+$|^(?:spawn_egg)$|"
    r"^(?:bow|crossbow)_pulling_\d+$|^crossbow_(?:arrow|firework)$|"
    r"^(?:campfire|soul_campfire)_(?:fire|log)$|^composter_(?:compost|ready)$|^crafter_(?:east|north|south|west)$|"
    r"^empty_slot_|^empty_armor_slot_|^calibrated_sculk_sensor_amethyst$|^broken_elytra$|^bundle_filled$|"
    r"^chorus_flower_dead$|^end_portal_frame_eye$|^grass_block_snow$|^hopper_(?:inside|outside)$|^jigsaw_lock$|"
    r"^lectern_sides$|^magma$|^mangrove_propagule_hanging$|^mushroom_block_inside$|^piston_top_sticky$|"
    r"^rail_corner$|^spyglass_model$|^stonecutter_saw$|^tipped_arrow_head$|^trial_spawner_top_ejecting_reward$|"
    r"^beehive_end$|^big_dripleaf_tip$|^bamboo_(?:large_leaves|singleleaf|small_leaves|stalk)$|"
    r"_stage\d+$|"
    r"(?:^|_)stage_?\d+$|_(?:particle|inner|outer|base|round|pivot|vertical|conditional|powered|triggered|crafting|"
    r"ejecting|ominous|inactive|active|bloom|tendril|overlay|markings|pulling|cast|standby|moist|still|flow|"
    r"line\d*|dot|honey|not_cracked|slightly_cracked|very_cracked|cracked|empty|occupied|plant|pot|side\d+|"
    r"top\d+|bottom\d+|frame\d*)$)",
    re.IGNORECASE,
)
NARCOTICS_POTION_ITEMS = (
    ("SPEED", "Зелье скорости"),
    ("WEAKNESS", "Зелье слабости"),
    ("POISON", "Зелье отравления"),
    ("SLOWNESS", "Зелье замедления"),
    ("REGENERATION", "Зелье регенерации"),
    ("STRENGTH", "Зелье силы"),
    ("WATER_BREATHING", "Зелье подводного дыхания"),
    ("INVISIBILITY", "Зелье невидимости"),
    ("FIRE_RESISTANCE", "Зелье огнестойкости"),
    ("HARM", "Зелье вреда"),
)
_NARCOTICS_RECIPE_ITEMS_CACHE: tuple[int, list[dict[str, Any]]] = (0, [])


def narcotics_runtime_config_path() -> Path:
    runtime = MC_SERVER_DIR / "plugins" / "CopiMineNarcotics" / "config.yml"
    return runtime if runtime.exists() else NARCOTICS_SOURCE_CONFIG_FILE


def _load_narcotics_config() -> tuple[Path, dict[str, Any]]:
    if yaml is None:
        raise HTTPException(status_code=503, detail="Редактор рецептов недоступен: PyYAML не установлен")
    path = narcotics_runtime_config_path()
    if not path.exists():
        raise HTTPException(status_code=404, detail="Конфиг CopiMineNarcotics не найден")
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Не удалось прочитать конфиг рецептов") from exc
    if not isinstance(data, dict):
        raise HTTPException(status_code=500, detail="Конфиг рецептов имеет неверный формат")
    return path, data


def _public_recipe_item(item_id: str, item: Mapping[str, Any]) -> dict[str, Any]:
    recipe = item.get("recipe") if isinstance(item, Mapping) else []
    clean_recipe = [str(x).strip() for x in recipe if str(x).strip()] if isinstance(recipe, list) else []
    return {
        "id": item_id,
        "name": re.sub(r"(?i)(?:§|&)[0-9a-fk-or]", "", str(item.get("display_name") or item_id)) if isinstance(item, Mapping) else item_id,
        "material": str(item.get("material") or "").upper() if isinstance(item, Mapping) else "",
        "recipe": clean_recipe,
    }


def _minecraft_recipe_item_catalog() -> list[dict[str, Any]]:
    """Build a complete material picker from the bundled vanilla textures.

    The asset directory is already populated from the matching Minecraft
    client version.  Technical block-face textures are excluded, while every
    remaining icon is exposed as a valid material token with its own sprite.
    A tiny mtime cache keeps opening the recipe editor cheap.
    """
    global _NARCOTICS_RECIPE_ITEMS_CACHE
    icon_dir = FRONTEND_DIR / "assets" / "mc-icons" / "item"
    if not icon_dir.exists():
        return []
    try:
        files = sorted(icon_dir.glob("*.png"), key=lambda path: path.name.lower())
        stamp = max((int(path.stat().st_mtime_ns) for path in files), default=0)
    except OSError:
        return []
    if _NARCOTICS_RECIPE_ITEMS_CACHE[0] == stamp:
        return list(_NARCOTICS_RECIPE_ITEMS_CACHE[1])

    blocked = {token.split(":", 1)[1].lower() for token in NARCOTICS_RECIPE_BLOCKED_TOKENS}
    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    for path in files:
        item_id = path.stem.lower()
        if not item_id or item_id in blocked or item_id in seen:
            continue
        if item_id.endswith(NARCOTICS_RECIPE_TECHNICAL_SUFFIXES) or NARCOTICS_RECIPE_TECHNICAL_RE.search(item_id):
            continue
        # Mojang stores animated compass/clock frames as compass_00, clock_01,
        # etc.  The material is still COMPASS/CLOCK, but the frame is not a
        # separate item and would only create confusing duplicates in the UI.
        frame_match = re.match(r"^(compass|clock|recovery_compass)_\d+$", item_id)
        if frame_match:
            canonical = frame_match.group(1)
            if canonical in seen:
                continue
            item_id = canonical
        seen.add(item_id)
        rows.append(
            {
                "id": item_id.upper(),
                "name": item_id.replace("_", " ").title(),
                "token": f"material:{item_id}",
                "iconUrl": f"/assets/mc-icons/item/{path.name}",
            }
        )
    rows.sort(key=lambda row: (str(row.get("name") or ""), str(row.get("id") or "")))
    _NARCOTICS_RECIPE_ITEMS_CACHE = (stamp, rows)
    return list(rows)


def _minecraft_recipe_potion_catalog() -> list[dict[str, Any]]:
    return [
        {
            "id": item_id,
            "name": name,
            "token": f"potion:{item_id.lower()}",
            "iconUrl": "/assets/mc-icons/item/potion.png",
        }
        for item_id, name in NARCOTICS_POTION_ITEMS
    ]


def _restart_minecraft_for_narcotics() -> dict[str, Any]:
    """Attempt the explicit fallback restart without hiding a saved config."""
    try:
        return run_systemctl("restart")
    except Exception as exc:
        return {"returncode": -1, "stdout": "", "stderr": str(exc)[:400], "command": "systemctl restart"}


def _validate_narcotics_recipe_token(token: str) -> str:
    normalized = str(token or "").strip().replace(" ", "_").upper()
    if ":" not in normalized:
        normalized = "MATERIAL:" + normalized
    if not NARCOTICS_RECIPE_TOKEN_RE.match(normalized):
        raise HTTPException(status_code=400, detail=f"Неверный предмет рецепта: {token}")
    if normalized in NARCOTICS_RECIPE_BLOCKED_TOKENS:
        raise HTTPException(status_code=400, detail="Алмазная руда недоступна для рецептов")
    return normalized.lower()


def read_narcotics_recipes_sync() -> dict[str, Any]:
    path, data = _load_narcotics_config()
    items = data.get("items") if isinstance(data.get("items"), dict) else {}
    recipes = []
    for item_id, item in sorted(items.items()):
        if str(item_id).lower() == "zhuzevo":
            continue
        if isinstance(item, Mapping):
            recipes.append(_public_recipe_item(str(item_id), item))
    return {
        "ok": True,
        "configPath": safe_location(path),
        "blocked": sorted(NARCOTICS_RECIPE_BLOCKED_TOKENS),
        "recipes": recipes,
        "minecraftItems": _minecraft_recipe_item_catalog(),
        "potionItems": _minecraft_recipe_potion_catalog(),
    }


def save_narcotics_recipes_sync(payload: dict[str, list[str]], actor: str, apply_mode: str = "save") -> dict[str, Any]:
    normalized_apply_mode = str(apply_mode or "save").strip().lower()
    if normalized_apply_mode not in NARCOTICS_RECIPE_APPLY_MODES:
        raise HTTPException(status_code=400, detail="Неизвестный режим применения рецептов")
    path, data = _load_narcotics_config()
    items = data.get("items")
    if not isinstance(items, dict):
        raise HTTPException(status_code=500, detail="В конфиге нет раздела items")
    updated: list[str] = []
    for item_id, raw_recipe in payload.items():
        normalized_id = str(item_id or "").strip().lower()
        if not normalized_id or normalized_id == "zhuzevo" or normalized_id not in items:
            raise HTTPException(status_code=400, detail=f"Неизвестный рецепт: {item_id}")
        if not isinstance(raw_recipe, list):
            raise HTTPException(status_code=400, detail=f"Рецепт {item_id} должен быть списком")
        recipe = [_validate_narcotics_recipe_token(token) for token in raw_recipe]
        if len(recipe) < 3:
            raise HTTPException(status_code=400, detail=f"В рецепте {item_id} должно быть минимум 3 ингредиента")
        items[normalized_id]["recipe"] = recipe
        updated.append(normalized_id)
    backup = path.with_suffix(path.suffix + f".bak-{now_ts()}")
    shutil.copy2(path, backup)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding="utf-8")
    os.replace(tmp, path)
    reload_result: dict[str, Any] = {
        "reloaded": False,
        "manual": normalized_apply_mode == "apply",
        "applyMode": "save-only" if normalized_apply_mode == "save" else "pending",
        "reloadCommand": "cmnarcotics reload",
        "message": "Конфиг сохранён. Изменения вступят в силу после применения рецептов.",
    }
    if normalized_apply_mode == "save":
        reload_result["message"] = "Конфиг сохранён без перезагрузки. Примени рецепты отдельной кнопкой, когда будешь готов."
    elif RCON_PASSWORD:
        try:
            response = str(rcon_quick("cmnarcotics reload") or "").strip()
            reload_result = {
                "reloaded": True,
                "manual": False,
                "applyMode": "plugin-reload",
                "reloadCommand": "cmnarcotics reload",
                "response": response[:400],
                "message": "Конфиг сохранён, CopiMineNarcotics перечитал его без перезапуска сервера.",
            }
        except Exception as exc:
            restart = _restart_minecraft_for_narcotics()
            reload_result = {
                "reloaded": restart.get("returncode") == 0,
                "manual": restart.get("returncode") != 0,
                "applyMode": "server-restart",
                "reloadCommand": "cmnarcotics reload",
                "restart": restart,
                "message": "RCON reload не выполнен, поэтому запущен перезапуск сервера." if restart.get("returncode") == 0 else f"RCON reload и перезапуск не выполнены: {str(exc)[:180]}",
            }
    else:
        restart = _restart_minecraft_for_narcotics()
        reload_result = {
            "reloaded": restart.get("returncode") == 0,
            "manual": restart.get("returncode") != 0,
            "applyMode": "server-restart",
            "reloadCommand": "cmnarcotics reload",
            "restart": restart,
            "message": "Конфиг сохранён и сервер перезапущен для применения рецептов." if restart.get("returncode") == 0 else "Конфиг сохранён, но перезапуск сервера не выполнен. Проверь права systemctl.",
        }
    append_panel_event("narcotics", "recipes_saved", actor=actor, target="CopiMineNarcotics", metadata={"updated": updated, "backup": str(backup), "applyMode": normalized_apply_mode}, tags=["narcotics", "config"])
    append_panel_event("narcotics", "recipes_reloaded" if reload_result["reloaded"] else "recipes_reload_pending", actor=actor, target="CopiMineNarcotics", metadata={"command": reload_result.get("reloadCommand"), "reloaded": reload_result["reloaded"], "applyMode": reload_result.get("applyMode")}, tags=["narcotics", "config", "rcon"])
    return {"ok": True, "updated": updated, "backup": safe_location(backup), "configPath": safe_location(path), "reload": reload_result}


@app.get("/api/admin/narcotics/recipes")
async def admin_narcotics_recipes(_: str = Depends(require_admin)) -> dict[str, Any]:
    return await bg(read_narcotics_recipes_sync)


@app.post("/api/admin/narcotics/recipes")
async def admin_narcotics_recipes_save(data: NarcoticsRecipesIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    normalized_apply_mode = str(data.apply_mode or "save").strip().lower()
    if normalized_apply_mode not in NARCOTICS_RECIPE_APPLY_MODES:
        raise HTTPException(status_code=400, detail="Неизвестный режим применения рецептов")
    require_sensitive_confirm(request, "NARCOTICS_RECIPES_APPLY" if normalized_apply_mode == "apply" else "NARCOTICS_RECIPES_SAVE")
    check_rate_limit(request, "admin-narcotics-recipes", limit=10, window_seconds=60)
    return await bg(save_narcotics_recipes_sync, data.recipes, username, normalized_apply_mode)


@app.post("/api/admin/cms/entries")
async def admin_cms_save_entry(data: SiteCmsEntryIn, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "CMS_SAVE")
    check_rate_limit(request, "admin-cms-save", limit=20, window_seconds=60)
    return await bg(upsert_site_cms_entry_sync, data, username)


@app.delete("/api/admin/cms/entries/{entry_key}")
async def admin_cms_disable_entry(entry_key: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:
    require_sensitive_confirm(request, "CMS_DISABLE")
    check_rate_limit(request, "admin-cms-disable", limit=20, window_seconds=60)
    return await bg(delete_site_cms_entry_sync, entry_key, username)


@app.get("/api/admin/security/ip-alerts")
async def admin_security_ip_alerts(limit: int = 100, _: str = Depends(require_admin)) -> dict[str, Any]:
    rows = await bg(read_ip_alerts_sync, limit)
    return {"alerts": rows, "count": len(rows)}


@app.get("/api/config")
async def config(_: str = Depends(require_panel_admin)) -> dict[str, Any]:
    resolved_admin_db = admin_plugin_db_path()
    return {
        "mcServerDir": safe_location(MC_SERVER_DIR),
        "worldDir": safe_location(WORLD_DIR),
        "logFile": safe_location(LOG_FILE),
        "coreprotectDb": safe_location(Path(COREPROTECT_DB)),
        "adminPluginDb": safe_location(resolved_admin_db),
        "authDb": admin_plugin_db_location(resolved_admin_db),
        "arItemIds": AR_ITEM_IDS,
        "rconConfigured": bool(RCON_PASSWORD),
        "minecraftService": MINECRAFT_SERVICE,
        "backupsDir": safe_location(BACKUPS_DIR),
        "minecraftAssetsVersion": MINECRAFT_ASSETS_VERSION,
        "rconWebAllowlist": RCON_WEB_COMMAND_ALLOWLIST,
        "dbWritePolicy": {
            "enabled": DB_WRITE_ENABLED,
            "allowlist": sorted(ADMIN_DB_WRITE_ALLOWLIST),
            "protectedPatterns": [{"pattern": p, "reason": r} for p, r in DB_WRITE_PROTECTED_TABLE_PATTERNS],
        },
        "adminPublicBaseUrl": ADMIN_PUBLIC_BASE_URL,
        "discordConfigured": {
            "token": DISCORD_BOT_TOKEN_CONFIGURED,
            "guildId": bool(DISCORD_GUILD_ID),
            "applicationsChannelId": bool(DISCORD_APPLICATIONS_CHANNEL_ID),
            "reportsChannelId": bool(DISCORD_REPORTS_CHANNEL_ID),
            "whitelistChannelId": bool(os.getenv("DISCORD_WHITELIST_CHANNEL_ID", "").strip()),
            "adminRoleId": bool(DISCORD_ADMIN_ROLE_ID),
        },
        "features": {
            "rcon": bool(RCON_PASSWORD),
            "nbt": bool(nbtlib),
            "psutil": bool(psutil),
            "coreprotectDbExists": Path(COREPROTECT_DB).exists(),
            "adminPluginDbExists": resolved_admin_db.exists(),
            "authDbExists": auth_storage_ready(),
            "postgresPool": pg_ready(),
            "authBackend": auth_storage_backend(),
            "cookieAuth": True,
            "eventIngestion": bool(PLUGIN_API_KEY),
            "discordBot": DISCORD_BOT_TOKEN_CONFIGURED and bool(DISCORD_BOT_API_KEY or PLUGIN_API_KEY),
        },
        "postgres": {
            "host": POSTGRES_HOST,
            "port": POSTGRES_PORT,
            "database": POSTGRES_DB,
            "schema": POSTGRES_SCHEMA,
            "poolMin": POSTGRES_POOL_MIN_SIZE,
            "poolMax": POSTGRES_POOL_MAX_SIZE,
        },
    }


def public_health_projection(report: Mapping[str, Any] | dict[str, Any]) -> dict[str, Any]:
    """Expose only service state; paths, hosts and diagnostic payloads stay admin-only."""
    raw = dict(report or {})
    raw_summary = safe_mapping(raw.get("summary"))
    summary = {
        "total": max(0, int(raw_summary.get("total") or 0)),
        "failures": max(0, int(raw_summary.get("failures") or 0)),
        "warnings": max(0, int(raw_summary.get("warnings") or 0)),
    }
    checks = []
    for raw_check in raw.get("checks", []) if isinstance(raw.get("checks"), list) else []:
        check = safe_mapping(raw_check)
        key = clip_text(check.get("key"), 80)
        status = str(check.get("status") or "unknown").lower()
        if not key or status not in {"ok", "warn", "fail"}:
            continue
        checks.append({"key": key, "status": status, "required": bool(check.get("required", True))})
    return {"ok": bool(raw.get("ok", False)), "summary": summary, "checks": checks}


def public_auth_transport_state() -> dict[str, Any]:
    if ALLOW_INSECURE_HTTP_AUTH:
        return {
            "http": "authenticated-opt-in",
            "authenticatedSessions": "insecure-http-opt-in",
            "warning": "HTTP authentication is explicitly enabled; use HTTPS for public access.",
        }
    return {
        "http": "public-only",
        "authenticatedSessions": "https-required",
        "warning": "Public pages and downloads work over HTTP; login cookies require HTTPS.",
    }


@app.get("/api/health")
async def health() -> dict[str, Any]:
    report = _STARTUP_REPORT or run_startup_checks()
    return {
        **public_health_projection(report),
        "name": "CopiMine Ultimate Admin",
        "version": APP_VERSION,
        "time": int(time.time()),
        "transport": public_auth_transport_state(),
    }


@app.get("/api/runtime")
async def runtime(request: Request, authorization: str = Header(default="")) -> dict[str, Any]:
    if not is_loopback_request(request):
        require_panel_admin(request, authorization)
    report = _STARTUP_REPORT or run_startup_checks()
    snapshot = managed_runtime_snapshot(PROJECT_ROOT, APP_ROOT)
    return {
        "ok": bool(report.get("ok", False)),
        "name": "CopiMine Ultimate Admin",
        "version": APP_VERSION,
        "time": int(time.time()),
        "startup": report,
        "runtime": snapshot,
    }


@app.get("/favicon.ico")
async def favicon() -> Response:
    path = FRONTEND_DIR / "assets" / "favicon.svg"
    if path.exists():
        return FileResponse(path, media_type="image/svg+xml")
    return Response(status_code=404)


if FRONTEND_DIR.exists():
    app.mount("/", FrontendStaticFiles(directory=str(FRONTEND_DIR), html=True), name="frontend")


