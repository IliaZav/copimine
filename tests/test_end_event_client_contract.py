from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"


def test_end_event_uses_the_existing_bridge_channel_only() -> None:
    protocol = (CLIENT / "src/main/java/me/copimine/client/ClientBridgeProtocol.java").read_text(encoding="utf-8")
    payload = (CLIENT / "src/main/java/me/copimine/client/BridgePayload.java").read_text(encoding="utf-8")
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert '"copimine:client_bridge"' in protocol
    assert "END_EVENT_MAGIC" in protocol
    assert "COPIMINE_END_EVENT_V1" in server
    assert "client_bridge_v2" not in protocol
    assert "client_gate" not in protocol.lower()
    assert "EndEventPacket.readAfterMagic" in payload


def test_end_event_client_has_cleanup_and_optional_control_mixins() -> None:
    mixins = json.loads((CLIENT / "src/main/resources/copimineclient.mixins.json").read_text(encoding="utf-8"))
    assert "KeyboardInputReverseMovementMixin" in mixins["client"]
    assert "EndermanEntityRendererMixin" in mixins["client"]
    assert "SpiderEntityRendererMixin" in mixins["client"]
    assert "ShulkerEntityRendererMixin" in mixins["client"]
    client = (CLIENT / "src/main/java/me/copimine/client/CopiMineClient.java").read_text(encoding="utf-8")
    protocol = (CLIENT / "src/main/java/me/copimine/client/ClientBridgeProtocol.java").read_text(encoding="utf-8")
    assert "clearEndEventState" in client
    assert "onDisconnect" in protocol
    assert "isReverseMovementActive" in protocol
    assert "isBoundEndBoss" in protocol
    assert "endEventVisualForEntity" in protocol


def test_end_event_client_texture_is_packaged_source() -> None:
    texture_dir = CLIENT / "src/main/resources/assets/copimineclient/textures/entity"
    for name in (
        "end_rift_enderman.png",
        "end_rift_elite.png",
        "end_rift_guardian.png",
        "end_rift_spider.png",
        "end_rift_shulker.png",
    ):
        texture = texture_dir / name
        assert texture.is_file()
        assert texture.stat().st_size > 100


def test_end_event_mob_bindings_are_uuid_scoped_and_not_global_overrides() -> None:
    state = (CLIENT / "src/main/java/me/copimine/client/EndEventClientState.java").read_text(encoding="utf-8")
    packet = (CLIENT / "src/main/java/me/copimine/client/EndEventPacket.java").read_text(encoding="utf-8")
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "END_ENTITY_BIND" in packet
    assert "entityVisuals" in state
    assert "visualForEntity" in state
    assert "END_RIFT_SPIDER_V1" in server
    assert "END_RIFT_SHULKER_V1" in server
    assert "bindEventEntityClientForOnlinePlayers" in server
    spider_mixin = (CLIENT / "src/main/java/me/copimine/client/mixin/SpiderEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "textures/entity/end_rift_spider.png" in spider_mixin
