import java.util.List;
import me.copimine.endevent.domain.TentacleAnimationPolicy;

public final class TentacleAnimationPolicyTest {
    public static void main(String[] args) {
        testAllArtistBriefStatesHaveBoundedDurations();
        testGrabMarkersUseTheSpecifiedTimeline();
        testTransitionsKeepTheServerSequenceDeterministic();
        System.out.println("TentacleAnimationPolicyTest OK");
    }

    private static void testAllArtistBriefStatesHaveBoundedDurations() {
        for (TentacleAnimationPolicy.State state : TentacleAnimationPolicy.State.values()) {
            int ticks = TentacleAnimationPolicy.durationTicks(state);
            check(ticks >= 5 && ticks <= 80,
                    state + " must have a bounded artist-brief duration");
        }
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.IDLE),
                "idle must loop");
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.HOLD),
                "hold must loop while the server keeps the player locked");
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.SHIELD_CHANNEL),
                "shield channel must loop");
        check(!TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.THROW),
                "throw must be a one-shot animation");
    }

    private static void testGrabMarkersUseTheSpecifiedTimeline() {
        List<TentacleAnimationPolicy.Marker> sequence = TentacleAnimationPolicy.grabMarkers();
        check(sequence.equals(List.of(
                        TentacleAnimationPolicy.Marker.CONTACT,
                        TentacleAnimationPolicy.Marker.HOLD_LOCK,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE,
                        TentacleAnimationPolicy.Marker.RECOVERY_START,
                        TentacleAnimationPolicy.Marker.HIDE_BELOW_FLOOR)),
                "grab markers must be ordered from contact to retract");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.Marker.CONTACT) == 0,
                "contact is the start of the server grab sequence");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.Marker.HOLD_LOCK) == 6,
                "hold lock is approximately 0.3 seconds");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.Marker.THROW_RELEASE) == 20,
                "throw release is approximately 1 second");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.Marker.RECOVERY_START) == 24,
                "recovery starts after release");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.Marker.HIDE_BELOW_FLOOR) == 36,
                "retract hides below the floor at approximately 1.8 seconds");
    }

    private static void testTransitionsKeepTheServerSequenceDeterministic() {
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.State.IDLE, true)
                        == TentacleAnimationPolicy.State.TELEGRAPH_GRAB,
                "idle must telegraph before contact");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.State.TELEGRAPH_GRAB, true)
                        == TentacleAnimationPolicy.State.GRAB_SUCCESS,
                "successful contact must enter grab success");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.State.TELEGRAPH_GRAB, false)
                        == TentacleAnimationPolicy.State.GRAB_MISS,
                "missed contact must enter grab miss");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.State.THROW, true)
                        == TentacleAnimationPolicy.State.RETRACT,
                "throw must recover through retract");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
