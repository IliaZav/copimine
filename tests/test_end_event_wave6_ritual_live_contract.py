"""Static gate for the dedicated local Wave 6 live probe.

The probe itself is the runtime authority.  This contract only keeps the
required safety rails and evidence checkpoints from being removed while the
PowerShell harness evolves.
"""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tests" / "RunEndRiftWave6RitualLive.ps1"
EVENT_SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def read_script() -> str:
    assert SCRIPT.is_file(), f"dedicated Wave 6 live probe is missing: {SCRIPT}"
    return SCRIPT.read_text(encoding="utf-8")


def test_wave6_live_probe_is_local_only_and_uses_isolated_ports() -> None:
    script = read_script()

    for needle in (
        "environment: local",
        "environment: staging",
        "production",
        "server-port=25566",
        "rcon.port=25576",
        "codex/end-rift-event",
        "cmend test wave 6",
    ):
        assert needle in script
    assert "production" in script.lower()
    assert "Start-LocalMinecraft" in script
    assert "Invoke-LocalRcon" in script


def test_wave6_live_probe_proves_physical_seal_capture_order() -> None:
    script = read_script()

    for needle in (
        "FirstBotName",
        "SecondBotName",
        "ThirdBotName",
        "FourthBotName",
        "Start-Bot",
        "Wait-BotsOnline",
        "Wait-Until",
        "LIVE_WAVE6_CASTER_GUARDED_PASS",
        "WAVE6_RITUAL_PRISONER_CAPTURED",
        "WAVE6_RITUAL_PRISONER_CAPTURED",
        "-Name $SecondBotName",
        "Get-OfflinePlayerUuid",
        "Sort-Object Uuid",
        "WAVE6_RITUAL_SPHERE_READY",
        "state=WAITING_FOR_PRISONER",
        "END_RIFT_RINGS_READY",
        "WAVE_6_PAIR_SPAWNED",
    ):
        assert needle in script
    assert "no prisoner" in script.lower() or "not captured" in script.lower()
    assert "outside" in script.lower()


def test_wave6_live_probe_checks_drain_immunity_targeting_and_origin() -> None:
    script = read_script()

    for needle in (
        "data get entity",
        "19.5",
        "20",
        "health_floor=1",
        "WAVE6_RITUAL_PRISONER_DRAIN",
        "damage $SecondBotName",
        "minecraft:fall",
        "WAVE6_RITUAL_PROJECTILE_ORIGIN_ASSERT",
        "sphere_origin=",
        "projectile_spawn=",
        "origin_distance=",
        "WAVE6_RITUAL_ZONE_TELEGRAPH",
        "WITHER",
        "SLOWNESS",
        "POISON",
        "WAVE6_RITUAL_ABILITY",
        "target=",
        "WAVE6_RITUAL_COMPLETE",
        "event-mobs=0",
    ):
        assert needle in script


def test_wave6_live_probe_has_fail_closed_cleanup() -> None:
    script = read_script()
    finally_start = script.index("finally")
    cleanup = script[finally_start:]

    for needle in (
        "cmend wave clear",
        "cmend boss kill cleanup",
        "Stop-Bots",
        "Remove-Item Env:",
        "gamerule doMobSpawning",
    ):
        assert needle in cleanup


def test_wave6_live_probe_anchors_drain_cadence_to_the_server_capture_marker() -> None:
    script = read_script()
    assert "first_drain_at=" in script
    assert "UnixTimeMilliseconds" in script
    assert "Stop-Bots" in script
    assert "Wait-BotsOffline" in script
    restart = script[script.index("function Restart-LocalMinecraftForWave6") :]
    assert restart.index("Stop-Bots") < restart.index("save-all")
    assert "Wait-Log-MarkerIncrease -Pattern $appliedDrainPattern" in script
    marker_start = script.index("function Wait-Log-MarkerIncrease")
    marker_end = script.index("function Get-AppliedRitualDrainCount", marker_start)
    marker_body = script[marker_start:marker_end]
    assert "return $current.Substring([int]$BeforeLength)" in marker_body
    first_drain_start = script.index("$firstDrainMatches = [Regex]::Matches")
    first_drain_end = script.index("$healthAfterFirstDrain", first_drain_start)
    assert "$firstDrainMatch = $firstDrainMatches[0]" in script[first_drain_start:first_drain_end]
    assert "$firstDrainMatches[$firstDrainMatches.Count - 1]" not in script[first_drain_start:first_drain_end]
    assert "applied=true[^\\r\\n]*damage=" in script
    assert "damage=(?:1\\.9+|2(?:\\.0+)?)[^\\r\\n]*drain_at=(\\d+)" in script
    assert "$appliedDrainsAfterFirst = Get-AppliedRitualDrainCount" in script
    assert "$drainsBeforeSecondDeadline -ne $appliedDrainsAfterFirst" in script
    drain_window = script.index("LIVE_WAVE6_DRAIN_19_5S_PASS")
    external_between_drains = script.index(
        "Assert-ExternalDamageIgnored -ExpectedHealth $healthAfterFirstDrain"
    )
    assert script.index("LIVE_WAVE6_DRAIN_20S_PASS") < external_between_drains


def test_wave6_restart_does_not_consume_a_drain_while_the_prisoner_is_offline() -> None:
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    drain_start = source.index("private void applyRitualSphereDrain")
    drain_end = source.index("private void tickRitualGuardGroups", drain_start)
    drain_body = source[drain_start:drain_end]
    assert "prisoner == null || !prisoner.isOnline() || prisoner.isDead()" in drain_body
    assert drain_body.index("prisoner == null") < drain_body.index("advanceDrain")
    assert "drain_at=" in drain_body


def test_wave6_completion_captures_log_offset_before_request() -> None:
    script = read_script()
    request = script.index("cmend test ritual complete")
    offset = script.index("$completionOffset = Get-LogLength")
    assert offset < request


def test_wave6_dynamic_uuid_patterns_are_bound_before_wait_log() -> None:
    script = read_script()
    assert "$reversePrisonerPattern =" in script
    assert "-match $reversePrisonerPattern" in script
    assert "$swapFreePairPattern =" in script
    assert "-Pattern $swapFreePairPattern" in script


def test_wave6_live_probe_requires_exact_hook_ack_and_positive_free_targets() -> None:
    script = read_script()
    for needle in (
        "$projectileResponse",
        "forced\\s+PROJECTILE_CASTER",
        "role=PROJECTILE_CASTER[^\\r\\n]*source=LOCAL_TEST_HOOK",
        "$reverseFreePattern =",
        "-Pattern $reverseFreePattern",
        "$swapFreePairPattern =",
        "-Pattern $swapFreePairPattern",
        "LIVE_WAVE6_DRAIN_39_5S_PASS",
        "$drainsBeforeSecondDeadline",
        "WAVE6_RITUAL_COMPLETE[^\\r\\n]*cleanup=server[^\\r\\n]*sphere=false",
        "$cleanupLog",
        "projectiles=0",
        "LIVE_WAVE6_CASTER_EXPOSED_PASS",
        "LIVE_WAVE6_CASTER_AWAKENED_PASS",
        "LIVE_WAVE6_CLEANUP_ZERO_STATE_PASS",
    ):
        assert needle in script


def test_wave6_completion_marker_is_owned_by_the_server_cleanup_path() -> None:
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    for needle in (
        "clearActiveEventArrows();",
        "WAVE6_RITUAL_COMPLETE event=",
        'sphere=" + (ritualSphereVisualUuid != null)',
        'zones=" + ritualZoneCenters.size()',
        'controls=" + ritualControlInstances.size()',
        'beams=" + activeWorldVfxInstances.keySet().stream()',
        'projectiles=" + activeEventArrowAges.size()',
        "prisoner_tag=cleared",
    ):
        assert needle in source


def test_wave6_control_transitions_emit_runtime_evidence() -> None:
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    for needle in (
        "WAVE6_RITUAL_CONTROL",
        "mode=REVERSE",
        "mode=SWAP",
        "action=START",
        "action=STOP",
        "prisoner=",
    ):
        assert needle in source


def test_wave6_live_probe_uses_a_local_only_force_hook_for_each_core_ability() -> None:
    script = read_script()
    source = EVENT_SOURCE.read_text(encoding="utf-8")

    for needle in (
        "cmend test ritual force projectile",
        "cmend test ritual force zone",
        "cmend test ritual force reverse",
        "cmend test ritual force swap",
        "cmend test ritual controls clear",
        "cmend test ritual complete",
        "Get-UuidRegexPattern",
        "active_effects[$index]",
        "mode=REVERSE[^\\r\\n]*action=START[^\\r\\n]*player=",
        "secondDrainAtMillis",
    ):
        assert needle in script

    for needle in (
        "handleTestRitual",
        "LOCAL_TEST_HOOK",
        "ConsoleCommandSender",
        "RemoteConsoleCommandSender",
        "environment=local|staging",
        "forceRitualCoreAbilityForTest",
        "String action = args[2]",
    ):
        assert needle in source

    assert "production" in source.lower()
