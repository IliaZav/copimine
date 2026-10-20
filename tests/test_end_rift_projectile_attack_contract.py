"""Source-boundary regressions for End Rift elite and skeleton projectiles."""
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _java_method_body(source: str, signature: str) -> str:
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
    raise AssertionError(f"unterminated Java method: {signature}")


def test_arrow_rain_releases_from_the_committed_mark_without_homing():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    launch = _java_method_body(source, "private boolean launchPhysicalEliteAbility(")
    rain = launch[
        launch.index("if (spell == EndRiftAiPolicy.MiniBossSpell.ARROW_RAIN)"):
        launch.index("EliteAbilityPolicy.Ability ability = switch (spell)")
    ]

    assert "mark == null" in rain
    assert "Location center = mark.clone();" in rain
    assert "target.getLocation()" not in rain
    assert "center.toVector().subtract(origin.toVector()).normalize()" in rain
    assert "setVelocity(" not in rain


def test_void_burst_damage_event_is_zeroed_for_collision_only_aoe():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    damage = _java_method_body(source, "public void onEliteProjectileDamage(")
    impact = _java_method_body(source, "private void resolveEliteProjectileImpact(")

    assert "EliteAbilityPolicy.Ability.VOID_BURST" in damage
    assert "event.setDamage(0.0D)" in damage
    assert "EliteAbilityPolicy.Ability.VOID_BURST" in impact
    assert "applyEliteVoidBurst(shooter, impact, impactGate)" in impact
    assert "blockImpact" in impact
    assert "eliteProjectileDamageAllowed.contains(projectileId)" in impact


def test_void_burst_aoe_is_bounded_player_only_and_uses_normal_damage_events():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    burst = _java_method_body(source, "private void applyEliteVoidBurst(")

    assert "VOID_BURST_DAMAGE_RADIUS_BLOCKS" in burst
    assert "world.getNearbyEntities(impact," in burst
    assert "nearby instanceof Player player" in burst
    assert "distanceSquared(impact)" in burst
    assert "isWaveTargetAllowed(shooter, player)" in burst
    assert "double damage = EliteAbilityPolicy.directDamage(EliteAbilityPolicy.Ability.VOID_BURST);" in burst
    assert "player.damage(Math.min(amount, damage), shooter)" in burst
    assert "rayTraceBlocks(impact," in burst
    assert "createExplosion(" not in burst
    assert "setFire" not in burst
    assert "LivingEntity nearby" not in burst


def test_elite_projectiles_must_belong_to_the_current_event_and_generation():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    valid = _java_method_body(source, "private boolean isEliteAbilityProjectile(")

    assert "ownedBySession(projectile, eventId, generation)" in valid
    assert "ownedBySession(shooter, eventId, generation)" in valid
    assert "activeEliteProjectileExpiresAtTick.get(projectile.getUniqueId())" in valid
    resolver = _java_method_body(source, "private void resolveEliteProjectileImpact(")
    assert "impactEventId" in resolver and "impactGeneration" in resolver
    assert "Objects.equals(eventId, impactEventId)" in resolver
    assert "collisionEvent.isCancelled()" in resolver
    assert "damageEvent.isCancelled()" in resolver
    cloud_tick = _java_method_body(source, "private void tickEliteDragonBreathCloud(")
    assert "activeEliteProjectileExpiresAtTick.get(cloudId)" in cloud_tick
    assert "ownedBySession(cloud, eventId, generation)" in cloud_tick
    assert "ownedBySession(shooter, eventId, generation)" in cloud_tick
    assert "generation" in cloud_tick and "eventId" in cloud_tick
    cleanup = _java_method_body(source, "private void clearEliteAbilityEntities(")
    assert "isTaggedEliteAbilityEntity(entity)" in cleanup


def test_delayed_elite_projectile_impact_rechecks_the_active_wave():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    valid = _java_method_body(source, "private boolean isEliteAbilityProjectile(")
    resolver = _java_method_body(source, "private void resolveEliteProjectileImpact(")

    assert "activeWave != wave" in valid
    assert "isWaveAiCombatPhase(wave)" in valid
    assert "activeWave != wave" in resolver
    assert "isWaveAiCombatPhase(wave)" in resolver


def test_event_projectiles_pass_through_mobs_and_reject_safe_zone_players():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    collision = _java_method_body(source, "public void onEventProjectileCollide(")
    target_gate = _java_method_body(source, "private boolean isWaveTargetAllowed(")
    elite_damage = _java_method_body(source, "public void onEliteProjectileDamage(")
    elite_hit = _java_method_body(source, "public void onEliteProjectileHit(")
    cloud = _java_method_body(source, "private void tickEliteDragonBreathCloud(")

    assert "ProjectileCollideEvent" in source
    assert "event.getCollidedWith() instanceof Player" in collision
    assert "if (!(event.getCollidedWith() instanceof Player player))" in collision
    assert "event.setCancelled(true)" in collision
    assert "readInt(entity, keyWave, activeWave) == 5 && isCurrentSafeZonePlayer(player)" in target_gate
    assert "if (!(event.getEntity() instanceof Player player))" in elite_damage
    assert "event.setCancelled(true)" in elite_hit
    assert "isWaveTargetAllowed(shooter, player)" in cloud


def test_event_arrow_player_collision_requires_current_eligible_target_before_hit():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    collision = _java_method_body(source, "public void onEventProjectileCollide(")

    assert "if (eventArrow)" in collision
    assert "ownedBySession(projectile, eventId, generation)" in collision
    assert "ownedBySession(shooter, eventId, generation)" in collision
    assert "isWaveTargetAllowed(shooter, player)" in collision
    assert "ritualProjectileTargetAllowed(eventArrowEntity, player)" in collision
    assert "if (!isEliteAbilityProjectile(projectile))" in collision


def test_ritual_arrows_bypass_only_the_ordinary_caster_target_gate():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    collision = _java_method_body(source, "public void onEventProjectileCollide(")
    ordinary_gate = _java_method_body(source, "private boolean isWaveTargetAllowed(")
    ritual_gate = _java_method_body(source, "private boolean ritualProjectileTargetAllowed(")

    assert "RitualTargetPolicy.projectileTargetAllowed(" in collision
    assert "isRitualProjectile(eventArrowEntity)" in collision
    assert "ritualProjectileTargetAllowed(eventArrowEntity, player)" in collision
    assert "return ritualCasterCanAttack(entity) && ritualTargetAllowed(entity, player);" in ordinary_gate
    assert "isFreeRitualTarget(player)" in ritual_gate


def test_wave4_obelisk_fireball_passes_through_only_nonparticipants_in_current_session():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    collision = _java_method_body(source, "public void onEventProjectileCollide(")

    assert "RiftFireballCollisionPolicy.shouldPassThroughNonParticipantCollision(" in collision
    assert "projectile instanceof LargeFireball" in collision
    assert "fireballState.isWave4Obelisk()" in collision
    assert "activeWave == 4" in collision
    assert "fireballState.generation() == generation" in collision
    assert "ownedBySession(projectile, eventId, generation)" in collision
    assert "isActiveArenaParticipant(player)" in collision


def test_wave4_reflected_fireball_entity_hit_does_not_consume_objective_shot():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    impact = _java_method_body(
        source,
        "private void handleRiftFireballImpact(LargeFireball fireball, Entity hitEntity,",
    )
    hit_event = _java_method_body(source, "public void onRiftFireballHit(")

    guard = impact.index("RiftFireballCollisionPolicy.shouldPassThroughNonParticipantCollision(")
    assert "hitEntity != null" in impact
    assert "state.isWave4Obelisk()" in impact
    assert "activeWave == 4 && phase == EventPhase.WAVE_4" in impact
    assert "state.generation() == generation" in impact
    assert "ownedBySession(fireball, eventId, generation)" in impact
    assert "isActiveArenaParticipant(player)" in impact
    assert guard < impact.index("return;", guard) < impact.index('"current-reflected-miss"')
    assert hit_event.index("event.setCancelled(true)") < hit_event.index("handleRiftFireballImpact(")


def test_dragon_breath_cloud_is_ticked_without_a_vanilla_effect_payload():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    state_tick = _java_method_body(source, "private void tickEliteAbilityState(")
    cloud_tick = _java_method_body(source, "private void tickEliteDragonBreathCloud(")
    damage = _java_method_body(source, "private void scheduleEliteDragonBreathCloudDamage(")
    cleanup = _java_method_body(source, "private void clearEliteProjectileRuntime(")

    assert "tickEliteDragonBreathCloud(cloud)" in state_tick
    assert "for (Player player : Bukkit.getOnlinePlayers())" in cloud_tick
    assert "scheduleEliteDragonBreathCloudDamage(cloud, shooter, player, wave)" in cloud_tick
    assert "import org.bukkit.event.entity.AreaEffectCloudApplyEvent;" not in source
    assert "DragonBreathCloudPolicy.admitHit(" in damage
    assert "DragonBreathCloudPolicy.shouldApplyEffects(" in damage
    assert "isWaveTargetAllowed(liveShooter, livePlayer)" in damage
    assert "distanceSquared > radius * radius" in damage
    assert "livePlayer.damage(EliteAbilityPolicy.DRAGON_CLOUD_AREA_DAMAGE, liveShooter)" in damage
    assert "DragonBreathCloudPolicy.releaseHit(" in damage
    assert "DragonBreathCloudPolicy.releaseCloud(eliteDragonBreathCloudHits, projectileId)" in cleanup


def test_event_arrow_slowness_requires_an_accepted_nested_damage_event():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    damage = _java_method_body(
        source,
        "private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell)",
    )
    outcome = _java_method_body(source, "public void onPendingPlayerDamageOutcome(")

    assert "pendingPlayerDamageOutcome" in damage
    assert "DragonBreathCloudPolicy.shouldApplyEffects(" in damage
    assert "damageOutcome.isCancelled()" in damage
    assert "if (debuffTicks > 0)" in damage
    assert "pending.outcome = event" in outcome
    assert "pending.playerId.equals(player.getUniqueId())" in outcome


def test_explosive_arrow_volley_limits_repeat_damage_to_one_hit_per_target(tmp_path):
    source = SOURCE_PATH.read_text(encoding="utf-8")
    shoot = _java_method_body(source, "public void onEventSkeletonShootBow(")
    detonate = _java_method_body(source, "private void detonateExplosiveArrow(")
    volley_id = _java_method_body(source, "private UUID eventArrowVolleyId(")
    assert "UUID volleyId = arrow.getUniqueId();" in shoot
    assert "volleyId, eventId, generation" in shoot
    spawn = _java_method_body(source, "private void spawnPendingSkeletonSideArrows(")
    assert "tagEventArrowVolley(extra, pending.volleyId())" in spawn
    assert "eventArrowVolleyId(arrow)" in detonate
    assert "ExplosiveVolleyPolicy.admitTarget(explosiveArrowLastHitMillis" in detonate
    assert "volleyId, player.getUniqueId(), System.currentTimeMillis()" in detonate
    assert "keyArrowVolley" in volley_id and "UUID.fromString(encoded)" in volley_id

    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ExplosiveVolleyPolicy.java"
    regression = ROOT / "tests/ExplosiveVolleyPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "ExplosiveVolleyPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_untracked_or_old_generation_event_arrows_fail_closed():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    valid = _java_method_body(source, "private boolean isEventArrow(")
    stale = _java_method_body(source, "public void onStaleEventArrowDamage(")

    assert "ownedBySession(entity, eventId, generation)" in valid
    assert "EVENT_KIND_PROJECTILE.equals(readString(arrow, keyKind))" in stale
    assert "event.setCancelled(true)" in stale
    assert "cleanupEventArrow(arrow.getUniqueId())" in stale
    cleanup = _java_method_body(source, "private void cleanupEventArrow(")
    assert "candidate instanceof Arrow" in cleanup
    assert "EVENT_KIND_PROJECTILE.equals(readString(candidate, keyKind))" in cleanup


def test_triple_arrow_side_projectiles_spawn_only_after_bow_event_is_accepted():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    shoot = _java_method_body(source, "public void onEventSkeletonShootBow(")
    outcome = _java_method_body(source, "public void onEventSkeletonShootBowOutcome(")

    assert "spawnArrow(" not in shoot
    assert "pendingSkeletonShotSideArrows.put(arrow.getUniqueId()" in shoot
    assert "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)" in source[
        source.index("public void onEventSkeletonShootBowOutcome(") - 120:
        source.index("public void onEventSkeletonShootBowOutcome(")
    ]
    assert "event.isCancelled()" in outcome
    assert "pendingSkeletonShotSideArrows.remove" in outcome
    assert "cleanupEventArrow(originalArrowId)" in outcome
    assert "spawnPendingSkeletonSideArrows(originalArrowId, pending)" in outcome
    assert outcome.index("event.isCancelled()") < outcome.index(
        "spawnPendingSkeletonSideArrows(originalArrowId, pending)"
    )
    cancellation_branch = outcome[
        outcome.index("if (event.isCancelled())"):
        outcome.index("spawnPendingSkeletonSideArrows(originalArrowId, pending)")
    ]
    assert "cleanupEventArrow(originalArrowId)" in cancellation_branch
    assert "return;" in cancellation_branch
    assert "runTaskLater(this, () ->" in outcome
    cleanup = _java_method_body(source, "private void cleanupEventArrow(")
    assert "PendingSkeletonSideArrowShot pendingShot = pendingSkeletonShotSideArrows.remove(arrowId)" in cleanup
    assert "releaseUnspawnedReservations(pendingShot.volleyId())" in cleanup

    group_cleanup = _java_method_body(source, "private void clearWaveProjectileGroup(")
    assert "pendingSkeletonShotSideArrows.remove(groupId)" in group_cleanup
    assert "waveProjectileAdmission.releaseGroup(groupId)" in group_cleanup
    assert "cleanupEventArrow(childId)" in group_cleanup
    assert "clearEliteProjectileRuntime(childId" in group_cleanup
    assert "entity.remove()" in group_cleanup

    spawn = _java_method_body(source, "private void spawnPendingSkeletonSideArrows(")
    assert "Objects.equals(eventId, pending.eventId())" in spawn
    assert "generation != pending.generation()" in spawn
    assert "isLiveOwnedEntity(pending.shooterId())" in spawn
    assert "ownedBySession(skeleton, pending.eventId(), pending.generation())" in spawn
    assert "world.spawnArrow(origin," in spawn


def test_native_bow_arrow_is_tracked_only_after_paper_inserts_the_accepted_projectile():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    shoot = _java_method_body(source, "public void onEventSkeletonShootBow(")
    outcome = _java_method_body(source, "public void onEventSkeletonShootBowOutcome(")
    spawn = _java_method_body(source, "private void spawnPendingSkeletonSideArrows(")
    configure = _java_method_body(source, "private boolean configureEventSkeletonArrow(")

    assert "prepareEventSkeletonArrow(" in shoot
    assert "configureEventSkeletonArrow(" not in shoot
    assert "trackEventArrow(" not in shoot
    assert "pendingSkeletonShotSideArrows.put(arrow.getUniqueId()" in shoot
    assert "pending.arrowCount()" in spawn
    assert "Bukkit.getEntity(arrowId)" in spawn
    assert "trackEventArrow(mainArrow)" in spawn
    assert spawn.index("trackEventArrow(mainArrow)") < spawn.index("pending.arrowCount() <= 1")
    assert "prepareEventSkeletonArrow(arrow," in configure
    assert "return trackEventArrow(arrow);" in configure
    assert "runTaskLater(this, () ->" in outcome


def test_replaced_bow_projectile_releases_original_pending_arrow_reservation():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    shoot = _java_method_body(source, "public void onEventSkeletonShootBow(")
    outcome = _java_method_body(source, "public void onEventSkeletonShootBowOutcome(")
    cleanup = _java_method_body(source, "private void cleanupEventArrow(")

    # The MONITOR callback must resolve the projectile prepared at HIGHEST from
    # the event itself; event.getProjectile() may now refer to a replacement.
    assert "pendingSkeletonShotBowEventArrowIds.put(event, arrow.getUniqueId())" in shoot
    assert "pendingSkeletonShotBowEventArrowIds.remove(event)" in outcome
    assert outcome.index("pendingSkeletonShotBowEventArrowIds.remove(event)") < outcome.index(
        "event.getProjectile()"
    )
    assert "originalArrowId" in outcome
    assert "event.getProjectile() != originalArrow" in outcome

    replacement_start = outcome.index("event.getProjectile() != originalArrow")
    replacement_end = outcome.index("return;", replacement_start)
    replacement_cleanup = outcome[replacement_start:replacement_end]
    assert "pendingSkeletonShotSideArrows.remove(originalArrowId)" in replacement_cleanup
    assert "clearWaveProjectileGroup(pending.volleyId()" in replacement_cleanup
    assert "cleanupEventArrow(originalArrowId)" in replacement_cleanup
    assert "releaseUnspawnedReservations(pendingShot.volleyId())" in cleanup


def test_manual_skeleton_arrow_payloads_wait_for_final_damage_event_outcome():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    skeleton_damage = _java_method_body(source, "public void onEventSkeletonArrowDamage(")
    custom_damage = _java_method_body(source, "private void onCustomEventArrowDamage(")
    outcome = _java_method_body(source, "public void onEventArrowDamageOutcome(")

    assert "deferEventArrowDamageEffect(event, arrow," in skeleton_damage
    assert skeleton_damage.index("deferEventArrowDamageEffect(event, arrow,") < skeleton_damage.index(
        "detonateExplosiveArrow(arrow,"
    )
    assert skeleton_damage.index("deferEventArrowDamageEffect(event, arrow,") < skeleton_damage.index(
        "livePlayer.addPotionEffect("
    )
    assert "event.setDamage(0.0D)" in custom_damage
    assert "deferEventArrowDamageEffect(event, arrow," in custom_damage
    assert "event.isCancelled()" in outcome
    assert "Bukkit.getScheduler().runTaskLater(this, () ->" in outcome
    assert "if (!event.isCancelled())" in outcome


def test_accepted_arrow_payload_survives_native_hit_removal_until_deferred_effect_finishes():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    removed = _java_method_body(source, "public void onOwnedEntityRemove(")
    outcome = _java_method_body(source, "public void onEventArrowDamageOutcome(")
    cleanup = _java_method_body(source, "private void cleanupEventArrow(")

    assert "hasDeferredEventArrowDamage(entity.getUniqueId())" in removed
    tracking = _java_method_body(source, "private boolean hasDeferredEventArrowDamage(")
    assert "pendingEventArrowDamageEffects.containsKey(arrowId)" in tracking
    assert "scheduledEventArrowDamageEffects.contains(arrowId)" in tracking
    assert "activeEventArrowAges.remove(entity.getUniqueId())" in removed
    assert "scheduledEventArrowDamageEffects.add(arrowId)" in outcome
    assert outcome.index("scheduledEventArrowDamageEffects.add(arrowId)") < outcome.index(
        "runTaskLater(this, () ->"
    )
    assert "effect.run();" in outcome
    assert "cleanupEventArrow(arrowId);" in outcome
    assert "activeEventArrowAges.remove(arrowId)" in cleanup


def test_elite_projectile_display_uses_owned_teleport_permit():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    visual = _java_method_body(source, "private void tickEliteProjectileVisuals(")

    assert "teleportCombatEntity(display, position)" in visual
    assert "display.teleport(position)" not in visual


def test_dragon_breath_cloud_uses_the_particle_data_type_required_by_paper():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    cloud = _java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")

    assert "cloud.setParticle(Particle.DRAGON_BREATH, 1.0F);" in cloud
    assert "cloud.setParticle(Particle.DRAGON_BREATH);" not in cloud
    assert "transferWaveProjectile(projectileId, cloud.getUniqueId())" in cloud
    assert "return true;" in cloud


def test_dragon_breath_allows_only_one_active_cloud_per_caster():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    spawn = _java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")
    cleanup = _java_method_body(source, "private void clearEliteProjectileRuntime(")

    assert "DragonBreathCloudPolicy.claimCloud(activeEliteDragonBreathCloudsByCaster" in spawn
    assert "activeEliteDragonBreathCloudsByCaster" in cleanup
    assert "DragonBreathCloudPolicy.releaseCloud(activeEliteDragonBreathCloudsByCaster" in cleanup


def test_dragon_breath_outcome_policy_executes_cancellation_cases(tmp_path):
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/DragonBreathCloudPolicy.java"
    regression = ROOT / "tests/DragonBreathCloudPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "DragonBreathCloudPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_dragon_breath_impact_does_not_add_direct_damage_before_cloud_area_hit(tmp_path):
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EliteAbilityPolicy.java"
    regression = ROOT / "tests/EliteAbilityPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "EliteAbilityPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_spider_root_cleanup_policy_executes_player_isolation_cases(tmp_path):
    policies = [
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BlackFogEffectLeasePolicy.java",
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SpiderEffectLeasePolicy.java",
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SpiderRootControlPolicy.java",
    ]
    regression = ROOT / "tests/SpiderRootControlPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), *(str(policy) for policy in policies), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "SpiderRootControlPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_dragon_breath_cloud_has_no_vanilla_payload_and_leases_blindness_after_admitted_damage():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    cloud_tick = _java_method_body(source, "private void tickEliteDragonBreathCloud(")
    delayed = _java_method_body(source, "private void scheduleEliteDragonBreathCloudDamage(")
    spawn = _java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")

    assert "DragonBreathCloudPolicy.shouldSchedulePlayerHit(" in cloud_tick
    assert "scheduleEliteDragonBreathCloudDamage(cloud, shooter, player, wave)" in cloud_tick
    assert "DragonBreathCloudPolicy.admitHit" in delayed
    assert "pendingPlayerDamageOutcome" in delayed
    assert "damageOutcome.isCancelled()" in delayed
    assert "applySpiderPotionLease(livePlayer, PotionEffectType.BLINDNESS" in delayed
    assert "cloud.setBasePotionType(PotionType.WATER)" in spawn
    assert "addCustomEffect(" not in spawn


def test_wave5_fog_start_removes_dragon_clouds_and_cancels_pending_hits():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    fog = _java_method_body(source, "private void tickBlackFogObjective(")
    cloud_tick = _java_method_body(source, "private void tickEliteDragonBreathCloud(")
    delayed_damage = _java_method_body(source, "private void scheduleEliteDragonBreathCloudDamage(")
    assert "private void clearDragonBreathCloudsForFog(" in source
    cleanup = _java_method_body(source, "private void clearDragonBreathCloudsForFog(")

    fog_transition = fog[fog.index("blackFogPhase = BlackFogPhase.FOG;"):]
    assert "clearDragonBreathCloudsForFog();" in fog_transition
    assert "blackFogPhase == BlackFogPhase.FOG" in cloud_tick
    assert "clearEliteProjectileRuntime(cloudId" in cloud_tick
    assert "blackFogPhase == BlackFogPhase.FOG" in delayed_damage
    assert "instanceof AreaEffectCloud" in cleanup
    assert "EliteAbilityPolicy.Ability.DRAGON_BREATH.name()" in cleanup
    assert "clearEliteProjectileRuntime(cloudId" in cleanup
    assert "cloud.remove()" in cleanup


def test_dragon_breath_player_target_policy_executes_player_world_range_and_eligibility_cases(tmp_path):
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/DragonBreathCloudPolicy.java"
    regression = ROOT / "tests/DragonBreathCloudPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "DragonBreathCloudPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr


def test_root_player_boundaries_clear_reapplication_cooldown_for_that_player():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    quit_handler = _java_method_body(source, "public void onPlayerQuit(")
    death_handler = _java_method_body(source, "public void onPlayerDeath(")
    world_handler = _java_method_body(source, "public void onPlayerChangedWorld(")

    for handler in (quit_handler, death_handler, world_handler):
        assert "SpiderRootControlPolicy.clearPlayerState(" in handler


def test_spider_weaving_block_interference_stays_inside_the_protected_arena():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    handler = _java_method_body(source, "public void onArenaEntityChangeBlock(")

    assert "isProtectedEventLocation(event.getBlock().getLocation())" in handler
    assert "ownsActiveSpiderWeaving(player)" not in handler


def test_expired_event_effect_lease_restores_original_then_can_be_reapplied():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    lease = _java_method_body(source, "private void applySpiderPotionLease(")

    assert "result.action() == SpiderEffectLeasePolicy.ApplyAction.FOREIGN_EFFECT_CHANGED" in lease
    assert "if (player.getPotionEffect(type) == null)" in lease
    assert "lease.original()" in lease
    assert "BlackFogEffectLeasePolicy.remainingDuration(" in lease
    assert "SpiderEffectLeasePolicy.applyOrRefresh(" in lease


def test_custom_manual_arrow_hits_zero_native_damage_and_wait_for_acceptance():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    custom_damage = _java_method_body(source, "private void onCustomEventArrowDamage(")
    outcome = _java_method_body(source, "public void onEventArrowDamageOutcome(")
    manual_hit = custom_damage[custom_damage.index("int debuffTicks = webProjectile"):]

    assert "event.setDamage(0.0D)" in manual_hit
    assert "deferEventArrowDamageEffect(event, arrow," in manual_hit
    assert manual_hit.index("event.setDamage(0.0D)") < manual_hit.index(
        "deferEventArrowDamageEffect(event, arrow,"
    ) < manual_hit.index("livePlayer.damage(")
    assert outcome.index("if (event.isCancelled())") < outcome.index(
        "scheduledEventArrowDamageEffects.add(arrowId)"
    )


def test_explosive_skeleton_arrow_cannot_damage_non_player_targets():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    skeleton_damage = _java_method_body(source, "public void onEventSkeletonArrowDamage(")
    explosive = skeleton_damage[
        skeleton_damage.index("if (ARROW_SPELL_EXPLOSIVE.equals(spell))"):
        skeleton_damage.index("if (ARROW_SPELL_POISON_NAUSEA.equals(spell))")
    ]

    assert "if (!(event.getEntity() instanceof Player player)" in explosive
    assert "event.setCancelled(true)" in explosive
    assert "cleanupEventArrow(arrow.getUniqueId())" in explosive


def test_wave_entity_cleanup_releases_owned_spider_effect_leases():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    cleanup = _java_method_body(
        source, "private boolean clearWaveEntities(boolean includeRewards, boolean resetWaveObjectiveState)"
    )

    assert "releaseAllSpiderEffects()" in cleanup


def test_spider_root_locks_horizontal_motion_but_preserves_vertical_control():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    movement = _java_method_body(source, "public void onEliteSpiderRootMove(")

    assert "held.setY(to.getY())" in movement
    assert "held.setYaw(to.getYaw())" in movement
    assert "held.setPitch(to.getPitch())" in movement
    assert "Vector currentVelocity = player.getVelocity()" in movement
    assert "new Vector(0.0D, currentVelocity.getY(), 0.0D)" in movement
    assert "player.setVelocity(new Vector())" not in movement


def test_spider_root_has_per_player_reapplication_immunity():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    apply_web = _java_method_body(source, "private void applyEliteSpiderWeb(")
    state_tick = _java_method_body(source, "private void tickEliteAbilityState(")

    assert "SpiderRootControlPolicy.canApply(eventTickCounter," in apply_web
    assert "rootedPlayerNextApplyTick" in apply_web
    assert "SpiderRootControlPolicy.nextAllowedTick(eventTickCounter)" in apply_web
    assert "rootedPlayerNextApplyTick" in state_tick


def test_each_elite_projectile_ability_gets_a_distinct_owned_display_model():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    launch = _java_method_body(source, "private boolean launchPhysicalEliteAbility(")
    visual = _java_method_body(source, "private void attachEliteAbilityVisual(")
    state_tick = _java_method_body(source, "private void tickEliteProjectileVisuals(")
    cleanup = _java_method_body(source, "private void clearEliteAbilityEntities(")
    removed = _java_method_body(source, "public void onOwnedEntityRemove(")

    assert "attachEliteAbilityVisual(projectile, ability, wave," in launch
    for ability, model in (
        ("WEB_SHOT", "MODEL_ELITE_WEB_SHOT"),
        ("RIFT_BOLT", "MODEL_ELITE_RIFT_BOLT"),
        ("DRAGON_BREATH", "MODEL_ELITE_DRAGON_BREATH"),
        ("VOID_BURST", "MODEL_ELITE_VOID_BURST"),
    ):
        assert f"case {ability} -> {model}" in visual
    assert "overlayItem(modelData" in visual
    assert "activeEliteProjectileVisuals.put(projectile.getUniqueId()" in visual
    assert "Location position = projectile.getLocation()" in state_tick
    assert "teleportCombatEntity(display, position)" in state_tick
    assert "display.teleport(position)" not in state_tick
    assert "clearEliteProjectileRuntime(removedId" in removed
    assert "activeEliteProjectileVisuals" in cleanup


def test_saved_elite_ability_entities_without_runtime_ttl_are_removed_on_chunk_load():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    load = _java_method_body(source, "public void onPersistedEliteAbilityEntitiesLoad(")
    stale_cleanup = _java_method_body(source, "private void removeStaleEliteAbilityEntity(")

    assert "isTaggedEliteAbilityEntity(entity)" in load
    assert "!activeEliteProjectileExpiresAtTick.containsKey(entity.getUniqueId())" in load
    assert "if (isTaggedEliteProjectileVisual(entity))" in load
    assert "if (isTaggedEliteAbilityEntity(entity)" in load
    assert "!ownedByEvent(entity, eventId)" in load
    assert "isTaggedEliteAbilityEntity(entity) && ownedByEvent(entity, eventId)" not in load
    assert "isTaggedEliteProjectileVisual(entity) && ownedByEvent(entity, eventId)" not in load
    assert "removeStaleEliteAbilityEntity(entity," in load
    assert "clearEliteProjectileRuntime(entityId, reason, false)" in stale_cleanup
    assert "entity.remove()" in stale_cleanup
    clear_runtime = _java_method_body(source, "private void clearEliteProjectileRuntime(")
    assert "eliteProjectileDamageAllowed.remove(projectileId)" in clear_runtime
    assert "eliteProjectileDamageBlocked.remove(projectileId)" in clear_runtime
    assert "removeEliteProjectileVisual(projectileId, reason)" in clear_runtime


def test_skeleton_arrow_damage_has_no_critical_bonus_and_pickup_is_disabled():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    configure = _java_method_body(source, "private boolean configureEventSkeletonArrow(")
    prepare = _java_method_body(source, "private void prepareEventSkeletonArrow(")
    assert "arrow.setDamage(damage)" in prepare
    assert "arrow.setCritical(false)" in prepare
    assert "PickupStatus.DISALLOWED" in prepare
    assert "prepareEventSkeletonArrow(arrow," in configure
    assert "return trackEventArrow(arrow);" in configure


def test_void_burst_policy_executes_collision_and_target_fakes(tmp_path):
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EliteProjectileImpactPolicy.java"
    regression = ROOT / "tests/EliteProjectileImpactPolicyTest.java"
    compile_result = subprocess.run(
        ["javac", "-d", str(tmp_path), str(policy), str(regression)],
        capture_output=True,
        text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ["java", "-cp", str(tmp_path), "EliteProjectileImpactPolicyTest"],
        capture_output=True,
        text=True,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr
