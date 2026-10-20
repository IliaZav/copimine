package me.copimine.endevent.domain;

/** Entry condition and retry pacing for Wave 5 combat before its first fog cycle. */
public final class Wave5PreFogRefillPolicy {
    public static final long REFILL_DELAY_MILLIS = 5_000L;

    private Wave5PreFogRefillPolicy() { }

    public static boolean shouldRefill(int activeWave, int completedFogCycles,
                                       boolean combatPhase, int liveWaveMobs,
                                       long nowMillis, long nextRefillAtMillis) {
        return activeWave == 5 && completedFogCycles == 0 && combatPhase
                && liveWaveMobs == 0 && nowMillis >= Math.max(0L, nextRefillAtMillis);
    }
}
