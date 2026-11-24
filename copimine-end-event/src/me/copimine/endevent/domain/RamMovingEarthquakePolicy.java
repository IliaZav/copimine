package me.copimine.endevent.domain;

/**
 * Authoritative rules for the Wave 6 Rift Ram moving arena-wide earthquake.
 *
 * <p>The shipped seam was a single stationary radius-12 circle drawn from one
 * snapshot origin ({@link RiftRamPolicy#stompHits}). The owner-approved design is
 * an original "Trample": the Ram roars, then stampedes across the arena for 5-7
 * seconds, and each deliberate foot contact emits ONE expanding earthquake front
 * that travels outward to the arena edge. This class owns the pure, testable part
 * of that contract so the runtime only has to hold state:</p>
 *
 * <ul>
 *   <li>a bounded beat count (5..6) derived from the living party size;</li>
 *   <li>a fixed per-beat timeline (tell, one owned damage window, planted gap);</li>
 *   <li>one owned front radius per beat, clamped to the arena-spanning maximum;</li>
 *   <li>a swept-annulus hit test so a grounded player is hit once per front and a
 *       player who times a jump (or stands on a raised block) is safe;</li>
 *   <li>a bounded march step and a gait yaw that points the body along its actual
 *       translation, so the Ram never slides sideways;</li>
 *   <li>a per-beat damage that is a little heavier on the visibly final footfall
 *       but never unbounded or unavoidable.</li>
 * </ul>
 *
 * <p>Nothing here touches Bukkit. The one rule the caller must honour is that a
 * front is only live during {@link BeatPhase#FRONT}; the tell and the planted gap
 * deal no damage, which is what makes the jump window readable.</p>
 */
public final class RamMovingEarthquakePolicy {
    /** The party-size-independent lower bound on footfalls. */
    public static final int BEATS_MIN = 5;
    /** The upper bound; a large party gets one extra, still bounded footfall. */
    public static final int BEATS_MAX = 6;
    /** At or above this many living players the tell gains one extra footfall. */
    public static final int LARGE_PARTY = 8;

    /** Plants the foot and marks the ground before the front is released. */
    public static final int BEAT_WARNING_TICKS = 16;
    /** The single owned damage window while the front travels outward. */
    public static final int BEAT_FRONT_TICKS = 24;
    /** Feet planted, no damage, between two footfalls. */
    public static final int BEAT_GAP_TICKS = 8;
    /** Total time for one footfall, warning through gap. */
    public static final int BEAT_PERIOD_TICKS = BEAT_WARNING_TICKS + BEAT_FRONT_TICKS + BEAT_GAP_TICKS;

    /**
     * How far a front travels before it stops.
     *
     * <p>The front is meant to cross the arena, so this is deliberately larger
     * than the old radius-12 seam. The caller may clip the drawn particles to the
     * arena bounds; the hit test uses this same number so the damage edge can
     * never grow past the shown front.</p>
     */
    public static final double FRONT_MAX_RADIUS = 26.0D;
    /** Small tolerance so a player on the drawn edge is treated fairly, not magically safe. */
    public static final double FRONT_EDGE_SLACK = 0.75D;
    /** Damage of one accepted footfall. */
    public static final double BEAT_DAMAGE = 5.0D;
    /** The last, visibly heavier footfall. Bounded: still under two melee hits. */
    public static final double FINAL_BEAT_DAMAGE = 6.5D;
    /** Slowness on an accepted footfall. Refresh-only, so repeated hits cannot stack. */
    public static final int BEAT_SLOW_TICKS = 40;
    public static final int BEAT_SLOW_AMPLIFIER = 0;

    /** The largest single step the body may take toward a target while marching. */
    public static final double MARCH_STEP = 4.0D;

    private RamMovingEarthquakePolicy() { }

    /** True when every argument is a real, finite number. */
    public static boolean allFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    /** How many footfalls this Trample will make. Bounded to [BEATS_MIN, BEATS_MAX]. */
    public static int beatsFor(int livingPlayers) {
        return livingPlayers >= LARGE_PARTY ? BEATS_MAX : BEATS_MIN;
    }

    /** Total ticks the whole Trample lasts for the given (clamped) beat count. */
    public static int totalTicks(int beats) {
        return Math.max(1, Math.min(BEATS_MAX, beats)) * BEAT_PERIOD_TICKS;
    }

    /** Which 0-based footfall an elapsed tick falls in; clamps at the last beat. */
    public static int beatIndex(int beats, int elapsed) {
        int count = Math.max(1, Math.min(BEATS_MAX, beats));
        if (elapsed < 0) return 0;
        return Math.min(count - 1, elapsed / BEAT_PERIOD_TICKS);
    }

    /** The tick offset inside the current footfall. */
    public static int withinBeat(int elapsed) {
        if (elapsed < 0) return 0;
        return elapsed % BEAT_PERIOD_TICKS;
    }

    /** The sub-phase of one footfall. */
    public enum BeatPhase { WARNING, FRONT, GAP }

    /** The sub-phase at the given offset inside a footfall. */
    public static BeatPhase phaseWithin(int elapsedWithinBeat) {
        if (elapsedWithinBeat < BEAT_WARNING_TICKS) return BeatPhase.WARNING;
        if (elapsedWithinBeat < BEAT_WARNING_TICKS + BEAT_FRONT_TICKS) return BeatPhase.FRONT;
        return BeatPhase.GAP;
    }

    /** The travelling front radius this many ticks into the owned damage window. */
    public static double frontRadius(int ticksIntoFront) {
        if (ticksIntoFront <= 0) return 0.0D;
        double progress = Math.min(1.0D, (double) ticksIntoFront / (double) BEAT_FRONT_TICKS);
        return FRONT_MAX_RADIUS * progress;
    }

    /** Whether the last (visibly heavier) footfall of a given beat count. */
    public static boolean isFinalBeat(int beat, int beats) {
        int count = Math.max(1, Math.min(BEATS_MAX, beats));
        return Math.max(0, Math.min(count - 1, beat)) == count - 1;
    }

    /** The damage this footfall publishes. The final beat is heavier but bounded. */
    public static double damageFor(int beat, int beats) {
        return isFinalBeat(beat, beats) ? FINAL_BEAT_DAMAGE : BEAT_DAMAGE;
    }

    /**
     * One owned swept-annulus footfall hit test.
     *
     * <p>Exactly the guarantees the old seam had, now per front: a grounded player
     * is hit once while the front sweeps past them, a player already behind the
     * front is safe, a fast tick cannot tunnel, a player who jumped (or stands on
     * a raised block) is safe, and the front never reaches past its maximum.</p>
     */
    public static boolean frontHits(double distance, double feetAboveFloor, boolean grounded,
                                    double previousRadius, double radius, boolean alreadyHit) {
        return !alreadyHit && grounded
                && allFinite(distance, feetAboveFloor, previousRadius, radius)
                && feetAboveFloor >= -0.25D && feetAboveFloor <= 0.55D
                && previousRadius >= 0.0D && radius >= previousRadius
                && radius <= FRONT_MAX_RADIUS + 1.0E-9D
                && distance >= Math.max(0.0D, previousRadius - FRONT_EDGE_SLACK)
                && distance <= Math.min(FRONT_MAX_RADIUS, radius + FRONT_EDGE_SLACK);
    }

    /**
     * One bounded marching step toward a target.
     *
     * @return {@code {newX, newZ, dx, dz}} where dx/dz is the full desired
     *         translation direction (for facing), or null on bad input
     */
    public static double[] march(double fromX, double fromZ, double toX, double toZ, double maxStep) {
        if (!allFinite(fromX, fromZ, toX, toZ, maxStep) || maxStep < 0.0D) return null;
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distance = Math.hypot(dx, dz);
        if (distance <= maxStep || distance == 0.0D) return new double[]{toX, toZ, dx, dz};
        double scale = maxStep / distance;
        return new double[]{fromX + dx * scale, fromZ + dz * scale, dx, dz};
    }

    /**
     * The body yaw that points the Ram along its translation vector.
     *
     * <p>Matches the vanilla Ravager convention already used by the encounter's
     * {@code face(...)} helper: yaw 0 faces +Z, yaw -90 faces +X, yaw 90 faces
     * -X, yaw 180 faces -Z. Using one helper for both facing a target and facing a
     * tangent is what stops the near-pillar sidestep from sliding sideways.</p>
     */
    public static float gaitYaw(double dx, double dz) {
        if (!allFinite(dx, dz) || (dx == 0.0D && dz == 0.0D)) return 0.0F;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        // atan2(-0.0, -1.0) is -180, not 180; canonicalise the straight-back
        // case so the documented "yaw 180 faces -Z" convention holds exactly and
        // the caller never has to reason about negative zero.
        if (yaw == -180.0D) yaw = 180.0D;
        return (float) yaw;
    }
}
