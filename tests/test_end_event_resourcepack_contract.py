from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "src" / "assets" / "copimine"


def test_end_event_assets_and_manifests_exist() -> None:
    expected = [
        PACK / "textures" / "block" / "end_event_core.png",
        PACK / "textures" / "block" / "end_event_pad.png",
        PACK / "textures" / "item" / "end_event_boss.png",
        PACK / "textures" / "item" / "artifacts" / "rift_core_shard.png",
        ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimine" / "textures" / "entity" / "end_rift_guardian.png",
    ]
    for path in expected:
        assert path.is_file(), path


def test_end_event_cmd_ids_are_reserved_without_collisions() -> None:
    model_manifest = (ROOT / "resourcepacks" / "models_manifest.json").read_text(encoding="utf-8")
    source_manifest = (ROOT / "resourcepacks" / "item_texture_sources.json").read_text(encoding="utf-8")
    for cmd in (830001, 830002, 830003, 830004):
        assert str(cmd) in model_manifest
    assert "rift_core_shard" in source_manifest
