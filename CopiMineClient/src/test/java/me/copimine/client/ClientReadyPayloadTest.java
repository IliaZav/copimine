package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientReadyPayloadTest {
    @Test
    void ready_payload_contains_only_protocol_and_diagnostic_client_version() {
        assertArrayEquals(
                new String[]{"protocolVersion", "clientVersion"},
                java.util.Arrays.stream(ClientReadyPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toArray(String[]::new));
        ClientReadyPayload payload = new ClientReadyPayload(3, "0.1.0");
        assertEquals(3, payload.protocolVersion());
        assertEquals("0.1.0", payload.clientVersion());
    }

    @Test
    void ack_payload_contains_a_bounded_decision_and_diagnostic_protocols() {
        assertArrayEquals(
                new String[]{"accepted", "requiredProtocol", "receivedProtocol", "reason"},
                java.util.Arrays.stream(ClientReadyAckPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toArray(String[]::new));
        ClientReadyAckPayload payload = new ClientReadyAckPayload(false, 3, 2, "PROTOCOL_MISMATCH");
        assertEquals("PROTOCOL_MISMATCH", payload.reason());
    }
}
