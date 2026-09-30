package me.copimine.endevent.domain;

/** Pure, immutable timing policy for a portal occupied by players. */
public final class PortalCapturePolicy {
    public static final long CAPTURE_MILLIS = 5_000L;
    public static final long GRACE_MILLIS = 450L;
    /** Progress milliseconds lost per millisecond outside the capture ring. */
    public static final double DEFAULT_DECAY_RATE = 0.50D;

    private PortalCapturePolicy() {
    }

    public static PortalState initial() {
        return new PortalState(false, 0L, -1L, -1L);
    }

    public static PortalState tick(PortalState state, boolean occupied, long nowMillis) {
        return tick(state, occupied, nowMillis, DEFAULT_DECAY_RATE);
    }

    /**
     * Advance one portal without ever resetting an occupied player's progress
     * in a single frame. A short grace period prevents boundary jitter from
     * flickering the counter; after it, progress drains at the configured
     * bounded rate until it reaches zero.
     */
    public static PortalState tick(PortalState state, boolean occupied, long nowMillis,
                                   double decayRate) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (nowMillis < 0L) throw new IllegalArgumentException("timestamp must be non-negative");
        validateDecayRate(decayRate);
        if (nowMillis < state.lastUpdateMillis()) return state;
        if (state.completed()) return state;
        if (state.lastUpdateMillis() == nowMillis) {
            if (occupied && state.lastOccupiedMillis() != nowMillis) {
                return new PortalState(false, state.progressMillis(), nowMillis, nowMillis);
            }
            return state;
        }

        long elapsed = nowMillis - state.lastUpdateMillis();
        long progress = state.progressMillis();
        long lastOccupied = state.lastOccupiedMillis();
        if (occupied) {
            if (lastOccupied >= 0L && lastOccupied == state.lastUpdateMillis()) {
                progress = saturatingAdd(progress, elapsed);
            } else if (lastOccupied >= 0L && nowMillis - lastOccupied > GRACE_MILLIS) {
                progress = decay(progress, nowMillis - lastOccupied - GRACE_MILLIS, decayRate);
            }
            lastOccupied = nowMillis;
        } else if (lastOccupied >= 0L && nowMillis - lastOccupied > GRACE_MILLIS) {
            progress = decay(progress, nowMillis - lastOccupied - GRACE_MILLIS, decayRate);
        }
        boolean completed = progress >= CAPTURE_MILLIS;
        return new PortalState(completed, Math.min(progress, CAPTURE_MILLIS), lastOccupied, nowMillis);
    }

    private static long decay(long progress, long excessGap, double decayRate) {
        double rawLoss = excessGap * decayRate;
        long loss = rawLoss >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(rawLoss);
        return Math.max(0L, progress - loss);
    }

    private static void validateDecayRate(double decayRate) {
        if (!Double.isFinite(decayRate) || decayRate <= 0.0D || decayRate > 1.0D) {
            throw new IllegalArgumentException("decay rate must be finite and between 0 and 1");
        }
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public record PortalState(boolean completed, long progressMillis, long lastOccupiedMillis, long lastUpdateMillis) {
        public PortalState {
            if (progressMillis < 0L || lastOccupiedMillis < -1L || lastUpdateMillis < -1L) {
                throw new IllegalArgumentException("portal state values must be non-negative timestamps");
            }
        }

        public long lastOccupiedTimeMillis() { return lastOccupiedMillis; }
        public long lastUpdateTimeMillis() { return lastUpdateMillis; }
    }
}
