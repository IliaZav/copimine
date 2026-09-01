from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_boss_configures_scaled_virtual_hp_without_exceeding_papers_physical_limit() -> None:
    configure = _body("private void configureBoss(Enderman boss, boolean test)", "private void ensureBossBar()")
    assert "BOSS_PHYSICAL_HEALTH_LIMIT = 2048.0D" in MAIN
    assert "keyBossVirtualHealth" in MAIN
    assert "keyBossVirtualMaxHealth" in configure
    assert "maxHealth.setBaseValue(Math.min(configuredMaxHealth, BOSS_PHYSICAL_HEALTH_LIMIT));" in configure
    assert "setBossVirtualHealth(boss, configuredMaxHealth);" in configure
    assert "boss.setHealth(config.bossHealth());" not in configure


def test_boss_thresholds_and_bossbar_use_the_active_virtual_hp_pool() -> None:
    synchronize = _body("private void synchronizeBossStage", "private void updateBossBar")
    bar = _body("private void updateBossBar", "private int randomSeconds")
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    servants = _body("private boolean servantSummonWindow", "private void summonServants")
    summon = _body("private void summonServants", "public void onOwnedEntityDeath")
    assert "bossVirtualHealth(boss)" in synchronize
    assert "bossMaxHealth(boss)" in bar
    assert "virtualHealth / max" in bar
    assert "bossVirtualHealth(boss)" in damage
    assert "double maxHealth = bossMaxHealth(boss)" in damage
    assert "scaledBossThreshold(config.bossHalfHealth(), boss)" in damage
    assert "bossVirtualHealth(boss)" in servants
    assert "bossVirtualHealth(boss)" in summon


def test_final_damage_is_cancelled_and_applied_to_virtual_health_until_real_zero() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    final_branch = damage[damage.index("phase == EventPhase.BOSS_FINISH"):damage.index("if (phase != EventPhase.BOSS_ACTIVE)")]
    assert "event.setCancelled(true);" in final_branch
    assert "applyBossDamage(boss" in final_branch
    assert "event.setCancelled(false);" not in final_branch


def test_virtual_boss_health_is_persisted_in_entity_pdc_and_can_restore_after_reload() -> None:
    assert "PersistentDataType.DOUBLE" in MAIN
    assert "keyBossVirtualHealth" in MAIN
    assert "bossVirtualHealth(LivingEntity boss)" in MAIN
    assert "getPersistentDataContainer().get(keyBossVirtualHealth" in MAIN


def test_local_test_boss_damage_uses_the_same_virtual_health_path() -> None:
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    assert "isTestBoss(boss) && !testCombatAiMode" in damage
    assert "applyBossDamage(boss, damage, null)" in MAIN
