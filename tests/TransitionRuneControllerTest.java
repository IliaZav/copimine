import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.TransitionRunePolicy;
import me.copimine.endevent.runtime.TransitionRuneController;

public final class TransitionRuneControllerTest {
    public static void main(String[] args) {
        UUID alpha = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID bravo = UUID.fromString("00000000-0000-0000-0000-000000000012");
        Set<UUID> roster = Set.of(alpha, bravo);
        List<TransitionRunePolicy.RuneOccupancy> ready = List.of(
                occupancy(alpha, "a"), occupancy(bravo, "b"));
        TransitionRuneController controller = new TransitionRuneController(10_000L);

        TransitionRuneController.Observation start = controller.observe(roster, ready, 100L);
        check(start.renderState() == TransitionRuneController.RenderState.CHARGING,
                "a full roster must visually enter charging state");
        check(!start.justCompleted(), "hold cannot complete at its start");

        TransitionRuneController.Observation beforeComplete = controller.observe(roster, ready, 10_099L);
        check(!beforeComplete.justCompleted(), "nine seconds and 999 milliseconds must not complete the hold");
        TransitionRuneController.Observation complete = controller.observe(roster, ready, 10_100L);
        check(complete.renderState() == TransitionRuneController.RenderState.COMPLETE,
                "ten-second hold must show completion");
        check(complete.justCompleted(), "completion notification fires exactly once");
        check(!controller.observe(roster, ready, 10_200L).justCompleted(),
                "repeated ticks after completion must not complete twice");

        controller.reset();
        TransitionRuneController.Observation empty = controller.observe(roster, List.of(), 6_000L);
        check(empty.renderState() == TransitionRuneController.RenderState.EMPTY,
                "missing roster occupancy renders empty");
        check(controller.holdState().startedAtMillis() == 0L,
                "missing player after reset leaves no hold timestamp");

        controller.reset();
        controller.observeServerTicks(roster, ready, 0L);
        check(!controller.observeServerTicks(roster, ready, 199L).justCompleted(),
                "wall-clock lag cannot shorten the two-hundred-server-tick hold");
        check(controller.observeServerTicks(roster, ready, 200L).justCompleted(),
                "the hold completes exactly after two hundred server ticks, including tick zero");
        controller.reset();
        controller.observeServerTicks(roster, ready, 400L);
        controller.reset(); // A move event observes leaving and returning between two objective polls.
        controller.observeServerTicks(roster, ready, 595L);
        check(!controller.observeServerTicks(roster, ready, 600L).justCompleted(),
                "a leave-and-return event starts a fresh continuous hold");
        check(controller.observeServerTicks(roster, ready, 795L).justCompleted(),
                "a fresh hold can complete after its own two hundred ticks");

        System.out.println("TransitionRuneControllerTest OK");
    }

    private static TransitionRunePolicy.RuneOccupancy occupancy(UUID player, String rune) {
        return new TransitionRunePolicy.RuneOccupancy(player, rune, true, true, true);
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
