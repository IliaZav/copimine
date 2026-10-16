"""Import the supplied Kagune Blockbench model into the client and resource pack.

The source .bbmodel remains the single source of truth for its hierarchy, face
UV rectangles, and animation keyframes. The importer emits a small runtime
descriptor and the static vanilla ItemDisplay fallback; the original texture
bytes are extracted without recoloring or resampling.
"""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
from typing import Any


CLIENT_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = CLIENT_ROOT.parent
SOURCE_MODEL = CLIENT_ROOT / "src/main/asset-source/end-rift-tentacle/kagune.bbmodel"
RUNTIME_ROOT = CLIENT_ROOT / "src/main/resources/assets/copimineclient"
RUNTIME_MODEL = RUNTIME_ROOT / "geometry/end_rift_tentacle.json"
RUNTIME_TENTACLE_TEXTURE = RUNTIME_ROOT / "textures/entity/end_rift_tentacle_hd.png"
SERVER_TENTACLE_TEXTURE = (
    REPO_ROOT / "resourcepacks/src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png"
)
SERVER_TENTACLE_MODEL = (
    REPO_ROOT / "resourcepacks/src/assets/copimine/models/item/end_event_rift_tentacle.json"
)

EXPECTED_SOURCE_MODEL_SHA256 = "9f3e661b9b70c0a6a906c592bc2a643bbe1a19abd5906d841b0e0aa9cb5f92dc"
EXPECTED_TEXTURE_SHA256 = "5817936653025968abdd07003eba29e4820b09b33ebac4708e463f3b8b4f6bbc"
SUPPORTED_FALLBACK_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


def _vec(value: Any) -> list[float]:
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError(f"expected three model coordinates, got {value!r}")
    return [float(component) for component in value]


def _walk_outliner(
    nodes: list[Any],
    groups_by_uuid: dict[str, dict[str, Any]],
    parent_group: str | None = None,
    group_parent: dict[str, str | None] | None = None,
    element_group: dict[str, str] | None = None,
) -> None:
    if group_parent is None:
        group_parent = {}
    if element_group is None:
        element_group = {}
    for node in nodes:
        if isinstance(node, str):
            if parent_group is not None:
                element_group[node] = parent_group
            continue
        if not isinstance(node, dict):
            continue
        node_uuid = str(node.get("uuid", ""))
        current_parent = parent_group
        if node_uuid in groups_by_uuid:
            group_parent[node_uuid] = parent_group
            current_parent = node_uuid
        _walk_outliner(node.get("children", []), groups_by_uuid,
                       current_parent, group_parent, element_group)


def import_model(source: dict[str, Any]) -> tuple[dict[str, Any], dict[str, str]]:
    groups = source.get("groups", [])
    elements = source.get("elements", [])
    animations = source.get("animations", [])
    groups_by_uuid = {str(group["uuid"]): group for group in groups}
    group_parent: dict[str, str | None] = {}
    element_group: dict[str, str] = {}
    _walk_outliner(source.get("outliner", []), groups_by_uuid,
                   group_parent=group_parent, element_group=element_group)

    imported_groups = []
    ordered_group_uuids = list(group_parent)
    if len(ordered_group_uuids) != len(groups):
        raise ValueError("the Kagune outliner does not contain every source bone")
    for group_uuid in ordered_group_uuids:
        group = groups_by_uuid[group_uuid]
        parent_uuid = group_parent.get(group_uuid)
        imported_groups.append({
            "uuid": group_uuid,
            "name": str(group["name"]),
            "parent": groups_by_uuid[parent_uuid]["name"] if parent_uuid else None,
            "origin": _vec(group.get("origin", [0.0, 0.0, 0.0])),
            "rotation": _vec(group.get("rotation") or [0.0, 0.0, 0.0]),
        })

    imported_elements = []
    for element in elements:
        element_uuid = str(element["uuid"])
        bone_uuid = element_group.get(element_uuid)
        if bone_uuid not in groups_by_uuid:
            raise ValueError(f"Blockbench cuboid {element_uuid} has no parent bone")
        faces = {}
        for face_name, face in element.get("faces", {}).items():
            uv = face.get("uv")
            if not isinstance(uv, list) or len(uv) != 4:
                raise ValueError(f"cuboid {element_uuid} face {face_name} has invalid UV data")
            faces[face_name] = {
                "uv": [float(component) for component in uv],
                "rotation": int(face.get("rotation", 0)),
            }
        if set(faces) != {"north", "east", "south", "west", "up", "down"}:
            raise ValueError(f"cuboid {element_uuid} is missing one or more face UVs")
        imported_elements.append({
            "uuid": element_uuid,
            "name": str(element.get("name", "cube")),
            "bone": str(groups_by_uuid[bone_uuid]["name"]),
            "from": _vec(element["from"]),
            "to": _vec(element["to"]),
            "origin": _vec(element.get("origin", [0.0, 0.0, 0.0])),
            "rotation": _vec(element.get("rotation") or [0.0, 0.0, 0.0]),
            "faces": faces,
        })

    imported_animations: dict[str, Any] = {}
    for animation in animations:
        animation_name = str(animation["name"])
        tracks: dict[str, dict[str, list[dict[str, Any]]]] = {}
        for target_uuid, animator in animation.get("animators", {}).items():
            group = groups_by_uuid.get(str(target_uuid))
            if group is None:
                continue
            bone_name = str(group["name"])
            channels: dict[str, list[dict[str, Any]]] = {}
            for keyframe in animator.get("keyframes", []) or []:
                channel = str(keyframe.get("channel", ""))
                points = keyframe.get("data_points", []) or []
                if not channel or not points:
                    continue
                value = [float(points[0].get(axis, 0.0)) for axis in ("x", "y", "z")]
                channels.setdefault(channel, []).append({
                    "time": float(keyframe.get("time", 0.0)),
                    "value": value,
                    "interpolation": str(keyframe.get("interpolation", "linear")),
                })
            for keyframes in channels.values():
                keyframes.sort(key=lambda frame: frame["time"])
            if channels:
                tracks[bone_name] = channels
        imported_animations[animation_name] = {
            "length": float(animation.get("length", 0.0)),
            "loop": str(animation.get("loop", "once")),
            "tracks": tracks,
        }

    resolution = source.get("resolution", {})
    texture = source.get("textures", [])[0]
    texture_info = {
        "width": int(texture["width"]),
        "height": int(texture["height"]),
        "uv_size": [int(resolution["width"]), int(resolution["height"])],
    }
    runtime = {
        "format": "copimine:kagune-import-v1",
        "texture": texture_info,
        "groups": imported_groups,
        "elements": imported_elements,
        "animations": imported_animations,
    }
    return runtime, texture


def _fallback_model(source: dict[str, Any], runtime_model: dict[str, Any]) -> dict[str, Any]:
    """Bake the source mesh to Minecraft's constrained static model rotations."""
    elements = source["elements"]
    x_min = min(float(element["from"][0]) for element in elements)
    x_max = max(float(element["to"][0]) for element in elements)
    y_min = min(float(element["from"][1]) for element in elements)
    z_min = min(float(element["from"][2]) for element in elements)
    z_max = max(float(element["to"][2]) for element in elements)
    x_shift = 8.0 - (x_min + x_max)
    z_shift = 8.0 - (z_min + z_max)

    def position(point: list[Any]) -> list[float]:
        return [float(point[0]) * 2.0 + x_shift,
                (float(point[1]) - y_min) * 2.0,
                float(point[2]) * 2.0 + z_shift]

    def fallback_angle(value: float) -> float:
        return min(SUPPORTED_FALLBACK_ANGLES, key=lambda angle: (abs(angle - value), abs(angle)))

    baked_elements = []
    for element in elements:
        raw_rotation = element.get("rotation") or [0.0, 0.0, 0.0]
        rotation = [float(component) for component in raw_rotation]
        item: dict[str, Any] = {
            "from": position(element["from"]),
            "to": position(element["to"]),
            "faces": {},
        }
        for face_name, face in element["faces"].items():
            u0, v0, u1, v1 = (float(component) * 8.0 for component in face["uv"])
            item["faces"][face_name] = {
                "uv": [u0, v0, u1, v1],
                "texture": "#tentacle",
                **({"rotation": int(face["rotation"])} if face.get("rotation") else {}),
            }
        if any(abs(component) > 0.0001 for component in rotation):
            if abs(rotation[0]) > 0.0001 or abs(rotation[1]) > 0.0001:
                raise ValueError("resource-pack fallback supports the supplied Z-axis rotations only")
            item["rotation"] = {
                "origin": position(element.get("origin", [0.0, 0.0, 0.0])),
                "axis": "z",
                "angle": fallback_angle(rotation[2]),
                "rescale": False,
            }
        baked_elements.append(item)

    group_names = [str(group["name"]) for group in runtime_model["groups"]]
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "copimine:item/end_event_rift_tentacle_hd",
            "tentacle": "copimine:item/end_event_rift_tentacle_hd",
        },
        "copimine_rig": {
            "texture_size": [int(source["resolution"]["width"]), int(source["resolution"]["height"])],
            "forward_axis": "+Y",
            "bones": group_names,
            "grab_socket": {"parent": "3layer2", "geometry": False, "local": [0.0, 7.6181, 0.0]},
        },
        "elements": baked_elements,
    }


def import_kagune_assets() -> None:
    source_bytes = SOURCE_MODEL.read_bytes()
    source_hash = hashlib.sha256(source_bytes).hexdigest()
    if source_hash != EXPECTED_SOURCE_MODEL_SHA256:
        raise ValueError(f"supplied kagune.bbmodel hash mismatch: {source_hash}")
    source = json.loads(source_bytes.decode("utf-8"))
    runtime_model, texture = import_model(source)

    embedded_source = str(texture.get("source", ""))
    if not embedded_source.startswith("data:image/png;base64,"):
        raise ValueError("supplied Kagune model does not embed its original PNG skin")
    texture_bytes = base64.b64decode(embedded_source.split(",", 1)[1], validate=True)
    texture_hash = hashlib.sha256(texture_bytes).hexdigest()
    if texture_hash != EXPECTED_TEXTURE_SHA256:
        raise ValueError(f"supplied Kagune texture hash mismatch: {texture_hash}")

    RUNTIME_MODEL.parent.mkdir(parents=True, exist_ok=True)
    RUNTIME_TENTACLE_TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    SERVER_TENTACLE_TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    SERVER_TENTACLE_MODEL.parent.mkdir(parents=True, exist_ok=True)
    RUNTIME_MODEL.write_text(json.dumps(runtime_model, indent=2, ensure_ascii=False) + "\n",
                             encoding="utf-8", newline="\n")
    RUNTIME_TENTACLE_TEXTURE.write_bytes(texture_bytes)
    SERVER_TENTACLE_TEXTURE.write_bytes(texture_bytes)
    SERVER_TENTACLE_MODEL.write_text(
        json.dumps(_fallback_model(source, runtime_model), indent=2) + "\n",
                                     encoding="utf-8", newline="\n")
    print("imported 6 Kagune cuboids, 6 bones and 12 animations; preserved supplied PNG bytes")


if __name__ == "__main__":
    import_kagune_assets()
