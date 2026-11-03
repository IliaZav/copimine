package me.copimine.endevent.domain;

/**
 * Frozen real-entity health for the boss.  The roster is committed before
 * the boss exists, so reconnects cannot change the boss's maximum health.
 */
public final class BossHealthPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final double MIN_HEALTH = 5_000.0D;
    public static final double MAX_HEALTH = 20_000.0D;
    private BossHealthPolicy() {
    }

    /**
     * Boss maximum health for a party size.
     *
     * <p>SUPERSEDED (2026-11-03): this used to walk a hand-written ladder that
     * reached twenty thousand health. The user's rule is now a proportional
     * curve capped at five thousand — see
     * {@link BossScalingPolicy#maxHealthForPlayers(int)}. The old
     * {@code MIN_HEALTH}/{@code MAX_HEALTH} constants are kept because persisted
     * bosses are clamped against them on load, but neither is authored balance
     * any more.</p>
     */
    public static double maxHealthFor(int players) {
        return BossScalingPolicy.maxHealthForPlayers(players);
    }

    public static double maxHealthForPlayers(int players) {
        return maxHealthFor(players);
    }

    /** Scale an old threshold proportionally to the current real-HP pool. */
    public static double scaleFromBase(double configuredValue, double configuredBase,
                                       double maxHealth) {
        if (!finite(configuredValue) || configuredValue <= 0.0D
                || !finite(configuredBase) || configuredBase <= 0.0D
                || !finite(maxHealth) || maxHealth <= 0.0D) {
            return 0.0D;
        }
        return clampCurrent(configuredValue * (maxHealth / configuredBase), maxHealth);
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
