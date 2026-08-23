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
        "assets/copimine/models/item/end_event_pad_occupied.json",
        "assets/copimine/textures/item/end_event_core.png",
        "assets/copimine/textures/item/end_event_core_charged.png",
        "assets/copimine/textures/item/end_event_pad.png",
        "assets/copimine/textures/item/end_event_pad_occupied.png",
    )
    for relative in required:
        assert f'"{relative}"' in builder, relative


def test_core_and_rune_displays_are_anchored_to_the_surface() -> None:
    source = MAIN.read_text(encoding="utf-8")
    assert "private Location coreOverlayLocation(Block core)" in source
    assert "return core.getLocation().add(0.5D, 0.5D, 0.5D);" in source
    assert "private Location runeOverlayLocation(Block floor)" in source
    assert "return floor.getLocation().add(0.5D, 1.0D, 0.5D);" in source
    assert "floor.getLocation().add(0.5D, 1.5D, 0.5D)" not in source
    assert "new Vector3f(1.10F, 1.10F, 1.10F)" in source
    assert "entity.setDisplayWidth(1.10F);" in source
    assert "entity.setDisplayHeight(1.10F);" in source
    assert "entity.setBrightness(new Display.Brightness(15, 15));" in source
    assert "ItemDisplay.ItemDisplayTransform.NONE" in source
    assert "ItemDisplay.ItemDisplayTransform.FIXED" not in source
    assert "world.getChunkAt(chunkX, chunkZ, true)" in source
    assert "addPluginChunkTicket(this)" in source
    assert "releaseOverlayChunkTickets()" in source


def test_rune_model_covers_the_block_top_and_has_a_distinct_occupied_variant() -> None:
    model = json.loads((ROOT / "resourcepacks/src/assets/copimine/models/block/end_event_rune.json").read_text(encoding="utf-8"))
    occupied_model = json.loads((ROOT / "resourcepacks/src/assets/copimine/models/block/end_event_rune_occupied.json").read_text(encoding="utf-8"))
    source = MAIN.read_text(encoding="utf-8")
    assert model["elements"][0]["from"] == [0.0, 0.01, 0.0]
    assert model["elements"][0]["to"] == [16.0, 2.0, 16.0]
    assert occupied_model["elements"][0]["from"] == [0.0, 0.01, 0.0]
    assert occupied_model["elements"][0]["to"] == [16.0, 2.0, 16.0]
    assert occupied_model["textures"]["top"] == "copimine:block/end_event_rune_occupied"
    assert "MODEL_RUNE_OVERLAY_OCCUPIED = 830005" in source
    assert "padOccupants.containsKey(padKey(pad))" in source
    assert "refreshRuneOverlayVisuals" in source
    assert source.count("entity.setBrightness(new Display.Brightness(15, 15));") >= 2


def test_visual_repair_uses_loaded_world_entities_after_chunk_unload() -> None:
    source = MAIN.read_text(encoding="utf-8")
    maintain = source[source.index("private void maintainRitualVisuals"):]
    refresh = source[source.index("private void refreshRuneOverlayVisuals"):]
    assert "private boolean hasCoreOverlay(World world, Block core)" in source
    assert "private ItemDisplay findRuneOverlay(World world, Block floor)" in source
    assert "world.getEntities()" in maintain
    assert "hasCoreOverlay(world, core)" in maintain
    assert "findRuneOverlay(world, floor)" in maintain
    assert "findRuneOverlay(world, floor)" in refresh
    assert "phase == EventPhase.UNLOCKED" in maintain


def test_status_and_rebuild_paths_expose_and_repair_real_core_and_rune_overlays() -> None:
    source = MAIN.read_text(encoding="utf-8")
    assert "visualStatusText()" in source
    assert "coreOverlay=" in source
    assert "runes=" in source
    charge = source[source.index("private void updateCoreChargeState()"):source.index("private boolean allResourcesComplete()")]
    assert "rebuildPersistedVisuals();" in charge


def test_resourcepack_maps_idle_and_occupied_runes_without_vanilla_overrides() -> None:
    subprocess.run([sys.executable, str(BUILDER)], cwd=BUILDER.parent, check=True, capture_output=True, text=True)
    pack = ROOT / "resourcepacks/build/CopiMineResourcePack.zip"
    with zipfile.ZipFile(pack) as archive:
        paper = json.loads(archive.read("assets/minecraft/models/item/paper.json"))
        overrides = paper["overrides"]
        assert any(
            entry["predicate"] == {"custom_model_data": 830003}
            and entry["model"] == "copimine:item/end_event_pad"
            for entry in overrides
        )
        assert any(
            entry["predicate"] == {"custom_model_data": 830005}
            and entry["model"] == "copimine:item/end_event_pad_occupied"
            for entry in overrides
        )
        names = set(archive.namelist())
        assert "assets/copimine/models/block/end_event_rune_occupied.json" in names
        assert "assets/copimine/models/item/end_event_pad_occupied.json" in names
        assert "assets/copimine/textures/block/end_event_rune_occupied.png" in names
        assert "assets/copimine/textures/item/end_event_pad_occupied.png" in names
        assert not any(name.startswith("assets/minecraft/textures/block/") for name in names)


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
