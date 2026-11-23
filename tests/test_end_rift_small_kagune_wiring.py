"""Master contract 18.1: small Kagune are a real enemy type, not a cast visual.

SmallKaguneSpawnPolicy already answers the contract correctly. The risk this file
covers is different and quietly fatal: the policy is correct but the monolith
never calls it, or calls it and then immediately clamps the answer with the
retired six-temporary-tentacle budget. A pure policy test cannot see that,
because it never reads the call site.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SmallKaguneSpawnPolicy.java"


def summon_body():
    """The body of spawnSmallKaguneBatch, brace-balanced."""
    source = MONOLITH.read_text("utf-8")
    marker = "private int spawnSmallKaguneBatch("
    start = source.index(marker)
    depth = 0
    for index in range(source.index("{", start), len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[source.index("{", start): index + 1]
    raise AssertionError("unbalanced spawnSmallKaguneBatch")


def test_summon_uses_the_real_policy_admission():
    body = summon_body()
    assert "SmallKaguneSpawnPolicy.admission(" in body, (
        "the summon must ask the contract policy how many may appear"
    )


def test_summon_is_not_clamped_by_the_retired_tentacle_budget():
    body = summon_body()
    # The retired rule spent the shared six-visual budget. Re-introducing it
    # silently caps a real enemy type at visual-cast limits.
    assert "MAX_TEMPORARY" not in body, (
        "small Kagune must not be clamped by TentacleController.MAX_TEMPORARY"
    )
    assert "temporaryCount()" not in body, (
        "small Kagune admission must not depend on transient visual count"
    )
    assert "SmallKagunePolicy.budget(" not in body, (
        "the superseded budget helper still subtracts on-screen visuals"
    )


def test_policy_declares_the_thirty_living_cap():
    source = POLICY.read_text("utf-8")
    assert "MAX_LIVING = 30" in source
    assert "RETIRED_TEMPORARY_LIMIT = 6" in source


def test_lifetime_expiry_is_retired_not_merely_lengthened():
    """Contract 18.1: each survives until players actually kill it."""
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SmallKagunePolicy.java"
    source = policy.read_text("utf-8")
    assert "public static boolean expired(long nowTick, long expiresAtTick) {\n        return false;" in source, (
        "expired must be hard-false; a long timer is still not 'survives until killed'"
    )


def test_small_kagune_pursue_the_party_instead_of_standing_still():
    """Owner requirement B-11: they must strike *and pursue*, not be static dolls.

    Source-level guard: the old body only attacked whoever walked into its fixed
    3-block radius. Pursuit must exist, run from the small-Kagune tick, be
    restricted to the between-attack states so a telegraph stays readable, move
    the paired Giant hitbox with the display, and stay inside the arena.
    """
    source = MONOLITH.read_text("utf-8")
    assert "private void pursueSmallKagune(ItemDisplay display," in source, (
        "small Kagune need a pursuit step; without it they are static dolls")
    small = source[source.index("private void tickSmallKagune(ItemDisplay display"):
                   source.index("private boolean smallKaguneFaces(", source.index(
                       "private void tickSmallKagune(ItemDisplay display"))]
    assert "pursueSmallKagune(display, animation, participants);" in small, (
        "the small-Kagune tick must drive pursuit")
    pursuit = source[source.index("private void pursueSmallKagune(ItemDisplay display,"):
                     source.index("private void tickSmallKagune(ItemDisplay display")]
    # Rooted attack frames: never move during the windup/strike telegraph.
    for rooted in ("SMALL_WINDUP", "SMALL_STRIKE"):
        assert rooted not in pursuit, (
            "pursuit must not run during the rooted telegraph/contact frames")
    assert "teleportCombatEntity(display, destination)" in pursuit, (
        "pursuit must move the visible actor")
    assert "teleportCombatEntity(carrier, destination)" in pursuit, (
        "pursuit must move the paired hitbox with the display")
    assert "isArenaLocation(destination)" in pursuit, (
        "pursuit must never walk a summon out of the arena")
    assert "SMALL_KAGUNE_PURSUE_STRIDE_TICKS" in pursuit, (
        "pursuit must be throttled so it reads as a walk")
    assert "withinSmallKaguneRange(display, target)" in pursuit, (
        "pursuit must stop once the target is already in strike reach")
