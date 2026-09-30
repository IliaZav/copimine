package me.copimine.endevent.domain;

/**
 * Pure composition bounds shared by the seven official waves.
 *
 * <p>Entity spawning, targeting and effects stay in the Bukkit adapter. This
 * class only performs deterministic arithmetic so every spawn path is subject
 * to the same pressure budget.</p>
 */
public final class WaveMechanicsPolicy {
    private WaveMechanicsPolicy() {
    }

    /**
     * Clamp a requested wave composition to the active-player pressure budget.
     * Elite entries are preserved first; ordinary entries are trimmed in a
     * stable order. The method is intentionally bounded for arbitrary input.
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

    private static int largestOrdinaryBucket(int[] counts) {
        int selected = -1;
        for (int index = 0; index < 3; index++) {
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
