from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_wave6_state_and_coordinator_have_no_drain_or_control_swap_api():
    policy = read(PLUGIN / "domain/RitualSphereEncounterPolicy.java")
    runtime = read(PLUGIN / "runtime/encounter/RitualSphereEncounter.java")

    for forbidden in (
        "drainDue",
        "advanceDrain",
        "DrainTransition",
        "successfulDrains",
        "lastDrainMillis",
        "REVERSE_MOVEMENT",
        "CONTROL_SWAP",
    ):
        assert forbidden not in policy
    assert " drain(" not in runtime
    assert "advanceDrain" not in runtime


def test_wave6_scaling_contains_no_resonance_or_spell_strength_multipliers():
    scaling = read(PLUGIN / "domain/RitualSphereScalingPolicy.java")
    for forbidden in (
        "MAX_INTENSITY",
        "successfulDrains",
        "intensityForSuccessfulDrains",
        "projectileDamageMultiplier",
        "effectDurationMultiplier",
        "cooldownMultiplier",
        "controlSwapPairs",
    ):
        assert forbidden not in scaling


def test_wave6_server_has_only_prisoner_abilities_and_no_health_floor_or_input_swap():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    for forbidden in (
        "applyRitualSphereDrain",
        "onRitualPrisonerDamage",
        "RitualPrisonerHealthPolicy",
        "startRitualReverse",
        "startRitualControlSwap",
        "RitualControlPairPolicy",
        "ritualReverseUntil",
        "ritualZoneReverseRecipients",
    ):
        assert forbidden not in event
    assert "PrisonerAbilityController" in event
    assert "END_PRISONER_ABILITY_REQUEST" in event


def test_legacy_snapshot_values_are_migration_only_and_not_encoded():
    snapshot = read(PLUGIN / "domain/RitualSphereEncounterSnapshot.java")
    assert "legacy" in snapshot.lower()
    encode = snapshot[snapshot.index("Map<String, String> encode"):snapshot.index("public static Data decode")]
    for obsolete_key in (
        "successful-drains",
        "intensity",
        "last-drain-millis",
    ):
        assert obsolete_key not in encode
