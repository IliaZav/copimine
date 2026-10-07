package me.copimine.endevent.domain;

/** Fog borrows a potion slot briefly and never owns effects applied by other sources. */
public final class BlackFogEffectLeasePolicy {
    public static final int LEASE_TICKS = 30;
    private BlackFogEffectLeasePolicy() { }

    public static boolean mayReplace(int existingAmplifier, int existingDuration, int fogAmplifier) {
        return existingDuration != -1 && existingAmplifier < fogAmplifier;
    }

    public static int remainingDuration(int originalDuration, long capturedAtTick, long nowTick) {
        if (originalDuration == -1) return -1;
        long elapsed = Math.max(0L, nowTick - capturedAtTick);
        return (int) Math.max(0L, (long) originalDuration - elapsed);
    }
}
