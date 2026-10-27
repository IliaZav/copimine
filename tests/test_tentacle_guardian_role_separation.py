"""Defensive guardians must strike, never capture (canonical spec §6.2/§11)."""

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


def test_guardian_strike_resolves_instead_of_grab(event_source: str) -> None:
    tick = method_body(event_source, "private void tickTentacle(ItemDisplay display")
    start = tick.index("} else if (!state.temporary()) {")
    guardian = tick[start:tick.index("} else {", start)]
    # The permanent branch must strike and leave the capture chain untouched.
    assert "strikeTentacleGuardianTarget(display, target);" in guardian
    assert guardian.index("strikeTentacleGuardianTarget") < guardian.index(
        "State.RECOVERY, null);")
    assert "GRAB_SUCCESS" not in guardian
    assert "HOLD" not in guardian
    assert "THROW" not in guardian


def test_strike_is_a_real_dodgeable_damage_event(event_source: str) -> None:
    strike = method_body(event_source,
                         "private void strikeTentacleGuardianTarget(ItemDisplay display")
    assert "withinTentacleGrabRange(origin, target.getLocation())" in strike
    assert strike.index("withinTentacleGrabRange") < strike.index("target.damage(")
    assert "TentacleGuardianPolicy.STRIKE_DAMAGE" in strike
    assert "setHealth" not in strike
    # A rejected or fully absorbed hit must not flinch or push the player.
    assert "if (healthAfter >= healthBefore) {" in strike
    assert strike.index("healthAfter >= healthBefore") < strike.index("EntityEffect.HURT")
    assert strike.index("EntityEffect.HURT") < strike.index("setVelocity(")


def test_guardians_keep_no_capture_states_or_throw(event_source: str) -> None:
    tick = method_body(event_source, "private void tickTentacle(ItemDisplay display")
    permanent_states = tick[tick.index("if (!state.temporary()) {"):]
    permanent_states = permanent_states[:permanent_states.index("switch (animation) {", 1)]
    assert "GRAB_SUCCESS" not in permanent_states
    assert "HOLD" not in permanent_states
    assert "THROW" not in permanent_states
    small = method_body(event_source, "private void tickSmallKagune(ItemDisplay display")
    assert "GRAB_SUCCESS" not in small
    assert "HOLD_LOCK" not in small
    assert "THROW" not in small
