package me.copimine.endevent.domain;

/** Entry condition and retry pacing for Wave 5 combat before each fog cycle. */
public final class Wave5PreFogRefillPolicy {
    // The historical class name is kept for the serialized/test contract;
    // the rule now applies before every fog cycle, not only the first.
    public static final long REFILL_DELAY_MILLIS = 5_000L;

    /** Base backoff after a refused refill attempt; doubles per consecutive failure. */
    public static final long FAILURE_BACKOFF_BASE_MILLIS = 1_000L;
    /** Ceiling so a persistent failure still retries about twice a minute. */
    public static final long FAILURE_BACKOFF_MAX_MILLIS = 30_000L;

    private Wave5PreFogRefillPolicy() { }

    public static boolean shouldRefill(int activeWave,
                                       boolean combatPhase, int liveWaveMobs,
                                       long nowMillis, long nextRefillAtMillis) {
        return activeWave == 5 && combatPhase
                && liveWaveMobs == 0 && nowMillis >= Math.max(0L, nextRefillAtMillis);
    }

    /**
     * Delay before the next refill attempt after {@code consecutiveFailures}.
     *
     * <p>The caller used to leave the deadline in the past when a spawn was
     * refused, so the very next tick retried forever and burned CPU without
     * ever settling the debt. The backoff is finite and capped, and the owed
     * pack is still never dropped.</p>
     */
    public static long failureBackoffMillis(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return 0L;
        }
        int shift = Math.min(20, consecutiveFailures - 1);
        long delay = FAILURE_BACKOFF_BASE_MILLIS << shift;
        return Math.min(FAILURE_BACKOFF_MAX_MILLIS, Math.max(FAILURE_BACKOFF_BASE_MILLIS, delay));
    }
}
