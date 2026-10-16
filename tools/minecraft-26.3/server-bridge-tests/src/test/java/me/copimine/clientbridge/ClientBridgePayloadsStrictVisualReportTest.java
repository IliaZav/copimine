package me.copimine.clientbridge;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

final class ClientBridgePayloadsStrictVisualReportTest {
    private static final String SESSION = "22e8762a-a01e-4d45-80ee-8bf5b12262fa";

    @Test
    void acceptsExactClientVisualReportWireFieldsFromFactories() throws Exception {
        ClientBridgePayloads.Message ack = ClientBridgePayloads.decodeStrictVisualReport(
                clientReport(ClientBridgePayloads.VISUAL_ACK, "INVERT", "STARTED_POST_PROCESS", "", ""));
        assertEquals(ClientBridgePayloads.VISUAL_ACK, ack.type());
        assertEquals(SESSION, ack.sessionId());
        assertEquals("INVERT", ack.effectId());
        assertEquals("STARTED_POST_PROCESS", ack.status());

        ClientBridgePayloads.Message finished = ClientBridgePayloads.decodeStrictVisualReport(
                clientReport(ClientBridgePayloads.VISUAL_FINISHED, "INVERT", "", "natural_end", ""));
        assertEquals("natural_end", finished.reason());

        ClientBridgePayloads.Message error = ClientBridgePayloads.decodeStrictVisualReport(
                clientReport(ClientBridgePayloads.VISUAL_ERROR, "INVERT", "ERROR", "pipeline_error", ""));
        assertEquals("ERROR", error.status());
        assertEquals("pipeline_error", error.reason());
    }

    @Test
    void rejectsNonDefaultFieldsAndTrailingBytesWithoutNormalizingThem() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ClientBridgePayloads.decodeStrictVisualReport(
                clientReport(ClientBridgePayloads.VISUAL_ACK, "INVERT", "STARTED", "", "", 1)));
        assertThrows(IllegalArgumentException.class, () -> ClientBridgePayloads.decodeStrictVisualReport(
                clientReport(ClientBridgePayloads.VISUAL_ACK, "INVERT", "STARTED_BAD ROUTE", "", "")));
        byte[] valid = clientReport(ClientBridgePayloads.VISUAL_ACK, "INVERT", "STARTED", "", "");
        byte[] withTrailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ClientBridgePayloads.decodeStrictVisualReport(withTrailing));
    }

    /** Mirrors BridgePayload.write's field order and the visual report factory defaults. */
    private static byte[] clientReport(String type, String effect, String status, String reason,
                                       String clearPolicy) throws IOException {
        return clientReport(type, effect, status, reason, clearPolicy, 0);
    }

    private static byte[] clientReport(String type, String effect, String status, String reason,
                                       String clearPolicy, int durationMillis) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(type);
            output.writeInt(ClientBridgePayloads.PROTOCOL_VERSION);
            output.writeLong(1L);
            output.writeLong(1_700_000_000_000L);
            output.writeUTF(SESSION);
            output.writeUTF("");
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeInt(0);
            output.writeUTF(effect);
            output.writeUTF("");
            output.writeInt(durationMillis);
            output.writeFloat(0.0F);
            output.writeInt(0);
            output.writeInt(0);
            output.writeUTF("CLIENT_MOD");
            output.writeUTF(clearPolicy);
            output.writeUTF("SYSTEM");
            output.writeUTF(reason);
            output.writeUTF(status);
        }
        return bytes.toByteArray();
    }
}
