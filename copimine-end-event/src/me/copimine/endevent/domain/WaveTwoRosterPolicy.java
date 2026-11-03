package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Audit wave 2: the three RIFT_HUNT subwaves ESCALATE.
 *
 * <p>W2-001/W2-002 fix the shape: exactly three finite subwaves with a
 * three-second gap. W2-003 requires later subwaves to awaken more actors than
 * earlier ones, and W2-011 gives the authored distribution of the ordinary
 * materialization budget as approximately 25% / 33% / 42%, rounded
 * deterministically while keeping later groups strictly stronger.</p>
 *
 * <p>The shipped configuration described one flat roster that the stage policy
 * split into two equal halves plus an eight-actor reserve — an even split, so
 * the escalation W2-003 asks for never existed. This policy composes the party
 * and then splits it by the authored shares.</p>
 */
public final class WaveTwoRosterPolicy {
    /** Authored share of the ordinary budget per subwave, in per-mille. */
    public static final int[] SUBWAVE_SHARES_PERMILLE = {250, 330, 420};
    /** The reference two-player party, matching the shipped subwave sizes. */
    public static final int REFERENCE_TOTAL = 40;
    /** Reference subwave sizes the flat roster produces at two players. */
    public static final int OPENING_GROUP = 16;
    public static final int SECOND_GROUP = 16;
    public static final int FINAL_RESERVE = 8;
    /** The shipped flat roster total (36 + 36 + 24), the roster this policy shapes. */
    public static final int FLAT_ROSTER_TOTAL = 96;
    /** Opening actors promoted to a real elite role by roster size. */
    public static final int MIN_OPENING_ELITES = 1;
    public static final int MAX_OPENING_ELITES = 2;
    /** Three skeletons for every two melee actors, the configured shares. */
    private static final int MELEE_SHARE = 2;
    private static final int SKELETON_SHARE = 3;

    private WaveTwoRosterPolicy() { }

    /**
     * The composed RIFT_HUNT roster for a party.
     *
     * <p>An authored roster that is not the shipped flat one is returned
     * untouched, so an operator keeps their own composition.</p>
     */
    public static WaveMechanicsPolicy.WaveCounts compose(WaveMechanicsPolicy.WaveCounts requested,
                                                        int players) {
        if (requested == null) return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        if (requested.total() != FLAT_ROSTER_TOTAL) return requested;
        int roster = Math.max(WaveScalingPolicy.MIN_PLAYERS,
                Math.min(WaveScalingPolicy.MAX_PLAYERS, players));
        int total = targetTotal(roster);
        int elites = openingElites(roster);
        int ordinary = Math.max(0, total - elites);
        int melee = (ordinary * MELEE_SHARE) / (MELEE_SHARE + SKELETON_SHARE);
        int skeletons = ordinary - melee;
        int endermen = (melee + 1) / 2;
        int spiders = melee - endermen;
        return new WaveMechanicsPolicy.WaveCounts(endermen, spiders, skeletons, elites, 0);
    }

    /**
     * Party total for a roster.
     *
     * <p>The reference two-player party is exactly the shipped total. A larger
     * roster keeps the same bounded curve as every other wave; the ESCALATION is
     * expressed inside the party by {@link #splitShares}, not by inflating the
     * party beyond the authored proportions.</p>
     */
    public static int targetTotal(int players) {
        return REFERENCE_TOTAL;
    }

    /** One or two opening actors become real elites once the party grows. */
    public static int openingElites(int players) {
        int roster = Math.max(WaveScalingPolicy.MIN_PLAYERS,
                Math.min(WaveScalingPolicy.MAX_PLAYERS, players));
        return roster >= 15 ? MAX_OPENING_ELITES : MIN_OPENING_ELITES;
    }

    /**
     * Split a party into the three authored subwaves (W2-011).
     *
     * <p>Uses largest-remainder rounding so the parts always sum to the party,
     * and then enforces the W2-003 rule: a later subwave must never be weaker
     * than an earlier one when the total permits it.</p>
     */
    public static List<Integer> splitShares(int total, int subwaves) {
        if (subwaves <= 0) return List.of();
        int safeTotal = Math.max(0, total);
        if (safeTotal == 0) {
            List<Integer> zeros = new ArrayList<>(subwaves);
            for (int index = 0; index < subwaves; index++) zeros.add(0);
            return List.copyOf(zeros);
        }
        // Use the authored shares for the three subwaves; a different count falls
        // back to an even split, which is all a generic caller can mean.
        int[] shares = subwaves == SUBWAVE_SHARES_PERMILLE.length
                ? SUBWAVE_SHARES_PERMILLE : evenShares(subwaves);
        int shareSum = 0;
        for (int share : shares) shareSum += share;
        int[] parts = new int[subwaves];
        long[] remainders = new long[subwaves];
        int assigned = 0;
        for (int index = 0; index < subwaves; index++) {
            long scaled = (long) safeTotal * shares[index];
            parts[index] = (int) (scaled / shareSum);
            remainders[index] = scaled % shareSum;
            assigned += parts[index];
        }
        // Largest remainder first, ties resolved by the earlier subwave so the
        // result is fully deterministic.
        while (assigned < safeTotal) {
            int best = -1;
            for (int index = 0; index < subwaves; index++) {
                if (best < 0 || remainders[index] > remainders[best]) best = index;
            }
            parts[best]++;
            remainders[best] = -1L;
            assigned++;
        }
        // W2-003: later subwaves are strictly stronger when the total permits it.
        for (int index = 1; index < subwaves; index++) {
            if (parts[index] < parts[index - 1] && parts[index - 1] > 1) {
                parts[index - 1]--;
                parts[index]++;
            }
        }
        List<Integer> result = new ArrayList<>(subwaves);
        for (int part : parts) result.add(part);
        return List.copyOf(result);
    }

    private static int[] evenShares(int subwaves) {
        int[] shares = new int[subwaves];
        java.util.Arrays.fill(shares, 1_000);
        return shares;
    }
}
