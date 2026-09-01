from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
POLICY = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossHealthScalingPolicy.java").read_text(
    encoding="utf-8"
)
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_scaling_policy_has_the_requested_bounded_roster_curve() -> None:
    for marker in (
        "BASE_PLAYERS = 2",
        "SCALE_START_PLAYERS = 8",
        "MAX_PLAYERS = 20",
        "BASE_HEALTH = 5000.0D",
        "SCALE_START_HEALTH = 10000.0D",
        "MAX_HEALTH = 20000.0D",
        "maxHealthForPlayers",
        "scaleFromBase",
    ):
        assert marker in POLICY


def test_event_uses_the_frozen_roster_max_for_entity_virtual_pool_and_bossbar() -> None:
    configure = _body("private void configureBoss(Enderman boss, boolean test)", "private void ensureBossBar()")
    virtual = _body("private double bossVirtualHealth", "private void clearBossServants")
    bar = _body("private void updateBossBar", "private int randomSeconds")
    client_bar = _body("private void sendBossBarVisualUpdate", "private void sendBossPhaseVisualUpdate(Entity")
    assert "computedBossMaxHealth()" in configure
    assert "keyBossVirtualMaxHealth" in configure
    assert "double configuredMax = bossMaxHealth(boss)" in virtual
    assert "double max = bossMaxHealth(boss)" in bar
    assert "double max = bossMaxHealth(boss)" in client_bar
    assert "maxHealth.setBaseValue(Math.min(configuredMaxHealth, BOSS_PHYSICAL_HEALTH_LIMIT))" in configure


def test_all_boss_stage_and_final_threshold_checks_use_scaled_pool() -> None:
    stage = _body("private void synchronizeBossStage", "private void updateBossBar")
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    summon = _body("private boolean servantSummonWindow", "private void summonServants")
    assert "BossStagePolicy.stageFor(virtualHealth, maxHealth, judgmentTriggered)" in stage
    assert "BossStagePolicy.transition(" in stage
    assert "BossStagePolicy.upperThreshold(BossStage.ABSORPTION, maxHealth)" in damage
    assert "BossStagePolicy.judgmentThreshold(maxHealth)" in damage
    assert "scaledBossThreshold(config.bossHalfHealth(), boss)" in damage
    assert "scaledBossThreshold(config.bossFinalThreshold(), boss)" in damage
    assert "scaledBossThreshold(config.bossFinalHealth(), boss)" in damage
    assert "bossMaxHealth(boss)" in summon


def test_config_keeps_5000_as_the_base_anchor_and_documents_the_curve() -> None:
    assert "health: 5000.0" in CONFIG
    assert "5000 at 2 players to 10000 at 8" in CONFIG
    assert "then to 20000 at 20 players" in CONFIG
