package me.copimine.endevent.domain;

/** Bounded boss HP scaling for the frozen official participant roster. */
public final class BossHealthScalingPolicy {
    public static final int BASE_PLAYERS = 2;
    public static final int SCALE_START_PLAYERS = 8;
    public static final int MAX_PLAYERS = 20;
    public static final double BASE_HEALTH = 5000.0D;
    public static final double SCALE_START_HEALTH = 10000.0D;
    public static final double MAX_HEALTH = 20000.0D;

    private BossHealthScalingPolicy() {
    }

    /**
     * Return the authoritative virtual HP pool.  The first two-player fight
     * starts at 5000 HP, rises smoothly to 10000 at eight players, then rises
     * one linear step per extra player until the 20000 HP cap at twenty.
     */
    public static double maxHealthForPlayers(int players) {
        int safePlayers = Math.max(1, Math.min(MAX_PLAYERS, players));
        if (safePlayers <= BASE_PLAYERS) {
            return BASE_HEALTH;
        }
        if (safePlayers <= SCALE_START_PLAYERS) {
            double progress = (safePlayers - BASE_PLAYERS)
                    / (double) (SCALE_START_PLAYERS - BASE_PLAYERS);
            return BASE_HEALTH + (SCALE_START_HEALTH - BASE_HEALTH) * progress;
        }
        double progress = (safePlayers - SCALE_START_PLAYERS)
                / (double) (MAX_PLAYERS - SCALE_START_PLAYERS);
        return SCALE_START_HEALTH + (MAX_HEALTH - SCALE_START_HEALTH) * progress;
    }

    /** Preserve a configured threshold's percentage when the max pool grows. */
    public static double scaleFromBase(double configuredValue,
                                       double configuredBaseHealth,
                                       double maxHealth) {
        double safeMax = positive(maxHealth, BASE_HEALTH);
        double safeBase = positive(configuredBaseHealth, BASE_HEALTH);
        double safeValue = finite(configuredValue) ? Math.max(0.0D, configuredValue) : 0.0D;
        return Math.max(0.0D, Math.min(safeMax, safeValue / safeBase * safeMax));
    }

    /** The Judgment cast remains at ten percent of whichever pool is active. */
    public static double judgmentThreshold(double maxHealth) {
        return positive(maxHealth, BASE_HEALTH) * 0.10D;
    }

    private static double positive(double value, double fallback) {
        return finite(value) && value > 0.0D ? value : fallback;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
