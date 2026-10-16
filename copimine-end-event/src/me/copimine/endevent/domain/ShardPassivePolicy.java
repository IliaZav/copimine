package me.copimine.endevent.domain;

/** Pure, bounded rules for passive effects owned by an authentic Rift Shard. */
public final class ShardPassivePolicy {
    public static final int END_EFFECT_AMPLIFIER = 1; // Strength II / Speed II
    public static final int END_EFFECT_REFRESH_TICKS = 40;
    public static final double ENDERMAN_DAMAGE_MULTIPLIER = 0.5D;

    private ShardPassivePolicy() {
    }

    public static boolean endPassivesActive(boolean authenticShard, boolean endWorld) {
        return authenticShard && endWorld;
    }

    public static boolean cancelEnderPearlSelfDamage(boolean authenticShard) {
        return authenticShard;
    }

    public static double endermanDamage(double incomingDamage, boolean authenticShard) {
        if (!Double.isFinite(incomingDamage) || incomingDamage <= 0.0D) {
            return 0.0D;
        }
        if (!authenticShard) {
            return incomingDamage;
        }
        return incomingDamage * ENDERMAN_DAMAGE_MULTIPLIER;
    }
}
