from pathlib import Path


MAIN = (
    Path(__file__).resolve().parents[1]
    / "copimine-end-event"
    / "src/me/copimine/endevent/CopiMineEndEvent.java"
).read_text(encoding="utf-8")
ROOT = Path(__file__).resolve().parents[1]
CONFIG = (Path(__file__).resolve().parents[1] / "copimine-end-event" / "config.yml").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_boss_is_forced_off_the_solid_core_when_ai_has_stuck_on_it() -> None:
    leash = _body("private void enforceCombatLeash", "private double horizontalDistanceSquared")
    assert "isCoreBlockPosition(current)" in leash
    assert "!standingOnCore" in leash
    assert "outsideCombatVertical(current, anchor)" in leash
    assert "configuredCombatVerticalRadius()" in MAIN


def test_combat_anchor_uses_the_saved_rune_floor_for_a_core_replacing_any_block() -> None:
    anchor = _body("private Location coreCombatAnchorLocation", "private Location coreBlockTopLocation")
    spawn = _body("private Location safeSpawnLocation(Location core", "@EventHandler(priority = EventPriority.HIGHEST")
    assert "combatLevelY()" in anchor
    assert "PadSnapshot pad : pads" in anchor
    assert "Location floorAnchor = coreCombatAnchorLocation();" in spawn
    assert "floorAnchor.clone().subtract(0.0D, 1.0D, 0.0D)" not in spawn


def test_boss_final_release_explicitly_clears_invulnerability_before_player_damage() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    final_wave = _body("private void tickWaveCompletion", "private boolean spawnWaveCompletionLoot")
    assert "phase == EventPhase.BOSS_FINISH" in damage
    assert "finalDrainTriggered" in damage
    assert "boss.setInvulnerable(false)" in final_wave
    assert "damageAllowed" in damage
    assert "event.setCancelled(true);" in damage
    assert "applyBossDamage(boss" in damage
    assert "incomingDamageMultiplier" in damage
    assert "bossCastState = BossCastState.NONE;" in final_wave
    assert "bossCastDeadlineMillis = 0L;" in final_wave
    assert "bossSpellPauseUntilMillis = 0L;" in final_wave
    assert "cancelBossCastTask();" in final_wave


def test_health_thresholds_enter_bounded_absorption_and_judgment_casts() -> None:
    synchronize = _body("private void synchronizeBossStage", "private void updateBossBar")
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    assert "startAbsorptionChannel(boss)" in synchronize
    assert "!absorptionTriggered" in synchronize
    assert "BossStagePolicy.judgmentThreshold()" in damage
    assert "!judgmentTriggered" in damage


def test_boss_stage_controller_blocks_health_projection_regressions_and_logs_the_value() -> None:
    synchronize = _body("private void synchronizeBossStage", "private void updateBossBar")
    assert "BossStage requestedStage = BossStagePolicy.stageFor" in synchronize
    assert "BOSS_STAGE_REGRESSION_BLOCKED" in synchronize
    assert "requested=" in synchronize
    assert "health=" in synchronize
    assert "BossStagePolicy.transition" in synchronize


def test_absorption_threshold_pins_health_before_the_channel_can_be_skipped() -> None:
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    assert "setBossVirtualHealth(boss, BossStage.ABSORPTION.upperInclusive());" in damage
    assert "setBossVirtualHealth(boss, projectedHealth);" not in damage[:damage.index(
        "// Judgment begins at exactly 250 HP")
    ]
    assert "BOSS_CAST_STATE" in MAIN


def test_boss_damage_path_releases_expired_absorption_before_evaluating_hit() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert "releaseExpiredBossCastBeforeDamage(boss" in damage
    assert "bossCastDeadlineMillis" in damage
    assert "boss.setInvulnerable(false)" in damage


def test_boss_damage_path_audits_real_player_damage_events_before_policy_gates() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert "BOSS_DAMAGE_EVENT" in damage
    assert "event.getFinalDamage()" in damage
    assert "event.isCancelled()" in damage
    assert "event instanceof EntityDamageByEntityEvent" in damage


def test_unlocked_local_official_boss_harness_enters_active_phase_before_spawn() -> None:
    spawn = _body("private void spawnOfficialBoss", "private void configureBoss")
    assert "endUnlocked" in spawn
    assert "forcePhase(EventPhase.BOSS_ACTIVE" in spawn
    assert "official boss local harness" in spawn


def test_final_phase_failure_paths_clear_entity_invulnerability_before_recovery() -> None:
    body = _body("private void triggerFinalPhase", "private void applyFinalDrain")
    save_failure = body[body.index("if (!saveStateSync())"):body.index("clearClientEffects")]
    transition_failure = body[body.index("if (!transition(EventPhase.FINAL_DRAIN"):]
    assert "boss.setInvulnerable(false)" in save_failure
    assert "boss.setInvulnerable(false)" in transition_failure


def test_boss_balance_contract_is_2500_hp_with_five_point_increment_and_level_four_effects() -> None:
    assert "health: 2500.0" in CONFIG
    assert "attack-damage-bonus: 5.0" in CONFIG
    assert "debuff-amplifier: 3" in CONFIG
    assert "control-duration-seconds: 20" in CONFIG


def test_boss_cleanup_removes_spell_servants_and_their_ai_state() -> None:
    cleanup = _body("private void clearBossOnly", "private void tickBoss")
    assert "clearBossServants();" in cleanup
    helper = _body("private void clearBossServants", "private void tickBoss")
    assert "spellServants" in helper
    assert "servant.remove();" in helper
    assert "miniBossSpells.remove(servantId);" in helper
    assert "nextMiniBossSpellMillis.remove(servantId);" in helper


def test_official_boss_defeat_clears_all_wave_combat_entities_before_death() -> None:
    commit = _body("private void commitOfficialBossDefeat", "private void triggerFinalPhase")
    assert 'clearWaveCombatEntities("official-boss-defeat")' in commit
    cleanup = _body("private int clearWaveCombatEntities", "private boolean isLiveOwnedEntity")
    for marker in (
        "EVENT_KIND_WAVE_MOB",
        "EVENT_KIND_ELITE",
        "EVENT_KIND_FINAL_WAVE",
        "entity.remove();",
        "ownedEntities.remove(entity.getUniqueId());",
        "finalWaveEntities.clear();",
    ):
        assert marker in cleanup


def test_absorption_completion_is_durable_and_grants_one_bounded_follow_up_attack() -> None:
    assert "boolean absorptionCompleted" in (
        Path(ROOT / "copimine-end-event/src/me/copimine/endevent/EventSnapshot.java")
        .read_text(encoding="utf-8")
    )
    assert "boss.absorption-completed" in (
        Path(ROOT / "copimine-end-event/src/me/copimine/endevent/EventStateStore.java")
        .read_text(encoding="utf-8")
    )
    finish = _body("private void finishBossCast", "private void renderBossCastState")
    melee = _body("public void onBossMeleeAttack", "private boolean isArenaLocation")
    assert "absorptionCompleted = true;" in finish
    assert "absorptionAttackEmpowered = true;" in finish
    assert "boss.setInvulnerable(false);" in finish
    assert "absorptionAttackEmpowered = false;" in melee
    assert "profile.nextMeleeAttackBonus()" in melee
    assert "BOSS_ABSORPTION_BUFF_CONSUMED" in melee
