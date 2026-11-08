"""Every refused Wave 7 start names its stage and its inputs.

The reported failure was a refusal with no explanation: the guard returned
false and nothing was logged, so "wave 7 did not start" was unfalsifiable. A
silent refusal is worse than a wrong one, because it cannot be reported against.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def preflight() -> str:
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private boolean preflightWave7StartBeforeWorldMutation(")
    return text[start:text.index("private boolean refuseWave7Preflight(", start)]


def test_every_refusal_line_carries_the_full_structured_identity():
    body = preflight()
    for needle in ("generation=", "phase=", "requested_wave=7",
                   "active_wave=", "eligible_players="):
        assert needle in body, f"a refusal line lost {needle}"


def test_each_refusal_names_a_stage_and_a_code():
    body = preflight()
    for code in ("WAVE7_PREFLIGHT_INPUT_INVALID", "WAVE7_NAMED_ACTOR_ADAPTER_PREFLIGHT_FAILED",
                 "WAVE7_NAMED_ECHO_PREFLIGHT_FAILED", "WAVE7_BARRIER_PREFLIGHT_FAILED",
                 "WAVE7_RETURN_RIGHTS_START_REFUSED",
                 "WAVE7_NAMED_LAYOUT_CHECKPOINT_FAILED"):
        assert code in body, f"{code} must be attributable in the log"


def test_the_input_guard_is_not_silent():
    body = preflight()
    invalid = body[body.index("if (world == null || core == null"):]
    invalid = invalid[:invalid.index("return false;")]
    # A substring check is not enough: wrapping the call in "if (false)" keeps the
    # text and kills the log. Require the call to be a statement that can run.
    assert "\n            getLogger().severe(\"END_RIFT_WAVE7_PREFLIGHT_REFUSED" in invalid, (
        "the very first guard used to return false with no log line, which is the "
        "reported unfalsifiable 'wave 7 did not start'; it must be an executable "
        "statement, not text inside a dead branch")
    assert "stage=" in invalid and "code=" in invalid


def test_a_small_roster_still_reports_how_many_arrived():
    body = preflight()
    roster = body[body.index("playerCount < 2L"):]
    roster = roster[:roster.index("return false;")]
    assert "eligible_players=" in roster and "minimum=2" in roster


def test_the_refusal_path_mentions_journal_ownership_and_recovery():
    body = preflight()
    assert "journal=" in body or "journal ownership" in body
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private boolean refuseWave7Preflight(")
    refuse = text[start:start + 4000]
    assert "enterNamedRealitySplitRecovery(" in refuse, (
        "a refusal must leave the attempt in a named, resumable state")
