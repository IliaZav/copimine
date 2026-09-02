from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_database_plugins_expose_explicit_readiness_contracts() -> None:
    economy = read(
        "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java"
    )
    election = read(
        "copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java"
    )

    assert "public boolean isDatabaseReady()" in economy
    assert "public boolean isDatabaseReady()" in election


def test_admin_waits_for_database_owners_before_creating_shared_tables() -> None:
    admin = read(
        "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
    )

    assert "waitForDatabaseDependencies" in admin
    assert "economy.isDatabaseReady()" in admin
    assert "CopiMineElectionCore" in admin
    assert "databaseDependencyRetryTask" in admin
    assert "ensureColumn(\"candidates\",\"uuid\"" in admin
    assert "ensureColumn(\"candidates\",\"name\"" in admin


def test_full_plugin_staging_script_keeps_election_alias_columns() -> None:
    script = read("scripts/start_copimine_full_plugin_local_postgres.ps1")

    assert "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS uuid" in script
    assert "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS name" in script
