"""Contract checks for the server hitbox profile and supplied client geometry.

The server cannot depend on a client resource at runtime, so this test is the
deliberate drift boundary: the checked-in profile must name real source bones,
preserve the artist's left/right hierarchy, and keep the model-unit envelopes
equal to the bind-pose cube bounds after bone and cube rotations.  A changed
geometry asset therefore fails the gate before it can silently desynchronise
damage from the visible model.
"""

from __future__ import annotations

import json
import math
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
CLIENT_JAVA = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client"
PLUGIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
HITBOX_CONTROLLER = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "runtime"
    / "BossHitboxController.java"
)
HITBOX_LIVE = ROOT / "tests" / "RunEndRiftBossHitboxLive.ps1"
GEOMETRY = (
    ROOT
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "models"
    / "entity"
    / "end_rift_guardian"
    / "geometry.json"
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def geometry_bones() -> dict[str, dict]:
    document = json.loads(read(GEOMETRY))
    geometry = document["minecraft:geometry"]
    assert len(geometry) == 1
    return {bone["name"]: bone for bone in geometry[0]["bones"]}


def _matrix_multiply(first: tuple[tuple[float, ...], ...],
                     second: tuple[tuple[float, ...], ...]) -> tuple[tuple[float, ...], ...]:
    return tuple(
        tuple(sum(first[row][index] * second[index][column] for index in range(3))
              for column in range(3))
        for row in range(3)
    )


def _matrix_vector(matrix: tuple[tuple[float, ...], ...], vector: tuple[float, ...]) -> tuple[float, ...]:
    return tuple(sum(matrix[row][index] * vector[index] for index in range(3))
                 for row in range(3))


def _add(first: tuple[float, ...], second: tuple[float, ...]) -> tuple[float, ...]:
    return tuple(first[index] + second[index] for index in range(3))


def _subtract(first: tuple[float, ...], second: tuple[float, ...]) -> tuple[float, ...]:
    return tuple(first[index] - second[index] for index in range(3))


def _rotation(values: list[float] | tuple[float, ...]) -> tuple[tuple[float, ...], ...]:
    pitch, yaw, roll = (math.radians(float(value)) for value in values)
    cp, sp = math.cos(pitch), math.sin(pitch)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cr, sr = math.cos(roll), math.sin(roll)
    rx = ((1.0, 0.0, 0.0), (0.0, cp, -sp), (0.0, sp, cp))
    ry = ((cy, 0.0, sy), (0.0, 1.0, 0.0), (-sy, 0.0, cy))
    rz = ((cr, -sr, 0.0), (sr, cr, 0.0), (0.0, 0.0, 1.0))
    return _matrix_multiply(_matrix_multiply(rx, ry), rz)


def _world_rotations(bones: dict[str, dict]) -> dict[str, tuple[tuple[float, ...], ...]]:
    rotations: dict[str, tuple[tuple[float, ...], ...]] = {}

    def resolve(name: str) -> tuple[tuple[float, ...], ...]:
        if name in rotations:
            return rotations[name]
        bone = bones[name]
        parent = bone.get("parent")
        parent_rotation = (
            ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
            if not parent else resolve(parent)
        )
        rotations[name] = _matrix_multiply(
            parent_rotation, _rotation(bone.get("rotation", [0.0, 0.0, 0.0]))
        )
        return rotations[name]

    for name in bones:
        resolve(name)
    return rotations


def _cube_world_points(
    bone_name: str,
    cube: dict,
    bones: dict[str, dict],
    rotations: dict[str, tuple[tuple[float, ...], ...]],
) -> list[tuple[float, ...]]:
    bone_pivot = tuple(float(value) for value in bones[bone_name]["pivot"])
    cube_pivot = tuple(float(value) for value in cube.get("pivot", cube["origin"]))
    origin = tuple(float(value) for value in cube["origin"])
    size = tuple(float(value) for value in cube["size"])
    cube_rotation = _rotation(cube.get("rotation", [0.0, 0.0, 0.0]))
    bone_rotation = rotations[bone_name]
    result = []
    for x in (0.0, 1.0):
        for y in (0.0, 1.0):
            for z in (0.0, 1.0):
                corner = tuple(origin[index] + (x, y, z)[index] * size[index]
                               for index in range(3))
                cube_space = _add(
                    cube_pivot,
                    _matrix_vector(cube_rotation, _subtract(corner, cube_pivot)),
                )
                result.append(_add(
                    bone_pivot,
                    _matrix_vector(bone_rotation, _subtract(cube_space, bone_pivot)),
                ))
    return result


def bind_pose_bounds(bone_names: list[str], bones: dict[str, dict],
                     predicate=None) -> tuple[float, float, float, float, float, float]:
    rotations = _world_rotations(bones)
    points = [
        point
        for bone_name in bone_names
        for cube in bones[bone_name].get("cubes", [])
        if predicate is None or predicate(bone_name, cube)
        for point in _cube_world_points(bone_name, cube, bones, rotations)
    ]
    assert points, f"geometry group {bone_names} must contain cubes"
    mins = [min(point[axis] for point in points) for axis in range(3)]
    maxes = [max(point[axis] for point in points) for axis in range(3)]
    return (*mins, *maxes)


def profile_rows() -> list[dict[str, object]]:
    source = read(DOMAIN / "BossHitboxProfile.java")
    number = r"[-+]?\d+(?:\.\d+)?"
    pattern = re.compile(
        rf'part\(PartId\.(?P<id>[A-Z_]+),\s*"(?P<bone>[^"]+)",\s*'
        rf'(?P<x>{number})D,\s*(?P<y>{number})D,\s*(?P<z>{number})D,\s*'
        rf'(?P<w>{number})D,\s*(?P<d>{number})D,\s*(?P<h>{number})D,\s*'
        rf'(?P<segment>\d+),\s*'
        rf'(?P<px>{number})D,\s*(?P<py>{number})D,\s*(?P<pz>{number})D\)',
        re.MULTILINE,
    )
    rows = []
    for match in pattern.finditer(source):
        rows.append(
            {
                "id": match.group("id"),
                "bone": match.group("bone"),
                "center": tuple(float(match.group(axis)) for axis in ("x", "y", "z")),
                "size": tuple(float(match.group(axis)) for axis in ("w", "d", "h")),
                "segment": int(match.group("segment")),
                "pivot": tuple(float(match.group(axis)) for axis in ("px", "py", "pz")),
            }
        )
    return rows


def test_hitbox_profile_tracks_supplied_geometry_bones_and_hierarchy() -> None:
    bones = geometry_bones()
    expected_bones = {
        "head",
        "body",
        "left_hand",
        "left_hand_low",
        "right_hand",
        "right_hand_low",
        "group2+group4",
        "group6",
        "group+group3",
        "group5",
    }
    rows = profile_rows()
    assert {row["bone"] for row in rows} == expected_bones
    assert set(bones) == {
        "head", "body", "right_hand", "right_hand_low", "left_hand", "left_hand_low",
        "right_leg", "group5", "right_leg_low", "group", "group3", "left_leg",
        "group6", "left_leg_low", "group2", "group4",
    }
    profile_sources = {
        source
        for row in rows
        for source in str(row["bone"]).split("+")
    }
    assert profile_sources <= set(bones)

    assert bones["left_hand_low"]["parent"] == "left_hand"
    assert bones["right_hand_low"]["parent"] == "right_hand"
    assert bones["group2"]["parent"] == "left_leg_low"
    assert bones["group6"]["parent"] == "left_leg"
    assert bones["group"]["parent"] == "right_leg_low"
    assert bones["group5"]["parent"] == "right_leg"

    source = read(DOMAIN / "BossHitboxProfile.java")
    assert 'PartId.LEFT_UPPER_ARM, "left_hand", -5.2365464907D' in source
    assert 'PartId.RIGHT_UPPER_ARM, "right_hand", 5.2365464907D' in source
    assert 'PartId.LEFT_FOREARM, "left_hand_low", -5.7856480035D' in source
    assert 'PartId.RIGHT_FOREARM, "right_hand_low", 5.7856480035D' in source


def test_articulated_profile_envelopes_are_derived_from_bind_pose_geometry() -> None:
    bones = geometry_bones()
    rows = [row for row in profile_rows() if row["bone"] != "body"]
    assert len(rows) == 9
    for row in rows:
        center = row["center"]
        size = row["size"]
        actual = bind_pose_bounds(str(row["bone"]).split("+"), bones)
        expected = (
            center[0] - size[0] / 2.0,
            center[1] - size[2] / 2.0,
            center[2] - size[1] / 2.0,
            center[0] + size[0] / 2.0,
            center[1] + size[2] / 2.0,
            center[2] + size[1] / 2.0,
        )
        assert all(
            math.isclose(left, right, abs_tol=1e-6)
            for left, right in zip(expected, actual)
        ), f"profile drift for {row['bone']}: profile={expected} geometry={actual}"

    body_rows = {row["id"]: row for row in profile_rows() if row["bone"] == "body"}
    for row, predicate in (
        (body_rows["PELVIS"], lambda _name, cube: float(cube["origin"][1]) < 40.0),
        (body_rows["CHEST"], lambda _name, cube: float(cube["origin"][1]) >= 40.0),
    ):
        center = row["center"]
        size = row["size"]
        actual = bind_pose_bounds(["body"], bones, predicate)
        expected = (
            center[0] - size[0] / 2.0,
            center[1] - size[2] / 2.0,
            center[2] - size[1] / 2.0,
            center[0] + size[0] / 2.0,
            center[1] + size[2] / 2.0,
            center[2] + size[1] / 2.0,
        )
        assert all(
            math.isclose(left, right, abs_tol=1e-6)
            for left, right in zip(expected, actual)
        ), f"profile drift for body/{row['id']}: profile={expected} geometry={actual}"


def test_client_importer_keeps_the_same_source_bones_and_exact_face_uv() -> None:
    importer = read(CLIENT_JAVA / "UserEndBossModelData.java")
    assert 'RESOURCE = "/assets/copimineclient/models/entity/end_rift_guardian/geometry.json"' in importer
    assert 'case "right_hand" -> "right_arm"' in importer
    assert 'case "left_hand" -> "left_arm"' in importer
    assert 'case "body" -> "torso"' in importer
    assert "applyExactFaceUv" in importer
    assert "Missing parent for supplied End Rift bone" in importer


def test_boss_hitbox_debug_command_and_live_probe_are_exposed() -> None:
    plugin = read(PLUGIN)
    assert '"bosshitbox"' in plugin
    assert "/cmend debug bosshitbox <on|off|status>" in plugin
    assert "setDebug" in plugin
    assert "debugBoxes" in plugin
    assert "renderBossHitboxDebug" in plugin
    assert HITBOX_LIVE.is_file()
    live = read(HITBOX_LIVE)
    assert "BukkitValues.copimineendevent:$Key" in live
    assert 'Read-ProxyPdc $proxyId \'boss_hitbox_parent\'' in live
    for marker in (
        "LIVE_BOSS_HITBOX_PROFILE_PASS",
        "LIVE_BOSS_HITBOX_MELEE_PASS",
        "LIVE_BOSS_HITBOX_PROJECTILE_PASS",
        "LIVE_BOSS_HITBOX_MISS_PASS",
        "LIVE_BOSS_HITBOX_CLEANUP_PASS",
        "cmend debug bosshitbox on",
        "cmend debug bosshitbox off",
    ):
        assert marker in live


def test_live_probe_reads_the_four_status_regex_groups() -> None:
    live = read(HITBOX_LIVE)
    assert "$match.Groups[3].Value" in live
    assert "$match.Groups[4].Value -split ','" in live
    assert "$match.Groups[5].Value" not in live


def test_projectile_hits_on_interaction_proxy_use_the_same_authoritative_route() -> None:
    plugin = read(PLUGIN)
    controller = read(HITBOX_CONTROLLER)
    assert "onBossHitboxProjectile" in plugin
    assert "ProjectileHitEvent event" in plugin
    assert "event.getHitEntity() instanceof Interaction proxy" in plugin
    assert '"projectile:" + projectile.getUniqueId()' in plugin
    assert "proxyRayIntersects" in controller
    assert "bossHitboxController.proxyRayIntersects" in plugin
    assert "BOSS_HITBOX_RAY_BLOCKED" in plugin
    assert "bossHitboxController.acceptHit" in plugin
    assert "BossRealHealthDamagePolicy.apply" in plugin
    assert "BOSS_HITBOX_PROJECTILE_DAMAGE_ROUTED" in plugin
    assert "BOSS_HITBOX_PROJECTILE_EVENT" in plugin
    assert "execute as $bossUuid at @s run summon arrow" in read(HITBOX_LIVE)
    assert "AimOffsetY 20.0D -AttackDelayMs 1000" in read(HITBOX_LIVE)


def test_final_boss_defeat_removes_combat_proxies_before_the_cinematic() -> None:
    plugin = read(PLUGIN)
    preparation = plugin.split("private boolean prepareOfficialBossDefeat", 1)[1].split(
        "private void startBossDefeatCinematic", 1
    )[0]
    assert "bossHitboxController.cleanup()" in preparation
    assert "lastBossHitboxUpdateServerTick = Long.MIN_VALUE" in preparation
