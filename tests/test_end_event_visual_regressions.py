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
    assert "return core.getLocation().add(0.5D, 1.0D, 0.5D);" in source
    assert "private Location runeOverlayLocation(Block floor)" in source
    assert "return floor.getLocation().add(0.5D, 1.0D, 0.5D);" in source
    assert "core.getLocation().add(0.5D, 0.5D, 0.5D)" not in source
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
    # ItemDisplay item models are centered on the entity origin.  The rune
    # display is anchored at the top of the floor block, so the thin plate
    # must be centered around the origin rather than extending below it.
    assert model["elements"][0]["from"] == [0.0, 8.0, 0.0]
    assert model["elements"][0]["to"] == [16.0, 10.0, 16.0]
    assert occupied_model["elements"][0]["from"] == [0.0, 8.0, 0.0]
    assert occupied_model["elements"][0]["to"] == [16.0, 10.0, 16.0]
    assert occupied_model["textures"]["top"] == "copimine:block/end_event_rune_occupied"
    assert "MODEL_RUNE_OVERLAY_OCCUPIED = 830005" in source
    assert "runeVisualOccupants.containsKey(padKey(pad))" in source
    assert "refreshRuneOverlayVisuals" in source
    assert source.count("entity.setBrightness(new Display.Brightness(15, 15));") >= 2


def test_rune_visual_occupancy_is_separate_from_the_official_survival_roster() -> None:
    source = MAIN.read_text(encoding="utf-8")
    occupancy = source[source.index("private void updatePadOccupancy()"):source.index("private String padKey")]
    rune_item = source[source.index("private ItemStack runeOverlayItem"):source.index("private void refreshRuneOverlayVisuals")]
    assert "runeVisualOccupants" in source
    assert "detectRuneOccupants(world, false)" in occupancy
    assert "detectRuneOccupants(world, true)" in occupancy
    assert "runeVisualOccupants.containsKey(padKey(pad))" in rune_item
    assert "GameMode.SPECTATOR" in occupancy
    assert "GameMode.CREATIVE" in occupancy
    assert "padOccupants.size() == requiredPlayers" in occupancy


def test_rune_overlay_returns_to_idle_when_a_player_leaves_or_ritual_is_cancelled() -> None:
    source = MAIN.read_text(encoding="utf-8")
    for handler_name, next_handler in (
        ("public void onPlayerQuit", "public void onPlayerDeath"),
        ("public void onPlayerDeath", "public void onPlayerRespawn"),
        ("public void onPlayerChangedWorld", "public void onShardInteract"),
    ):
        handler = source[source.index(handler_name):source.index(next_handler)]
        assert "runeVisualOccupants.values().removeIf" in handler
        assert "refreshRuneOverlayVisuals" in handler
    cancel = source[source.index("private void cancelRitual"):source.index("private void updateCombatHelpers")]
    assert "runeVisualOccupants.clear();" in cancel
    assert "refreshRuneOverlayVisuals();" in cancel


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


def test_visual_repair_keeps_runes_visible_during_combat_phases() -> None:
    source = MAIN.read_text(encoding="utf-8")
    maintain = source[source.index("private void maintainRitualVisuals"):source.index("private String padKey")]
    assert "if (!coreCharged) {\n            return;\n        }" in maintain
    assert "phase != EventPhase.READY_FOR_PLAYERS && phase != EventPhase.COUNTDOWN" not in maintain
    assert "if (missing > 0 && System.currentTimeMillis() >= nextRitualVisualRepairMillis)" in maintain


def test_status_and_rebuild_paths_expose_and_repair_real_core_and_rune_overlays() -> None:
    source = MAIN.read_text(encoding="utf-8")
    assert "visualStatusText()" in source
    assert "coreOverlay=" in source
    assert "runes=" in source
    charge = source[source.index("private void updateCoreChargeState()"):source.index("private boolean allResourcesComplete()")]
    assert "rebuildPersistedVisuals();" in charge


def test_resourcepack_maps_idle_and_occupied_runes_without_vanilla_overrides() -> None:
    subprocess.run(
        [sys.executable, str(BUILDER), "--skip-server-properties"],
        cwd=BUILDER.parent,
        check=True,
        capture_output=True,
        text=True,
    )
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
        [sys.executable, str(BUILDER), "--skip-server-properties"],
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


def test_spell_projectiles_are_particle_only_and_each_spell_has_a_unique_pattern() -> None:
    source = MAIN.read_text(encoding="utf-8")
    builder = BUILDER.read_text(encoding="utf-8")
    assert "private void spawnSpellFlightPattern(" in source
    assert "private void spawnRiftProjectileTrail(" in source
    assert "projectile.setInvisible(true);" in source
    assert "projectile.setVisibleByDefault(false);" in source
    assert "MODEL_RIFT_PROJECTILE" not in source
    assert "riftProjectileVisuals" not in source
    assert "end_event_rift_projectile" not in source
    assert '"assets/copimine/models/item/end_event_rift_projectile.json"' not in builder
    assert '"assets/copimine/textures/item/end_event_rift_projectile.png"' not in builder
    for spell_id in (
        "void_blast", "rift_projectile", "void_mark", "summon", "will_distortion",
        "rift_step", "void_snare", "echo_pulse",
    ):
        assert f'case "{spell_id}"' in source

    model_path = ROOT / "resourcepacks/src/assets/copimine/models/item/end_event_rift_projectile.json"
    texture_path = ROOT / "resourcepacks/src/assets/copimine/textures/item/end_event_rift_projectile.png"
    assert not model_path.exists()
    assert not texture_path.exists()

    subprocess.run(
        [sys.executable, str(BUILDER), "--skip-server-properties"],
        cwd=BUILDER.parent,
        check=True,
        capture_output=True,
        text=True,
    )
    pack = ROOT / "resourcepacks/build/CopiMineResourcePack.zip"
    with zipfile.ZipFile(pack) as archive:
        assert "assets/copimine/models/item/end_event_rift_projectile.json" not in archive.namelist()
        assert "assets/copimine/textures/item/end_event_rift_projectile.png" not in archive.namelist()


def test_void_mark_pattern_initializes_all_glyph_corners_before_joining_edges() -> None:
    source = MAIN.read_text(encoding="utf-8")
    # The runtime diagnostics mapper also names this spell.  Anchor the
    # assertion to the final visual-pattern cases, not to whichever switch
    # happens to appear first in the source file.
    pattern = source[source.rindex('case "void_mark" -> {'):source.rindex('case "summon", "summon_servants"')]
    loop = "for (int i = 0; i < corners.length; i++)"
    assert pattern.count(loop) >= 2
    assert pattern.index("corners[i] =") < pattern.rindex("spawnPatternSegment")


def test_particle_segment_uses_required_data_for_colored_dust() -> None:
    source = MAIN.read_text(encoding="utf-8")
    helper = source[source.index("private void spawnPatternSegment"):source.index("private void spawnEventParticle")]
    assert "particle == Particle.DUST" in helper
    assert "Particle.DustOptions safeDust = dust != null" in helper
    assert "? dust : new Particle.DustOptions(Color.WHITE, 1.0F);" in helper
    assert "spawnParticle(Particle.DUST, linePoint, 1" in helper
    assert "player.spawnParticle(particle, linePoint, 1" in helper


def test_boss_cue_supplies_float_payload_for_sculk_charge_particle() -> None:
    source = MAIN.read_text(encoding="utf-8")
    helper = source[source.index("private void spawnBossCueParticle"):
                    source.index("private void spawnRiftProjectileTrail")]

    assert "particle == Particle.SCULK_CHARGE" in helper
    assert "Float.valueOf((float) extra)" in helper
    assert "spawnParticle(Particle.SCULK_CHARGE, point" in helper
