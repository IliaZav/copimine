"""The Kagune importer reads the supplied tentacle rig, and its sampler drives every frame.

Complaint (10) covers the wave 6 fight; the tentacle rig is picked, posed and blended there. The
importer turns the generated runtime file into bones, cuboids, UVs and clips, and sample()
interpolates those clips per frame with three interpolation modes, looping and edge
extrapolation. None of it had a behavioural test - only the artifact check that the class ships
in the jar.

Two things matter beyond taste. A sampler that returns garbage at the clip edges is exactly
what a tentacle snapping back to rest looks like; and a parser that accepts an unknown parent
bone or a cuboid on a missing bone produces a rig that renders in the wrong place, silently.
"""
from __future__ import annotations

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
IMPORTER = CLIENT_SRC / "me/copimine/client/KaguneModelImporter.java"

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import com.google.gson.JsonParser;

    import java.util.List;
    import java.util.Map;

    public final class KaguneProbe {
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

        private static KaguneModelImporter.Vec3 v(float x, float y, float z) {
            return new KaguneModelImporter.Vec3(x, y, z);
        }

        private static String face(String uv) {
            return "{\\"uv\\":[" + uv + "]}";
        }

        private static String faces(String uv) {
            return "{\\"north\\":" + face(uv) + ",\\"east\\":" + face(uv)
                    + ",\\"south\\":" + face(uv) + ",\\"west\\":" + face(uv)
                    + ",\\"up\\":" + face(uv) + ",\\"down\\":" + face(uv) + "}";
        }

        /** One bone, one cuboid, and a clip with the given track JSON. */
        private static String model(String extraBone, String extraCuboid, String animations) {
            return "{"
                    + "\\"format\\":\\"copimine:kagune-import-v1\\","
                    + "\\"texture\\":{\\"width\\":64,\\"height\\":64,\\"uv_size\\":[64,64]},"
                    + "\\"groups\\":["
                    + "{\\"uuid\\":\\"u-root\\",\\"name\\":\\"root\\",\\"parent\\":null,"
                    + "\\"origin\\":[0,0,0],\\"rotation\\":[0,0,0]}"
                    + extraBone
                    + "],"
                    + "\\"elements\\":["
                    + "{\\"uuid\\":\\"e-1\\",\\"name\\":\\"seg\\",\\"bone\\":\\"root\\","
                    + "\\"from\\":[0,0,0],\\"to\\":[1,2,3],\\"origin\\":[0,0,0],\\"rotation\\":[0,0,0],"
                    + "\\"faces\\":" + faces("0,0,1,1") + "}"
                    + extraCuboid
                    + "],"
                    + "\\"animations\\":{" + animations + "}"
                    + "}";
        }

        private static String clip(String name, double length, String loop, String tracks) {
            return "\\"" + name + "\\":{\\"length\\":" + length + ",\\"loop\\":\\"" + loop
                    + "\\",\\"tracks\\":{" + tracks + "}}";
        }

        private static String track(String bone, String channel, String frames) {
            return "\\"" + bone + "\\":{\\"" + channel + "\\":[" + frames + "]}";
        }

        private static String frame(double time, float x, String interpolation) {
            return "{\\"time\\":" + time + ",\\"value\\":[" + x + ",0,0],"
                    + "\\"interpolation\\":\\"" + interpolation + "\\"}";
        }

        private static KaguneModelImporter.ImportedModel parse(String json) {
            return KaguneModelImporter.parse(JsonParser.parseString(json).getAsJsonObject());
        }

        public static void main(String[] args) {
            // --- A minimal model parses ---------------------------------------------------
            String linear = clip("grab", 2.0D, "once",
                    track("root", "rotation", frame(0.0D, 0.0F, "linear") + ","
                            + frame(2.0D, 10.0F, "linear")));
            KaguneModelImporter.ImportedModel base = parse(model("", "", linear));
            check("the texture size is read", base.textureWidth() == 64 && base.textureHeight() == 64);
            check("the uv size is read", base.textureUvWidth() == 64 && base.textureUvHeight() == 64);
            check("one bone parses", base.bones().size() == 1);
            check("the bone is addressable by name", base.bone("root") != null);
            check("an unknown bone is null", base.bone("nope") == null);
            check("one cuboid parses", base.elements().size() == 1);
            check("the clip parses", base.animation("grab") != null);
            check("a missing clip is null", base.animation("nope") == null);
            check("the bounds follow the cuboid",
                    near(base.bounds().width(), 1.0F, 1.0E-6F)
                            && near(base.bounds().height(), 2.0F, 1.0E-6F)
                            && near(base.bounds().depth(), 3.0F, 1.0E-6F));

            // --- The format tag is enforced ------------------------------------------------
            boolean rejectedFormat = false;
            try {
                parse("{\\"format\\":\\"wrong\\",\\"texture\\":{\\"width\\":1,\\"height\\":1,"
                        + "\\"uv_size\\":[1,1]},\\"groups\\":[],\\"elements\\":[],\\"animations\\":{}}");
            } catch (IllegalArgumentException expected) {
                rejectedFormat = true;
            }
            check("an unsupported format is rejected", rejectedFormat);

            // --- Structural errors are caught, not silently rendered -----------------------
            boolean rejectedParent = false;
            try {
                parse(model(",{\\"uuid\\":\\"u-b\\",\\"name\\":\\"b\\",\\"parent\\":\\"ghost\\","
                        + "\\"origin\\":[0,0,0],\\"rotation\\":[0,0,0]}", "", linear));
            } catch (IllegalArgumentException expected) {
                rejectedParent = true;
            }
            check("an unknown parent bone is rejected", rejectedParent);

            boolean rejectedDuplicate = false;
            try {
                parse(model(",{\\"uuid\\":\\"u-2\\",\\"name\\":\\"root\\",\\"parent\\":null,"
                        + "\\"origin\\":[0,0,0],\\"rotation\\":[0,0,0]}", "", linear));
            } catch (IllegalArgumentException expected) {
                rejectedDuplicate = true;
            }
            check("a duplicate bone name is rejected", rejectedDuplicate);

            boolean rejectedCuboid = false;
            try {
                parse(model("", ",{\\"uuid\\":\\"e-2\\",\\"name\\":\\"x\\",\\"bone\\":\\"ghost\\","
                        + "\\"from\\":[0,0,0],\\"to\\":[1,1,1],\\"origin\\":[0,0,0],"
                        + "\\"rotation\\":[0,0,0],\\"faces\\":" + faces("0,0,1,1") + "}", linear));
            } catch (IllegalArgumentException expected) {
                rejectedCuboid = true;
            }
            check("a cuboid on a missing bone is rejected", rejectedCuboid);

            boolean rejectedUv = false;
            try {
                parse(model("", ",{\\"uuid\\":\\"e-2\\",\\"name\\":\\"x\\",\\"bone\\":\\"root\\","
                        + "\\"from\\":[0,0,0],\\"to\\":[1,1,1],\\"origin\\":[0,0,0],"
                        + "\\"rotation\\":[0,0,0],\\"faces\\":{\\"north\\":{\\"uv\\":[0,0,1]},"
                        + "\\"east\\":" + face("0,0,1,1") + ",\\"south\\":" + face("0,0,1,1")
                        + ",\\"west\\":" + face("0,0,1,1") + ",\\"up\\":" + face("0,0,1,1")
                        + ",\\"down\\":" + face("0,0,1,1") + "}}", linear));
            } catch (IllegalArgumentException | NullPointerException expected) {
                rejectedUv = true;
            }
            check("a malformed UV is rejected", rejectedUv);

            // Missing top-level sections are rejected too.
            boolean rejectedMissing = false;
            try {
                parse("{\\"format\\":\\"copimine:kagune-import-v1\\","
                        + "\\"texture\\":{\\"width\\":1,\\"height\\":1,\\"uv_size\\":[1,1]}}");
            } catch (IllegalArgumentException | NullPointerException expected) {
                rejectedMissing = true;
            }
            check("a model with no groups is rejected", rejectedMissing);

            // --- Linear sampling -----------------------------------------------------------
            KaguneModelImporter.Animation grab = base.animation("grab");
            check("the clip start samples the first key",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", 0.0F).x(), 0.0F, 1.0E-5F));
            check("the clip end samples the last key",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", 1.0F).x(), 10.0F, 1.0E-4F));
            check("the clip midpoint is interpolated linearly",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", 0.5F).x(), 5.0F, 1.0E-4F));
            check("a quarter of the way is a quarter of the value",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", 0.25F).x(), 2.5F, 1.0E-4F));
            // Progress outside [0,1] must land on the edge values, never extrapolate off the
            // end. NOTE: the source also clamps progress into [0,1], but that clamp is not
            // separately observable - the "time <= first key" branch and the trailing
            // "return last key" already cover both edges, so removing the clamp changes no
            // result for any input (verified by mutation). These assertions pin the BEHAVIOUR;
            // they are not evidence that the clamp itself is exercised.
            check("negative progress clamps to the first key",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", -5.0F).x(), 0.0F, 1.0E-4F));
            check("progress beyond one clamps to the last key",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", 9.0F).x(), 10.0F, 1.0E-4F));
            check("NaN progress is treated as the start",
                    near(KaguneModelImporter.sample(grab, "root", "rotation", Float.NaN).x(), 0.0F, 1.0E-4F));

            // Missing animation, bone, channel, and a null argument all yield zero.
            check("a null animation yields zero",
                    near(KaguneModelImporter.sample(null, "root", "rotation", 0.5F).x(), 0.0F, 1.0E-6F));
            check("an unknown bone yields zero",
                    near(KaguneModelImporter.sample(grab, "ghost", "rotation", 0.5F).x(), 0.0F, 1.0E-6F));
            check("an unknown channel yields zero",
                    near(KaguneModelImporter.sample(grab, "root", "position", 0.5F).x(), 0.0F, 1.0E-6F));
            check("a null bone yields zero",
                    near(KaguneModelImporter.sample(grab, null, "rotation", 0.5F).x(), 0.0F, 1.0E-6F));

            // --- Step interpolation holds the earlier key until the next one ----------------
            String stepped = clip("throw", 2.0D, "once",
                    track("root", "rotation", frame(0.0D, 1.0F, "step") + ","
                            + frame(1.0D, 9.0F, "step")));
            KaguneModelImporter.ImportedModel stepModel = parse(model("", "", stepped));
            KaguneModelImporter.Animation throwClip = stepModel.animation("throw");
            check("step holds the before-key at the start",
                    near(KaguneModelImporter.sample(throwClip, "root", "rotation", 0.1F).x(), 1.0F, 1.0E-5F));
            check("step holds the before-key just before the next key",
                    near(KaguneModelImporter.sample(throwClip, "root", "rotation", 0.45F).x(), 1.0F, 1.0E-5F));
            check("step jumps to the after-key past the next key",
                    near(KaguneModelImporter.sample(throwClip, "root", "rotation", 0.6F).x(), 9.0F, 1.0E-5F));

            // --- Catmull-Rom stays inside the authored envelope -----------------------------
            String curved = clip("hurt", 2.0D, "once",
                    track("root", "rotation", frame(0.0D, 0.0F, "catmullrom") + ","
                            + frame(1.0D, 4.0F, "catmullrom") + ","
                            + frame(2.0D, 6.0F, "catmullrom")));
            KaguneModelImporter.ImportedModel curveModel = parse(model("", "", curved));
            KaguneModelImporter.Animation hurtClip = curveModel.animation("hurt");
            check("catmullrom hits the first key exactly",
                    near(KaguneModelImporter.sample(hurtClip, "root", "rotation", 0.0F).x(), 0.0F, 1.0E-4F));
            check("catmullrom hits an interior key at its own time",
                    near(KaguneModelImporter.sample(hurtClip, "root", "rotation", 0.5F).x(), 4.0F, 1.0E-3F));
            check("catmullrom hits the last key exactly",
                    near(KaguneModelImporter.sample(hurtClip, "root", "rotation", 1.0F).x(), 6.0F, 1.0E-3F));
            float between = KaguneModelImporter.sample(hurtClip, "root", "rotation", 0.25F).x();
            check("catmullrom stays between the neighbouring keys", between > 0.0F && between < 4.0F);

            // --- Looping clips must not snap at the wrap ------------------------------------
            String looping = clip("idle", 2.0D, "loop",
                    track("root", "rotation", frame(0.0D, 0.0F, "catmullrom") + ","
                            + frame(1.0D, 8.0F, "catmullrom") + ","
                            + frame(2.0D, 0.0F, "catmullrom")));
            KaguneModelImporter.ImportedModel loopModel = parse(model("", "", looping));
            KaguneModelImporter.Animation idleClip = loopModel.animation("idle");
            check("an idle loop starts at its first key",
                    near(KaguneModelImporter.sample(idleClip, "root", "rotation", 0.0F).x(), 0.0F, 1.0E-3F));
            check("an idle loop peaks at its middle key",
                    near(KaguneModelImporter.sample(idleClip, "root", "rotation", 0.5F).x(), 8.0F, 1.0E-2F));
            check("an idle loop returns to its first key at the end",
                    near(KaguneModelImporter.sample(idleClip, "root", "rotation", 1.0F).x(), 0.0F, 1.0E-3F));
            // Just past the seam the value must stay near the start value, not jump.
            float seam = KaguneModelImporter.sample(idleClip, "root", "rotation", 0.99F).x();
            check("an idle loop stays near the start value at the very end", Math.abs(seam) < 2.0F);

            System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """
)


def _toolchain() -> tuple[Path, Path, Path]:
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/javac.exe"))
    assert candidates, "no local JDK found under local-runtime/toolchains"
    javac = candidates[0]
    gson = sorted(ROOT.glob("local-runtime/end-rift-server-26.3/libraries/com/google/code/gson/**/gson-*.jar"))
    assert gson, "no gson jar found under the server libraries"
    return javac, javac.with_name("java.exe"), gson[-1]


def test_kagune_importer_parses_and_samples(tmp_path: Path) -> None:
    assert IMPORTER.is_file(), f"Kagune importer source is missing: {IMPORTER}"
    javac, java, gson = _toolchain()

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    probe = package_dir / "KaguneProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-cp", str(gson),
         "-d", str(tmp_path / "out"), str(probe), str(IMPORTER)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out") + ";" + str(gson),
         "me.copimine.client.KaguneProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "Kagune probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
