package me.copimine.endevent.domain;

/**
 * Wave 7 Warden emergence.
 *
 * <p>The Warden is the first named trial and it must <em>arrive</em>, not pop into
 * existence: the player asked for a Warden that spawns beautifully with its own
 * emergence animation. Vanilla drives that with a digging sequence, so this
 * policy owns the beats of that sequence as pure data: how long the body stays
 * sunk, when the sculk dust starts, when the roar lands, and how the ground
 * shakes afterwards.</p>
 */
public final class Wave7WardenEmergePolicy {
    /** Total emergence: sunk, rising, then the roar. */
    public static final int EMERGE_TICKS = 56;
    /** The body is buried and only sculk dust moves. */
    public static final int SINK_TICKS = 14;
    /** Vertical travel of the body out of the floor. */
    public static final int RISE_TICKS = 34;
    /** The roar lands when the body clears the ground. */
    public static final int ROAR_TICKS = 48;
    /** Blocks the body starts below its final position. */
    public static final double SINK_DEPTH_BLOCKS = 2.6D;

    private Wave7WardenEmergePolicy() {
    }

    /** True while the actor must stay untouchable and driven by the sequence. */
    public static boolean emerging(int elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < EMERGE_TICKS;
    }

    /** Vertical offset of the body at {@code elapsedTicks} of the sequence. */
    public static double verticalOffset(int elapsedTicks) {
        if (elapsedTicks < 0) return 0.0D;
        if (elapsedTicks >= EMERGE_TICKS) return 0.0D;
        if (elapsedTicks < SINK_TICKS) return -SINK_DEPTH_BLOCKS;
        double progress = (elapsedTicks - SINK_TICKS) / (double) RISE_TICKS;
        double eased = easeOut(progress);
        return -SINK_DEPTH_BLOCKS * (1.0D - eased);
    }

    /**
     * The emergence eases out, so the body decelerates as it breaks the floor
     * instead of sliding up at a constant rate.
     */
    public static double easeOut(double progress) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        double inverse = 1.0D - clamped;
        return 1.0D - inverse * inverse * inverse;
    }

    /** True exactly on the tick the roar must play (once per emergence). */
    public static boolean roarsAt(int elapsedTicks) {
        return elapsedTicks == ROAR_TICKS;
    }

    /** How loud the ground rumble is, fading out after the roar. */
    public static float rumbleVolume(int elapsedTicks) {
        if (elapsedTicks < 0 || elapsedTicks >= EMERGE_TICKS) return 0.0F;
        double tail = (elapsedTicks - ROAR_TICKS) / (double) (EMERGE_TICKS - ROAR_TICKS);
        double volume = elapsedTicks < ROAR_TICKS
                ? elapsedTicks / (double) ROAR_TICKS : 1.0D - Math.max(0.0D, tail);
        return (float) Math.max(0.0D, Math.min(1.0D, volume));
    }
}
