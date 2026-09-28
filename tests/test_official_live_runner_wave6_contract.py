"""Regression contracts for the official multi-player live runner."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1"
BOUNDARY_RUNNER = ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1"


def test_official_runner_derives_wave6_ritual_profile_from_participant_count() -> None:
    """The live runner must follow RitualSphereScalingPolicy for 2-20 players."""

    source = RUNNER.read_text(encoding="utf-8")

    assert "function Get-RitualScalingProfile" in source
    assert "$ritualProfile = Get-RitualScalingProfile -Participants $playerNames.Count" in source
    assert "projectiles=$($ritualProfile.Projectiles)" in source
    assert "zones=$($ritualProfile.Zones)" in source
    assert "Casters = 5" in source
    assert "Guards = $guards" in source
    assert "Projectiles = $projectiles" in source
    assert "Zones = $zones" in source
    assert "MajorCooldownSeconds = $cooldown" in source
    assert "ControlPairs" not in source


def test_official_runner_drives_one_participant_through_the_ritual_seal() -> None:
    """The full flow must exercise the physical prisoner-capture boundary."""

    source = RUNNER.read_text(encoding="utf-8")

    assert "$ritualPrisonerName = $playerNames[0]" in source
    assert "Teleport-Player -Name $ritualPrisonerName -X ($core[0] + 0.5D)" in source
    assert "WAVE6_RITUAL_PRISONER_CAPTURED" in source
    assert "WAVE6_RITUAL_PRISONER_DRAIN" not in source


def test_four_player_wave6_wave7_boundary_runner_uses_current_control_profile() -> None:
    """The fixed four-player boundary probe must not expect the old ring profile."""

    source = BOUNDARY_RUNNER.read_text(encoding="utf-8")

    assert "WAVE6_RITUAL_SPHERE_READY" in source
    assert "casters=5.*guards=5.*projectiles=3.*zones=1.*authority=server" in source
    assert "control_pairs" not in source
    assert "drain_interval_ms" not in source
