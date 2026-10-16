from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def _read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def test_end_event_server_uses_the_migration_client_bridge_protocol_version():
    server = _read("copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java")
    client_policy = _read(
        "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/ClientModHandshakePolicy.java"
    )
    v4_sender = _read("copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java")
    signing_policy = _read("tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeSigningPolicy.java")

    assert re.search(r"PROTOCOL_VERSION\s*=\s*3\s*;", client_policy)
    assert re.search(r"CLIENT_BRIDGE_PROTOCOL_VERSION\s*=\s*3\s*;", server)
    assert server.count("protocol != CLIENT_BRIDGE_PROTOCOL_VERSION") >= 3
    assert 'int protocol = input.readInt();' in server
    assert "Never send unsigned V3 fallbacks for End Event controls." in server
    assert "endEventBridgeV4.send(player.getUniqueId(), type, instanceId," in server
    assert "metadata.protocolVersion() != BridgeSigningPolicy.PROTOCOL_VERSION" in v4_sender
    assert re.search(r"PROTOCOL_VERSION\s*=\s*4\s*;", signing_policy)


def test_migration_client_event_decoders_accept_the_current_bridge_version():
    client_protocol = _read(
        "tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocol.java"
    )
    echo_packet = _read(
        "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EchoPresentationPacket.java"
    )
    player_visuals = _read(
        "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventPlayerVisualManager.java"
    )

    assert "payload.protocol() != PROTOCOL_VERSION" in client_protocol
    assert "payload.protocol() != ClientBridgeProtocol.PROTOCOL_VERSION" in echo_packet
    assert "payload.protocol() != ClientBridgeProtocol.PROTOCOL_VERSION" in player_visuals


def test_pending_client_cannot_mount_a_vehicle_before_mod_verification():
    bridge = _read("copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java")

    assert "import org.bukkit.event.entity.EntityMountEvent;" in bridge
    assert re.search(
        r"@EventHandler\(priority\s*=\s*EventPriority\.HIGHEST,\s*ignoreCancelled\s*=\s*true\)\s*public void onPendingMount",
        bridge,
    ), "pending-client mount protection must be registered for cancelled, highest-priority events"
    mount_handler = re.search(
        r"public void onPendingMount\(EntityMountEvent event\)\s*\{(?P<body>.*?)\n    \}",
        bridge,
        re.DOTALL,
    )
    assert mount_handler, "pending-client vehicle mounting must have a dedicated event guard"
    assert "event.getEntity() instanceof Player player" in mount_handler.group("body")
    assert "cancelIfPending(player, event)" in mount_handler.group("body")


def test_pending_client_auth_commands_reject_unrelated_plugin_namespaces():
    bridge = _read("copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java")
    command_policy = _read("tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/PendingClientCommandPolicy.java")

    assert "import me.copimine.protocol.PendingClientCommandPolicy;" in bridge
    assert "return PendingClientCommandPolicy.allowsAuthenticationCommand(input);" in bridge
    assert 'AUTHENTICATION_NAMESPACE = "authme"' in command_policy
    assert "command.substring(0, namespaceSeparator).equalsIgnoreCase(AUTHENTICATION_NAMESPACE)" in command_policy
    assert "command.indexOf(':', namespaceSeparator + 1) >= 0" in command_policy


def test_client_mod_docs_describe_reported_identity_without_claiming_install_attestation():
    protocol = _read("CopiMineClient/PROTOCOL.md")
    readme = _read("CopiMineClient/README_INSTALL_RU.md")

    assert "challenge response only when it reports mod ID" in protocol
    assert "This protocol does not prove that an unmodified copy of the mod is installed." in protocol
    assert "A repeated `hello` for an already verified session is acknowledged with `ping`" in protocol
    assert "клиент сообщает mod ID" in readme
    assert "не доказывает, что клиент использует неизменённый JAR мода" in readme
    assert "verifies the installed CopiMineClient version" not in protocol
    assert "сервер проверяет только этот мод" not in readme


def test_paper_bridge_test_fork_has_a_bounded_heap_and_single_worker():
    gradle = _read("tools/minecraft-26.3/build.gradle")
    task = re.search(
        r"tasks\.register\('bridgeServerTest', Test\)\s*\{(?P<body>.*?)\n\}",
        gradle,
        re.DOTALL,
    )

    assert task is not None
    assert "maxParallelForks = 1" in task.group("body")
    assert "maxHeapSize = '128m'" in task.group("body")
