package me.copimine.endevent.domain;

/** Capacity rules for Wave 5 safe-zone placement before fog activation. */
public final class BlackFogSafeZoneCapacityPolicy {
    private static final int MAX_ELIGIBLE_PLAYERS = 20;
    private static final int MAX_ZONE_SIDE = 3;

    private BlackFogSafeZoneCapacityPolicy() {
    }

    /**
     * Keeps the planned number of zones or expands it to provide at least one
     * cell per eligible player, based on the square area of each zone. Invalid
     * bounds return zero so callers can fail closed before activating the fog.
     */
    public static int zoneCountForCapacity(int plannedCount, int eligiblePlayers, int side) {
        if (plannedCount < 0 || plannedCount > MAX_ELIGIBLE_PLAYERS
                || eligiblePlayers < 1 || eligiblePlayers > MAX_ELIGIBLE_PLAYERS
                || side < 1 || side > MAX_ZONE_SIDE) {
            return 0;
        }

        int cellsPerZone = side * side;
        int requiredZones = (eligiblePlayers + cellsPerZone - 1) / cellsPerZone;
        return Math.max(plannedCount, requiredZones);
    }

    /**
     * Allows fog activation only when the caller has counted enough distinct,
     * usable safe cells for all eligible players.
     */
    public static boolean hasCapacity(int eligiblePlayers, int distinctUsableCells) {
        return eligiblePlayers >= 1 && eligiblePlayers <= MAX_ELIGIBLE_PLAYERS
                && distinctUsableCells >= eligiblePlayers;
    }
}
