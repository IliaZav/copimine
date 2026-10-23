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


def method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


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
    body = method(root, "private void castNextRitualAbility(")
    assert "RitualCasterTacticsPolicy.Role role" in body
    assert "RitualCasterTacticsPolicy.roleForSlot(" in body
    # Roles identify the surviving caster in logs. The sphere retains spells
    # unlocked by deaths, even if the original role's caster was the first to die.
    assert "RitualCasterProgressionPolicy.availableSpells(ritualCasterDeathCount)" in body
    assert "availableSpells.isEmpty()" in body
    normalized_body = " ".join(body.split())
    assert "availableSpells.get( Math.floorMod(ritualSpellCursor, availableSpells.size()))" in normalized_body
    assert "ritualSpellForMajor(" in body
    assert "ritualSpellForRole(candidateRole)" not in body
    assert "candidateRole == RitualCasterTacticsPolicy.Role.FINAL_SEAL" not in body
    assert "wave6RitualRuntimeAdapter.startGravityAnchor(" in body
    assert "wave6RitualRuntimeAdapter.startFallingAnvils(" in body
    assert "ritualSpellController.start(generation, spell," in body
    assert "advanceAndPersistRitualSpellCursor(availableSpells.size())" in body
    assert "StartBlocker.INVALID_GEOMETRY" in body
    assert "StartBlocker.NO_ELIGIBLE_PARTICIPANTS" in body
    assert "StartBlocker.CLEANUP_PENDING" in body
    assert "StartBlocker.ENCOUNTER_FAILURE" in body
    failed_start = body.split("if (!started.accepted())", 1)[1].split("return;", 1)[0]
    assert "handleRitualMajorSpellBlocker(" in failed_start
    assert "StartBlocker.ENCOUNTER_FAILURE" in failed_start
    assert "advanceAndPersistRitualSpellCursor" not in failed_start
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


def test_wave6_major_spell_cursor_is_persisted_and_restored_without_replaying_a_skipped_cast() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    snapshot = read(DOMAIN / "RitualSphereEncounterSnapshot.java")
    advancement = method(root, "private void advanceAndPersistRitualSpellCursor(")
    blocker = method(root, "private void handleRitualMajorSpellBlocker(")
    apply_snapshot = method(root, "private void applySnapshot(")
    wave_snapshot = method(root, "private Map<String, String> waveObjectiveProgressSnapshot()")

    assert "RitualCasterProgressionPolicy.advanceSpellCursor(" in advancement
    assert "saveStateAsync()" in advancement
    assert "RitualCasterProgressionPolicy.resolutionFor(blocker)" in blocker
    assert "case SKIP_AND_RETRY" in blocker
    assert "case WAIT" in blocker
    assert "case RECOVERY_REQUIRED" in blocker
    assert "ritualSpellCursor = wave6.spellCursor()" in apply_snapshot
    assert "ritualSpellCursor" in wave_snapshot
    assert "SPELL_CURSOR" in snapshot
    assert "spellCursor" in snapshot
    assert "RitualCasterProgressionPolicy.availableSpells(casterDeaths).size()" in snapshot


def test_guard_melee_and_cast_share_composed_controller_ownership() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    ability_scheduler = method(root, "private void tickRitualGuardAbilities(")
    assert "RitualGuardAbilityPolicy.endermanMeleeOwnsController(" in ability_scheduler
    guard_post = method(root, "private void tickRitualGuardPost(")
    assert "guard.getUniqueId().equals(ritualGuardAbilityGuard)" in guard_post
    assert "guard.setAI(false)" in guard_post


def test_wave6_pre_capture_lock_is_rechecked_inside_the_health_transaction() -> None:
    """Listener registration order must not open pre-capture caster damage."""

    root = read(SRC / "CopiMineEndEvent.java")
    commit = method(root, "public void onWaveMobPlayerDamageAuthoritative(")
    admission = method(root, "public void onRitualCasterDamageAdmission(")
    gate = method(root, "private boolean ritualCasterDamageLocked(")

    assert gate.index("isCurrentRitualCaster(entity)") < gate.index(
        "RitualCasterProgressionPolicy.mayTakeCasterDamage("
    )
    assert "ritualPrisonerId() != null" in gate
    assert "testRitualCombatMode" in gate
    assert "ritualCasterDamageLocked(victim)" in commit
    assert commit.index("ritualCasterDamageLocked(victim)") < commit.index(
        "blockRitualCasterShieldHit(event, victim)"
    )
    assert commit.index("ritualCasterDamageLocked(victim)") < commit.index(
        "victim.setHealth(result.remainingHealth())"
    )
    assert "event.setCancelled(true)" in commit[
        commit.index("ritualCasterDamageLocked(victim)"):
        commit.index("blockRitualCasterShieldHit(event, victim)")
    ]
    assert "ritualCasterDamageLocked(event.getEntity())" in admission
    assert "mayTakeCasterDamage" not in admission


def test_wave6_scheduler_retains_unlocked_spells_and_uses_only_live_owners() -> None:
    """The shared sphere scheduler survives original-role deaths and caster awakening."""

    root = read(SRC / "CopiMineEndEvent.java")
    body = method(root, "private void castNextRitualAbility(")
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
    assert "RitualGuardAbilityPolicy.roleSlot(casterSlot, guardSlot)" in ability_tick
    assert "RitualGuardAbilityPolicy.forGuardActionAtRange(" in ability_tick
    assert "roleSlot, actionOrdinal, distance)" in ability_tick
    assert "actionOrdinal" in ability_tick
    assert "RitualGuardAbilityPolicy.mayCommit(" in ability_tick
    resolve = root[root.index("private void resolveRitualGuardAbility("):
                    root.index("private void completeRitualGuardAbility(")]
    assert "RitualGuardAbilityPolicy.mayHitWardenSlamVictim(" in resolve
    assert "RitualGuardAbilityPolicy.acceptedPlayerDamage(" in resolve
    assert "clearRitualGuardAbilityState(" in clear
    assert "PotionEffectType.SLOWNESS" in ability_tick
    cast_validity = root[root.index("private boolean isRitualGuardAbilityCastValid"):
                         root.index("private void renderRitualGuardAbilityTelegraph")]
    assert "ritualGuardAbilityGeneration != generation" in cast_validity
    assert "isCurrentRitualGuard(mob)" in cast_validity
    assert "target.isOnline()" in cast_validity
    assert "target.isDead()" in cast_validity


def test_wave6_shield_stage_uses_the_selected_profiles_guard_total() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    shield = read(DOMAIN / "RitualCasterShieldPolicy.java")
    guard_tick = root[root.index("private void tickRitualGuardGroups"):
                      root.index("private void faceRitualCaster")]
    expected_count = root[root.index("private int ritualExpectedGuardsPerCaster"):
                          root.index("private String ritualShieldName")]

    assert "state(boolean casterAlive, int livingGuards, int expectedGuards)" in shield
    assert "blocksDamage(boolean casterAlive, int livingGuards, int expectedGuards)" in shield
    assert "RitualCasterShieldPolicy.state(" in guard_tick
    assert "ritualExpectedGuardsPerCaster()" in guard_tick
    assert "ritualSphereState.profile().guardsPerCaster()" in expected_count
    assert "ritualShieldName(shield)" in guard_tick


def test_wave6_guard_roles_and_giant_replacements_preserve_the_selected_roster() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    ability_policy = read(DOMAIN / "RitualGuardAbilityPolicy.java")
    spawn = method(root, "private boolean startRitualSphereObjective(")

    assert "int roleSlot = RitualGuardAbilityPolicy.roleSlot(casterSlot, guardSlot)" in spawn
    assert "RitualGuardAbilityPolicy.isGiantReplacement(" in spawn
    assert "if (roleSlot == 0 && giantReplacement)" in spawn
    assert "spawnRiftGiantEntity(world, guardLocation, 6, true, guardEntitySlot)" in spawn
    assert "RitualGuardAbilityPolicy.entityTypeForGuardRole(roleSlot)" in spawn
    assert "retagRitualEntity(guard, \"GUARD\", caster.getUniqueId(), casterSlot, guardSlot)" in spawn
    assert "int guardsPerCaster = boundedParticipants <= 4 ? 1 : boundedParticipants <= 8 ? 2 : 3" in ability_policy
    assert "guardSlot >= guardsPerCaster" in ability_policy
    assert "int expected = boundedParticipants <= 4 ? 1 : boundedParticipants <= 8 ? 2 : 3" in ability_policy

    recovery = method(root, "private boolean ensureRitualCasterRosterAvailableForRecovery(")
    assert "isGuardEntityTypeCompatible" in recovery
    assert "giantReplacement != isRiftGiant(guard)" in recovery


def test_wave6_giant_guards_keep_one_combat_controller_and_support_guard_allies() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    giant_policy = read(DOMAIN / "RiftGiantPolicy.java")
    generic_eligibility = method(root, "private boolean isWaveAiCombatEntity(")
    guard_tick = method(root, "private void tickRitualGuardGroups(")
    giant_check = method(root, "private boolean isRiftGiant(")

    assert "if (isCurrentRitualGuard(entity)) return false" in generic_eligibility
    assert guard_tick.count("tickRiftGiantOffense(giant, target, now)") == 1
    assert "EVENT_KIND_RITUAL_GUARD.equals(kind)" in giant_check
    assert "EVENT_KIND_ELITE.equals(kind)" in giant_check
    assert "PersistentDataType.BYTE" in giant_check
    assert "&& !ritualCaster" in giant_policy
    assert "&& !ritualGuard" not in giant_policy


def test_blocked_melee_and_projectile_hits_still_alert_the_casters_guard_group() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    damage = method(root, "public void onRitualCasterDamage(")
    projectile = method(root, "public void onRitualCasterShieldProjectile(")

    assert "alertRitualCasterAndGuards(caster, player)" in damage
    assert damage.index("alertRitualCasterAndGuards(caster, player)") < damage.index(
        "blockRitualCasterShieldHit(event, caster)"
    )
    assert "alertRitualCasterAndGuards(caster, player)" in projectile
    assert projectile.index("alertRitualCasterAndGuards(caster, player)") < projectile.index(
        "event.setCancelled(true)"
    )


def test_wave6_role_casts_release_physical_arrows_with_atomic_ownership() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    ability_tick = method(root, "private void tickRitualGuardAbilities(")
    resolve = method(root, "private void resolveRitualGuardAbility(")
    skeleton_damage = method(root, "public void onEventSkeletonArrowDamage(")
    generic_damage = method(root, "public void onCustomEventArrowDamage(EntityDamageByEntityEvent event)")
    volley = method(root, "private boolean spawnRitualGuardVolley(")
    brand = method(root, "private boolean spawnRitualGuardSoulBrandProjectile(")
    physical = method(root, "private boolean spawnRitualGuardPhysicalProjectile(")

    assert "RitualGuardAbilityPolicy.forGuardActionAtRange(" in ability_tick
    assert "roleSlot, actionOrdinal, distance)" in ability_tick
    assert "isCompatibleWithMob(profile, guard.getType().name())" in ability_tick
    assert "Skeleton skeleton = arrow.getShooter() instanceof Skeleton candidate ? candidate : null;" in skeleton_damage
    assert "if (skeleton == null || !isEventSkeleton(skeleton)) return;" in skeleton_damage
    assert "if (arrow.getShooter() instanceof Skeleton skeleton && isEventSkeleton(skeleton))" in generic_damage
    assert "spawnRitualGuardVolley(guard, target, ritualGuardAbilityImpact)" in resolve
    assert "spawnRitualGuardSoulBrandProjectile(" in resolve
    assert "target, ritualGuardAbilityImpact)" in resolve
    assert "distanceSquaredToSegment" not in resolve
    assert "reserveWaveProjectileGroup(groupId, 3)" in volley
    assert "world.spawnArrow(" in volley
    assert "rotateAroundY" in volley
    assert "tagRitualProjectileOrigin" in volley and "tagRitualProjectileTarget" in volley
    assert "trackEventArrow" in volley
    assert "clearWaveProjectileGroup" in volley
    assert "ARROW_SPELL_RITUAL_GUARD_BRAND" in brand
    assert "tagRitualProjectileTarget" in physical


def test_wave6_spider_poison_requires_a_successful_owned_melee_bite() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    bite = method(root, "public void onRitualSpiderBitePoison(")

    assert "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)" in root[
        root.index("@EventHandler", root.index("public void onRitualSpiderBitePoison(") - 100):
        root.index("public void onRitualSpiderBitePoison(")
    ]
    assert "event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK" in bite
    assert "event.getFinalDamage() <= 0.0D" in bite
    assert "isCurrentRitualGuard(spider)" in bite
    assert "isFreeRitualTarget(player)" in bite
    assert "PotionEffectType.POISON" in bite
    assert "RITUAL_SPIDER_POISON_TICKS" in bite


def test_wave6_only_escape_restricting_guard_control_waits_for_anchor_and_anvils() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    ability_tick = method(root, "private void tickRitualGuardAbilities(")
    hard_control = method(root, "private boolean isRitualHardControlWindowActive(")
    giant = method(root, "private boolean tickRiftGiantOffense(")
    damage = method(root, "private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell)")

    assert "if (isRitualMajorSpellBusy())" not in ability_tick
    assert "isHardControl(ritualGuardAbilityProfile)" in ability_tick
    assert "cancelRitualGuardAbilityCast" in ability_tick
    assert "Wave6Spell.GRAVITY_ANCHOR" in hard_control
    assert "Wave6Spell.FALLING_ANVILS" in hard_control
    assert "RitualGuardAbilityPolicy.suppressesHardControl" in hard_control
    assert "isRitualHardControlWindowActive()" in giant
    assert "RIFT_GIANT_STRIKE_CANCELLED" in giant
    assert "webProjectile && isRitualHardControlWindowActive()" in damage
    assert "? 0 : debuffTicks" in damage


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
