"""Wave 2 phantoms must be a deterministic, bounded, safe ring around the marked player.

Complaint (4) is "there are no phantoms in wave 2". The phantoms are a client-only
presentation driven by WaveTwoPhantomLayout, and the class had no behavioural test: only the
artifact check that it ships. Its geometry, its bounds, its yaw and its lookup are all pure
functions, so we drive them for real through a throwaway main.

WaveTwoPhantomLayout is package-private, so the probe must live in me.copimine.client.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
LAYOUT = CLIENT_SRC / "me/copimine/client/WaveTwoPhantomLayout.java"

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.List;
    import java.util.function.Function;

    public final class PhantomLayoutProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        public static void main(String[] args) {
            WaveTwoPhantomLayout.Position anchor = new WaveTwoPhantomLayout.Position(100.0, 64.0, -200.0);

            List<WaveTwoPhantomLayout.Position> first = WaveTwoPhantomLayout.candidatesAround(anchor, 42L);
            List<WaveTwoPhantomLayout.Position> second = WaveTwoPhantomLayout.candidatesAround(anchor, 42L);
            List<WaveTwoPhantomLayout.Position> other = WaveTwoPhantomLayout.candidatesAround(anchor, 43L);

            check("same seed is deterministic", first.equals(second));
            check("different seed rotates the ring", !first.equals(other));
            // Six rings of sixteen: 96 candidates so 70 can survive blocked spots.
            check("six rings of sixteen", first.size() == 96);

            // Every candidate is finite, keeps the anchor height, and sits on one of the rings.
            boolean allFinite = true;
            boolean sameHeight = true;
            boolean onRing = true;
            for (WaveTwoPhantomLayout.Position p : first) {
                if (!WaveTwoPhantomLayout.finite(p)) allFinite = false;
                if (p.y() != anchor.y()) sameHeight = false;
                double dx = p.x() - anchor.x();
                double dz = p.z() - anchor.z();
                double radius = Math.sqrt(dx * dx + dz * dz);
                boolean ring = false;
                for (double expected : new double[]{6.0, 8.0, 10.0, 12.0, 14.0, 16.0}) {
                    if (Math.abs(radius - expected) < 1.0E-6) ring = true;
                }
                if (!ring) onRing = false;
            }
            check("candidates finite", allFinite);
            check("candidates keep anchor height", sameHeight);
            check("candidates sit on the authored rings", onRing);

            // A non-finite anchor yields nothing rather than NaN coordinates.
            check("null anchor refused",
                    WaveTwoPhantomLayout.candidatesAround(null, 1L).isEmpty());
            check("nan anchor refused", WaveTwoPhantomLayout.candidatesAround(
                    new WaveTwoPhantomLayout.Position(Double.NaN, 64.0, 0.0), 1L).isEmpty());

            // selectSafe keeps at most 35 and drops duplicates.
            Function<WaveTwoPhantomLayout.Position, WaveTwoPhantomLayout.Position> identity = p -> p;
            List<WaveTwoPhantomLayout.Position> capped = WaveTwoPhantomLayout.selectSafe(first, identity);
            check("selection is capped at 35", capped.size() == WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT);
            check("selection has no duplicates",
                    capped.size() == new java.util.LinkedHashSet<>(capped).size());
            check("floor allows a partly blocked ring",
                    WaveTwoPhantomLayout.MIN_PHANTOM_COUNT < WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT);

            // A resolver that refuses everything produces an empty, not an invalid, layout.
            check("null resolver refused", WaveTwoPhantomLayout.selectSafe(first, null).isEmpty());
            check("rejecting resolver yields empty",
                    WaveTwoPhantomLayout.selectSafe(first, p -> null).isEmpty());
            // Non-finite resolutions are dropped, and the rest still survive.
            List<WaveTwoPhantomLayout.Position> partly = WaveTwoPhantomLayout.selectSafe(first, p ->
                    p.x() > anchor.x() + 1000.0 ? null : p);
            check("resolver results survive", !partly.isEmpty());

            // Yaw points from the phantom toward the marked player; body stays forward.
            WaveTwoPhantomLayout.Position phantom = new WaveTwoPhantomLayout.Position(0.0, 64.0, 0.0);
            WaveTwoPhantomLayout.Position east = new WaveTwoPhantomLayout.Position(10.0, 64.0, 0.0);
            float yaw = WaveTwoPhantomLayout.targetYaw(phantom, east);
            check("yaw toward +x is a quarter turn", Math.abs(yaw - (float)(Math.PI / 2.0)) < 1.0E-6);
            check("yaw of a coincident point is zero",
                    WaveTwoPhantomLayout.targetYaw(phantom, phantom) == 0.0F);

            // A settled observer faces the viewer with its WHOLE body: a phantom
            // that only turns its head reads as a swivel-necked doll, which is the
            // exact defect the cinematic was asked to avoid.
            WaveTwoPhantomLayout.LookPose pose = WaveTwoPhantomLayout.lookAt(phantom, east);
            check("body turns toward the player",
                    Math.abs(pose.bodyYawDegrees() + 90.0F) < 1.0E-4F);
            check("the head adds no offset on top of the body",
                    pose.headYawDegrees() == 0.0F);
            check("lookAt of a non-finite target is neutral",
                    WaveTwoPhantomLayout.lookAt(phantom, null).headYawDegrees() == 0.0F
                    && WaveTwoPhantomLayout.lookAt(phantom, null).bodyYawDegrees() == 0.0F);

            // Lookup only accepts the exact server-authored position.
            int index = WaveTwoPhantomLayout.indexOfExactPosition(first, first.get(7));
            check("exact position resolves", index == 7);
            check("near-miss position rejected",
                    WaveTwoPhantomLayout.indexOfExactPosition(first,
                            new WaveTwoPhantomLayout.Position(first.get(7).x() + 1.0E-9,
                                    first.get(7).y(), first.get(7).z())) == -1);
            check("null list rejected",
                    WaveTwoPhantomLayout.indexOfExactPosition(null, phantom) == -1);

            System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """
)


def _jdk_bin(name: str) -> Path:
    """Find a JDK tool on CI as well as in this checkout.

    CI installs Temurin into the runner's tool cache and exposes it through
    JAVA_HOME; only this workstation keeps a JDK under local-runtime/toolchains.
    Searching that directory alone made every probe here fail on CI with
    "no local JDK found".
    """
    executable = name + (".exe" if os.name == "nt" else "")
    for variable in ("JAVA_HOME", "JAVA_HOME_25_X64", "JAVA_HOME_21_X64"):
        configured = os.environ.get(variable)
        if configured:
            candidate = Path(configured) / "bin" / executable
            if candidate.is_file():
                return candidate
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/" + executable))
    if candidates:
        return candidates[0]
    found = shutil.which(name)
    assert found, f"{name} is required to compile this probe but was not found"
    return Path(found)


def _javac() -> Path:
    return _jdk_bin("javac")


def test_wave_two_phantom_layout_is_bounded_and_deterministic(tmp_path: Path) -> None:
    assert LAYOUT.is_file(), f"phantom layout source is missing: {LAYOUT}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    probe = package_dir / "PhantomLayoutProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(LAYOUT), str(probe)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.PhantomLayoutProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "phantom layout probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
