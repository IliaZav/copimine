"""Meta-contract preventing the strong End Rift gate from losing coverage."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "tests" / "RunEndRiftEventChecks.ps1"

REQUIRED_JAVA = (
    "RitualPrisonerCapturePolicyTest",
    "RitualTargetPolicyTest",
    "RitualZoneEffectPolicyTest",
    "RitualConversionTargetPolicyTest",
    "PrisonerAbilityControllerTest",
    "RitualCasterProgressionPolicyTest",
    "RitualSphereEncounterSnapshotTest",
    "RitualSphereProjectilePolicyTest",
    "BossOrientedHitboxPolicyTest",
    "BossProjectileSweepPolicyTest",
)
REQUIRED_PYTHON = (
    "test_end_rift_evidence_portability.py",
    "test_end_rift_diagnostic_report.py",
)


def test_required_strong_java_and_python_tests_exist_and_are_wired() -> None:
    gate = GATE.read_text(encoding="utf-8")

    for name in REQUIRED_JAVA:
        assert (ROOT / "tests" / f"{name}.java").is_file(), name
        assert f"'{name}'" in gate, name
    for name in REQUIRED_PYTHON:
        assert (ROOT / "tests" / name).is_file(), name
        assert name in gate, name


def test_required_live_boundaries_and_quality_evidence_are_present() -> None:
    wave6 = (ROOT / "tests" / "RunEndRiftWave6RitualLive.ps1").read_text(encoding="utf-8")
    boss = (ROOT / "tests" / "RunEndRiftBossHitboxLive.ps1").read_text(encoding="utf-8")
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    report = (ROOT / "tests" / "tools" / "build_end_rift_diagnostic_report.py").read_text(encoding="utf-8")
    evidence_dir = ROOT / "docs" / "superpowers" / "evidence"

    for needle in (
        "Wait-Players -Minimum 2",
        "WAVE6_RITUAL_SPHERE_READY",
        "LIVE_W6_ROSTER_PASS",
        "ritualGuardOwnershipValid",
        "LIVE_W6_SCENE_PASS",
        "visuals=",
    ):
        assert needle in wave6, needle
    for needle in (
        "LIVE_BOSS_HITBOX_PROXY_REMOVAL_CONFIRMED",
        "LIVE_BOSS_HITBOX_SELF_HEAL_PASS",
        "LIVE_BOSS_HITBOX_NO_DUPLICATE_PASS",
        "LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS",
        "[Regex]::Escape($missBotName)",
    ):
        assert needle in boss, needle
    for needle in (
        "W7-BARRIER-01",
        "W7-RESTART-01",
        "W7-POST-RESTART-CLIENTS-01",
        "W7-NATURAL-CLEANUP-01",
        "W7-COMMAND-CLEANUP-01",
        "end-rift-events.jsonl",
    ):
        assert needle in wave7, needle
    assert "Verification Matrix" in report
    assert evidence_dir.is_dir()
    assert any(evidence_dir.glob("end-rift-test-strength-*.md"))


def test_live_boundary_harness_sets_combat_hotbar_slots_independent_of_selected_slot() -> None:
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    configure = wave7[
        wave7.index("function Configure-Bot") : wave7.index("function Teleport-Player")
    ]

    assert "hotbar.0 with minecraft:netherite_sword" in configure
    assert "hotbar.1 with minecraft:bow" in configure
    assert "Inventory[{Slot:0b}]" in configure
    assert "Inventory[{Slot:1b}]" in configure
    assert "data get entity $name SelectedItem" not in configure


def test_wave7_damage_gate_matches_the_warden_and_reflection_trial_mix() -> None:
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    natural = wave7[
        wave7.index("$damageLedger = Get-PositivePlayerDamageLedger") : wave7.index(
            "Write-Output 'LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS"
        )
    ]

    assert "reflection-only chamber does not require Warden damage" in natural
    assert "if ($damageParts.Count -lt 1)" in natural
    assert "did not record positive player damage for $name" not in natural
