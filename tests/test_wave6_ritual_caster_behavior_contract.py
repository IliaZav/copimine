"""Contracts for the Wave 6 Ritual Sphere caster behaviour.

The caster is a staged encounter role, not a normal Enderman with a shorter
aggro timer.  These checks keep the guard gate, first-hit wake-up, persistent
state and four-role slot mapping explicit in source and in the pure policy.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
SRC = PLUGIN / "src/me/copimine/endevent"
DOMAIN = SRC / "domain"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_ritual_caster_policy_has_guarded_exposed_and_awakened_states() -> None:
    policy_path = DOMAIN / "RitualCasterTacticsPolicy.java"
    assert policy_path.is_file(), "Wave 6 casters need a dedicated pure tactics policy"
    policy = read(policy_path)
    for state in ("GUARDED_CASTING", "EXPOSED_CASTING", "AWAKENED_ATTACKING"):
        assert state in policy
    assert "state(boolean guardAlive, boolean damaged)" in policy
    assert "if (guardAlive)" in policy
    assert "damaged ? State.AWAKENED_ATTACKING : State.EXPOSED_CASTING" in policy
    assert "canTargetPlayers" in policy
    assert "castsSphere" in policy


def test_wave6_caster_slots_use_four_spell_roles_and_a_final_seal() -> None:
    policy = read(DOMAIN / "RitualCasterTacticsPolicy.java")
    for role in (
        "RIFT_BARRAGE_CASTER",
        "GRAVITY_WELL_CASTER",
        "SOUL_BRAND_CASTER",
        "RIFT_CHAINS_CASTER",
        "FINAL_SEAL",
    ):
        assert role in policy
    assert "public static Role roleForSlot(int casterSlot)" in policy
    for mapping in (
        "case 0 -> Role.RIFT_BARRAGE_CASTER",
        "case 1 -> Role.GRAVITY_WELL_CASTER",
        "case 2 -> Role.SOUL_BRAND_CASTER",
        "case 3 -> Role.RIFT_CHAINS_CASTER",
        "default -> Role.FINAL_SEAL",
    ):
        assert mapping in policy
    assert "VOID_LANCE" not in policy
    assert "RIFT_SPIKES" not in policy


def test_wave6_caster_runtime_keeps_casters_passive_until_guard_death_and_first_hit() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert "RitualCasterTacticsPolicy" in root
    assert "keyRitualCasterAwakened" in root
    assert "PersistentDataType.BYTE" in root
    assert "livingGuards > 0" in root
    assert "RitualCasterTacticsPolicy.state" in root
    assert "caster.setTarget(null)" in root
    assert "caster.setAI(false)" in root
    assert "caster.setAware(false)" in root
    assert "caster.setAI(true)" in root
    assert "caster.setAware(true)" in root
    assert "ritualCasterCanAttack" in root
    assert "readRitualCasterAwakened" in root
    assert "markRitualCasterAwakened" in root
    assert "event.getFinalDamage()" in root


def test_wave6_spell_dispatch_uses_accumulated_unlocks_independently_of_caster_slot() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private void castNextRitualAbility")
    end = root.index("private void startRitualZone", start)
    body = root[start:end]
    assert "RitualCasterTacticsPolicy.Role role" in body
    assert "RitualCasterTacticsPolicy.roleForSlot(" in body
    # Roles identify the surviving caster in logs. The sphere retains spells
    # unlocked by deaths, even if the original role's caster was the first to die.
    assert "RitualCasterProgressionPolicy.availableSpells(ritualCasterDeathCount)" in body
    assert "availableSpells.isEmpty()" in body
    assert "availableSpells.get(Math.floorMod(ritualSpellCursor, availableSpells.size()))" in body
    assert "ritualSpellForMajor(" in body
    assert "ritualSpellForRole(candidateRole)" not in body
    assert "candidateRole == RitualCasterTacticsPolicy.Role.FINAL_SEAL" not in body
    for handler in (
        "spawnRitualProjectileVolley",
        "startRitualZone",
    ):
        assert handler in body
    assert "startRitualReverse" not in body
    assert "startRitualControlSwap" not in body
    for removed in (
        "VOID_LANCE",
        "RIFT_SPIKES",
        "spawnRitualVoidLance",
        "spawnRitualRiftSpikes",
    ):
        assert removed not in body
    assert "RitualSphereEncounterPolicy.Ability.values()" not in body


def test_wave6_scheduler_retains_unlocked_spells_and_uses_only_live_owners() -> None:
    """The shared sphere scheduler survives original-role deaths and caster awakening."""

    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private void castNextRitualAbility")
    end = root.index("private void startRitualZone", start)
    body = root[start:end]
    assert "isLiveOwnedEntity(entity.getUniqueId())" in body
    assert "ritualSpellForMajor(" in body
    assert "RitualCasterTacticsPolicy.ownsRitualAbility(" not in body
    assert "wave6PrisonBroken" in body
    assert "scheduler.stage() != RitualSpellController.Stage.IDLE" in body
    assert "source=NATURAL" not in body
    assert "source=SHARED_SCHEDULER" in body
    assert "cooldown_ms=" in body
    assert "RitualCasterProgressionPolicy.isSpellEnabled(" in body
    assert "RitualAmplifierPolicy" not in body
    assert "ritualGuardAbilityGuard != null" in body


def test_wave6_caster_visual_binding_has_a_dedicated_raised_arms_variant() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    catalog = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java")
    selection = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndermanRendererSelection.java")
    model = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java")
    assert "END_RIFT_RITUAL_CASTER_V1" in root
    assert 'textures.put("END_RIFT_RITUAL_CASTER_V1", entityTexture("end_rift_user_enderman.png"))' in catalog
    assert "RITUAL_CASTER" in selection
    assert "caster" in model.lower()
    assert "leftArm.pitch" in model
    assert "rightArm.pitch" in model


def test_wave6_caster_pose_tracks_channel_windup_release_and_server_facing() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    guard_tick = root[root.index("private void tickRitualGuardGroups"):
                      root.index("private String ritualShieldName")]
    execute_tick = root[root.index("private void tickRitualSpellController"):
                        root.index("private void tickActiveRitualSpell")]
    model = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java")

    assert "enderman.setScreaming(" in guard_tick
    assert "faceRitualCaster(" in guard_tick
    assert "faceRitualCaster(" in execute_tick
    assert "livingCaster.swingMainHand()" in execute_tick
    assert "if (isChannelingPhase(phase, entity.isAngry())) {\n                applyChannelingPose(pulse);" in model
    assert 'case "RITUAL_CHANNEL", "RITUAL_WINDUP", "RITUAL_RELEASE" -> true' in model
    assert 'case "RITUAL_COMBAT" -> false' in model
    assert "sendRitualCasterPhase(" in guard_tick
    # -70 degrees left the long hands below shoulder level. The real model
    # regression also checks their raised geometry and pose reset numerically.
    assert "leftArm.pitch = -2.62F" in model
    assert "rightArm.pitch = -2.62F" in model
    assert "} else {\n                leftArm.pitch += pulse * 0.02F;" in model


def test_wave6_pressure_pack_is_bounded_and_never_gates_ritual_progression() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    objective_tick = root[root.index("private boolean tickCurrentObjective"):
                          root.index("private boolean reportCurrentWaveResult")]
    ritual_tick = root[root.index("private void tickCurrentRitualSphereObjective"):
                       root.index("private void renderRitualSphereChanneling")]
    scaling = read(DOMAIN / "RitualSphereScalingPolicy.java")

    assert "boolean noMobs = activeWave == 6 || countLiveWaveEntitiesForWave(activeWave) == 0;" in objective_tick
    assert "clearRitualPressureMobs(" in ritual_tick
    assert "Math.min(8, activePressure / 4)" in scaling
    assert "spawnWave6PressurePack(world, core, players, sandbox)" in root
    assert "initializeWaveGameplay(wave, world, core, roster, true, combatMode, false)" in root
    assert "initializeWaveGameplay(wave, world, core, authoritativeAttemptRoster(), false, false, false)" in root


def test_wave6_guards_have_tuned_once_health_and_one_shared_cancellable_ability() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert "private void tickRitualGuardAbilities" in root
    spawn = root[root.index("private boolean startRitualSphereObjective"):
                 root.index("private boolean placeRitualEntity")]
    group_tick = root[root.index("private void tickRitualGuardGroups"):
                      root.index("private void faceRitualCaster")]
    ability_tick = root[root.index("private void tickRitualGuardAbilities"):
                        root.index("private String ritualShieldName")]
    clear = root[root.index("private void clearRitualSphereObjective"):
                 root.index("private void removeRitualEntity")]

    assert "configureRitualGuardHealth(living)" in spawn
    assert "keyRitualGuardHealthConfigured" in root
    assert "RitualGuardStatsPolicy.maximumHealth(" in root
    assert "tickRitualGuardAbilities(now)" in group_tick
    assert "RitualGuardAbilityPolicy.forGuardSlot(" in ability_tick
    assert "RitualGuardAbilityPolicy.mayCommit(" in ability_tick
    assert "RitualGuardAbilityPolicy.hits(" in ability_tick
    assert "clearRitualGuardAbilityState(" in clear
    assert "PotionEffectType.SLOWNESS" in ability_tick
    cast_validity = root[root.index("private boolean isRitualGuardAbilityCastValid"):
                         root.index("private void renderRitualGuardAbilityTelegraph")]
    assert "ritualGuardAbilityGeneration != generation" in cast_validity
    assert "isCurrentRitualGuard(mob)" in cast_validity
    assert "target.isOnline()" in cast_validity
    assert "target.isDead()" in cast_validity


def test_wave6_live_ai_probe_allows_server_controlled_passive_casters() -> None:
    probe = read(ROOT / "tests/RunEndRiftAiPhasesLive.ps1")
    root = read(SRC / "CopiMineEndEvent.java")
    assert "cmend debug ai --json" in probe
    assert "ConvertFrom-Json" in probe
    assert "ritualGuardOwnershipValid" in probe
    assert "LIVE_W6_CASTER_GUARDED_PASS" in probe
    assert "LIVE_W6_CASTER_EXPOSED_PASS" in probe
    assert "LIVE_W6_CASTER_AWAKENED_PASS" in probe
    assert "nativeAiEnabled" in probe
    assert "ExpectedCasterState" in probe
    assert "AllowPassiveRitualCasters" not in probe
    assert "ritualCastersTargeted" in probe
    assert "ritualCasters=" in root
    assert "ritualCastersPassive=" in root
    assert "ritualCastersTargeted=" in root
    assert "handleStructuredAiDiagnosticsJson" in root
    assert "WAVE6_RITUAL_CASTER_TEST_STATE" in root
