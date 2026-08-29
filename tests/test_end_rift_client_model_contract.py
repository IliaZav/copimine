from __future__ import annotations

import struct
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"
JAR = CLIENT / "build/libs/CopiMineClient-0.1.0.jar"
SOURCE_ROOT = CLIENT / "src/main/java/me/copimine/client"
TEXTURE_ROOT = CLIENT / "src/main/resources/assets/copimineclient/textures/entity"

BOSS_PHASE_TEXTURES = (
    "rift_guardian_awakening.png",
    "rift_guardian_hunter.png",
    "rift_guardian_distortion.png",
    "rift_guardian_absorption.png",
    "rift_guardian_catastrophe.png",
)

LEGACY_EVENT_TEXTURES = (
    "end_rift_enderman.png",
    "end_rift_elite.png",
    "end_rift_spider.png",
    "end_rift_shulker.png",
    "end_rift_skeleton.png",
    "end_rift_elite_skeleton.png",
)


def test_client_jar_packages_guardian_model_and_all_end_rift_mob_textures() -> None:
    assert JAR.is_file(), f"Build the client JAR first: {JAR}"

    required_entries = {
        "me/copimine/client/RiftGuardianModel.class",
        "me/copimine/client/RiftGuardianModelRenderer.class",
        "me/copimine/client/RiftGuardianModelRenderer$Phase.class",
        "copimineclient.mixins.json",
    }
    required_entries.update(
        f"assets/copimineclient/textures/entity/{name}"
        for name in (*BOSS_PHASE_TEXTURES, *LEGACY_EVENT_TEXTURES)
    )

    with zipfile.ZipFile(JAR) as archive:
        names = set(archive.namelist())
        missing = sorted(required_entries - names)
        assert not missing, "Client JAR is missing required End Rift entries: " + ", ".join(missing)


def test_phase_textures_are_real_bounded_png_assets_and_not_identical() -> None:
    blobs: list[bytes] = []
    for name in BOSS_PHASE_TEXTURES:
        texture = TEXTURE_ROOT / name
        assert texture.is_file(), f"Missing source texture: {name}"
        blob = texture.read_bytes()
        blobs.append(blob)
        assert len(blob) > 100, f"Texture is empty or placeholder-sized: {name}"
        assert blob.startswith(b"\x89PNG\r\n\x1a\n"), f"Texture is not a PNG: {name}"
        width, height = struct.unpack(">II", blob[16:24])
        assert (width, height) == (128, 128), f"{name} must be 128x128, got {width}x{height}"

    assert len(set(blobs)) > 1, "Boss phase textures must not all be byte-identical"


def test_phase_protocol_uses_bridge_v2_fields_and_rejects_unknown_types() -> None:
    packet = (SOURCE_ROOT / "EndEventPacket.java").read_text(encoding="utf-8")
    bridge = (SOURCE_ROOT / "ClientBridgeProtocol.java").read_text(encoding="utf-8")
    payload = (SOURCE_ROOT / "BridgePayload.java").read_text(encoding="utf-8")

    assert '"END_BOSS_PHASE"' in packet
    assert '"END_BOSS_PHASE"' in bridge
    assert "TYPE_END_EVENT_PREFIX" in bridge
    assert "payload.seq()" in bridge
    assert "payload.clientVersion()" in bridge
    assert "payload.mode()" in bridge
    assert "payload.clearPolicy()" in bridge
    assert "payload.source()" in bridge
    assert "packet.phaseId()" in payload
    assert "Unknown End Rift event type" in packet


def test_state_tracks_boss_phase_by_uuid_and_binding_generation() -> None:
    state = (SOURCE_ROOT / "EndEventClientState.java").read_text(encoding="utf-8")

    assert "applyBossPhase" in state
    assert "BossPhaseBinding" in state
    assert "bossPhaseForEntity" in state
    assert "bossPhase.clear()" in state
    assert "packet.generation() < generation" in state
    assert "bossBindingInstance" in state
    assert "packet.instanceId()" in state
    assert "Objects.equals(bossBindingInstance, packet.instanceId())" in state


def test_guardian_model_uses_modelpart_geometry_and_bounded_phase_animation() -> None:
    model = (SOURCE_ROOT / "RiftGuardianModel.java").read_text(encoding="utf-8")
    renderer = (SOURCE_ROOT / "RiftGuardianModelRenderer.java").read_text(encoding="utf-8")

    for part in ("torso", "left_shoulder", "right_shoulder", "left_arm", "right_arm", "chest_rift"):
        assert part in model
    for shard in ("left_horn", "right_horn", "left_shard", "right_shard"):
        assert shard in model
    assert "TexturedModelData.of(modelData, 128, 128)" in model
    assert "ModelPart" in model
    assert "MathHelper.clamp" in model
    for phase in ("AWAKENING", "HUNTER", "DISTORTION", "ABSORPTION", "CATASTROPHE"):
        assert phase in renderer
    for texture in BOSS_PHASE_TEXTURES:
        assert f'"{texture}"' in renderer


def test_enderman_renderer_scopes_custom_model_to_bound_boss_uuid_with_fallback() -> None:
    mixin = (SOURCE_ROOT / "mixin/EndermanEntityRendererMixin.java").read_text(encoding="utf-8")

    assert "RiftGuardianModelRenderer" in mixin
    assert "isBoundEndBoss(entity.getUuid().toString())" in mixin
    assert "bossPhaseForEntity(entity.getUuid().toString())" in mixin
    assert 'case "END_RIFT_ELITE_V1"' in mixin
    assert 'case "END_RIFT_ENDERMAN_V1"' in mixin
    assert "default -> null" in mixin
    assert "copimine$vanillaModel = model" in mixin
    assert "model = copimine$guardianRenderer.modelForPhase" in mixin
    assert "model = copimine$vanillaModel" in mixin
    assert "assets/minecraft" not in mixin
