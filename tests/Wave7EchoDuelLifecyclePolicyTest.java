import java.util.OptionalLong;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;

public final class Wave7EchoDuelLifecyclePolicyTest {
    public static void main(String[] args) {
        bedRespawnRequiresExplicitCombatAdmission();
        reconnectAndRepeatedDisconnectKeepOriginalDeadline();
        expiredDeadlineCannotBeExtended();
        onlineButUnadmittedOwnerStillExpires();
        System.out.println("Wave7EchoDuelLifecyclePolicyTest OK");
    }

    private static void bedRespawnRequiresExplicitCombatAdmission() {
        check(!RealitySplitEchoDuelState.ownerMayResume(true, true, true, true, false),
                "a bed-respawned but not admitted owner cannot resume Echo teleport or combat");
        check(RealitySplitEchoDuelState.ownerMayResume(true, true, true, true, true),
                "an online, alive, ready owner resumes only after explicit admission");
        check(!RealitySplitEchoDuelState.ownerMayResume(false, true, true, true, true), "offline owner cannot resume");
        check(!RealitySplitEchoDuelState.ownerMayResume(true, false, true, true, true), "dead owner cannot resume");
        check(!RealitySplitEchoDuelState.ownerMayResume(true, true, false, true, true), "owner in another world cannot resume");
        check(!RealitySplitEchoDuelState.ownerMayResume(true, true, true, false, true), "owner without a ready client bridge cannot resume");
    }

    private static void reconnectAndRepeatedDisconnectKeepOriginalDeadline() {
        long firstDisconnectAt = 1_800_000_000_000L;
        long graceMillis = 120_000L;
        long originalDeadline = firstDisconnectAt + graceMillis;
        OptionalLong initial = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                0L, firstDisconnectAt, graceMillis);
        check(initial.isPresent() && initial.getAsLong() == originalDeadline,
                "first disconnect initializes the offline deadline");

        OptionalLong repeated = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                originalDeadline, firstDisconnectAt + 30_000L, graceMillis);
        check(repeated.isPresent() && repeated.getAsLong() == originalDeadline,
                "reconnect followed by another disconnect preserves the original deadline");
    }

    private static void expiredDeadlineCannotBeExtended() {
        OptionalLong expired = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                1_800_000_120_000L, 1_800_000_120_000L, 120_000L);
        check(expired.isEmpty(), "a reconnect after the original deadline fails closed");
        OptionalLong overflow = RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(
                0L, 253_402_300_799_999L, 120_000L);
        check(overflow.isEmpty(), "deadline arithmetic cannot overflow the supported epoch range");
    }

    private static void onlineButUnadmittedOwnerStillExpires() {
        long deadline = 1_800_000_120_000L;
        check(RealitySplitEchoDuelState.ownerReturnDeadlineExpired(
                        RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE, deadline, deadline),
                "an online but unadmitted owner cannot keep an expired Echo duel frozen");
        check(!RealitySplitEchoDuelState.ownerReturnDeadlineExpired(
                        RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE, deadline, deadline - 1L),
                "the original return window remains usable until its deadline");
        check(!RealitySplitEchoDuelState.ownerReturnDeadlineExpired(
                        RealitySplitEchoDuelState.Lifecycle.ACTIVE, deadline, deadline + 1L),
                "an admitted active owner is not expired by the offline return deadline");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
