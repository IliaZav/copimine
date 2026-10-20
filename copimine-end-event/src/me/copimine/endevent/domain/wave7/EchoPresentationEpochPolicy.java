package me.copimine.endevent.domain.wave7;

/** Monotonic, overflow-safe allocator for disposable Echo presentation actors. */
public final class EchoPresentationEpochPolicy {
    private EchoPresentationEpochPolicy() {
    }

    public static long next(long currentEpoch, long wallClockMillis) {
        if (currentEpoch < 0 || wallClockMillis <= 0) {
            throw new IllegalArgumentException("Echo presentation epoch inputs must be non-negative and clock must be positive");
        }
        if (currentEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("Echo presentation epoch is exhausted");
        }
        return Math.max(currentEpoch + 1L, wallClockMillis);
    }
}
