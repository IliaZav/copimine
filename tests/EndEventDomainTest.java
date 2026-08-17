import me.copimine.endevent.domain.CoreDepositMath;
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
        testTransientRecoveryAndTerminalUnlock();
        testPadGeometryHasExactlyNUniquePoints();
        testDepositCapLeavesRemainder();
        testDepositRejectsOffhandAndOfficialItems();
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
