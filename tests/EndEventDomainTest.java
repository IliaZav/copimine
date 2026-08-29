import me.copimine.endevent.domain.CoreDepositMath;
import me.copimine.endevent.domain.CoreInteractionGuard;
import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.FinalDrainMath;
import me.copimine.endevent.domain.PadLayout;
import me.copimine.endevent.domain.RewardRoster;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EndEventDomainTest {
    public static void main(String[] args) {
        testLegalStateTransitions();
        testIllegalStateTransitionsDoNotAdvance();
        testCanonicalFinalDrainAndVictoryTransitions();
        testFiveInitialWavesLeadToBoss();
        testTransientRecoveryAndTerminalUnlock();
        testPadGeometryHasExactlyNUniquePoints();
        testDepositCapLeavesRemainder();
        testDepositRejectsOffhandAndOfficialItems();
        testCoreInteractionGuardIsPerPlayerAndPerTick();
        testFinalDrainUsesCurrentHealthAndMinimumOne();
        testRosterRequiresExactlyNDistinctOccupants();
        testRosterIsImmutable();
        System.out.println("EndEventDomainTest OK");
    }

    private static void testLegalStateTransitions() {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.COLLECTING);
        check(machine.transition(EventPhase.COLLECTING, EventPhase.READY_FOR_PLAYERS,
                "resources-complete", "transition-1").success(),
                "collecting must transition to ready");
        check(machine.transition(EventPhase.READY_FOR_PLAYERS, EventPhase.COUNTDOWN,
                "pads-occupied", "transition-2").success(),
                "ready must transition to countdown");
        check(machine.phase() == EventPhase.COUNTDOWN, "phase must advance after a legal transition");
    }

    private static void testIllegalStateTransitionsDoNotAdvance() {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.COLLECTING);
        check(!machine.transition(EventPhase.COLLECTING, EventPhase.BOSS_ACTIVE,
                "forged", "transition-invalid").success(),
                "collecting must not jump directly to boss");
        check(machine.phase() == EventPhase.COLLECTING,
                "illegal transition must not mutate current phase");
    }

    private static void testTransientRecoveryAndTerminalUnlock() {
        check(EndEventStateMachine.recoveryPhase(EventPhase.BOSS_ACTIVE)
                        == EventPhase.READY_FOR_PLAYERS,
                "a transient boss phase must recover to ready");
        check(EndEventStateMachine.recoveryPhase(EventPhase.UNLOCKED)
                        == EventPhase.UNLOCKED,
                "unlocked must never relock after restart");
        check(EndEventStateMachine.recoveryPhase(EventPhase.RECOVERY_REQUIRED)
                        == EventPhase.RECOVERY_REQUIRED,
                "corrupt state must remain recovery required");
    }

    private static void testCanonicalFinalDrainAndVictoryTransitions() {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.BOSS_ACTIVE);
        check(machine.transition(EventPhase.BOSS_ACTIVE, EventPhase.FINAL_DRAIN,
                        "threshold", "final-drain-1").success(),
                "boss must enter the canonical final drain phase");
        check(machine.transition(EventPhase.FINAL_DRAIN, EventPhase.FINAL_WAVE,
                        "telegraph-complete", "final-wave-1").success(),
                "final drain must transition to the final wave");
        check(machine.transition(EventPhase.FINAL_WAVE, EventPhase.BOSS_FINISH,
                        "wave-dead", "boss-finish-1").success(),
                "final wave must release the boss");
        EndEventStateMachine officialSequence = new EndEventStateMachine(EventPhase.BOSS_CINEMATIC);
        check(machine.phase() == EventPhase.BOSS_FINISH,
                "legacy final-wave route must remain independently complete");
        check(officialSequence.transition(EventPhase.BOSS_CINEMATIC, EventPhase.FINAL_WAVE,
                        "cinematic-complete", "official-final-wave-1").success(),
                "official cinematic must start the final wave");
        check(officialSequence.transition(EventPhase.FINAL_WAVE, EventPhase.BOSS_ACTIVE,
                        "final-wave-dead", "official-boss-1").success(),
                "official final wave must awaken the boss after it is defeated");
        check(machine.transition(EventPhase.BOSS_FINISH, EventPhase.VICTORY_PROCESSING,
                        "boss-dead", "victory-1").success(),
                "boss death must enter the canonical victory saga");
        check(machine.transition(EventPhase.VICTORY_PROCESSING, EventPhase.UNLOCKED,
                        "rewards-complete", "unlock-1").success(),
                "victory saga must be able to commit the terminal unlock");
    }

    private static void testFiveInitialWavesLeadToBoss() {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.COUNTDOWN);
        EventPhase[] expected = {
                EventPhase.WAVE_1, EventPhase.INTERMISSION_1,
                EventPhase.WAVE_2, EventPhase.INTERMISSION_2,
                EventPhase.WAVE_3, EventPhase.INTERMISSION_3,
                EventPhase.WAVE_4, EventPhase.INTERMISSION_4,
                EventPhase.WAVE_5, EventPhase.BOSS_ACTIVE
        };
        EventPhase current = EventPhase.COUNTDOWN;
        for (int index = 0; index < expected.length; index++) {
            EventPhase next = expected[index];
            check(machine.transition(current, next, "test-five-waves", "five-waves-" + index).success(),
                    "initial wave sequence must allow " + current + " -> " + next);
            check(machine.phase() == next, "state machine must advance to " + next);
            current = next;
        }
    }

    private static void testPadGeometryHasExactlyNUniquePoints() {
        for (int count = 1; count <= 20; count++) {
            PadLayout.Result result = PadLayout.compute(count, 0.0D, List.of(5.0D, 6.0D, 7.0D, 8.0D));
            check(result.valid(), "pad layout must be valid for N=" + count);
            check(result.points().size() == count, "pad layout must have exactly N points for N=" + count);
            Set<String> unique = new HashSet<>();
            result.points().forEach(point -> unique.add(point.blockX() + ":" + point.blockZ()));
            check(unique.size() == count, "pad layout must not duplicate blocks for N=" + count);
        }
    }

    private static void testDepositCapLeavesRemainder() {
        check(CoreDepositMath.acceptedAmount(64, 100, 88) == 12,
                "deposit must accept only the remaining requirement");
        check(CoreDepositMath.acceptedAmount(12, 100, 0) == 12,
                "deposit must accept a held stack when progress is zero");
        check(CoreDepositMath.acceptedAmount(64, 100, 100) == 0,
                "completed requirement must accept nothing");
    }

    private static void testDepositRejectsOffhandAndOfficialItems() {
        Set<String> requirements = Set.of("DIAMOND", "ENDER_EYE");
        check(CoreDepositMath.canAccept(true, "DIAMOND", requirements, false, false),
                "exact vanilla main-hand material must be accepted");
        check(!CoreDepositMath.canAccept(false, "DIAMOND", requirements, false, false),
                "offhand must not be accepted");
        check(!CoreDepositMath.canAccept(true, "DIAMOND", requirements, true, false),
                "official artifact identity must not be consumed");
        check(!CoreDepositMath.canAccept(true, "DIAMOND", requirements, false, true),
                "custom protected item must not be consumed");
        check(!CoreDepositMath.canAccept(true, "GOLD_INGOT", requirements, false, false),
                "wrong material must not be accepted");
    }

    private static void testCoreInteractionGuardIsPerPlayerAndPerTick() {
        CoreInteractionGuard guard = new CoreInteractionGuard();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        check(guard.accept(10L, "event", 3L, first), "first core click must be accepted");
        check(!guard.accept(10L, "event", 3L, first), "duplicate same-tick core click must be rejected");
        check(guard.accept(10L, "event", 3L, second), "another player may deposit in the same tick");
        check(guard.accept(11L, "event", 3L, first), "the next tick must accept a new click");
        check(guard.accept(11L, "event", 4L, first), "a new generation must have its own interaction key");
        check(!guard.accept(11L, "", 4L, first), "blank event identity must fail closed");
    }

    private static void testFinalDrainUsesCurrentHealthAndMinimumOne() {
        check(close(FinalDrainMath.healthAfterDrain(20.0D, 20.0D, 0.60D, 1.0D), 8.0D),
                "20 health must become 8 after a 60 percent current-health drain");
        check(close(FinalDrainMath.healthAfterDrain(10.0D, 20.0D, 0.60D, 1.0D), 4.0D),
                "10 health must become 4 after a 60 percent current-health drain");
        check(close(FinalDrainMath.healthAfterDrain(2.0D, 20.0D, 0.60D, 1.0D), 1.0D),
                "drain must never kill a player");
        check(close(FinalDrainMath.healthAfterDrain(100.0D, 20.0D, 0.60D, 1.0D), 8.0D),
                "health above the maximum must be capped before applying the drain");
    }

    private static void testRosterRequiresExactlyNDistinctOccupants() {
        Set<UUID> occupants = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RewardRoster roster = RewardRoster.commitExactly(occupants, 5);
        check(roster.players().size() == 5, "official roster must contain exactly N players");
        boolean rejected = false;
        try {
            RewardRoster.commitExactly(Set.of(UUID.randomUUID(), UUID.randomUUID()), 5);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "a roster with fewer than N occupants must be rejected");
    }

    private static void testRosterIsImmutable() {
        RewardRoster roster = RewardRoster.commitExactly(
                Set.of(UUID.randomUUID(), UUID.randomUUID()), 2);
        boolean rejected = false;
        try {
            roster.players().clear();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        check(rejected, "official roster must be immutable");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
