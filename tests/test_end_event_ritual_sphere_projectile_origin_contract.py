"""Source contracts for the sphere-centered Wave 6 projectile adapter."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / (
    "copimine-end-event/src/me/copimine/endevent/domain/"
    "RitualSphereProjectilePolicy.java"
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = source.find("\n    @EventHandler", start + len(signature))
    return source[start:end]


def test_policy_exposes_bounded_finite_direction_contract() -> None:
    policy = read(POLICY)

    assert "public static final double MAX_INITIAL_SPEED = 0.85D" in policy
    assert "public static Vec3 direction(Vec3 origin, Vec3 target)" in policy
    assert "public record Vec3(double x, double y, double z)" in policy
    assert "Double.isFinite" in policy
    assert "return new Vec3(0.0D, 0.0D, 0.0D)" in policy


def test_wave6_barrage_uses_a_dedicated_sphere_origin_path() -> None:
    source = read(SOURCE)
    barrage = method_body(
        source,
        "private void spawnRitualProjectileVolley(LivingEntity caster, Player target, int count,",
    )

    assert "Location sphereOrigin = ritualSphereCenter(coreCombatAnchorLocation());" in barrage
    assert "spawnRitualSphereProjectileVolley" in barrage
    assert "riftArrowVolley(" not in barrage

    adapter = method_body(source, "private void spawnRitualSphereProjectileVolley")
    for required in (
        "ritualTargetAllowed(caster, target)",
        "RitualSphereProjectilePolicy.direction",
        "RitualSphereProjectilePolicy.MAX_INITIAL_SPEED",
        "tagRitualProjectile(arrow)",
        "tagRitualProjectileOrigin(arrow, sphereOrigin)",
        "tagRitualProjectileTarget(arrow, caster, target, liveAmplifiers)",
        "arrow.setShooter(caster)",
        "arrow.setGravity(false)",
        "arrow.setDamage(0.0D)",
        "trackEventArrow(arrow)",
        "Double.isFinite",
        "origin_distance=",
        "sphere_origin=",
        "projectile_spawn=",
        "cleanupEventArrow(arrow.getUniqueId())",
    ):
        assert required in adapter


def test_sphere_projectile_provenance_persists_origin_owner_target_and_lifetime() -> None:
    source = read(SOURCE)

    for required in (
        "private NamespacedKey keyRitualProjectileOriginX",
        "private NamespacedKey keyRitualProjectileOriginY",
        "private NamespacedKey keyRitualProjectileOriginZ",
        "private NamespacedKey keyRitualProjectileOwner",
        "private NamespacedKey keyRitualProjectileTarget",
        "private NamespacedKey keyRitualProjectileExpiresTick",
        'new NamespacedKey(this, "end_event_ritual_projectile_origin_x")',
        'new NamespacedKey(this, "end_event_ritual_projectile_origin_y")',
        'new NamespacedKey(this, "end_event_ritual_projectile_origin_z")',
        'new NamespacedKey(this, "end_event_ritual_projectile_owner")',
        'new NamespacedKey(this, "end_event_ritual_projectile_target")',
        'new NamespacedKey(this, "end_event_ritual_projectile_expires_tick")',
        "PersistentDataType.DOUBLE",
        "PersistentDataType.LONG",
        "EVENT_ARROW_MAX_TICKS",
    ):
        assert required in source


def test_sphere_projectile_provenance_is_validated_at_tick_and_hit_boundaries() -> None:
    source = read(SOURCE)
    for required in (
        "private boolean ritualSphereProjectileProvenanceAllowed(Arrow arrow, UUID hitTarget)",
        "RitualSphereProjectileProvenancePolicy.accepts(",
        "parseUuid(data, keyRitualProjectileOwner)",
        "parseUuid(data, keyRitualProjectileTarget)",
        "eventTickCounter)",
        "!ritualSphereProjectileProvenanceAllowed(arrow, null)",
        "player == null ? null : player.getUniqueId()",
    ):
        assert required in source

    cleanup = method_body(source, "private void clearRitualProjectileMarker")
    for key in (
        "keyRitualProjectileOriginX",
        "keyRitualProjectileOriginY",
        "keyRitualProjectileOriginZ",
        "keyRitualProjectileOwner",
        "keyRitualProjectileTarget",
        "keyRitualProjectileExpiresTick",
    ):
        assert f"data.remove({key})" in cleanup


def test_ordinary_caster_arrow_adapter_keeps_its_existing_origin() -> None:
    source = read(SOURCE)
    ordinary = method_body(source, "private void riftArrowVolley")

    assert "Location start = caster.getEyeLocation().clone();" in ordinary
    assert "caster.getWorld().spawn(start, Arrow.class)" in ordinary
    assert "arrow.setShooter(caster)" in ordinary
    assert "RitualSphereProjectilePolicy" not in ordinary
