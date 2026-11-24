import java.util.List;

import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Acquire;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Advance;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Bind;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Cleanup;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Resolve;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.ResourceKind;
import me.copimine.endevent.domain.CinematicEffectCoordinatorPolicy.Stage;

public final class CinematicEffectCoordinatorPolicyTest {
    public static void main(String[] args) {
        testAcquireIsOneLiveLeasePerOwner();
        testAcquireValidatesAndBounds();
        testStageLadderIsMonotoneAndNeverSkips();
        testOverrunForcesCleanupInsteadOfMoreAnimation();
        testResultSettlesOnceAndOnlyWhileExecuting();
        testResourceLedgerIsBoundedAndDeterministicallyOrdered();
        testReleaseIsOnceOnlyAndFreesTheLease();
        testRepeatedCastsDoNotLeakLeases();
        testReducedMotionKeepsTheContract();
        testNoCameraTakeover();
        System.out.println("CinematicEffectCoordinatorPolicyTest OK");
    }

    private static void testAcquireIsOneLiveLeasePerOwner() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        check(coordinator.acquire("boss", "swipe", 100L, 40, false) == Acquire.ACCEPTED,
                "the first cast takes the owner lease");
        check(coordinator.stage("boss") == Stage.SELECT, "a fresh cast starts at SELECT");
        check(coordinator.acquire("boss", "slam", 101L, 40, false) == Acquire.BUSY,
                "a second cast may not replace a committed animation");
        check("swipe".equals(coordinator.actionId("boss")),
                "the refused cast changed nothing");
        check(coordinator.acquire("miniboss", "volley", 101L, 40, false) == Acquire.ACCEPTED,
                "a different owner still acquires its own lease");
        check(coordinator.liveCount() == 2, "two owners hold two leases");
    }

    private static void testAcquireValidatesAndBounds() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        check(coordinator.acquire("", "swipe", 0L, 10, false) == Acquire.INVALID,
                "a blank owner is invalid");
        check(coordinator.acquire("boss", "  ", 0L, 10, false) == Acquire.INVALID,
                "a blank action is invalid");
        check(coordinator.acquire("boss", "swipe", -1L, 10, false) == Acquire.INVALID,
                "a negative start tick is invalid");
        check(coordinator.acquire("boss", "swipe", 0L, 0, false) == Acquire.INVALID,
                "a zero duration is invalid");
        check(coordinator.acquire("boss", "swipe", 0L,
                        CinematicEffectCoordinatorPolicy.MAX_DURATION_TICKS + 1, false) == Acquire.INVALID,
                "a duration past the cap must fail closed");
        for (int i = 0; i < CinematicEffectCoordinatorPolicy.MAX_OWNERS; i++) {
            check(coordinator.acquire("owner-" + i, "cast", 0L, 10, false) == Acquire.ACCEPTED,
                    "owners up to the cap are accepted");
        }
        check(coordinator.acquire("owner-over", "cast", 0L, 10, false) == Acquire.INVALID,
                "the owner cap fails closed instead of growing without bound");
    }

    private static void testStageLadderIsMonotoneAndNeverSkips() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        coordinator.acquire("boss", "swipe", 0L, 100, false);
        check(coordinator.advance("boss", Stage.LOCK, 1L) == Advance.OUT_OF_ORDER,
                "skipping WARN must be refused");
        check(coordinator.advance("boss", Stage.WARN, 1L) == Advance.ADVANCED, "WARN advances");
        check(coordinator.advance("boss", Stage.WARN, 2L) == Advance.ALREADY_SETTLED,
                "replaying the current stage is idempotent, not a second run");
        check(coordinator.advance("boss", Stage.SELECT, 3L) == Advance.OUT_OF_ORDER,
                "the ladder never walks backwards");
        check(coordinator.advance("boss", Stage.LOCK, 4L) == Advance.ADVANCED, "LOCK advances");
        check(coordinator.advance("boss", Stage.EXECUTE, 5L) == Advance.ADVANCED, "EXECUTE advances");
        check(coordinator.advance("nobody", Stage.RECOVERY, 6L) == Advance.UNKNOWN_OWNER,
                "an unknown owner cannot advance anything");
    }

    private static void testOverrunForcesCleanupInsteadOfMoreAnimation() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        coordinator.acquire("boss", "slam", 0L, 20, false);
        coordinator.advance("boss", Stage.WARN, 1L);
        check(coordinator.remainingTicks("boss", 1L) == 19L, "remaining ticks count down");
        check(!coordinator.expired("boss", 20L), "the deadline tick itself is not overrun");
        check(coordinator.expired("boss", 21L), "one tick past the deadline is overrun");
        check(coordinator.advance("boss", Stage.LOCK, 21L) == Advance.EXPIRED,
                "an overrunning cast cannot keep animating the next stage");
        check(coordinator.stage("boss") == Stage.WARN, "a refused advance changes nothing");
        check(coordinator.advance("boss", Stage.CLEANUP, 21L) == Advance.OUT_OF_ORDER,
                "even cleanup must not skip the ladder from WARN");
        check(coordinator.remainingTicks("boss", 999L) == 0L, "remaining ticks never go negative");
    }

    private static void testResultSettlesOnceAndOnlyWhileExecuting() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        coordinator.acquire("boss", "ambush", 0L, 100, false);
        check(coordinator.resolve("boss", CinematicEffectCoordinatorPolicy.Result.HIT)
                        == Resolve.NOT_EXECUTING,
                "a cast cannot settle before it executes");
        coordinator.advance("boss", Stage.WARN, 1L);
        coordinator.advance("boss", Stage.LOCK, 2L);
        coordinator.advance("boss", Stage.EXECUTE, 3L);
        check(coordinator.result("boss") == CinematicEffectCoordinatorPolicy.Result.PENDING,
                "a committed cast starts unresolved");
        check(coordinator.resolve("boss", CinematicEffectCoordinatorPolicy.Result.MISS)
                        == Resolve.SETTLED,
                "the first outcome settles the cast");
        check(coordinator.resolve("boss", CinematicEffectCoordinatorPolicy.Result.HIT)
                        == Resolve.ALREADY_SETTLED,
                "a later contradictory outcome must not overwrite the first");
        check(coordinator.result("boss") == CinematicEffectCoordinatorPolicy.Result.MISS,
                "the settled outcome never changes");
        check(coordinator.resolve("nobody", CinematicEffectCoordinatorPolicy.Result.HIT)
                        == Resolve.UNKNOWN_OWNER,
                "an unknown owner cannot settle anything");
    }

    private static void testResourceLedgerIsBoundedAndDeterministicallyOrdered() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        coordinator.acquire("boss", "beam", 0L, 100, false);
        check(coordinator.bind("boss", ResourceKind.AUDIO, "zap") == Bind.BOUND, "audio binds");
        check(coordinator.bind("boss", ResourceKind.AUDIO, "zap") == Bind.ALREADY_BOUND,
                "the same kind/id binds once");
        check(coordinator.bind("boss", ResourceKind.MESH, "guardian_beam") == Bind.BOUND, "mesh binds");
        check(coordinator.bind("boss", ResourceKind.RIG, "kagune_main") == Bind.BOUND, "rig binds");
        check(coordinator.bind("boss", ResourceKind.SHADER, " ") == Bind.INVALID,
                "a blank resource id is invalid");
        check(coordinator.bind("nobody", ResourceKind.MESH, "x") == Bind.UNKNOWN_OWNER,
                "an unknown owner cannot bind");

        List<String> plan = coordinator.cleanupPlan("boss");
        check(plan.equals(List.of("MESH:guardian_beam", "RIG:kagune_main", "AUDIO:zap")),
                "cleanup is ordered by resource kind, then by id: " + plan);

        while (coordinator.boundCount("boss") < CinematicEffectCoordinatorPolicy.MAX_RESOURCES_PER_CAST) {
            check(coordinator.bind("boss", ResourceKind.LIGHT, "l" + coordinator.boundCount("boss"))
                            == Bind.BOUND,
                    "the ledger accepts resources up to the cap");
        }
        check(coordinator.bind("boss", ResourceKind.LIGHT, "overflow") == Bind.OVER_BUDGET,
                "the resource budget fails closed");
    }

    private static void testReleaseIsOnceOnlyAndFreesTheLease() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        coordinator.acquire("boss", "beam", 0L, 100, false);
        coordinator.bind("boss", ResourceKind.MESH, "beam_mesh");
        coordinator.bind("boss", ResourceKind.SHADER, "beam_shader");

        CinematicEffectCoordinatorPolicy.Release first = coordinator.release("boss");
        check(first.status() == Cleanup.RELEASED, "the first release reports RELEASED");
        check(first.resources().equals(List.of("MESH:beam_mesh", "SHADER:beam_shader")),
                "release returns the exact ordered plan: " + first.resources());
        check(coordinator.stage("boss") == Stage.DONE, "a released cast is DONE");
        check(coordinator.releasedCount() == 1, "one release is counted");
        check(!coordinator.hasLiveLease("boss"), "the owner slot is free again");

        CinematicEffectCoordinatorPolicy.Release again = coordinator.release("boss");
        check(again.status() == Cleanup.ALREADY_RELEASED, "a retried release is idempotent");
        check(again.resources().isEmpty(), "a retried release frees nothing a second time");
        check(coordinator.releasedCount() == 1, "the retry is not counted as a release");
        check(coordinator.release("nobody").status() == Cleanup.UNKNOWN_OWNER,
                "releasing an unknown owner is harmless");
    }

    private static void testRepeatedCastsDoNotLeakLeases() {
        CinematicEffectCoordinatorPolicy coordinator = new CinematicEffectCoordinatorPolicy();
        for (int i = 0; i < 50; i++) {
            check(coordinator.acquire("boss", "cast-" + i, i * 10L, 10, false) == Acquire.ACCEPTED,
                    "a freed owner slot can start the next cast");
            coordinator.bind("boss", ResourceKind.MESH, "m" + i);
            coordinator.advance("boss", Stage.WARN, i * 10L + 1L);
            coordinator.advance("boss", Stage.LOCK, i * 10L + 2L);
            coordinator.advance("boss", Stage.EXECUTE, i * 10L + 3L);
            coordinator.resolve("boss", CinematicEffectCoordinatorPolicy.Result.HIT);
            coordinator.advance("boss", Stage.RECOVERY, i * 10L + 4L);
            coordinator.advance("boss", Stage.CLEANUP, i * 10L + 5L);
            check(coordinator.release("boss").status() == Cleanup.RELEASED,
                    "every cast releases exactly once");
        }
        check(coordinator.liveCount() == 0, "no lease survives a fifty-cast cycle");
        check(coordinator.releasedCount() == 50, "all fifty releases are accounted for");
        check(coordinator.cleanupPlan("boss").isEmpty(), "no resource survives the cycle");
    }

    private static void testReducedMotionKeepsTheContract() {
        List<Stage> full = CinematicEffectCoordinatorPolicy.authoredStages(false);
        List<Stage> reduced = CinematicEffectCoordinatorPolicy.authoredStages(true);
        check(full.contains(Stage.WARN) && full.contains(Stage.LOCK) && full.contains(Stage.CLEANUP),
                "the full timeline always carries the warning, lock and cleanup");
        check(reduced.contains(Stage.WARN), "reduced motion never drops the dodge warning");
        check(reduced.contains(Stage.LOCK), "reduced motion keeps the committed target lock");
        check(reduced.contains(Stage.CLEANUP), "reduced motion still proves the effect ended");
        check(!reduced.contains(Stage.EXECUTE) && !reduced.contains(Stage.RECOVERY),
                "reduced motion drops the flourish stages");
        check(reduced.size() < full.size(), "reduced motion authors strictly less");
        check(CinematicEffectCoordinatorPolicy.flourishLayers(false) > 0,
                "the full timeline has flourish layers");
        check(CinematicEffectCoordinatorPolicy.flourishLayers(true) == 0,
                "reduced motion has no flourish layers");
    }

    private static void testNoCameraTakeover() {
        check(!CinematicEffectCoordinatorPolicy.allowsCameraTakeover(),
                "a shared cinematic must never seize the camera");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
