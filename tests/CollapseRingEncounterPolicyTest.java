import java.util.UUID;
import me.copimine.endevent.domain.CollapseRingEncounterPolicy;

public final class CollapseRingEncounterPolicyTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CollapseRingEncounterPolicy.State state =
                CollapseRingEncounterPolicy.initial(7L, 2, a, b, 10_000L);
        check(state.phase() == CollapseRingEncounterPolicy.Phase.BOTH_ALIVE,
                "pair starts with both guards alive");
        state = CollapseRingEncounterPolicy.guardDown(state, 7L, a, 20_000L);
        check(state.phase() == CollapseRingEncounterPolicy.Phase.FIRST_DOWN,
                "first death opens the paired kill window");
        check(state.firstDownTick() == 20_000L
                        && state.killWindowDeadlineTick() == 20_200L,
                "timer starts exactly at first guard death");
        check(CollapseRingEncounterPolicy.timerActive(state, 7L, 20_200L),
                "the inclusive deadline is still active");
        CollapseRingEncounterPolicy.State defeated =
                CollapseRingEncounterPolicy.guardDown(state, 7L, b, 20_200L);
        check(defeated.phase() == CollapseRingEncounterPolicy.Phase.PAIR_DEFEATED,
                "partner death on the deadline completes the pair");
        check(CollapseRingEncounterPolicy.guardDown(defeated, 7L, b, 20_201L)
                        .equals(defeated),
                "duplicate callbacks are idempotent");

        CollapseRingEncounterPolicy.State timeoutState =
                CollapseRingEncounterPolicy.guardDown(
                        CollapseRingEncounterPolicy.initial(8L, 0, a, b, 500L),
                        8L, a, 1_000L);
        check(CollapseRingEncounterPolicy.tick(timeoutState, 8L, 1_201L).phase()
                        == CollapseRingEncounterPolicy.Phase.BOTH_ALIVE,
                "timeout rebuilds the pair instead of counting success");
        check(CollapseRingEncounterPolicy.tick(timeoutState, 8L, 1_201L)
                        .reviveHealthFraction() == 0.35D,
                "timeout uses the bounded revive fraction");
        check(CollapseRingEncounterPolicy.guardDown(timeoutState, 8L, b, 1_201L).phase()
                        == CollapseRingEncounterPolicy.Phase.BOTH_ALIVE,
                "late partner callback cannot turn timeout into success");
        check(!CollapseRingEncounterPolicy.guardDown(timeoutState, 9L, b, 1_001L)
                        .equals(CollapseRingEncounterPolicy.guardDown(timeoutState, 8L, b, 1_001L)),
                "stale generation callbacks are rejected");
        check(CollapseRingEncounterPolicy.attackingGuardIndex(2, 10_000L, 10_000L) == 0,
                "rotation starts at the encounter tick");
        check(CollapseRingEncounterPolicy.attackingGuardIndex(2, 10_000L, 10_200L) == 1,
                "rotation advances by elapsed encounter time");
        boolean invalid = false;
        try {
            CollapseRingEncounterPolicy.initial(0L, 0, a, b, 0L);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid, "generation zero must fail closed");
        System.out.println("CollapseRingEncounterPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
