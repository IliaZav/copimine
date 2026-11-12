"""Phase 4 inferno is a self-cancelling spell outside OVERLOAD.

Master contract 14.x makes ARENA_INFERNO the property of Phase 4. The runtime
ticker enforces that: its first act each tick is to clear the cast when
bossPhase != BossPhase.OVERLOAD. But BossPhasePolicy.spellPool() offers
ARENA_INFERNO to RAGE and LAST_SEAL as well, and arenaInferno(...) does not
check the phase before mutating the arena.

So outside OVERLOAD the boss could roll the spell, pay the cooldown, journal the
floor and set every selected block burning - and the first ticker tick would put
it all straight back. The player sees a one-tick flash and an unavailable spell;
the arena is mutated for no gameplay reason.

Two independent ways to be right, and the test accepts either:

* the pool does not offer ARENA_INFERNO outside OVERLOAD (it is never chosen), or
* arenaInferno(...) refuses at entry before mutating anything.

What it does not accept is the current shape: selectable outside OVERLOAD and
mutable outside OVERLOAD, with only the ticker objecting afterwards.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossPhasePolicy.java"


def method(text, name):
    start = text.index(name)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start: index + 1]
    raise AssertionError("unbalanced " + name)


def pool_block(phase):
    """The spellPool() case arm for one phase, as source text."""
    text = POLICY.read_text("utf-8")
    marker = "case " + phase + " -> List.of("
    start = text.index(marker)
    end = text.index(");", start)
    return text[start:end]


def test_inferno_is_never_selectable_outside_overload():
    """Either the pool withholds it, or the cast refuses before mutating."""
    runtime = MONOLITH.read_text("utf-8")
    entry = method(runtime, "private void arenaInferno(LivingEntity boss, Player target)")

    # Does the cast itself refuse outside OVERLOAD, before any mutation?
    refuses_early = "BossPhase.OVERLOAD" in entry
    if refuses_early:
        # The refusal must precede clearArenaInferno() / prepare(): the point of
        # an entry guard is that nothing happened yet.
        guard_at = entry.index("BossPhase.OVERLOAD")
        for later in ("clearArenaInferno()", "hazardJournal.prepare("):
            if later in entry:
                assert guard_at < entry.index(later), (
                    "the OVERLOAD guard must run before " + later
                )
        return

    # Otherwise the pool must not offer it to the other phases.
    for phase in ("RAGE", "LAST_SEAL"):
        # Match the enum reference, not prose: the pools carry a comment that
        # names the spell in order to explain its absence.
        assert "BossSpell.ARENA_INFERNO" not in pool_block(phase), (
            phase + " offers ARENA_INFERNO, but the ticker clears the cast on the "
            "first tick for any phase other than OVERLOAD - the spell would burn a "
            "cooldown and mutate the arena for a single-tick flash. Either remove it "
            "from the pool or refuse it at the start of arenaInferno(...)"
        )


def test_overload_keeps_the_spell():
    """The repair must not remove the spell from the phase that owns it."""
    assert "BossSpell.ARENA_INFERNO" in pool_block("OVERLOAD"), (
        "ARENA_INFERNO is Phase 4's defining spell and must stay in its pool"
    )
