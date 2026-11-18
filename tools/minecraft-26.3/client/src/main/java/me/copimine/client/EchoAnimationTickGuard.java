package me.copimine.client;

/**
 * Per-tick gate for the echo's vanilla animation clocks.
 *
 * <p>An echo view is driven from two places: the client tick, which advances
 * once per game tick, and the render pass, which can run several times per
 * tick. Vanilla advances gait and the hurt flash once per tick, so the copy
 * must do the same at 60 FPS and at 144 FPS. This guard collapses every call
 * for one tick into a single advance.</p>
 *
 * <p>It also owns the first-frame baseline for the swing and hurt serials. The
 * server numbers those receipts from zero, and zero is the initial state, not
 * an event: adopting the first frame's serials keeps a freshly bound copy from
 * swinging and flashing hurt the instant it appears.</p>
 *
 * <p>The class is deliberately free of Minecraft types, so this timing is
 * exercised as arithmetic by the test suite instead of being inferred from
 * source text.</p>
 */
public final class EchoAnimationTickGuard {
    private int lastTick = Integer.MIN_VALUE;
    private boolean primed;
    private long swingSerial;
    private long hurtSerial;

    /** True on the first call for each distinct game tick, false on repeats. */
    public boolean advance(int tick) {
        if (tick == lastTick) return false;
        lastTick = tick;
        return true;
    }

    /**
     * Bind the serial baseline to the first frame. Idempotent: later frames
     * cannot move the baseline, so a real receipt is still delivered.
     */
    public void prime(long swingSerial, long hurtSerial) {
        if (primed) return;
        primed = true;
        this.swingSerial = swingSerial;
        this.hurtSerial = hurtSerial;
    }

    /**
     * True once for each newly observed swing serial. Serials only grow, so an
     * equal or stale value is not a new receipt.
     */
    public boolean swingDue(long serial) {
        if (!primed || serial <= swingSerial) return false;
        swingSerial = serial;
        return true;
    }

    /** True once for each newly observed hurt serial. */
    public boolean hurtDue(long serial) {
        if (!primed || serial <= hurtSerial) return false;
        hurtSerial = serial;
        return true;
    }

    /** The most recently observed swing serial, for diagnostics. */
    public long swingSerial() {
        return swingSerial;
    }

    /** The most recently observed hurt serial, for diagnostics. */
    public long hurtSerial() {
        return hurtSerial;
    }
}