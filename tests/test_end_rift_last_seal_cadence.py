"""The Last Seal ranged cadence must scale with the *frozen* guardian roster.

The approved design is explicit: the damaged fraction of the defensive Kagune
drives four cadence bands, 7–9 s / 5–7 s / 4–5.5 s / 3–4.5 s, and the
denominator is the original roster — not the party that happens to be online
when a cast is due. A live denominator let one player leaving slide the fight
back to its calmest band mid-phase.

This probe reads the real monolith because the interval is chosen inline next
to the cast dispatch; it proves the exact bands, the frozen denominator and the
single freeze/reset points so a later refactor cannot quietly reintroduce a
party-dependent band.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
EVENT_SOURCE = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
                / "CopiMineEndEvent.java")


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_the_four_cadence_bands_match_the_approved_damage_escalation():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    interval = _method(source, "private long lastSealSpellIntervalMillis(")

    lower = re.search(r"int\[\] lower = \{([0-9,\s]+)\};", interval)
    upper = re.search(r"int\[\] upper = \{([0-9,\s]+)\};", interval)
    assert lower and upper, "the escalation bands must stay explicit"
    lows = [int(value) for value in lower.group(1).split(",") if value.strip()]
    highs = [int(value) for value in upper.group(1).split(",") if value.strip()]
    # 7–9 s, 5–7 s, 4–5.5 s, 3–4.5 s: zero destroyed is the calmest band and
    # every destroyed-quarter tightens the window.
    assert list(zip(lows, highs)) == [
        (7000, 9000),
        (5000, 7000),
        (4000, 5500),
        (3000, 4500),
    ], f"cadence bands drifted: {list(zip(lows, highs))}"

    assert "4 * guardianDeaths.destroyedCount() / roster" in interval, (
        "the band must be selected by the destroyed fraction of the roster")
    assert "Math.clamp(" in interval and "0, 3" in interval, (
        "the destroyed fraction must clamp into the four real bands")
    assert "ThreadLocalRandom.current().nextLong(min, max + 1L)" in interval, (
        "each band is a real random window, not a fixed delay")
    assert "2800, 15000" in interval, (
        "a config override must stay bounded inside the real fight envelope")


def test_the_escalation_denominator_is_the_frozen_original_roster():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    interval = _method(source, "private long lastSealSpellIntervalMillis(")

    assert "guardianRosterOriginal > 0 ? guardianRosterOriginal : liveTarget" in interval, (
        "an intact roster must be the denominator; the live target is only a "
        "one-tick fallback before it is frozen")

    tick = _method(source, "private void tickCurrentTentacles(")
    freeze = tick.index("permanentTarget > 0 && guardianRosterOriginal <= 0")
    assert "guardianRosterOriginal = permanentTarget;" in tick[freeze:], (
        "the roster must be frozen exactly once at phase entry")

    reset = _method(source, "private void resetTentacleGuardianLedger(")
    assert "guardianRosterOriginal = 0;" in reset, (
        "a new encounter generation must forget the frozen roster")


def test_the_freeze_guard_cannot_shrink_when_a_player_leaves():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    tick = _method(source, "private void tickCurrentTentacles(")
    # The freeze condition is "only when not already frozen", so a later tick
    # with a smaller party cannot overwrite the denominator.
    assert "guardianRosterOriginal <= 0" in tick, (
        "the roster must never be re-frozen to a smaller live party")
    interval = _method(source, "private long lastSealSpellIntervalMillis(")
    assert "Math.max(1, permanentTentacleTarget(" in interval, (
        "the one-tick fallback must still guard against a zero divisor")
