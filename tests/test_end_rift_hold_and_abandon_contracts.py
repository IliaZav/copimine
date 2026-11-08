"""A released hold is not a hold, and an abandoned wave is not a win.

Two reported logic errors:

1. tickCoreChargeHold() used to start a timer on a single right-click and then
   finish the delivery five seconds later no matter what the player did. The
   click was a trigger, not a hold.
2. abandonRunningWave() called completeWavePhase(), so a wave that everyone
   walked away from advanced the phase and was recorded as completed.

Both are contracts about what the code may NOT do, so these tests read the
production source and hold the shape of the fix rather than a helper.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(name):
    """Return the body of a method, from its signature to its closing brace."""
    text = SOURCE.read_text(encoding="utf-8")
    marker = text.index(name)
    open_brace = text.index("{", marker)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace:index + 1]
    raise AssertionError("unterminated method " + name)


def test_the_hold_is_cancelled_as_soon_as_the_charge_is_released():
    body = method_body("private void tickCoreChargeHold(long now)")
    assert "isCarrierPresentingCharge(player)" in body, (
        "the hold must re-check that the charge is still presented every tick")
    assert '"released"' in body, (
        "a released hold needs its own cancellation reason in the log")
    # The guard has to run BEFORE the elapsed-time comparison, otherwise the
    # timer finishes first and the check never gets a chance to stop it.
    assert body.index("isCarrierPresentingCharge") < body.index("CORE_CHARGE_HOLD_MILLIS"), (
        "the presentation must be checked before the timer is allowed to finish")


def test_presentation_requires_the_charge_in_a_hand():
    body = method_body("private boolean isCarrierPresentingCharge(Player holder)")
    assert "getItemInMainHand" in body and "getItemInOffHand" in body, (
        "presentation is read from the hands, not from storage slots")
    assert "getStorageContents" not in body, (
        "a charge buried in the inventory is not being held")


def test_an_abandoned_wave_never_completes_its_objective():
    body = method_body("private boolean abandonRunningWave(int waveNumber, String reason)")
    assert "completeWavePhase" not in body, (
        "an abandoned wave must not record an objective completion")
    assert "clearWaveEntities" in body, (
        "the leftover mobs still have to be removed")
    assert "activeWave = 0" in body, (
        "the arena has to go back to having no active wave")


def test_abandonment_clears_the_victory_flag():
    body = method_body("private boolean abandonRunningWave(int waveNumber, String reason)")
    assert "waveObjectiveComplete = false" in body, (
        "a discarded wave must not leak its completion flag into the next run")
