import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.AttemptLifecycleController;

public final class AttemptLifecycleControllerTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var controller = new AttemptLifecycleController();
        controller.begin(10L, Set.of(a, b));
        check(controller.markDead(a, 10L), "first death is recorded");
        check(controller.performAttemptWipe(10L, "one dead").status()
                == AttemptLifecycleController.WipeStatus.NO_LIVING_PLAYERS,
                "one dead player must keep the attempt alive");
        check(controller.markDead(b, 10L), "second death is recorded");
        var wiped = controller.performAttemptWipe(10L, "all dead");
        check(wiped.status() == AttemptLifecycleController.WipeStatus.ACCEPTED,
                "all-dead wipe must be accepted");
        check(wiped.nextGeneration() == 11L, "wipe increments generation");
        check(controller.living().size() == 2, "wipe restores roster to alive start state");
        check(controller.performAttemptWipe(10L, "stale").status()
                == AttemptLifecycleController.WipeStatus.STALE_GENERATION,
                "old callbacks cannot wipe a new generation");
        check(controller.performAttemptWipe(11L, "not dead").status()
                == AttemptLifecycleController.WipeStatus.NO_LIVING_PLAYERS,
                "new attempt with living roster is protected");
        System.out.println("AttemptLifecycleControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
