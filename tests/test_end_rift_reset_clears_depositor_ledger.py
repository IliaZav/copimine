"""Guard the reset paths against leaving the depositor ledger behind.

Bug R04 from the 2026-11-22 release report.  ``resourceContributors`` records
who deposited into the Core.  Two of the full-reset paths (admin ``/cmend reset
confirm`` via ``resetEventSafely`` and ``/cmend core remove`` via ``removeCore``)
used to clear ``participantUuids`` but leave ``resourceContributors`` populated.
The very next ``cmend status`` then reported ``participants=1`` against an empty
roster, because the status formatter counted the stale depositor as a
participant while the archived roster had already been emptied.

The fix -- ``resourceContributors.clear();`` beside every
``participantUuids.clear();`` -- was landed in the same commit that documented
the defect.  This test pins it so a later edit cannot quietly drop it again.

It is a static source scan on purpose: the two ledgers must be cleared together
in every session/clone-reset body, and a source assertion catches a regression
even where the surrounding runtime needs a full Paper server to exercise.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"

# Reset paths that must drop the depositor ledger with the participant roster.
RESET_METHODS = ("resetEventSafely", "removeCore")


def _method_body(source: str, signature: str) -> str:
    """Return the brace-balanced body of the declared method.

    The marker includes the return type so a *call* to the method (which only
    has ``name(``) can never be mistaken for its declaration.
    """
    marker = f"void {signature}("
    start = source.find(marker)
    assert start != -1, f"method {signature} not found in CopiMineEndEvent.java"
    assert source.find(marker, start + 1) == -1, f"method {signature} is not unique"
    open_brace = source.index("{", start)
    depth = 0
    for cursor in range(open_brace, len(source)):
        char = source[cursor]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:cursor]
    raise AssertionError(f"unterminated body for {signature}")


def test_every_full_reset_clears_the_depositor_ledger():
    source = EVENT.read_text(encoding="utf-8")
    missing = []
    for signature in RESET_METHODS:
        body = _method_body(source, signature)
        if "participantUuids.clear();" not in body:
            missing.append(f"{signature} does not clear participantUuids")
        if "resourceContributors.clear();" not in body:
            missing.append(
                f"{signature} clears participants but leaves resourceContributors "
                "(stale depositor reads as participants=1 after a reset)"
            )
    assert not missing, "; ".join(missing)


def test_the_guard_looks_at_a_real_body_not_an_empty_string():
    """Fail if the scanner stops finding the method it exists to check."""
    source = EVENT.read_text(encoding="utf-8")
    body = _method_body(source, "resetEventSafely")
    assert "forcePhase(EventPhase.COLLECTING" in body, (
        "resetEventSafely body did not resolve; the scanner is checking the wrong text"
    )
