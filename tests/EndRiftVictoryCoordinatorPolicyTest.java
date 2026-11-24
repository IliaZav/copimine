import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.copimine.endevent.domain.EndRiftVictoryCoordinatorPolicy;
import me.copimine.endevent.domain.EndRiftVictoryCoordinatorPolicy.Advance;
import me.copimine.endevent.domain.EndRiftVictoryCoordinatorPolicy.Commit;
import me.copimine.endevent.domain.EndRiftVictoryCoordinatorPolicy.RewardGrant;
import me.copimine.endevent.domain.EndRiftVictoryCoordinatorPolicy.Stage;

public final class EndRiftVictoryCoordinatorPolicyTest {
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static void main(String[] args) {
        testDefeatCommitsExactlyOnceWithFrozenRoster();
        testDiagnosticKillNeverCommits();
        testStageLadderIsMonotoneAndNeverSkips();
        testRewardOperationIsOnceOnly();
        testEndUnlockIsOnceOnlyAndNeedsLiveReadBack();
        testCallbackGenerationIsFenced();
        testDurableRoundTripPreservesProgress();
        testCorruptProgressFailsClosed();
        System.out.println("EndRiftVictoryCoordinatorPolicyTest OK");
    }

    private static void testDefeatCommitsExactlyOnceWithFrozenRoster() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-1", 7L);
        check(coordinator.commitDefeat(true, Set.of(ALICE, BOB)) == Commit.COMMITTED,
                "the first legal lethal hit commits the defeat");
        check(coordinator.stage() == Stage.DEFEAT, "the stage opens at DEFEAT");
        check(coordinator.roster().equals(Set.of(ALICE, BOB)), "the official roster is frozen");
        // Two lethal hits in the same tick: the second must not rewind or re-roll.
        check(coordinator.commitDefeat(true, Set.of(ALICE)) == Commit.ALREADY_COMMITTED,
                "a second simultaneous lethal hit must not commit again");
        check(coordinator.roster().equals(Set.of(ALICE, BOB)), "the committed roster never changes");
    }

    private static void testDiagnosticKillNeverCommits() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-2", 3L);
        check(coordinator.commitDefeat(false, Set.of(ALICE)) == Commit.NOT_OFFICIAL,
                "an administrative/test kill is refused");
        check(coordinator.stage() == Stage.NONE && !coordinator.official(),
                "a refused kill records no progress at all");
        check(coordinator.advance(Stage.DEFEAT) == Advance.NOT_COMMITTED,
                "an uncommitted encounter cannot advance");
        check(!coordinator.commitEndUnlock(), "an uncommitted encounter never opens the End");
    }

    private static void testStageLadderIsMonotoneAndNeverSkips() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-3", 5L);
        coordinator.commitDefeat(true, Set.of(ALICE));
        check(coordinator.advance(Stage.REWARDS) == Advance.OUT_OF_ORDER,
                "skipping DYING must be refused");
        check(coordinator.advance(Stage.DYING) == Advance.ADVANCED, "the next step advances");
        check(coordinator.advance(Stage.DYING) == Advance.ALREADY_SETTLED,
                "replaying the current stage is idempotent, not a second run");
        check(coordinator.advance(Stage.DEFEAT) == Advance.OUT_OF_ORDER,
                "the ladder never moves backwards");
        check(coordinator.advance(Stage.REWARDS) == Advance.ADVANCED, "the ladder keeps moving up");
        check(coordinator.atLeast(Stage.REWARDS) && !coordinator.atLeast(Stage.END_OPEN),
                "atLeast reflects the current rung");
    }

    private static void testRewardOperationIsOnceOnly() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-4", 9L);
        coordinator.commitDefeat(true, Set.of(ALICE, BOB));
        coordinator.advance(Stage.DYING);
        check(coordinator.recordReward("op-alice") == RewardGrant.NOT_COMMITTED,
                "rewards cannot be issued before the REWARDS stage");
        coordinator.advance(Stage.REWARDS);
        check(coordinator.recordReward("op-alice") == RewardGrant.GRANTED, "the first grant applies");
        check(coordinator.recordReward("op-alice") == RewardGrant.ALREADY_APPLIED,
                "retrying the SAME operation id must not pay twice");
        check(coordinator.recordReward("op-bob") == RewardGrant.GRANTED, "each recipient has its own op");
        check(coordinator.recordReward("  ") == RewardGrant.INVALID, "a blank op id is invalid");
        check(coordinator.rewardOperationCount() == 2, "two operations were recorded exactly once");
        check(coordinator.rewardApplied("op-alice") && !coordinator.rewardApplied("op-carol"),
                "applied lookups reflect the ledger");
    }

    private static void testEndUnlockIsOnceOnlyAndNeedsLiveReadBack() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-5", 11L);
        coordinator.commitDefeat(true, Set.of(ALICE));
        coordinator.advance(Stage.DYING);
        coordinator.advance(Stage.REWARDS);
        check(!coordinator.commitEndUnlock(), "the End cannot open before its stage");
        coordinator.advance(Stage.END_OPEN);
        check(coordinator.commitEndUnlock(), "the first unlock commits");
        check(!coordinator.commitEndUnlock(), "a restart must not re-issue the unlock");
        check(!coordinator.endUnlockVisible(false),
                "the committed intent is not visible until WorldCore reads it back");
        check(coordinator.endUnlockVisible(true),
                "a live authoritative read-back makes the unlock visible");
    }

    private static void testCallbackGenerationIsFenced() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-6", 4L);
        check(coordinator.acceptsGeneration(4L), "the live generation accepts its own callback");
        check(!coordinator.acceptsGeneration(3L), "a callback from a wiped generation is refused");
        check(!coordinator.acceptsGeneration(5L), "a future generation cannot touch this event");
    }

    private static void testDurableRoundTripPreservesProgress() {
        EndRiftVictoryCoordinatorPolicy coordinator = new EndRiftVictoryCoordinatorPolicy("event-7", 13L);
        coordinator.commitDefeat(true, Set.of(ALICE, BOB));
        coordinator.advance(Stage.DYING);
        coordinator.advance(Stage.REWARDS);
        coordinator.recordReward("op-alice");
        coordinator.advance(Stage.END_OPEN);
        coordinator.commitEndUnlock();
        Map<String, String> progress = coordinator.encode();

        EndRiftVictoryCoordinatorPolicy restored =
                EndRiftVictoryCoordinatorPolicy.decode(progress, 13L);
        check(restored != null, "a recorded victory decodes");
        check(restored.stage() == Stage.END_OPEN, "the stage survives a restart");
        check(restored.roster().equals(Set.of(ALICE, BOB)), "the frozen roster survives a restart");
        check(restored.rewardApplied("op-alice"), "applied reward operations survive a restart");
        check(!restored.rewardApplied("op-bob"), "unapplied operations are not invented");
        check(restored.endUnlockCommitted(), "the End-open intent survives a restart");
        check(!restored.commitEndUnlock(), "a restored commit does not re-open the End");
        check(EndRiftVictoryCoordinatorPolicy.decode(new LinkedHashMap<>(), 13L) == null,
                "empty progress decodes to no coordinator");
    }

    private static void testCorruptProgressFailsClosed() {
        checkThrows(() -> EndRiftVictoryCoordinatorPolicy.decode(
                Map.of("end-rift-victory.generation", "99",
                        "end-rift-victory.stage", "REWARDS",
                        "end-rift-victory.official", "DONE"), 13L),
                "a foreign generation must not be misread as this event");
        checkThrows(() -> EndRiftVictoryCoordinatorPolicy.decode(
                Map.of("end-rift-victory.generation", "13",
                        "end-rift-victory.stage", "NOT_A_STAGE"), 13L),
                "an unknown stage fails closed");
        checkThrows(() -> EndRiftVictoryCoordinatorPolicy.decode(
                Map.of("end-rift-victory.generation", "13",
                        "end-rift-victory.stage", "REWARDS"), 13L),
                "a stage without an official defeat is contradictory");
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static void checkThrows(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
