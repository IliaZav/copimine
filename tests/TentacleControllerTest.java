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
        check(controller.transition(generation, permanent, TentacleAnimationPolicy.State.GRAB_SUCCESS,
                        110L, null), "state transition must be generation-scoped");
        check(!controller.transition(generation + 1L, permanent,
                        TentacleAnimationPolicy.State.THROW, 111L, null),
                "a stale generation must not mutate an existing tentacle");
        check(controller.state(permanent).state()
                        == TentacleAnimationPolicy.State.GRAB_SUCCESS,
                "state must be stored for the bound entity");
        check(controller.state(temporary).kind() == TentacleAnimationPolicy.Kind.UNDER_PLAYER,
                "temporary registration must retain its under-player kind");
        check(!controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.CONTACT, 117L),
                "contact marker must not fire before its state offset");
        check(controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.CONTACT, 118L),
                "contact marker must use the active grab-success timeline");
        check(controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.HOLD_LOCK, 122L),
                "server marker clock must be independent of client bone position");
        check(!controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE, 122L),
                "a marker owned by another state must not fire");
        check(controller.transition(generation, permanent, TentacleAnimationPolicy.State.THROW,
                        130L, null), "throw state must be accepted");
        check(!controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE, 137L),
                "throw release must wait for its state offset");
        check(controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE, 138L),
                "throw release must use the active throw timeline");
        check(!controller.markerReached(permanent,
                        TentacleAnimationPolicy.Marker.RECOVERY_START, 138L),
                "recovery marker must not fire during throw");
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
