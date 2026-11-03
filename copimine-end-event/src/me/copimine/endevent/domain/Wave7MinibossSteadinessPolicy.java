package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Steady, readable movement for the W7 minibosses.
 *
 * <p>SUPERSEDED (2026-11-03): the old routine re-picked a direction from a
 * fixed list every tick — left, right, away, toward, away-left, away-right —
 * and applied a fresh velocity each time. Nothing remembered the previous
 * choice, so the actor alternated between opposite vectors on consecutive
 * ticks and vibrated in place. The user's words for it were "чтобы они не
 * метались", and they were describing exactly this: jitter is not evasiveness,
 * it just looks broken and makes the fight unreadable.</p>
 *
 * <p>This policy makes the choice sticky: a committed heading is held for a
 * number of ticks, the acceptable turn per tick is bounded, and a new heading
 * is only picked when the hold expires or the current one is blocked. Pure
 * arithmetic only, so the whole thing is testable.</p>
 */
public final class Wave7MinibossSteadinessPolicy {

    /** Ticks a chosen heading is held before it may be reconsidered. */
    public static final int COMMIT_TICKS = 14;
    /** Hard cap on the turn rate, degrees per tick. */
    public static final double MAX_TURN_DEGREES_PER_TICK = 9.0D;
    /** Below this cosine change a new heading is treated as a reversal. */
    private static final double REVERSAL_COS = -0.6D;
    /** Minimum fair standoff: the actor does not crowd the target. */
    public static final double STANDOFF_BLOCKS = 3.2D;
    /** Distance at which an actor stops closing entirely. */
    public static final double ENGAGE_BLOCKS = 11.0D;

    private Wave7MinibossSteadinessPolicy() {
    }

    /** What the actor should do this tick. */
    public enum Intent {
        /** Close the distance to the target. */
        APPROACH,
        /** Hold a fair standoff and strafe. */
        STRAFE,
        /** Break contact toward cover. */
        RETREAT
    }

    public static Intent intentFor(double distanceBlocks, double healthFraction, boolean hasCover,
                                   boolean vanished) {
        if (vanished || hasCover && healthFraction <= 0.55D) {
            return Intent.RETREAT;
        }
        if (!Double.isFinite(distanceBlocks)) return Intent.STRAFE;
        if (distanceBlocks > ENGAGE_BLOCKS) return Intent.APPROACH;
        if (distanceBlocks < STANDOFF_BLOCKS) return Intent.RETREAT;
        return healthFraction <= 0.80D ? Intent.STRAFE : Intent.APPROACH;
    }

    /**
     * Whether {@code now} allows a new heading.
     *
     * @param decidedTick tick the current heading was committed
     * @param lastTick    last tick the actor actually moved; kept for callers
     *                    that want to detect a stalled actor, not used for the
     *                    commit window itself
     */
    public static boolean mayRethink(long now, long decidedTick, long lastTick, boolean blocked) {
        if (blocked) return true;
        if (now < decidedTick) return true;
        long held = now - decidedTick;
        return held >= COMMIT_TICKS;
    }

    /**
     * Clamps a desired direction against the committed one so the actor turns
     * instead of flipping.
     *
     * @param committedX committed direction, normalised (may be 0,0,0 when unset)
     * @param desiredX   the direction this tick would like
     * @param desiredZ   non-normalised input is accepted; only direction matters
     * @return the direction to actually move, normalised, as {x, z}
     */
    public static double[] steadyHeading(double committedX, double committedZ,
                                         double desiredX, double desiredZ) {
        double dl = Math.hypot(desiredX, desiredZ);
        if (dl < 1.0E-6D) return new double[]{0.0D, 0.0D};
        double dx = desiredX / dl;
        double dz = desiredZ / dl;
        double cl = Math.hypot(committedX, committedZ);
        if (cl < 1.0E-6D) return new double[]{dx, dz};
        double cx = committedX / cl;
        double cz = committedZ / cl;
        double dot = Math.max(-1.0D, Math.min(1.0D, cx * dx + cz * dz));
        double angle = Math.toDegrees(Math.acos(dot));
        double maxRad = Math.toRadians(MAX_TURN_DEGREES_PER_TICK);
        if (angle <= MAX_TURN_DEGREES_PER_TICK) return new double[]{dx, dz};
        // Rotate from the committed heading toward the desired one by exactly
        // the allowed step: the actor arcs into a new direction instead of
        // snapping, which is what stops the left/right vibration.
        double sign = (cx * desiredZ - cz * desiredX) >= 0.0D ? 1.0D : -1.0D;
        double base = Math.atan2(cz, cx);
        double step = base + sign * Math.toRadians(Math.min(MAX_TURN_DEGREES_PER_TICK, angle));
        return new double[]{Math.cos(step), Math.sin(step)};
    }

    /** True when a proposed heading reverses the committed one sharply. */
    public static boolean isReversal(double committedX, double committedZ,
                                     double proposedX, double proposedZ) {
        double cl = Math.hypot(committedX, committedZ);
        double pl = Math.hypot(proposedX, proposedZ);
        if (cl < 1.0E-6D || pl < 1.0E-6D) return false;
        double dot = (committedX * proposedX + committedZ * proposedZ) / (cl * pl);
        return dot <= REVERSAL_COS;
    }

    public static String describe(Intent intent) {
        return switch (intent) {
            case APPROACH -> "approaching";
            case STRAFE -> "holding a standoff";
            case RETREAT -> "breaking contact";
        };
    }
}
