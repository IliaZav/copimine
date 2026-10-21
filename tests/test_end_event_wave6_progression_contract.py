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


def test_wave6_runtime_adapter_is_the_single_tick_owner_for_major_spells() -> None:
    source = EVENT.read_text(encoding="utf-8")
    global_tick = method_body(source, "private void tick()",
                              "private void updatePadOccupancy")
    runtime_tick = method_body(source, "private void tickWave6RitualRuntimeOnce",
                               "private void castNextRitualAbility")
    cast = method_body(source, "private void castNextRitualAbility",
                       "private RitualSpellController.Spell ritualSpellForRole")
    tick = method_body(source, "private void tickRitualSpellController",
                       "private void tickActiveRitualSpell")
    assert "tickWave6RitualRuntimeOnce()" in global_tick
    assert "lastWave6RitualRuntimeTick == eventTickCounter" in runtime_tick
    assert "wave6RitualRuntimeAdapter.tick(generation, eventTickCounter)" in runtime_tick
    assert "wave6RitualRuntimeAdapter.snapshot()" in cast
    assert "ritualSpellController.tick(" not in cast
    assert "wave6RitualRuntimeAdapter.snapshot()" in tick
    assert "ritualSpellController.tick(" not in tick
    assert "snapshot.wave6Spell() != null" in tick


def test_wave6_replacement_spells_use_owned_crystal_and_anvil_display_effects() -> None:
    source = EVENT.read_text(encoding="utf-8")
    cast = method_body(source, "private void castNextRitualAbility",
                       "private AnvilCluster lockFallingAnvilCluster")
    test_hook = method_body(source, "private boolean forceRitualCoreAbilityForTest",
                            "private void logTestWaveSpellAssignments")
    effects = method_body(source, "private final class BukkitWave6RitualEffects",
                          "private RitualSpellController.Spell ritualSpellForRole")
    assert "startGravityAnchor(" in cast
    assert "startFallingAnvils(" in cast
    assert "startGravityAnchor(" in test_hook
    assert "startFallingAnvils(" in test_hook
    assert "world.spawn(location, EnderCrystal.class" in effects
    assert "Material.ANVIL" in effects and "ItemDisplay.class" in effects
    assert "isFreeRitualTarget(target)" in effects
    assert "createExplosion(" not in effects
    assert "case GRAVITY_WELL, RIFT_CHAINS -> false;" in source


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


def test_official_caster_damage_waits_for_prisoner_capture_and_combat_sandbox_is_explicit() -> None:
    source = EVENT.read_text(encoding="utf-8")
    admission = method_body(source, "public void onRitualCasterDamageAdmission",
                            "public void onOwnedEntityDeath")
    assert "isCurrentRitualCaster" in admission
    assert "ritualPrisonerId() != null" in admission
    assert "testRitualCombatMode" in admission
    assert "RitualCasterProgressionPolicy.mayTakeCasterDamage" in admission
    assert "event.setCancelled(true)" in admission


def test_wave6_completion_requires_capture_outside_the_combat_sandbox() -> None:
    source = EVENT.read_text(encoding="utf-8")
    completion = method_body(source, "private boolean currentObjectiveProgressComplete",
                             "private void tickCurrentCarrierObjective")
    assert "ritualPrisonerId() != null || testRitualCombatMode" in completion
    assert "RitualCasterProgressionPolicy.mayComplete" in completion


def test_repeated_completion_check_after_prison_break_still_requires_capture() -> None:
    source = EVENT.read_text(encoding="utf-8")
    tick = method_body(source, "private void tickCurrentRitualSphereObjective",
                       "private void renderRitualSphereChanneling")
    broken_branch = tick[tick.index("if (wave6PrisonBroken)"):tick.index("ensureRitualSphereVisual")]
    assert "ritualPrisonerId() != null || testRitualCombatMode" in broken_branch
    assert "RitualCasterProgressionPolicy.mayComplete" in broken_branch


def test_scheduler_has_generation_fenced_single_telegraph_execute_active_recovery_flow() -> None:
    source = SPELL_CONTROLLER.read_text(encoding="utf-8")
    for stage in ("IDLE", "TELEGRAPH", "EXECUTE", "ACTIVE", "RECOVERY"):
        assert stage in source
    assert "eventGeneration != generation" in source
    assert "stage != Stage.IDLE" in source
    assert "TELEGRAPH_TICKS = 20L" in source
