from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCES = ROOT / "resourcepacks" / "item_texture_sources.json"
MANIFEST = ROOT / "resourcepacks" / "models_manifest.json"
BUILDER = ROOT / "resourcepacks" / "build-resourcepack.py"
SRC = ROOT / "resourcepacks" / "src"
STAGE = ROOT / "resourcepacks" / "build" / "_stage"
ZIP_PATH = ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"

PROJECTILE_ITEMS = {
    "combat_crossbow": ("BOW", 10014, "teleport_bow"),
    "cobblestone_trail_bow": ("BOW", 10015, "cobblestone_trail_bow"),
    "explosive_crossbow": ("CROSSBOW", 10016, "explosive_crossbow"),
}
UTILITY_ITEMS = {
    "repair_kit": ("SHEARS", 10025, "repair_kit"),
    "return_stone": ("ECHO_SHARD", 10026, "return_stone"),
    "angel_wings": ("FEATHER", 10028, "angel_seal"),
    "infinite_torch": ("TORCH", 10027, "infinite_torch"),
}
NOTE_ITEMS = [
    "narcotic_recipe_feta",
    "narcotic_recipe_kola",
    "narcotic_recipe_girion",
    "narcotic_recipe_sbp",
    "narcotic_recipe_sos",
    "narcotic_recipe_drun",
    "narcotic_recipe_chups",
    "narcotic_recipe_borshevik",
]


def item_block(item_id: str) -> str:
    text = ITEMS.read_text(encoding="utf-8")
    match = re.search(rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id:|^donation-catalog:|\Z)", text)
    assert match is not None, f"missing catalog item {item_id}"
    return match.group(0)


def model_json(path: Path) -> dict:
    assert path.is_file(), f"missing model {path}"
    return json.loads(path.read_text(encoding="utf-8"))


def assert_predicate(model: dict, predicate: dict, target: str) -> None:
    assert any(
        override.get("predicate") == predicate and override.get("model") == target
        for override in model.get("overrides", [])
    ), (predicate, target, model.get("overrides", []))


def test_new_projectiles_and_notes_have_custom_model_data():
    for item_id, (material, custom_model_data, _) in {**PROJECTILE_ITEMS, **UTILITY_ITEMS}.items():
        block = item_block(item_id)
        assert f"material: {material}" in block
        assert f"custom_model_data: {custom_model_data}" in block
        assert "custom-texture-mode-allowed: true" in block

    for offset, item_id in enumerate(NOTE_ITEMS, start=10017):
        block = item_block(item_id)
        assert "material: WRITTEN_BOOK" in block
        assert f"custom_model_data: {offset}" in block
        assert "custom-texture-mode-allowed: true" in block


def test_manifest_and_texture_sources_cover_every_new_custom_item():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    sources = json.loads(SOURCES.read_text(encoding="utf-8"))
    manifest_by_id = {row["id"]: row for row in manifest["items"]}
    sources_by_id = {row["id"]: row for row in sources["items"]}

    expected = list(PROJECTILE_ITEMS) + list(UTILITY_ITEMS) + NOTE_ITEMS
    for item_id in expected:
        assert item_id in manifest_by_id
        assert item_id in sources_by_id
        assert manifest_by_id[item_id]["custom_model_data"] == sources_by_id[item_id]["custom_model_data"]

    note_models = {manifest_by_id[item_id]["model"] for item_id in NOTE_ITEMS}
    note_textures = {manifest_by_id[item_id]["texture"] for item_id in NOTE_ITEMS}
    assert note_models == {"copimine:item/artifacts/recipe_note"}
    assert note_textures == {"copimine:item/artifacts/recipe_note"}


def test_vanilla_predicate_composition_is_implemented():
    builder = BUILDER.read_text(encoding="utf-8")
    assert 'if material == "bow":' in builder
    assert 'if material == "crossbow":' in builder
    for marker in ('"pulling": 1', '"pull": 0.65', '"pull": 0.9', '"charged": 1', '"firework": 1'):
        assert marker in builder
    assert '"custom_model_data": custom_model_data' in builder


def test_source_models_and_textures_have_all_animation_states():
    expected = {
        "teleport_bow": ["", "_pulling_0", "_pulling_1", "_pulling_2"],
        "cobblestone_trail_bow": ["", "_pulling_0", "_pulling_1", "_pulling_2"],
        "explosive_crossbow": ["", "_pulling_0", "_pulling_1", "_pulling_2", "_charged", "_charged_firework"],
    }
    for stem, states in expected.items():
        for suffix in states:
            model = SRC / "assets" / "copimine" / "models" / "item" / "artifacts" / f"{stem}{suffix}.json"
            texture = SRC / "assets" / "copimine" / "textures" / "item" / "artifacts" / f"{stem}{suffix}.png"
            assert model.is_file(), model
            assert texture.is_file(), texture
            with Image.open(texture) as image:
                expected_size = (32, 128) if stem == "explosive_crossbow" and suffix == "_charged" else (32, 32)
                assert image.size == expected_size
                assert image.mode == "RGBA"

    for stem in ("repair_kit", "return_stone", "infinite_torch"):
        model = SRC / "assets" / "copimine" / "models" / "item" / "artifacts" / f"{stem}.json"
        texture = SRC / "assets" / "copimine" / "textures" / "item" / "artifacts" / f"{stem}.png"
        assert model.is_file(), model
        assert texture.is_file(), texture
        with Image.open(texture) as image:
            assert image.size == (32, 32)
            assert image.mode == "RGBA"

    wings_model = SRC / "assets" / "copimine" / "models" / "item" / "artifacts" / "angel_wings.json"
    assert wings_model.is_file()
    assert model_json(wings_model)["textures"]["layer0"] == "copimine:item/artifacts/angel_seal"
    with Image.open(SRC / "assets" / "copimine" / "textures" / "item" / "artifacts" / "angel_seal.png") as image:
        assert image.size == (32, 32)
        assert image.mode == "RGBA"

    common_model = SRC / "assets" / "copimine" / "models" / "item" / "artifacts" / "recipe_note.json"
    common_texture = SRC / "assets" / "copimine" / "textures" / "item" / "artifacts" / "recipe_note.png"
    assert common_model.is_file()
    assert common_texture.is_file()
    with Image.open(common_texture) as image:
        assert image.size == (32, 32)
        assert image.mode == "RGBA"


def test_explosive_crossbow_frames_preserve_vanilla_shape():
    reference_dir = ROOT / "resourcepacks" / "reference" / "vanilla" / "item"
    source_names = {
        "": "crossbow_standby.png",
        "_pulling_0": "crossbow_pulling_0.png",
        "_pulling_1": "crossbow_pulling_1.png",
        "_pulling_2": "crossbow_pulling_2.png",
    }
    for suffix, source_name in source_names.items():
        texture = SRC / "assets" / "copimine" / "textures" / "item" / "artifacts" / f"explosive_crossbow{suffix}.png"
        with Image.open(texture) as output_file, Image.open(reference_dir / source_name) as source_file:
            output = output_file.convert("RGBA").resize((16, 16), Image.Resampling.NEAREST)
            source = source_file.convert("RGBA")
            assert output_file.size == (32, 32)
            assert any(
                output.getpixel((x, y))[:3] != source.getpixel((x, y))[:3]
                for y in range(16)
                for x in range(16)
                if source.getpixel((x, y))[3]
            )
            for y in range(16):
                for x in range(16):
                    assert output.getpixel((x, y))[3] == source.getpixel((x, y))[3], (texture, x, y)


def test_built_models_preserve_vanilla_states_and_zip_contains_new_assets():
    bow = model_json(STAGE / "assets" / "minecraft" / "models" / "item" / "bow.json")
    crossbow = model_json(STAGE / "assets" / "minecraft" / "models" / "item" / "crossbow.json")
    torch = model_json(STAGE / "assets" / "minecraft" / "models" / "item" / "torch.json")
    written_book = model_json(STAGE / "assets" / "minecraft" / "models" / "item" / "written_book.json")

    assert_predicate(bow, {"pulling": 1}, "minecraft:item/bow_pulling_0")
    assert_predicate(bow, {"pulling": 1, "pull": 0.65}, "minecraft:item/bow_pulling_1")
    assert_predicate(bow, {"pulling": 1, "pull": 0.9}, "minecraft:item/bow_pulling_2")
    assert_predicate(crossbow, {"pulling": 1}, "minecraft:item/crossbow_pulling_0")
    assert_predicate(crossbow, {"pulling": 1, "pull": 0.58}, "minecraft:item/crossbow_pulling_1")
    assert_predicate(crossbow, {"pulling": 1, "pull": 1}, "minecraft:item/crossbow_pulling_2")
    assert_predicate(crossbow, {"charged": 1}, "minecraft:item/crossbow_arrow")
    assert_predicate(crossbow, {"charged": 1, "firework": 1}, "minecraft:item/crossbow_firework")
    assert crossbow["textures"]["layer0"] == "minecraft:item/crossbow_standby"
    assert torch["parent"] == "minecraft:block/torch"
    assert "textures" not in torch
    assert any(override.get("predicate", {}).get("custom_model_data") == 10014 for override in bow["overrides"])
    assert any(override.get("predicate", {}).get("custom_model_data") == 10016 for override in crossbow["overrides"])
    assert any(override.get("predicate", {}).get("custom_model_data") == 10017 for override in written_book["overrides"])

    assert ZIP_PATH.is_file()
    with ZipFile(ZIP_PATH) as archive:
        assert archive.testzip() is None
        names = set(archive.namelist())
        assert "assets/minecraft/models/item/bow.json" in names
        assert "assets/minecraft/models/item/crossbow.json" in names
        assert "assets/minecraft/models/item/written_book.json" in names
        assert "assets/copimine/textures/item/artifacts/teleport_bow_pulling_2.png" in names
        assert "assets/copimine/textures/item/artifacts/explosive_crossbow_charged_firework.png" in names
        assert "assets/copimine/textures/item/artifacts/recipe_note.png" in names


def test_bow_and_crossbow_root_models_preserve_vanilla_hand_transforms():
    expected = {
        "bow": {
            "firstperson_righthand": {
                "rotation": [0, -90, 25],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
            "firstperson_lefthand": {
                "rotation": [0, 90, -25],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
        },
        "crossbow": {
            "firstperson_righthand": {
                "rotation": [-90, 0, -55],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
            "firstperson_lefthand": {
                "rotation": [-90, 0, 35],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
        },
    }
    for material, transforms in expected.items():
        model = model_json(STAGE / "assets" / "minecraft" / "models" / "item" / f"{material}.json")
        assert {key: model.get("display", {}).get(key) for key in transforms} == transforms
