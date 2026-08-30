from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"


def test_end_event_uses_a_legacy_safe_v2_bridge_envelope() -> None:
    protocol = (CLIENT / "src/main/java/me/copimine/client/ClientBridgeProtocol.java").read_text(encoding="utf-8")
    payload = (CLIENT / "src/main/java/me/copimine/client/BridgePayload.java").read_text(encoding="utf-8")
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert '"copimine:client_bridge"' in protocol
    assert 'TYPE_END_EVENT_PREFIX = "END_EVENT:"' in protocol
    assert 'output.writeUTF("END_EVENT:" + type)' in server
    assert 'output.writeInt(2)' in server
    assert 'output.writeLong(System.currentTimeMillis())' in server
    assert 'output.writeBoolean(false)' in server
    assert 'output.writeInt(0)' in server
    assert 'output.writeUTF(visualId == null ? "" : visualId)' in server
    assert "COPIMINE_END_EVENT_V1" not in server
    assert "client_bridge_v2" not in protocol
    assert "client_gate" not in protocol.lower()
    assert "TYPE_END_EVENT_PREFIX" in payload
    assert "applyEndEventPayload" in protocol


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


def test_custom_end_rift_boss_bar_is_registered_and_keeps_vanilla_bars_scoped() -> None:
    mixins = json.loads((CLIENT / "src/main/resources/copimineclient.mixins.json").read_text(encoding="utf-8"))
    state = (CLIENT / "src/main/java/me/copimine/client/EndEventClientState.java").read_text(encoding="utf-8")
    hud = (CLIENT / "src/main/java/me/copimine/client/EndRiftBossBarHud.java").read_text(encoding="utf-8")
    mixin = (CLIENT / "src/main/java/me/copimine/client/mixin/EndRiftBossBarHudMixin.java").read_text(encoding="utf-8")
    frame = CLIENT / "src/main/resources/assets/copimineclient/textures/gui/end_rift_bossbar_frame.png"
    assert "EndRiftBossBarHudMixin" in mixins["client"]
    assert "BossBarState" in state and "hasActiveBossBar" in state
    assert "copimineclient" in hud and "end_rift_bossbar_frame.png" in hud
    assert "title.startsWith(\"Страж Разлома\")" in mixin
    assert frame.is_file() and frame.stat().st_size > 100


def test_end_event_client_texture_is_packaged_source() -> None:
    texture_dir = CLIENT / "src/main/resources/assets/copimineclient/textures/entity"
    for name in (
        "end_rift_enderman.png",
        "end_rift_elite.png",
        "end_rift_guardian.png",
        "end_rift_spider.png",
        "end_rift_shulker.png",
        "end_rift_skeleton.png",
        "end_rift_elite_skeleton.png",
    ):
        texture = texture_dir / name
        assert texture.is_file()
        assert texture.stat().st_size > 100


def test_end_event_texture_catalog_covers_every_bound_entity_and_exposes_diagnostics() -> None:
    catalog = (CLIENT / "src/main/java/me/copimine/client/EndEventTextureCatalog.java").read_text(encoding="utf-8")
    client = (CLIENT / "src/main/java/me/copimine/client/CopiMineClient.java").read_text(encoding="utf-8")
    for visual_id in (
        "END_RIFT_ENDERMAN_V1",
        "END_RIFT_ELITE_V1",
        "END_RIFT_SPIDER_V1",
        "END_RIFT_SHULKER_V1",
        "END_RIFT_SKELETON_V1",
        "END_RIFT_ELITE_SKELETON_V1",
    ):
        assert visual_id in catalog
    assert "getResourceManager().getResource(texture).isPresent()" in catalog
    assert "copimineclient endrift textures" not in client
    assert 'literal("endrift")' in client
    assert 'literal("textures")' in client
    for mixin_name in (
        "EndermanEntityRendererMixin.java",
        "SpiderEntityRendererMixin.java",
        "ShulkerEntityRendererMixin.java",
        "SkeletonEntityRendererMixin.java",
    ):
        mixin = (CLIENT / "src/main/java/me/copimine/client/mixin" / mixin_name).read_text(encoding="utf-8")
        assert "EndEventTextureCatalog.isAvailable" in mixin


def test_skeleton_renderer_mixin_uses_the_runtime_abstract_skeleton_signature() -> None:
    mixin = (CLIENT / "src/main/java/me/copimine/client/mixin/SkeletonEntityRendererMixin.java").read_text(encoding="utf-8")

    # Yarn's 1.21.1 renderer accepts AbstractSkeletonEntity.  Keeping the
    # contract here prevents a compile-valid but runtime-invalid injection
    # from making the client drop back to vanilla rendering or crash at load.
    assert "import net.minecraft.entity.mob.AbstractSkeletonEntity;" in mixin
    assert "AbstractSkeletonEntity entity" in mixin
    assert "(SkeletonEntity entity" not in mixin


def test_end_event_mob_bindings_are_uuid_scoped_and_not_global_overrides() -> None:
    state = (CLIENT / "src/main/java/me/copimine/client/EndEventClientState.java").read_text(encoding="utf-8")
    packet = (CLIENT / "src/main/java/me/copimine/client/EndEventPacket.java").read_text(encoding="utf-8")
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "END_ENTITY_BIND" in packet
    assert "entityVisuals" in state
    assert "visualForEntity" in state
    assert "END_RIFT_SPIDER_V1" in server
    assert "END_RIFT_SKELETON_V1" in server
    assert "END_RIFT_ELITE_SKELETON_V1" in server
    assert "END_RIFT_SHULKER_V1" not in server
    assert "bindEventEntityClientForOnlinePlayers" in server
    spider_mixin = (CLIENT / "src/main/java/me/copimine/client/mixin/SpiderEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "textures/entity/end_rift_spider.png" in spider_mixin


def test_client_bindings_retry_after_authentication_or_delayed_arena_entry() -> None:
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "clientBindingReadyPlayers" in server
    assert "refreshClientBindingsForOnlinePlayers" in server
    assert "refreshClientBindingsForPlayer" in server
    assert "nextClientBindingRefreshMillis" in server
    assert "clientBindingReadyPlayers.remove(uuid)" in server


def test_guardian_model_is_assignable_to_the_enderman_renderer_model_contract() -> None:
    model = (CLIENT / "src/main/java/me/copimine/client/RiftGuardianModel.java").read_text(encoding="utf-8")

    assert "extends EndermanEntityModel<EndermanEntity>" in model
    assert "super(root);" in model
    assert "SinglePartEntityModel" not in model
    for required_part in ("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        assert f'root.addChild("{required_part}"' in model
