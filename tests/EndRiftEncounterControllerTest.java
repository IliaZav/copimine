import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.EncounterContext;
import me.copimine.endevent.runtime.EndRiftEncounterController;

public final class EndRiftEncounterControllerTest {
    public static void main(String[] args) {
        UUID player = UUID.randomUUID();
        EncounterContext context = new EncounterContext("event-one", 4L, "world",
                0, 64, 0, new EncounterContext.ArenaBounds(-8, 0, -8, 8, 255, 8),
                Set.of(player), Set.of(player), null);
        EndRiftEncounterController controller =
                new EndRiftEncounterController(context, EventPhase.WAVE_4);

        check(!controller.completeWave("event-one", 4L, 5,
                "complete a different wave", "w4:wrong-wave").success(),
                "a wave runtime cannot complete a different global wave");
        check(controller.completeWave("event-one", 4L, 4,
                "wave four complete", "w4:restore").success(),
                "Wave 4 completion must enter Core restoration");
        check(!controller.advanceIntermission("event-one", 4L,
                "skip required runes", "w4:skip-runes").success(),
                "Core restoration cannot skip the Wave 4 rune hold");
        check(controller.completeCoreRestoration("event-one", 4L,
                "Core restored", "w4:intermission").success(),
                "Core restoration must enter the required rune intermission");
        check(controller.advanceIntermission("event-one", 4L,
                "all participants held runes", "w5:start").success(),
                "the lifecycle authority may start Wave 5 after the rune phase");

        EncounterContext nextRoster = context.withLivingParticipants(Set.of());
        controller.replaceContext(nextRoster);
        check(controller.context().livingPlayerCount() == 0,
                "the controller must own updated roster context");
        controller.restore("event-one", 5L, EventPhase.INTERMISSION_4, null);
        check(!controller.transition("event-one", 4L, EventPhase.INTERMISSION_4,
                EventPhase.WAVE_5, "stale callback", "w5:stale").success(),
                "a callback from the previous generation must be rejected");
        check(controller.transition("event-one", 5L, EventPhase.INTERMISSION_4,
                EventPhase.WAVE_5, "current callback", "w5:current").success(),
                "the current generation must still transition");
        check(controller.recoverTo("event-one", 5L, EventPhase.READY_FOR_PLAYERS),
                "the current generation may explicitly recover to a safe phase");
        check(!controller.recoverTo("event-one", 4L, EventPhase.READY_FOR_PLAYERS),
                "a stale generation must not force a lifecycle recovery");
        check(controller.phase() == EventPhase.READY_FOR_PLAYERS,
                "recovery must reset the authoritative global phase");
        System.out.println("EndRiftEncounterControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
