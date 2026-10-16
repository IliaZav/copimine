from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
EVENT_SOURCE = PLUGIN / "CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[opening + 1 : end - 1]


def test_event_startup_registers_signed_v4_and_freshens_existing_connections():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    startup = method_body(source, "private void tryBootstrap()")

    assert "EndEventBridgeV4Bootstrap.create(this)" in startup
    assert "endEventBridgeV4.register(this)" in startup
    assert "endEventBridgeV4.requestFreshChallenges(" in startup


def test_event_visuals_are_sent_only_over_ready_v4_streams():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    refresh = method_body(source, "private void refreshClientBindingsForPlayer(Player player)")
    send = method_body(source, "private void sendClientPacket(Player player, String type, String instanceId, long durationMillis,\n"
                               "                                  String subjectId, String visualId, int health, int maxHealth,\n"
                               "                                  float progress, String clearPolicy, String status, String source)")

    assert "endEventBridgeV4.isReady(playerUuid)" in refresh
    assert "endEventBridgeV4.send(" in send
    assert "player.sendPluginMessage(this, config.bridgeChannel()" not in send


def test_non_viewer_refresh_revokes_signed_event_context():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    refresh_all = method_body(source, "private void refreshClientBindingsForOnlinePlayers()")
    per_player_clear = method_body(source, "private void clearClientEffects(Player player)")

    assert "clearClientEffects(player);" in refresh_all
    assert "endEventBridgeV4.unbindContext(player.getUniqueId())" in per_player_clear


def test_v3_channel_cannot_dispatch_prisoner_ability_mutations():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    inbound = method_body(source, "public void onPluginMessageReceived(String channel, Player player, byte[] message)")

    assert "handleRitualPrisonerAbilityRequest(" not in inbound
    assert "END_PRISONER_ABILITY_REQUEST" not in inbound


def test_v4_ability_adapter_rechecks_current_event_and_generation():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    context = method_body(source, "EndEventBridgeV4Access.EventContext currentEndEventBridgeContext(UUID playerUuid)")
    ability = method_body(source, "void handleEndEventBridgeAbility(Player player,")

    assert "isEventVisualViewer(player)" in context
    assert "request.eventUuid()" in ability
    assert "request.generation() != generation" in ability
    assert "handleRitualPrisonerAbilityRequest(" in ability
