"""Regression contract for Wave 3 portal-origin enemy spawning.

This is intentionally a narrow source-adapter contract: this repository does
not expose a Bukkit/Paper fixture for invoking CopiMineEndEvent's objective
tick. The contract proves that production wires a bounded spawner to the
active portal and uses the normal event entity tagging path. It does not prove
Paper entity materialization, pathfinding, or player-visible visuals; those
still need an isolated server playtest.
"""
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
PRESENTATION_POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/PortalPresentationPolicy.java"


def _method(source: str, name: str) -> str:
    declaration = re.search(
        rf"\bprivate\s+(?:static\s+)?[\w.<>, ?\[\]]+\s+{re.escape(name)}\s*\([^;]*?\)\s*\{{",
        source,
        re.DOTALL,
    )
    assert declaration, f"missing required production method: {name}"
    opening = source.index("{", declaration.start())
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[declaration.start() : index + 1]
    raise AssertionError(f"unterminated production method: {name}")


def test_wave3_objective_tick_runs_the_portal_spawn_controller():
    source = EVENT.read_text(encoding="utf-8")
    tick = _method(source, "tickCurrentObjective")
    gate_branch = re.search(
        r"case\s+RIFT_GATES\s*->(?P<body>.*?)(?=case\s+OBELISK_ASSAULT)",
        tick,
        re.DOTALL,
    )

    assert gate_branch, "Wave 3's objective branch must remain explicit"
    assert "tickWave3PortalSpawns(now);" in gate_branch.group("body"), (
        "Wave 3 needs an active objective-tick path that actually emits portal mobs"
    )


def test_portal_spawns_are_bounded_and_only_use_the_active_incomplete_portal():
    source = EVENT.read_text(encoding="utf-8")
    scheduler = _method(source, "tickWave3PortalSpawns")
    active_picker = _method(source, "currentWave3ActivePortal")
    presentation = PRESENTATION_POLICY.read_text(encoding="utf-8")

    assert "wavePortals.getOrDefault(3, List.of())" in scheduler
    assert "portalCaptureStates.getOrDefault(3, List.of())" in scheduler
    assert re.search(r"currentWave3ActivePortal\s*\(\s*states\s*,\s*now\s*\)", scheduler)
    assert re.search(
        r"if\s*\(\s*active\s*<\s*0\s*\)\s*\{[^}]*\breturn\s*;",
        scheduler,
        flags=re.DOTALL,
    ), (
        "no active portal (including all-captured or still-collapsing states) must stop spawning"
    )
    assert "PortalPresentationPolicy.activeIndex(states)" in active_picker
    assert "collapseFinished(previousClose, now)" in active_picker
    assert "if (!states.get(index).completed()) return index;" in presentation
    assert "return -1;" in presentation
    assert re.search(r"Location\s+portal\s*=\s*portals\.get\(active\)", scheduler)

    assert "Wave3PortalSpawnPolicy.spawnCountWithinBudget(" in scheduler, (
        "portal pressure needs an explicit total-live and per-tick spawn budget"
    )
    assert "int liveWaveMobs = countLiveWaveEntitiesForWave(3);" in scheduler, (
        "portal admission must count every live Wave 3 mob, not only portal-origin mobs"
    )
    assert "PressureBudgetController.profileForPlayers(currentWaveScalePlayers).activePressure()" in scheduler, (
        "portal admission must use the current party's shared simultaneous-pressure cap"
    )
    assert re.search(
        r"spawnCountWithinBudget\s*\(\s*livePortalMobs\s*,\s*liveWaveMobs\s*,\s*pressureCap\s*,\s*1\s*\)",
        scheduler,
    ), "Wave 3 portals must obey both the portal roster and the global wave pressure cap"
    assert re.search(r"ordinal\s*<\s*spawnCount", scheduler), (
        "entity creation must be limited to the policy-approved per-tick quota"
    )
    assert re.search(
        r"spawnWaveThreePortalMob\s*\(\s*world\s*,\s*portal\s*,\s*active\s*,\s*ordinal\s*\)",
        scheduler,
    ), "new portal mobs must receive the active portal's location and identity"


def test_portal_mob_creation_uses_the_safe_portal_origin_and_event_ownership_tags():
    source = EVENT.read_text(encoding="utf-8")
    spawn = _method(source, "spawnWaveThreePortalMob")
    tag = _method(source, "tag")

    assert re.search(r"safePortalSpawnLocation\s*\(\s*portal\s*,", spawn), (
        "portal enemy positions must be safely derived from the selected portal, not arena core"
    )
    assert re.search(r"world\.spawnEntity\s*\(\s*spawnLocation\s*,\s*type\s*\)", spawn)
    assert re.search(r"tag\s*\(\s*entity\s*,\s*EVENT_KIND_WAVE_MOB\s*,\s*3\s*,", spawn), (
        "portal enemies must use the normal Wave 3 entity tag path"
    )
    assert "registerOwnedEntity(entity)" in spawn
    assert "keyWaveThreePortalSpawn" in spawn
    assert "data.set(keyEventId, PersistentDataType.STRING, eventId)" in tag
    assert "data.set(keyGeneration, PersistentDataType.LONG, generation)" in tag
    assert "registerOwnedEntity(entity)" in tag

    assert "living instanceof Enderman enderman" in spawn
    assert "WaveScalingPolicy.mobStrengthMultiplier(currentWaveScalePlayers)" in spawn
    assert "WaveDamagePolicy.minimumCombatDamage(" in spawn, (
        "portal Endermen must receive the same participant-scaled health and damage as the regular roster"
    )


def test_portals_emit_multiple_mobs_before_capture_can_finish():
    spawn_policy = (EVENT.parent / "domain/Wave3PortalSpawnPolicy.java").read_text(encoding="utf-8")
    capture_policy = (EVENT.parent / "domain/PortalCapturePolicy.java").read_text(encoding="utf-8")
    spawn_interval = re.search(r"SPAWN_INTERVAL_MILLIS\s*=\s*([\d_]+)L", spawn_policy)
    capture_duration = re.search(r"CAPTURE_MILLIS\s*=\s*([\d_]+)L", capture_policy)
    assert spawn_interval and capture_duration
    interval_ms = int(spawn_interval.group(1).replace("_", ""))
    capture_ms = int(capture_duration.group(1).replace("_", ""))
    assert interval_ms * 3 < capture_ms, (
        "the active portal must have multiple spawn opportunities before its five-second capture completes"
    )
    source = EVENT.read_text(encoding="utf-8")
    assert "WAVE3_PORTAL_MOB_SPAWNED event=" in source


def test_wave3_live_visual_runner_waits_for_a_portal_mob_spawn():
    runner = (ROOT / "tests/RunEndRiftVisualFivePlayerLive.ps1").read_text(encoding="utf-8")
    assert re.search(
        r"Wait-Log\s+-AfterOffset\s+\$portalOffset\s+-Pattern\s+'WAVE3_PORTAL_MOB_SPAWNED.*portal=0.*'",
        runner,
    ), "the Paper live probe must require an actual owned mob emitted from Wave 3's active portal"
