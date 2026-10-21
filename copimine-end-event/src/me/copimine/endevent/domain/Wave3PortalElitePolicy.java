package me.copimine.endevent.domain;

/** Pure schedule policy for the Wave 3 elite assigned to portal two. */
public final class Wave3PortalElitePolicy {
    public static final int SECOND_PORTAL_INDEX = 1;

    public enum PortalProgress {
        NOT_REACHED,
        OPEN,
        CAPTURED
    }

    public enum Action {
        WAIT,
        EMERGE_FROM_SECOND_PORTAL,
        FALLBACK_AT_SECOND_PORTAL_ANCHOR,
        ALREADY_SPAWNED
    }

    /** A spawn action always names portal two; non-spawn actions use index -1. */
    public record Decision(Action action, int portalIndex) {
        public Decision {
            if (action == null) throw new IllegalArgumentException("action is required");
            boolean spawn = action == Action.EMERGE_FROM_SECOND_PORTAL
                    || action == Action.FALLBACK_AT_SECOND_PORTAL_ANCHOR;
            if (spawn && portalIndex != SECOND_PORTAL_INDEX) {
                throw new IllegalArgumentException("Wave 3 elite spawn must use portal two");
            }
            if (!spawn && portalIndex != -1) {
                throw new IllegalArgumentException("non-spawn decision cannot name a portal");
            }
        }
    }

    private static final Decision WAIT = new Decision(Action.WAIT, -1);
    private static final Decision ALREADY_SPAWNED = new Decision(Action.ALREADY_SPAWNED, -1);
    private static final Decision EMERGE = new Decision(Action.EMERGE_FROM_SECOND_PORTAL,
            SECOND_PORTAL_INDEX);
    private static final Decision FALLBACK = new Decision(Action.FALLBACK_AT_SECOND_PORTAL_ANCHOR,
            SECOND_PORTAL_INDEX);

    private Wave3PortalElitePolicy() { }

    /**
     * Choose the single scheduled elite attempt. If portal two was captured
     * before the due point, use its saved anchor once due instead of reopening
     * the portal or relocating the elite to another portal.
     */
    public static Decision decide(PortalProgress portalProgress, boolean eliteDue,
                                  boolean waveActive, boolean alreadySpawned) {
        if (alreadySpawned) return ALREADY_SPAWNED;
        if (!waveActive || !eliteDue || portalProgress == null
                || portalProgress == PortalProgress.NOT_REACHED) return WAIT;
        return portalProgress == PortalProgress.OPEN ? EMERGE : FALLBACK;
    }
}
