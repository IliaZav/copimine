"""Regression contracts for Wave 6 ritual projectile provenance.

There is no Bukkit harness in the pure test suite, so this contract checks the
server boundary where a projectile's immutable PDC provenance is translated
into recipient eligibility.  The important regression is that a dead ritual
shooter cannot turn an already-fired arrow into a generic active-player hit.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def read_source() -> str:
    return SOURCE.read_text(encoding="utf-8")


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = source.find("\n    @EventHandler", start + len(signature))
    return source[start:end]


def test_dead_ritual_shooter_keeps_prisoner_out_of_all_arrow_recipients() -> None:
    source = read_source()

    assert "private NamespacedKey keyRitualProjectile" in source
    assert 'keyRitualProjectile = new NamespacedKey(this, "end_event_ritual_projectile")' in source

    marker_body = method_body(source, "private boolean isRitualProjectile(Arrow arrow)")
    assert "getPersistentDataContainer()" in marker_body
    assert "keyRitualProjectile" in marker_body
    assert "PersistentDataType.BYTE" in marker_body

    target_body = method_body(
        source, "private boolean ritualProjectileTargetAllowed(Arrow arrow, Player player)"
    )
    assert "isRitualProjectile(arrow)" in target_body
    assert "isFreeRitualTarget(player)" in target_body
    assert "isCurrentRitualCaster" not in target_body
    assert "isCurrentRitualGuard" not in target_body

    recipients_body = method_body(
        source, "private List<Player> ritualFreeTargetsForProjectile(Arrow arrow)"
    )
    assert "isRitualProjectile(arrow)" in recipients_body
    assert "isRitualProjectileSpell(readString(arrow, keyArrowSpell))" in recipients_body
    assert "ritualFreeTargets(activeWaveParticipants())" in recipients_body
    participants_body = method_body(source, "private List<Player> activeWaveParticipants()")
    assert "Bukkit.getOnlinePlayers().stream()" in participants_body
    assert ".filter(this::isActiveArenaParticipant)" in participants_body
    eligibility_body = method_body(source, "private boolean isActiveArenaParticipant(Player player)")
    assert "attemptLifecycle.status(player.getUniqueId())" in eligibility_body
    assert "attemptLifecycle.refreshObjectiveEligibility" in eligibility_body
    assert "List.of()" in recipients_body
    assert "isCurrentRitualCaster" not in recipients_body
    assert "isCurrentRitualGuard" not in recipients_body

    damage_body = method_body(source, "public void onEventSkeletonArrowDamage")
    assert "ritualProjectileTargetAllowed(arrow, player)" in damage_body
    assert "ritualProjectileTargetAllowed(skeleton, player)" not in damage_body

    skeleton_shoot_body = method_body(source, "public void onEventSkeletonShootBow")
    shoot_start = source.index("public void onEventSkeletonShootBow")
    shoot_handler = source.rfind("@EventHandler", 0, shoot_start)
    assert "priority = EventPriority.HIGHEST" in source[shoot_handler:shoot_start]
    assert "prepareEventSkeletonArrow(arrow, skeleton" in skeleton_shoot_body
    assert "configureEventSkeletonArrow(" not in skeleton_shoot_body
    shot_admission = skeleton_shoot_body.index("if (!isWaveProjectileShooterAllowed(skeleton))")
    assert shot_admission < skeleton_shoot_body.index("reserveWaveProjectileGroup(")
    assert shot_admission < skeleton_shoot_body.index("tagEventArrowVolley(")
    assert shot_admission < skeleton_shoot_body.index("prepareEventSkeletonArrow(")
    denied_shot = skeleton_shoot_body[shot_admission:skeleton_shoot_body.index("boolean miniBoss", shot_admission)]
    assert "event.setCancelled(true)" in denied_shot
    assert "arrow.remove()" in denied_shot
    outcome_body = method_body(source, "public void onEventSkeletonShootBowOutcome")
    assert "runTaskLater(this, () ->" in outcome_body
    assert "event.isCancelled()" in outcome_body
    assert "spawnPendingSkeletonSideArrows(originalArrowId, pending)" in outcome_body
    assert outcome_body.index("event.isCancelled()") < outcome_body.index(
        "spawnPendingSkeletonSideArrows(originalArrowId, pending)"
    )
    side_arrows_body = method_body(source, "private void spawnPendingSkeletonSideArrows(")
    assert "configureEventSkeletonArrow(extra, skeleton" in side_arrows_body
    configure_arrow_body = method_body(
        source, "private boolean configureEventSkeletonArrow(Arrow arrow, Skeleton shooter"
    )
    assert "prepareEventSkeletonArrow(arrow, shooter" in configure_arrow_body
    assert "return trackEventArrow(arrow)" in configure_arrow_body
    prepare_arrow_body = method_body(
        source, "private void prepareEventSkeletonArrow(Arrow arrow, Skeleton shooter"
    )
    assert "isCurrentRitualCaster(shooter) || isCurrentRitualGuard(shooter)" in prepare_arrow_body
    assert "tagRitualProjectile(arrow)" in prepare_arrow_body

    custom_body = method_body(
        source, "private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell)"
    )
    assert "ritualProjectileTargetAllowed(arrow, player)" in custom_body
    assert "&& !isFreeRitualTarget(player)" not in custom_body

    detonation_body = method_body(source, "private void detonateExplosiveArrow")
    assert "ritualFreeTargetsForProjectile(arrow)" in detonation_body
    assert "ritualFreeTargetsForProjectile(shooter)" not in detonation_body
    assert "for (Player player : activeLivingPlayers())" not in detonation_body

    hit_body = method_body(source, "public void onEventArrowHit")
    assert "detonateExplosiveArrow(arrow, arrow.getLocation())" in hit_body

    volley_body = method_body(source, "private void riftArrowVolley")
    assert "isCurrentRitualGuard(caster)" in volley_body
    assert "tagRitualProjectile(arrow)" in volley_body

    cleanup_body = method_body(source, "private void cleanupEventArrow")
    assert "clearRitualProjectileMarker(arrow)" in cleanup_body
    assert source.count("clearRitualProjectileMarker(") == 2

    phase_body = method_body(source, "private boolean isEventArrowPhaseAllowed(Arrow arrow)")
    assert "isRitualProjectile(arrow)" in phase_body
    assert phase_body.index("isRitualProjectile(arrow)") < phase_body.index("arrow.getShooter()")


def test_marked_sphere_arrow_uses_one_authoritative_handler_without_skeleton_shooter() -> None:
    source = read_source()

    skeleton_body = method_body(source, "public void onEventSkeletonArrowDamage")
    assert "!isEventArrow(arrow)" in skeleton_body
    assert "if (skeleton == null || !isEventSkeleton(skeleton)) return;" in skeleton_body

    listener_body = method_body(source, "public void onCustomEventArrowDamage")
    assert "if (arrow.getShooter() instanceof Skeleton skeleton && isEventSkeleton(skeleton))" in listener_body
    assert "onCustomEventArrowDamage(event, arrow, spell)" in listener_body

    damage_body = method_body(
        source,
        "private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell)",
    )
    assert "boolean ritualProjectile = isRitualProjectileSpell(spell)" in damage_body
    assert "damageShooter == null ? null : damageShooter.getUniqueId()" in damage_body
    assert "livePlayer.damage(damage);" in damage_body
    assert "ARROW_SPELL_RITUAL_PROJECTILE.equals(spell)" in method_body(
        source, "private boolean isRitualProjectileSpell(String spell)"
    )


def test_ritual_guard_volley_uses_its_bounded_debuff_profile() -> None:
    damage_body = method_body(
        read_source(),
        "private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell)",
    )

    assert "boolean guardVolley = ARROW_SPELL_RITUAL_GUARD_VOLLEY.equals(spell)" in damage_body
    assert "guardVolley ? RitualGuardAbilityPolicy.forRole(" in damage_body
    assert "RitualGuardAbilityPolicy.Role.DEFENSIVE_VOLLEY).slownessTicks()" in damage_body
    assert "webProjectile || guardBrand || guardVolley" in damage_body


def test_sphere_barrage_requires_live_caster_and_still_enabled_spell():
    check = method_body(read_source(), "private boolean ritualSphereProjectileProvenanceAllowed(")
    assert "!isLiveOwnedEntity(owner)" in check
    assert "ritualCasterUuids.contains(owner)" in check
    assert "RitualCasterProgressionPolicy.isSpellEnabled" in check
    assert "MajorSpell.RIFT_BARRAGE" in check
