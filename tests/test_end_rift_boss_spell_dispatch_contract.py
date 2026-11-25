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
    # A refusal backs off on its own clock. It must NOT write nextSpellMillis,
    # because that clock is the "major cast is imminent" signal that
    # BossCloseCombatPolicy.canStrike uses to hold melee: writing it on a
    # refused spell silenced every attack for the retry window.
    assert "bossSpellRetryAtMillis = now + 1_000L" in tick_body
    assert "BOSS_SPELL_RETRY" in tick_body
    accepted_branch = tick_body[tick_body.index("if (accepted) {"):]
    accepted_branch = accepted_branch[:accepted_branch.index("} else {")]
    assert "nextSpellMillis = now + (bossPhase == BossPhase.LAST_SEAL" in accepted_branch, (
        "only an accepted cast may consume the major-spell cooldown")
    # The accepted cadence has two sanctioned sources: the Last Seal
    # destroyed-fraction escalation while the cocoon is intact, and the generic
    # party-scaled delay everywhere else.
    assert "lastSealSpellIntervalMillis(boss)" in accepted_branch, (
        "an intact Last Seal cocoon must use the escalation interval")
    assert "scaledBossDelayMillis(" in accepted_branch, (
        "the generic cadence must remain reachable for other phases")
    assert "bossSpellRetryAtMillis = 0L" in accepted_branch, (
        "an accepted cast must clear the refusal back-off")
    refusal_branch = tick_body[tick_body.index("} else {", tick_body.index("if (accepted) {")):]
    refusal_branch = refusal_branch[:refusal_branch.index("BOSS_SPELL_RETRY")]
    assert "nextSpellMillis =" not in refusal_branch, (
        "a refused spell must not consume the major-spell cooldown or gate melee")
    assert "bossSpellRetryAtMillis <= now" in tick_body, (
        "the refusal back-off must be consulted before retrying a spell")


def test_final_strike_start_is_a_real_dispatch_result() -> None:
    source = read_event()
    assert "private boolean startBossFinalStrike" in source
    dispatch_start = source.index("private boolean castCurrentBossSpell(")
    dispatch_end = source.index("private Player currentBrainTarget", dispatch_start)
    dispatch_body = source[dispatch_start:dispatch_end]
    assert "return startBossFinalStrike(boss, target, forced);" in dispatch_body
    assert "return telegraphBossSpell(boss, target, spell, forced);" in dispatch_body
