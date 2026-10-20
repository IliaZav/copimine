package me.copimine.endevent.domain;

/** Wave-scoped pressure handling for authored exceptions to the common profile. */
public final class WavePressurePolicy {
    private static final int WAVE_ONE = 1;
    private static final int WAVE_FIVE = 5;
    private static final int SMALL_PARTY_WAVE_ONE_PRESSURE = 12;
    private static final int APPROVED_WAVE_FIVE_ELITE_ENDERMEN = 6;
    private static final int APPROVED_WAVE_FIVE_ELITE_SKELETONS = 2;

    private WavePressurePolicy() { }

    /**
     * Apply the normal profile, with only the approved two-player Wave 5 roster
     * exception. The exception has fixed elite role caps, drops ordinary mobs,
     * and remains below the absolute entity hard cap.
     */
    public static WaveMechanicsPolicy.WaveCounts clamp(
            int wave, WaveMechanicsPolicy.WaveCounts requested, int livingPlayers) {
        if (requested == null) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        }
        if (wave == WAVE_ONE
                && livingPlayers >= PressureBudgetController.MIN_PLAYERS
                && livingPlayers <= 3) {
            return clampToEarlyWavePressure(requested);
        }
        WaveMechanicsPolicy.WaveCounts pressureLimited =
                WaveMechanicsPolicy.clampToPressure(requested, livingPlayers);
        if (wave != WAVE_FIVE || livingPlayers != 2) {
            return pressureLimited;
        }

        int eliteEndermen = Math.min(APPROVED_WAVE_FIVE_ELITE_ENDERMEN,
                Math.max(0, requested.eliteEndermen()));
        int eliteSkeletons = Math.min(APPROVED_WAVE_FIVE_ELITE_SKELETONS,
                Math.max(0, requested.eliteSkeletons()));
        int eliteTotal = eliteEndermen + eliteSkeletons;
        if (eliteTotal > PressureBudgetController.profileForPlayers(livingPlayers).activePressure()) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, eliteEndermen, eliteSkeletons);
        }
        return new WaveMechanicsPolicy.WaveCounts(
                pressureLimited.endermen(), pressureLimited.spiders(), pressureLimited.skeletons(),
                eliteEndermen, eliteSkeletons);
    }

    /**
     * Wave 1 uses the full twelve simultaneous slots for two- and three-player
     * parties. This narrow cap stays below the absolute event hard cap and
     * does not alter the shared pressure profile for later waves.
     */
    private static WaveMechanicsPolicy.WaveCounts clampToEarlyWavePressure(
            WaveMechanicsPolicy.WaveCounts requested) {
        int cap = Math.min(SMALL_PARTY_WAVE_ONE_PRESSURE, PressureBudgetController.HARD_CAP);
        int[] counts = {
                boundedCount(requested.endermen(), cap),
                boundedCount(requested.spiders(), cap),
                boundedCount(requested.skeletons(), cap),
                boundedCount(requested.eliteEndermen(), cap),
                boundedCount(requested.eliteSkeletons(), cap)};
        int total = sum(counts);
        while (total > cap) {
            int selected = largestBucket(counts, 0, 3);
            if (selected < 0) {
                selected = largestBucket(counts, 3, counts.length);
            }
            if (selected < 0) {
                break;
            }
            counts[selected]--;
            total--;
        }
        return new WaveMechanicsPolicy.WaveCounts(
                counts[0], counts[1], counts[2], counts[3], counts[4]);
    }

    private static int boundedCount(int count, int cap) {
        return Math.min(cap, Math.max(0, count));
    }

    private static int largestBucket(int[] counts, int startInclusive, int endExclusive) {
        int selected = -1;
        for (int index = startInclusive; index < endExclusive; index++) {
            if (counts[index] > 0 && (selected < 0 || counts[index] > counts[selected])) {
                selected = index;
            }
        }
        return selected;
    }

    private static int sum(int[] counts) {
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }

    /** True only for the exact approved eight-elite two-player Wave 5 roster. */
    public static boolean isApprovedTwoPlayerException(
            int wave, int livingPlayers, WaveMechanicsPolicy.WaveCounts active) {
        return wave == WAVE_FIVE && livingPlayers == 2 && active != null
                && active.endermen() == 0 && active.spiders() == 0 && active.skeletons() == 0
                && active.eliteEndermen() == APPROVED_WAVE_FIVE_ELITE_ENDERMEN
                && active.eliteSkeletons() == APPROVED_WAVE_FIVE_ELITE_SKELETONS
                && active.total() == 8 && active.total() <= PressureBudgetController.HARD_CAP;
    }
}
