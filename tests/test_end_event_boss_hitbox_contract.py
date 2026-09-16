"""Contract checks for the server hitbox profile and supplied client geometry.

The server cannot depend on a client resource at runtime, so this test is the
deliberate drift boundary: the checked-in profile must name real source bones,
preserve the artist's left/right hierarchy, and keep the model-unit envelopes
equal to the direct-cube bounds for articulated parts.  A changed geometry
asset therefore fails the gate before it can silently desynchronise damage
from the visible model.
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


def direct_bounds(bone: dict) -> tuple[float, float, float, float, float, float]:
    cubes = bone.get("cubes", [])
    assert cubes, f"geometry bone {bone['name']} must have direct cubes"
    mins = [math.inf, math.inf, math.inf]
    maxes = [-math.inf, -math.inf, -math.inf]
    for cube in cubes:
        for axis in range(3):
            origin = float(cube["origin"][axis])
            end = origin + float(cube["size"][axis])
            mins[axis] = min(mins[axis], origin)
            maxes[axis] = max(maxes[axis], end)
    return (*mins, *maxes)


def profile_rows() -> list[dict[str, object]]:
    source = read(DOMAIN / "BossHitboxProfile.java")
    number = r"[-+]?\d+(?:\.\d+)?"
    pattern = re.compile(
        rf'part\(PartId\.(?P<id>[A-Z_]+),\s*"(?P<bone>[^"]+)",\s*'
        rf'(?P<x>{number})D,\s*(?P<y>{number})D,\s*(?P<z>{number})D,\s*'
        rf'(?P<w>{number})D,\s*(?P<d>{number})D,\s*(?P<h>{number})D,\s*'
        rf'(?P<segment>\d+)\)',
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
        "group2",
        "group6",
        "group",
        "group5",
    }
    rows = profile_rows()
    assert {row["bone"] for row in rows} == expected_bones
    assert expected_bones <= set(bones)

    assert bones["left_hand_low"]["parent"] == "left_hand"
    assert bones["right_hand_low"]["parent"] == "right_hand"
    assert bones["group2"]["parent"] == "left_leg_low"
    assert bones["group6"]["parent"] == "left_leg"
    assert bones["group"]["parent"] == "right_leg_low"
    assert bones["group5"]["parent"] == "right_leg"

    source = read(DOMAIN / "BossHitboxProfile.java")
    assert 'PartId.LEFT_UPPER_ARM, "left_hand", -7.75D' in source
    assert 'PartId.RIGHT_UPPER_ARM, "right_hand", 7.75D' in source
    assert 'PartId.LEFT_FOREARM, "left_hand_low", -8.0D' in source
    assert 'PartId.RIGHT_FOREARM, "right_hand_low", 8.0D' in source


def test_articulated_profile_envelopes_are_derived_from_direct_cubes() -> None:
    bones = geometry_bones()
    rows = {row["bone"]: row for row in profile_rows() if row["bone"] != "body"}
    assert len(rows) == 9
    for bone_name, row in rows.items():
        center = row["center"]
        size = row["size"]
        actual = direct_bounds(bones[bone_name])
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
        ), f"profile drift for {bone_name}: profile={expected} geometry={actual}"


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
    assert "onBossHitboxProjectile" in plugin
    assert "ProjectileHitEvent event" in plugin
    assert "event.getHitEntity() instanceof Interaction proxy" in plugin
    assert '"projectile:" + projectile.getUniqueId()' in plugin
    assert "bossHitboxController.acceptHit" in plugin
    assert "BossRealHealthDamagePolicy.apply" in plugin
    assert "BOSS_HITBOX_PROJECTILE_DAMAGE_ROUTED" in plugin
    assert "BOSS_HITBOX_PROJECTILE_EVENT" in plugin
    assert "execute as $bossUuid at @s run summon arrow" in read(HITBOX_LIVE)
    assert "AimOffsetY 20.0D -AttackDelayMs 1000" in read(HITBOX_LIVE)
