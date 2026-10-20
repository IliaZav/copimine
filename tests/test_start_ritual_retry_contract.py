from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def test_start_ritual_retry_uses_a_fresh_transition_key_after_cancellation():
    begin = EVENT.split("private void beginStartRitualIfReady()", 1)[1].split(
        "private void resetTransitionRuneHoldOnExit", 1
    )[0]

    assert 'eventId + ":start-ritual:" + UUID.randomUUID()' in begin, (
        "A canceled START_RITUAL attempt leaves its idempotency key in the state machine. "
        "A retry must receive a fresh key so READY_FOR_PLAYERS can actually transition."
    )
