from pathlib import Path


MAIN = (
    Path(__file__).resolve().parents[1]
    / "copimine-end-event"
    / "src/me/copimine/endevent/CopiMineEndEvent.java"
).read_text(encoding="utf-8")
CONFIG = (Path(__file__).resolve().parents[1] / "copimine-end-event" / "config.yml").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_boss_is_forced_off_the_solid_core_when_ai_has_stuck_on_it() -> None:
    leash = _body("private void enforceCombatLeash", "private double horizontalDistanceSquared")
    assert "isCoreBlockPosition(current)" in leash
    assert "!standingOnCore" in leash
    assert "configuredCombatVerticalRadius()" in leash


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


def test_boss_damage_path_releases_expired_absorption_before_evaluating_hit() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert "releaseExpiredBossCastBeforeDamage(boss" in damage
    assert "bossCastDeadlineMillis" in damage
    assert "boss.setInvulnerable(false)" in damage


def test_final_phase_failure_paths_clear_entity_invulnerability_before_recovery() -> None:
    body = _body("private void triggerFinalPhase", "private void applyFinalDrain")
    save_failure = body[body.index("if (!saveStateSync())"):body.index("clearClientEffects")]
    transition_failure = body[body.index("if (!transition(EventPhase.FINAL_DRAIN"):]
    assert "boss.setInvulnerable(false)" in save_failure
    assert "boss.setInvulnerable(false)" in transition_failure


def test_boss_balance_contract_is_2500_hp_with_five_point_increment_and_level_four_effects() -> None:
    assert "health: 2500.0" in CONFIG
    assert "attack-damage-bonus: 8.0" in CONFIG
    assert "debuff-amplifier: 3" in CONFIG
