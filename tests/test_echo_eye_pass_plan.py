"""The body drain is unconditional; only the eyes wait for the glow ramp.

``EchoEyePassPlan`` is the small policy ``EchoEyesFeature`` consults. It imports
no Minecraft types, so this module compiles the real class and drives it across
the whole glow range. A freshly bound copy has ``glow == 0`` and must still
submit the darkened body pass, which the old early-return skipped.
"""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
PLAN = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/"
               "EchoEyePassPlan.java")

HARNESS = """
import me.copimine.client.EchoEyePassPlan;

public class EchoEyePassPlanChecks {
    static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        // A fresh copy: the eyes are still dark, but the body is already drained.
        int dark = EchoEyePassPlan.passes(0.0F);
        require(EchoEyePassPlan.includes(dark, EchoEyePassPlan.BODY),
                "a dark-eyed copy must still get the darkened body pass");
        require(!EchoEyePassPlan.includes(dark, EchoEyePassPlan.EYES),
                "the eyes must stay dark at glow zero");
        require(!EchoEyePassPlan.includes(EchoEyePassPlan.passes(0.01F), EchoEyePassPlan.EYES),
                "the eye pass must wait for the threshold");
        require(EchoEyePassPlan.includes(EchoEyePassPlan.passes(0.01F), EchoEyePassPlan.BODY),
                "a sub-threshold glow must not drop the body drain");

        // At and above the threshold the eyes join the body pass.
        require(!EchoEyePassPlan.includes(
                        EchoEyePassPlan.passes(EchoEyePassPlan.GLOW_THRESHOLD),
                        EchoEyePassPlan.EYES),
                "the threshold itself is still dark");
        int lit = EchoEyePassPlan.passes(0.03F);
        require(EchoEyePassPlan.includes(lit, EchoEyePassPlan.EYES),
                "the eyes light up above the threshold");
        require(EchoEyePassPlan.includes(lit, EchoEyePassPlan.BODY),
                "the body drain stays present once the eyes light up");

        // Across the whole ramp the body pass is never absent.
        for (int step = 0; step <= 100; step++) {
            int plan = EchoEyePassPlan.passes(step / 100.0F);
            require(EchoEyePassPlan.includes(plan, EchoEyePassPlan.BODY),
                    "the body drain disappeared at glow step " + step);
        }

        System.out.println("EchoEyePassPlan OK");
    }
}
"""


@pytest.fixture(scope="module")
def compiled(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-eye-pass-plan")
    source = directory / "EchoEyePassPlanChecks.java"
    source.write_text(HARNESS, encoding="utf-8")
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory),
         str(PLAN), str(source)],
        capture_output=True, text=True,
    )
    assert build.returncode == 0, build.stdout + build.stderr
    return directory


def test_the_darkened_body_survives_the_whole_eye_glow_ramp(compiled: Path):
    run = subprocess.run(["java", "-cp", str(compiled), "EchoEyePassPlanChecks"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "EchoEyePassPlan OK" in run.stdout
