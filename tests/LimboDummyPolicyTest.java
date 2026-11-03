import me.copimine.endevent.domain.wave7.EchoRealmAssignmentPolicy;
import me.copimine.endevent.domain.wave7.LimboDummyPolicy;

/** The Limbo rehearsal bodies: how many a two-player night needs and where they stand. */
public final class LimboDummyPolicyTest {
    private static int checks;

    private LimboDummyPolicyTest() {
    }

    public static void main(String[] args) {
        theThresholdIsTheAuthoredOne();
        twoPlayersNeedThirteen();
        crossingTheThreshold();
        theRingKeepsBodiesApart();
        theRingIsDeterministic();
        lifetimeExpiresExactlyAtTheDeadline();
        clampRefusesNonsense();
        expectationMentionsTheOnlyTwoRealOutcomes();
        describeReportsTheThreshold();
        System.out.println("LimboDummyPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theThresholdIsTheAuthoredOne() {
        check("limbo threshold mirrors Echo policy",
                LimboDummyPolicy.limboCount() == EchoRealmAssignmentPolicy.LIMBO_THRESHOLD);
        check("limbo threshold is fifteen", LimboDummyPolicy.limboCount() == 15);
    }

    private static void twoPlayersNeedThirteen() {
        check("two players need thirteen", LimboDummyPolicy.dummiesNeeded(2) == 13);
        check("one player needs fourteen", LimboDummyPolicy.dummiesNeeded(1) == 14);
        check("fourteen players need one", LimboDummyPolicy.dummiesNeeded(14) == 1);
        check("fifteen players need none", LimboDummyPolicy.dummiesNeeded(15) == 0);
        check("twenty players need none", LimboDummyPolicy.dummiesNeeded(20) == 0);
        check("negative players cannot overflow", LimboDummyPolicy.dummiesNeeded(-4) == 15);
    }

    private static void crossingTheThreshold() {
        check("two plus thirteen crosses", LimboDummyPolicy.reachesLimbo(2, 13));
        check("two plus twelve does not", !LimboDummyPolicy.reachesLimbo(2, 12));
        check("exactly fifteen crosses", LimboDummyPolicy.reachesLimbo(15, 0));
        check("fourteen alone does not", !LimboDummyPolicy.reachesLimbo(14, 0));
        check("zero real needs fifteen", LimboDummyPolicy.reachesLimbo(0, 15));
        check("zero real and fourteen does not", !LimboDummyPolicy.reachesLimbo(0, 14));
    }

    private static void theRingKeepsBodiesApart() {
        int total = 15;
        for (int first = 0; first < total; first++) {
            for (int second = first + 1; second < total; second++) {
                double[] a = LimboDummyPolicy.offsetOf(first, total);
                double[] b = LimboDummyPolicy.offsetOf(second, total);
                double gap = Math.hypot(a[0] - b[0], a[1] - b[1]);
                check("bodies keep a rune of space " + first + "/" + second,
                        gap >= LimboDummyPolicy.SPACING_BLOCKS);
            }
        }
        check("spacing clears the occupancy radius", LimboDummyPolicy.SPACING_BLOCKS > 1.35D);
    }

    private static void theRingIsDeterministic() {
        for (int index = 0; index < 15; index++) {
            double[] once = LimboDummyPolicy.offsetOf(index, 15);
            double[] twice = LimboDummyPolicy.offsetOf(index, 15);
            check("offset is stable " + index, once[0] == twice[0] && once[1] == twice[1]);
        }
        double[] lone = LimboDummyPolicy.offsetOf(0, 1);
        check("a lone body sits on one spot", lone[0] == 0.0D && lone[1] == 0.0D);
        double[] outOfRange = LimboDummyPolicy.offsetOf(99, 15);
        double[] last = LimboDummyPolicy.offsetOf(14, 15);
        check("an out-of-range index clamps", outOfRange[0] == last[0] && outOfRange[1] == last[1]);
    }

    private static void lifetimeExpiresExactlyAtTheDeadline() {
        long start = 1_000L;
        check("alive just before the deadline",
                !LimboDummyPolicy.expired(start, start + LimboDummyPolicy.LIFETIME_TICKS - 1L));
        check("expired on the deadline",
                LimboDummyPolicy.expired(start, start + LimboDummyPolicy.LIFETIME_TICKS));
        check("expired long after",
                LimboDummyPolicy.expired(start, start + 10 * LimboDummyPolicy.LIFETIME_TICKS));
    }

    private static void clampRefusesNonsense() {
        check("zero becomes one", LimboDummyPolicy.clampCount(0) == LimboDummyPolicy.MIN_DUMMIES);
        check("negative becomes one", LimboDummyPolicy.clampCount(-7) == LimboDummyPolicy.MIN_DUMMIES);
        check("ten stays ten", LimboDummyPolicy.clampCount(10) == 10);
        check("a hundred clamps to the ceiling", LimboDummyPolicy.clampCount(100) == LimboDummyPolicy.MAX_DUMMIES);
        check("the ceiling is the limbo count", LimboDummyPolicy.MAX_DUMMIES == LimboDummyPolicy.limboCount());
    }

    private static void expectationMentionsTheOnlyTwoRealOutcomes() {
        String text = LimboDummyPolicy.expectation();
        check("expectation mentions the threshold", text.contains("15"));
        check("expectation says what seeing everyone means", text.contains("видите всех"));
        check("expectation says what seeing nobody means", text.contains("не видите никого"));
    }

    private static void describeReportsTheThreshold() {
        check("describe marks a crossing party", LimboDummyPolicy.describe(2, 13).contains("лимбо включится"));
        check("describe marks a short party", LimboDummyPolicy.describe(2, 5).contains("мало для лимбо"));
        check("describe counts the total", LimboDummyPolicy.describe(2, 13).contains("всего=15"));
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
