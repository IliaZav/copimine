package me.copimine.endevent.domain;

/** Bounded combat stat increases for every event-owned elite role. */
public final class EliteCombatStatsPolicy {
    public static final double HEALTH_MULTIPLIER = 1.50D;
    public static final double ATTACK_MULTIPLIER = 1.25D;
    public static final double MOVEMENT_SPEED_MULTIPLIER = 1.12D;
    public static final double ARMOR_BONUS = 3.0D;
    public static final double ARMOR_TOUGHNESS_BONUS = 1.0D;
    public static final double MIN_KNOCKBACK_RESISTANCE = 0.20D;
    public static final double MAX_HEALTH = 256.0D;
    public static final double MAX_ATTACK_DAMAGE = 40.0D;
    public static final double MAX_MOVEMENT_SPEED = 0.65D;
    public static final double MAX_ARMOR = 30.0D;
    public static final double MAX_ARMOR_TOUGHNESS = 20.0D;
    public static final double MAX_KNOCKBACK_RESISTANCE = 0.80D;

    private EliteCombatStatsPolicy() { }

    public static Stats forElite(double health, double attack, double speed,
                                 double armor, double toughness, double knockbackResistance,
                                 int players) {
        double strength = WaveScalingPolicy.mobStrengthMultiplier(players);
        return new Stats(
                bounded(positive(health) * HEALTH_MULTIPLIER * strength, MAX_HEALTH),
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
