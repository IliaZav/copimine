package me.copimine.endevent.domain;

/** Non-lethal prisoner drain and external-damage floor for Ritual Sphere. */
public final class RitualPrisonerHealthPolicy {
    public static final long DRAIN_INTERVAL_MILLIS = 20_000L;
    public static final double DRAIN_HEALTH = 2.0D;
    public static final double MIN_HEALTH = 1.0D;

    private RitualPrisonerHealthPolicy() {
    }

    public static DrainResult drain(double currentHealth) {
        double safeHealth = finiteOrFloor(currentHealth);
        if (safeHealth <= MIN_HEALTH) {
            return new DrainResult(MIN_HEALTH, 0.0D, 0);
        }
        double remaining = Math.max(MIN_HEALTH, safeHealth - DRAIN_HEALTH);
        return new DrainResult(remaining, safeHealth - remaining, 1);
    }

    public static boolean drainDue(long nowMillis, long lastDrainMillis) {
        if (nowMillis < 0L || lastDrainMillis < 0L || nowMillis < lastDrainMillis) {
            return false;
        }
        return nowMillis - lastDrainMillis >= DRAIN_INTERVAL_MILLIS;
    }

    /** Captured prisoners take no external damage between authoritative drains. */
    public static double safeExternalDamage(double currentHealth, double requestedDamage) {
        return 0.0D;
    }

    public record DrainResult(double remainingHealth, double appliedDamage, int intensityGain) {
        public DrainResult {
            if (!Double.isFinite(remainingHealth) || !Double.isFinite(appliedDamage)
                    || remainingHealth < MIN_HEALTH || appliedDamage < 0.0D
                    || intensityGain < 0) {
                throw new IllegalArgumentException("invalid prisoner drain result");
            }
        }
    }

    private static double finiteOrFloor(double value) {
        return Double.isFinite(value) ? Math.max(MIN_HEALTH, value) : MIN_HEALTH;
    }
}
