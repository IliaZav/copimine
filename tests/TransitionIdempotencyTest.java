import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;

public final class TransitionIdempotencyTest {
    public static void main(String[] args) {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.READY_FOR_PLAYERS);

        EndEventStateMachine.TransitionResult first = machine.transition(
                EventPhase.READY_FOR_PLAYERS, EventPhase.START_RITUAL,
                "start", "transition-1");
        check(first.success(), "first transition must succeed");
        check(machine.phase() == EventPhase.START_RITUAL, "first transition must advance phase");

        EndEventStateMachine.TransitionResult replay = machine.transition(
                EventPhase.READY_FOR_PLAYERS, EventPhase.START_RITUAL,
                "start", "transition-1");
        check(replay.success(), "same transition key must be an idempotent success");
        check("IDEMPOTENT_REPLAY".equals(replay.code()),
                "replay must be distinguishable from a new transition");
        check(machine.phase() == EventPhase.START_RITUAL,
                "replay must not move the phase a second time");

        EndEventStateMachine.TransitionResult conflict = machine.transition(
                EventPhase.START_RITUAL, EventPhase.WAVE_1,
                "reuse", "transition-1");
        check(!conflict.success(), "same key with a different transition must be rejected");
        check("IDEMPOTENCY_KEY_CONFLICT".equals(conflict.code()),
                "conflicting key reuse must have an explicit reason");
        check(machine.phase() == EventPhase.START_RITUAL,
                "conflicting replay must not mutate the phase");

        System.out.println("TransitionIdempotencyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
