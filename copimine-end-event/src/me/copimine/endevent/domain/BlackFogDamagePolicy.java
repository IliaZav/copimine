package me.copimine.endevent.domain;

/** Bounded, non-lethal health loss for the short Wave 5 fog interval. */
public final class BlackFogDamagePolicy {
    public static final double MAX_DAMAGE_PER_IMPACT = 1.0D;
    public static final double MINIMUM_HEALTH = 2.0D;

    private BlackFogDamagePolicy() {
    }

    public static double damageFor(double health) {
        if (!Double.isFinite(health) || health <= MINIMUM_HEALTH) {
            return 0.0D;
        }
        return Math.min(MAX_DAMAGE_PER_IMPACT, health - MINIMUM_HEALTH);
    }
}
