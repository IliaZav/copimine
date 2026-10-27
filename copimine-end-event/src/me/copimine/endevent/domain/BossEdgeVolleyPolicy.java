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
    /** A boss that is still this far from the chosen corner has not repositioned. */
    public static final double REPOSITION_TOLERANCE_BLOCKS = 8.0D;

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

    /** What a volley attempt should do before any salvo is scheduled. */
    public enum Decision {
        /** Phase allows it, the cooldown expired and the boss reached a safe corner. */
        START,
        /** Wrong phase or still cooling down; nothing is attempted. */
        GATED,
        /** No safe corner exists: abort without consuming the cooldown. */
        NO_CORNER,
        /** The corner exists but the boss never got there: abort, keep the cooldown. */
        REPOSITION_FAILED
    }

    /** Pure gate: a volley may only start from an allowed phase, off cooldown, at a corner. */
    public static Decision decide(boolean phaseAllows, boolean cornerAvailable, boolean coolingDown) {
        if (!phaseAllows || coolingDown) {
            return Decision.GATED;
        }
        return cornerAvailable ? Decision.START : Decision.NO_CORNER;
    }

    /** Whether the boss actually ended up at the corner it was sent to. */
    public static boolean relocated(double distanceToCorner) {
        return Double.isFinite(distanceToCorner) && distanceToCorner <= REPOSITION_TOLERANCE_BLOCKS;
    }
}