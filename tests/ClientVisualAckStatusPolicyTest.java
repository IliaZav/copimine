import me.copimine.clientbridge.ClientVisualAckStatusPolicy;

public final class ClientVisualAckStatusPolicyTest {
    public static void main(String[] args) {
        check(!ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_IRIS_SHADERPACK"),
                "Iris route should not report a fallback");
        check(!ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_TRANSITION_DEFERRED"),
                "deferred runtime transition should not report a permanent fallback");
        check(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_CLIENT_OVERLAY"),
                "client overlay route should report a non-Iris fallback");
        check(ClientVisualAckStatusPolicy.shouldReportNonIrisFallback("STARTED_FALLBACK_POST_PROCESS"),
                "post-process route should report a non-Iris fallback");
        System.out.println("Client visual ACK status policy tests passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
