import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.BossPerceptionSnapshot;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossTargetPolicy;
import me.copimine.endevent.domain.BossTargetSelector;

public final class BossTargetPolicyTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    public static void main(String[] args) {
        BossTargetPolicy.TargetDecision locked = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 1_000L, 6_000L, 0L);
        check(locked.target().equals(FIRST), "a valid target must remain during its lock window");
        check(locked.newLockUntilMillis() == 6_000L, "a retained target must keep its lock deadline");

        BossTargetPolicy.TargetDecision expired = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 6_000L, 6_000L, 0L);
        check(expired.target().equals(SECOND), "an expired two-player lock must switch once, not oscillate");
        check(expired.newLockUntilMillis() == 17_000L,
                "a newly selected target must receive one bounded lock window");
        check(BossTargetPolicy.DEFAULT_LOCK_MILLIS >= 10_000L
                        && BossTargetPolicy.DEFAULT_LOCK_MILLIS <= 12_000L,
                "the default primary-focus lock must stay inside the spec's 10-12 s window");
        BossTargetPolicy.TargetDecision stolen = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 2_000L, 11_000L, 0L);
        check(stolen.target().equals(FIRST),
                "a single hit from a new player must not steal aggro inside the lock");

        BossTargetPolicy.TargetDecision invalid = BossTargetPolicy.chooseTarget(
                List.of(SECOND, THIRD), FIRST, List.of(FIRST), 2_000L, 10_000L, 0L);
        check(invalid.target().equals(SECOND), "an invalid target must be replaced immediately");
        check(BossTargetPolicy.chooseTarget(List.of(), FIRST, List.of(), 2_000L, 10_000L, 0L).target() == null,
                "an empty roster must produce no target");

        BossTargetPolicy.TargetDecision first = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), null, List.of(), 0L, 0L, 0L);
        BossTargetPolicy.TargetDecision held = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), first.target(), List.of(first.target()), 2_500L,
                first.newLockUntilMillis(), 1L);
        check(held.target().equals(first.target()), "refreshes must not ping-pong between two players");
        BossTargetPolicy.TargetDecision next = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), held.target(), List.of(held.target()),
                held.newLockUntilMillis(), held.newLockUntilMillis(), 1L);
        check(!next.target().equals(held.target()), "the next choice may change only after the lock expires");

        BossTargetPolicy.TargetDecision deterministicA = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND, THIRD), null, List.of(), 100L, 0L, 1L);
        BossTargetPolicy.TargetDecision deterministicB = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND, THIRD), null, List.of(), 100L, 0L, 1L);
        check(deterministicA.equals(deterministicB), "the same roster and cursor must be deterministic");
        testPrimaryTargetStaysLockedForElevenSeconds();
        testSpellSelectionIsIndependentOfThePursuitLock();
        testStackedAndSurroundedUseWalkableHorizontalPosition();
        testEqualScoreTiesRotateFairly();
        System.out.println("BossTargetPolicyTest OK");
    }

    private static void testPrimaryTargetStaysLockedForElevenSeconds() {
        BossTargetPolicy.TargetDecision locked = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(), 10_000L, 21_000L, 0L, 11_000L);
        check(locked.target().equals(FIRST), "an 11 s lock must keep the primary target");
        check(locked.newLockUntilMillis() == 21_000L, "an 11 s lock must keep its deadline");
        BossTargetPolicy.TargetDecision rotated = BossTargetPolicy.chooseTarget(
                List.of(FIRST, SECOND), FIRST, List.of(FIRST), 21_000L, 21_000L, 0L, 11_000L);
        check(!rotated.target().equals(FIRST), "the primary target may change only after 11 s");
        check(rotated.newLockUntilMillis() == 32_000L, "a rotated target gets a fresh 11 s window");
    }

    private static void testSpellSelectionIsIndependentOfThePursuitLock() {
        var weak = observation(FIRST, 0.9D, 6.0D, false, 0.0D, 0.0D, 0.0D);
        var attractive = observation(SECOND, 0.2D, 2.0D, true, 3.0D, 0.0D, 0.0D);
        BossPerceptionSnapshot snapshot = snapshot(weak, attractive);
        // The pursuit lock owns navigation (BossTargetPolicy), never spell choice.
        // A spell pick that stays glued to the locked player withholds every ranged
        // attack from a genuinely better target, so the selector must be free to leave.
        var lockedOut = BossTargetSelector.choose(snapshot, FIRST);
        check(lockedOut.target().equals(SECOND),
                "spell selection must be free to leave the locked pursuit target");
        check(lockedOut.reason().equals("ISOLATED_OPPORTUNITY"),
                "an isolated weak player is the spell opportunity, not the locked player");
        check(BossTargetSelector.choose(snapshot, null).target().equals(SECOND),
                "without a lock the fairness score still selects the isolated weak player");
        check(BossTargetSelector.choose(snapshot, THIRD).target().equals(SECOND),
                "a stale lock target is ignored and scoring resumes");

        // Leaving the lock must not mean the lock is ignored entirely: repeating the
        // same spell target still carries a preference. Measure it on a sole player,
        // where the same player is returned either way, so the delta is the nudge.
        BossPerceptionSnapshot solo = snapshot(weak);
        double bareSolo = BossTargetSelector.choose(solo, null).score();
        var nudgedSolo = BossTargetSelector.choose(solo, FIRST);
        check(nudgedSolo.target().equals(FIRST),
                "the bounded nudge must not remove the locked player from spell eligibility");
        double nudge = bareSolo - nudgedSolo.score();
        check(nudge > 0.0D, "repeating the locked spell target must still cost a bounded preference");
        // Bounded is the whole point: an unbounded or dominant penalty would restore
        // the monopoly this contract removed, so the preference may never exceed the
        // fairness margin it is allowed to weigh.
        check(nudge <= 1.0D,
                "the locked-target preference must stay bounded, not dominate the fairness score");
    }

    private static void testStackedAndSurroundedUseWalkableHorizontalPosition() {
        var ground = observation(FIRST, 1.0D, 5.0D, false, 0.0D, 0.0D, 0.0D);
        var ledge = observation(SECOND, 1.0D, 5.0D, false, 0.5D, 25.0D, 0.5D);
        check(snapshot(ground, ledge).partyStacked(),
                "a player on a ledge above the stack is still stacked for ground combat");
        var far = observation(SECOND, 1.0D, 5.0D, false, 30.0D, 0.0D, 0.0D);
        check(!snapshot(ground, far).partyStacked(), "a spread-out party is not stacked");
        var sameSideA = observation(FIRST, 1.0D, 3.0D, false, 3.0D, 0.0D, 0.5D);
        var sameSideB = observation(SECOND, 1.0D, 3.0D, false, 2.0D, 0.0D, -0.5D);
        check(!snapshot(sameSideA, sameSideB).bossSurrounded(),
                "two players on the same side are pressure, not an encirclement");
        var opposite = observation(SECOND, 1.0D, 3.0D, false, -3.0D, 0.0D, 0.0D);
        check(snapshot(sameSideA, opposite).bossSurrounded(),
                "players on opposing sides surround the boss");
    }

    private static void testEqualScoreTiesRotateFairly() {
        // Three identical observers all score exactly the same. The selector must
        // not hand every ambiguous decision to the lexicographically largest UUID;
        // successive cursors must rotate the choice across all equally valid players.
        var a = observation(FIRST, 1.0D, 5.0D, false, 0.0D, 0.0D, 0.0D);
        var b = observation(SECOND, 1.0D, 5.0D, false, 0.0D, 0.0D, 0.0D);
        var c = observation(THIRD, 1.0D, 5.0D, false, 0.0D, 0.0D, 0.0D);
        BossPerceptionSnapshot trio = snapshot(a, b, c);
        var firstPick = BossTargetSelector.choose(trio, null, 0).target();
        var secondPick = BossTargetSelector.choose(trio, null, 1).target();
        var thirdPick = BossTargetSelector.choose(trio, null, 2).target();
        java.util.Set<UUID> distinct = new java.util.HashSet<>(java.util.List.of(
                firstPick, secondPick, thirdPick));
        check(distinct.size() == 3,
                "equal-score ties must rotate across all players, not always pick the UUID maximum");
        check(BossTargetSelector.choose(trio, null, 0).target().equals(firstPick),
                "tie rotation must stay deterministic for a repeated cursor");
    }

    private static BossPerceptionSnapshot snapshot(BossPerceptionSnapshot.PlayerObservation... players) {
        return new BossPerceptionSnapshot(BossPhase.RIFT, null, List.of(players),
                new BossPerceptionSnapshot.Position(0.0D, 0.0D, 0.0D), null, "", 1L, false);
    }

    private static BossPerceptionSnapshot.PlayerObservation observation(UUID id, double healthFraction,
            double distanceToBoss, boolean isolated, double x, double y, double z) {
        return new BossPerceptionSnapshot.PlayerObservation(id, true, healthFraction, distanceToBoss,
                true, isolated, 2.0D, false, false, isolated, x, y, z,
                0.0D, 0.0D, 0.0D, "");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
