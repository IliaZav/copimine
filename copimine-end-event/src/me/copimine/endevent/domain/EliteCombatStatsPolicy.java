package me.copimine.endevent.domain;

/**
 * Bounded combat stat increases for every event-owned elite role.
 *
 * <p>The health rule is a contract, not tuning: an elite's maximum HP is
 * exactly {@link #HEALTH_MULTIPLIER} times the corresponding ordinary role's
 * maximum HP under the same frozen party scaling. There is deliberately no
 * absolute health ceiling, because a hidden cap would silently break that
 * ratio; only the non-health stats are clamped.</p>
 */
public final class EliteCombatStatsPolicy {
    public static final double HEALTH_MULTIPLIER = 3.0D;
    // The player reported elites as "weak". A 1.25x attack on top of the same
    // health an ordinary body carries made an elite a bigger target that hit
    // barely harder, so it always lost the trade. The damage multiplier is now
    // a real threat jump; the HP ratio above stays exactly 3x by contract.
    public static final double ATTACK_MULTIPLIER = 1.75D;
    public static final double MOVEMENT_SPEED_MULTIPLIER = 1.12D;
    public static final double ARMOR_BONUS = 3.0D;
    public static final double ARMOR_TOUGHNESS_BONUS = 1.0D;
    public static final double MIN_KNOCKBACK_RESISTANCE = 0.20D;
    public static final double MAX_ATTACK_DAMAGE = 40.0D;
    public static final double MAX_MOVEMENT_SPEED = 0.65D;
    public static final double MAX_ARMOR = 30.0D;
    public static final double MAX_ARMOR_TOUGHNESS = 20.0D;
    public static final double MAX_KNOCKBACK_RESISTANCE = 0.80D;

    private EliteCombatStatsPolicy() { }

    /**
     * Elite maximum HP for a role whose ordinary maximum is {@code
     * ordinaryMaximumHealth} under the already-frozen party scaling. Exact and
     * unbounded on purpose: the ratio is the contract.
     */
    public static double eliteHealth(double ordinaryMaximumHealth) {
        return positive(ordinaryMaximumHealth) * HEALTH_MULTIPLIER;
    }

    public static Stats forElite(double ordinaryHealth, double attack, double speed,
                                 double armor, double toughness, double knockbackResistance) {
        return new Stats(
                eliteHealth(ordinaryHealth),
                bounded(positive(attack) * ATTACK_MULTIPLIER, MAX_ATTACK_DAMAGE),
                bounded(positive(speed) * MOVEMENT_SPEED_MULTIPLIER, MAX_MOVEMENT_SPEED),
                bounded(positive(armor) + ARMOR_BONUS, MAX_ARMOR),
                bounded(positive(toughness) + ARMOR_TOUGHNESS_BONUS, MAX_ARMOR_TOUGHNESS),
                bounded(Math.max(MIN_KNOCKBACK_RESISTANCE, positive(knockbackResistance)),
                        MAX_KNOCKBACK_RESISTANCE));
    }

    public static Stats forElite(double ordinaryHealth, double attack, double speed,
                                 double armor, double toughness, double knockbackResistance,
                                 int players) {
        double strength = WaveScalingPolicy.mobStrengthMultiplier(players);
        return new Stats(
                eliteHealth(ordinaryHealth),
                bounded(positive(attack) * ATTACK_MULTIPLIER * strength, MAX_ATTACK_DAMAGE),
                bounded(positive(speed) * MOVEMENT_SPEED_MULTIPLIER, MAX_MOVEMENT_SPEED),
                bounded(positive(armor) + ARMOR_BONUS, MAX_ARMOR),
                bounded(positive(toughness) + ARMOR_TOUGHNESS_BONUS, MAX_ARMOR_TOUGHNESS),
                bounded(Math.max(MIN_KNOCKBACK_RESISTANCE, positive(knockbackResistance)),
                        MAX_KNOCKBACK_RESISTANCE));
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static double bounded(double value, double maximum) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(maximum, value)) : maximum;
    }

    public record Stats(double health, double attack, double movementSpeed,
                        double armor, double armorToughness, double knockbackResistance) { }
}
