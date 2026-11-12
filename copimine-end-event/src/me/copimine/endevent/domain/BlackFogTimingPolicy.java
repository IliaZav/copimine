package me.copimine.endevent.domain;

/** Roster-frozen Wave 5 escalation. Indices are zero based; deadlines are wall time. */
public final class BlackFogTimingPolicy {
    public static final int MIN_CYCLES = 5;
    /** Protocol/recovery upper bound, not the target of every party. */
    public static final int CYCLE_COUNT = 10;
    /** Additional reach time after the three-second visible warning. */
    public static final int SAFE_ZONE_SECONDS = 7;
    public static final int FOG_SECONDS = 18;
    private BlackFogTimingPolicy() { }
    public static int cycleCount(int rosterPlayers) {
        int players = Math.max(2, Math.min(20, rosterPlayers));
        return MIN_CYCLES + (players - 2 + 3) / 4;
    }
    public static double progress(int cycle, int targetCycles) {
        int target = Math.max(MIN_CYCLES, Math.min(CYCLE_COUNT, targetCycles));
        return Math.max(0.0D, Math.min(1.0D, cycle / (double) (target - 1)));
    }
    public static int combatSeconds(int cycle) {
        return 25 + Math.min(10, Math.max(0, cycle) * 2);
    }
    public static int safeZoneSeconds(int cycle, int targetCycles) {
        return SAFE_ZONE_SECONDS - (int) Math.floor(4 * progress(cycle, targetCycles));
    }
    public static int fogSeconds(int cycle, int targetCycles) {
        return FOG_SECONDS + (int) Math.round(8 * progress(cycle, targetCycles));
    }
    public static int zoneCount(int rosterPlayers, int cycle) {
        int players = Math.max(1, Math.min(20, rosterPlayers));
        // The final zone count and footprint must be solved together. The old
        // ceil(players/8) demanded 3x3 shelters for 5/10/20-player parties and
        // consequently prevented their footprint from ever shrinking.
        // Large parties finish at 2x2; tiny parties can reach 1x1. Capacity is
        // preserved without adding shelters halfway through the escalation.
        int finalSide = players <= 3 ? 1 : 2;
        int cells = finalSide * finalSide;
        int last = (players + cells - 1) / cells;
        int initial = Math.max(last, (players + 2) / 3);
        return initial - (int) Math.floor((initial - last) * progress(cycle, cycleCount(players)));
    }
    /** Three-and-a-half seconds bright, then a smooth finite fade; mirror is wire-tested. */
    public static float guideStrength(long ageMillis) {
        if (ageMillis < 0L || ageMillis >= 5_000L) return 0.0F;
        if (ageMillis <= 3_500L) return 1.0F;
        float t = (ageMillis - 3_500L) / 1_500.0F;
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }
    public static int safeZoneSeconds() { return SAFE_ZONE_SECONDS; }
    public static int fogSeconds() { return FOG_SECONDS; }
    public static boolean complete(int completedCycles) { return completedCycles >= CYCLE_COUNT; }
    public static boolean hasRemainingCycles(int completedCycles) {
        return hasRemainingCycles(completedCycles, CYCLE_COUNT);
    }
    public static boolean hasRemainingCycles(int completedCycles, int targetCycles) {
        return targetCycles >= MIN_CYCLES && targetCycles <= CYCLE_COUNT
                && completedCycles >= 0 && completedCycles < targetCycles;
    }
}
