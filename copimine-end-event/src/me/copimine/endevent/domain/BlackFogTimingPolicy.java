package me.copimine.endevent.domain;

/**
 * Deterministic timing for the three Wave 4 black-fog cycles.
 *
 * <p>The Bukkit controller owns the wall clock.  This class only owns the
 * contract so a change to the warning window cannot silently shorten the
 * encounter in one of the adapters.</p>
 */
public final class BlackFogTimingPolicy {
    public static final int CYCLE_COUNT = 3;
    public static final int SAFE_ZONE_SECONDS = 4;
    public static final int FOG_SECONDS = 3;
    private static final int[] COMBAT_SECONDS = {40, 50, 60};

    private BlackFogTimingPolicy() {
    }

    public static int combatSeconds(int cycle) {
        return COMBAT_SECONDS[Math.max(0, Math.min(CYCLE_COUNT - 1, cycle))];
    }

    public static int safeZoneSeconds() {
        return SAFE_ZONE_SECONDS;
    }

    public static int fogSeconds() {
        return FOG_SECONDS;
    }

    public static boolean complete(int completedCycles) {
        return completedCycles >= CYCLE_COUNT;
    }
}
