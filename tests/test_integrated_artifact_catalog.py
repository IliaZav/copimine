from __future__ import annotations

import json
import zipfile
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def _catalogs() -> tuple[dict[str, dict], dict[str, dict]]:
    config = yaml.safe_load((ROOT / "copimine-artifacts/items.yml").read_text(encoding="utf-8"))
    normal = {item["id"]: item for item in config["items"]}
    donation = {item["item-id"]: item for item in config["donation-catalog"]["items"]}
    return normal, donation


def _method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"unterminated method: {signature}")


def test_new_artifact_items_keep_distinct_ids_and_runtime_effects() -> None:
    normal, donation = _catalogs()

    assert normal["gravedigger_contract"]["effect"] == "GRAVEDIGGER_CONTRACT"
    assert normal["signal_bell"]["effect"] == "SIGNAL_BELL"
    assert donation["berserker_heart"]["effect-profile-id"] == "BERSERKER_HEART"
    assert donation["zhilorez_pickaxe"]["effect-profile-id"] == "VEIN_MINER"
    assert donation["donation_night_cloak"]["base-material"] == "PHANTOM_MEMBRANE"
    assert donation["donation_night_cloak"]["custom-model-data"] == 20012
    assert donation["donation_night_cloak"]["owner-bound"] is False

    # End Rift keeps its own reward identity and has no donation gameplay power.
    reward = normal["night_cloak"]
    assert reward["source"] == "ADMIN_ONLY"
    assert reward["material"] == "LEATHER_CHESTPLATE"
    assert reward["custom_model_data"] == 830005
    assert reward["effect"] == "NONE"

    source = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(
        encoding="utf-8"
    )
    for marker in (
        "captureGravediggerContract",
        "onSignalBellInventoryOpen",
        '"BERSERKER_HEART"',
        "tryVeinMine",
        '"donation_night_cloak"',
    ):
        assert marker in source


def test_latest_scythe_and_shield_catalog_values_are_preserved() -> None:
    _, donation = _catalogs()
    scythe = donation["kosa_nalogovoy_inspekcii"]
    shield = donation["ne_segodnya_suka_shield"]

    assert scythe["cooldown-seconds"] == 0
    assert scythe["proc-chance"] == 0.20
    assert "2.5%" in scythe["lore"][0]
    assert shield["cooldown-seconds"] == 20

    source = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(
        encoding="utf-8"
    )
    assert "KOSA_HEALTH_PROC_CHANCE" in source
    assert "KOSA_HUNGER_PROC_CHANCE" in source
    assert "SHIELD_LIGHTNING_COOLDOWN_SECONDS" in source


def test_donation_catalog_metadata_tracks_the_current_catalog_revision() -> None:
    config = yaml.safe_load((ROOT / "copimine-artifacts/items.yml").read_text(encoding="utf-8"))
    metadata = config["donation-catalog"]

    assert metadata["catalog-version"] >= 2
    assert metadata["updated-at"] > 1782345600


def test_every_new_donation_item_has_a_matching_resource_mapping() -> None:
    normal, donation = _catalogs()
    item_ids = {"gravedigger_contract", "signal_bell", "berserker_heart", "zhilorez_pickaxe", "donation_night_cloak"}
    mappings = json.loads((ROOT / "resourcepacks/item_texture_sources.json").read_text(encoding="utf-8"))[
        "items"
    ]
    manifest = json.loads((ROOT / "resourcepacks/models_manifest.json").read_text(encoding="utf-8"))[
        "items"
    ]
    mappings_by_id = {entry["id"]: entry for entry in mappings}
    models_by_id = {entry["id"]: entry for entry in manifest}

    for item_id in item_ids:
        item = normal[item_id] if item_id in {"gravedigger_contract", "signal_bell"} else donation[item_id]
        assert item_id in mappings_by_id
        expected_catalog = "AR" if item_id in {"gravedigger_contract", "signal_bell"} else "DONATION"
        expected_model_data = item["custom_model_data"] if expected_catalog == "AR" else item["custom-model-data"]
        assert mappings_by_id[item_id]["catalog"] == expected_catalog
        assert mappings_by_id[item_id]["custom_model_data"] == expected_model_data
        assert item_id in models_by_id
        model = models_by_id[item_id]["model"].split(":", 1)[1]
        texture = models_by_id[item_id]["texture"].split(":", 1)[1]
        assert (ROOT / f"resourcepacks/src/assets/copimine/models/{model}.json").is_file()
        assert (ROOT / f"resourcepacks/src/assets/copimine/textures/{texture}.png").is_file()

    # The End Rift reward model stays mapped to its separate vanilla chestplate.
    event = mappings_by_id["night_cloak"]
    assert event["catalog"] == "ADMIN_ONLY"
    assert event["custom_model_data"] == 830005
    assert models_by_id["night_cloak"]["model"] == "copimine:item/night_cloak"


def test_donation_night_cloak_compatibility_migration_is_narrow_and_repeatable() -> None:
    source = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(
        encoding="utf-8"
    )
    migration = _method_body(source, "private void migrateDonationNightCloakIdentity(")
    assert "donation_purchases" in migration
    assert "donation_item_claims" in migration
    assert "artifact_item_instances" in migration
    assert "artifact_pending_deliveries" in migration
    assert "UPDATE artifact_purchases" not in migration
    assert "UPDATE donation_purchases" not in migration
    assert "END_RIFT_EVENT" not in migration


def test_built_artifacts_jar_embeds_the_current_catalog() -> None:
    catalog_path = ROOT / "copimine-artifacts/items.yml"
    expected = yaml.safe_load(catalog_path.read_text(encoding="utf-8"))
    jar_path = ROOT / "copimine-artifacts/CopiMineArtifacts.jar"

    with zipfile.ZipFile(jar_path) as archive:
        embedded = yaml.safe_load(archive.read("items.yml").decode("utf-8"))

    assert embedded == expected
