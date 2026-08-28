from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")


def _method_body(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


def test_official_boss_creates_a_visible_health_bar_for_active_players() -> None:
    configure = _method_body(MAIN, "private void configureBoss(Enderman boss, boolean test)", "private void ensureBossBar()")
    ensure = _method_body(MAIN, "private void ensureBossBar()", "private LivingEntity liveBoss()")
    tick = _method_body(MAIN, "private void tickBoss()", "private int randomSeconds")
    assert "ensureBossBar();" in configure
    assert "Bukkit.createBossBar" in ensure
    assert "BarColor.PURPLE" in ensure
    assert "BarStyle.SEGMENTED_20" in ensure
    assert "bossBar.setVisible(true)" in ensure
    assert "bossBar.addPlayer(player)" in ensure
    assert "bossBar.setProgress" in tick
    assert "virtualHealth / max" in tick
    assert "bossBar.setTitle" in tick
    assert "bossBar.removePlayer(player)" in tick
    assert "bossBarLastUpdateMillis" in MAIN
    assert "now - bossBarLastUpdateMillis < 200L" in MAIN
    assert "bossBarLastTitle" in MAIN


def test_boss_bar_is_removed_when_the_boss_or_event_session_is_cleaned() -> None:
    clear_boss = _method_body(MAIN, "private void clearBossOnly()", "private void tickBoss()")
    victory = _method_body(MAIN, "private void beginVictory()", "private void unlockEnd")
    disable = _method_body(MAIN, "public void onDisable()", "private boolean isAdmin")
    assert "bossBar.removeAll();" in clear_boss
    assert "bossBar.removeAll();" in victory
    assert "bossBar.removeAll();" in disable


def test_core_removal_cleans_event_owned_combat_roles_even_after_a_restart_generation() -> None:
    cleanup = _method_body(MAIN, "private void cleanupOwnedEntitiesForEvent(String expectedEventId)", "private void cleanupOwnedEntities(String expectedEventId, long expectedGeneration)")
    assert "isEndEventOwnedRole(entity)" in cleanup
    assert "ownedByEvent(entity, expectedEventId)" in cleanup
    assert "entity.remove();" in cleanup
    assert "Bukkit.getWorlds()" in cleanup
