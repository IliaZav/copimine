from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"

sys.path.insert(0, str(ROOT / "admin-web"))
from backend.commerce_catalog import load_commerce_catalog  # noqa: E402


def test_latest_public_catalog_contains_all_current_artifacts_and_no_compass() -> None:
    catalog = load_commerce_catalog(ITEMS)
    ar_ids = {str(row["item_id"]) for row in catalog["ar"]["items"]}
    donation_ids = {str(row["item_id"]) for row in catalog["donation"]["items"]}

    assert {"kopatel_transhey_shovel", "gravedigger_contract", "signal_bell"} <= ar_ids
    assert {"kosa_nalogovoy_inspekcii", "berserker_heart", "zhilorez_pickaxe", "night_cloak"} <= donation_ids
    assert "gde_moy_lut_blyat_compass" not in donation_ids


def test_latest_artifact_runtime_uses_independent_scythe_effects() -> None:
    source = JAVA.read_text(encoding="utf-8")

    assert "AR_THEFT_PROC_CHANCE = 0.025D" in source
    assert "KOSA_HEALTH_PROC_CHANCE = 0.30D" in source
    assert "KOSA_HUNGER_PROC_CHANCE = 0.20D" in source
    assert "KOSA_WITHER_PROC_CHANCE = 0.20D" in source
    assert "KOSA_BLINDNESS_PROC_CHANCE = 0.10D" in source
    assert "applyKosaCombatEffects" in source
    assert "1 + random.nextInt(3)" in source


def test_latest_artifact_runtime_has_policies_for_new_catalog_items() -> None:
    source = JAVA.read_text(encoding="utf-8")

    assert '"GRAVEDIGGER_CONTRACT"' in source
    assert '"SIGNAL_BELL"' in source
    assert '"BERSERKER_HEART"' in source
    assert '"VEIN_MINER"' in source
    assert '"NIGHT_CLOAK"' in source
