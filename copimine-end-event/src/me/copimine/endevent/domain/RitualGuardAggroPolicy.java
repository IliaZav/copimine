package me.copimine.endevent.domain;

/** Local wake/leash rules for the guards assigned to one caster. */
public final class RitualGuardAggroPolicy {
    public static final double AGGRO_RADIUS_BLOCKS = 6.0D;
    public static final double INTERCEPT_HOLD_RADIUS_BLOCKS = 10.0D;
    public static final double LEASH_RADIUS_BLOCKS = 14.0D;
    public static final double POST_RADIUS_BLOCKS = 2.4D;
    public static final int RETURN_PATH_MAX_REQUESTS = 3;
    public static final long RETURN_PATH_REQUEST_INTERVAL_MILLIS = 1_000L;
    public static final long RETURN_PATH_RETRY_DELAY_MILLIS = 5_000L;
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

    /** Saturate the failure counter; the retry policy continues after the fast request budget. */
    public static int nextReturnPathAttempt(int previousAttempts) {
        int attempts = Math.max(0, previousAttempts);
        return attempts >= RETURN_PATH_MAX_REQUESTS ? RETURN_PATH_MAX_REQUESTS : attempts + 1;
    }

    /** Slow retries after the bounded fast path request budget instead of freezing the guard. */
    public static long returnPathRetryDelayMillis(int attempts) {
        return attempts >= RETURN_PATH_MAX_REQUESTS
                ? RETURN_PATH_RETRY_DELAY_MILLIS
                : RETURN_PATH_REQUEST_INTERVAL_MILLIS;
    }

    public static boolean mayRequestReturnPath(long nowMillis, long nextAllowedMillis) {
        return nowMillis >= 0L && nowMillis >= nextAllowedMillis;
    }

    /** Return the closest/farthest eligible target, preserving caller order for ties. */
    public static int preferredTargetIndex(double[] distanceBlocks, boolean[] eligible,
                                           boolean preferDistant) {
        if (distanceBlocks == null || eligible == null
                || distanceBlocks.length != eligible.length) {
            return -1;
        }
        int selected = -1;
        double selectedDistance = preferDistant ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int index = 0; index < distanceBlocks.length; index++) {
            double distance = distanceBlocks[index];
            if (!eligible[index] || !Double.isFinite(distance) || distance < 0.0D) continue;
            if (selected < 0 || (preferDistant ? distance > selectedDistance : distance < selectedDistance)) {
                selected = index;
                selectedDistance = distance;
            }
        }
        return selected;
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
