"""The echo arrival ramps, exercised as arithmetic, not as source text.

`EchoAppearanceClock` holds the two timings the client uses to make a copy
appear with an animation and light its eyes gradually. It deliberately imports
no Minecraft types, so this module compiles the real class and drives it with
fake instants. A source-only test could not tell a correct ramp from one that
never starts.
"""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
CLOCK = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/"
                "EchoAppearanceClock.java")

HARNESS = """
import me.copimine.client.EchoAppearanceClock;

public class EchoAppearanceClockChecks {
    static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    static void near(float actual, float expected, String reason) {
        if (Math.abs(actual - expected) > 1.0e-4F) {
            throw new AssertionError(reason + " expected=" + expected + " actual=" + actual);
        }
    }
    public static void main(String[] args) {
        EchoAppearanceClock clock = new EchoAppearanceClock();

        // Before the first drawn frame there is nothing to animate.
        require(!clock.appeared(), "a fresh clock is not appeared");
        near(clock.spawn(1234L), 1.0F, "undrawn spawn");
        near(clock.eyeGlow(1234L), 1.0F, "undrawn eye glow");

        clock.mark(10_000L);
        require(clock.appeared(), "mark makes it appeared");
        near(clock.spawn(10_000L), 0.0F, "spawn at arrival");
        near(clock.eyeGlow(10_000L), 0.0F, "eyes dark at arrival");

        // mark() keeps the first instant; a later mark must not restart it.
        clock.mark(99_999L);
        near(clock.spawn(10_000L), 0.0F, "a second mark must not restart the ramp");

        // The body grows monotonically and completes at SPAWN_MILLIS.
        float previous = -1.0F;
        for (long step = 10_000L; step <= 10_000L + EchoAppearanceClock.SPAWN_MILLIS; step += 25L) {
            float value = clock.spawn(step);
            require(value >= previous, "spawn must never go backwards at step " + step);
            require(value >= 0.0F && value <= 1.0F, "spawn stays in range");
            previous = value;
        }
        near(clock.spawn(10_000L + EchoAppearanceClock.SPAWN_MILLIS), 1.0F, "spawn complete");

        // The eyes stay dark through the delay, then fade up monotonically.
        long eyeStart = 10_000L + EchoAppearanceClock.EYE_DELAY_MILLIS;
        require(clock.eyeGlow(eyeStart) == 0.0F, "eyes are dark at the end of the delay");
        near(clock.eyeGlow(eyeStart + EchoAppearanceClock.EYE_FADE_MILLIS / 2L), 0.5F,
                "eyes are half lit mid-fade");
        near(clock.eyeGlow(eyeStart + EchoAppearanceClock.EYE_FADE_MILLIS), 1.0F,
                "eyes fully lit at the end of the fade");
        float glow = -1.0F;
        for (long step = 10_000L; step <= eyeStart + EchoAppearanceClock.EYE_FADE_MILLIS; step += 25L) {
            float value = clock.eyeGlow(step);
            require(value >= glow, "eye glow must never go backwards at step " + step);
            require(value >= 0.0F && value <= 1.0F, "eye glow stays in range");
            glow = value;
        }

        // A clock that went backwards in time must not report a started ramp.
        near(clock.spawn(5_000L), 0.0F, "time before arrival is still zero");
        near(clock.eyeGlow(5_000L), 0.0F, "time before arrival keeps eyes dark");

        // Scale ramps from the born size to full size and clamps outside [0,1].
        near(EchoAppearanceClock.materialiseScale(0.0F), EchoAppearanceClock.BORN_SCALE, "born scale");
        near(EchoAppearanceClock.materialiseScale(1.0F), EchoAppearanceClock.FULL_SCALE, "full scale");
        near(EchoAppearanceClock.materialiseScale(-3.0F), EchoAppearanceClock.BORN_SCALE, "clamped low");
        near(EchoAppearanceClock.materialiseScale(4.0F), EchoAppearanceClock.FULL_SCALE, "clamped high");
        float scale = -1.0F;
        for (int i = 0; i <= 100; i++) {
            float value = EchoAppearanceClock.materialiseScale(i / 100.0F);
            require(value >= scale, "scale must never shrink as the body forms");
            scale = value;
        }

        // Resetting re-arms the arrival, so a recycled view materialises again.
        clock.reset();
        require(!clock.appeared(), "reset clears the arrival");
        clock.mark(50_000L);
        near(clock.spawn(50_000L), 0.0F, "reset spawn re-arms at zero");

        System.out.println("EchoAppearanceClock OK");
    }
}
"""


@pytest.fixture(scope="module")
def compiled(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-appearance-clock")
    source = directory / "EchoAppearanceClockChecks.java"
    source.write_text(HARNESS, encoding="utf-8")
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory),
         str(CLOCK), str(source)],
        capture_output=True, text=True,
    )
    assert build.returncode == 0, build.stdout + build.stderr
    return directory


def test_the_arrival_and_eye_ramps_are_deterministic(compiled: Path):
    run = subprocess.run(["java", "-cp", str(compiled), "EchoAppearanceClockChecks"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "EchoAppearanceClock OK" in run.stdout
