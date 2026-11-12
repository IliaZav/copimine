"""Contract 18.1: small Kagune spawn on their OWN 15-20 second cadence.

SmallKaguneSpawnPolicy carries the whole contract: a 15-20 s interval, a
party-scaled batch, a hard cap of thirty LIVING enemies, and the rule that
spawns stop when the cocoon breaks while survivors stay killable.

None of that cadence was wired. The only trigger was the SMALL_KAGUNE boss
spell, so the enemy type appeared when the brain happened to roll that spell
and not otherwise - and the spell pool is shared with FINAL_STRIKE, AMBUSH_GRAB
and VOID_MARK, so "every 15-20 seconds" was really "whenever the die says so",
which on a bad run is not at all.

Contract 18 is explicit that this is an independent timer, so these tests check
for an actual timer: the policy cadence functions must be reachable from the
production tick path, not merely present in the policy class.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


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


def test_the_cadence_functions_are_reachable_from_the_tick_path():
    """A policy nobody schedules is a policy that does not exist."""
    text = source()
    tick = method(text, "private void tickBoss()")
    assert "SmallKaguneSpawnPolicy" in tick or "tickSmallKaguneSpawner" in tick, (
        "the small-Kagune spawner must run from the boss tick, independently of "
        "spell selection; contract 18.1 is a timer, not a spell outcome"
    )


def test_the_spawner_uses_the_policy_cadence_and_not_a_spell_roll():
    text = source()
    spawner = method(text, "private void tickSmallKaguneSpawner(")
    # The due check is what makes it a cadence rather than an occasional event.
    assert "smallKaguneCadence.poll(" in spawner, (
        "the spawner must ask the policy whether it is due"
    )
    cadence = (MONOLITH.parent / "domain/SmallKaguneCadence.java").read_text("utf-8")
    assert "SmallKaguneSpawnPolicy.intervalMillis(" in cadence, (
        "the spawner must schedule the next window from the policy"
    )
    # It must not be gated on the spell having been chosen.
    assert "currentBossBrainAbility" not in spawner, (
        "the cadence must not depend on the brain having selected a spell"
    )


def test_the_spawner_stops_after_the_cocoon_breaks():
    """Contract 18: no new spawns after the break; survivors stay killable."""
    text = source()
    spawner = method(text, "private void tickSmallKaguneSpawner(")
    cadence = (MONOLITH.parent / "domain/SmallKaguneCadence.java").read_text("utf-8")
    assert "if (cocoonBroken) stopped = true" in cadence
    assert "!combatActive || stopped" in cadence, (
        "the spawner must consult the policy for whether spawning is still allowed"
    )
    assert "cocoon" in spawner.lower(), (
        "the stop condition is the cocoon break and must appear explicitly"
    )


def test_the_spawner_is_generation_scoped():
    """A restart inside LAST_SEAL must not inherit the old cadence clock."""
    text = source()
    spawner = method(text, "private void tickSmallKaguneSpawner(")
    assert "generation" in spawner, (
        "the cadence must be fenced by the encounter generation"
    )