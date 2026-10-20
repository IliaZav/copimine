import me.copimine.endevent.domain.IntermissionWatchdogPolicy;

public final class IntermissionWatchdogPolicyTest {
    public static void main(String[] args) {
        long now = 1_000_000L;
        long missing = IntermissionWatchdogPolicy.recoverDeadline(0L, now);
        check(missing == now + 600_000L,
                "a restored intermission without a deadline gets the documented ten-minute watchdog");
        check(IntermissionWatchdogPolicy.recoverDeadline(now + 12_000L, now) == now + 12_000L,
                "a persisted deadline remains authoritative after restart");
        check(!IntermissionWatchdogPolicy.isExpired(missing - 1L, missing),
                "the watchdog must not expire before its deadline");
        check(IntermissionWatchdogPolicy.isExpired(missing, missing),
                "the watchdog expires exactly at its deadline");
        check(!IntermissionWatchdogPolicy.isExpired(now, 0L),
                "an absent deadline is not treated as already expired");
        System.out.println("IntermissionWatchdogPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
