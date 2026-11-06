"""The wave 6 tentacle's facing and scale are pure maths, and both were untested.

Complaint (10) covers the wave 6 fight. EndRiftTentacleFacingPolicy keeps target tracking out
of hurt and death poses and bounds the render turn rate - the thing that stops the tentacle
snapping around, and the shortest-arc wrap that stops it taking the long way round past pi.
EndRiftTentacleWorldScalePolicy keeps the authored rig compact in world units.
EndRiftTentacleCarrierPolicy decides when the vanilla ItemDisplay carrier may be suppressed.

All four classes (these three plus EndRiftTentaclePose) had no behavioural test, only the
artifact check that they ship in the jar.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
PKG = CLIENT_SRC / "me/copimine/client"
FACING = PKG / "EndRiftTentacleFacingPolicy.java"
SCALE = PKG / "EndRiftTentacleWorldScalePolicy.java"
CARRIER = PKG / "EndRiftTentacleCarrierPolicy.java"
POSE = PKG / "EndRiftTentaclePose.java"

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.List;

    public final class TentacleProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        private static boolean near(float actual, float expected, float tolerance) {
            return Math.abs(actual - expected) <= tolerance;
        }

        public static void main(String[] args) {
            // --- Which poses are allowed to turn toward the target -----------------------
            for (String tracking : new String[]{"TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD", "THROW"}) {
                check(tracking + " tracks the target",
                        EndRiftTentacleFacingPolicy.tracksTarget(tracking));
                check(tracking + " tracks when lowercased",
                        EndRiftTentacleFacingPolicy.tracksTarget(tracking.toLowerCase()));
                check(tracking + " tracks with a stage suffix",
                        EndRiftTentacleFacingPolicy.tracksTarget(tracking + "|stage-2"));
                check(tracking + " tracks when padded",
                        EndRiftTentacleFacingPolicy.tracksTarget("  " + tracking + "  "));
            }
            // Hurt and death must NOT swivel toward the target mid-animation.
            for (String frozen : new String[]{"HURT", "DEAD", "DYING", "DEATH", "IDLE", "READY"}) {
                check(frozen + " does not track the target",
                        !EndRiftTentacleFacingPolicy.tracksTarget(frozen));
            }
            check("a null animation does not track",
                    !EndRiftTentacleFacingPolicy.tracksTarget(null));
            check("a blank animation does not track",
                    !EndRiftTentacleFacingPolicy.tracksTarget("   "));
            check("an unknown animation does not track",
                    !EndRiftTentacleFacingPolicy.tracksTarget("SOMETHING_ELSE"));

            // --- The turn rate is bounded --------------------------------------------------
            // A full second of elapsed time is clamped to MAX_STEP_MILLIS, so one call can
            // never turn further than the per-step budget.
            float start = 0.0F;
            float far = (float) Math.PI;
            float stepped = EndRiftTentacleFacingPolicy.advanceYaw(start, far, 1000L);
            check("a huge elapsed time is clamped, not applied whole",
                    !near(stepped, far, 0.01F));
            // A request exactly pi away is the one ambiguous case: both arcs are the same
            // length, and the float wrap resolves the sign of the seam. Assert the STEP SIZE,
            // not the sign - the tentacle turns the same distance either way.
            check("the clamped step has the full per-step magnitude",
                    near(Math.abs(stepped), 0.8F, 1.0E-4F));
            // One tenth of a radian short of pi the direction IS unambiguous.
            float nearlyFar = EndRiftTentacleFacingPolicy.advanceYaw(
                    0.0F, (float) (Math.PI - 0.1D), 1000L);
            check("a nearly-opposite target turns in the positive direction", nearlyFar > 0.0F);
            check("the nearly-opposite turn keeps the per-step budget",
                    near(nearlyFar, 0.8F, 1.0E-4F));

            // Short steps are applied exactly.
            float tiny = EndRiftTentacleFacingPolicy.advanceYaw(0.0F, 0.01F, 10L);
            check("a small requested turn is applied exactly", near(tiny, 0.01F, 1.0E-5F));

            // Zero elapsed time cannot move.
            check("zero elapsed time cannot move",
                    near(EndRiftTentacleFacingPolicy.advanceYaw(0.5F, 2.0F, 0L), 0.5F, 1.0E-6F));
            // Negative elapsed time cannot move either.
            check("negative elapsed time cannot move",
                    near(EndRiftTentacleFacingPolicy.advanceYaw(0.5F, 2.0F, -50L), 0.5F, 1.0E-6F));

            // Already at the target stays put.
            check("already at the target stays put",
                    near(EndRiftTentacleFacingPolicy.advanceYaw(1.0F, 1.0F, 50L), 1.0F, 1.0E-6F));

            // --- The shortest arc, not the long way round ---------------------------------
            // From just below +pi to just above -pi the shortest arc is a small positive move,
            // not a near-full negative turn.
            float almostPi = (float) (Math.PI - 0.05D);
            float almostMinusPi = (float) (-Math.PI + 0.05D);
            float wrapped = EndRiftTentacleFacingPolicy.advanceYaw(almostPi, almostMinusPi, 10L);
            // The two angles are 0.1 rad apart across the seam, so the bounded 0.08 rad step
            // moves FORWARD across +pi and wraps to just above -pi. A long-way-round turn
            // would instead land near +pi minus the step, i.e. still large and positive.
            // Start at +pi-0.05, request -pi+0.05. The two are 0.1 rad apart the SHORT way
            // across the seam, so the bounded 0.08 rad step crosses +pi and lands 0.02 rad
            // short of the target, i.e. at -pi+0.03. Going the long way round would instead
            // stay just below +pi, so landing on the far side proves the short arc.
            check("the seam turn is the short hundredth of a radian, not the long way",
                    near(wrapped, (float) (-Math.PI + 0.03D), 1.0E-4F));
            check("the seam turn actually crossed the +/-pi boundary", wrapped < 0.0F);
            // The result must always be a wrapped angle in (-pi, pi].
            for (long elapsed : new long[]{0L, 5L, 25L, 50L, 100L, 400L}) {
                for (int index = 0; index < 64; index++) {
                    float target = (float) (-10.0D + index * 0.4D);
                    float out = EndRiftTentacleFacingPolicy.advanceYaw(0.3F, target, elapsed);
                    check("result stays wrapped for target " + target,
                            out >= -Math.PI - 1.0E-4F && out <= Math.PI + 1.0E-4F);
                }
            }

            // Non-finite inputs cannot poison the pose.
            check("a NaN current yaw starts from zero",
                    Float.isFinite(EndRiftTentacleFacingPolicy.advanceYaw(Float.NaN, 0.2F, 25L)));
            check("a NaN target yaw leaves the current angle alone",
                    near(EndRiftTentacleFacingPolicy.advanceYaw(0.7F, Float.NaN, 25L), 0.7F, 1.0E-6F));
            check("an infinite target yaw leaves the current angle alone",
                    near(EndRiftTentacleFacingPolicy.advanceYaw(0.7F, Float.POSITIVE_INFINITY, 25L),
                            0.7F, 1.0E-6F));

            // --- World scale ------------------------------------------------------------------
            check("sixteen model units make one block",
                    near(EndRiftTentacleWorldScalePolicy.MODEL_UNITS_PER_BLOCK, 16.0F, 1.0E-6F));
            check("the rig is rendered compact, not arena-sized",
                    EndRiftTentacleWorldScalePolicy.BASE_RENDER_SCALE < 1.0F);
            check("the authored height lands near five blocks",
                    near(EndRiftTentacleWorldScalePolicy.worldHeightForAuthoredUnits(7.8681F, 1.0F),
                            4.9F, 0.1F));
            check("a larger rig scales the render up",
                    near(EndRiftTentacleWorldScalePolicy.rendererScaleForRig(2.0F),
                            2.0F * EndRiftTentacleWorldScalePolicy.BASE_RENDER_SCALE, 1.0E-6F));
            // Degenerate rig scales fall back to one instead of collapsing the model.
            check("a NaN rig scale falls back",
                    near(EndRiftTentacleWorldScalePolicy.rendererScaleForRig(Float.NaN),
                            EndRiftTentacleWorldScalePolicy.BASE_RENDER_SCALE, 1.0E-6F));
            check("a zero rig scale falls back",
                    near(EndRiftTentacleWorldScalePolicy.rendererScaleForRig(0.0F),
                            EndRiftTentacleWorldScalePolicy.BASE_RENDER_SCALE, 1.0E-6F));
            check("a negative rig scale falls back",
                    near(EndRiftTentacleWorldScalePolicy.rendererLengthScaleForRig(-3.0F),
                            EndRiftTentacleWorldScalePolicy.BASE_LENGTH_RENDER_SCALE, 1.0E-6F));
            check("a NaN authored height yields zero",
                    near(EndRiftTentacleWorldScalePolicy.worldHeightForAuthoredUnits(Float.NaN, 1.0F),
                            0.0F, 1.0E-6F));
            check("a negative authored height is clamped to zero",
                    near(EndRiftTentacleWorldScalePolicy.worldHeightForAuthoredUnits(-5.0F, 1.0F),
                            0.0F, 1.0E-6F));
            check("a zero height yields zero",
                    near(EndRiftTentacleWorldScalePolicy.worldHeightForAuthoredUnits(0.0F, 1.0F),
                            0.0F, 1.0E-6F));

            // --- Carrier suppression needs BOTH proofs ---------------------------------------
            check("a bridge binding alone does not suppress the carrier",
                    !EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(true, false));
            check("a ready renderer alone does not suppress the carrier",
                    !EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(false, true));
            check("neither means the carrier stays",
                    !EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(false, false));
            check("both together suppress the carrier",
                    EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(true, true));

            // --- The pose record refuses non-finite or collapsed bones ------------------------
            check("identity is finite", EndRiftTentaclePose.BoneTransform.identity().isFinite());
            check("identity has unit scale",
                    EndRiftTentaclePose.BoneTransform.identity().scaleX() == 1.0F);
            check("a NaN translation is not finite",
                    !new EndRiftTentaclePose.BoneTransform(Float.NaN, 0, 0, 0, 0, 0, 1, 1, 1).isFinite());
            check("a zero scale is not finite",
                    !new EndRiftTentaclePose.BoneTransform(0, 0, 0, 0, 0, 0, 0.0F, 1, 1).isFinite());
            check("a negative scale is not finite",
                    !new EndRiftTentaclePose.BoneTransform(0, 0, 0, 0, 0, 0, 1, -1, 1).isFinite());
            check("a finite socket passes",
                    new EndRiftTentaclePose.Socket(1.0F, 2.0F, 3.0F).isFinite());
            check("an infinite socket fails",
                    !new EndRiftTentaclePose.Socket(1.0F, Float.POSITIVE_INFINITY, 3.0F).isFinite());

            // A null bone must be rejected at construction.
            boolean rejected = false;
            try {
                new EndRiftTentaclePose.TentaclePose(null,
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        EndRiftTentaclePose.BoneTransform.identity(),
                        new EndRiftTentaclePose.Socket(0, 0, 0), 1.0F);
            } catch (NullPointerException expected) {
                rejected = true;
            }
            check("a null root bone is rejected", rejected);

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


def test_tentacle_facing_and_scale_are_bounded(tmp_path: Path) -> None:
    for path in (FACING, SCALE, CARRIER, POSE):
        assert path.is_file(), f"tentacle source is missing: {path}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    probe = package_dir / "TentacleProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(probe), str(FACING), str(SCALE), str(CARRIER), str(POSE)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.TentacleProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "tentacle probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
