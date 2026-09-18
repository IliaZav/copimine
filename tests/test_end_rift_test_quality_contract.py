"""Meta-contract preventing the strong End Rift gate from losing coverage."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "tests" / "RunEndRiftEventChecks.ps1"

REQUIRED_JAVA = (
    "RitualPrisonerCapturePolicyTest",
    "RitualPrisonerHealthPolicyTest",
    "RitualTargetPolicyTest",
    "RitualZoneEffectPolicyTest",
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
        "FirstBotName",
        "SecondBotName",
        "ThirdBotName",
        "FourthBotName",
        "WAVE6_RITUAL_PRISONER_CAPTURED",
        "WAVE6_RITUAL_REHYDRATED",
        "LIVE_WAVE6_RESTART_RECOVERY_PASS",
        "LIVE_WAVE6_DRAIN_20S_PASS",
        "LIVE_WAVE6_EXTERNAL_DAMAGE_IMMUNITY_PASS",
        "LIVE_WAVE6_FREE_TARGET_CONTROL_PASS",
    ):
        assert needle in wave6, needle
    for needle in (
        "LIVE_BOSS_HITBOX_PROXY_REMOVAL_CONFIRMED",
        "LIVE_BOSS_HITBOX_SELF_HEAL_PASS",
        "LIVE_BOSS_HITBOX_NO_DUPLICATE_PASS",
        "LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS",
    ):
        assert needle in boss, needle
    for needle in (
        "W7-BARRIER-01",
        "W7-RESTART-01",
        "W7-NATURAL-CLEANUP-01",
        "W7-COMMAND-CLEANUP-01",
        "end-rift-events.jsonl",
    ):
        assert needle in wave7, needle
    assert "Verification Matrix" in report
    assert evidence_dir.is_dir()
    assert any(evidence_dir.glob("end-rift-test-strength-*.md"))
