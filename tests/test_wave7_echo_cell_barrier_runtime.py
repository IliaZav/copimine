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


def test_echo_owner_walls_join_the_owned_world_plan_and_restart_geometry() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private boolean spawnRealitySplitBarriers(")
    echo_plan = _method(source, "private Wave7EchoDuelBarrierPlan namedEchoDuelBarrierPlan(")

    assert "namedEchoDuelBarrierPlan()" in plan
    assert "plannedCells.addAll(echoBarrierPlan.closedCells())" in plan
    assert "geometryCells.addAll(echoBarrierPlan.geometryCells())" in plan
    assert "Wave7EchoDuelCellBarrierPolicy.cells(" in echo_plan
    assert "duel.lifecycle() == me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle.COMPLETE" in echo_plan
    assert "realitySplitEchoBarrierByPair" in plan


def test_invalid_echo_geometry_is_refused_before_rebuild_or_world_mutation() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private boolean spawnRealitySplitBarriers(")

    assert plan.index("if (!echoBarrierPlan.valid())") < plan.index("clearRealitySplitBarriers(")
    assert plan.index("for (RealitySplitBarrierPolicy.Cell cell : echoBarrierPlan.closedCells())") \
        < plan.index("clearRealitySplitBarriers(")
    preflight = plan[:plan.index("clearRealitySplitBarriers(")]
    assert "!floor.getType().isSolid()" in preflight
    assert "floor.isLiquid()" in preflight
    assert "instanceof org.bukkit.block.TileState" in preflight
    assert "!fullExistingWall" in preflight


def test_echo_cell_open_is_derived_from_persisted_completion_before_mutating_world() -> None:
    source = EVENT.read_text(encoding="utf-8")
    death = _method(source, "private void handleNamedRealitySplitTrialBossDeath(")
    opener = _method(source, "private boolean openCompletedNamedEchoCellBarriers(")

    persist = death.index("saveStateSync()")
    open_cells = death.index("openCompletedNamedEchoCellBarriers(")
    assert persist < open_cells, "durable Echo completion must precede opening physical cell walls"
    assert "openRealitySplitEchoCellBoundary(" in opener
    assert "Wave7EchoDuelCellBarrierPolicy.plan(" in opener
    plan = _method(source, "private Wave7EchoDuelBarrierPlan namedEchoDuelBarrierPlan(")
    assert "Wave7EchoDuelCellBarrierPolicy.remaining(" in plan


def test_echo_cell_wall_mutation_uses_originals_and_rolls_back_on_failure() -> None:
    source = EVENT.read_text(encoding="utf-8")
    opener = _method(source, "private boolean openRealitySplitEchoCellBoundary(")

    assert "hazardJournal.load()" in opener
    assert "HazardMutationJournal.Entry::isRealitySplitBarrierMutation" in opener
    assert "RealitySplitBarrierMutationPolicy.applyAtomically" in opener
    assert "barrier.setType(Material.AIR, false)" in opener
    assert "restoreRealitySplitBoundaryClosed" in opener
    assert "enterNamedRealitySplitRecovery" in opener
    assert "rememberUncommittedRealitySplitEchoBoundaryRollback" in opener


def test_failed_echo_wall_open_retries_collision_restore_and_cleanup_clears_ownership() -> None:
    source = EVENT.read_text(encoding="utf-8")
    retry = _method(source, "private boolean recloseUncommittedRealitySplitEchoBoundary(")
    cleanup = _method(source, "private boolean clearRealitySplitBarriers(")

    assert "restoreRealitySplitBoundaryCollisionClosed" in retry
    assert "restoreRealitySplitBoundaryVisuals" in retry
    assert "realitySplitEchoBarrierByPair.put(boundary" in retry
    assert "realitySplitEchoBoundaryCellsPendingRollback.remove(boundary)" in retry
    assert "entry.webOriginal()" in cleanup
    assert "realitySplitEchoBarrierByPair.clear()" in cleanup
