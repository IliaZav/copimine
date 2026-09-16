package me.copimine.endevent.domain;

/** Local wake/leash rules for the three guards assigned to one caster. */
public final class RitualGuardAggroPolicy {
    public static final double AGGRO_RADIUS_BLOCKS = 3.0D;
    public static final double LEASH_RADIUS_BLOCKS = 14.0D;

    private RitualGuardAggroPolicy() {
    }

    public static boolean shouldWake(double distanceToCaster, boolean casterAttacked,
                                     boolean guardAttacked, boolean rangedAttack) {
        boolean near = Double.isFinite(distanceToCaster)
                && distanceToCaster >= 0.0D
                && distanceToCaster <= AGGRO_RADIUS_BLOCKS;
        return near || casterAttacked || guardAttacked || rangedAttack;
    }

    public static boolean withinLeash(double distanceFromCaster) {
        return Double.isFinite(distanceFromCaster) && distanceFromCaster >= 0.0D
                && distanceFromCaster <= LEASH_RADIUS_BLOCKS;
    }
}
