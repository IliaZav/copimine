"""Executable regressions for Task 5 projectile visibility and admission."""
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
RESOURCE_SCOPE = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/EncounterResourceScope.java"


def java_method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[opening + 1:end - 1]


def test_dragon_breath_cloud_requires_clear_block_sight_before_scheduling_damage():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    tick = java_method_body(source, "private void tickEliteDragonBreathCloud(")
    assert "hasProjectileBlockLineOfSight(cloud.getLocation(), player.getEyeLocation())" in tick
    assert "WaveProjectileTargetPolicy.allowsPlayerAoe" in tick


def test_dragon_breath_cloud_rechecks_block_sight_before_deferred_damage():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    damage = java_method_body(source, "private void scheduleEliteDragonBreathCloudDamage(")
    assert "hasProjectileBlockLineOfSight(liveCloud.getLocation()," in damage
    assert "livePlayer.getEyeLocation()" in damage
    assert "WaveProjectileTargetPolicy.allowsPlayerAoe" in damage


def test_explosive_arrow_aoe_requires_clear_block_sight_from_impact():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    detonate = java_method_body(source, "private void detonateExplosiveArrow(")
    assert "hasProjectileBlockLineOfSight(point, player.getEyeLocation())" in detonate
    assert "WaveProjectileTargetPolicy.allowsPlayerAoe" in detonate


def test_explosive_arrow_block_impact_rechecks_active_event_phase_before_damage():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    detonate = java_method_body(source, "private void detonateExplosiveArrow(")
    rejected_phase_cleanup = detonate.index("!isEventArrowPhaseAllowed(arrow)")
    first_damage = detonate.index("player.damage(")

    assert rejected_phase_cleanup < first_damage
    assert "cleanupEventArrow(arrow.getUniqueId())" in detonate


def test_block_sight_adapter_uses_the_real_world_block_ray_trace():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    sight = java_method_body(source, "private boolean hasProjectileBlockLineOfSight(")
    assert "rayTraceBlocks(" in sight
    assert "FluidCollisionMode.NEVER" in sight
    assert "distance" in sight


def test_wall_blocks_player_aoe_but_clear_path_still_admits_eligible_player(tmp_path):
    policy = DOMAIN / "WaveProjectileTargetPolicy.java"
    regression = ROOT / "tests/WaveProjectileTargetPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "WaveProjectileTargetPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_projectile_leases_cover_each_admitted_wave_launch_group():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    launch = java_method_body(source, "private boolean launchPhysicalEliteAbility(")
    shoot = java_method_body(source, "public void onEventSkeletonShootBow(")
    side_arrows = java_method_body(source, "private void spawnPendingSkeletonSideArrows(")
    cloud = java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")
    arrow_volley = java_method_body(source, "private void riftArrowVolley(LivingEntity caster, Player target, String spellId,")
    sphere = java_method_body(source, "private void spawnRitualSphereProjectileVolley(")
    guard_web = java_method_body(source, "private boolean spawnRitualGuardWebProjectile(")
    diagnostics = source[source.index("RUNTIME_DIAGNOSTICS ownedLiving="):
                         source.index("RUNTIME_DIAGNOSTICS ownedLiving=") + 500]

    assert "reserveWaveProjectileGroup(" in launch
    assert "reserveNamedFiveArrowRainGroup(groupId)" in launch
    assert "reserveWaveProjectileGroup(" in shoot
    assert "reserveWaveProjectileGroup(" in arrow_volley
    assert side_arrows.index("tagEventArrowVolley(extra, pending.volleyId())") < side_arrows.index(
        "configureEventSkeletonArrow(extra,"
    )
    assert "clearWaveProjectileGroup(pending.volleyId()" in side_arrows
    assert "transferWaveProjectile(projectileId, cloud.getUniqueId())" in cloud
    assert "reserveWaveProjectileGroup(" in sphere
    assert "reserveWaveProjectileGroup(" in guard_web
    assert "activeProjectileGroups=" in diagnostics
    assert "activeWaveProjectiles=" in diagnostics
    assert "waveProjectileCap=" in diagnostics


def test_each_child_and_cloud_releases_or_transfers_its_physical_lease_once():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    resolve = java_method_body(source, "private void resolveEliteProjectileImpact(")
    clear_elite = java_method_body(source, "private void clearEliteProjectileRuntime(")
    clear_arrows = java_method_body(source, "private void cleanupEventArrow(")
    remove_entity = java_method_body(source, "public void onOwnedEntityRemove(")
    full_clear = java_method_body(source, "private void clearEliteAbilityEntities(")

    assert "waveProjectileAdmission.releaseProjectile(projectileId)" in resolve
    assert "waveProjectileAdmission.releaseProjectile(projectileId)" in clear_elite
    assert "waveProjectileAdmission.releaseProjectile(arrowId)" in clear_arrows
    assert "waveProjectileAdmission.releaseProjectile(removedId)" in remove_entity
    assert "projectileIds.addAll(eliteProjectileImpactScheduled)" in full_clear
    assert "clearEliteProjectileRuntime(entityId" in full_clear


def test_cancelled_elite_impact_task_clears_its_admission_through_the_phase_scope():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    impact = java_method_body(source, "public void onEliteProjectileHit(")
    scope = RESOURCE_SCOPE.read_text(encoding="utf-8")
    cleanup_task = java_method_body(scope, "private void cleanupTask(")

    assert "registerEncounterTask(impactTask, () -> clearEliteProjectileRuntime(projectileId," in impact
    assert '"elite-projectile-impact-cancelled", false));' in impact
    assert "if (cancellationCompleted)" in cleanup_task
    assert "cleanup.run();" in cleanup_task


def test_all_bounded_volley_adapters_roll_back_spawn_and_configuration_failures():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    bow = java_method_body(source, "public void onEventSkeletonShootBow(")
    side_arrows = java_method_body(source, "private void spawnPendingSkeletonSideArrows(")
    arrow_volley = java_method_body(
        source,
        "private void riftArrowVolley(LivingEntity caster, Player target, String spellId,\n                                 SkeletonCombatPolicy.ArrowProfile profile, Location committedAim)",
    )
    guard_web = java_method_body(source, "private boolean spawnRitualGuardWebProjectile(")
    elite_launch = java_method_body(source, "private boolean launchPhysicalEliteAbility(")
    cloud = java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")

    for adapter, reason in (
        (bow, "skeleton-arrow-spawn-failed"),
        (side_arrows, "skeleton-side-arrow-spawn-failed"),
        (arrow_volley, "event-arrow-volley-spawn-failed"),
        (guard_web, "ritual-guard-web-spawn-failed"),
        (elite_launch, "elite-projectile-configure-failed"),
    ):
        assert "catch (RuntimeException failure)" in adapter
        assert "clearWaveProjectileGroup(" in adapter
        assert reason in adapter
    assert "catch (RuntimeException failure)" in cloud
    assert "clearEliteProjectileRuntime(cloud.getUniqueId()" in cloud
    assert "dragon-breath-cloud-spawn-failed" in cloud

    assert "spawnedSideArrows" in side_arrows
    assert "for (Arrow extra : spawnedSideArrows)" in side_arrows
    assert "cleanupEventArrow(" in side_arrows
    assert "List<Arrow> spawned = new ArrayList<>();" in arrow_volley
    assert "for (Arrow arrow : spawned)" in arrow_volley
    assert "cleanupEventArrow(arrow.getUniqueId())" in arrow_volley
    assert "Arrow arrow = null;" in guard_web
    assert "if (arrow != null)" in guard_web
    assert "if (arrow.isValid()) arrow.remove();" in guard_web


def test_partial_spawn_rollback_releases_full_capacity_for_retry(tmp_path):
    policy = DOMAIN / "WaveProjectileAdmissionController.java"
    pressure = DOMAIN / "PressureBudgetController.java"
    regression = ROOT / "tests/WaveProjectileAdmissionControllerTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(pressure), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "WaveProjectileAdmissionControllerTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_player_targeted_elite_spells_and_arrows_never_apply_to_mobs():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    cloud = java_method_body(source, "private void tickEliteDragonBreathCloud(")
    explosive = java_method_body(source, "private void detonateExplosiveArrow(")
    impact = java_method_body(source, "public void onEliteProjectileDamage(")
    void_burst = java_method_body(source, "private void applyEliteVoidBurst(")

    assert "for (Player player" in cloud
    assert "for (Player player" in explosive
    assert "if (!(event.getEntity() instanceof Player player))" in impact
    assert "nearby instanceof Player player" in void_burst
    assert "LivingEntity nearby" not in void_burst


def test_projectile_group_policy_enforces_live_slots_and_child_cleanup(tmp_path):
    policy = DOMAIN / "WaveProjectileAdmissionController.java"
    pressure = DOMAIN / "PressureBudgetController.java"
    regression = ROOT / "tests/WaveProjectileAdmissionControllerTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(pressure), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "WaveProjectileAdmissionControllerTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr
