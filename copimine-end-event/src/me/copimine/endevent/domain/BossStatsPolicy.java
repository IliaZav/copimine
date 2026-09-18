package me.copimine.endevent.domain;

/** Small, deterministic balance calculations used by the live boss adapter. */
public final class BossStatsPolicy {
    private BossStatsPolicy() {
    }

    public static double attackDamage(double vanillaBase, double configuredBonus) {
        double base = finite(vanillaBase) ? Math.max(0.0D, vanillaBase) : 0.0D;
        double bonus = finite(configuredBonus) ? Math.max(0.0D, configuredBonus) : 0.0D;
        return base + bonus;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
