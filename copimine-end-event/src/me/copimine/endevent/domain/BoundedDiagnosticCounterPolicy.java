package me.copimine.endevent.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * N23-P1-14 / specification 13 (proof and production noise): key-based bounded
 * diagnostic counters.
 *
 * <p>Production logs carried one INFO/WARN line <em>per event occurrence</em> —
 * a suffocation rejection for every touched mob, a rejection line for every
 * refused dragon-breath cloud hit. That is useful information at the wrong
 * volume: at boss scale it buries the lines that actually matter. This policy
 * keeps the information and drops the flood. Each distinct key emits at most
 * <strong>one</strong> line per window, and the line it eventually emits carries
 * the exact number of occurrences that were suppressed during the window before
 * it, so the total is batched, never lost.</p>
 *
 * <p>The tracker is bounded by construction. At most {@code maxKeys} distinct
 * keys are remembered at once; once the cap is reached a brand-new key is folded
 * into a single synthetic {@link #OVERFLOW_KEY} bucket instead of growing the map
 * without limit. A hostile, misspelled or per-entity key can therefore never turn
 * the diagnostics themselves into a memory leak, and the suppressed total is
 * still reported.</p>
 *
 * <p>Pure by design: the current time is always passed in by the caller, so
 * every window boundary is deterministic under test. No Bukkit type is imported,
 * and the class is exercised by a plain Java test in the gate bundle.</p>
 */
public final class BoundedDiagnosticCounterPolicy {

    /** Synthetic bucket that absorbs keys once the bounded key set is full. */
    public static final String OVERFLOW_KEY = "DIAGNOSTIC_KEY_OVERFLOW";

    /** Default window: one summary line per key per 30 seconds. */
    public static final long DEFAULT_WINDOW_MILLIS = 30_000L;
    /** Default key budget: enough for every real subsystem, hostile-key safe. */
    public static final int DEFAULT_MAX_KEYS = 64;

    /**
     * The caller's answer for one occurrence of a key.
     *
     * @param emit            whether the caller should log the line now
     * @param suppressedBefore occurrences suppressed since the previous emitted
     *                         line for this key; non-zero only on an emit
     * @param overflow        true when this occurrence belongs to the synthetic
     *                        overflow bucket (the key budget was exhausted)
     */
    public record Decision(boolean emit, long suppressedBefore, boolean overflow) {
        /**
         * A ready-to-append log suffix. Empty when nothing was suppressed, so an
         * ordinary first line stays byte-identical to the pre-throttle output.
         */
        public String suffix() {
            if (suppressedBefore <= 0L) {
                return "";
            }
            return overflow
                    ? " suppressed_overflow=" + suppressedBefore
                    : " suppressed=" + suppressedBefore;
        }
    }

    private static final class Counter {
        private long windowStart;
        private long suppressedSinceEmit;
    }

    private final long windowMillis;
    private final int maxKeys;
    private final Map<String, Counter> counters;

    public BoundedDiagnosticCounterPolicy() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_MAX_KEYS);
    }

    public BoundedDiagnosticCounterPolicy(long windowMillis, int maxKeys) {
        this.windowMillis = Math.max(1L, windowMillis);
        this.maxKeys = Math.max(1, maxKeys);
        this.counters = new HashMap<>();
    }

    /**
     * Record one occurrence of {@code key} at {@code nowMillis}.
     *
     * <p>The first occurrence of a key emits immediately (an operator should not
     * wait a full window to learn a new failure exists). Occurrences inside the
     * window are counted and suppressed. When the window elapses the next
     * occurrence emits again, carrying the count of everything suppressed in the
     * window that just closed.</p>
     */
    public synchronized Decision decide(String key, long nowMillis) {
        String resolved = key == null || key.isEmpty() ? "null" : key;
        boolean overflow = false;
        Counter counter = counters.get(resolved);
        if (counter == null && counters.size() >= maxKeys) {
            resolved = OVERFLOW_KEY;
            overflow = true;
            counter = counters.get(resolved);
        }
        if (counter == null) {
            counter = new Counter();
            counter.windowStart = nowMillis;
            counters.put(resolved, counter);
            return new Decision(true, 0L, overflow);
        }
        long elapsed = nowMillis - counter.windowStart;
        if (elapsed >= windowMillis || elapsed < 0L) {
            // Window closed (or the clock stepped backwards): emit again and
            // report exactly how many occurrences the closed window suppressed.
            long suppressed = counter.suppressedSinceEmit;
            counter.windowStart = nowMillis;
            counter.suppressedSinceEmit = 0L;
            return new Decision(true, suppressed, overflow);
        }
        counter.suppressedSinceEmit++;
        return new Decision(false, 0L, overflow);
    }

    /** Number of distinct keys currently remembered (never exceeds the budget). */
    public synchronized int trackedKeys() {
        return counters.size();
    }

    /** Configured per-key window in milliseconds. */
    public long windowMillis() {
        return windowMillis;
    }

    /** Configured maximum number of distinct remembered keys. */
    public int maxKeys() {
        return maxKeys;
    }
}
