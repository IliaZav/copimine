import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;

public final class EndEventStateMachineTest {
    public static void main(String[] args) {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.WAVE_5);
        check(machine.transition(EventPhase.WAVE_5, EventPhase.BOSS_CINEMATIC,
                "wave five complete", "event:cinematic").success(),
                "wave five must enter a distinct cinematic phase");
        check(machine.transition(EventPhase.BOSS_CINEMATIC, EventPhase.BOSS_ACTIVE,
                "cinematic complete", "event:boss").success(),
                "cinematic must hand off to active boss combat");
        check(!machine.transition(EventPhase.BOSS_ACTIVE, EventPhase.WAVE_5,
                "backwards", "event:bad").success(), "combat must not go back to wave five");
        check(EndEventStateMachine.recoveryPhase(EventPhase.BOSS_CINEMATIC) == EventPhase.READY_FOR_PLAYERS,
                "a crashed cinematic must recover to ready without spawning a boss");
        check(EndEventStateMachine.recoveryPhase(EventPhase.BOSS_ACTIVE) == EventPhase.READY_FOR_PLAYERS,
                "a crashed boss combat must recover to ready");
        System.out.println("EndEventStateMachineTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
