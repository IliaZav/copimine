from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT_SOURCE = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
                / "CopiMineEndEvent.java")


def read_event() -> str:
    return EVENT_SOURCE.read_text(encoding="utf-8")


def test_automatic_spell_cooldown_is_consumed_only_after_a_cast_is_accepted() -> None:
    source = read_event()
    tick_start = source.index("private void tickCurrentBoss")
    tick_end = source.index("/** Keep the final-phase boss", tick_start)
    tick_body = source[tick_start:tick_end]

    assert "boolean accepted = castBossSpell(boss, false);" in tick_body
    assert "if (accepted)" in tick_body
    assert "nextSpellMillis = now + 1_000L" in tick_body
    assert "BOSS_SPELL_RETRY" in tick_body


def test_final_strike_start_is_a_real_dispatch_result() -> None:
    source = read_event()
    assert "private boolean startBossFinalStrike" in source
    dispatch_start = source.index("private boolean castCurrentBossSpell(")
    dispatch_end = source.index("private Player currentBrainTarget", dispatch_start)
    dispatch_body = source[dispatch_start:dispatch_end]
    assert "return startBossFinalStrike(boss, target, forced);" in dispatch_body
    assert "return telegraphBossSpell(boss, target, spell, forced);" in dispatch_body
