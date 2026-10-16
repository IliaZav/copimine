package me.copimine.endevent.domain;

/**
 * Narrow combat-physics rule for the closed Wave 7 rooms.  The damage event
 * remains authoritative; this only recognizes the short follow-up window in
 * which Paper emits the knockback event for that already-accepted hit.
 */
public final class RealitySplitPlayerKnockbackPolicy {
    public static final int WAVE = 7;
    public static final long SUPPRESSION_WINDOW_MILLIS = 250L;

    private RealitySplitPlayerKnockbackPolicy() {
    }

    public static boolean shouldSuppress(int wave, boolean owned, boolean sameRoom,
                                         long nowMillis, long armedUntilMillis) {
        return wave == WAVE && owned && sameRoom
                && armedUntilMillis > nowMillis;
    }
}
