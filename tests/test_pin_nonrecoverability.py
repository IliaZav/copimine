"""Regression tests for the non-recoverable bank PIN contract."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1].joinpath("admin-web")))

from backend.main import public_pin_status


def test_public_pin_status_drops_reversible_or_plaintext_values() -> None:
    payload = public_pin_status(
        {
            "set": True,
            "mustChange": False,
            "status": "configured",
            "visiblePin": "1234",
            "pin": "1234",
            "temporaryPin": "1234",
        }
    )

    assert payload == {
        "set": True,
        "mustChange": False,
        "status": "configured",
    }


def test_authoritative_pin_tables_do_not_declare_reversible_columns() -> None:
    source = Path(__file__).parents[1].joinpath("admin-web", "backend", "main.py").read_text(encoding="utf-8")
    migration = Path(__file__).parents[1].joinpath(
        "db", "migrations", "20260802_016_drop_recoverable_bank_pin_copies.sql"
    ).read_text(encoding="utf-8")

    assert "SELECT pin_sealed" not in source
    assert "INSERT INTO bank_pin_hashes(minecraft_uuid,site_account_id,pin_hash,pin_sealed" not in source
    assert "INSERT INTO bank_account_pins(account_id,pin_hash,pin_sealed" not in source
    assert "seal_persistent_pin" not in source
    assert "reveal_persistent_pin" not in source
    assert "ALTER TABLE {table} DROP COLUMN pin_sealed" in source
    assert "ALTER TABLE bank_pin_hashes DROP COLUMN IF EXISTS pin_sealed" in migration
    assert "ALTER TABLE bank_account_pins DROP COLUMN IF EXISTS pin_sealed" in migration
