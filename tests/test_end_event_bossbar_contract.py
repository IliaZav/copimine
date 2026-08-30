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


def test_custom_boss_bar_is_uuid_scoped_and_receives_authoritative_health_state() -> None:
    client_state = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java").read_text(encoding="utf-8")
    protocol = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java").read_text(encoding="utf-8")
    hud = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndRiftBossBarHud.java").read_text(encoding="utf-8")
    mixin = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/mixin/EndRiftBossBarHudMixin.java").read_text(encoding="utf-8")
    frame = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/gui/end_rift_bossbar_frame.png"
    assert '"END_BOSS_BAR"' in protocol
    assert "applyBossBar" in protocol
    assert "Objects.equals(bossUuid, packet.subjectId())" in client_state
    assert "Objects.equals(bossBindingInstance, packet.instanceId())" in client_state
    assert "health()" in hud and "maxHealth()" in hud and "progress()" in hud
    assert "drawTexture(FRAME" in hud
    assert "SOURCE_WIDTH = 2172" in hud
    assert "SOURCE_HEIGHT = 724" in hud
    assert "WIDTH = 384" in hud and "HEIGHT = 128" in hud
    assert "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V" in mixin
    assert "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V" in mixin
    assert 'method = "render(Lnet/minecraft/client/gui/DrawContext;)V"' in mixin
    assert 'target = "Ljava/util/Map;values()Ljava/util/Collection;"' in mixin
    assert "copimine$filterVanillaEndRiftBars" in mixin
    assert "title.startsWith(\"Хранитель Разлома\")" in mixin
    assert "title.startsWith(\"Страж Разлома\")" in mixin
    assert frame.is_file() and frame.stat().st_size > 100


def test_server_sends_custom_bar_snapshot_without_changing_the_vanilla_fallback() -> None:
    send = _method_body(MAIN, "private void sendBossBarVisualUpdate", "private void sendBossPhaseVisualUpdate(Entity")
    packet = _method_body(MAIN, "private void sendClientPacket(Player player, String type, String instanceId, long durationMillis,\n                                  String subjectId, String visualId, int health", "    @Override")
    assert '"END_BOSS_BAR"' in send
    assert "bossVirtualHealth(boss)" in send
    assert "stage.name() + \"|\" + bossCastState.name()" in send
    assert "output.writeFloat" in packet
    assert "output.writeInt(Math.max(0, Math.min(10_000, health)))" in packet
    assert "output.writeInt(Math.max(0, Math.min(10_000, maxHealth)))" in packet


def test_boss_bar_is_rebound_after_a_player_respawns_during_combat() -> None:
    assert "import org.bukkit.event.player.PlayerRespawnEvent;" in MAIN
    respawn = _method_body(MAIN, "public void onPlayerRespawn(PlayerRespawnEvent event)", "public void onShardChannelDamage")
    assert "clientBindingReadyPlayers.remove(player.getUniqueId())" in respawn
    assert "Bukkit.getScheduler().runTaskLater(this, () -> refreshClientBindingsForPlayer(player), 2L)" in respawn


def test_core_removal_cleans_event_owned_combat_roles_even_after_a_restart_generation() -> None:
    cleanup = _method_body(MAIN, "private void cleanupOwnedEntitiesForEvent(String expectedEventId)", "private void cleanupOwnedEntities(String expectedEventId, long expectedGeneration)")
    assert "isEndEventOwnedRole(entity)" in cleanup
    assert "ownedByEvent(entity, expectedEventId)" in cleanup
    assert "entity.remove();" in cleanup
    assert "Bukkit.getWorlds()" in cleanup
