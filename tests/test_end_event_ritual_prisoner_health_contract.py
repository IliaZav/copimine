"""Wave 6 no longer changes prisoner health on a timer or by immunity."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualSphereEncounterPolicy.java"
SNAPSHOT = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualSphereEncounterSnapshot.java"


def test_wave6_has_no_prisoner_drain_or_health_floor_runtime_path() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    policy = POLICY.read_text(encoding="utf-8")

    for obsolete in (
        "applyRitualSphereDrain",
        "onRitualPrisonerDamage",
        "successfulDrains",
        "lastDrainMillis",
    ):
        assert obsolete not in source
        assert obsolete not in policy
    assert "RitualPrisonerHealthPolicy" not in source
    assert "RitualPrisonerHealthPolicy" not in policy
    assert "DRAIN_HEALTH" not in source
    assert "MIN_HEALTH" not in policy


def test_prisoner_takes_ordinary_combat_damage_and_can_be_defeated() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    assert "onRitualSupportDamage(EntityDamageEvent event)" in source
    assert "ritualGuardianLinkUntilMillis.getOrDefault(victim.getUniqueId(), 0L)" in source
    assert "event.setDamage(event.getDamage() * 0.70D);" in source
    assert "onRitualPrisonerDamage" not in source


def test_old_drain_snapshot_fields_are_discarded_and_never_reencoded() -> None:
    source = SNAPSHOT.read_text(encoding="utf-8")
    encode = source[source.index("Map<String, String> encode"):source.index("public static Data decode")]
    assert "LEGACY_KEYS" in source
    assert "migratedLegacyState" in source
    for key in ("successful-drains", "intensity", "last-drain-millis"):
        assert key not in encode
