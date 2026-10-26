package me.copimine.endevent.domain;

/**
 * The corner volley the boss unlocks from RIFT onwards: it repositions to the
 * arena corner farthest from the players and fires exactly five salvos, then
 * the ability is unavailable until the cooldown elapses.
 */
public final class BossEdgeVolleyPolicy {
    public static final int SALVO_COUNT = 5;
    public static final long SALVO_INTERVAL_TICKS = 16L;
    /** Spec: 60-90 s between volleys. */
    public static final long COOLDOWN_MILLIS = 75_000L;
    public static final double CORNER_INSET_BLOCKS = 2.0D;

    private BossEdgeVolleyPolicy() {
    }

    public static boolean allowsPhase(BossPhase phase) {
        return phase == BossPhase.RIFT
                || phase == BossPhase.OVERLOAD
                || phase == BossPhase.RAGE;
    }

    public static boolean onCooldown(long nowMillis, long nextAllowedMillis) {
        return nowMillis < nextAllowedMillis;
    }
}
