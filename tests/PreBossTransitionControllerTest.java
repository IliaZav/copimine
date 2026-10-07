import me.copimine.endevent.runtime.BossStartGateway;
import me.copimine.endevent.runtime.EncounterContext;
import me.copimine.endevent.runtime.PreBossTransitionController;
import java.util.Set;

public final class PreBossTransitionControllerTest {
    public static void main(String[] args) {
        EncounterContext context = context("preboss", 7L);
        PreBossTransitionController controller = new PreBossTransitionController();
        controller.start(context, 1_000L);
        controller.start(context, 20_000L);
        int[] calls = {0};
        EncounterContext[] expectedContext = {context};
        BossStartGateway gateway = received -> {
            check(received == expectedContext[0], "gateway receives the current encounter context");
            calls[0]++;
        };

        check(controller.tick(context, 40_999L, gateway).status()
                        == PreBossTransitionController.Status.WAITING,
                "handoff waits until the full forty seconds have elapsed");
        check(calls[0] == 0, "gateway must not run before the deadline");
        check(controller.tick(context, 41_000L, gateway).status()
                        == PreBossTransitionController.Status.HANDOFF_STARTED,
                "handoff begins exactly at forty seconds");
        check(controller.tick(context, 50_000L, gateway).status()
                        == PreBossTransitionController.Status.ALREADY_STARTED,
                "a repeated tick cannot start the boss twice");
        check(calls[0] == 1, "gateway must be invoked exactly once");

        EncounterContext next = context("preboss", 8L);
        expectedContext[0] = next;
        controller.start(next, 60_000L);
        check(controller.tick(context, 100_000L, gateway).status()
                        == PreBossTransitionController.Status.STALE_CONTEXT,
                "a stale generation cannot hand off the boss");
        check(controller.tick(next, 99_999L, gateway).status()
                        == PreBossTransitionController.Status.WAITING,
                "the replacement generation has its own complete forty seconds");
        check(controller.tick(next, 100_000L, gateway).status()
                        == PreBossTransitionController.Status.HANDOFF_STARTED,
                "the replacement generation hands off at its own deadline");
        check(calls[0] == 2, "each generation has at most one handoff");

        PreBossTransitionController throwing = new PreBossTransitionController();
        throwing.start(context, 0L);
        int[] failedCalls = {0};
        BossStartGateway failure = ignored -> {
            failedCalls[0]++;
            throw new IllegalStateException("injected gateway failure");
        };
        try {
            throwing.tick(context, 40_000L, failure);
            throw new AssertionError("injected gateway failure should propagate");
        } catch (IllegalStateException expected) {
            check("injected gateway failure".equals(expected.getMessage()),
                    "gateway failure must remain diagnosable");
        }
        check(throwing.tick(context, 41_000L, failure).status()
                        == PreBossTransitionController.Status.ALREADY_STARTED,
                "a failed gateway call is not replayed implicitly");
        check(failedCalls[0] == 1, "a failing gateway is still attempted exactly once");

        PreBossTransitionController serverTicks = new PreBossTransitionController();
        serverTicks.startServerTicks(context, 0L);
        int[] tickCalls = {0};
        BossStartGateway tickSpy = ignored -> tickCalls[0]++;
        check(serverTicks.tickServerTicks(context, 799L, tickSpy).status()
                        == PreBossTransitionController.Status.WAITING,
                "799 actual server ticks must not hand off, regardless of wall-clock delay");
        check(serverTicks.elapsedServerTicks(799L) == 799L, "persisted progress is server ticks");
        check(serverTicks.tickServerTicks(context, 800L, tickSpy).status()
                        == PreBossTransitionController.Status.HANDOFF_STARTED,
                "handoff fires after exactly eight hundred server ticks");
        serverTicks.tickServerTicks(context, 805L, tickSpy);
        check(tickCalls[0] == 1, "server tick handoff is single fire");

        PreBossTransitionController resumed = new PreBossTransitionController();
        resumed.restoreServerTicks(context, 5L, 300L);
        resumed.restoreServerTicks(context, 10L, 0L);
        check(resumed.tickServerTicks(context, 504L, tickSpy).status()
                        == PreBossTransitionController.Status.WAITING,
                "a restart resumes the saved progress, without counting offline wall time");
        check(resumed.tickServerTicks(context, 505L, tickSpy).status()
                        == PreBossTransitionController.Status.HANDOFF_STARTED,
                "the remaining five hundred server ticks complete the same interval");
        resumed.reset();
        check(resumed.tickServerTicks(context, 1000L, tickSpy).status()
                        == PreBossTransitionController.Status.NOT_STARTED,
                "reset retires the old timer and its handoff");
        System.out.println("PreBossTransitionControllerTest OK");
    }

    private static EncounterContext context(String eventId, long generation) {
        return new EncounterContext(eventId, generation, "CopiMine", 0, 64, 0,
                new EncounterContext.ArenaBounds(-5, 0, -5, 5, 100, 5),
                Set.of(), Set.of(), null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
