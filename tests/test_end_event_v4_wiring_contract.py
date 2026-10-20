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


def call_arguments(source: str, signature: str) -> list[str]:
    opening = source.index("(", source.index(signature))
    arguments = []
    start = opening + 1
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            if depth == 0:
                arguments.append(source[start:index].strip())
                return arguments
            depth -= 1
        elif char == "," and depth == 0:
            arguments.append(source[start:index].strip())
            start = index + 1
    raise AssertionError(f"unterminated invocation: {signature}")


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


def test_wave_two_control_lease_matches_signed_v4_body_contract():
    event = EVENT_SOURCE.read_text(encoding="utf-8")
    sender = (ROOT / "copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java").read_text(encoding="utf-8")
    codec = (ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EndEventBridgeBodyCodec.java").read_text(encoding="utf-8")

    start = call_arguments(event, 'sendClientPacket(marked, "END_CONTROL_START"')
    stop = call_arguments(event, 'sendClientPacket(marked, "END_CONTROL_STOP"')
    assert start[2] == "waveTwoControlInstanceId"
    assert start[4:6] == ['""', '""'], "REVERSE belongs in the signed controlId field, not visualId"
    assert start[11] == '"REVERSE"'
    assert stop[2] == "waveTwoControlInstanceId"
    assert stop[4:6] == ['""', '""'] and stop[6:9] == ["0", "0", "0.0F"]
    assert stop[9:] == ['""'] * 3, "STOP carries only its canonical lease UUID"
    assert "UUID.nameUUIDFromBytes(" in event[event.index("waveTwoControlInstanceId =", event.index('"END_CONTROL_START"') - 800):event.index('"END_CONTROL_START"')]

    # The generic production adapter maps the final `source` argument to
    # Body.controlId, while the protocol validator requires a canonical UUID
    # and an empty visualId for these control leases.
    assert "context.eventUuid(), nullToEmpty(instanceId), nullToEmpty(source)," in sender
    assert "nullToEmpty(visualId), duration" in sender
    assert 'body.controlId().equals("REVERSE")' in codec
    assert 'body.visualId().isEmpty()' in codec
    assert 'requireCanonicalUuid(body.instanceId(), "control lease UUID")' in codec
