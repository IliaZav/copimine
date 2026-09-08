package me.copimine.endevent.domain;

/** Single bounded reduction applied to wave and mini-boss outgoing damage. */
public final class WaveDamagePolicy {
    private WaveDamagePolicy() {
    }

    public static double reduce(double configuredDamage, double reduction) {
        double damage = finite(configuredDamage) ? Math.max(0.0D, configuredDamage) : 0.0D;
        double amount = finite(reduction) ? Math.max(0.0D, reduction) : 0.0D;
        return Math.max(0.0D, damage - amount);
    }

    /** Keep an attack interactive even when a reduction exceeds its base. */
    public static double minimumCombatDamage(double configuredDamage, double reduction) {
        return Math.max(1.0D, reduce(configuredDamage, reduction));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
