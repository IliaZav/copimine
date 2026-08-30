from __future__ import annotations

import json
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
RUNTIME_ITEMS = ROOT / "minecraft" / "server" / "plugins" / "CopiMineArtifacts" / "items.yml"
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
MODELS = ROOT / "resourcepacks" / "models_manifest.json"
ARTIFACT_JAR = ROOT / "copimine-artifacts" / "CopiMineArtifacts.jar"


def test_current_shop_roster_has_trench_shovel_and_no_retired_compass() -> None:
    items = ITEMS.read_text(encoding="utf-8")
    runtime_items = RUNTIME_ITEMS.read_text(encoding="utf-8")
    models = json.loads(MODELS.read_text(encoding="utf-8"))

    assert "id: kopatel_transhey_shovel" in items
    assert "effect: TRENCH_BONUS" in items
    assert "item-id: gde_moy_lut_blyat_compass" not in items
    assert "item-id: gde_moy_lut_blyat_compass" not in runtime_items
    assert all(item.get("id") != "gde_moy_lut_blyat_compass" for item in models["items"])


def test_runtime_no_longer_registers_retired_compass_ability() -> None:
    source = JAVA.read_text(encoding="utf-8")

    assert '"LOOT_COMPASS"' not in source
    assert "activateLootCompass" not in source
    assert "keyCompassCooldownUntil" not in source
    assert "TRENCH_BONUS" in source


def test_tax_scythe_uses_current_catalog_and_effect_path() -> None:
    items = ITEMS.read_text(encoding="utf-8")
    source = JAVA.read_text(encoding="utf-8")

    assert "item-id: kosa_nalogovoy_inspekcii" in items
    assert "effect-profile-id: NALOGOVAYA_KOSA" in items
    assert "proc-chance: 0.20" in items
    assert 'if ("NALOGOVAYA_KOSA".equals(var16))' in source
    assert "applyKosaCombatEffects" in source
    assert "KOSA_HEALTH_PROC_CHANCE = 0.30D" in source
    assert "KOSA_HUNGER_PROC_CHANCE = 0.20D" in source
    assert "KOSA_WITHER_PROC_CHANCE = 0.20D" in source
    assert "KOSA_BLINDNESS_PROC_CHANCE = 0.10D" in source
    assert "event.setDamage(event.getDamage() + 3.0)" in source
    assert "healPlayerCapped" in source
    assert "tryRareArTheft" in source
    assert "AR_THEFT_PROC_CHANCE" in source


def test_built_artifact_jar_contains_the_same_current_catalog() -> None:
    assert ARTIFACT_JAR.is_file()
    with zipfile.ZipFile(ARTIFACT_JAR) as archive:
        bundled_items = archive.read("items.yml").decode("utf-8")
        plugin_yml = archive.read("plugin.yml").decode("utf-8")

    assert "id: kopatel_transhey_shovel" in bundled_items
    assert "item-id: gde_moy_lut_blyat_compass" not in bundled_items
    assert "version:" in plugin_yml
