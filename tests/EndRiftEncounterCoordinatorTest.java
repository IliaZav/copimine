import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.ObeliskScalingPolicy;
import me.copimine.endevent.runtime.EncounterContext;
import me.copimine.endevent.runtime.EndRiftEncounterCoordinator;
import me.copimine.endevent.runtime.encounter.BlackFogEncounter;
import me.copimine.endevent.runtime.encounter.CollapseRingsEncounter;
import me.copimine.endevent.runtime.encounter.ObeliskAssaultEncounter;
import me.copimine.endevent.runtime.encounter.RealitySplitEncounter;
import me.copimine.endevent.runtime.encounter.RiftCarriersEncounter;
import me.copimine.endevent.runtime.encounter.RiftGatesEncounter;
import me.copimine.endevent.runtime.encounter.RiftHuntEncounter;
import me.copimine.endevent.runtime.encounter.WaveEncounter;

public final class EndRiftEncounterCoordinatorTest {
    public static void main(String[] args) {
        testCanonicalSevenWaveLifecycle();
        testW6CannotBypassW7();
        testTransitionReplayDoesNotRepeatCoordinatorSideEffects();
        testWaveStartIsAtomicWhenEncounterRejects();
        testWaveCompletionPreflightsTransitionAndIsReplaySafe();
        testGenerationFenceAndCleanup();
        testCleanupFailurePropagatesAndRemainsVisible();
        System.out.println("EndRiftEncounterCoordinatorTest OK");
    }

    private static void testCanonicalSevenWaveLifecycle() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> players = new LinkedHashSet<>(Set.of(first, second));
        EncounterContext context = new EncounterContext("coordinator-test", 7L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-20, 0, -20, 20, 100, 20),
                players, players, null);
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(
                context, EventPhase.START_RITUAL);

        check(coordinator.startNextWave(EndRiftObjective.Objective.RIFT_CARRIERS,
                "test", "w1").accepted(), "wave 1 must start after ritual");
        RiftCarriersEncounter carriers = (RiftCarriersEncounter) coordinator.encounter(
                EndRiftObjective.Objective.RIFT_CARRIERS);
        for (int index = 0; index < 3; index++) {
            check(carriers.selectCarrier(context.withObjective(EndRiftObjective.Objective.RIFT_CARRIERS), first),
                    "carrier must be selectable");
            check(carriers.deliver(context.withObjective(EndRiftObjective.Objective.RIFT_CARRIERS), first,
                    UUID.randomUUID(), true).accepted(), "delivery must be accepted");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.RIFT_CARRIERS,
                "w1 done", "w1-complete").accepted(), "wave 1 must complete");
        check(coordinator.advanceIntermission("w2", "w2-start").accepted(), "wave 2 must start");
        RiftHuntEncounter hunt = (RiftHuntEncounter) coordinator.encounter(EndRiftObjective.Objective.RIFT_HUNT);
        for (int cycle = 1; cycle <= 3; cycle++) {
            check(hunt.completeCycle(context.withObjective(EndRiftObjective.Objective.RIFT_HUNT), cycle).accepted(),
                    "hunt cycle must complete in order");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.RIFT_HUNT, "w2 done", "w2-complete").accepted(),
                "wave 2 must complete");
        check(coordinator.advanceIntermission("w3", "w3-start").accepted(), "wave 3 must start");
        RiftGatesEncounter gates = (RiftGatesEncounter) coordinator.encounter(EndRiftObjective.Objective.RIFT_GATES);
        for (int gate = 0; gate < 3; gate++) {
            check(gates.captureGate(context.withObjective(EndRiftObjective.Objective.RIFT_GATES), gate).accepted(),
                    "gate must capture in order");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.RIFT_GATES, "w3 done", "w3-complete").accepted(),
                "wave 3 must complete");
        check(coordinator.advanceIntermission("w4", "w4-start").accepted(), "wave 4 must start");
        ObeliskAssaultEncounter obelisks = (ObeliskAssaultEncounter) coordinator.encounter(
                EndRiftObjective.Objective.OBELISK_ASSAULT);
        int requiredHits = ObeliskScalingPolicy.obeliskCount(2) * 3;
        for (int hit = 0; hit < requiredHits; hit++) {
            check(obelisks.reflectedHit(context.withObjective(EndRiftObjective.Objective.OBELISK_ASSAULT),
                    UUID.randomUUID(), first, hit % obelisks.obeliskCount()).accepted(),
                    "reflected obelisk hit must count");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.OBELISK_ASSAULT,
                "w4 done", "w4-complete").accepted(), "wave 4 must enter restoration");
        check(coordinator.phase() == EventPhase.CORE_RESTORATION,
                "wave 4 must enter core restoration, not an intermission");
        check(coordinator.completeCoreRestoration("restore", "restore-complete").accepted(),
                "core restoration must lead to wave 5");
        check(coordinator.startCurrentWave(EndRiftObjective.Objective.BLACK_FOG).accepted(),
                "wave 5 must start after restoration");
        BlackFogEncounter fog = (BlackFogEncounter) coordinator.encounter(EndRiftObjective.Objective.BLACK_FOG);
        for (int cycle = 1; cycle <= 3; cycle++) {
            check(fog.completeCycle(context.withObjective(EndRiftObjective.Objective.BLACK_FOG), cycle, true).accepted(),
                    "fog cycle must complete");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.BLACK_FOG, "w5 done", "w5-complete").accepted(),
                "wave 5 must complete");
        check(coordinator.advanceIntermission("w6", "w6-start").accepted(), "wave 6 must start");
        CollapseRingsEncounter rings = (CollapseRingsEncounter) coordinator.encounter(
                EndRiftObjective.Objective.COLLAPSE_RINGS);
        for (int ring = 1; ring <= 3; ring++) {
            check(rings.firstGuardDown(context.withObjective(EndRiftObjective.Objective.COLLAPSE_RINGS), ring, 100L),
                    "first guard death must start timer");
            check(rings.completePair(context.withObjective(EndRiftObjective.Objective.COLLAPSE_RINGS), ring, 300L).accepted(),
                    "pair must complete at inclusive deadline");
        }
        check(coordinator.completeWave(EndRiftObjective.Objective.COLLAPSE_RINGS,
                "w6 done", "w6-complete").accepted(), "wave 6 must complete");
        check(coordinator.advanceIntermission("w7", "w7-start").accepted(), "wave 7 must start");
        RealitySplitEncounter split = (RealitySplitEncounter) coordinator.encounter(
                EndRiftObjective.Objective.REALITY_SPLIT);
        check(split.assignment().chamberCount() == 2, "two players must get two isolated chambers");
        check(split.completeChamber(context.withObjective(EndRiftObjective.Objective.REALITY_SPLIT), 0).accepted(),
                "first chamber must complete");
        check(split.completeChamber(context.withObjective(EndRiftObjective.Objective.REALITY_SPLIT), 1).accepted(),
                "second chamber must complete");
        check(coordinator.completeWave(EndRiftObjective.Objective.REALITY_SPLIT,
                "w7 done", "w7-complete").accepted(), "wave 7 must enter pre-boss cooldown");
        check(coordinator.phase() == EventPhase.PRE_BOSS_COOLDOWN, "wave 7 must lead to pre-boss cooldown");
    }

    private static void testW6CannotBypassW7() {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.WAVE_6);
        check(!machine.transition(EventPhase.WAVE_6, EventPhase.PRE_BOSS_COOLDOWN,
                "legacy bypass", "bad").success(), "W6 direct pre-boss bypass must be rejected");
    }

    private static void testTransitionReplayDoesNotRepeatCoordinatorSideEffects() {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("replay-test", 12L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-5, 0, -5, 5, 80, 5),
                Set.of(player), Set.of(player), null);
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(
                context, EventPhase.START_RITUAL);
        check(coordinator.transition(EventPhase.START_RITUAL, EventPhase.WAVE_1,
                "start", "wave-1-once").accepted(), "first transition must succeed");
        check(coordinator.history().size() == 1, "first transition must be recorded once");
        EndRiftEncounterCoordinator.Result replay = coordinator.transition(
                EventPhase.START_RITUAL, EventPhase.WAVE_1, "start", "wave-1-once");
        check(replay.accepted(), "duplicate transition must be an idempotent success");
        check("IDEMPOTENT_REPLAY".equals(replay.code()),
                "coordinator must expose an idempotent replay code");
        check(coordinator.history().size() == 1,
                "duplicate transition must not repeat coordinator history side effects");
    }

    private static void testWaveStartIsAtomicWhenEncounterRejects() {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("atomic-start", 13L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-5, 0, -5, 5, 80, 5),
                Set.of(player), Set.of(player), null);
        WaveEncounter rejecting = new StubEncounter(EndRiftObjective.Objective.RIFT_CARRIERS,
                new WaveEncounter.Result(WaveEncounter.Status.REJECTED, 0, 3, "injected start failure"));
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(
                context, EventPhase.START_RITUAL, null,
                Map.of(EndRiftObjective.Objective.RIFT_CARRIERS, rejecting));
        EndRiftEncounterCoordinator.Result result = coordinator.startNextWave(
                EndRiftObjective.Objective.RIFT_CARRIERS, "injected", "atomic-start");
        check(!result.accepted(), "rejected encounter start must reject the operation");
        check("START_REJECTED".equals(result.code()),
                "rejected encounter start must expose its typed coordinator reason");
        check(coordinator.phase() == EventPhase.START_RITUAL,
                "rejected encounter start must not advance the state graph");
        check(coordinator.history().isEmpty(),
                "rejected encounter start must not append transition history");
    }

    private static void testWaveCompletionPreflightsTransitionAndIsReplaySafe() {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("atomic-complete", 14L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-5, 0, -5, 5, 80, 5),
                Set.of(player), Set.of(player), null);
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(
                context, EventPhase.START_RITUAL);
        check(coordinator.startNextWave(EndRiftObjective.Objective.RIFT_CARRIERS,
                "start", "atomic-wave").accepted(), "wave must start");
        RiftCarriersEncounter carriers = (RiftCarriersEncounter) coordinator.encounter(
                EndRiftObjective.Objective.RIFT_CARRIERS);
        for (int index = 0; index < 3; index++) {
            check(carriers.deliver(context.withObjective(EndRiftObjective.Objective.RIFT_CARRIERS),
                    player, UUID.randomUUID(), true).accepted(), "delivery must count");
        }
        EndRiftEncounterCoordinator.Result invalid = coordinator.completeWave(
                EndRiftObjective.Objective.RIFT_CARRIERS, "missing key", "");
        check(!invalid.accepted() && "IDEMPOTENCY_KEY_REQUIRED".equals(invalid.code()),
                "completion must validate its transition before mutating the encounter");
        check(coordinator.phase() == EventPhase.WAVE_1,
                "invalid completion must keep the wave phase");
        check(!carriers.completed(), "invalid completion must not mark the wave complete");
        EndRiftEncounterCoordinator.Result completed = coordinator.completeWave(
                EndRiftObjective.Objective.RIFT_CARRIERS, "complete", "atomic-complete");
        check(completed.accepted(), "valid completion must succeed");
        EndRiftEncounterCoordinator.Result replay = coordinator.completeWave(
                EndRiftObjective.Objective.RIFT_CARRIERS, "complete", "atomic-complete");
        check(replay.accepted() && "IDEMPOTENT_REPLAY".equals(replay.code()),
                "replayed completion must be an idempotent success");
        check(coordinator.history().size() == 2,
                "replayed completion must not repeat coordinator history");
    }

    private static void testGenerationFenceAndCleanup() {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("cleanup-test", 9L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-5, 0, -5, 5, 80, 5),
                Set.of(player), Set.of(player), null);
        int[] closed = {0};
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(context,
                EventPhase.READY_FOR_PLAYERS, () -> closed[0]++);
        check(coordinator.accepts("cleanup-test", 9L), "current generation must be accepted");
        check(!coordinator.accepts("cleanup-test", 8L), "stale generation must be rejected");
        coordinator.close();
        coordinator.close();
        check(closed[0] == 1, "cleanup scope must be idempotent");
        check(!coordinator.accepts("cleanup-test", 9L), "closed coordinator must reject callbacks");
    }

    private static void testCleanupFailurePropagatesAndRemainsVisible() {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("cleanup-failure-test", 10L, "CopiMine",
                0, 64, 0, new EncounterContext.ArenaBounds(-5, 0, -5, 5, 80, 5),
                Set.of(player), Set.of(player), null);
        RuntimeException expected = new IllegalStateException("injected scope cleanup failure");
        int[] attempts = {0};
        EndRiftEncounterCoordinator coordinator = new EndRiftEncounterCoordinator(
                context, EventPhase.READY_FOR_PLAYERS, () -> {
                    attempts[0]++;
                    throw expected;
                });
        RuntimeException first = expectFailure(coordinator::close);
        RuntimeException second = expectFailure(coordinator::close);
        check(first == expected, "the original cleanup failure must reach the coordinator owner");
        check(second == first, "repeated close must expose the same durable cleanup failure");
        check(attempts[0] == 1, "a failed cleanup must not be retried by an idempotent close");
        check(!coordinator.accepts("cleanup-failure-test", 10L),
                "a failed close still fences the closed coordinator");
    }

    private static RuntimeException expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            return error;
        }
        throw new AssertionError("expected cleanup failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class StubEncounter implements WaveEncounter {
        private final EndRiftObjective.Objective objective;
        private final Result startResult;

        private StubEncounter(EndRiftObjective.Objective objective, Result startResult) {
            this.objective = objective;
            this.startResult = startResult;
        }

        @Override public EndRiftObjective.Objective objective() { return objective; }
        @Override public int wave() { return 1; }
        @Override public boolean started() { return false; }
        @Override public boolean completed() { return false; }
        @Override public long generation() { return 0L; }
        @Override public Result start(EncounterContext context) { return startResult; }
        @Override public Result tick(EncounterContext context) { return startResult; }
        @Override public Result complete(EncounterContext context) { return startResult; }
        @Override public void reset() { }
    }
}
