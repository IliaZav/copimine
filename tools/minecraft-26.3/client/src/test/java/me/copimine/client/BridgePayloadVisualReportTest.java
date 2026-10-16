package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BridgePayloadVisualReportTest {
    @Test
    void clientVisualReportsKeepTheirRequiredZeroDuration() {
        assertEquals(0, BridgePayload.visualAck("session", 1, "INVERT", "STARTED").durationMillis());
        assertEquals(0, BridgePayload.visualFinished("session", 2, "INVERT", "expired").durationMillis());
        assertEquals(0, BridgePayload.visualError("session", 3, "INVERT", "unavailable").durationMillis());
    }

    @Test
    void visualReportConstructorCannotEncodeAnApplicationDuration() {
        BridgePayload report = new BridgePayload(
                ClientBridgeProtocol.TYPE_VISUAL_ACK,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                1,
                1,
                "session",
                "",
                false,
                false,
                false,
                false,
                java.util.Set.of(),
                "INVERT",
                "",
                30_000,
                0.5F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                "STARTED");

        assertEquals(0, report.durationMillis());
    }
}
