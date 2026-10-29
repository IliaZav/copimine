"""Numeric contract for the active 26.3 Kagune rig transform.

Spec 10 forbids marking this contract as verified from source strings, so the
real policy is compiled and compared against an independently computed
Rx*Ry*Rz product (and against the rejected Rz*Ry*Rx order).
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ACTIVE_RIG = REPO / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndRiftTentacleRig.java"
ACTIVE_POLICY = REPO / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/TentacleRigTransformPolicy.java"
LEGACY_RIG = REPO / "CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRig.java"
IMPORTED_MODEL = (
    REPO / "CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json"
)

PROBE = r"""
package me.copimine.client;

import java.util.Arrays;

public final class KaguneRigMatrixProbe {
    private static final double EPS = 1e-5D;

    public static void main(String[] args) {
        // Identity is a real identity matrix, not just a named constant.
        float[] identity = TentacleRigTransformPolicy.identity();
        requireNear(identity, new double[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1}, "identity matrix");

        // A zero rotation with unit scale must reproduce identity exactly.
        float[] rest = TentacleRigTransformPolicy.local(0, 0, 0, 0, 0, 0, 1);
        requireNear(rest, toDouble(identity), "rest pose");

        // Translation and uniform scale land in the documented matrix slots.
        float[] placed = TentacleRigTransformPolicy.local(1.5D, -2.0D, 0.25D, 0, 0, 0, 2.0D);
        check(placed[12] == 1.5F && placed[13] == -2.0F && placed[14] == 0.25F,
                "translation must occupy the last column");
        check(placed[0] == 2.0F && placed[5] == 2.0F && placed[10] == 2.0F,
                "uniform scale must scale the rotation basis");

        // Mixed angles: the policy must equal Rx*Ry*Rz and differ from Rz*Ry*Rx.
        double pitch = Math.toRadians(31.0D);
        double yaw = Math.toRadians(-57.0D);
        double roll = Math.toRadians(18.5D);
        float[] actual = TentacleRigTransformPolicy.local(0, 0, 0, pitch, yaw, roll, 1.0D);
        double[] expected = multiplyXYZ(rotX(pitch), rotY(yaw), rotZ(roll));
        requireNear(actual, toColumnMajor(expected), "Rx*Ry*Rz composition");
        double[] zyx = multiplyXYZ(rotZ(roll), rotY(yaw), rotX(pitch));
        check(maxDelta(actual, toColumnMajor(zyx)) > 1e-3D,
                "a ZYX composition must not pass the numeric contract");

        // A single 90 degree pitch is a hand-checkable quarter turn about X.
        float[] quarter = TentacleRigTransformPolicy.local(0, 0, 0, Math.PI / 2.0D, 0, 0, 1.0D);
        double[] point = TentacleRigTransformPolicy.transformPoint(quarter, 0.0D, 1.0D, 0.0D);
        requirePoint(point, new double[]{0.0D, 0.0D, 1.0D}, "90 degree pitch about X");

        // scale <= 0 or a non-finite angle must degrade safely, never NaN the rig.
        float[] guarded = TentacleRigTransformPolicy.local(0, 0, 0, Double.NaN, 0, 0, 0.0D);
        float[] unit = TentacleRigTransformPolicy.local(0, 0, 0, 0, 0, 0, 1.0D);
        requireNear(guarded, toDouble(unit), "unusable scale or angle must fall back to identity");

        // multiply(a, b) applies b first, so the composed pose must match.
        float[] parent = TentacleRigTransformPolicy.local(0, 1, 0, 0, 0, 0, 1);
        float[] child = TentacleRigTransformPolicy.local(0, 2, 0, 0, 0, 0, 1);
        double[] tip = TentacleRigTransformPolicy.transformPoint(
                TentacleRigTransformPolicy.multiply(parent, child), 0, 0, 0);
        requirePoint(tip, new double[]{0.0D, 3.0D, 0.0D}, "parent * local chain translation");
        check(TentacleRigTransformPolicy.distance(identity, identity) == 0.0D,
                "distance of a matrix to itself must be zero");

        System.out.println("KaguneRigMatrixProbe OK");
    }

    private static double[] rotX(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static double[] rotY(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static double[] rotZ(double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    private static double[] multiplyXYZ(double[] a, double[] b, double[] c) {
        return multiply(multiply(a, b), c);
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] result = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double sum = 0.0D;
                for (int k = 0; k < 3; k++) {
                    sum += a[row * 3 + k] * b[k * 3 + column];
                }
                result[row * 3 + column] = sum;
            }
        }
        return result;
    }

    private static double[] toColumnMajor(double[] rowMajor) {
        return new double[]{
                rowMajor[0], rowMajor[3], rowMajor[6], 0.0D,
                rowMajor[1], rowMajor[4], rowMajor[7], 0.0D,
                rowMajor[2], rowMajor[5], rowMajor[8], 0.0D,
                0.0D, 0.0D, 0.0D, 1.0D};
    }

    private static double[] toDouble(float[] values) {
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }

    private static double maxDelta(float[] actual, double[] expected) {
        double max = 0.0D;
        for (int index = 0; index < 16; index++) {
            max = Math.max(max, Math.abs(actual[index] - expected[index]));
        }
        return max;
    }

    private static void requireNear(float[] actual, double[] expected, String label) {
        double delta = maxDelta(actual, expected);
        check(delta < EPS, label + " deviates by " + delta);
    }

    private static void requirePoint(double[] actual, double[] expected, String label) {
        double delta = 0.0D;
        for (int index = 0; index < 3; index++) {
            delta = Math.max(delta, Math.abs(actual[index] - expected[index]));
        }
        check(delta < EPS, label + " deviates by " + delta + " -> " + Arrays.toString(actual));
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private KaguneRigMatrixProbe() {
    }
}
"""


def _tool(name: str) -> str:
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required to compile the Kagune rig matrix probe"
    return found


def test_rig_composes_source_rotations_as_rx_ry_rz(tmp_path) -> None:
    probe = tmp_path / "me" / "copimine" / "client" / "KaguneRigMatrixProbe.java"
    probe.parent.mkdir(parents=True)
    probe.write_text(PROBE, encoding="utf-8")
    compiled = subprocess.run(
        [_tool("javac"), "-encoding", "UTF-8", "-d", str(tmp_path),
         str(ACTIVE_POLICY), str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        [_tool("java"), "-cp", str(tmp_path), "me.copimine.client.KaguneRigMatrixProbe"],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
    assert "KaguneRigMatrixProbe OK" in executed.stdout


def test_active_rig_delegates_to_the_numeric_policy() -> None:
    source = ACTIVE_RIG.read_text(encoding="utf-8")
    assert "TentacleRigTransformPolicy.local(" in source
    assert "rotateXYZ(" not in source, "rig must not compose rotations inline"
    assert "rotateZYX(" not in source


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
