package me.copimine.endevent.domain;

/** Single-owner, one-pass adjustment for non-boss event damage. */
public final class EventMobDamagePolicy {
    public static final double DEFAULT_REDUCTION = 4.0D;
    public static final double DEFAULT_MINIMUM_DAMAGE = 1.0D;

    private EventMobDamagePolicy() {
    }

    public record Decision(double damage, boolean adjusted, String reason) {
        public Decision {
            damage = finite(damage) ? Math.max(0.0D, damage) : 0.0D;
            reason = reason == null ? "UNKNOWN" : reason;
        }
    }

    public static Decision adjust(double oldEffectiveDamage, double minimumDamage,
                                  boolean boss, boolean scripted, boolean alreadyAdjusted) {
        double minimum = finite(minimumDamage) ? Math.max(0.0D, minimumDamage) : DEFAULT_MINIMUM_DAMAGE;
        double old = finite(oldEffectiveDamage) ? Math.max(0.0D, oldEffectiveDamage) : minimum;
        if (boss) return new Decision(old, false, "BOSS_EXEMPT");
        if (scripted) return new Decision(old, false, "SCRIPTED_EXEMPT");
        if (alreadyAdjusted) return new Decision(old, false, "ALREADY_ADJUSTED");
        return new Decision(Math.max(minimum, old - DEFAULT_REDUCTION), true, "REDUCED_ONCE");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
