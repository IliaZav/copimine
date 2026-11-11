package me.copimine.endevent.domain;

/**
 * Finite melee-only summon policy; numerical tuning belongs to EventConfig.
 *
 * <p>SUPERSEDED by {@link SmallKaguneSpawnPolicy}. Master contract 18 states that
 * the previous small Kagune implementation - a bounded summon that shares the
 * six temporary tentacle slots and disappears on a lifetime timer - does not
 * implement the specification. The compatibility answers below are kept only so
 * historical assertions fail honestly instead of silently:</p>
 *
 * <ul>
 *   <li>{@link #budget} no longer caps at the six temporary tentacles and no
 *       longer shortens the batch by the number of visuals already on screen.</li>
 *   <li>{@link #expired} is always false: a small Kagune now survives until it
 *       is killed.</li>
 * </ul>
 */
public final class SmallKagunePolicy {
    private SmallKagunePolicy() { }

    /** Party-scaled batch that ignores the retired six-tentacle budget. */
    public static int budget(int rosterSize, int maxActive, int activeCount) {
        return SmallKaguneSpawnPolicy.admission(rosterSize, Math.max(0, activeCount));
    }

    /** SUPERSEDED: no short forced lifetime exists any more. */
    public static boolean expired(long nowTick, long expiresAtTick) {
        return false;
    }

    public static boolean inStrikeRange(double distanceSquared, double range) {
        return Double.isFinite(distanceSquared) && Double.isFinite(range) && range > 0.0D
                && distanceSquared >= 0.0D && distanceSquared <= range * range;
    }
}
