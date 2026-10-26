import me.copimine.endevent.domain.VictorySagaRecoveryPolicy;

public final class VictorySagaRecoveryPolicyTest {
    public static void main(String[] args) {
        testRequestedReturnStoneIsRetriedAfterRestart();
        testFinalReturnStoneStatusesArePreserved();
        testCallbackIsFencedToItsAttempt();
        System.out.println("VictorySagaRecoveryPolicyTest OK");
    }

    private static void testRequestedReturnStoneIsRetriedAfterRestart() {
        check("PENDING_RETRY".equals(VictorySagaRecoveryPolicy.restoredReturnStoneStatus("REQUESTED")),
                "a REQUESTED return stone has no live future after restart and must be retried");
        check("PENDING_RETRY".equals(VictorySagaRecoveryPolicy.restoredReturnStoneStatus("PENDING_RETRY")),
                "an already pending retry must stay pending");
        check("PENDING".equals(VictorySagaRecoveryPolicy.restoredReturnStoneStatus("PENDING")),
                "an unrequested return stone must stay pending");
    }

    private static void testFinalReturnStoneStatusesArePreserved() {
        check("DELIVERED".equals(VictorySagaRecoveryPolicy.restoredReturnStoneStatus("DELIVERED")),
                "a delivered return stone must never be re-issued");
        check("ALREADY_ISSUED".equals(VictorySagaRecoveryPolicy.restoredReturnStoneStatus("ALREADY_ISSUED")),
                "an already issued return stone must never be re-issued");
        check(VictorySagaRecoveryPolicy.restoredReturnStoneStatus(null) == null,
                "a missing status must not be invented");
    }

    private static void testCallbackIsFencedToItsAttempt() {
        check(VictorySagaRecoveryPolicy.callbackIsCurrent("event-a", 7L, "event-a", 7L),
                "the current attempt must accept its own reward callback");
        check(!VictorySagaRecoveryPolicy.callbackIsCurrent("event-a", 7L, "event-a", 8L),
                "a callback from a wiped generation must be dropped");
        check(!VictorySagaRecoveryPolicy.callbackIsCurrent("event-a", 7L, "event-b", 7L),
                "a callback from another event must be dropped");
        check(!VictorySagaRecoveryPolicy.callbackIsCurrent(null, 7L, null, 7L),
                "a callback without an event id must be dropped");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
