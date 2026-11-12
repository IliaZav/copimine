"""Wave 5 music mixing: the track ducks, it never restarts.

``WaveMusicMixPolicy`` is what stops the fog from cutting or restarting the
wave music. It had no behavioural test, so a regression that resets the track
gain on every fog state change - the exact defect the user described as music
restarting repeatedly - would pass every gate.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(CLIENT / "WaveMusicMixPolicy.java"), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_gain_glides_instead_of_jumping(tmp_path):
    """Every step is smaller than the whole remaining distance."""
    run_probe(
        tmp_path,
        "MixProbe",
        """
import me.copimine.client.*;

public final class MixProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        float FOG = 0.30F;

        // Ducking a loud track moves DOWN by a bounded step, never straight to
        // the target: an instant jump is what an abrupt fade sounds like.
        float stepDown = WaveMusicMixPolicy.advance(1.0F, true);
        check(stepDown < 1.0F, "fog must lower the gain");
        check(stepDown > FOG, "one step must not reach the floor: " + stepDown);

        // Restoring moves UP by a bounded step too.
        float stepUp = WaveMusicMixPolicy.advance(FOG, false);
        check(stepUp > FOG, "leaving fog must raise the gain");
        check(stepUp < 1.0F, "one step must not reach full: " + stepUp);

        // Many ticks converge and then STOP, without overshoot.
        float g = 1.0F;
        for (int i = 0; i < 200; i++) g = WaveMusicMixPolicy.advance(g, true);
        check(Math.abs(g - FOG) < 1e-5F, "must settle on the fog gain, got " + g);

        float u = FOG;
        for (int i = 0; i < 200; i++) u = WaveMusicMixPolicy.advance(u, false);
        check(Math.abs(u - 1.0F) < 1e-5F, "must settle on full gain, got " + u);

        // Convergence must not oscillate around the target.
        float p = 1.0F;
        for (int i = 0; i < 40; i++) p = WaveMusicMixPolicy.advance(p, true);
        float q = WaveMusicMixPolicy.advance(p, true);
        check(FOG <= q && q <= p, "gain must never rise while fog is active");

        // Degenerate input must not mute the track permanently.
        check(WaveMusicMixPolicy.advance(Float.NaN, false) > FOG,
                "a NaN gain must recover, not stay silent");
        check(WaveMusicMixPolicy.advance(Float.POSITIVE_INFINITY, true) < 1.0F,
                "an infinite gain must be brought down");
        check(WaveMusicMixPolicy.advance(-5.0F, false) >= FOG,
                "a negative gain must be clamped up");
        check(WaveMusicMixPolicy.advance(9.0F, true) <= 1.0F,
                "an over-loud gain must be clamped down");
    }
}
""",
    )


def test_only_the_wave5_track_is_touched(tmp_path):
    """Nothing but copimine:end_rift/wave_5 may have its volume scaled."""
    run_probe(
        tmp_path,
        "VolumeProbe",
        """
import me.copimine.client.*;

public final class VolumeProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        float base = 0.8F;

        // The fog track is scaled by the factor.
        check(Math.abs(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_5", true, base, 0.5F)
                - 0.4F) < 1e-5F, "the wave 5 track must follow the factor");

        // Anything that is not music keeps its volume untouched.
        check(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_5", false, base, 0.5F) == base,
                "a non-music sound must not be scaled");
        // Another track in our own namespace. This is the assertion that fails
        // if the path check is dropped and the whole mod's audio is ducked.
        check(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_3", true, base, 0.5F) == base,
                "only the wave 5 track is scaled, not wave 3");
        // Another namespace entirely - the user's other mods and vanilla.
        check(WaveMusicMixPolicy.volume("minecraft", "music.game", true, base, 0.5F) == base,
                "vanilla music must never be rewritten");
        check(WaveMusicMixPolicy.volume(null, "end_rift/wave_5", true, base, 0.5F) == base,
                "a null namespace must be left alone");
        check(WaveMusicMixPolicy.volume("copimine", null, true, base, 0.5F) == base,
                "a null path must be left alone");

        // The factor is clamped into the audible band, and NaN cannot silence it.
        check(Math.abs(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_5", true, base, 0.0F)
                - base * 0.30F) < 1e-5F, "a zero factor is floored, not muted to nothing");
        check(Math.abs(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_5", true, base, 5.0F)
                - base) < 1e-5F, "an over-unity factor is capped at the base volume");
        check(WaveMusicMixPolicy.volume("copimine", "end_rift/wave_5", true, base, Float.NaN) == base,
                "a NaN factor leaves the volume unchanged");
    }
}
""",
    )
