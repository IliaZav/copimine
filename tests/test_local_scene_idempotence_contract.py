"""Contracts for the one-shot local End Rift scene rebuild.

The local scene is allowed to reset only its isolated event state.  It must
also be able to rebind the Core and Gate after a previous local run placed
them elsewhere; otherwise a stale durable layout makes the runner fail before
the gameplay checks begin.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
SCENE = (ROOT / "tests/SetupEndRiftLocalScene.ps1").read_text(encoding="utf-8")


def test_local_scene_rebinds_core_and_gate_after_clearing_stale_event_state() -> None:
    assert "core setat" in MAIN
    assert "gate setat" in MAIN
    assert "cmend core remove confirm" in SCENE
    assert 'cmend core setat $coreX $coreY $coreZ 2' in SCENE
    assert 'cmend gate setat $gateX $gateMinY $gateMinZ $gateX $gateMaxY $gateMaxZ' in SCENE
    assert "$coreX = 8; $coreY = 68; $coreZ = -39" in SCENE
    assert "$gateX = 29; $gateMinY = 68; $gateMaxY = 71; $gateMinZ = -40; $gateMaxZ = -38" in SCENE


def test_local_scene_force_loads_only_its_bounded_footprint_before_building() -> None:
    force_load = 'forceload add $arenaMinX $arenaMinZ $roomMaxX $spawnStationZ'
    assert force_load in SCENE
    assert "forceLoadResponse -notmatch" in SCENE
    assert "No chunks were marked for force loading" in SCENE
    assert SCENE.index(force_load) < SCENE.index('"execute in $world run fill $arenaMinX')


def test_local_scene_does_not_treat_shard_destination_as_local_room_metadata() -> None:
    smoke = (ROOT / "tests/RunEndRiftLocalSceneSmoke.ps1").read_text(encoding="utf-8")
    for text in (SCENE, smoke):
        assert "cmend portalroom info" not in text
        assert "cmend portalroom set" not in text
        assert "portalroom=.*CopiMine 31\\.5,68\\.0,-42\\.5" not in text


def test_live_scene_helpers_accept_minecraft_26_3_time_query_wording() -> None:
    time_query_pattern = "The (?:game )?time is"
    scripts = (
        SCENE,
        (ROOT / "tests/RunEndRiftLocalSceneSmoke.ps1").read_text(encoding="utf-8"),
        (ROOT / "tests/RunEndRiftCoreRemovalGuiLive.ps1").read_text(encoding="utf-8"),
    )
    assert all(time_query_pattern in script for script in scripts)


def test_coordinate_rebinding_is_explicitly_local_and_console_only() -> None:
    assert '"local".equalsIgnoreCase(config.environment())' in MAIN
    assert "ConsoleCommandSender" in MAIN
    assert "setat" in MAIN
