from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualCasterProgressionPolicy.java"
SPELL_CONTROLLER = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/ritual/RitualSpellController.java"


def method_body(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


def test_live_wave6_uses_the_shared_spell_controller_without_drain_or_control_swap() -> None:
    source = EVENT.read_text(encoding="utf-8")
    tick = method_body(source, "private void tickCurrentRitualSphereObjective",
                       "private void renderRitualSphereChanneling")
    cast = method_body(source, "private void castNextRitualAbility",
                       "private RitualSpellController.Spell ritualSpellForRole")
    assert "tickRitualSpellController(now)" in tick
    assert "tickRitualControls(now)" not in tick
    assert "applyRitualSphereDrain(now)" not in tick
    assert "RitualSpellController.StartResult" in cast
    assert "RitualCasterProgressionPolicy.isSpellEnabled" in cast
    assert "startRitualReverse(" not in cast
    assert "startRitualControlSwap(" not in cast


def test_wave6_no_longer_changes_prisoner_health_for_drain_or_health_floor() -> None:
    source = EVENT.read_text(encoding="utf-8")
    assert "private void applyRitualSphereDrain" not in source
    assert "onRitualPrisonerDamage" not in source
    assert "RitualPrisonerHealthPolicy.MIN_HEALTH" not in source
    assert "RitualPrisonerHealthPolicy.DRAIN_HEALTH" not in source


def test_caster_progression_requires_the_fifth_death_and_cleanup_before_completion() -> None:
    source = EVENT.read_text(encoding="utf-8")
    death = method_body(source, "private void handleRitualCasterDeath",
                        "private RitualCasterProgressionPolicy.MajorSpell majorSpell")
    completion = method_body(source, "private boolean currentObjectiveProgressComplete",
                             "private void tickCurrentCarrierObjective")
    assert "afterCasterDeath(ritualCasterDeathCount)" in death
    assert "breakRitualPrison()" in death
    assert "removeRitualEntity(id)" in source[source.index("private void breakRitualPrison"):]
    assert "RitualCasterProgressionPolicy.mayComplete" in completion


def test_scheduler_has_generation_fenced_single_telegraph_execute_active_recovery_flow() -> None:
    source = SPELL_CONTROLLER.read_text(encoding="utf-8")
    for stage in ("IDLE", "TELEGRAPH", "EXECUTE", "ACTIVE", "RECOVERY"):
        assert stage in source
    assert "eventGeneration != generation" in source
    assert "stage != Stage.IDLE" in source
    assert "TELEGRAPH_TICKS = 20L" in source
    assert "RECOVERY_TICKS = 20L" in source
