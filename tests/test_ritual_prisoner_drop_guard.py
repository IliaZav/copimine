"""The prison state owns the prisoner's inventory: Q may not drop an item."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_prisoner_drop_is_cancelled_server_side():
    source = SOURCE.read_text(encoding="utf-8")
    handler = _method(source, "public void onRitualPrisonerDrop(PlayerDropItemEvent event)")
    assert "activeWave != 6" in handler, "only the ritual prisoner is restricted"
    assert "wave6PrisonBroken" in handler, (
        "the released prisoner must be able to drop items again"
    )
    assert "isCurrentRitualPrisoner" in handler, "prisoner identity must be re-validated"
    assert "event.setCancelled(true)" in handler, "the server must own the drop block"
    carrier = _method(source, "public void onCurrentCarrierChargeDrop(PlayerDropItemEvent event)")
    assert "isCurrentRitualPrisoner" not in carrier, (
        "the Wave 1 charge drop handler must stay unrelated to the prison state"
    )


def _method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start : cursor + 1]
    raise AssertionError(signature)
