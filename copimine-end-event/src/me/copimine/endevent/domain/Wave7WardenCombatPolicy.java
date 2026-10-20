package me.copimine.endevent.domain;

/** Bounded runtime profile for the existing Wave 7 Warden attack cycle. */
public final class Wave7WardenCombatPolicy {
    public enum Warning {
        NARROW_STRIKE,
        WIDE_STRIKE,
        SONIC_LUNGE
    }

    public record AttackProfile(
            int pattern,
            Warning warning,
            double warningLength,
            double warningWidth,
            double damageWidth,
            double damageRange,
            double damage,
            boolean lunge,
            double lungeSpeed,
            int lungeTicks,
            double lungeDamageWidth,
            double lungeDamageRange,
            double lungeDamage) {
    }

    private static final double MAX_HEALTH = 200.0D;
    private static final AttackProfile[] ATTACKS = {
            new AttackProfile(0, Warning.NARROW_STRIKE, 7.0D, 1.35D,
                    1.35D, 7.0D, 5.5D, false, 0.0D, 0, 0.0D, 0.0D, 0.0D),
            new AttackProfile(1, Warning.WIDE_STRIKE, 5.0D, 2.7D,
                    2.7D, 5.0D, 4.0D, false, 0.0D, 0, 0.0D, 0.0D, 0.0D),
            new AttackProfile(2, Warning.SONIC_LUNGE, 7.0D, 1.35D,
                    0.0D, 0.0D, 0.0D, true, 0.82D, 9, 1.3D, 4.0D, 5.0D)
    };

    private Wave7WardenCombatPolicy() {
    }

    public static double maxHealth() {
        return MAX_HEALTH;
    }

    /** Preserve remaining damage on recovery, while promoting an old full-health actor. */
    public static double currentHealthAfterMaxHealthChange(
            double currentHealth, double previousMaxHealth) {
        if (!Double.isFinite(currentHealth) || currentHealth <= 0.0D) return 0.0D;
        if (Double.isFinite(previousMaxHealth) && previousMaxHealth > 0.0D
                && currentHealth >= previousMaxHealth) {
            return MAX_HEALTH;
        }
        return Math.min(currentHealth, MAX_HEALTH);
    }

    public static int patternForOrdinal(int attackCount) {
        return Math.floorMod(attackCount, ATTACKS.length);
    }

    public static AttackProfile profileForPattern(int pattern) {
        return ATTACKS[Math.floorMod(pattern, ATTACKS.length)];
    }

    public static String warningName(int pattern) {
        return profileForPattern(pattern).warning().name();
    }
}
