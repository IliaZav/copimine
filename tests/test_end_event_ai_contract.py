from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def test_boss_health_and_phase_thresholds_are_explicitly_balanced_for_2500_hp() -> None:
    assert "health: 2500.0" in CONFIG
    assert "attack-damage-bonus: 8.0" in CONFIG
    assert "half-health: 1250.0" in CONFIG
    assert "final-threshold: 250.0" in CONFIG
    assert "bossFinalHealth()" in MAIN


def test_boss_ai_has_one_controller_with_telegraph_and_generation_guards() -> None:
    for marker in (
        "EndRiftAiPolicy",
        "recentBossTargets",
        "rotateBossTarget",
        "telegraphBossSpell",
        "BOSS_SPELL_TELEGRAPH",
        "BOSS_SPELL_CAST",
        "taskRegistry.owns(callbackGeneration)",
        "BOSS_AI_TARGET",
        "BOSS_AI_LEASH",
        "MAX_ACTIVE_VOID_MARKS = 2",
        "VOID_MARK_DURATION_SECONDS = 10",
        "MAX_POTION_AMPLIFIER = 3",
        "ARENA_INFERNO_DURATION_TICKS = 100",
        "VOID_MARK_RADIUS_BLOCKS = 3",
        "runTaskTimer(this",
        "BOSS_VOID_MARK_CLEANUP",
        "clearVoidMarkZones",
        "Snowball",
        "EVENT_KIND_PROJECTILE",
        "MAX_ACTIVE_RIFT_PROJECTILES = 8",
        "BOSS_PROJECTILE_SPAWN",
        "BOSS_PROJECTILE_HIT",
        "cleanupRiftProjectile",
    ):
        assert marker in MAIN
    assert "random.nextInt(4)" not in MAIN
    assert "BOSS_BLAST_DAMAGE = 12.0D" in MAIN
    assert "BOSS_PROJECTILE_DAMAGE = 12.0D" in MAIN
    assert "VOID_MARK_DAMAGE = 4.0D" in MAIN


def test_boss_uses_the_floor_combat_anchor_and_can_take_damage_after_absorption() -> None:
    tick = MAIN[MAIN.index("private void tickBoss()"):MAIN.index("private int randomSeconds", MAIN.index("private void tickBoss()"))]
    teleport = MAIN[MAIN.index("private void maintainBossTeleport"):MAIN.index("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)", MAIN.index("private void maintainBossTeleport"))]
    damage = MAIN[MAIN.index("public void onBossDamage"):MAIN.index("private void applyBossDamage", MAIN.index("public void onBossDamage"))]
    assert "Location core = coreCombatAnchorLocation();" in tick
    assert "Location anchor = coreCombatAnchorLocation();" in teleport
    assert "phase == EventPhase.BOSS_FINISH" in damage
    assert "finalDrainTriggered" in damage
    assert "event.setCancelled(true);" in damage
    assert "return;" in damage


def test_boss_has_a_bounded_stuck_detector_and_reasoned_teleport_log() -> None:
    assert "private void detectBossStuck" in MAIN
    stuck = MAIN[MAIN.index("private void detectBossStuck"):MAIN.index("@EventHandler(priority = EventPriority.HIGHEST", MAIN.index("private void detectBossStuck"))]
    assert "now - bossLastProgressAt < 4_000L" in stuck
    assert "moved >= 0.5D" in stuck
    assert "reason=stuck" in stuck
    assert "BOSS_MOVE_TELEPORT" in stuck
    assert "nextBossStuckTeleportMillis" in MAIN


def test_boss_leash_runs_before_any_cast_state_can_short_circuit_movement() -> None:
    tick = MAIN[MAIN.index("private void tickBoss()"):MAIN.index("private void synchronizeBossStage", MAIN.index("private void tickBoss()"))]
    assert tick.index("Location core = coreCombatAnchorLocation();") < tick.index("if (bossCastState == BossCastState.ABSORPTION_CHANNEL")
    assert tick.index("enforceCombatLeash(boss, core") < tick.index("if (bossCastState == BossCastState.ABSORPTION_CHANNEL")


def test_boss_does_not_choose_a_core_standing_target_as_a_reason_to_collapse_back_to_core() -> None:
    maintain = MAIN[MAIN.index("private void maintainBossTeleport"):MAIN.index("private void detectBossStuck")]
    assert "targetOnCore" in maintain
    assert "isCoreBlockPosition(target.getLocation())" in maintain
    assert "target-on-core" in maintain


def test_boss_debuffs_are_max_level_and_three_times_longer() -> None:
    assert "MAX_POTION_AMPLIFIER = 255" not in MAIN
    assert "configuredDebuffAmplifier()" in MAIN
    assert "MAX_POTION_AMPLIFIER = 3" in MAIN
    assert "DEBUFF_DURATION_MULTIPLIER = 3" in MAIN
    for constant, base in (
        ("BOSS_BLAST_DEBUFF_TICKS", "BASE_BOSS_DEBUFF_TICKS"),
        ("BOSS_PROJECTILE_DEBUFF_TICKS", "BASE_BOSS_DEBUFF_TICKS"),
        ("VOID_MARK_DEBUFF_TICKS", "BASE_VOID_MARK_DEBUFF_TICKS"),
        ("BOSS_JUDGMENT_WITHER_DEBUFF_TICKS", "BASE_JUDGMENT_WITHER_DEBUFF_TICKS"),
        ("BOSS_JUDGMENT_WEAKNESS_DEBUFF_TICKS", "BASE_JUDGMENT_WEAKNESS_DEBUFF_TICKS"),
        ("ARENA_INFERNO_DEBUFF_TICKS", "BASE_ARENA_INFERNO_DEBUFF_TICKS"),
    ):
        assert re.search(
            rf"{constant}\s*=\s*{base}\s*\*\s*DEBUFF_DURATION_MULTIPLIER",
            MAIN,
        )
    assert "configuredDebuffAmplifier()" in MAIN[MAIN.index("private void voidBlast"):MAIN.index("private void riftProjectile")]
    assert "configuredDebuffAmplifier()" in MAIN[MAIN.index("private void riftProjectile"):MAIN.index("private void cleanupRiftProjectile")]
    mark = MAIN[MAIN.index("private void voidMark"):MAIN.index("private void cancelVoidMark")]
    assert "PotionEffectType.WITHER" in mark
    assert "VOID_MARK_DEBUFF_TICKS, configuredDebuffAmplifier()" in mark
    assert "BOSS_JUDGMENT_WITHER_DEBUFF_TICKS, configuredDebuffAmplifier()" in MAIN
    assert "BOSS_JUDGMENT_WEAKNESS_DEBUFF_TICKS, configuredDebuffAmplifier()" in MAIN
    assert "ARENA_INFERNO_DEBUFF_TICKS, configuredDebuffAmplifier()" in MAIN
    for method, next_method in (
        ("private void miniBossRiftStep", "private void miniBossVoidSnare"),
        ("private void miniBossVoidSnare", "private void miniBossEchoPulse"),
        ("private void miniBossEchoPulse", "private void enforceCombatLeash"),
    ):
        assert "configuredDebuffAmplifier()" in MAIN[MAIN.index(method):MAIN.index(next_method)]


def test_arena_inferno_is_a_five_second_owned_spell() -> None:
    assert "ARENA_INFERNO" in MAIN
    assert "arenaInferno" in MAIN
    inferno = MAIN[MAIN.index("private void arenaInferno"):MAIN.index("private void clearArenaInferno")]
    assert "Material.FIRE" in inferno
    assert "HazardMutationJournal.Entry" in inferno
    assert "hazardJournal.prepare" in inferno
    assert "restoreArenaInfernoBlocks" in MAIN
    assert "player.setFireTicks" in inferno
    assert "ARENA_INFERNO_DURATION_TICKS = 100" in MAIN
    assert "real_fire=true" in inferno


def test_wave_elites_have_one_bound_spell_and_are_ticked_by_the_same_controller() -> None:
    for marker in (
        "MiniBossSpell",
        "miniBossSpell(wave, abilityIndex)",
        "tickMiniBosses",
        "telegraphMiniBossSpell",
        "MINIBOSS_SPELL_TELEGRAPH",
        "MINIBOSS_SPELL_CAST",
        "EVENT_KIND_ELITE",
    ):
        assert marker in MAIN
    assert "miniBossSpell = null" not in MAIN


def test_rift_guardians_have_exactly_40_health() -> None:
    start = MAIN.index("private void spawnEnderman")
    end = MAIN.index("private Entity spawnOwnedMob", start)
    body = MAIN[start:end]
    assert "max.setBaseValue(40.0D);" in body
    assert "enderman.setHealth(40.0D);" in body
    assert "max.setBaseValue(80.0D);" not in body
    assert "enderman.setHealth(80.0D);" not in body


def test_ai_config_has_separate_cooldowns_and_telegraphs_for_boss_and_minibosses() -> None:
    for marker in (
        "recent-target-memory: 3",
        "teleport-cooldown-seconds: 12",
        "mini-bosses:",
        "spell-cooldown-seconds: [8, 12]",
        "spell-telegraph-ticks: 20",
        "rift-step:",
        "void-snare:",
        "echo-pulse:",
    ):
        assert marker in CONFIG


def test_summon_spell_is_only_offered_at_the_two_health_windows() -> None:
    assert "servantSummonWindow(boss)" in MAIN
    assert "available.remove(EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS)" in MAIN
    assert "servantsSummonedAt70" in MAIN
    assert "servantsSummonedAt35" in MAIN


def test_local_ai_harness_runs_real_controllers_without_touching_official_phase() -> None:
    for marker in (
        '"ai"',
        "spawnTestAi",
        "testCombatAiMode",
        "official phase/roster/victory не изменены",
        "boolean testBossAi = testCombatAiMode && isTestBoss(boss)",
        "isMiniBossCombatPhase()",
    ):
        assert marker in MAIN


def test_local_ai_harness_ticks_boss_controller_outside_official_boss_phase() -> None:
    """The local harness deliberately stays out of the official state machine."""
    tick = MAIN[MAIN.index("private void tick()"):MAIN.index("private void updatePadOccupancy")]
    assert "testCombatAiMode && liveBoss() != null" in tick
    assert "tickBoss();" in tick
    assert "&& !(testCombatAiMode && isTestBoss(boss))" in MAIN
    assert "phase != EventPhase.BOSS_ACTIVE && !testBossAi" in MAIN


def test_disposable_boss_spawn_enables_real_ai_and_client_visuals() -> None:
    start = MAIN.index("private void spawnTestBoss")
    end = MAIN.index("private void spawnTestAi", start)
    body = MAIN[start:end]
    assert "testCombatAiMode = true;" in body
    assert "halfHealthTriggered = false;" in body
    assert "finalDrainTriggered = false;" in body
    assert "finalDrainApplied = false;" in body
    assert "ensureBossBar();" in body
    assert "bindBossClientForOnlinePlayers();" in body


def test_clearing_a_boss_resets_target_and_spell_cooldown_state() -> None:
    assert "nextTargetMillis = 0L;" in MAIN
    assert "nextSpellMillis = 0L;" in MAIN
    assert "private void clearBossOnly()" in MAIN
    assert "private void clearCombatAiState()" in MAIN


def test_disposable_test_phase_state_is_not_persisted_and_cleanup_clears_it() -> None:
    half_start = MAIN.index("private void triggerHalfPhase")
    half_end = MAIN.index("private List<Player> activeLivingPlayers", half_start)
    half_body = MAIN[half_start:half_end]
    assert "if (!isTestBoss(boss) && !saveStateSync())" in half_body

    clear_start = MAIN.index("private void clearBossOnly()")
    clear_end = MAIN.index("private void tickBoss()", clear_start)
    clear_body = MAIN[clear_start:clear_end]
    assert "boolean disposableTest = testCombatAiMode || (boss != null && isTestBoss(boss));" in clear_body
    assert "if (disposableTest)" in clear_body
    assert "halfHealthTriggered = false;" in clear_body
    assert "controlSpellUnlocked = false;" in clear_body
    assert "finalDrainTriggered = false;" in clear_body
    assert "finalDrainApplied = false;" in clear_body
    assert "if (disposableTest && !saveStateSync())" in clear_body


def test_combat_containment_allows_twenty_blocks_but_keeps_teleports_on_core_level() -> None:
    assert "boss-radius: 20.0" in CONFIG
    assert "containment-radius: 20.0" in CONFIG
    assert "config.containmentRadius()" in MAIN
    assert "MIN_BOSS_CORE_DISTANCE_BLOCKS = 3.5D" in MAIN
    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS = 2.5D" in MAIN
    assert "BossMovementPolicy.chooseSafeDestination" in MAIN
    assert "BossMovementPolicy.chooseStuckFallback" in MAIN
    assert "horizontalDistanceSquared" in MAIN
    assert "configuredCombatVerticalRadius()" in MAIN
    assert "The pad coordinate is the air block above this floor" in MAIN


def test_wave_containment_watchdog_runs_for_test_waves_before_phase_gate() -> None:
    start = MAIN.index("private void tickWaveMobAi()")
    end = MAIN.index("private boolean isWaveCombatKind", start)
    body = MAIN[start:end]
    assert "enforceWaveMobContainment();" in body
    assert body.index("enforceWaveMobContainment();") < body.index("if (!isCombatPhase())")
    assert "private void enforceWaveMobContainment()" in MAIN
    assert "boundedCombatRadius(config.containmentRadius())" in MAIN
    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS" in MAIN


def test_wave_teleports_normalize_to_the_core_block_top_instead_of_stacking_inside_it() -> None:
    assert "private boolean isCoreBlockPosition(Location location)" in MAIN
    assert "private Location coreBlockTopLocation()" in MAIN
    assert "if (isCoreBlockPosition(target))" in MAIN
    assert "Location safe = findSafeCombatLocation(anchor, null, radius - 0.75D, minimum);" in MAIN
    assert "event.setTo(safe);" in MAIN


def test_wave_spawns_use_the_floor_below_an_elevated_core_and_never_fall_back_onto_it() -> None:
    start = MAIN.index("private Location safeSpawnLocation(Location core, int index, double offset)")
    end = MAIN.index("@EventHandler(priority = EventPriority.HIGHEST", start)
    body = MAIN[start:end]
    assert "Location floorAnchor = coreCombatAnchorLocation();" in body
    assert "coreY + 1" in MAIN[MAIN.index("private int combatLevelY()"):MAIN.index("private Location coreBlockTopLocation")]
    assert "Location candidate = spawnLocation(floorAnchor, index + attempt, offset);" in body
    assert "return null;" in body
    assert "core.clone().subtract(0.0D, 1.0D, 0.0D)" not in body


def test_wave_leash_uses_the_core_block_level_floor_instead_of_the_core_top() -> None:
    start = MAIN.index("private void enforceWaveMobContainment()")
    end = MAIN.index("private boolean isWaveCombatKind", start)
    body = MAIN[start:end]
    assert "private Location coreCombatAnchorLocation()" in MAIN
    assert "Location anchor = coreCombatAnchorLocation();" in body


def test_boss_and_miniboss_spells_have_a_visible_particle_flight_before_impact() -> None:
    for marker in (
        "launchSpellFlight",
        "BOSS_SPELL_FLIGHT",
        "MINIBOSS_SPELL_FLIGHT",
        "spell-flight",
        "spawnParticle",
        "taskRegistry.owns(callbackGeneration)",
    ):
        assert marker in MAIN


def test_every_boss_and_miniboss_flight_uses_a_named_visual_pattern() -> None:
    flight = MAIN[MAIN.index("private void launchSpellFlight"):MAIN.index("private boolean isSpellFlightAllowed")]
    assert "spawnSpellFlightPattern" in flight
    assert "spawnRiftProjectileTrail" in MAIN
    for spell_id in (
        "void_blast", "rift_projectile", "void_mark", "summon", "will_distortion",
        "rift_step", "void_snare", "echo_pulse",
    ):
        assert f'case "{spell_id}"' in MAIN


def test_final_wave_elites_keep_their_bound_spell_flight_path() -> None:
    flight = MAIN[MAIN.index("private boolean isSpellFlightAllowed"):MAIN.index("private void miniBossRiftStep")]
    assert "return (EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_FINAL_WAVE.equals(kind))" in flight
    assert "&& isMiniBossCombatPhase();" in flight
