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
        "narcotics_item_texture_migrations",
        "elections",
        "ar_accounts",
    ):
        assert re.search(rf"'{re.escape(table)}'", wipe), table

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
    assert "43200" in script_text
    assert re.search(r"mv\s+--?\S*\$", script_text) or "mv --" in script_text

    assert service.is_file()
    service_text = service.read_text(encoding="utf-8")
    assert "world_backup.sh" in service_text
    assert "Nice=19" in service_text
    assert "IOSchedulingClass=idle" in service_text

    assert timer.is_file()
    timer_text = timer.read_text(encoding="utf-8")
    assert "OnUnitActiveSec=5h" in timer_text
    assert "Persistent=true" in timer_text

    assert "copimine-world-backup.service" in common
    assert "copimine-world-backup.timer" in common


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


def test_modpack_contains_only_mods_directory() -> None:
    archive = ROOT / "thirdparty/CopiMineMods.zip"
    with zipfile.ZipFile(archive) as bundle:
        members = [name.replace("\\", "/").strip("/") for name in bundle.namelist()]
    assert members
    assert all(name == "mods" or name.startswith("mods/") for name in members)
    assert all(name.endswith(".jar") or name == "mods" for name in members)
