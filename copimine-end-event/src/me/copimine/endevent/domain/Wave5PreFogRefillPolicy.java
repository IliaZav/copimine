package me.copimine.endevent.domain;

/** Entry condition and retry pacing for Wave 5 combat before each fog cycle. */
public final class Wave5PreFogRefillPolicy {
    // The historical class name is kept for the serialized/test contract;
    // the rule now applies before every fog cycle, not only the first.
    public static final long REFILL_DELAY_MILLIS = 5_000L;

    private Wave5PreFogRefillPolicy() { }

    public static boolean shouldRefill(int activeWave,
                                       boolean combatPhase, int liveWaveMobs,
                                       long nowMillis, long nextRefillAtMillis) {
        return activeWave == 5 && combatPhase
                && liveWaveMobs == 0 && nowMillis >= Math.max(0L, nextRefillAtMillis);
    }
}
