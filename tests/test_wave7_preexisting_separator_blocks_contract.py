from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
JOURNAL = ROOT / "copimine-end-event/src/me/copimine/endevent/HazardMutationJournal.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated method: {signature}")


def test_preexisting_full_blocks_enter_owned_barrier_and_visual_sets() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private boolean spawnRealitySplitBarriers(")

    existing_full_block = plan.find("isRealitySplitExistingWallCoverage")
    original_capture = plan.find("plannedOriginals.putIfAbsent")
    assert existing_full_block >= 0 and original_capture >= 0 and existing_full_block < original_capture, (
        "a pre-existing full-collision separator block must enter the owned/journaled barrier plan; "
        "counting it only in the later coverage predicate leaves it opaque and unopenable"
    )
    assert "realitySplitBarrierCells.addAll(plannedOriginals.keySet())" in plan
    assert "new LinkedHashSet<>(realitySplitBarrierCells)" in plan


def test_completed_boundary_clears_wall_and_cleanup_restores_original_block() -> None:
    source = EVENT.read_text(encoding="utf-8")
    journal_source = JOURNAL.read_text(encoding="utf-8")
    open_boundary = _method(source, "private boolean openRealitySplitBoundary(")
    cleanup = _method(source, "private boolean clearRealitySplitBarriers(")

    assert re.search(r"barrier\.setType\(Material\.AIR\s*,\s*false\)", open_boundary), (
        "opening a completed passage must remove the physical separator cell instead of restoring "
        "its original opaque block into the passage"
    )
    assert "restoreBlock(barrier, realitySplitBarrierOriginals.get(cell))" not in open_boundary

    assert "entry.webOriginal()" in cleanup
    restore = _method(source, "private boolean isRealitySplitOwnedOrRestoredState(")
    assert "restoreRealitySplitBarrierCellsAtomically" in cleanup
    assert re.search(r"(?:isAir\(\)|Material\.AIR)", restore), (
        "full cleanup must restore the original journaled block for a passage cell that the event "
        "deliberately left as air"
    )
    assert "new Snapshot(schemaVersion, eventId, generation, world, next, entries, valid)" in journal_source, (
        "journal status transitions must retain original block entries for reset/restart restoration"
    )


def test_restart_rebuild_keeps_open_boundary_originals_in_hazard_journal() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private boolean spawnRealitySplitBarriers(")
    recover = _method(source, "private void recoverHazardJournal(")

    assert "openPassages()" in plan
    assert "isRealitySplitBarrierMutation()" in recover
    assert "hazardJournal.load()" in plan and "journal.entries()" in plan, (
        "Wave 7 rehydration must carry originals for already-opened boundaries into the next "
        "journal prepare, while rebuilding only closed collision walls"
    )
    assert "journalEntries.addAll" in plan, (
        "rehydration must merge retained open-boundary originals with newly planned wall cells "
        "instead of replacing their restart/cleanup records"
    )
