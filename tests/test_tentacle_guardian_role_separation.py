"""Permanent Kagune guardians must STRIKE and also CAPTURE (V6 final seal).

The earlier "defensive guardians strike, never capture" contract is superseded.
V6 requires the permanent rig to own both a heavy, dodgeable strike and a
committed, bounded capture. These tests pin the current approved behaviour, so a
future edit cannot silently retire either half again.
"""

from pathlib import Path

import pytest

PLUGIN = Path(__file__).resolve().parents[1] / "copimine-end-event"
EVENT = PLUGIN / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opened = source.index("{", start)
    depth = 0
    for index in range(opened, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError("unbalanced braces after " + signature)


@pytest.fixture(scope="module")
def event_source() -> str:
    return EVENT.read_text(encoding="utf-8")


def test_permanent_guardian_strike_is_a_real_dodgeable_damage_event(
        event_source: str) -> None:
    strike = method_body(event_source,
                         "private void strikeTentacleGuardianTarget(ItemDisplay display")
    # Reach is the imported rig's real length, not the retired eight-block grab,
    # and the strike is refused out of range.
    assert "guardianStrikeRangeSquared()" in strike
    assert strike.index("distanceSquared(target.getLocation()) > guardianStrikeRangeSquared()") < (
        strike.index("applyBossActorAbilityDamage("))
    # A dodge means breaking facing or cover, so both gates must precede damage.
    assert "smallKaguneFaces(display,target)" in strike
    assert "hasProjectileBlockLineOfSight(" in strike
    assert strike.index("smallKaguneFaces(") < strike.index("applyBossActorAbilityDamage(")
    assert strike.index("hasProjectileBlockLineOfSight(") < strike.index("applyBossActorAbilityDamage(")
    # Damage is party-scaled and bounded by the fight policy; nothing writes HP.
    assert "KaguneGuardianFightPolicy.strikeDamage(eventScalePlayers())" in strike
    assert "setHealth" not in strike
    # Reactions depend on the observed accepted event, including absorption.
    assert "if (!accepted ||" in strike
    assert strike.index("if (!accepted ||") < strike.index("EntityEffect.HURT")
    assert strike.index("EntityEffect.HURT") < strike.index("setVelocity(")


def test_permanent_guardian_also_captures_behind_real_guards(event_source: str) -> None:
    capture = method_body(event_source,
                          "private boolean acceptGuardianCapture(ItemDisplay display")
    # The same real body reach, facing and cover gate the capture as the strike.
    assert "guardianStrikeRangeSquared()" in capture
    assert "smallKaguneFaces(display, target)" in capture
    assert "hasProjectileBlockLineOfSight(" in capture
    # At most one live hold/throw at a time: a second capture is refused.
    assert "State.HOLD" in capture and "State.THROW" in capture
    # The socket must be a legal, safe arena cell before the grab commits.
    assert "isArenaLocation(socket)" in capture
    assert "isSafeTentacleHoldLocation(socket, target)" in capture
    # A committed capture applies an accepted contact hit and can still miss.
    assert "applyTentacleReleaseHit(display, target)" in capture
    assert "return false" in capture


def test_permanent_guardian_grab_lifecycle_reaches_hold_and_throw(event_source: str) -> None:
    tick = method_body(event_source, "private void tickTentacle(ItemDisplay display")
    # The shared lifecycle still drives the real capture chain for permanent rigs;
    # a refused capture falls through to recovery, never a phantom hold.
    assert "acceptGuardianCapture(display, target)" in tick
    for state in ("State.GRAB_SUCCESS", "State.HOLD", "State.THROW"):
        assert state in tick


def test_melee_apparition_and_small_kagune_never_capture(event_source: str) -> None:
    # Role C and the small Kagune share the display plumbing but own no capture
    # chain, so they can never pin a player.
    directed = method_body(event_source,
                           "private void tickGuardianDirectedStrike(ItemDisplay display")
    assert "GRAB_SUCCESS" not in directed
    assert "State.HOLD" not in directed
    assert "State.THROW" not in directed
    small = method_body(event_source, "private void tickSmallKagune(ItemDisplay display")
    assert "GRAB_SUCCESS" not in small
    assert "HOLD_LOCK" not in small
    assert "THROW" not in small
