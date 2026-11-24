"""Wave 6 moving arena-wide earthquake: a bounded multi-front Trample, not one ring.

The owner-approved design (N23-P1-10) replaces the single stationary radius-12
seam with a favourite "Trample": the Ram roars, then stampedes across the arena
for 5-7 seconds and each deliberate foot contact emits ONE expanding front that
travels outward. The pure rules live in
``me.copimine.endevent.domain.RamMovingEarthquakePolicy``, so that is what these
tests compile and exercise directly. The guarantees the old seam had are kept per
front: a grounded player is hit once while the front sweeps past them, a player
already behind it is safe, a fast tick cannot tunnel, and a player who jumps is
safe. The moving front adds the bounded beat count, the owned per-beat timeline
and the heavier-but-bounded final footfall.
"""
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

PROBE = textwrap.dedent("""
    import me.copimine.endevent.domain.RamMovingEarthquakePolicy;
    import me.copimine.endevent.domain.RamMovingEarthquakePolicy.BeatPhase;

    public final class TrampleProbe {
        private static int checks = 0;

        private static void check(String label, boolean value) {
            if (!value) throw new AssertionError("FAIL " + label);
            checks++;
        }

        public static void main(String[] args) {
            // --- the Trample is bounded to 5..6 footfalls -----------------------
            check("a small party gets the minimum bounded beat count",
                    RamMovingEarthquakePolicy.beatsFor(2) == RamMovingEarthquakePolicy.BEATS_MIN);
            check("a large party gets exactly one extra footfall",
                    RamMovingEarthquakePolicy.beatsFor(20) == RamMovingEarthquakePolicy.BEATS_MAX);
            check("the footfall band is 5..6",
                    RamMovingEarthquakePolicy.BEATS_MIN >= 5
                            && RamMovingEarthquakePolicy.BEATS_MAX <= 6);

            // --- one owned timeline per footfall -------------------------------
            int period = RamMovingEarthquakePolicy.BEAT_PERIOD_TICKS;
            check("a footfall is tell + owned window + planted gap",
                    period == RamMovingEarthquakePolicy.BEAT_WARNING_TICKS
                            + RamMovingEarthquakePolicy.BEAT_FRONT_TICKS
                            + RamMovingEarthquakePolicy.BEAT_GAP_TICKS);
            check("the whole Trample is bounded",
                    RamMovingEarthquakePolicy.totalTicks(RamMovingEarthquakePolicy.BEATS_MAX)
                            == period * RamMovingEarthquakePolicy.BEATS_MAX);
            check("the tell starts at zero",
                    RamMovingEarthquakePolicy.phaseWithin(0) == BeatPhase.WARNING);
            check("the release begins the front",
                    RamMovingEarthquakePolicy.phaseWithin(RamMovingEarthquakePolicy.BEAT_WARNING_TICKS)
                            == BeatPhase.FRONT);
            check("the gap follows the front",
                    RamMovingEarthquakePolicy.phaseWithin(RamMovingEarthquakePolicy.BEAT_WARNING_TICKS
                            + RamMovingEarthquakePolicy.BEAT_FRONT_TICKS) == BeatPhase.GAP);
            check("the beat index is monotonic per period",
                    RamMovingEarthquakePolicy.beatIndex(5, period) == 1
                            && RamMovingEarthquakePolicy.beatIndex(5, period * 4) == 4);

            // --- the front is a travelling annulus, not an instant radius -------
            check("not hit before the front arrives",
                    !RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 2.5D, 3.0D, false));
            check("hit when the front reaches them",
                    RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, false));
            check("a player already behind the front is safe",
                    !RamMovingEarthquakePolicy.frontHits(0.5D, 0.0D, true, 8.0D, 9.0D, false));
            check("a fast tick cannot tunnel past a player",
                    RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 4.0D, 9.0D, false));
            check("a recorded hit is never repeated",
                    !RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, true));

            // --- jumping and elevation clear the front --------------------------
            check("a player well above the floor is safe",
                    !RamMovingEarthquakePolicy.frontHits(6.0D, 1.6D, true, 5.5D, 6.4D, false));
            check("an airborne player is safe",
                    !RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, false, 5.5D, 6.4D, false));
            check("standing on the floor is hittable",
                    RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, false));

            // --- the front never reaches past its own maximum -------------------
            check("the front crosses the arena (bigger than the old radius-12 seam)",
                    RamMovingEarthquakePolicy.FRONT_MAX_RADIUS > 12.0D);
            check("beyond the maximum radius is safe",
                    !RamMovingEarthquakePolicy.frontHits(
                            RamMovingEarthquakePolicy.FRONT_MAX_RADIUS + 1.0D, 0.0D, true,
                            RamMovingEarthquakePolicy.FRONT_MAX_RADIUS - 1.0D,
                            RamMovingEarthquakePolicy.FRONT_MAX_RADIUS, false));
            check("a radius past the maximum is refused",
                    !RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 1.0D,
                            RamMovingEarthquakePolicy.FRONT_MAX_RADIUS * 2.0D, false));

            // --- degenerate input is fail-closed --------------------------------
            check("NaN distance is refused",
                    !RamMovingEarthquakePolicy.frontHits(Double.NaN, 0.0D, true, 1.0D, 2.0D, false));
            check("infinite radius is refused",
                    !RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 1.0D,
                            Double.POSITIVE_INFINITY, false));
            check("a shrinking front is refused",
                    !RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 5.0D, 3.0D, false));
            check("a negative previous radius is refused",
                    !RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, -1.0D, 3.0D, false));

            // --- the footfall really damages, and the last is heavier ------------
            check("the footfall publishes a real damage amount",
                    RamMovingEarthquakePolicy.BEAT_DAMAGE > 0.0D
                            && Double.isFinite(RamMovingEarthquakePolicy.BEAT_DAMAGE));
            check("the final footfall is visibly heavier",
                    RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE
                            > RamMovingEarthquakePolicy.BEAT_DAMAGE);
            check("even the final footfall stays bounded",
                    RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE <= 2.0D * 6.0D);
            check("only the last beat is heavier",
                    RamMovingEarthquakePolicy.damageFor(0, 5) == RamMovingEarthquakePolicy.BEAT_DAMAGE
                            && RamMovingEarthquakePolicy.damageFor(4, 5)
                                    == RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE);

            System.out.println("TRAMPLE_CHECKS " + checks);
        }
    }
""")


def test_the_moving_earthquake_is_a_bounded_multibeat_trample() -> None:
    work = ROOT / "build" / "trample-probe"
    work.mkdir(parents=True, exist_ok=True)
    source = work / "TrampleProbe.java"
    source.write_text(PROBE, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-d", str(work), str(source),
         str(DOMAIN / "RamMovingEarthquakePolicy.java")],
        capture_output=True, text=True,
    )
    assert compile_result.returncode == 0, compile_result.stderr
    run = subprocess.run(["java", "-cp", str(work), "TrampleProbe"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "TRAMPLE_CHECKS" in run.stdout
