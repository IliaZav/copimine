package me.copimine.endevent.domain;

/** Bounded combat decisions for the Rift Giant zombie elite. */
public final class RiftGiantPolicy {
    public static final double AURA_RADIUS_BLOCKS = 7.0D;
    public static final double HEAVY_STRIKE_START_RADIUS_BLOCKS = 4.5D;
    public static final double HEAVY_STRIKE_HIT_RADIUS_BLOCKS = 5.0D;
    public static final long HEAVY_STRIKE_TELEGRAPH_MILLIS = 1_800L;
    public static final long HEAVY_STRIKE_COOLDOWN_MILLIS = 20_000L;
    public static final double HEAVY_STRIKE_MAX_DAMAGE = 6.0D;
    public static final long SUMMON_TELEGRAPH_MILLIS = 1_500L;
    public static final long SUMMON_COOLDOWN_MILLIS = 30_000L;

    private RiftGiantPolicy() { }

    public static int summonBudget(int participantCount) {
        if (participantCount <= 0) return 0;
        if (participantCount <= 2) return 2;
        if (participantCount <= 5) return 3;
        return 4;
    }

    /** Reserve only the summons allowed by both the roster and live pressure cap. */
    public static int admittedSummons(int participantCount, int alreadySummoned,
                                      int remainingPressureSlots) {
        if (participantCount <= 0 || alreadySummoned < 0 || remainingPressureSlots <= 0) return 0;
        int budgetRemaining = Math.max(0, summonBudget(participantCount) - alreadySummoned);
        return Math.min(budgetRemaining, remainingPressureSlots);
    }

    /**
     * Live giants persist the same encounter-wide total. The maximum keeps the
     * budget conservative if only some entities have completed a checkpoint.
     */
    public static int sharedSummonsUsed(Iterable<Integer> persistedCounters) {
        if (persistedCounters == null) return 0;
        int used = 0;
        for (Integer counter : persistedCounters) {
            if (counter != null && counter > used) used = counter;
        }
        return used;
    }

    public static boolean shouldBeginHeavyStrike(double targetDistanceSquared,
                                                  boolean targetEligible,
                                                  boolean cooldownReady,
                                                  boolean alreadyTelegraphing) {
        return shouldBeginHeavyStrike(targetDistanceSquared, targetEligible, cooldownReady,
                alreadyTelegraphing, false);
    }

    public static boolean shouldBeginHeavyStrike(double targetDistanceSquared,
                                                  boolean targetEligible,
                                                  boolean cooldownReady,
                                                  boolean alreadyTelegraphing,
                                                  boolean otherMajorAbilityPending) {
        return targetEligible && cooldownReady && !alreadyTelegraphing && !otherMajorAbilityPending
                && Double.isFinite(targetDistanceSquared) && targetDistanceSquared >= 0.0D
                && targetDistanceSquared <= HEAVY_STRIKE_START_RADIUS_BLOCKS
                * HEAVY_STRIKE_START_RADIUS_BLOCKS;
    }

    public static boolean shouldRefreshAura(boolean combatPhaseActive,
                                            long nowMillis,
                                            long nextRefreshMillis) {
        return combatPhaseActive && nowMillis >= nextRefreshMillis;
    }

    /** Defer cleanup while the owner is unloaded or its last known chunk is unavailable. */
    public static boolean shouldRetireOrphanedSummon(boolean ownerPresent,
                                                     boolean ownerKnownUnloaded,
                                                     boolean ownerChunkLoaded) {
        return shouldRetireOrphanedSummon(ownerPresent, ownerKnownUnloaded,
                ownerChunkLoaded, false);
    }

    /**
     * A known-unloaded owner is absent only after its actual last-known chunk
     * is the chunk whose entity-load batch is being reconciled.
     */
    public static boolean shouldRetireOrphanedSummon(boolean ownerPresent,
                                                     boolean ownerKnownUnloaded,
                                                     boolean ownerChunkLoaded,
                                                     boolean ownerChunkLoadedInThisEvent) {
        return !ownerPresent && ownerChunkLoaded
                && (!ownerKnownUnloaded || ownerChunkLoadedInThisEvent);
    }

    public static boolean shouldHitAfterTelegraph(double targetDistanceSquared,
                                                  boolean targetEligible) {
        return targetEligible && Double.isFinite(targetDistanceSquared)
                && targetDistanceSquared >= 0.0D
                && targetDistanceSquared <= HEAVY_STRIKE_HIT_RADIUS_BLOCKS
                * HEAVY_STRIKE_HIT_RADIUS_BLOCKS;
    }

    public static boolean isAuraRecipient(double distanceSquared, int giantWave, int allyWave,
                                          boolean eventOwned, boolean livingCombatMob,
                                          boolean giantItself, boolean ritualCaster,
                                          boolean ritualGuard) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
                && distanceSquared <= AURA_RADIUS_BLOCKS * AURA_RADIUS_BLOCKS
                && giantWave == allyWave && eventOwned && livingCombatMob && !giantItself
                && !ritualCaster;
    }

    /** Keep an existing aura lease only while its source remains a nearby active owner. */
    public static boolean shouldRetainAuraLease(boolean sourceIsActiveGiant,
                                                boolean sameWorld,
                                                double sourceDistanceSquared,
                                                int sourceWave,
                                                int recipientWave) {
        return sourceIsActiveGiant && sameWorld
                && Double.isFinite(sourceDistanceSquared) && sourceDistanceSquared >= 0.0D
                && sourceDistanceSquared <= AURA_RADIUS_BLOCKS * AURA_RADIUS_BLOCKS
                && sourceWave == recipientWave;
    }
}
