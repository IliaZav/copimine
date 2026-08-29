from __future__ import annotations

import json
import struct
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "src" / "assets" / "copimine"


def test_end_event_assets_and_manifests_exist() -> None:
    expected = [
        PACK / "textures" / "item" / "end_event_core.png",
        PACK / "textures" / "item" / "end_event_core_charged.png",
        PACK / "textures" / "item" / "end_event_pad.png",
        PACK / "textures" / "item" / "end_event_boss.png",
        PACK / "textures" / "item" / "rift_core_shard.png",
        ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity" / "end_rift_guardian.png",
    ]
    for path in expected:
        assert path.is_file(), path
        assert path.stat().st_size > 100, path
        width, height = struct.unpack(">II", path.read_bytes()[16:24])
        expected_dimensions = (
            (64, 32) if path.name == "end_rift_guardian.png"
            else (32, 32) if path.name.startswith("end_event_core") or path.name.startswith("end_event_pad")
            else (64, 64)
        )
        assert (width, height) == expected_dimensions, path


def test_end_event_assets_are_overlay_models_and_never_override_vanilla_blocks() -> None:
    block_assets = {
        "end_event_core.png": PACK / "textures" / "block" / "end_event_core.png",
        "end_event_core_charged.png": PACK / "textures" / "block" / "end_event_core_charged.png",
        "end_event_rune.png": PACK / "textures" / "block" / "end_event_rune.png",
        "end_event_rune_occupied.png": PACK / "textures" / "block" / "end_event_rune_occupied.png",
    }
    for path in block_assets.values():
        assert path.is_file(), path
        width, height = struct.unpack(">II", path.read_bytes()[16:24])
        expected_dimensions = (32, 32)
        assert (width, height) == expected_dimensions, path
    for forbidden in (
        ROOT / "resourcepacks/src/assets/minecraft/blockstates/crying_obsidian.json",
        ROOT / "resourcepacks/src/assets/minecraft/blockstates/respawn_anchor.json",
        ROOT / "resourcepacks/src/assets/minecraft/models/item/crying_obsidian.json",
        ROOT / "resourcepacks/src/assets/minecraft/models/item/respawn_anchor.json",
    ):
        assert not forbidden.exists(), forbidden
    for model_name, expected_parent in (
        ("end_event_core", "copimine:block/end_event_core"),
        ("end_event_core_charged", "copimine:block/end_event_core_charged"),
        ("end_event_pad", "copimine:block/end_event_rune"),
    ):
        model = json.loads((PACK / "models" / "item" / f"{model_name}.json").read_text(encoding="utf-8"))
        assert model["parent"] == expected_parent


def test_end_event_client_mob_textures_are_native_uv_sheets() -> None:
    entity_dir = ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"
    expected = {
        "end_rift_enderman.png": (64, 32),
        "end_rift_elite.png": (64, 32),
        "end_rift_guardian.png": (64, 32),
        "end_rift_spider.png": (64, 32),
        "end_rift_shulker.png": (64, 64),
        "end_rift_skeleton.png": (64, 32),
        "end_rift_elite_skeleton.png": (64, 32),
    }
    for name, dimensions in expected.items():
        path = entity_dir / name
        assert path.is_file(), path
        width, height = struct.unpack(">II", path.read_bytes()[16:24])
        assert (width, height) == dimensions, path


def test_end_event_client_mob_textures_have_visible_colored_pixels() -> None:
    entity_dir = ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"
    for name in ("end_rift_enderman.png", "end_rift_elite.png", "end_rift_spider.png", "end_rift_shulker.png", "end_rift_guardian.png"):
        with Image.open(entity_dir / name).convert("RGBA") as image:
            pixels = list(image.getdata())
        visible = [pixel for pixel in pixels if pixel[3] > 0]
        colorful = [pixel for pixel in visible if max(pixel[:3]) - min(pixel[:3]) >= 20]
        assert visible, name
        assert len(colorful) >= max(4, len(visible) // 25), name


def test_end_event_cmd_ids_are_reserved_without_collisions() -> None:
    manifest = json.loads((ROOT / "resourcepacks" / "models_manifest.json").read_text(encoding="utf-8"))
    rows = [row for row in manifest["items"] if int(row["custom_model_data"]) in range(830001, 830005)]
    assert {(row["id"], int(row["custom_model_data"])) for row in rows} == {
        ("end_event_core", 830001),
        ("end_event_core_charged", 830002),
        ("end_event_pad", 830003),
        ("rift_core_shard", 830004),
    }
    assert len({(row["base_material"].upper(), int(row["custom_model_data"])) for row in manifest["items"]}) == len(manifest["items"])


def test_end_event_models_reference_existing_self_made_textures() -> None:
    model_dir = PACK / "models" / "item"
    for model_name, texture_name in (
        ("end_event_core", "end_event_core"),
        ("end_event_core_charged", "end_event_core_charged"),
        ("end_event_pad", "end_event_pad"),
        ("rift_core_shard", "rift_core_shard"),
    ):
        model = json.loads((model_dir / f"{model_name}.json").read_text(encoding="utf-8"))
        if model_name == "end_event_core":
            assert model["parent"] == "copimine:block/end_event_core"
        elif model_name == "end_event_core_charged":
            assert model["parent"] == "copimine:block/end_event_core_charged"
        elif model_name == "end_event_pad":
            assert model["parent"] == "copimine:block/end_event_rune"
        else:
            assert model["parent"] == "minecraft:item/generated"
            assert model["textures"]["layer0"] == f"copimine:item/{texture_name}"


def test_end_event_item_textures_are_colored_opaque_art_not_paper_fallbacks() -> None:
    for path in (
        PACK / "textures" / "item" / "end_event_core.png",
        PACK / "textures" / "item" / "end_event_core_charged.png",
        PACK / "textures" / "item" / "end_event_pad.png",
        PACK / "textures" / "item" / "rift_core_shard.png",
    ):
        with Image.open(path).convert("RGBA") as image:
            pixels = list(image.getdata())
        visible = [pixel for pixel in pixels if pixel[3] > 0]
        colorful = [pixel for pixel in visible if max(pixel[:3]) - min(pixel[:3]) >= 24]
        assert len(visible) >= 16, path
        assert len(colorful) >= max(8, len(visible) // 20), path


def test_end_event_block_textures_are_symmetric_32px_and_bright_enough_for_gameplay() -> None:
    names = (
        "end_event_core.png",
        "end_event_core_charged.png",
        "end_event_rune.png",
        "end_event_rune_occupied.png",
    )
    for name in names:
        path = PACK / "textures" / "block" / name
        with Image.open(path).convert("RGBA") as image:
            assert image.size == (32, 32), path
            pixels = image.load()
            assert all(
                pixels[x, y] == pixels[31 - x, y] == pixels[x, 31 - y] == pixels[31 - x, 31 - y]
                for y in range(32)
                for x in range(32)
            ), path
            visible = [pixels[x, y] for y in range(32) for x in range(32) if pixels[x, y][3] > 0]
            assert max(max(pixel[:3]) for pixel in visible) >= 180, path
            assert sum(1 for pixel in visible if max(pixel[:3]) - min(pixel[:3]) >= 40) >= 32, path
