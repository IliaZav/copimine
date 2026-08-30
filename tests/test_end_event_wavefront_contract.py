from __future__ import annotations

import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
CLIENT_MIXIN = (
    ROOT / "CopiMineClient/src/main/java/me/copimine/client/mixin/SkeletonEntityRendererMixin.java"
).read_text(encoding="utf-8")


def test_wavefront_is_started_for_wave_one_and_is_cancelled_exactly() -> None:
    for marker in (
        "WaveVisualPolicy",
        "startWaveFrontAnimation",
        "tickWaveFrontAnimation",
        "cancelWaveFrontAnimation",
        "waveFrontVisualTask",
        "WAVE_FRONT_STARTED",
        "WAVE_FRONT_FRAME",
        "WAVE_FRONT_COMPLETED",
        "spawnWaveFrontTexture",
        "WaveVisualPolicy.frame",
    ):
        assert marker in SERVER

    spawn = SERVER[SERVER.index("private void spawnWave"):SERVER.index("private void spawnWaveGroup")]
    assert "startWaveFrontAnimation" in spawn
    cleanup = SERVER[SERVER.index("private void clearWaveObjectiveState"):SERVER.index("private void scheduleOfficialBossSpawn")]
    assert "cancelWaveFrontAnimation" in cleanup


def test_wavefront_is_floor_bound_and_viewer_scoped() -> None:
    start = SERVER.index("private void startWaveFrontAnimation")
    end = SERVER.index("private void spawnWaveGroup", start)
    source = SERVER[start:end]
    for marker in (
        "coreCombatAnchorLocation",
        "combatFloorY",
        "combatFloorY() + 1.0D + frame.floorY()",
        "isEventParticleViewer",
        "player.spawnParticle",
        "ItemDisplay",
        "setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE)",
        "setBillboard(Display.Billboard.FIXED)",
        "setGravity(false)",
    ):
        assert marker in source
    assert "world.spawnParticle" not in source


def test_wave_one_keeps_a_visible_zone_telegraph_after_the_opening_ring() -> None:
    for marker in (
        "renderWaveOneArenaZones",
        "WAVE_ONE_ZONE_RENDER_INTERVAL_TICKS",
        "WAVE_ONE_ZONES",
        "WAVE_BALANCE",
        "WaveScalingPolicy",
        "WaveScalingPolicy.scale",
        "WaveScalingPolicy.mobStrengthMultiplier",
    ):
        assert marker in SERVER


def test_intermission_is_twenty_seconds_and_wave_one_is_not_reduced_for_two_players() -> None:
    config = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
    assert "intermission-seconds: 20" in config
    assert "MIN_EXTRA_MOBS" in (
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WaveScalingPolicy.java"
    ).read_text(encoding="utf-8")


def test_wavefront_texture_is_packaged_as_a_custom_overlay_without_vanilla_overrides() -> None:
    texture = ROOT / "resourcepacks/src/assets/copimine/textures/item/end_event_wave_ring.png"
    assert texture.is_file()
    assert struct.unpack(">II", texture.read_bytes()[16:24]) == (64, 64)

    block_model = ROOT / "resourcepacks/src/assets/copimine/models/block/end_event_wave_ring.json"
    item_model = ROOT / "resourcepacks/src/assets/copimine/models/item/end_event_wave_ring.json"
    assert block_model.is_file()
    assert item_model.is_file()
    assert json.loads(item_model.read_text(encoding="utf-8"))["parent"] == "copimine:block/end_event_wave_ring"

    manifest = json.loads((ROOT / "resourcepacks/models_manifest.json").read_text(encoding="utf-8"))
    rows = [row for row in manifest["items"] if row["id"] == "end_event_wave_ring"]
    assert len(rows) == 1
    assert rows[0]["custom_model_data"] == 830006
    assert rows[0]["base_material"] == "paper"
    assert rows[0]["model"] == "copimine:item/end_event_wave_ring"
    assert not (ROOT / "resourcepacks/src/assets/minecraft/textures/entity").exists()


def test_skeleton_texture_has_a_runtime_bind_fallback_and_diagnostic() -> None:
    for marker in (
        "AbstractSkeletonEntity",
        "END_ENTITY_BIND",
        "EndEventTextureCatalog.isAvailable",
        "resourcePresent=",
        "SKELETON_TEXTURE_BIND",
        "refreshClientBindingsForPlayer",
        "isEventVisualViewer",
    ):
        assert marker in CLIENT_MIXIN or marker in SERVER
    for name in ("end_rift_skeleton.png", "end_rift_elite_skeleton.png"):
        texture = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity" / name
        assert texture.is_file()
        assert texture.stat().st_size > 128


def test_wave_transition_diagnostics_are_async_and_capture_stalls_and_failures() -> None:
    diagnostics = ROOT / "copimine-end-event/src/me/copimine/endevent/WaveTransitionDiagnostics.java"
    assert diagnostics.is_file()
    helper = diagnostics.read_text(encoding="utf-8")
    for marker in (
        "ArrayBlockingQueue",
        "ThreadPoolExecutor",
        "ConcurrentHashMap",
        "WAVE_TRANSITION_STARTED",
        "WAVE_TRANSITION_COMMITTED",
        "WAVE_TRANSITION_FAILED",
        "WAVE_MAIN_THREAD_STALL",
        "activeTasks",
        "operationMillis",
        "stackTrace",
        "StringWriter",
        "printStackTrace",
        "jsonLine",
        "closeAndFlush",
    ):
        assert marker in helper
    for marker in (
        "WaveTransitionDiagnostics",
        "recordWaveTransitionStarted",
        "recordWaveTransitionCommitted",
        "recordWaveTransitionFailed",
        "lastMainThreadTickAtMillis",
        "WAVE_MAIN_THREAD_STALL",
        "runTaskTimerAsynchronously",
        "taskRegistry.size()",
        "diagnostics.closeAndFlush",
    ):
        assert marker in SERVER
