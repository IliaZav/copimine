"""Wave 2 combat mobs must be visible and attackable by every participant.

Ownership in Wave 2 decides which player a mob CHASES. It must not decide who
is allowed to see it: hiding every non-owned mob made a party of ten fight an
arena that looked empty for nine of them.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def body(path, signature):
    text = path.read_text(encoding="utf-8")
    start = text.index(signature)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace:index + 1]
    raise AssertionError("unterminated " + signature)


def test_a_shared_wave_two_mob_is_never_hidden_from_a_non_owner():
    sync = body(SERVER, "private void syncWaveTwoPrivateViewers(Player participant)")
    assert "hideEntity" not in sync, (
        "ownership must never hide a shared combat enemy from another player")
    assert "desiredHidden" not in sync, (
        "the per-owner hide set is exactly the defect; it must be gone")


def test_ownership_is_still_a_real_concept():
    text = SERVER.read_text(encoding="utf-8")
    assert "waveTwoEntityOwner" in text, (
        "targeting ownership must survive the visibility fix")


def test_stale_hides_are_still_released():
    sync = body(SERVER, "private void syncWaveTwoPrivateViewers(Player participant)")
    assert "showEntity" in sync, (
        "a reconnect must clear any hide left behind by an older session")
    assert "bindEventEntityClient" in sync, (
        "a released mob has to be rebound so it keeps its custom visual")
