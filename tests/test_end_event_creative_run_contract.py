from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def test_creative_run_is_local_only_and_requires_creative_operator() -> None:
    assert "/cmend test run creative" in MAIN
    assert "environment" in MAIN and "local" in MAIN
    assert "GameMode.CREATIVE" in MAIN
    assert "copimine.endevent.test" in PLUGIN
    assert "officialRewardRoster" in MAIN
    assert "participantUuids" in MAIN


def test_creative_run_has_generation_guard_and_disposable_cleanup() -> None:
    for marker in (
        "CREATIVE_TEST_START", "CREATIVE_TEST_CORE", "CREATIVE_TEST_RESOURCES",
        "CREATIVE_TEST_RUNES", "CREATIVE_TEST_WAVE_1", "CREATIVE_TEST_INTERMISSION_1",
        "CREATIVE_TEST_WAVE_2", "CREATIVE_TEST_INTERMISSION_2", "CREATIVE_TEST_WAVE_3",
        "CREATIVE_TEST_BOSS_ACTIVE", "CREATIVE_TEST_HALF", "CREATIVE_TEST_CONTROL",
        "CREATIVE_TEST_FINAL_DRAIN", "CREATIVE_TEST_FINAL_WAVE",
        "CREATIVE_TEST_BOSS_FINISH", "CREATIVE_TEST_CLEANUP", "CREATIVE_TEST_COMPLETE",
        "creativeTestTask", "generation",
    ):
        assert marker in MAIN
    assert "clearWaveEntities()" in MAIN
    assert "clearBossOnly()" in MAIN
    assert "official_phase_unchanged=true" in MAIN


def test_creative_run_does_not_relax_the_official_pad_rule() -> None:
    assert "GameMode.CREATIVE" in MAIN
    assert "padOccupants" in MAIN
    assert "requiredPlayers" in MAIN
    assert "environment: local" in CONFIG


def test_creative_run_covers_spell_flight_markers() -> None:
    for marker in ("BOSS_SPELL_TELEGRAPH", "BOSS_SPELL_FLIGHT", "BOSS_SPELL_CAST", "MINIBOSS_SPELL_FLIGHT"):
        assert marker in MAIN


def test_creative_run_waits_for_miniboss_flight_before_clearing_wave_three() -> None:
    delay = MAIN[MAIN.index("private int creativeTestStageDelay"):
                 MAIN.index("private Player creativeTestPlayer")]
    assert "case 8 ->" in delay
    assert "spellTelegraphTicks()" in delay
    assert "SPELL_FLIGHT_TICKS" in delay


def test_creative_run_accepts_an_idle_local_event_when_worldcore_already_unlocked() -> None:
    start = MAIN[MAIN.index("private void startCreativeTest"):
                 MAIN.index("private boolean officialCombatStateActive")]
    tick = MAIN[MAIN.index("private void tickCreativeTest"):
                MAIN.index("private int creativeTestStageDelay")]

    # The local scene intentionally keeps the End open so the portal can be
    # inspected.  That permanent WorldCore fact must not block a disposable
    # test while the End Rift event itself is still idle and uncommitted.
    assert "phase != EventPhase.COLLECTING" in start
    assert "endUnlocked || phase == EventPhase.UNLOCKED" not in start
    assert "phase != EventPhase.COLLECTING" in tick
    assert "|| endUnlocked ||" not in tick


def test_creative_cleanup_clears_transient_ai_helpers_and_spell_state() -> None:
    cleanup = MAIN[MAIN.index("private void finishCreativeTest"):
                   MAIN.index("private void handleWave")]

    # A completed disposable run must not leave the operator in the helper
    # set or leave mini-boss/boss targeting state attached to the next run.
    assert "clearCombatAiState();" in cleanup
