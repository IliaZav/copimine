from __future__ import annotations

import json
import subprocess
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
BUILDER = ROOT / "resourcepacks" / "build-resourcepack.py"


def test_resourcepack_builder_declares_every_end_event_item_asset() -> None:
    builder = BUILDER.read_text(encoding="utf-8")
    required = (
        "assets/copimine/models/item/end_event_core.json",
        "assets/copimine/models/item/end_event_core_charged.json",
        "assets/copimine/models/item/end_event_pad.json",
        "assets/copimine/textures/item/end_event_core.png",
        "assets/copimine/textures/item/end_event_core_charged.png",
        "assets/copimine/textures/item/end_event_pad.png",
    )
    for relative in required:
        assert f'"{relative}"' in builder, relative


def test_core_and_rune_displays_are_anchored_to_the_surface() -> None:
    source = MAIN.read_text(encoding="utf-8")
    assert "private Location coreOverlayLocation(Block core)" in source
    assert "return core.getLocation().add(0.5D, 0.5D, 0.5D);" in source
    assert "private Location runeOverlayLocation(Block floor)" in source
    assert "return floor.getLocation().add(0.5D, 1.42D, 0.5D);" in source
    assert "core.getLocation().add(0.5D, 1.5D, 0.5D)" not in source
    assert "floor.getLocation().add(0.5D, 1.5D, 0.5D)" not in source


def test_built_pack_maps_event_cmds_to_event_models_not_narcotics() -> None:
    result = subprocess.run(
        [sys.executable, str(BUILDER)],
        cwd=BUILDER.parent,
        check=True,
        capture_output=True,
        text=True,
    )
    assert "SHA1 " in result.stdout
    pack = ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"
    with zipfile.ZipFile(pack) as archive:
        paper = json.loads(archive.read("assets/minecraft/models/item/paper.json"))
        event_entries = {
            830001: "copimine:item/end_event_core",
            830002: "copimine:item/end_event_core_charged",
            830003: "copimine:item/end_event_pad",
        }
        overrides = paper["overrides"]
        for custom_model_data, model in event_entries.items():
            assert {"custom_model_data": custom_model_data} in [entry["predicate"] for entry in overrides]
            assert any(
                entry["predicate"] == {"custom_model_data": custom_model_data} and entry["model"] == model
                for entry in overrides
            )
        names = set(archive.namelist())
        assert "assets/copimine/textures/block/end_event_core.png" in names
        assert "assets/copimine/textures/block/end_event_core_charged.png" in names
        assert "assets/copimine/textures/block/end_event_rune.png" in names
        assert "assets/copimine/models/item/end_event_core.json" in names
        assert "assets/copimine/models/item/end_event_core_charged.json" in names
        assert "assets/copimine/models/item/end_event_pad.json" in names
