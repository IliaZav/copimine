"""Locks the Kagune rig transform contract in the ACTIVE 26.3 client.

The renderer must use one documented rotation convention (Bedrock source
rotations compose as Rx*Ry*Rz in the same Y-up model space the vertices are
drawn in), and the historical 1.21.1 client must not drift back to the
unverified ZYX experiment.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ACTIVE_RIG = REPO / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndRiftTentacleRig.java"
ACTIVE_POLICY = REPO / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/TentacleRigTransformPolicy.java"
ACTIVE_POLICY_TEST = REPO / "tools/minecraft-26.3/client/src/test/java/me/copimine/client/TentacleRigTransformPolicyTest.java"
LEGACY_RIG = REPO / "CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRig.java"
IMPORTED_MODEL = (
    REPO / "CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json"
)


def test_active_rig_uses_the_single_transform_policy() -> None:
    source = ACTIVE_RIG.read_text(encoding="utf-8")
    assert "TentacleRigTransformPolicy.local(" in source
    assert "rotateXYZ(" not in source, "rig must not compose rotations inline"
    assert "rotateZYX(" not in source
    policy = ACTIVE_POLICY.read_text(encoding="utf-8")
    assert "Rx(pitch) * Ry(yaw) * Rz(roll)" in policy
    assert "BedrockCoordinateTransform" in policy
    assert ACTIVE_POLICY_TEST.is_file()


def test_legacy_client_uses_the_same_rotation_order() -> None:
    source = LEGACY_RIG.read_text(encoding="utf-8")
    assert "rotateZYX(" not in source, (
        "the unverified ZYX experiment must not survive in the shipped client"
    )
    assert "rotateXYZ(" in source
    assert "BedrockCoordinateTransform.sourceRotation" in source


def test_imported_model_rest_pose_is_identity_and_bones_match_the_rig() -> None:
    imported = json.loads(IMPORTED_MODEL.read_text(encoding="utf-8"))
    assert imported["format"] == "copimine:kagune-import-v1"
    rig = ACTIVE_RIG.read_text(encoding="utf-8")
    required = re.search(r"REQUIRED_BONES = List\.of\((.*?)\);", rig, re.S)
    assert required is not None
    names = re.findall(r'"([^"]+)"', required.group(1))
    assert names == [group["name"] for group in imported["groups"]], (
        "rig bone list must track the imported hierarchy"
    )
    for group in imported["groups"]:
        assert group["rotation"] == [0.0, 0.0, 0.0], (
            f"rest rotation of {group['name']} must stay identity"
        )
    parents = {group["name"]: group["parent"] for group in imported["groups"]}
    assert parents == {
        "1layer": None,
        "1layer2": "1layer",
        "2layer": None,
        "2layer2": "2layer",
        "3layer": None,
        "3layer2": "3layer",
    }, "the six-bone hierarchy is part of the transform contract"


def test_authored_animation_rotations_stay_within_the_documented_envelope() -> None:
    imported = json.loads(IMPORTED_MODEL.read_text(encoding="utf-8"))
    for clip in imported["animations"].values():
        for bone, channels in clip["tracks"].items():
            for keyframe in channels.get("rotation", []):
                for axis, value in enumerate(keyframe["value"]):
                    assert -180.0 <= value <= 180.0, (
                        f"{bone} axis {axis} rotation {value} is outside a single turn"
                    )