package me.copimine.endevent.domain;

/** Pure, immutable timing policy for a portal occupied by players. */
public final class PortalCapturePolicy {
    public static final long CAPTURE_MILLIS = 5_000L;
    public static final long GRACE_MILLIS = 450L;
    private static final long DECAY_MULTIPLIER = 2L;

    private PortalCapturePolicy() {
    }

    public static PortalState initial() {
        return new PortalState(false, 0L, -1L, -1L);
    }

    public static PortalState tick(PortalState state, boolean occupied, long nowMillis) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (nowMillis < 0L) throw new IllegalArgumentException("timestamp must be non-negative");
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
                progress = decay(progress, nowMillis - lastOccupied - GRACE_MILLIS);
            }
            lastOccupied = nowMillis;
        } else if (lastOccupied >= 0L && nowMillis - lastOccupied > GRACE_MILLIS) {
            progress = decay(progress, nowMillis - lastOccupied - GRACE_MILLIS);
        }
        boolean completed = progress >= CAPTURE_MILLIS;
        return new PortalState(completed, Math.min(progress, CAPTURE_MILLIS), lastOccupied, nowMillis);
    }

    private static long decay(long progress, long excessGap) {
        long loss = excessGap > Long.MAX_VALUE / DECAY_MULTIPLIER
                ? Long.MAX_VALUE : excessGap * DECAY_MULTIPLIER;
        return Math.max(0L, progress - loss);
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
