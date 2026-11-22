package me.copimine.endevent.domain;

/**
 * Deterministic, bounded description of the LAST_SEAL arena ritual.
 *
 * <p>The final phase used to be announced by two small rings around the boss
 * itself, so the largest fight in the encounter looked like the smallest. This
 * policy describes a genuine arena-scale transformation: a broad ground seal
 * that visibly converges on the Core, a ring of Kagune spikes rising out of the
 * floor, vertical pillars that turn the floor and the overhead sigil into one
 * cage, and a slow counter-rotating sigil above the boss. The monolith owns the
 * Bukkit particles; this class owns only the geometry and the clock, so the
 * whole ritual is exercised by plain Java tests.</p>
 *
 * <p>Everything here is bounded on purpose: fixed point counts, a finite
 * radius, a finite height and a fixed cadence. There is no per-tick growth and
 * no value that scales with the number of players, so a 2-player and a
 * 20-player arena draw the same shape at the same cost per viewer.</p>
 */
public final class LastSealArenaRitualPolicy {
    /** Ground seal ring resolution. */
    public static final int SEAL_RING_POINTS = 32;
    /** Overhead sigil resolution. */
    public static final int SIGIL_RING_POINTS = 20;
    /** Kagune spikes on the arena floor. */
    public static final int SPIKE_COUNT = 8;
    /** Vertical pillars that turn the seal and the sigil into one cage. */
    public static final int PILLAR_COUNT = 4;
    /**
     * Points a single straight line draws through the shared segment helper.
     * The helper subdivides into four steps and skips both endpoints, so a
     * line costs exactly three points; the budget below uses that real figure
     * instead of a nominal segment count.
     */
    public static final int LINE_POINTS = 3;
    /** Where the spikes stand, as a radius from the seal centre. */
    public static final double SPIKE_BASE_RADIUS = 8.5D;
    /** The seal converges between these two radii, never further out. */
    public static final double SEAL_MAX_RADIUS = 9.0D;
    public static final double SEAL_MIN_RADIUS = 4.5D;
    public static final double SIGIL_RADIUS = 6.5D;
    /** How high above the hover point the sigil floats. */
    public static final double SIGIL_HEIGHT = 7.0D;
    /** The tallest a spike may ever rise. */
    public static final double SPIKE_MAX_HEIGHT = 5.0D;
    /** The heavy part of the ritual redraws every fourth tick, not every tick. */
    public static final int RITUAL_PERIOD_TICKS = 4;
    /**
     * Ceiling on the points one viewer may be asked to draw in a single
     * during-ritual tick. Matches the authored silhouette ceiling, so the arena
     * transformation can never outdraw a single authored spell shape.
     */
    public static final int MAX_POINTS_PER_DUE_TICK = 96;

    private static final double TAU = Math.PI * 2.0D;

    private LastSealArenaRitualPolicy() {
    }

    /** True on the ticks the arena ritual is allowed to redraw. */
    public static boolean ritualDue(int tick) {
        return Math.floorMod(tick, RITUAL_PERIOD_TICKS) == 0;
    }

    /** The seal breathes between {@link #SEAL_MIN_RADIUS} and {@link #SEAL_MAX_RADIUS}. */
    public static double sealRadius(int tick) {
        double wave = 0.5D - 0.5D * Math.cos(phase(tick, 96));
        return SEAL_MIN_RADIUS + (SEAL_MAX_RADIUS - SEAL_MIN_RADIUS) * wave;
    }

    /** One full turn of the ground seal every six seconds. */
    public static double sealPhase(int tick) {
        return phase(tick, 120);
    }

    /** The sigil drifts a little in radius so it never looks like a fixed decal. */
    public static double sigilRadius(int tick) {
        return SIGIL_RADIUS + Math.sin(phase(tick, 72)) * 0.6D;
    }

    /** The sigil counter-rotates against the floor, reading as a sealed lid. */
    public static double sigilPhase(int tick) {
        return phase(90 - tick, 90);
    }

    /** Evenly spaced spike anchors; stable for a given index. */
    public static double spikeAngle(int index) {
        return TAU * Math.floorMod(index, SPIKE_COUNT) / SPIKE_COUNT;
    }

    /** Evenly spaced pillar anchors; independent of the spike ring. */
    public static double pillarAngle(int index) {
        return TAU * Math.floorMod(index, PILLAR_COUNT) / PILLAR_COUNT
                + Math.PI / PILLAR_COUNT;
    }

    /** Tallest a spike may be this tick; always within {@code [0, SPIKE_MAX_HEIGHT]}. */
    public static double spikeHeight(int tick, int index) {
        double pulse = 0.5D - 0.5D * Math.cos(phase(tick, 60));
        double stagger = 0.75D + 0.25D * Math.sin(spikeAngle(index) * 3.0D + tick * 0.05D);
        return SPIKE_MAX_HEIGHT * pulse * stagger;
    }

    /** Ground seal points per ritual tick. */
    public static int sealPoints() {
        return SEAL_RING_POINTS;
    }

    /** Overhead sigil points per ritual tick. */
    public static int sigilPoints() {
        return SIGIL_RING_POINTS;
    }

    /** Spike points per ritual tick. */
    public static int spikePoints() {
        return SPIKE_COUNT * LINE_POINTS;
    }

    /** Pillar points per ritual tick. */
    public static int pillarPoints() {
        return PILLAR_COUNT * LINE_POINTS;
    }

    /** Points the arena ritual asks a single viewer to draw on a due tick. */
    public static int pointsPerDueTick() {
        return sealPoints() + sigilPoints() + spikePoints() + pillarPoints();
    }

    /**
     * Average points per viewer per server tick, amortising the ritual across
     * its own cadence. The boss-local halo it supplements drew 45 points every
     * two ticks, i.e. 22.5 per tick; staying at or under that figure is what
     * makes a far larger transformation affordable at 20 players.
     */
    public static double averagePointsPerTick() {
        return pointsPerDueTick() / (double) RITUAL_PERIOD_TICKS;
    }

    private static double phase(int tick, int period) {
        int safe = Math.max(1, period);
        return TAU * Math.floorMod(tick, safe) / safe;
    }
}
