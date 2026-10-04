package me.copimine.endevent.domain;

/** Local wake/leash rules for the three guards assigned to one caster. */
public final class RitualGuardAggroPolicy {
    public static final double AGGRO_RADIUS_BLOCKS = 3.0D;
    public static final double INTERCEPT_HOLD_RADIUS_BLOCKS = 6.0D;
    public static final double LEASH_RADIUS_BLOCKS = 14.0D;
    public static final double POST_RADIUS_BLOCKS = 2.4D;
    public enum Stance { HOLD, RETURN, INTERCEPT, CAST }
    public record Post(double x, double z) { }

    private RitualGuardAggroPolicy() {
    }

    public static boolean shouldWake(double distanceToCaster, boolean casterAttacked,
                                     boolean guardAttacked, boolean rangedAttack) {
        return shouldWake(distanceToCaster, casterAttacked, guardAttacked, rangedAttack, false);
    }

    public static boolean shouldWake(double distanceToCaster, boolean casterAttacked,
                                     boolean guardAttacked, boolean rangedAttack, boolean alreadyEngaged) {
        boolean near = Double.isFinite(distanceToCaster)
                && distanceToCaster >= 0.0D
                && distanceToCaster <= (alreadyEngaged ? INTERCEPT_HOLD_RADIUS_BLOCKS : AGGRO_RADIUS_BLOCKS);
        return near || casterAttacked || guardAttacked || rangedAttack;
    }

    public static boolean withinLeash(double distanceFromCaster) {
        return Double.isFinite(distanceFromCaster) && distanceFromCaster >= 0.0D
                && distanceFromCaster <= LEASH_RADIUS_BLOCKS;
    }

    public static Stance stance(boolean threatPresent, boolean casting, double distanceToPost) {
        if (casting) return Stance.CAST;
        if (threatPresent) return Stance.INTERCEPT;
        return Double.isFinite(distanceToPost) && distanceToPost >= 0.0D
                && distanceToPost <= 0.55D ? Stance.HOLD : Stance.RETURN;
    }

    public static Post postOffset(int slot, int count, double outwardAngle) {
        if (count < 1 || count > 3 || slot < 0 || slot >= count || !Double.isFinite(outwardAngle)) {
            throw new IllegalArgumentException("invalid guard post");
        }
        double spread = count == 1 ? 0.0D
                : (slot / (double) (count - 1) - 0.5D) * Math.PI * 2.0D / 3.0D;
        double angle = outwardAngle + spread;
        return new Post(Math.cos(angle) * POST_RADIUS_BLOCKS, Math.sin(angle) * POST_RADIUS_BLOCKS);
    }
}
