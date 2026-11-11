"""Master contract 20: the three-row Kagune ring actually happens in play.

KaguneRingGapPolicy (geometry) and KaguneRingCastPolicy (one damage event per
player per cast) were complete, tested and called by nothing. This file guards
the wiring - the place where a correct policy produces no gameplay at all.

The contract detail that matters most: three rows must never stack onto one
launched player. A per-row claim would let a player standing between rows be hit
three times, so the claim has to be per CAST.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def body(name, text=None):
    text = text if text is not None else source()
    start = text.index(name if "(" in name else name + "(")
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace: index + 1]
    raise AssertionError("unbalanced " + name)


def test_the_ring_cast_is_reachable_from_the_boss_spell_path():
    text = source()
    assert "startKaguneRingCast(boss)" in text, (
        "the ring cast must be started from the live spell dispatch"
    )
    assert "KaguneRingGapPolicy.patternPossible(" in text, (
        "an infeasible escape pattern must refuse the cast before it fires"
    )


def test_geometry_comes_from_the_policy_not_from_inline_math():
    start = body("private boolean startKaguneRingCast(")
    assert "KaguneRingGapPolicy.layout(" in start, (
        "gap positions must come from the policy's deterministic layout"
    )
    # The player angles fed in are what stops a gap being centred on a player.
    assert "playerAngles" in start
    assert "Math.atan2" in start


def test_one_damage_event_per_player_per_entire_cast():
    damage = body("private void damageKaguneRingRow(")
    # The claim must GATE the damage, not merely be called. Asserting only that
    # claim( appears lets a mutant that calls it and ignores the result pass,
    # which is exactly the bug this test exists to catch.
    assert "if (!kaguneRingCast.claim(" in damage, (
        "the claim result must gate the damage; calling it and ignoring the "
        "boolean lets three rows stack onto one player"
    )
    gate = damage.index("if (!kaguneRingCast.claim(")
    assert damage.index(".damage(", gate) > gate, (
        "the damage must be inside the claim branch"
    )
    between = damage[gate: damage.index(".damage(", gate)]
    assert "continue;" in between, (
        "a lost claim must skip this player entirely"
    )
    # A per-row claim would reset each row and let rows stack.
    start = body("private boolean startKaguneRingCast(")
    assert "kaguneRingCast.reset()" in start, (
        "the claim set may only reset when a NEW cast begins"
    )
    tick = body("private void tickKaguneRingRow(") if "private void tickKaguneRingRow(" in source() else body("private void tickKaguneRingCast(")
    assert "kaguneRingCast.reset()" not in tick, (
        "resetting the claim per row would let three rows stack onto one player"
    )


def test_rows_advance_outward_in_order_not_all_at_once():
    tick = body("private void tickKaguneRingCast(")
    assert "kaguneRingRow + 1" in tick, "rows must advance one at a time"
    assert "ROW_COUNT" in tick, "the cast ends after the last row"
    assert "renderKaguneRingRow(" in tick and "damageKaguneRingRow(" in tick


def test_a_row_has_gaps_the_player_can_escape_through():
    render = body("private void renderKaguneRingRow(")
    assert "KaguneRingGapPolicy.insideGap(" in render, (
        "the visible ring must skip the gaps, or the render contradicts the hitbox"
    )
    damage = body("private void damageKaguneRingRow(")
    assert "KaguneRingGapPolicy.rowHits(" in damage, (
        "the hit test must be the policy's, so the visible gap IS the safe gap"
    )


def test_the_ring_is_retired_on_cast_end_and_phase_change():
    clear = body("private void clearKaguneRingCast(")
    for forgotten in ("kaguneRingLayout = null", "kaguneRingRow = -1",
                      "kaguneRingCastEndTick = -1L", "kaguneRingCast.reset()"):
        assert forgotten in clear, "cleanup must forget " + forgotten
    text = source()
    assert 'clearKaguneRingCast("phase-transition")' in text, (
        "a phase change must not leave rows running in the next phase"
    )
    assert 'clearKaguneRingCast("cast-complete")' in text


def test_the_ring_ticks_from_the_boss_loop():
    text = source()
    assert "tickKaguneRingCast(boss);" in text, (
        "without a ticker the rows never advance and the cast hangs forever"
    )
