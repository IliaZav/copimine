"""Safety and evidence contract for the local Wave 6 scene probe."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tests" / "RunEndRiftWave6RitualLive.ps1"
PLUGIN_CONFIG = ROOT / "copimine-end-event" / "config.yml"
SERVER_PROPERTIES = ROOT / "local-runtime" / "end-rift-server" / "server.properties"


def read_script() -> str:
    assert SCRIPT.is_file(), f"dedicated Wave 6 local probe is missing: {SCRIPT}"
    return SCRIPT.read_text(encoding="utf-8")


def test_wave6_probe_targets_only_the_isolated_local_server() -> None:
    script = read_script()
    config = PLUGIN_CONFIG.read_text(encoding="utf-8")
    properties = SERVER_PROPERTIES.read_text(encoding="utf-8")

    for needle in (
        "codex/end-rift-event",
        r"environment:\s*local",
        "StartEndRiftLocal.ps1",
        "InvokeEndRiftLocalRcon.ps1",
        "127.0.0.1:25566",
        "127.0.0.1:25576",
        "cmend test wave 6",
    ):
        assert needle in script

    assert "staging" not in script.lower()
    assert "production" not in script.lower()
    assert "environment: local" in config
    assert "server-port=25566" in properties
    assert "rcon.port=25576" in properties


def test_wave6_probe_fails_closed_around_server_and_encounter_state() -> None:
    script = read_script()

    for needle in (
        "Test-LocalPort 25566",
        "Test-LocalPort 25576",
        "sourceHash",
        "activeHash",
        "Refused to interrupt an active server",
        "state=READY_FOR_PLAYERS",
        "Refused to alter an active End Rift event",
    ):
        assert needle in script

    assert "Invoke-LocalRcon 'stop'" not in script
    assert "cmend wave clear" not in script
    assert "cmend boss kill cleanup" not in script
    assert "Start-Bot" not in script
    assert "END_RIFT_BOT_PASSWORD" not in script


def test_wave6_probe_requires_real_players_and_checks_authoritative_scene() -> None:
    script = read_script()

    for needle in (
        "Wait-Players -Minimum 2",
        "Connect at least $Minimum local players",
        "WAVE6_RITUAL_SPHERE_READY",
        "state=WAITING_FOR_PRISONER",
        "casters=5",
        "authority=server",
        "cmend debug ai --json",
        "ritualCasters",
        "ritualGuardOwnershipValid",
        "LIVE_W6_ROSTER_PASS",
        "cmend debug objectives",
        "visuals=",
        "LIVE_W6_SCENE_PASS",
        "Connect or remain connected at 127.0.0.1:25566",
    ):
        assert needle in script


def test_wave6_probe_does_not_assert_removed_drain_generation() -> None:
    script = read_script().lower()

    for obsolete in (
        "first_drain_at",
        "ritual_prisoner_drain",
        "health_floor",
        "control_pairs",
        "amplifier_count",
        "successful-drains",
    ):
        assert obsolete not in script
