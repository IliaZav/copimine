import me.copimine.clientbridge.ClientReadyAdmission;
import me.copimine.clientbridge.ClientReadyPayloads;

import java.util.UUID;

public final class ClientReadyAdmissionHarness {
    public static void main(String[] args) {
        ClientReadyAdmission admission = new ClientReadyAdmission(15_000L);
        UUID player = UUID.randomUUID();
        ClientReadyAdmission.PendingClientAdmission first = admission.onJoin(player, 0L);
        require(admission.onReady(player, new ClientReadyAdmission.ReadyRequest(3, "0.1.0"), 800L).decision()
                == ClientReadyAdmission.Decision.ACCEPTED, "compatible READY must be accepted");
        require(admission.onReady(player, new ClientReadyAdmission.ReadyRequest(3, "0.1.0"), 900L).decision()
                == ClientReadyAdmission.Decision.DUPLICATE_ACCEPTED, "duplicate READY must be idempotent");
        require(admission.onTimeout(player, first.joinAttemptId(), 15_001L).decision()
                == ClientReadyAdmission.Decision.ALREADY_ACCEPTED, "accepted player must not be kicked by timeout");

        UUID mismatchPlayer = UUID.randomUUID();
        admission.onJoin(mismatchPlayer, 0L);
        ClientReadyAdmission.ReadyDecision mismatch = admission.onReady(
                mismatchPlayer,
                new ClientReadyAdmission.ReadyRequest(2, "0.0.1"),
                500L);
        require(mismatch.decision() == ClientReadyAdmission.Decision.PROTOCOL_MISMATCH, "wrong protocol must be diagnosed");
        require("PROTOCOL_MISMATCH".equals(mismatch.ack().reason()), "wrong protocol reason must be stable");

        UUID timeoutPlayer = UUID.randomUUID();
        ClientReadyAdmission.PendingClientAdmission timeoutAttempt = admission.onJoin(timeoutPlayer, 0L);
        require(admission.onTimeout(timeoutPlayer, timeoutAttempt.joinAttemptId(), 15_001L).decision()
                == ClientReadyAdmission.Decision.TIMEOUT, "missing READY must time out");
        require(admission.onReady(timeoutPlayer, new ClientReadyAdmission.ReadyRequest(3, "0.1.0"), 15_002L).decision()
                == ClientReadyAdmission.Decision.AFTER_TIMEOUT, "late READY must be diagnosed");

        UUID reconnectPlayer = UUID.randomUUID();
        ClientReadyAdmission.PendingClientAdmission oldAttempt = admission.onJoin(reconnectPlayer, 0L);
        ClientReadyAdmission.PendingClientAdmission currentAttempt = admission.onJoin(reconnectPlayer, 100L);
        require(admission.onTimeout(reconnectPlayer, oldAttempt.joinAttemptId(), 20_000L).decision()
                == ClientReadyAdmission.Decision.STALE_ATTEMPT, "old timeout must be ignored");
        require(admission.onReady(reconnectPlayer, new ClientReadyAdmission.ReadyRequest(3, "0.1.0"), 900L).state().joinAttemptId()
                .equals(currentAttempt.joinAttemptId()), "current reconnect must remain authoritative");

        try {
            ClientReadyAdmission.ReadyRequest decoded = ClientReadyPayloads.decodeReady(ClientReadyPayloads.encodeReady(3, "0.1.0"));
            require(decoded.protocolVersion() == 3 && "0.1.0".equals(decoded.clientVersion()), "READY wire codec must round-trip");
            byte[] malformed = java.util.Arrays.copyOf(ClientReadyPayloads.encodeReady(3, "0.1.0"), 32);
            boolean rejected = false;
            try {
                ClientReadyPayloads.decodeReady(malformed);
            } catch (ClientReadyPayloads.MalformedReadyException expected) {
                rejected = true;
            }
            require(rejected, "malformed READY bytes must be rejected");
        } catch (Exception error) {
            throw new AssertionError("READY wire codec failed", error);
        }

        for (int index = 0; index < 100; index++) {
            UUID stressPlayer = UUID.randomUUID();
            admission.onJoin(stressPlayer, index * 20_000L);
            require(admission.onReady(stressPlayer, new ClientReadyAdmission.ReadyRequest(3, "0.1.0"), index * 20_000L + 800L).decision()
                    == ClientReadyAdmission.Decision.ACCEPTED, "stress READY must be accepted at index " + index);
        }
        System.out.println("ClientReadyAdmissionHarness PASS: normal=1 duplicate=1 mismatch=1 timeout=1 stale=1 stress=100");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
