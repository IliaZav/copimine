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
    marker = f"private void {name}"
    start = SOURCE.index(marker)
    next_marker = SOURCE.find("\n    private ", start + len(marker))
    return SOURCE[start:] if next_marker < 0 else SOURCE[start:next_marker]


def test_wave_completion_cannot_advance_before_special_objective_is_complete() -> None:
    body = _method_body("tickWaveCompletion")
    assert "if (!tickWaveObjective())" in body
    assert body.index("if (!tickWaveObjective())") < body.index("boolean live")


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


def test_portal_wave_has_floor_visuals_and_bounded_speed_knockback() -> None:
    assert "spawnPortalObjectiveVisuals" in SOURCE
    assert "refreshPortalObjectiveVisuals" in SOURCE
    assert "WAVE_PORTAL_MOB_MODIFIERS" in SOURCE
    assert "PotionEffectType.SPEED" in SOURCE
    assert "Knockback II-equivalent" in SOURCE
    assert "onPortalWaveMobAttack" in SOURCE


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
