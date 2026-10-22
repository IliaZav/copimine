package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded retaliation policy for normal wave zombies protecting allied skeletons. */
public final class WaveSkeletonProtectionPolicy {
    public static final double RESPONSE_RADIUS_BLOCKS = 8.0D;
    public static final double RESPONSE_RADIUS_SQUARED =
            RESPONSE_RADIUS_BLOCKS * RESPONSE_RADIUS_BLOCKS;
    public static final long RESPONSE_DURATION_MILLIS = 7_000L;
    public static final int MAX_RESPONDERS = 2;

    private WaveSkeletonProtectionPolicy() {
    }

    public static boolean mayReact(boolean acceptedDamage, boolean attackerEligible,
                                   boolean sameAttempt, boolean sameWave,
                                   boolean normalSkeleton, boolean normalZombie,
                                   double distanceSquared) {
        return acceptedDamage && attackerEligible && sameAttempt && sameWave
                && normalSkeleton && normalZombie
                && Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
                && distanceSquared <= RESPONSE_RADIUS_SQUARED;
    }

    /** Keep target ownership only until its deadline or the first invalidating state change. */
    public static boolean responseIsActive(long nowMillis, long expiresAtMillis,
                                           boolean targetEligible, boolean sameWave,
                                           boolean sameGeneration) {
        return nowMillis >= 0L && expiresAtMillis > nowMillis
                && targetEligible && sameWave && sameGeneration;
    }

    /** Select nearest candidates, preserving input order when distances tie. */
    public static int[] nearestResponders(double[] distancesSquared, boolean[] eligible,
                                          int availableSlots) {
        if (distancesSquared == null || eligible == null
                || distancesSquared.length != eligible.length || availableSlots <= 0) {
            return new int[0];
        }
        int limit = Math.min(MAX_RESPONDERS, availableSlots);
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < distancesSquared.length; index++) {
            double distance = distancesSquared[index];
            if (eligible[index] && Double.isFinite(distance) && distance >= 0.0D
                    && distance <= RESPONSE_RADIUS_SQUARED) {
                candidates.add(index);
            }
        }
        candidates.sort(Comparator.comparingDouble((Integer index) -> distancesSquared[index])
                .thenComparingInt(Integer::intValue));
        return candidates.stream().limit(limit).mapToInt(Integer::intValue).toArray();
    }
}
