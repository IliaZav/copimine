import java.util.UUID;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.runtime.TentacleController;

public final class TentacleControllerTest {
    public static void main(String[] args) {
        long generation = 7L;
        TentacleController controller = new TentacleController();
        UUID permanent = UUID.randomUUID();
        UUID temporary = UUID.randomUUID();
        controller.begin(generation);
        check(controller.register(generation, permanent, false, 0, 100L),
                "permanent tentacle must register in its generation");
        check(controller.register(generation, temporary, true, 0, 100L),
                "temporary tentacle must register in its generation");
        check(controller.permanentCount() == 1 && controller.temporaryCount() == 1,
                "controller must keep separate permanent and temporary counts");
        check(!controller.register(generation + 1, UUID.randomUUID(), false, 1, 100L),
                "stale generation must be rejected");
        check(controller.transition(permanent, TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                        110L, null), "state transition must be generation-scoped");
        check(controller.state(permanent).state()
                        == TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                "state must be stored for the bound entity");
        check(controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.HOLD_LOCK, 116L),
                "server marker clock must be independent of client bone position");
        check(!controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE, 129L),
                "future marker must not fire early");
        check(controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE, 130L),
                "throw release marker must fire on its server tick");
        controller.remove(temporary);
        check(controller.temporaryCount() == 0, "temporary cleanup must release its slot");
        controller.clear();
        check(controller.count() == 0 && !controller.owns(generation),
                "clear must remove all runtime state and generation ownership");
        System.out.println("TentacleControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
