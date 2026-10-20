"""Safety and evidence contract for the local Wave 6 scene probe."""

from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import pytest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tests" / "RunEndRiftWave6RitualLive.ps1"
PLUGIN_CONFIG = ROOT / "copimine-end-event" / "config.yml"
SERVER_PROPERTIES = ROOT / "local-runtime" / "end-rift-server" / "server.properties"
RITUAL_SCALING = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "RitualSphereScalingPolicy.java"
WAVE_MECHANICS = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "WaveMechanicsPolicy.java"
PRESSURE_BUDGET = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "PressureBudgetController.java"
ELITE_ABILITIES = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "EliteAbilityPolicy.java"
RITUAL_SCALING_TEST = ROOT / "tests" / "RitualSphereScalingPolicyTest.java"


def read_script() -> str:
    assert SCRIPT.is_file(), f"dedicated Wave 6 local probe is missing: {SCRIPT}"
    return SCRIPT.read_text(encoding="utf-8")


def test_wave6_small_party_elite_roles_stay_inside_the_pressure_roster(tmp_path) -> None:
    compiled = subprocess.run(
        ["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(PRESSURE_BUDGET), str(WAVE_MECHANICS), str(ELITE_ABILITIES),
         str(RITUAL_SCALING), str(RITUAL_SCALING_TEST)],
        capture_output=True, text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr

    result = subprocess.run(
        ["java", "-cp", str(tmp_path), "RitualSphereScalingPolicyTest"],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave6_probe_targets_only_the_isolated_local_server() -> None:
    script = read_script()
    config = PLUGIN_CONFIG.read_text(encoding="utf-8")
    launcher = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

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
    assert "Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $serverPropertiesLines" in launcher
    assert "-ServerPort 25566 -RconPort 25576" in launcher
    # A configured workstation also audits the actual local file. A clean CI
    # checkout proves the launcher guard below without starting a server or
    # requiring private runtime configuration/passwords in source control.
    if SERVER_PROPERTIES.is_file():
        properties = SERVER_PROPERTIES.read_text(encoding="utf-8")
        assert "server-port=25566" in properties
        assert "rcon.port=25576" in properties


def test_wave6_launcher_rejects_missing_or_wrong_ports_without_starting_a_server() -> None:
    launcher = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")
    assert "Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $serverPropertiesLines" in launcher
    assert "-ServerPort 25566 -RconPort 25576" in launcher
    assert "Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576) | Out-Null" in launcher or "$null = Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576)" in launcher
    assert "function Assert-EndRiftLocalConfiguredPorts" in helper
    assert "server-port" in helper and "rcon.port" in helper
    assert "single configured value" in helper


def test_shared_local_port_guards_reject_malformed_and_busy_ports() -> None:
    powershell = shutil.which("powershell") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the shared local port guards")
    helper_path = str(ROOT / "tests" / "EndRiftLocalAuthMode.ps1").replace("'", "''")
    command = f"""
. '{helper_path}'
$valid = @('server-port=25566', 'rcon.port=25576')
if (-not (Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $valid -ServerPort 25566 -RconPort 25576)) {{ throw 'Valid isolated ports were rejected.' }}
$badCases = @(
  @{{ Lines = @('rcon.port=25576'); ServerPort = 25566; RconPort = 25576 }},
  @{{ Lines = @('server-port=25565', 'rcon.port=25576'); ServerPort = 25566; RconPort = 25576 }},
  @{{ Lines = @('server-port=25566', 'server-port=25566', 'rcon.port=25576'); ServerPort = 25566; RconPort = 25576 }},
  @{{ Lines = @('server-port=25566', 'rcon.port=25566'); ServerPort = 25566; RconPort = 25566 }}
)
foreach ($case in $badCases) {{
  $rejected = $false
  try {{ Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $case.Lines -ServerPort $case.ServerPort -RconPort $case.RconPort | Out-Null }}
  catch {{ $rejected = $true }}
  if (-not $rejected) {{ throw "Malformed local port case was accepted: $($case | ConvertTo-Json -Compress)" }}
}}
$script:simulateListener = $false
function Get-NetTCPConnection {{
  [CmdletBinding()]
  param([int]$LocalPort, [string]$State)
  if ($script:simulateListener) {{ return ,([pscustomobject]@{{ LocalPort = $LocalPort; State = $State }}) }}
  return @()
}}
if (-not (Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576))) {{ throw 'Free local ports were rejected.' }}
$script:simulateListener = $true
$busyRejected = $false
try {{ Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576) | Out-Null }}
catch {{ $busyRejected = $true }}
if (-not $busyRejected) {{ throw 'A listening local port was accepted.' }}
Write-Output 'LOCAL_PORT_GUARDS_PASS'
"""
    result = subprocess.run(
        [powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command],
        check=True,
        capture_output=True,
        text=True,
    )
    assert "LOCAL_PORT_GUARDS_PASS" in result.stdout


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
