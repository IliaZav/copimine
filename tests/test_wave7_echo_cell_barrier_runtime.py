"""ECHO-005: one shared Echo region instead of individual owner cells.

The personal Echo used to receive owner-local lanes with physical separators,
opened only after two adjacent duels had both completed. The contract forbids
those walls: owners fight their copies inside one shared combat region and the
server owner-pair rule (EchoCombatAdmission) does the isolation. These tests
keep the replacement honest - no separator geometry, no per-owner world
mutation, and the barrier plan still refuses an invalid persisted Echo state.
"""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
TRIALS = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RealitySplitTrialController.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


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


def test_echo_stage_adds_no_owner_separators_to_the_wave7_world_plan() -> None:
    source = EVENT.read_text(encoding="utf-8")
    preflight = _method(source, "private boolean preflightWave7BarrierCells(")
    plan = _method(source, "private Wave7EchoDuelBarrierPlan namedEchoDuelBarrierPlan(")
    spawn = _method(source, "private boolean spawnRealitySplitBarriers(")

    for body in (preflight, plan, spawn):
        assert "Wave7EchoDuelCellBarrierPolicy" not in body
        assert "closedCells()" not in body
        assert "geometryCells()" not in body
    assert "realitySplitEchoBarrierByPair" not in source
    assert "realitySplitEchoBoundaryCellsPendingRollback" not in source
    assert "openRealitySplitEchoCellBoundary" not in source


def test_invalid_persisted_echo_state_still_blocks_the_barrier_build() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _method(source, "private Wave7EchoDuelBarrierPlan namedEchoDuelBarrierPlan(")

    # A broken persisted duel state exits as invalid before the final empty()
    # success, so an unfinished Echo can never be treated as a valid plan.
    assert plan.index("duels == null || trial.owners().isEmpty()") < plan.rindex(
        "return Wave7EchoDuelBarrierPlan.empty();")
    assert "return Wave7EchoDuelBarrierPlan.invalid();" in plan[plan.index(
        "duels == null || trial.owners().isEmpty()"):]
    spawn = _method(source, "private boolean spawnRealitySplitBarriers(")
    assert "if (!echoBarrierPlan.valid())" in spawn
    assert spawn.index("!echoBarrierPlan.valid()") < spawn.index("clearRealitySplitBarriers(")
    assert spawn.index("namedEchoDuelBarrierPlan()") < spawn.index("clearRealitySplitBarriers(")


def test_echo_runtime_keeps_every_owner_inside_one_shared_region() -> None:
    source = EVENT.read_text(encoding="utf-8")
    checkpoint = _method(source, "private boolean checkpointNamedEchoHealthChanges(")
    relocate = _method(source, "private boolean moveNamedEchoOwnerToCell(")

    assert "Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, cell.owner()," in checkpoint
    # The relocation keeps its single public shape and delegates to the
    # shared-region overload with the plan it already loaded.
    assert "echoDuelCells(cell.chamber())" in relocate
    # No per-owner corridor gate may come back into the movement checks.
    for body in (checkpoint, relocate):
        assert "Wave7EchoDuelCellBarrierPolicy" not in body


def test_echo_duel_region_matches_the_actual_room_layout(tmp_path) -> None:
    # Wave 7 owns exactly ONE room at every party size, so every Echo owner is
    # placed in that one shared region rather than scattered across the four
    # walled sectors an older contract used. Exercise the controller and the
    # snapshot rather than forbidding a helper name.
    from tests.test_wave7_roster_routes import test_one_sequential_route_for_every_roster_size

    test_one_sequential_route_for_every_roster_size(tmp_path)


def test_shared_region_policy_exposes_lane_and_access_helpers() -> None:
    policy = (DOMAIN / "Wave7EchoDuelCellPolicy.java").read_text(encoding="utf-8")

    assert "public static List<Cell> sharedRegionPlan(" in policy
    assert "public static boolean allowsSharedRegionAccess(" in policy
    assert "one shared combat region" in policy or "SHARED" in policy
