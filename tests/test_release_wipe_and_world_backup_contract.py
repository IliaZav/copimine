"""Contracts for the destructive release reset and world backup rotation.

These tests intentionally use only the Python standard library so they can run
on the Windows checkout even when pytest is not installed.
"""

from __future__ import annotations

import re
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def _array_body(sql: str, name: str) -> str:
    match = re.search(
        rf"{re.escape(name)}\s+constant\s+text\[\]\s*:=\s*ARRAY\[(.*?)\];",
        sql,
        re.S | re.I,
    )
    assert match, f"SQL array is missing: {name}"
    return match.group(1)


def test_game_wipe_preserves_configuration_and_catalog_tables() -> None:
    sql = (ROOT / "db/runtime/reset_game_state_preserve_accounts.sql").read_text(
        encoding="utf-8"
    )
    protected = _array_body(sql, "protected_names")
    wipe = _array_body(sql, "wipe_names")

    for table in (
        "site_accounts",
        "player_web_accounts",
        "minecraft_account_links",
        "whitelist_account_links",
        "whitelist_requests",
        "ar_settings",
        "artifact_items_catalog",
        "narcotics_config_values",
        "narcotics_schema_version",
    ):
        assert re.search(rf"'{re.escape(table)}'", protected), table
        assert not re.search(rf"'{re.escape(table)}'", wipe), table

    for table in (
        "artifact_shops",
        "artifact_purchases",
        "narcotics_brewing_states",
        "narcotics_brewing_completion_intents",
        "narcotics_item_texture_migrations",
        "elections",
        "ar_accounts",
        "cmv731_vote_sessions",
        "cmv731_votes",
        "cmv7_ar_balances",
        "cmv7_ar_assets",
        "cmv7_election_settings",
        "cmv7_president_state",
        "cmv7_polling_stations",
        "cmv7_audit",
        "audit",
    ):
        assert re.search(rf"'{re.escape(table)}'", wipe), table

    reset_wrapper = (
        ROOT / "deploy/ubuntu/reset_game_state_preserve_accounts.sh"
    ).read_text(encoding="utf-8")
    for table in ("password_hashes", "bank_pin_hashes", "bank_account_pins"):
        assert re.search(rf"\b{re.escape(table)}\b", reset_wrapper), table

    clean_world_sql = (ROOT / "db/runtime/clean_world_state.sql").read_text(
        encoding="utf-8"
    )
    for table in (
        "ar_settings",
        "artifact_items_catalog",
        "narcotics_config_values",
        "narcotics_schema_version",
    ):
        assert not re.search(rf"'{re.escape(table)}'", clean_world_sql), table
    assert re.search(r"'artifact_shops'", clean_world_sql)


def test_world_backup_is_scheduled_and_low_priority() -> None:
    script = ROOT / "deploy/ubuntu/world_backup.sh"
    service = ROOT / "admin-web/deploy/copimine-world-backup.service"
    timer = ROOT / "admin-web/deploy/copimine-world-backup.timer"
    common = (ROOT / "deploy/shared/common.sh").read_text(encoding="utf-8")

    assert script.is_file()
    script_text = script.read_text(encoding="utf-8")
    assert "flock" in script_text
    assert "rsync" in script_text
    assert "save-off" in script_text
    assert "save-all flush" in script_text
    assert "save-on" in script_text
    assert "18000" in script_text
    assert "RCON_READY_TIMEOUT_SECONDS" in script_text
    assert "RCON_RETRY_INTERVAL_SECONDS" in script_text
    assert "wait_for_rcon" in script_text
    assert "run_rcon list" in script_text
    assert re.search(r"mv\s+--?\S*\$", script_text) or "mv --" in script_text

    assert service.is_file()
    service_text = service.read_text(encoding="utf-8")
    assert "world_backup.sh" in service_text
    assert "Nice=19" in service_text
    assert "IOSchedulingClass=idle" in service_text

    assert timer.is_file()
    timer_text = timer.read_text(encoding="utf-8")
    assert "OnUnitActiveSec=1h" in timer_text
    assert "Persistent=true" in timer_text

    assert "copimine-world-backup.service" in common
    assert "copimine-world-backup.timer" in common


def test_world_restore_helper_is_guarded_and_restores_only_the_latest_snapshot() -> None:
    restore = ROOT / "deploy/ubuntu/world_restore_latest.sh"
    common = (ROOT / "deploy/shared/common.sh").read_text(encoding="utf-8")
    assert restore.is_file()
    restore_text = restore.read_text(encoding="utf-8")
    assert "COPIMINE_CONFIRM_WORLD_RESTORE" in restore_text
    assert "worlds-*" in restore_text
    assert "sort -r" in restore_text
    assert "systemctl stop copimine-minecraft" in restore_text
    assert "systemctl start copimine-minecraft" in restore_text
    assert "rsync" in restore_text
    assert "realpath" in restore_text
    assert "world_restore_latest.sh" in common


def test_noncritical_database_backup_is_separate_hourly_contract() -> None:
    script = ROOT / "deploy/ubuntu/backup_noncritical_db.sh"
    service = ROOT / "admin-web/deploy/copimine-noncritical-db-backup.service"
    timer = ROOT / "admin-web/deploy/copimine-noncritical-db-backup.timer"
    common = (ROOT / "deploy/shared/common.sh").read_text(encoding="utf-8")
    assert script.is_file()
    script_text = script.read_text(encoding="utf-8")
    assert "--data-only" in script_text
    assert "RETENTION_SECONDS=\"${COPIMINE_NONCRITICAL_DB_RETENTION_SECONDS:-86400}\"" in script_text
    assert "site_accounts" not in script_text
    assert "whitelist_account_links" not in script_text
    assert "pg_dump" in script_text
    assert "sha256sum" in script_text
    assert service.is_file()
    assert "backup_noncritical_db.sh" in service.read_text(encoding="utf-8")
    assert timer.is_file()
    timer_text = timer.read_text(encoding="utf-8")
    assert "OnUnitActiveSec=12h" in timer_text
    assert "Persistent=true" in timer_text
    assert "copimine-noncritical-db-backup.service" in common
    assert "copimine-noncritical-db-backup.timer" in common


def test_wipe_path_requires_durable_world_snapshot() -> None:
    unpack = (
        ROOT / "deploy/ubuntu/copimine_unpack_and_verify.sh"
    ).read_text(encoding="utf-8")
    reset = (
        ROOT / "deploy/ubuntu/reset_game_state_preserve_accounts.sh"
    ).read_text(encoding="utf-8")
    assert "world_backup.sh" in unpack
    assert "WIPE_WORLDS" in unpack
    assert "flock" in reset
    assert "copimine-before-wipe.dump" in reset
    assert "published snapshot: " in reset
    assert "cp -al" in reset
    assert "worlds-pre-wipe" in reset


def test_modpack_contains_only_mods_directory() -> None:
    archive = ROOT / "thirdparty/CopiMineMods.zip"
    with zipfile.ZipFile(archive) as bundle:
        members = [name.replace("\\", "/").strip("/") for name in bundle.namelist()]
    assert members
    assert all(name == "mods" or name.startswith("mods/") for name in members)
    assert all(name.endswith(".jar") or name == "mods" for name in members)
