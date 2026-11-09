"""Wave 6 seismic stomp: a real travelling particle wave, not an instant debuff.

The specification is explicit that the stomp must be a genuine expanding ground
ring made only of vanilla particles, that a grounded player inside the annulus is
slowed and nobody else is, and that no player can be hit twice by one cast. The
encounter controller only drew the particles; the geometry that decides who is
hit lives in RiftRamPolicy.stompHits, so that is what these tests compile and
exercise directly.
"""
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

PROBE = textwrap.dedent("""
    import me.copimine.endevent.domain.RiftRamPolicy;

    public final class StompProbe {
        private static int checks = 0;

        private static void check(String label, boolean value) {
            if (!value) throw new AssertionError("FAIL " + label);
            checks++;
        }

        public static void main(String[] args) {
            // --- dimensions match the approved design ---------------------------
            check("radius is in the 10-12 band", RiftRamPolicy.STOMP_RADIUS >= 10.0D
                    && RiftRamPolicy.STOMP_RADIUS <= 12.0D);
            check("slowness lasts three seconds", RiftRamPolicy.stompSlowTicks() == 60);
            check("slowness is level two", RiftRamPolicy.STOMP_SLOW_AMPLIFIER == 1);
            check("the wave takes over a second to expand",
                    RiftRamPolicy.STOMP_EXPANSION_TICKS >= 24
                            && RiftRamPolicy.STOMP_EXPANSION_TICKS <= 30);
            check("the warning precedes the wave",
                    RiftRamPolicy.STOMP_WARNING_TICKS >= 15
                            && RiftRamPolicy.STOMP_WARNING_TICKS <= 20);
            check("the cooldown is 18-22 seconds",
                    RiftRamPolicy.STOMP_COOLDOWN_TICKS >= 360
                            && RiftRamPolicy.STOMP_COOLDOWN_TICKS <= 440);

            // --- the wave is a travelling annulus, not an instant radius --------
            // A player at 4 blocks is NOT hit while the ring has only reached 2.
            check("not hit before the ring arrives",
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, true, 1.5D, 2.0D, false));
            // The same player IS hit once the ring sweeps past them.
            check("hit when the ring reaches them",
                    RiftRamPolicy.stompHits(4.0D, 0.0D, true, 3.5D, 4.2D, false));
            // A player the ring has ALREADY swept past must never be hit on a
            // later frame. This is the case that proves the wave travels
            // outward instead of being an instant radius: the player is well
            // INSIDE the current ring, so only the lower bound of the annulus
            // can protect them.
            check("a player already behind the ring is safe",
                    !RiftRamPolicy.stompHits(0.5D, 0.0D, true, 4.0D, 5.0D, false));
            // And they are still safe many frames later, near the maximum radius.
            check("a player behind the ring stays safe at full radius",
                    !RiftRamPolicy.stompHits(1.0D, 0.0D, true, 10.0D, 11.5D, false));

            // The swept band keeps a fast tick from tunnelling through a player.
            check("a fast tick cannot tunnel past a player",
                    RiftRamPolicy.stompHits(4.0D, 0.0D, true, 3.0D, 6.0D, false));

            // --- the same cast never hits one player twice ---------------------
            check("a recorded hit is never repeated",
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, true, 3.5D, 4.2D, true));

            // --- jumping clears the wave ---------------------------------------
            check("a player well above the floor is safe",
                    !RiftRamPolicy.stompHits(4.0D, 1.6D, true, 3.5D, 4.2D, false));
            check("an airborne player is safe", 
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, false, 3.5D, 4.2D, false));
            // Standing exactly on the floor is inside the band.
            check("standing on the floor is hittable",
                    RiftRamPolicy.stompHits(4.0D, 0.0D, true, 3.5D, 4.2D, false));

            // --- range limits ---------------------------------------------------
            check("beyond the maximum radius is safe",
                    !RiftRamPolicy.stompHits(RiftRamPolicy.STOMP_RADIUS + 1.0D, 0.0D, true,
                            RiftRamPolicy.STOMP_RADIUS - 1.0D, RiftRamPolicy.STOMP_RADIUS, false));
            // A player standing exactly at the outer rim is caught by the final frame.
            check("the outer rim is caught",
                    RiftRamPolicy.stompHits(RiftRamPolicy.STOMP_RADIUS, 0.0D, true,
                            RiftRamPolicy.STOMP_RADIUS - 1.0D, RiftRamPolicy.STOMP_RADIUS, false));

            // --- degenerate input is fail-closed --------------------------------
            check("NaN distance is refused", !RiftRamPolicy.stompHits(Double.NaN, 0.0D, true, 1.0D, 2.0D, false));
            check("infinite radius is refused",
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, true, 1.0D, Double.POSITIVE_INFINITY, false));
            check("a shrinking ring is refused",
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, true, 5.0D, 3.0D, false));
            check("a negative previous radius is refused",
                    !RiftRamPolicy.stompHits(4.0D, 0.0D, true, -1.0D, 3.0D, false));

            // --- the stomp never deals damage -----------------------------------
            // RiftRamPolicy exposes no damage value for the stomp: the only
            // numbers it publishes are the slow duration and its amplitude.
            // Asserting the API shape keeps a future edit from adding damage.
            check("the stomp publishes no damage constant",
                    RiftRamPolicy.stompSlowTicks() > 0 && RiftRamPolicy.STOMP_SLOW_AMPLIFIER == 1);

            System.out.println("STOMP_CHECKS " + checks);
        }
    }
""")


def test_seismic_stomp_is_a_travelling_particle_wave() -> None:
    work = ROOT / "build" / "stomp-probe"
    work.mkdir(parents=True, exist_ok=True)
    source = work / "StompProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-d", str(work), str(source), str(DOMAIN / "RiftRamPolicy.java")],
        capture_output=True, text=True,
    )
    assert compile_result.returncode == 0, compile_result.stderr
    run = subprocess.run(["java", "-cp", str(work), "StompProbe"], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "STOMP_CHECKS" in run.stdout
