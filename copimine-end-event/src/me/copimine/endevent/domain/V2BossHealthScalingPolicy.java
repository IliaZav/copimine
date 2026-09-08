package me.copimine.endevent.domain;

/**
 * Frozen real-entity health for the V2 boss.  The roster is committed before
 * the boss exists, so reconnects cannot change the boss's maximum health.
 */
public final class V2BossHealthScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final double MIN_HEALTH = 5_000.0D;
    public static final double MAX_HEALTH = 20_000.0D;

    private static final int[] PLAYERS = {
            2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20
    };
    private static final double[] HEALTH = {
            5_000.0D, 6_000.0D, 7_000.0D, 8_500.0D, 9_500.0D,
            10_500.0D, 11_500.0D, 12_500.0D, 13_500.0D, 14_500.0D,
            15_000.0D, 15_500.0D, 16_000.0D, 17_500.0D, 18_000.0D,
            18_500.0D, 19_000.0D, 19_500.0D, 20_000.0D
    };

    private V2BossHealthScalingPolicy() {
    }

    public static double maxHealthFor(int players) {
        int safePlayers = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        for (int index = 0; index < PLAYERS.length; index++) {
            if (safePlayers <= PLAYERS[index]) {
                return HEALTH[index];
            }
        }
        return MAX_HEALTH;
    }

    public static double clampCurrent(double health, double maxHealth) {
        double safeMax = finite(maxHealth) && maxHealth > 0.0D ? maxHealth : MIN_HEALTH;
        double safeHealth = finite(health) ? health : 0.0D;
        return Math.max(0.0D, Math.min(safeMax, safeHealth));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
