from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


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


def test_one_room_recovery_validates_barrier_journal_before_solo_shortcut() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private boolean spawnRealitySplitBarriers(")

    cleanup = plan.index('if (!clearRealitySplitBarriers("wave7-rebuild"))')
    journal_load = plan.index("HazardMutationJournal.Snapshot existing")
    journal_status_check = plan.index("if (!journal.valid() ||")
    journal_scope_check = plan.index(
        "if (!journal.entries().isEmpty() && !journalScopeMatches && !discardRestoredJournal)"
    )
    invalid_solo_guard = plan.index("if (soloRoomWithoutOuterDivider && !journal.valid())")
    invalid_solo_branch = _method(plan, "if (soloRoomWithoutOuterDivider && !journal.valid())")
    solo_condition = "if (soloRoomWithoutOuterDivider && echoBarrierPlan.closedCells().isEmpty())"
    solo_shortcut = plan.index(solo_condition)
    solo_branch = _method(plan, solo_condition)

    assert cleanup < journal_load < invalid_solo_guard < journal_status_check < journal_scope_check < solo_shortcut, (
        "a divider-free Wave 7 layout must complete cleanup, journal validity/status checks, and "
        "event-generation-world scope checks before it can report success"
    )
    assert 'recoveryReason = "END_RIFT_WAVE7_HAZARD_JOURNAL_INVALID"' in plan
    assert "EventPhase.RECOVERY_REQUIRED" in invalid_solo_branch
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED, recoveryReason)" in invalid_solo_branch
