package me.copimine.endevent.domain;

/**
 * Pure composition bounds shared by the seven official waves.
 *
 * <p>Entity spawning, targeting and effects stay in the Bukkit adapter. This
 * class only performs deterministic arithmetic so every spawn path is subject
 * to the same pressure budget.</p>
 */
public final class WaveMechanicsPolicy {
    /** Exactly the three charge carriers Wave 1 can consume are elite roles. */
    public static final int CARRIER_ELITE_SLOTS = RiftCarrierPolicy.REQUIRED_DELIVERIES;

    private WaveMechanicsPolicy() {
    }

    /**
     * Clamp a requested wave composition to the active-player pressure budget.
     * Elite entries are preserved while ordinary entries are available;
     * if elites alone exceed the budget, they are trimmed in a stable order.
     * The method is intentionally bounded for arbitrary input.
     */
    public static WaveCounts clampToPressure(WaveCounts requested, int livingPlayers) {
        if (requested == null) {
            return new WaveCounts(0, 0, 0, 0, 0);
        }
        int cap = PressureBudgetController.profileForPlayers(livingPlayers).activePressure();
        int[] counts = {
                nonNegative(requested.endermen()),
                nonNegative(requested.spiders()),
                nonNegative(requested.skeletons()),
                nonNegative(requested.eliteEndermen()),
                nonNegative(requested.eliteSkeletons())};
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        while (total > cap) {
            int selected = largestOrdinaryBucket(counts);
            if (selected < 0) {
                selected = largestEliteBucket(counts);
            }
            if (selected < 0) {
                break;
            }
            counts[selected]--;
            total--;
        }
        return new WaveCounts(counts[0], counts[1], counts[2], counts[3], counts[4]);
    }

    /** Wave 3 uses three gates in the official flow. */
    public static int gateCount() {
        return 3;
    }

    /** Fold Wave 1 ranged and Wave 2 elite slots into ordinary roles; remove Wave 7 elites. */
    public static WaveCounts compositionForWave(int wave, WaveCounts requested) {
        if (requested == null) return new WaveCounts(0, 0, 0, 0, 0);
        if (wave == 1) {
            // Wave 1 charge carriers are elites. Exactly the number of carriers the
            // objective can consume is promoted to the elite role and the surplus
            // endermen slots become ordinary melee pressure, so the wave keeps its
            // configured size while no ranged mob is ever created.
            int carrierSlots = requested.endermen() + requested.eliteEndermen();
            int elites = Math.min(CARRIER_ELITE_SLOTS, Math.max(0, carrierSlots));
            int spiders = requested.spiders() + requested.skeletons()
                    + requested.eliteSkeletons() + (carrierSlots - elites);
            return new WaveCounts(0, spiders, 0, elites, 0);
        }
        if (wave == 2) {
            return new WaveCounts(requested.endermen() + requested.eliteEndermen(),
                    requested.spiders(), requested.skeletons() + requested.eliteSkeletons(),
                    0, 0);
        }
        if (wave == 7) {
            return new WaveCounts(requested.endermen(), requested.spiders(), requested.skeletons(),
                    0, 0);
        }
        return requested;
    }

    private static int largestOrdinaryBucket(int[] counts) {
        int selected = -1;
        for (int index = 0; index < 3; index++) {
            if (counts[index] > 0 && (selected < 0 || counts[index] > counts[selected])) {
                selected = index;
            }
        }
        return selected;
    }

    private static int largestEliteBucket(int[] counts) {
        int selected = -1;
        for (int index = 3; index < 5; index++) {
            if (counts[index] > 0 && (selected < 0 || counts[index] > counts[selected])) {
                selected = index;
            }
        }
        return selected;
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    public record WaveCounts(int endermen, int spiders, int skeletons,
                             int eliteEndermen, int eliteSkeletons) {
        public int total() {
            return nonNegative(endermen) + nonNegative(spiders) + nonNegative(skeletons)
                    + nonNegative(eliteEndermen) + nonNegative(eliteSkeletons);
        }
    }
}
