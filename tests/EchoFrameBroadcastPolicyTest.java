import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoFrameBroadcastPolicy;

/**
 * NET-001: the echo presentation broadcast used to sign one packet per viewer
 * per tick, even for a copy that had not visibly changed. These assertions drive
 * the real dedupe policy: a repeat is suppressed, a transition is immediate, and
 * a keepalive still refreshes the client lease.
 */
public final class EchoFrameBroadcastPolicyTest {
    private static final UUID VIEWER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID VIEWER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID ACTOR_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static void main(String[] args) {
        signatureDropsTheCountersTheClientOwns();
        signatureDetectsEveryVisibleTransition();
        unknownFieldShapeDegradesToAlwaysSend();
        firstFrameIsAlwaysSent();
        repeatedFrameIsSuppressedUntilKeepalive();
        transitionIsImmediateEvenInsideKeepaliveWindow();
        viewersAndActorsAreIndependent();
        backwardsClockFailsOpen();
        forgetAndClearReArmTheViewer();
        nothingIsRecordedUnlessRecorded();
        System.out.println("EchoFrameBroadcastPolicyTest OK");
    }

    private static String fields(String owner, String epoch, String sequence, String pose, String sprint,
                                 String hand, String useElapsed, String useDuration, String swing,
                                 String hurt, String death, String equipment) {
        return owner + "|" + epoch + "|" + sequence + "|" + pose + "|" + sprint + "|" + hand + "|"
                + useElapsed + "|" + useDuration + "|" + swing + "|" + hurt + "|" + death + "|" + equipment;
    }

    private static String idle(String sequence, String useElapsed) {
        return fields("owner-a", "7", sequence, "STANDING", "0", "NONE", useElapsed, "0", "0", "0", "0", "3");
    }

    private static void signatureDropsTheCountersTheClientOwns() {
        String first = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        String second = EchoFrameBroadcastPolicy.signatureOf(idle("2", "0"));
        check(first.equals(second), "a new sequence number alone must not count as a visible change");
        String advanced = EchoFrameBroadcastPolicy.signatureOf(idle("2", "9"));
        check(first.equals(advanced),
                "the extrapolated use-elapsed counter alone must not count as a visible change");

        String[] parts = first.split("\\|", -1);
        check(parts.length == 10, "the signature keeps ten of the twelve frame fields, saw " + parts.length);
        check(!first.contains("|1|") || !first.startsWith("owner-a|7|1|"),
                "the sequence value must not survive at its old position");
    }

    private static void signatureDetectsEveryVisibleTransition() {
        String base = EchoFrameBroadcastPolicy.signatureOf(idle("5", "0"));
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "CROUCHING", "0", "NONE", "0", "0", "0", "0", "0", "3"))),
                "crouching is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "1", "NONE", "0", "0", "0", "0", "0", "3"))),
                "sprinting is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "0", "MAIN", "0", "32", "0", "0", "0", "3"))),
                "starting a bow draw is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "0", "NONE", "0", "0", "1", "0", "0", "3"))),
                "a new swing serial is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "0", "NONE", "0", "0", "0", "1", "0", "3"))),
                "a new hurt serial is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "0", "NONE", "0", "0", "0", "0", "4", "3"))),
                "death progress is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-a", "7", "6", "STANDING", "0", "NONE", "0", "0", "0", "0", "0", "4"))),
                "an equipment change is visible");
        check(!base.equals(EchoFrameBroadcastPolicy.signatureOf(
                        fields("owner-b", "7", "6", "STANDING", "0", "NONE", "0", "0", "0", "0", "0", "3"))),
                "re-skinning to another owner is visible");
    }

    private static void unknownFieldShapeDegradesToAlwaysSend() {
        String odd = "owner|1|2|3";
        check(EchoFrameBroadcastPolicy.signatureOf(odd).equals(odd),
                "an unknown field shape must be passed through so it is never suppressed");
        check(EchoFrameBroadcastPolicy.signatureOf(null).isEmpty(), "a null field list is empty, not a crash");
    }

    private static void firstFrameIsAlwaysSent() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 100L), "an unwatched copy must be sent at once");
        check(!policy.shouldSend(null, ACTOR_ONE, signature, 100L), "no viewer means no send");
        check(!policy.shouldSend(VIEWER_A, null, signature, 100L), "no actor means no send");
        check(!policy.shouldSend(VIEWER_A, ACTOR_ONE, null, 100L), "no signature means no send");
    }

    private static void repeatedFrameIsSuppressedUntilKeepalive() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 100L), "first frame is due");
        policy.record(VIEWER_A, ACTOR_ONE, signature, 100L);
        check(!policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 100L + 5L), "an immediate repeat is suppressed");
        check(!policy.shouldSend(VIEWER_A, ACTOR_ONE, signature,
                        100L + EchoFrameBroadcastPolicy.KEEPALIVE_TICKS - 1L),
                "a repeat inside the keepalive window is suppressed");
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 100L + EchoFrameBroadcastPolicy.KEEPALIVE_TICKS),
                "a repeat at the keepalive boundary keeps the client lease alive");
        check(EchoFrameBroadcastPolicy.KEEPALIVE_TICKS > 0L
                        && EchoFrameBroadcastPolicy.KEEPALIVE_TICKS < 40L,
                "the keepalive must fit inside the client's two-second lease at twenty ticks a second");
    }

    private static void transitionIsImmediateEvenInsideKeepaliveWindow() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String standing = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        String crouching = EchoFrameBroadcastPolicy.signatureOf(
                fields("owner-a", "7", "2", "CROUCHING", "0", "NONE", "0", "0", "0", "0", "0", "3"));
        policy.record(VIEWER_A, ACTOR_ONE, standing, 100L);
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, crouching, 101L),
                "a crouch must not wait for the keepalive");
    }

    private static void viewersAndActorsAreIndependent() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        policy.record(VIEWER_A, ACTOR_ONE, signature, 100L);
        check(policy.shouldSend(VIEWER_B, ACTOR_ONE, signature, 101L),
                "a second viewer has its own memory");
        check(policy.shouldSend(VIEWER_A, ACTOR_TWO, signature, 101L),
                "a second copy shown to the same viewer is not suppressed by the first");
        check(policy.tracked() == 1, "only recorded pairs are tracked, saw " + policy.tracked());
    }

    private static void backwardsClockFailsOpen() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        policy.record(VIEWER_A, ACTOR_ONE, signature, 500L);
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 10L),
                "a clock that moved backwards must re-send rather than suppress forever");
    }

    private static void forgetAndClearReArmTheViewer() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        policy.record(VIEWER_A, ACTOR_ONE, signature, 100L);
        policy.record(VIEWER_B, ACTOR_ONE, signature, 100L);
        policy.forget(VIEWER_A);
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 101L), "forget re-arms that viewer");
        check(!policy.shouldSend(VIEWER_B, ACTOR_ONE, signature, 101L), "forget leaves other viewers alone");
        policy.clear();
        check(policy.tracked() == 0, "clear drops every pair");
        check(policy.shouldSend(VIEWER_B, ACTOR_ONE, signature, 102L), "clear re-arms every viewer");
    }

    private static void nothingIsRecordedUnlessRecorded() {
        EchoFrameBroadcastPolicy policy = new EchoFrameBroadcastPolicy();
        String signature = EchoFrameBroadcastPolicy.signatureOf(idle("1", "0"));
        // A send the transport refused must not be remembered, or a dropped
        // packet would be suppressed instead of retried on the next tick.
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 100L), "an unrecorded frame stays due");
        check(policy.shouldSend(VIEWER_A, ACTOR_ONE, signature, 101L), "a refused send is retried");
        policy.record(null, ACTOR_ONE, signature, 100L);
        policy.record(VIEWER_A, null, signature, 100L);
        check(policy.tracked() == 0, "an incomplete pair must never be recorded");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}