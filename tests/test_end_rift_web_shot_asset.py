"""Asset contracts for the 26.3 End Rift projectile display models."""

import importlib.util
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "resourcepacks" / "models_manifest.json"
MODEL = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "models" / "item" / "end_rift_web_shot.json"
TEXTURE = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "end_rift_web_shot.png"
MIGRATION_BUILDER = ROOT / "resourcepacks" / "build-migration-pack.py"
MODEL_DATA = 830030
PROJECTILE_ASSETS = (
    ("end_rift_rift_bolt", 830031, "end_rift_rift_bolt"),
    ("end_rift_dragon_breath", 830032, "end_rift_dragon_breath"),
    ("end_rift_void_burst", 830033, "end_rift_void_burst"),
)


def test_web_shot_is_registered_as_a_paper_model_with_a_transparent_32px_texture():
    rows = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))["items"]
    matches = [row for row in rows if row["id"] == "end_rift_web_shot"]
    assert matches == [{
        "id": "end_rift_web_shot",
        "custom_model_data": MODEL_DATA,
        "base_material": "paper",
        "model": "copimine:item/end_rift_web_shot",
        "texture": "copimine:item/end_rift_web_shot",
    }]
    paper_ids = [row["custom_model_data"] for row in rows if row["base_material"] == "paper"]
    assert paper_ids.count(MODEL_DATA) == 1

    model = json.loads(MODEL.read_text(encoding="utf-8"))
    assert model == {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "copimine:item/end_rift_web_shot"},
    }
    with Image.open(TEXTURE) as image:
        assert image.mode == "RGBA"
        assert image.size == (32, 32)
        assert image.getchannel("A").getextrema() == (0, 255)
        assert len(image.getcolors(32 * 32) or []) <= 8

    # The custom display uses a separate Paper item; ordinary projectile sprites stay vanilla.
    assert not (ROOT / "resourcepacks" / "src" / "assets" / "minecraft" / "models" / "item" / "snowball.json").exists()
    assert all(row["base_material"] != "snowball" for row in rows)


def test_migration_dispatch_routes_only_the_registered_web_shot_paper_id():
    spec = importlib.util.spec_from_file_location("end_rift_web_shot_migration_pack", MIGRATION_BUILDER)
    migration_pack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(migration_pack)

    rows = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))["items"]
    entry = next(row for row in rows if row["id"] == "end_rift_web_shot")
    native_paper = migration_pack.model("minecraft:item/paper")
    definition = migration_pack.dispatch([entry], native_paper, None)

    def choose(value):
        selected = definition["fallback"]
        for rule in definition["entries"]:
            if value >= rule["threshold"]:
                selected = rule["model"]
        return selected

    assert choose(MODEL_DATA) == migration_pack.model("copimine:item/end_rift_web_shot")
    assert choose(MODEL_DATA + 1) == native_paper
    assert definition["fallback"] == native_paper


def test_elite_projectile_assets_are_closed_3d_meshes_with_per_spell_atlases():
    rows = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))["items"]
    by_id = {row["id"]: row for row in rows}
    paper_ids = [row["custom_model_data"] for row in rows if row["base_material"] == "paper"]

    for item_id, model_data, name in PROJECTILE_ASSETS:
        assert by_id[item_id] == {
            "id": item_id,
            "custom_model_data": model_data,
            "base_material": "paper",
            "model": f"copimine:item/{name}",
            "texture": f"copimine:item/{name}",
        }
        assert paper_ids.count(model_data) == 1

    # The three elite spells are closed 3D meshes, not flat sprites: they fly as
    # authored projectiles and must therefore carry real geometry and a UV atlas.
    # A shared texture pair made every ability look identical, so each spell now
    # owns its own atlas and its own silhouette.
    silhouettes = {}
    for item_id, model_data, name in PROJECTILE_ASSETS:
        model_path = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "models" / "item" / f"{name}.json"
        texture_path = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / f"{name}.png"
        model = json.loads(model_path.read_text(encoding="utf-8"))

        assert model["parent"] == "minecraft:block/block"
        assert set(model["textures"].values()) == {f"copimine:item/{name}"}

        elements = model["elements"]
        assert len(elements) >= 6
        for element in elements:
            assert set(element["faces"]) == {"down", "up", "north", "south", "west", "east"}
            low, high = element["from"], element["to"]
            assert all(0 <= value <= 16 for value in low + high)
            assert all(a < b for a, b in zip(low, high)), "a degenerate element is invisible"
            for face in element["faces"].values():
                uv = face["uv"]
                assert 0 <= uv[0] < uv[2] <= 64 and 0 <= uv[1] < uv[3] <= 64

        silhouettes[name] = frozenset(
            (tuple(element["from"]), tuple(element["to"])) for element in elements
        )

        with Image.open(texture_path) as image:
            assert image.mode == "RGBA"
            assert image.size == (64, 64)
            assert image.getchannel("A").getextrema() == (0, 255)
            # A UV atlas needs more than the handful of flat colours a sprite used.
            assert len(image.getcolors(64 * 64) or []) > 32

    assert len(set(silhouettes.values())) == len(silhouettes)

    assert all(row["base_material"] != "snowball" for row in rows)
    assert not (ROOT / "resourcepacks" / "src" / "assets" / "minecraft" / "models" / "item" / "snowball.json").exists()


def test_elite_projectile_paper_dispatch_selects_each_model_and_resets_to_vanilla():
    spec = importlib.util.spec_from_file_location("elite_projectile_migration_pack", MIGRATION_BUILDER)
    migration_pack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(migration_pack)

    rows = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))["items"]
    entries = [row for row in rows if row["id"] in {item_id for item_id, _, _ in PROJECTILE_ASSETS}]
    assert len(entries) == 3
    native_paper = migration_pack.model("minecraft:item/paper")
    definition = migration_pack.dispatch(entries, native_paper, None)

    def choose(value):
        selected = definition["fallback"]
        for rule in definition["entries"]:
            if value >= rule["threshold"]:
                selected = rule["model"]
        return selected

    for item_id, model_data, name in PROJECTILE_ASSETS:
        assert choose(model_data) == migration_pack.model(f"copimine:item/{name}")
        assert choose(migration_pack.next_float32(model_data)) == native_paper
    assert choose(830034) == native_paper
