import java.util.LinkedHashSet;
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

public final class EndRiftEncounterCoordinatorTest {
    public static void main(String[] args) {
        testCanonicalSevenWaveLifecycle();
        testW6CannotBypassW7();
        testGenerationFenceAndCleanup();
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
