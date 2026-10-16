from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_wave3_gate_guard_is_assigned_to_a_bounded_stable_squad_subset():
    source = EVENT.read_text(encoding="utf-8")
    planner = _declaration(source, "private Map<UUID, UUID> planWaveMobSquads(")
    reconcile = _declaration(source, "private void reconcileWave3GateSquadIntents(")

    assert "Wave3SquadIntentPolicy.gateAssignments(" in reconcile
    assert "currentWave3ActivePortal(" in _declaration(source, "private List<Wave3SquadIntentPolicy.GateTarget> wave3GateTargets(")
    assert "GateState.CAPTURED" in _declaration(source, "private List<Wave3SquadIntentPolicy.GateTarget> wave3GateTargets(")
    assert "GateState.CLOSED" in _declaration(source, "private List<Wave3SquadIntentPolicy.GateTarget> wave3GateTargets(")
    assert "wave3GateAssignmentMembers" in reconcile
    assert "wave3Squads.addAll(plan.squads())" in planner
    assert "reconcileWave3GateSquadIntents(wave3Squads, now)" in planner


def test_only_assigned_wave3_fighters_anchor_at_gate_and_capture_stays_player_owned():
    source = EVENT.read_text(encoding="utf-8")
    tactic = _declaration(source, "private CombatTacticsPolicy.MobTactic assignCombatTactic(")
    combat_tactic = _declaration(source, "private CombatTacticsPolicy.MobTactic combatTactic(")
    skeleton_controller = _declaration(source, "private void maintainSkeletonCombatPosture(")
    anchor = _declaration(source, "private Location waveMobObjectiveAnchor(")
    skeleton_posture = _declaration(source, "private Location skeletonRangedDestination(")
    capture = _declaration(source, "private void updatePortalObjective(")

    assert "wave3GateMobTargets.containsKey(entity.getUniqueId())" in tactic
    assert "MobTactic.ASSAULT" in tactic
    assert "wave3GateMobTargets" in combat_tactic
    assert "&& !gateGuard" in skeleton_controller
    assert "wave3GateMobTargets.get(mob.getUniqueId())" in anchor
    assert "wave3GateMobTargets.get(skeleton.getUniqueId())" in skeleton_posture
    assert "activeWaveParticipants().stream().anyMatch" in capture
    assert "wave3GateMobTargets" not in capture


def test_wave3_gate_assignment_cache_clears_with_generation_and_objective_resets():
    source = EVENT.read_text(encoding="utf-8")
    initialize = _declaration(source, "private boolean initializeWaveGameplay(")
    reset = _declaration(source, "private boolean clearWaveObjectiveState(")
    combat_reset = _declaration(source, "private void clearCombatAiState(")
    reconcile = _declaration(source, "private void reconcileWave3GateSquadIntents(")

    assert "clearWave3GateAssignments()" in initialize
    assert "clearWave3GateAssignments()" in reset
    assert "clearWave3GateAssignments()" in combat_reset
    assert "wave3GateAssignmentGeneration != generation" in reconcile


def test_gate_defenders_keep_player_targets_instead_of_attacking_portals():
    source = EVENT.read_text(encoding="utf-8")
    tick = _declaration(source, "private void tickWaveMobAi(")
    reconcile = _declaration(source, "private void reconcileWave3GateSquadIntents(")

    assert "target = Bukkit.getPlayer(plannedTargetId)" in tick
    assert "if (supportSpider || targetChanged) mob.setTarget(supportSpider ? null : target)" in tick
    assert "mob.setTarget(portal" not in reconcile
    assert "updatePortalObjective(" not in reconcile


def test_wave3_gate_intent_changes_invalidate_only_the_affected_path():
    source = EVENT.read_text(encoding="utf-8")
    reconcile = _declaration(source, "private void reconcileWave3GateSquadIntents(")
    assert "private void refreshWave3GateIntentPath(" in source
    invalidate = _declaration(source, "private void refreshWave3GateIntentPath(")
    skeleton_path = _declaration(source, "private void maintainSkeletonCombatPosture(")
    mob_path = _declaration(source, "private void maintainWaveMobPath(")

    assert "wave3GateMobTargets" in reconcile
    assert "Objects.equals(previousGate, currentGate)" in reconcile
    assert "wave3GateIntentPathRefresh.add(fighter)" in reconcile
    assert "refreshWave3GateIntentPath(skeleton)" in skeleton_path
    assert "refreshWave3GateIntentPath(mob)" in mob_path
    assert skeleton_path.index("refreshWave3GateIntentPath(skeleton)") \
        < skeleton_path.index("now < nextWavePathRequestMillis")
    assert mob_path.index("refreshWave3GateIntentPath(mob)") \
        < mob_path.index("now < nextWavePathRequestMillis")
    assert invalidate.index("stopPathfinding()") < invalidate.index("nextWavePathRequestMillis.remove")
    assert "waveLastPathDestinations.remove" in invalidate
