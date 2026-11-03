import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7TrialTargetPolicy;
import me.copimine.endevent.domain.Wave7TrialTargetPolicy.Candidate;

/** A Wave 7 miniboss sweeps the party instead of camping one victim. */
public final class Wave7TrialTargetPolicyTest {
    private static int failures;

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    public static void main(String[] args) {
        emptyPoolPicksNobody();
        closestWinsOnTheFirstSweep();
        neverRepeatsImmediately();
        leastRecentlyHitWinsAfterTheSweep();
        invalidCandidatesAreIgnored();
        fullSweepOverThreeAttacks();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("Wave7TrialTargetPolicyTest OK");
    }

    private static void emptyPoolPicksNobody() {
        check(Wave7TrialTargetPolicy.choose(List.of(), null, 1L) == null,
                "no eligible players means no target");
        check(Wave7TrialTargetPolicy.choose(null, null, 1L) == null,
                "a null pool means no target");
    }

    private static void closestWinsOnTheFirstSweep() {
        List<Candidate> pool = List.of(
                new Candidate(A, 100.0D, Wave7TrialTargetPolicy.NEVER),
                new Candidate(B, 4.0D, Wave7TrialTargetPolicy.NEVER));
        check(B.equals(Wave7TrialTargetPolicy.choose(pool, null, 1L)),
                "with nobody hit yet the nearest player is chosen first");
    }

    private static void neverRepeatsImmediately() {
        // A was hit last attack and stands closer; B must be answered.
        List<Candidate> pool = List.of(
                new Candidate(A, 1.0D, 5L),
                new Candidate(B, 9.0D, Wave7TrialTargetPolicy.NEVER));
        check(B.equals(Wave7TrialTargetPolicy.choose(pool, A, 6L)),
                "a never-hit player outranks the previous target");
    }

    private static void leastRecentlyHitWinsAfterTheSweep() {
        // Everyone has been hit: the one hit longest ago must be answered.
        List<Candidate> pool = List.of(
                new Candidate(A, 1.0D, 9L),
                new Candidate(B, 2.0D, 2L),
                new Candidate(C, 3.0D, 6L));
        check(B.equals(Wave7TrialTargetPolicy.choose(pool, A, 10L)),
                "the least recently hit player is chosen, got "
                        + Wave7TrialTargetPolicy.choose(pool, A, 10L));
    }

    private static void invalidCandidatesAreIgnored() {
        List<Candidate> pool = List.of(
                new Candidate(A, Double.NaN, Wave7TrialTargetPolicy.NEVER),
                new Candidate(B, -4.0D, Wave7TrialTargetPolicy.NEVER),
                new Candidate(C, 30.0D, Wave7TrialTargetPolicy.NEVER));
        check(C.equals(Wave7TrialTargetPolicy.choose(pool, null, 1L)),
                "impossible distances are not candidates");
    }

    private static void fullSweepOverThreeAttacks() {
        // Three consecutive attacks must not all land on the same nearest player.
        UUID first = A, second, third;
        second = Wave7TrialTargetPolicy.choose(List.of(
                new Candidate(A, 1.0D, 1L),
                new Candidate(B, 5.0D, Wave7TrialTargetPolicy.NEVER),
                new Candidate(C, 9.0D, Wave7TrialTargetPolicy.NEVER)), first, 2L);
        check(second != null && !second.equals(first), "the second attack changes target");
        third = Wave7TrialTargetPolicy.choose(List.of(
                new Candidate(A, 1.0D, 1L),
                new Candidate(B, 5.0D, 2L),
                new Candidate(C, 9.0D, Wave7TrialTargetPolicy.NEVER)), second, 3L);
        check(C.equals(third), "the third attack reaches the last untouched player, got " + third);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }
}
