from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "db" / "migrations" / "20260829_017_launcher_binding.sql"


def test_launcher_binding_migration_is_present_and_transactional() -> None:
    assert MIGRATION.is_file()
    sql = MIGRATION.read_text(encoding="utf-8")
    assert re.search(r"(?im)^\s*BEGIN\s*;", sql)
    assert re.search(r"(?im)^\s*COMMIT\s*;", sql)
    tables = set(re.findall(r"(?im)^\s*CREATE TABLE IF NOT EXISTS\s+([a-z_]+)", sql))
    assert tables == {"launcher_link_challenges", "launcher_account_links"}


def test_launcher_binding_migration_has_the_backend_contract_and_no_game_data_write() -> None:
    sql = MIGRATION.read_text(encoding="utf-8")
    for marker in (
        "challenge_id TEXT PRIMARY KEY",
        "device_id_hash TEXT PRIMARY KEY",
        "poll_token_hash TEXT NOT NULL",
        "site_account_id TEXT NOT NULL",
        "idx_launcher_link_challenges_expiry",
        "idx_launcher_link_challenges_device",
        "idx_launcher_account_links_site",
    ):
        assert marker in sql

    statements = re.sub(r"--[^\r\n]*", "", sql)
    assert "CREATE SCHEMA" not in statements.upper()
    assert not re.search(r"(?i)\b(?:ALTER|DROP|TRUNCATE|DELETE|UPDATE|INSERT|VACUUM)\b", statements)
    assert not re.search(r"(?i)\b(?:world|playerdata|inventor(?:y|ies)|authme|economy|shops?)\b", statements)
