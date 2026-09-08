import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT_STATE = ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java"
CLIENT_BRIDGE = ROOT / "CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java"
TEXTURE_CATALOG = ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java"
PACK_MANIFEST = ROOT / "resourcepacks/models_manifest.json"
PACK_MODEL = ROOT / "resourcepacks/src/assets/copimine/models/item/end_event_rift_tentacle.json"
PACK_TEXTURE = ROOT / "resourcepacks/src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png"
PACK_ANIMATIONS = ROOT / "resourcepacks/src/assets/copimine/animations/end_event_rift_tentacle.json"


def _body(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_server_owns_generation_scoped_tentacle_runtime_and_bounded_counts():
    body = _body(MAIN)
    assert 'EVENT_KIND_TENTACLE = "RIFT_TENTACLE"' in body
    assert "TentacleController" in body
    assert "TentacleScalingPolicy.MAX_PERMANENT" in body
    assert "TentacleScalingPolicy.MAX_TEMPORARY" in body
    assert "tentacleController.begin(generation)" in body
    assert "clearTentacles(" in body
    assert "tickV2Tentacles(" in body
    assert 'CLIENT_VISUAL_TENTACLE = "END_RIFT_TENTACLE_V1"' in body


def test_server_uses_server_authoritative_grab_markers_and_cleanup():
    body = _body(MAIN)
    for marker in ("CONTACT", "HOLD_LOCK", "THROW_RELEASE", "RECOVERY_START", "HIDE_BELOW_FLOOR"):
        assert f"TentacleAnimationPolicy.Marker.{marker}" in body
    assert "target.teleport(" in body
    assert "target.setVelocity(" in body
    assert "tentacleController.remove(" in body
    assert "cleanupOwnedEntities" in body


def test_client_bridge_carries_server_tentacle_animation_state():
    state = _body(CLIENT_STATE)
    bridge = _body(CLIENT_BRIDGE)
    catalog = _body(TEXTURE_CATALOG)
    assert '"END_ENTITY_PHASE"' in state or '"END_ENTITY_PHASE"' in bridge
    assert "entityAnimation" in state
    assert "tentaclePoseForEntity" in state
    assert "END_RIFT_TENTACLE_V1" in catalog
    assert "end_rift_tentacle_hd.png" in catalog


def test_tentacle_resource_pack_assets_are_real_and_manifested():
    manifest = json.loads(PACK_MANIFEST.read_text(encoding="utf-8"))
    entries = manifest["items"]
    assert any(
        entry.get("id") == "end_event_rift_tentacle"
        and entry.get("custom_model_data") == 830017
        for entry in entries
    )
    model = json.loads(PACK_MODEL.read_text(encoding="utf-8"))
    assert model.get("elements")
    assert len(model["elements"]) >= 8
    assert "#tentacle" in json.dumps(model)
    animation = json.loads(PACK_ANIMATIONS.read_text(encoding="utf-8"))
    assert set(animation["animations"]) >= {
        "idle",
        "emerge",
        "telegraph_grab",
        "grab_success",
        "hold",
        "throw",
        "grab_miss",
        "hurt",
        "death",
        "retract",
        "spawn_under_player",
        "shield_channel",
    }
    assert set(animation["markers"]) >= {
        "CONTACT",
        "HOLD_LOCK",
        "THROW_RELEASE",
        "RECOVERY_START",
        "HIDE_BELOW_FLOOR",
    }
    assert PACK_TEXTURE.exists()
    assert PACK_TEXTURE.stat().st_size > 1024
