package me.copimine.endevent.domain;

/** Wave-scoped pressure handling for authored exceptions to the common profile. */
public final class WavePressurePolicy {
    private static final int WAVE_ONE = 1;
    private static final int WAVE_TWO = 2;
    private static final int WAVE_FIVE = 5;
    private static final int SMALL_PARTY_WAVE_ONE_PRESSURE = 12;
    private static final int TWO_PLAYER_WAVE_FIVE_BASE_PRESSURE = 8;
    private static final int WAVE_PRESSURE_MULTIPLIER = 2;
    private static final int APPROVED_WAVE_FIVE_ELITE_ENDERMEN = 6;
    private static final int APPROVED_WAVE_FIVE_ELITE_SKELETONS = 2;

    private WavePressurePolicy() { }

    /** Double the live roster for Waves 1 and 3-5 while retaining bounded profiles. */
    public static WaveMechanicsPolicy.WaveCounts clamp(
            int wave, WaveMechanicsPolicy.WaveCounts requested, int livingPlayers) {
        if (requested == null) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        }
        int cap = activeCap(wave, livingPlayers);
        if (cap == 0 || wave == WAVE_TWO) {
            return wave == WAVE_TWO ? WaveMechanicsPolicy.clampToPressure(requested, livingPlayers)
                    : new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        }
        if (wave == WAVE_FIVE && livingPlayers == PressureBudgetController.MIN_PLAYERS) {
            return clampTwoPlayerWaveFive(requested, cap);
        }
        return clampToCapacity(requested, cap);
    }

    /** Bounded doubled live pressure for the explicitly strengthened early event waves. */
    public static int activeCap(int wave, int livingPlayers) {
        if (livingPlayers < PressureBudgetController.MIN_PLAYERS) return 0;
        if (wave == WAVE_TWO || wave < WAVE_ONE || wave > WAVE_FIVE) {
            return PressureBudgetController.profileForPlayers(livingPlayers).activePressure();
        }
        int baseline = wave == WAVE_ONE && livingPlayers <= 3
                ? SMALL_PARTY_WAVE_ONE_PRESSURE
                : PressureBudgetController.profileForPlayers(livingPlayers).activePressure();
        if (wave == WAVE_FIVE && livingPlayers == PressureBudgetController.MIN_PLAYERS) {
            baseline = Math.max(baseline, TWO_PLAYER_WAVE_FIVE_BASE_PRESSURE);
        }
        return Math.min(PressureBudgetController.HARD_CAP,
                baseline * WAVE_PRESSURE_MULTIPLIER);
    }

    private static WaveMechanicsPolicy.WaveCounts clampTwoPlayerWaveFive(
            WaveMechanicsPolicy.WaveCounts requested, int cap) {
        int eliteEndermen = requested.eliteEndermen() > 0
                ? APPROVED_WAVE_FIVE_ELITE_ENDERMEN : 0;
        int eliteSkeletons = requested.eliteSkeletons() > 0
                ? APPROVED_WAVE_FIVE_ELITE_SKELETONS : 0;
        int ordinaryCap = Math.max(0, cap - eliteEndermen - eliteSkeletons);
        WaveMechanicsPolicy.WaveCounts ordinary = clampOrdinaryToCapacity(requested, ordinaryCap);
        return new WaveMechanicsPolicy.WaveCounts(ordinary.endermen(), ordinary.spiders(),
                ordinary.skeletons(), eliteEndermen, eliteSkeletons);
    }

    private static WaveMechanicsPolicy.WaveCounts clampToCapacity(
            WaveMechanicsPolicy.WaveCounts requested, int cap) {
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

    private static WaveMechanicsPolicy.WaveCounts clampOrdinaryToCapacity(
            WaveMechanicsPolicy.WaveCounts requested, int cap) {
        int[] counts = {
                boundedCount(requested.endermen(), cap),
                boundedCount(requested.spiders(), cap),
                boundedCount(requested.skeletons(), cap)};
        int total = sum(counts);
        while (total > cap) {
            int selected = largestBucket(counts, 0, counts.length);
            if (selected < 0) break;
            counts[selected]--;
            total--;
        }
        return new WaveMechanicsPolicy.WaveCounts(counts[0], counts[1], counts[2], 0, 0);
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

    /** True only for the doubled two-player Wave 5 roster: eight elites and eight ordinary mobs. */
    public static boolean isApprovedTwoPlayerException(
            int wave, int livingPlayers, WaveMechanicsPolicy.WaveCounts active) {
        return wave == WAVE_FIVE && livingPlayers == 2 && active != null
                && active.eliteEndermen() == APPROVED_WAVE_FIVE_ELITE_ENDERMEN
                && active.eliteSkeletons() == APPROVED_WAVE_FIVE_ELITE_SKELETONS
                && active.endermen() + active.spiders() + active.skeletons()
                == TWO_PLAYER_WAVE_FIVE_BASE_PRESSURE
                && active.total() == activeCap(WAVE_FIVE, livingPlayers)
                && active.total() <= PressureBudgetController.HARD_CAP;
    }
}
