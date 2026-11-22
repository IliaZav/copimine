import java.util.List;
import me.copimine.endevent.domain.TentacleAnimationPolicy;

public final class TentacleAnimationPolicyTest {
    public static void main(String[] args) {
        testAllArtistBriefStatesHaveBoundedDurations();
        testAttackCuesStayInsideTheAuthoredAndArtistTiming();
        testGrabMarkersUseTheSpecifiedTimeline();
        testPlayerHitsDoNotInterruptAttacksOrRestartHitRecovery();
        testAcceptedDamageWindowsMatchTheAuthoredContract();
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

    /**
     * K03 regression: HP may only be deducted inside an authored vulnerability
     * window.  A spammed or previously canceled hit during HIT_RECOVERY, DYING,
     * the committed grab or any other closed window must be refused before the
     * slot ledger is touched, and a temporary ambush Kagune must stay
     * undamageable while it owns a held player.
     *
     * <p>{@code onTentacleGuardianDamage} and {@code applyTentacleGuardianDamage}
     * both gate on this exact predicate before deducting HP, so a regression
     * here is the first place a "spam the dying guardian" bug would surface.</p>
     */
    private static void testAcceptedDamageWindowsMatchTheAuthoredContract() {
        // Open windows: idle, the shield channel, and the heavy-strike counterattack.
        for (TentacleAnimationPolicy.State open : List.of(
                TentacleAnimationPolicy.State.READY,
                TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                TentacleAnimationPolicy.State.SMALL_RECOVERY)) {
            check(TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.PERMANENT, open),
                    "permanent guardian must accept damage in " + open);
        }
        // Every committed, recovering or finished permanent state refuses the hit.
        for (TentacleAnimationPolicy.State closed : List.of(
                TentacleAnimationPolicy.State.EMERGING,
                TentacleAnimationPolicy.State.TELEGRAPH_GRAB,
                TentacleAnimationPolicy.State.GRAB_SUCCESS,
                TentacleAnimationPolicy.State.HOLD,
                TentacleAnimationPolicy.State.THROW,
                TentacleAnimationPolicy.State.MISS_RECOVERY,
                TentacleAnimationPolicy.State.HIT_RECOVERY,
                TentacleAnimationPolicy.State.RECOVERY,
                TentacleAnimationPolicy.State.DYING,
                TentacleAnimationPolicy.State.DEAD_RESPAWN,
                TentacleAnimationPolicy.State.RETRACT,
                TentacleAnimationPolicy.State.SPAWN_UNDER_PLAYER)) {
            check(!TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.PERMANENT, closed),
                    "permanent guardian must refuse damage in " + closed);
        }
        // The committed ambush grab is never a damageable actor.
        for (TentacleAnimationPolicy.State closed : List.of(
                TentacleAnimationPolicy.State.EMERGING,
                TentacleAnimationPolicy.State.TELEGRAPH_GRAB,
                TentacleAnimationPolicy.State.GRAB_SUCCESS,
                TentacleAnimationPolicy.State.HOLD,
                TentacleAnimationPolicy.State.THROW,
                TentacleAnimationPolicy.State.MISS_RECOVERY,
                TentacleAnimationPolicy.State.RECOVERY,
                TentacleAnimationPolicy.State.SPAWN_UNDER_PLAYER)) {
            check(!TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.TEMPORARY, closed),
                    "temporary ambush must refuse damage in " + closed);
            check(!TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.UNDER_PLAYER, closed),
                    "under-player eruption must refuse damage in " + closed);
        }
        // The small melee role has real HP and is killable while it fights.
        for (TentacleAnimationPolicy.State alive : List.of(
                TentacleAnimationPolicy.State.EMERGING,
                TentacleAnimationPolicy.State.READY,
                TentacleAnimationPolicy.State.SHIELD_CHANNEL,
                TentacleAnimationPolicy.State.SMALL_WINDUP,
                TentacleAnimationPolicy.State.SMALL_STRIKE,
                TentacleAnimationPolicy.State.SMALL_RECOVERY)) {
            check(TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.SMALL, alive),
                    "small Kagune must accept damage in " + alive);
        }
        for (TentacleAnimationPolicy.State gone : List.of(
                TentacleAnimationPolicy.State.HIT_RECOVERY,
                TentacleAnimationPolicy.State.DYING,
                TentacleAnimationPolicy.State.DEAD_RESPAWN,
                TentacleAnimationPolicy.State.RETRACT)) {
            check(!TentacleAnimationPolicy.acceptsDamage(
                            TentacleAnimationPolicy.Kind.SMALL, gone),
                    "small Kagune must refuse damage in " + gone);
        }
        // Missing identity fails closed rather than defaulting to vulnerable.
        check(!TentacleAnimationPolicy.acceptsDamage(
                        null, TentacleAnimationPolicy.State.READY),
                "a missing kind must refuse damage");
        check(!TentacleAnimationPolicy.acceptsDamage(
                        TentacleAnimationPolicy.Kind.PERMANENT, null),
                "a missing state must refuse damage");
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
