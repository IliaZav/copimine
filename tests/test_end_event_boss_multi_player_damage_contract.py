from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
REAL_HEALTH_POLICY = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EventRealHealthDamagePolicy.java").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_official_damage_commits_each_accepted_hit_to_real_entity_health() -> None:
    damage = _body("private void handleV2BossDamage", "private void applyBossDamage")
    assert "double healthBefore = boss.getHealth()" in damage
    assert "double finalDamage = Math.max(0.0D, event.getFinalDamage())" in damage
    assert "BossRealHealthDamagePolicy.apply(" in damage
    accepted = damage[damage.index("event.setCancelled(true);"):]
    assert "boss.setHealth(result.remainingHealth())" in accepted
    assert "event.setDamage(adjustedBaseDamage)" not in accepted
    assert "releaseEventCombatHurtWindow(boss)" not in accepted
    assert "authority=entity-health" in damage
    assert "BossVirtualHealthPolicy" not in damage
    assert "setBossVirtualHealth" not in damage


def test_real_health_damage_policy_does_not_depend_on_native_hurt_window() -> None:
    assert "BossRealHealthDamagePolicy" in MAIN
    assert "applySeries" in REAL_HEALTH_POLICY
    assert "before - requested" in REAL_HEALTH_POLICY
    assert "EVENT_ENTITY_MAX_NO_DAMAGE_TICKS = 3" in MAIN
    assert "configureEventCombatHurtWindow" in MAIN
    assert "releaseEventCombatHurtWindow(boss)" not in MAIN
    assert "releaseEventCombatHurtWindow(victim)" in MAIN
    assert "entity.setNoDamageTicks(0)" in MAIN
    assert "setMaximumNoDamageTicks(0)" not in MAIN


def test_hurt_window_reset_is_scoped_to_owned_event_entities() -> None:
    assert "if (!EVENT_KIND_BOSS.equals(kind) && !isWaveCombatKind(kind))" in MAIN
    assert "private void configureEventCombatHurtWindow" in MAIN
    assert "entity instanceof LivingEntity living" in MAIN
    assert "entity.setLastDamage(0.0D)" in MAIN


def test_bukkit_adapter_uses_each_event_final_damage_and_commits_real_health() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert damage.count("event.getFinalDamage()") >= 2
    assert "BossRealHealthDamagePolicy.apply(" in damage
    assert damage.count("event.setCancelled(true);") >= 3
    assert "handleV2BossDamage(event, boss, source)" in damage
    assert "applyBossDamage(boss" in damage
    assert "BOSS_DAMAGE_EVENT" in damage
    assert "source=" in damage
    assert "final=" in damage


def test_exhausted_multiplier_is_kept_once_per_hit_and_blocked_casts_still_fail_closed() -> None:
    damage_policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossDamagePolicy.java").read_text(encoding="utf-8")
    assert "EXHAUSTED ? 1.5D" in damage_policy
    assert "applyIncomingDamage" in MAIN
    assert "BOSS_DAMAGE_BLOCKED" in MAIN
    assert "BossCastState.ABSORPTION_CHANNEL" in MAIN
    assert "BossCastState.JUDGMENT_CAST" in MAIN


def test_official_configuration_uses_real_entity_health_and_clears_legacy_markers() -> None:
    configure = _body("private boolean configureBoss(Enderman boss, boolean test)", "private void ensureBossBar()")
    official = configure[configure.index("// V2 is authoritative"):]
    assert "maxHealth.setBaseValue(configuredMaxHealth)" in official
    assert "boss.setHealth(configuredMaxHealth)" in official
    assert "setBossVirtualHealth(boss, configuredMaxHealth)" not in official
    assert "keyBossVirtualHealth" in official
    assert "keyBossVirtualMaxHealth" in official
    assert ".remove(keyBossVirtualHealth)" in official
    assert ".remove(keyBossVirtualMaxHealth)" in official


def test_lethal_real_health_sequence_has_one_terminal_death_transaction() -> None:
    assert "commitOfficialBossDefeat(boss, source)" in MAIN
    assert "officialBossDeathCommitted" in MAIN
    damage = _body("private void handleV2BossDamage", "private void applyBossDamage")
    assert "result.lethal()" in damage
    assert "boss.setHealth(result.remainingHealth())" in damage
    assert "event.setDamage(adjustedBaseDamage)" not in damage


def test_local_admin_health_probe_uses_the_same_real_entity_value() -> None:
    admin = _body("private void applyV2AdministrativeDamage", "private void triggerHalfPhase")
    assert "double before" in admin
    assert "boss.getHealth()" in admin
    assert "boss.setHealth(after)" in admin
    assert "synchronizeV2BossStage(boss)" in admin
    assert "BossVirtualHealthPolicy" not in admin


def test_multiplayer_probe_uses_the_official_real_health_boss_and_independent_clients() -> None:
    probe = (ROOT / "tests/RunEndRiftBossMultiPlayerDamageLive.ps1").read_text(encoding="utf-8")
    bot = (ROOT / "tests/LocalEndRiftBossCombatBot.js").read_text(encoding="utf-8")
    assert "function Plain" in probe
    assert "cmend boss spawn official confirm" in probe
    assert '" Health"' in probe
    assert '" minecraft:generic.max_health get"' in probe
    assert "BOSS_V2_DAMAGE_ACCEPTED" in probe
    assert "BOSS_V2_DAMAGE_ACCEPTED" in MAIN
    assert "end_event_boss_virtual_health" not in probe
    assert "cmend boss freeze" in probe
    assert "release-attacks.barrier" in probe
    assert "END_RIFT_BOSS_ATTACK_BARRIER" in probe
    assert "function Wait-LocalAuthentication" in probe
    assert "logged in" in probe
    assert "hand: 0" in bot
    assert "fs.existsSync(attackBarrierPath)" in bot
    assert "authRetryDelaysMs" in bot
    assert "[500, 2000, 5000, 9000, 13000]" in bot
    assert "function startFollowing ()" in bot
    assert "startFollowing()" in bot
    assert bot.index("startFollowing()") < bot.index("tryAttack()")
    assert "minecraft:teleport" in probe


def test_local_damage_probe_can_freeze_the_official_v2_boss_without_changing_production_ai() -> None:
    freeze = _body('case "freeze", "unfreeze"', 'case "kill"')
    tick = _body("private void tickV2Boss", "private BossStagePolicy.CombatProfile currentBossCombatProfile")
    assert "isV2OfficialBoss(boss)" in freeze
    assert "testBossMovementFrozen" in tick
    assert "if (testBossMovementFrozen && isV2OfficialBoss(boss))" in tick
    assert "maintainBossPath(boss, now)" in tick
    assert tick.index("if (testBossMovementFrozen && isV2OfficialBoss(boss))") < tick.index("maintainBossPath(boss, now)")
    assert "isV2OfficialBoss(entity)" in MAIN


def test_multiplayer_bot_does_not_consume_combat_window_during_setup() -> None:
    bot = (ROOT / "tests/LocalEndRiftBossCombatBot.js").read_text(encoding="utf-8")
    probe = (ROOT / "tests/RunEndRiftBossMultiPlayerDamageLive.ps1").read_text(encoding="utf-8")
    start = bot.index("function startAttacking ()")
    release = bot.index("function releaseAttacksAfterBarrier ()")
    assert "function armDurationTimer ()" in bot
    assert "armDurationTimer()" in bot[start:release]
    assert "\nsetTimeout(() => bot.quit(), durationMs)" not in bot
    assert "const attackBarrierTimeoutMs" in bot
    assert "END_RIFT_BOSS_BARRIER_TIMEOUT_MS" in probe
