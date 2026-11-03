import me.copimine.endevent.domain.WaveMechanicsPolicy;
import me.copimine.endevent.domain.WaveScalingPolicy;
import me.copimine.endevent.domain.WaveTwoRosterPolicy;
import me.copimine.endevent.domain.WaveTwoStagePolicy;

import java.util.List;

/**
 * Audit wave 2: the RIFT_HUNT subwaves ESCALATE by the authored 25/33/42 shares.
 *
 * <p>W2-003 requires later subwaves to awaken more actors than earlier ones, and
 * W2-011 gives the ordinary budget as approximately 25% / 33% / 42%. The shipped
 * split was two equal halves plus a flat eight-actor reserve, so the escalation
 * never existed.</p>
 */
public final class WaveTwoRosterPolicyTest {
    public static void main(String[] args) {
        WaveTwoRosterPolicyTest test = new WaveTwoRosterPolicyTest();
        test.sharesAreTheAuthoredOnes();
        test.splitEscalatesAndSumsToTheParty();
        test.smallPartiesStillKeepThreeSubwaves();
        test.rosterPolicyComposesAPlayableHunt();
        test.authoredNonReferenceRosterIsPreserved();
        System.out.println("WaveTwoRosterPolicyTest OK");
    }

    private static WaveMechanicsPolicy.WaveCounts shipped() {
        return new WaveMechanicsPolicy.WaveCounts(36, 36, 24, 0, 0);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void sharesAreTheAuthoredOnes() {
        int[] shares = WaveTwoStagePolicy.SUBWAVE_SHARES_PERMILLE;
        check(shares.length == 3, "wave 2 has exactly three subwaves");
        check(shares[0] == 250 && shares[1] == 330 && shares[2] == 420,
                "the authored shares are 25%, 33% and 42%");
        check(shares[0] < shares[1] && shares[1] < shares[2],
                "later subwaves carry a larger share");
    }

    private void splitEscalatesAndSumsToTheParty() {
        // At a party of 48 the shares land exactly on 12/16/20 = 25%/33%/42%.
        int[] exact = WaveTwoStagePolicy.distributeByAuthoredShares(48, 3);
        check(exact[0] == 12 && exact[1] == 16 && exact[2] == 20,
                "48 actors split exactly 12/16/20");
        // Integer division truncates 16/48 = 33.3% and 20/48 = 41.7%, so compare
        // the rounded percentages the contract quotes.
        check(Math.round(12 * 100.0 / 48) == 25 && Math.round(16 * 100.0 / 48) == 33
                        && Math.round(20 * 100.0 / 48) == 42,
                "12/16/20 is the authored 25%/33%/42%");
        // For every party the split must sum to the party and escalate.
        for (int total = 3; total <= 120; total++) {
            int[] parts = WaveTwoStagePolicy.distributeByAuthoredShares(total, 3);
            int sum = parts[0] + parts[1] + parts[2];
            check(sum == total, "split lost actors at total " + total + ": " + sum);
            check(parts[0] <= parts[1] && parts[1] <= parts[2],
                    "later subwaves must not be weaker at total " + total);
            check(parts[0] > 0, "every subwave is non-empty at total " + total);
            // The final reserve is the strongest subwave but still bounded: it may
            // exceed the flat legacy budget only because escalation requires it.
            check(parts[2] <= (int) Math.ceil(total * 0.42D) + 1,
                    "the final reserve stays close to its authored share at total " + total);
        }
    }

    private void smallPartiesStillKeepThreeSubwaves() {
        // The shipped two-player party stays a valid three-subwave schedule.
        WaveMechanicsPolicy.WaveCounts reference =
                new WaveMechanicsPolicy.WaveCounts(21, 0, 0, 0, 0);
        List<WaveMechanicsPolicy.WaveCounts> groups = WaveTwoStagePolicy.split(reference);
        check(groups.size() == 3, "wave 2 is exactly three subwaves");
        int previous = 0;
        for (WaveMechanicsPolicy.WaveCounts group : groups) {
            check(group.total() > 0, "no subwave may be empty");
            check(group.total() >= previous, "subwaves must escalate: " + group.total());
            previous = group.total();
        }
        check(groups.get(2).total() <= WaveTwoStagePolicy.LATE_REINFORCEMENT_BUDGET,
                "the final reserve respects its bounded budget");
    }

    private void rosterPolicyComposesAPlayableHunt() {
        WaveMechanicsPolicy.WaveCounts composed =
                WaveTwoRosterPolicy.compose(shipped(), WaveScalingPolicy.MIN_PLAYERS);
        check(composed.total() == WaveTwoRosterPolicy.REFERENCE_TOTAL,
                "the reference party keeps the authored total: " + composed.total());
        check(composed.endermen() > 0 && composed.spiders() > 0 && composed.skeletons() > 0,
                "the hunt keeps endermen, spiders and skeletons");
        check(composed.eliteEndermen() >= 1 && composed.eliteEndermen() <= 2,
                "the hunt carries one or two real elites");
        // Wave 2 must never promote a ranged elite.
        check(WaveMechanicsPolicy.compositionForWave(2, composed).eliteSkeletons() == 0,
                "wave 2 must never promote a ranged elite");
        // Every roster size stays launchable.
        for (int players = 2; players <= 20; players++) {
            WaveMechanicsPolicy.WaveCounts party = WaveTwoRosterPolicy.compose(shipped(), players);
            check(WaveTwoStagePolicy.canStart(party),
                    "wave 2 must remain launchable at " + players + " players");
        }
    }

    private void authoredNonReferenceRosterIsPreserved() {
        WaveMechanicsPolicy.WaveCounts custom =
                new WaveMechanicsPolicy.WaveCounts(7, 3, 2, 0, 0);
        check(WaveTwoRosterPolicy.compose(custom, 12) == custom,
                "an operator's own roster must not be silently rewritten");
        check(WaveTwoRosterPolicy.compose(null, 12).total() == 0,
                "a null roster is handled, never thrown");
    }
}
