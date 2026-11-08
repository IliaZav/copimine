package me.copimine.endevent.domain;

/**
 * Deterministic timing for the three Wave 5 black-fog cycles.
 *
 * <p>The Bukkit controller owns the wall clock.  This class only owns the
 * contract so a change to the warning window cannot silently shorten the
 * encounter in one of the adapters.</p>
 */
public final class BlackFogTimingPolicy {
    /**
     * Number of Wave 5 fog cycles.
     *
     * <p>The approved contract is THREE cycles of roughly 40, 50 and 60
     * seconds. The code still carried the older five-cycle constant, so a party
     * that had survived the approved encounter was asked two more times and the
     * wave overran its approved length.</p>
     */
    public static final int CYCLE_COUNT = 3;
    /** Eight seconds gives a player a real window to read and reach the zone. */
    public static final int SAFE_ZONE_SECONDS = 8;
    /**
     * How long the fog itself lasts, per cycle.
     *
     * <p>The authored value was three seconds, which read as a blink rather than
     * as a hazard: the blindness and the damage landed and were gone before a
     * player could register which zone they had failed to reach. The request was
     * a flat +7 seconds on whatever was already running, so ten seconds.</p>
     */
    public static final int FOG_SECONDS = 10;
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

    public static boolean hasRemainingCycles(int completedCycles) {
        return completedCycles >= 0 && completedCycles < CYCLE_COUNT;
    }
}