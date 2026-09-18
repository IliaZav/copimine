package me.copimine.endevent.domain;

/**
 * Applies already-computed Bukkit final damage to an event entity's real
 * health.  Event mobs use this only after the ordinary protection listeners
 * have accepted a player hit, preventing the vanilla hurt-resistance window
 * from silently retaining only part of a later hit.
 */
public final class EventRealHealthDamagePolicy {
    private EventRealHealthDamagePolicy() {
    }

    public static Result apply(double currentHealth, double maximumHealth, double finalDamage) {
        double maximum = Math.max(1.0D, maximumHealth);
        double before = Math.max(0.0D, Math.min(maximum, currentHealth));
        double requested = Math.max(0.0D, finalDamage);
        double remaining = Math.max(0.0D, before - requested);
        return new Result(before, remaining, before - remaining);
    }

    public static Result applySeries(double currentHealth, double maximumHealth, double... finalDamages) {
        Result result = apply(currentHealth, maximumHealth, 0.0D);
        if (finalDamages == null) {
            return result;
        }
        double applied = 0.0D;
        for (double finalDamage : finalDamages) {
            result = apply(result.remainingHealth(), maximumHealth, finalDamage);
            applied += result.appliedDamage();
        }
        return new Result(Math.max(0.0D, Math.min(Math.max(1.0D, maximumHealth), currentHealth)),
                result.remainingHealth(), applied);
    }

    public record Result(double healthBefore, double remainingHealth, double appliedDamage) {
        public boolean applied() {
            return appliedDamage > 0.0D;
        }
    }
}
