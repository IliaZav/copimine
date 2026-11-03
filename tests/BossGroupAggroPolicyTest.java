import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.BossGroupAggroPolicy;
import me.copimine.endevent.domain.BossGroupAggroPolicy.Candidate;
import me.copimine.endevent.domain.BossGroupAggroPolicy.Decision;
import me.copimine.endevent.domain.BossGroupAggroPolicy.Outcome;

/** The boss must fight the group, not a queue. */
public final class BossGroupAggroPolicyTest {
    private static int failures;

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    public static void main(String[] args) {
        noCandidatesClears();
        deadAndIneligibleAreIgnored();
        heldLockIsKeptWithoutPressure();
        recentAttackerEarnsRetaliationLateInTheLock();
        retaliationIsRateLimited();
        attackerTooOldIsNotRetaliated();
        expiredLockPrefersTheAttacker();
        droppedTargetIsReplacedImmediately();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("BossGroupAggroPolicyTest OK");
    }

    private static void noCandidatesClears() {
        Outcome outcome = BossGroupAggroPolicy.evaluate(List.of(), A, 1_000L, 0L, 5_000L, 0L);
        check(outcome.decision() == Decision.CLEAR, "an empty party must clear the target");
        check(outcome.target() == null, "a cleared target is null");
    }

    private static void deadAndIneligibleAreIgnored() {
        List<Candidate> pool = List.of(
                new Candidate(A, false, true, 0L),
                new Candidate(B, true, false, 0L));
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, null, 1_000L, 0L, 0L, 0L);
        check(outcome.decision() == Decision.CLEAR,
                "dead or ineligible players must not be chosen");
    }

    private static void heldLockIsKeptWithoutPressure() {
        List<Candidate> pool = List.of(
                new Candidate(A, true, true, 0L),
                new Candidate(B, true, true, 0L));
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 1_000L, 0L, 9_000L, 0L);
        check(outcome.decision() == Decision.HOLD, "an uncontested lock must hold");
        check(A.equals(outcome.target()), "the held target stays the focus");
    }

    private static void recentAttackerEarnsRetaliationLateInTheLock() {
        // Lock ends at 10s, so half the lock is 5s: an attack at 8s is inside
        // the allowed cut and must be answered.
        List<Candidate> pool = List.of(
                new Candidate(A, true, true, 0L),
                new Candidate(B, true, true, 8_000L));
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 8_000L, 0L, 10_000L, 0L);
        check(outcome.decision() == Decision.RETALIATE,
                "a fresh attacker late in the lock must be answered, was " + outcome.decision());
        check(B.equals(outcome.target()), "retaliation targets the attacker");
    }

    private static void retaliationIsRateLimited() {
        List<Candidate> pool = List.of(
                new Candidate(A, true, true, 0L),
                new Candidate(B, true, true, 8_000L));
        // Last retaliation 1s ago: below the cooldown, so the boss must not thrash.
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 8_000L, 7_500L, 10_000L, 0L);
        check(outcome.decision() == Decision.HOLD,
                "retaliation must respect its cooldown, was " + outcome.decision());
    }

    private static void attackerTooOldIsNotRetaliated() {
        List<Candidate> pool = List.of(
                new Candidate(A, true, true, 0L),
                new Candidate(B, true, true, 1_000L));
        // B hit 9 seconds ago: outside the retaliation window.
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 10_000L, 0L, 30_000L, 0L);
        check(outcome.decision() == Decision.HOLD,
                "a stale attacker is not a retaliation trigger, was " + outcome.decision());
    }

    private static void expiredLockPrefersTheAttacker() {
        List<Candidate> pool = List.of(
                new Candidate(A, true, true, 0L),
                new Candidate(B, true, true, 9_500L));
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 10_000L, 0L, 10_000L, 0L);
        check(outcome.decision() == Decision.RETALIATE,
                "once the lock expires the attacker becomes the focus");
        check(outcome.newLockUntilMillis() > 10_000L,
                "switching installs a fresh lock");
    }

    private static void droppedTargetIsReplacedImmediately() {
        List<Candidate> pool = List.of(
                new Candidate(B, true, true, 0L),
                new Candidate(C, true, true, 0L));
        Outcome outcome = BossGroupAggroPolicy.evaluate(pool, A, 1_000L, 0L, 9_000L, 0L);
        check(outcome.decision() == Decision.SPREAD,
                "a target that left the party must be replaced at once");
        check(outcome.target() != null && !A.equals(outcome.target()),
                "the replacement is an eligible player");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }
}
