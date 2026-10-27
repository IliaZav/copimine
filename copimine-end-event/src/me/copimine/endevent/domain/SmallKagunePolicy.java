package me.copimine.endevent.domain;

/** Finite melee-only summon policy; numerical tuning belongs to EventConfig. */
public final class SmallKagunePolicy {
    private SmallKagunePolicy() { }

    /** Existing live summons count against both the scaled and configured cap. */
    public static int budget(int rosterSize, int maxActive, int activeCount) {
        int roster = Math.max(0, Math.min(20, rosterSize));
        int cap = Math.max(0, Math.min(TentacleScalingPolicy.MAX_TEMPORARY, maxActive));
        int scaled = Math.min(cap, (roster + 4) / 5);
        return Math.max(0, scaled - Math.max(0, activeCount));
    }

    public static boolean expired(long nowTick, long expiresAtTick) {
        return expiresAtTick > 0L && nowTick >= expiresAtTick;
    }

    public static boolean inStrikeRange(double distanceSquared, double range) {
        return Double.isFinite(distanceSquared) && Double.isFinite(range) && range > 0.0D
                && distanceSquared >= 0.0D && distanceSquared <= range * range;
    }
}
