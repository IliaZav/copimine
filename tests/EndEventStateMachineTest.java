import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;

public final class EndEventStateMachineTest {
    public static void main(String[] args) {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.READY_FOR_PLAYERS);
        check(machine.transition(EventPhase.READY_FOR_PLAYERS, EventPhase.START_RITUAL,
                "all ritual runes held", "event:start-ritual").success(),
                "V2 ready state must enter the distinct start ritual");
        check(machine.transition(EventPhase.START_RITUAL, EventPhase.WAVE_1,
                "ritual complete", "event:wave-one").success(),
                "V2 start ritual must hand off to the first wave");
        EndEventStateMachine officialSequence = new EndEventStateMachine(EventPhase.WAVE_5);
        check(officialSequence.transition(EventPhase.WAVE_5, EventPhase.INTERMISSION_5,
                "wave five complete", "event:intermission-five").success(),
                "wave five must enter its V2 transition-rune intermission");
        check(officialSequence.transition(EventPhase.INTERMISSION_5, EventPhase.WAVE_6,
                "runes complete", "event:wave-six").success(),
                "the fifth intermission must enter Wave 6, not a legacy final wave");
        check(!machine.transition(EventPhase.WAVE_1, EventPhase.FINAL_DRAIN,
                "legacy final drain", "event:legacy-final").success(),
                "an official V2 attempt must not enter legacy final drain");
        check(!machine.transition(EventPhase.WAVE_1, EventPhase.WAVE_5,
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
