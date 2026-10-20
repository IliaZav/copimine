package me.copimine.endevent.domain;

/** Bounds additional combat pressure emitted by the active Wave 3 portal. */
public final class Wave3PortalSpawnPolicy {
    public static final int MAX_LIVE_PORTAL_MOBS = 3;
    public static final int MAX_SPAWNS_PER_TICK = 1;
    public static final long SPAWN_INTERVAL_MILLIS = 1_500L;
    public static final long RETRY_DELAY_MILLIS = 1_000L;

    private Wave3PortalSpawnPolicy() { }

    /** Return a safe number to spawn for the current tick, bounded by remaining live slots. */
    public static int spawnCountWithinBudget(int livePortalMobs, int requestedSpawnCount) {
        return spawnCountWithinBudget(livePortalMobs, livePortalMobs,
                PressureBudgetController.HARD_CAP, requestedSpawnCount);
    }

    /**
     * Bound portal-origin mobs by both their local roster and the shared live
     * Wave 3 pressure budget. The portal roster must be a subset of the wave's
     * live mob count; inconsistent accounting fails closed.
     */
    public static int spawnCountWithinBudget(int livePortalMobs, int liveWaveMobs,
                                             int activePressureBudget, int requestedSpawnCount) {
        if (livePortalMobs < 0 || liveWaveMobs < 0 || activePressureBudget < 0
                || requestedSpawnCount <= 0 || livePortalMobs > liveWaveMobs) return 0;
        int available = Math.max(0, MAX_LIVE_PORTAL_MOBS - livePortalMobs);
        int sharedPressureAvailable = Math.max(0,
                Math.min(PressureBudgetController.HARD_CAP, activePressureBudget) - liveWaveMobs);
        return Math.min(Math.min(Math.min(requestedSpawnCount, MAX_SPAWNS_PER_TICK), available),
                sharedPressureAvailable);
    }
}
