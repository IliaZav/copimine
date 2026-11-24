import me.copimine.endevent.domain.RamMovingEarthquakePolicy;
import me.copimine.endevent.domain.RamMovingEarthquakePolicy.BeatPhase;

/**
 * N23-P1-10: the Wave 6 moving arena-wide earthquake.
 *
 * <p>The shipped seam was a single stationary radius-12 circle. The approved
 * design is a bounded 5-6 footfall Trample in which the Ram visibly traverses
 * while each foot contact owns exactly one expanding front. These checks pin the
 * pure contract the runtime depends on: a bounded beat count and timeline, one
 * owned front per beat that a jump or a raised block clears, a per-beat damage
 * that only the visibly final footfall raises, a bounded march step, and a gait
 * yaw that points the body along its real translation (never sideways).</p>
 */
public final class RamMovingEarthquakePolicyTest {

    public static void main(String[] args) {
        beatCountIsBounded();
        timelineIsFixedAndOwned();
        beatIndexIsMonotonicAndClamped();
        phaseBoundariesAreExact();
        frontRadiusIsOwnedAndClamped();
        frontIsASweptAnnulusNotAnInstantRadius();
        frontNeverReachesPastItsMaximum();
        jumpAndElevationClearTheFront();
        degenerateInputIsFailClosed();
        onlyTheFinalFootfallIsHeavier();
        marchIsBoundedAndReportsDirection();
        gaitYawPointsAlongTranslation();
        System.out.println("RamMovingEarthquakePolicyTest OK");
    }

    private static void beatCountIsBounded() {
        check(RamMovingEarthquakePolicy.beatsFor(1) == RamMovingEarthquakePolicy.BEATS_MIN,
                "a small party gets the minimum bounded beat count");
        check(RamMovingEarthquakePolicy.beatsFor(2) == RamMovingEarthquakePolicy.BEATS_MIN,
                "a two-player party is bounded");
        check(RamMovingEarthquakePolicy.beatsFor(RamMovingEarthquakePolicy.LARGE_PARTY)
                        == RamMovingEarthquakePolicy.BEATS_MAX,
                "a large party gets exactly one extra footfall");
        check(RamMovingEarthquakePolicy.beatsFor(20) == RamMovingEarthquakePolicy.BEATS_MAX,
                "the beat count never exceeds the documented maximum");
        check(RamMovingEarthquakePolicy.BEATS_MIN >= 5 && RamMovingEarthquakePolicy.BEATS_MAX <= 6,
                "the owner-approved 5..6 footfall band is respected");
    }

    private static void timelineIsFixedAndOwned() {
        int period = RamMovingEarthquakePolicy.BEAT_WARNING_TICKS
                + RamMovingEarthquakePolicy.BEAT_FRONT_TICKS
                + RamMovingEarthquakePolicy.BEAT_GAP_TICKS;
        check(RamMovingEarthquakePolicy.BEAT_PERIOD_TICKS == period,
                "one footfall is exactly tell + owned window + planted gap");
        check(RamMovingEarthquakePolicy.totalTicks(RamMovingEarthquakePolicy.BEATS_MIN)
                        == period * RamMovingEarthquakePolicy.BEATS_MIN,
                "the whole Trample is bounded by beats times period");
        check(RamMovingEarthquakePolicy.totalTicks(RamMovingEarthquakePolicy.BEATS_MAX)
                        == period * RamMovingEarthquakePolicy.BEATS_MAX,
                "the large-party Trample is still bounded");
        check(RamMovingEarthquakePolicy.BEAT_WARNING_TICKS > 0
                        && RamMovingEarthquakePolicy.BEAT_FRONT_TICKS > 0,
                "there is a real tell and a real damage window");
    }

    private static void beatIndexIsMonotonicAndClamped() {
        int period = RamMovingEarthquakePolicy.BEAT_PERIOD_TICKS;
        check(RamMovingEarthquakePolicy.beatIndex(5, -5) == 0, "a negative tick clamps to the first beat");
        check(RamMovingEarthquakePolicy.beatIndex(5, 0) == 0, "tick zero is the first footfall");
        check(RamMovingEarthquakePolicy.beatIndex(5, period) == 1, "the next period is the next footfall");
        check(RamMovingEarthquakePolicy.beatIndex(5, period * 4) == 4, "the fifth footfall is index four");
        check(RamMovingEarthquakePolicy.beatIndex(5, period * 99) == 4,
                "an over-long tick clamps at the last beat instead of overflowing");
        check(RamMovingEarthquakePolicy.withinBeat(period + 3) == 3,
                "the within-beat offset wraps per period");
    }

    private static void phaseBoundariesAreExact() {
        int warning = RamMovingEarthquakePolicy.BEAT_WARNING_TICKS;
        int front = RamMovingEarthquakePolicy.BEAT_FRONT_TICKS;
        check(RamMovingEarthquakePolicy.phaseWithin(0) == BeatPhase.WARNING, "the tell starts at zero");
        check(RamMovingEarthquakePolicy.phaseWithin(warning - 1) == BeatPhase.WARNING,
                "the tell runs to the last warning tick");
        check(RamMovingEarthquakePolicy.phaseWithin(warning) == BeatPhase.FRONT,
                "the first front tick is the release");
        check(RamMovingEarthquakePolicy.phaseWithin(warning + front - 1) == BeatPhase.FRONT,
                "the front runs to its last tick");
        check(RamMovingEarthquakePolicy.phaseWithin(warning + front) == BeatPhase.GAP,
                "the planted gap follows the front");
        check(RamMovingEarthquakePolicy.phaseWithin(RamMovingEarthquakePolicy.BEAT_PERIOD_TICKS - 1)
                        == BeatPhase.GAP,
                "the gap holds to the end of the footfall");
    }

    private static void frontRadiusIsOwnedAndClamped() {
        check(RamMovingEarthquakePolicy.frontRadius(0) == 0.0D, "the front starts at zero");
        check(Math.abs(RamMovingEarthquakePolicy.frontRadius(RamMovingEarthquakePolicy.BEAT_FRONT_TICKS)
                        - RamMovingEarthquakePolicy.FRONT_MAX_RADIUS) < 1.0E-9D,
                "the front reaches its maximum at the end of the window");
        check(RamMovingEarthquakePolicy.frontRadius(RamMovingEarthquakePolicy.BEAT_FRONT_TICKS * 4)
                        == RamMovingEarthquakePolicy.FRONT_MAX_RADIUS,
                "the front radius never exceeds its maximum");
        check(RamMovingEarthquakePolicy.frontRadius(RamMovingEarthquakePolicy.BEAT_FRONT_TICKS / 2)
                        < RamMovingEarthquakePolicy.FRONT_MAX_RADIUS,
                "the front grows monotonically, not instantly");
        check(RamMovingEarthquakePolicy.FRONT_MAX_RADIUS > 12.0D,
                "the front crosses the arena, so it is larger than the old radius-12 seam");
    }

    private static void frontIsASweptAnnulusNotAnInstantRadius() {
        // A player at 6 blocks is not hit while the front has only reached 3.
        check(!RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 2.5D, 3.0D, false),
                "a player is safe until the front arrives");
        // They ARE hit once it sweeps past them.
        check(RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, false),
                "a grounded player is hit when the front reaches them");
        // A player the front already passed stays safe: only the inner bound saves them.
        check(!RamMovingEarthquakePolicy.frontHits(0.5D, 0.0D, true, 8.0D, 9.0D, false),
                "a player behind the front is safe");
        // A fast tick cannot tunnel past a player.
        check(RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 4.0D, 9.0D, false),
                "a fast tick cannot tunnel past a player");
        // Once counted for this front, never again.
        check(!RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, true),
                "a recorded footfall hit is never repeated");
    }

    private static void frontNeverReachesPastItsMaximum() {
        check(!RamMovingEarthquakePolicy.frontHits(RamMovingEarthquakePolicy.FRONT_MAX_RADIUS + 1.0D,
                        0.0D, true, RamMovingEarthquakePolicy.FRONT_MAX_RADIUS - 1.0D,
                        RamMovingEarthquakePolicy.FRONT_MAX_RADIUS, false),
                "a player beyond the front's maximum reach is safe");
        check(RamMovingEarthquakePolicy.frontHits(RamMovingEarthquakePolicy.FRONT_MAX_RADIUS,
                        0.0D, true, RamMovingEarthquakePolicy.FRONT_MAX_RADIUS - 1.0D,
                        RamMovingEarthquakePolicy.FRONT_MAX_RADIUS, false),
                "a player on the outer rim is caught by the final frame");
        check(!RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 1.0D,
                        RamMovingEarthquakePolicy.FRONT_MAX_RADIUS * 2.0D, false),
                "a radius past the maximum is refused");
    }

    private static void jumpAndElevationClearTheFront() {
        check(!RamMovingEarthquakePolicy.frontHits(6.0D, 1.6D, true, 5.5D, 6.4D, false),
                "a player who jumped well clear is safe");
        check(!RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, false, 5.5D, 6.4D, false),
                "an airborne player is safe");
        check(RamMovingEarthquakePolicy.frontHits(6.0D, 0.0D, true, 5.5D, 6.4D, false),
                "a player standing on the floor is hittable");
    }

    private static void degenerateInputIsFailClosed() {
        check(!RamMovingEarthquakePolicy.frontHits(Double.NaN, 0.0D, true, 1.0D, 2.0D, false),
                "a NaN distance is refused");
        check(!RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 1.0D,
                        Double.POSITIVE_INFINITY, false),
                "an infinite radius is refused");
        check(!RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, 5.0D, 3.0D, false),
                "a shrinking front is refused");
        check(!RamMovingEarthquakePolicy.frontHits(4.0D, 0.0D, true, -1.0D, 3.0D, false),
                "a negative previous radius is refused");
    }

    private static void onlyTheFinalFootfallIsHeavier() {
        check(RamMovingEarthquakePolicy.damageFor(0, 5) == RamMovingEarthquakePolicy.BEAT_DAMAGE,
                "an ordinary footfall deals the base damage");
        check(RamMovingEarthquakePolicy.damageFor(4, 5) == RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE,
                "the last footfall of a five-beat Trample is heavier");
        check(RamMovingEarthquakePolicy.damageFor(5, 6) == RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE,
                "the last footfall of a six-beat Trample is heavier");
        check(RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE > RamMovingEarthquakePolicy.BEAT_DAMAGE,
                "the final footfall is visibly stronger");
        check(RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE <= 2.0D * 6.0D,
                "even the final footfall stays bounded, not unavoidable one-shot damage");
    }

    private static void marchIsBoundedAndReportsDirection() {
        double[] exact = RamMovingEarthquakePolicy.march(0, 0, 3, 0, 4);
        check(exact != null && Math.abs(exact[0] - 3.0D) < 1.0E-9D && Math.abs(exact[1]) < 1.0E-9D,
                "a step inside the bound moves exactly to the target");
        double[] clamped = RamMovingEarthquakePolicy.march(0, 0, 10, 0, 4);
        check(clamped != null && Math.abs(clamped[0] - 4.0D) < 1.0E-9D
                        && Math.abs(clamped[1]) < 1.0E-9D,
                "a step beyond the bound is clamped to the max step");
        check(clamped != null && Math.abs(clamped[2] - 10.0D) < 1.0E-9D,
                "the caller still receives the full desired direction for facing");
        check(RamMovingEarthquakePolicy.march(0, 0, 1, 0, -1) == null,
                "a negative step budget is refused");
        check(RamMovingEarthquakePolicy.march(Double.NaN, 0, 1, 0, 1) == null,
                "a non-finite origin is refused");
    }

    private static void gaitYawPointsAlongTranslation() {
        check(Math.abs(RamMovingEarthquakePolicy.gaitYaw(0, 1)) < 1.0E-4F,
                "+Z is yaw zero, matching the Ram's face helper");
        check(Math.abs(RamMovingEarthquakePolicy.gaitYaw(1, 0) + 90.0F) < 1.0E-4F,
                "+X is yaw -90, so the body turns into the tangent instead of sliding");
        check(Math.abs(RamMovingEarthquakePolicy.gaitYaw(0, -1) - 180.0F) < 1.0E-4F,
                "-Z is yaw 180");
        check(Math.abs(RamMovingEarthquakePolicy.gaitYaw(-1, 0) - 90.0F) < 1.0E-4F,
                "-X is yaw 90");
        check(RamMovingEarthquakePolicy.gaitYaw(0, 0) == 0.0F,
                "a zero translation has no meaningful yaw");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
