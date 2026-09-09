from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_disposable_postgres_script_is_guarded_and_synthetic() -> None:
    script = read("scripts/start_copimine_full_plugin_local_postgres.ps1")

    assert "artifacts\\local-validation" in script
    assert "Disposable PostgreSQL data root" in script
    assert "production|authme|backup" in script
    assert "copimine_test" in script
    assert "LOCAL_POSTGRES_DATA_COPIED=NO" in script
    assert "CREATE ROLE copimine_test LOGIN PASSWORD 'copimine_test'" in script
    assert "CREATE SCHEMA IF NOT EXISTS copimine_test" in script
    assert "LOCAL_STAGING_SCHEMA_COMPATIBILITY=ELECTION_CANDIDATE_ALIASES_ONLY" in script
    assert "player_uuid" in script
    assert "uuid TEXT" in script


def test_disposable_postgres_script_does_not_touch_a_remote_database() -> None:
    script = read("scripts/start_copimine_full_plugin_local_postgres.ps1")

    assert "ssh" not in script.lower()
    assert "90.188.117.117" not in script
    assert "INSERT INTO" not in script.upper()
    assert "UPDATE PLAYERS" not in script.upper()
    assert "DROP DATABASE" not in script.upper()
