"""Contract 20.3: every one of the three Kagune ring rows must be able to hurt.

The cast is authored as three concentric rows that emerge and sweep outward one
after another. Row 0 emerges at cast start and the other two follow from the
ticker. Only the ticker damaged anything, because damageKaguneRingRow is called
exclusively for the row it advances TO. So row 0 was drawn and never struck:
a player standing in the inner band watched the first ring pass through them
harmed, and only the outer two rows could ever cost a hit.

That also made the once-per-cast damage claim misleading. KaguneRingCastPolicy
grants one hit per player per cast, so a player standing in row 0 was not spared
by luck - they were simply never evaluated, and the ring they could not see
through was free to stand in.

These tests pin the ROW COVERAGE, not the policy geometry: the policy answers
rowHits() correctly for all three rows, so the fault is entirely in which rows the
event asks about.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def method(text, name):
    start = text.index(name)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start: index + 1]
    raise AssertionError("unbalanced " + name)


def test_the_first_row_actually_damages():
    """Row 0 is rendered at cast start and must strike at cast start too."""
    text = source()
    start = method(text, "private boolean startKaguneRingCast(LivingEntity boss)")
    assert "renderKaguneRingRow(boss, layout, 0" in start, (
        "precondition: row 0 is drawn at cast start"
    )
    assert "damageKaguneRingRow(boss, layout, 0" in start, (
        "row 0 is drawn but never damages, so a player standing in the inner "
        "band cannot be hit by the first ring. The cast must damage the row it "
        "just brought out, exactly as the ticker does for rows 1 and 2."
    )


def test_the_ticker_damages_each_row_it_advances_to():
    """Rows 1 and 2 already did this; keep it."""
    ticker = method(source(), "private void tickKaguneRingCast(LivingEntity boss)")
    assert "renderKaguneRingRow(boss, kaguneRingLayout, next" in ticker
    assert "damageKaguneRingRow(boss, kaguneRingLayout, next" in ticker, (
        "each advanced row must damage the band it sweeps"
    )


def test_all_three_rows_are_reachable_as_damage():
    """Guards the whole cast: three rows out means three damage opportunities."""
    text = source()
    cast = method(text, "private boolean startKaguneRingCast(LivingEntity boss)")
    ticker = method(text, "private void tickKaguneRingCast(LivingEntity boss)")
    combined = cast + ticker
    # Row 0 comes from the cast; the ticker supplies 1..ROW_COUNT-1.
    assert "damageKaguneRingRow(boss, layout, 0" in combined
    assert "damageKaguneRingRow(boss, kaguneRingLayout, next" in combined
    # And the ticker must really iterate to the last row, not stop at two.
    assert "next >= KaguneRingGapPolicy.ROW_COUNT" in ticker, (
        "the ticker must terminate on ROW_COUNT so every authored row emerges"
    )