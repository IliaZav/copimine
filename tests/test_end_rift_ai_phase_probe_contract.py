"""Behavior-oriented contract for the exact-head AI/phase live probe."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "tests" / "RunEndRiftAiPhasesLive.ps1"


def read_probe() -> str:
    return PROBE.read_text(encoding="utf-8")


def test_probe_uses_structured_per_caster_state_and_proves_the_full_lifecycle() -> None:
    probe = read_probe()
    for needle in (
        "cmend debug ai --json",
        "ConvertFrom-Json",
        "LIVE_W6_CASTER_GUARDED_PASS",
        "LIVE_W6_CASTER_EXPOSED_PASS",
        "LIVE_W6_CASTER_AWAKENED_PASS",
        "ritualGuardOwnershipValid",
        "nativeAiEnabled",
        "AWAKENED_ATTACKING",
        "cmend test ritual caster guarded",
        "cmend test ritual caster exposed",
        "cmend test ritual caster awakened",
        "controlDirectory",
        "Set-ProbeBotMode",
        "Set-ProbeBotMode -Mode PASSIVE",
        "BOT_",
        "PASSIVE",
    ):
        assert needle in probe
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "forceRitualCasterStateForTest" in source
    assert "WAVE6_RITUAL_CASTER_TEST_STATE" in source


def test_probe_verifies_each_boss_phase_instead_of_printing_assumptions() -> None:
    probe = read_probe()
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    for phase in ("awakening", "hunt", "rift", "overload", "rage", "last_seal"):
        assert f"'{phase}'" in probe
    assert "cmend boss phase $phaseCommand" in probe
    assert "LIVE_CURRENT_BOSS_PHASE_PASS" in probe
    assert "AI_PROFILE" in probe
    assert "boss_phases=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL" not in probe
    assert "TEST_BOSS_STAGE_TRANSITION" in source
    assert "isTestBoss(boss)" in source


def test_probe_uses_condition_waits_and_fail_closed_cleanup() -> None:
    probe = read_probe()
    assert "function Wait-Until" in probe
    assert "cleanupFailures" in probe
    assert "LIVE_CURRENT_CLEANUP_PASS" in probe
    assert "catch { }" not in probe
    assert "Start-Sleep -Seconds 3" not in probe
