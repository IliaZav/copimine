from pathlib import Path


SOURCE = (
    Path(__file__).resolve().parents[1]
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "CopiMineEndEvent.java"
).read_text(encoding="utf-8")


def _method_body(name: str) -> str:
    marker = next(
        candidate
        for candidate in (
            f"private void {name}",
            f"private boolean {name}",
            f"private int {name}",
        )
        if candidate in SOURCE
    )
    start = SOURCE.index(marker)
    next_marker = SOURCE.find("\n    private ", start + len(marker))
    return SOURCE[start:] if next_marker < 0 else SOURCE[start:next_marker]


def test_wave_completion_cannot_advance_before_special_objective_is_complete() -> None:
    body = _method_body("tickWaveCompletion")
    assert "if (!tickWaveObjective())" in body
    assert body.index("if (!tickWaveObjective())") < body.index("boolean live")


def test_completed_wave_objective_is_not_tick_processed_again() -> None:
    body = _method_body("tickWaveObjective")
    assert "if (waveObjectiveComplete)" in body
    assert "return true;" in body[body.index("if (waveObjectiveComplete)"):]


def test_all_special_wave_objectives_have_runtime_controllers() -> None:
    for method in (
        "startWaveObjective",
        "updateCorePulseObjective",
        "updateMarkedTargetObjective",
        "updatePortalObjective",
        "updateTowerObjective",
        "updateRiftStormObjective",
    ):
        assert f"private void {method}" in SOURCE
    assert "case 1 -> updateCorePulseObjective(now)" in SOURCE
    assert "case 2 -> updateMarkedTargetObjective(now)" in SOURCE
    assert "case 3 -> updatePortalObjective(now)" in SOURCE
    assert "case 4 -> updateTowerObjective(now)" in SOURCE
    assert "case 5 -> updateRiftStormObjective(now)" in SOURCE


def test_wave_objective_cleanup_is_explicit_and_retry_is_owned() -> None:
    assert "private void clearWaveObjectiveState()" in SOURCE
    assert "towerRetryTask" in SOURCE
    assert "taskRegistry.register(towerRetryTask)" in SOURCE


def test_tower_deadline_uses_the_exact_deadline_for_late_scheduler_ticks() -> None:
    body = _method_body("updateTowerObjective")
    deadline_branch = body[body.index("if (now >= state.deadlineMillis())"):]
    assert "completeAtDeadline(state, now)" in deadline_branch
    assert "TowerDefensePolicy.finish(state, now)" not in deadline_branch


def test_rift_storm_uses_bounded_journaled_floor_and_web_mutations() -> None:
    planner = _method_body("planRiftStorm")
    apply = _method_body("applyRiftStormPlan")
    restore = _method_body("restoreRiftStormBlocks")
    update = _method_body("updateRiftStormObjective")
    assert "HazardPlanner.plan" in planner
    assert "HazardMutationJournal.Entry" in apply
    assert "hazardJournal.prepare" in apply
    assert "Material.MAGMA_BLOCK" in apply
    assert "Material.COBWEB" in apply
    assert "setType(Material.FIRE" not in apply
    assert "restoreBlock" in restore
    assert "hazardJournal.markRestored" in restore
    assert "restoreRiftStormBlocks();" in update
    assert "riftStormLastDamageSecond" in update
    assert "int stormFloorY = combatFloorY();" in planner
    assert "world.getBlockAt(x, stormFloorY, z)" in planner
    assert "world.getBlockAt(point.x(), stormFloorY, point.z())" in apply
    assert "private int combatFloorY()" in SOURCE
    assert "world.getBlockAt(entry.getKey().x(), stormFloorY, entry.getKey().z())" in restore


def test_wave_four_spawn_composition_is_capped_after_player_scaling() -> None:
    spawn = _method_body("spawnWave")
    assert "WaveMechanicsPolicy.capTowerCounts" in spawn
    assert "WAVE_TOWER_COMPOSITION_CAP" in spawn
    assert "wave == 4" in spawn


def test_portal_wave_has_floor_visuals_and_bounded_speed_knockback() -> None:
    assert "spawnPortalObjectiveVisuals" in SOURCE
    assert "refreshPortalObjectiveVisuals" in SOURCE
    assert "WAVE_PORTAL_MOB_MODIFIERS" in SOURCE
    assert "PotionEffectType.SPEED" in SOURCE
    assert "Knockback II-equivalent" in SOURCE
    assert "onPortalWaveMobAttack" in SOURCE


def test_portal_capture_centers_use_the_playable_floor_anchor() -> None:
    start = SOURCE.index("if (wave == 3) {")
    end = SOURCE.index("} else if (wave == 4) {", start)
    body = SOURCE[start:end]
    assert "Location portalAnchor = coreCombatAnchorLocation();" in body
    assert "portalAnchor.clone().add" in body
    assert "floor_y=" in body
    assert "coreLocation().add(Math.cos(angle) * 8.0D" not in body


def test_portal_objective_emits_one_completion_marker_after_all_portals_are_captured() -> None:
    body = _method_body("updatePortalObjective")
    assert "boolean wasComplete = waveObjectiveComplete;" in body
    assert "WAVE_OBJECTIVE_COMPLETE" in body
    assert "if (waveObjectiveComplete && !wasComplete)" in body
    assert body.count("announceEventTitle(\"§aПОРТАЛЫ ЗАПЕЧАТАНЫ\"") == 1


def test_tower_defense_retry_cannot_be_replaced_by_empty_objective_rehydration() -> None:
    """A failed tower must wait for its scheduled mob respawn before ticking a new objective."""
    body = _method_body("tickWaveObjective")
    assert "towerRetryTask != null" in body
    assert "return false" in body
    retry = _method_body("handleTowerDefenseFailure")
    assert "spawnWave(4, false)" in retry
    assert "WAVE_RETRY_STARTED" in retry


def test_wave_one_and_two_objectives_are_live_controllers_not_auto_completed() -> None:
    start = SOURCE.index("private void startWaveObjective")
    end = SOURCE.index("private void planRiftStorm", start)
    body = SOURCE[start:end]
    assert "waveObjectiveComplete = true" not in body
    assert "waveOneNextPulseMillis" in body
    assert "waveTwoNextMarkMillis" in body
    assert "WAVE_ONE_PULSE_TELEGRAPH_TICKS = 50" in SOURCE
    assert "WAVE_ONE_PULSE_DAMAGE = 6.0D" in SOURCE
    assert "PotionEffectType.SLOWNESS" in SOURCE
    assert "WAVE_TWO_MARK_DURATION_TICKS = 220" in SOURCE
    assert "PotionEffectType.GLOWING" in SOURCE
    assert "PotionEffectType.SPEED" in SOURCE
    assert "PotionEffectType.RESISTANCE" in SOURCE
    assert "sendActionBar" in SOURCE
    assert "playSound" in SOURCE
