package me.copimine.endevent.domain;

/**
 * The single 50% repel: a short controlled shove plus a blind and slow window.
 *
 * <p>The shove is bounded and only applied where the player has legal floor to
 * land on, so the arena-wide effect can never launch somebody into the void or
 * through the arena walls.</p>
 */
public final class BossRepelPolicy {
    /** Impulse of the shove; deliberately short and readable. */
    public static final double IMPULSE = 1.1D;
    public static final double LIFT = 0.45D;
    public static final int DARKNESS_TICKS = 60;
    public static final int SLOWNESS_TICKS = 100;
    public static final int SLOWNESS_AMPLIFIER = 1;
    /** Farthest landing spot the safety check accepts. */
    public static final double FULL_DISTANCE = 2.0D;
    /** Shortened shove tried when the full one ends over a void or wall. */
    public static final double SHORT_DISTANCE = 1.0D;

    private BossRepelPolicy() {
    }

    /** Landing distances to test, farthest first: the first legal one wins. */
    public static double[] travelCandidates() {
        return new double[]{FULL_DISTANCE, SHORT_DISTANCE};
    }
}