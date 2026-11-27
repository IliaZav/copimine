"""Master contract 14: Phase 4 OVERLOAD must be a real, reversible inferno.

InfernoArenaLifecyclePolicy answers the contract correctly; this file guards the
call sites, which is where the contract can silently fail - the phase runs, the
policy is ignored, and the arena keeps its magma after the fight.

The checks are deliberately about reachability as much as behaviour: a restore
that exists but is unreachable from some exit path is the same as no restore.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def body(name, text=None):
    """Brace-balanced body of a method, starting at its opening brace."""
    text = text if text is not None else source()
    # Callers may pass a bare name or a full signature fragment.
    start = text.index(name if "(" in name else name + "(")
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace: index + 1]
    raise AssertionError("unbalanced " + name)


def test_phase_four_drives_the_contract_policy():
    ingest = body("private void arenaInferno(")
    assert "InfernoArenaLifecyclePolicy.mayRestart(" in ingest, (
        "the inferno must honour the contract restart cooldown"
    )
    # V7-19: safe/live cells may be selected by InfernoArenaLifecyclePolicy or
    # by InfernoDecayPolicy (which owns the HP-dependent decay schedule).
    has_surface_policy = ("InfernoArenaLifecyclePolicy.safeCells(" in ingest
                         or "InfernoDecayPolicy.selectCovering(" in ingest)
    assert has_surface_policy, (
        "cooling surfaces must come from a policy, not be invented per cast"
    )
    assert "InfernoArenaLifecyclePolicy.durationTicks(" in ingest


def test_blocks_are_journaled_before_the_arena_burns():
    """Save-before-mutate: nothing changes until the originals are durable."""
    ingest = body("private void arenaInferno(LivingEntity boss, Player target)")
    assert "hazardJournalIsIdle(" in ingest, (
        "the inferno shares the hazard journal and must not force a second entry"
    )
    assert "hazardJournal.prepare(" in ingest, (
        "every original block must be written before the first setBlockData"
    )
    prepare = ingest.index("hazardJournal.prepare(")
    apply = ingest.index("hazardJournal.markApplied()")
    assert prepare < apply


def test_restore_puts_the_original_block_data_back():
    restore = body("private boolean restoreArenaInfernoBlocks(")
    # The capture stores the exact BlockData string, so the write-back must go
    # through createBlockData(original) (or the restoreBlock helper). Writing
    # only a Material would drop orientation/waterlogged/age state.
    assert "restoreBlock(" in restore or "Bukkit.createBlockData(original)" in restore, (
        "restoration must write the captured BlockData, not just the material"
    )
    assert "isArenaInfernoSurface(" in restore, (
        "only blocks this cast wrote may be restored"
    )


def test_a_failed_journal_restore_is_retried_not_forgotten():
    """Contract 14: no permanent magma, even when the journal write fails."""
    restore = body("private boolean restoreArenaInfernoBlocks()")
    # The guard also refuses while any cell is still pending, so the exact
    # literal is the whole condition; what matters is that the journal is
    # retired and its failure is treated as a restore failure.
    assert "!hazardJournal.markRestored()" in restore, (
        "restoration must retire the hazard journal entry"
    )
    failure = restore[restore.index("!hazardJournal.markRestored()"):]
    assert "return false" in failure.split("}")[0] + "}", (
        "a failed journal restore must report failure so a later tick retries"
    )
    # The originals must survive a failed journal write, or nothing is left to retry.
    assert failure.index("arenaInfernoOriginalBlocks.clear()") > failure.index("return false")


def test_every_exit_path_cleans_the_arena_up():
    text = source()
    assert text.count("clearArenaInferno();") >= 8, (
        "phase change, wave end, plugin disable and abandonment must all clear it"
    )


def test_leaving_phase_four_always_clears_the_inferno():
    text = source()
    # White-space-tolerant check: normalise and look for the guard.
    normalised = text.replace(" ", "").replace("\n", "").replace("\r", "")
    assert "if(bossPhase!=BossPhase.OVERLOAD){clearArenaInferno();" in normalised, (
        "a phase change out of OVERLOAD must never leave the arena burning"
    )


def test_one_cast_cannot_damage_a_player_twice_in_one_interval():
    text = source()
    assert "arenaInfernoLastDamageTick" in text, (
        "damage must be rate-limited per player, not applied every tick"
    )
    assert "InfernoArenaLifecyclePolicy.DAMAGE_INTERVAL_TICKS" in text, (
        "the interval must come from the contract policy"
    )


def test_the_boss_is_not_immobilised_by_the_inferno():
    """Contract 14.5: no full immobilisation on top of an arena-wide hazard."""
    ingest = body("private void arenaInferno(LivingEntity boss, Player target)")
    for forbidden in ("setAI(false)", "stopPathfinding()", "setAware(false)"):
        assert forbidden not in ingest, (
            "the inferno must not freeze the boss: " + forbidden
        )