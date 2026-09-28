import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EventPhase;

public final class EndEventStateMachineTest {
    public static void main(String[] args) {
        EndEventStateMachine machine = new EndEventStateMachine(EventPhase.READY_FOR_PLAYERS);
        check(machine.transition(EventPhase.READY_FOR_PLAYERS, EventPhase.START_RITUAL,
                "all ritual runes held", "event:start-ritual").success(),
                "ready state must enter the distinct start ritual");
        check(machine.transition(EventPhase.START_RITUAL, EventPhase.WAVE_1,
                "ritual complete", "event:wave-one").success(),
                "start ritual must hand off to the first wave");
        EndEventStateMachine officialSequence = new EndEventStateMachine(EventPhase.WAVE_5);
        check(officialSequence.transition(EventPhase.WAVE_5, EventPhase.INTERMISSION_5,
                "wave five complete", "event:intermission-five").success(),
                "wave five must enter its transition-rune intermission");
        check(officialSequence.transition(EventPhase.INTERMISSION_5, EventPhase.WAVE_6,
                "runes complete", "event:wave-six").success(),
                "the fifth intermission must enter Wave 6");
        EndEventStateMachine gateway = new EndEventStateMachine(EventPhase.PRE_BOSS_COOLDOWN);
        check(gateway.transition(EventPhase.PRE_BOSS_COOLDOWN, EventPhase.BOSS_ACTIVE,
                "forty-second gateway handoff", "event:boss-gateway").success(),
                "the Stage 1 boss gateway must allow its single direct handoff after the cooldown");
        EndEventStateMachine waveFour = new EndEventStateMachine(EventPhase.WAVE_4);
        check(waveFour.transition(EventPhase.WAVE_4, EventPhase.CORE_RESTORATION,
                "wave four complete", "event:restore-core").success(),
                "Wave 4 must enter Core restoration first");
        check(!waveFour.transition(EventPhase.CORE_RESTORATION, EventPhase.WAVE_5,
                "bypass required runes", "event:skip-wave-four-runes").success(),
                "Core restoration must not bypass Wave 4's transition rune hold");
        check(waveFour.transition(EventPhase.CORE_RESTORATION, EventPhase.INTERMISSION_4,
                "Core restored", "event:wave-four-runes").success(),
                "Core restoration must enter the Wave 4 rune phase");
        check(waveFour.transition(EventPhase.INTERMISSION_4, EventPhase.WAVE_5,
                "runes held", "event:wave-five").success(),
                "Wave 5 can start only after the Wave 4 rune phase");
        check(!machine.transition(EventPhase.WAVE_1, EventPhase.BOSS_ACTIVE,
                "skip waves", "event:skip-waves").success(),
                "an official attempt must not skip mandatory waves");
        EndEventStateMachine noSkip = new EndEventStateMachine(EventPhase.WAVE_6);
        check(!noSkip.transition(EventPhase.WAVE_6, EventPhase.PRE_BOSS_COOLDOWN,
                "skip wave seven", "event:skip-wave-seven").success(),
                "Wave 6 must not enter pre-boss directly");
        check(noSkip.transition(EventPhase.WAVE_6, EventPhase.INTERMISSION_6,
                "wave six complete", "event:intermission-six").success(),
                "Wave 6 must enter its intermission");
        check(noSkip.transition(EventPhase.INTERMISSION_6, EventPhase.WAVE_7,
                "runes complete", "event:wave-seven").success(),
                "Wave 7 is mandatory");
        check(noSkip.transition(EventPhase.WAVE_7, EventPhase.PRE_BOSS_COOLDOWN,
                "wave seven complete", "event:pre-boss").success(),
                "only Wave 7 may open the pre-boss cooldown");
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
