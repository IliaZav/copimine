from __future__ import annotations

import json
import struct
from pathlib import Path


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
        assert (width, height) == (64, 64), path


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
        assert model["parent"] == "minecraft:item/generated"
        assert model["textures"]["layer0"] == f"copimine:item/{texture_name}"
