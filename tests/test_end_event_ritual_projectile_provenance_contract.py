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
    assert "ritualFreeTargets(activeLivingPlayers())" in recipients_body
    assert "List.of()" in recipients_body
    assert "isCurrentRitualCaster" not in recipients_body
    assert "isCurrentRitualGuard" not in recipients_body

    damage_body = method_body(source, "public void onEventSkeletonArrowDamage")
    assert "ritualProjectileTargetAllowed(arrow, player)" in damage_body
    assert "ritualProjectileTargetAllowed(skeleton, player)" not in damage_body

    skeleton_shoot_body = method_body(source, "public void onEventSkeletonShootBow")
    assert "isCurrentRitualGuard(skeleton)" in skeleton_shoot_body
    assert "tagRitualProjectile(arrow)" in skeleton_shoot_body

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


def test_marked_arrow_direct_damage_does_not_require_a_live_skeleton_shooter() -> None:
    source = read_source()

    damage_body = method_body(source, "public void onEventSkeletonArrowDamage")
    assert "!isEventArrow(arrow)" in damage_body
    assert "boolean ritualProjectile = isRitualProjectile(arrow)" in damage_body
    assert "if (!ritualProjectile &&" in damage_body
    assert "!isEventSkeleton(skeleton)" in damage_body
    assert "skeleton != null && !realitySplitTargetAllowed(skeleton, player)" in damage_body

    custom_body = method_body(source, "public void onCustomEventArrowDamage")
    assert "if (isRitualProjectile(arrow))" in custom_body
    assert "onEventSkeletonArrowDamage(event)" in custom_body
