import java.util.List;
import me.copimine.endevent.domain.TentacleAnimationPolicy;

public final class TentacleAnimationPolicyTest {
    public static void main(String[] args) {
        testAllArtistBriefStatesHaveBoundedDurations();
        testAttackCuesStayInsideTheAuthoredAndArtistTiming();
        testGrabMarkersUseTheSpecifiedTimeline();
        testPlayerHitsDoNotInterruptAttacksOrRestartHitRecovery();
        testTransitionsKeepTheServerSequenceDeterministic();
        testStaticShowroomStartsPermanentTentaclesInTheLoopingIdleState();
        System.out.println("TentacleAnimationPolicyTest OK");
    }

    private static void testStaticShowroomStartsPermanentTentaclesInTheLoopingIdleState() {
        TentacleAnimationPolicy.State showroomState = TentacleAnimationPolicy.initialSpawnState(
                TentacleAnimationPolicy.Kind.PERMANENT, true);
        check(showroomState == TentacleAnimationPolicy.State.READY,
                "static showroom permanent tentacles must start on the authored idle clip");
        check(TentacleAnimationPolicy.loops(showroomState),
                "static showroom idle must keep looping while combat ticks are paused");
        check(TentacleAnimationPolicy.initialSpawnState(
                        TentacleAnimationPolicy.Kind.PERMANENT, false)
                        == TentacleAnimationPolicy.State.EMERGING,
                "live permanent guardians must keep their emergence animation");
        check(TentacleAnimationPolicy.initialSpawnState(
                        TentacleAnimationPolicy.Kind.TEMPORARY, true)
                        == TentacleAnimationPolicy.State.EMERGING,
                "a temporary tentacle must not inherit the permanent showroom idle state");
    }

    private static void testAllArtistBriefStatesHaveBoundedDurations() {
        for (TentacleAnimationPolicy.State state : TentacleAnimationPolicy.State.values()) {
            int ticks = TentacleAnimationPolicy.durationTicks(state);
            check(ticks >= 5 && ticks <= 80,
                    state + " must have a bounded artist-brief duration");
        }
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.READY),
                "ready must loop");
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.HOLD),
                "hold must loop while the server keeps the player locked");
        check(TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.SHIELD_CHANNEL),
                "shield channel must loop");
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.RECOVERY) == 16,
                "recovery must have its own bounded animation");
        check(!TentacleAnimationPolicy.loops(TentacleAnimationPolicy.State.THROW),
                "throw must be a one-shot animation");
    }

    private static void testAttackCuesStayInsideTheAuthoredAndArtistTiming() {
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.TELEGRAPH_GRAB) == 24,
                "telegraph must match the 0.8-1.2 second artist brief");
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.GRAB_SUCCESS) == 16,
                "grab success must match the 0.55-0.8 second artist brief");
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.THROW) == 14,
                "throw must not outlast the 0.7 second artist brief");
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.MISS_RECOVERY) == 16,
                "miss recovery must fit the 0.5-0.8 second artist brief");
        check(TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.DYING) == 24,
                "death must fit the 0.8-1.2 second artist brief");
    }

    private static void testPlayerHitsDoNotInterruptAttacksOrRestartHitRecovery() {
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.TELEGRAPH_GRAB),
                "a hit must not cancel the telegraphed grab");
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.GRAB_SUCCESS),
                "a hit must not cancel the committed grab");
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.HOLD),
                "a hit must not cancel a held player");
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.THROW),
                "a hit must not cancel a committed throw");
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.HIT_RECOVERY),
                "repeated hits must not restart the flinch timer");
        check(!TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.RECOVERY),
                "a hit must not restart the recovery timer");
        check(TentacleAnimationPolicy.shouldEnterHitRecovery(
                        TentacleAnimationPolicy.State.SHIELD_CHANNEL),
                "an idle guardian can play one bounded hit reaction");
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
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.State.GRAB_SUCCESS,
                        TentacleAnimationPolicy.Marker.CONTACT) == 10,
                "contact must land at about 60 percent of grab success");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.State.GRAB_SUCCESS,
                        TentacleAnimationPolicy.Marker.HOLD_LOCK) == 14,
                "hold lock must land near the end of grab success");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.State.THROW,
                        TentacleAnimationPolicy.Marker.THROW_RELEASE) == 9,
                "throw release must land between 60 and 70 percent of throw");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.State.RECOVERY,
                        TentacleAnimationPolicy.Marker.RECOVERY_START) == 0,
                "recovery starts at the recovery state boundary");
        check(TentacleAnimationPolicy.markerTick(TentacleAnimationPolicy.State.RETRACT,
                        TentacleAnimationPolicy.Marker.HIDE_BELOW_FLOOR) == 18,
                "temporary retraction hides at its end marker");
    }

    private static void testTransitionsKeepTheServerSequenceDeterministic() {
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.TEMPORARY,
                        TentacleAnimationPolicy.State.READY, true)
                        == TentacleAnimationPolicy.State.TELEGRAPH_GRAB,
                "ready must telegraph before contact");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.TEMPORARY,
                        TentacleAnimationPolicy.State.TELEGRAPH_GRAB, true)
                        == TentacleAnimationPolicy.State.GRAB_SUCCESS,
                "successful contact must enter grab success");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.TEMPORARY,
                        TentacleAnimationPolicy.State.TELEGRAPH_GRAB, false)
                        == TentacleAnimationPolicy.State.MISS_RECOVERY,
                "missed contact must enter miss recovery");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.PERMANENT,
                        TentacleAnimationPolicy.State.EMERGING, false)
                        == TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                "permanent emergence must enter shield channel");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.PERMANENT,
                        TentacleAnimationPolicy.State.DYING, false)
                        == TentacleAnimationPolicy.State.DEAD_RESPAWN,
                "death must enter the bounded dead/respawn state");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.TEMPORARY,
                        TentacleAnimationPolicy.State.THROW, true)
                        == TentacleAnimationPolicy.State.RECOVERY,
                "throw must enter the recovery animation");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.PERMANENT,
                        TentacleAnimationPolicy.State.RECOVERY, false)
                        == TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                "permanent recovery must return to shield channel");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.UNDER_PLAYER,
                        TentacleAnimationPolicy.State.SPAWN_UNDER_PLAYER, false)
                        == TentacleAnimationPolicy.State.RECOVERY,
                "under-player eruption must enter recovery after emergence");
        check(TentacleAnimationPolicy.next(TentacleAnimationPolicy.Kind.UNDER_PLAYER,
                        TentacleAnimationPolicy.State.RECOVERY, false)
                        == TentacleAnimationPolicy.State.RETRACT,
                "under-player recovery must retract instead of becoming permanent");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
