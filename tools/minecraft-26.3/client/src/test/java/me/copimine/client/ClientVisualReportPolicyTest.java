package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.junit.jupiter.api.Test;

final class ClientVisualReportPolicyTest {
    @Test
    void visualCommandsUsePaperCorrelationStatuses() {
        assertEquals("STARTED", ClientVisualReportPolicy.ackStatus(NarcoticsBridgeBodyCodec.Type.VISUAL_START));
        assertEquals("STOPPED", ClientVisualReportPolicy.ackStatus(NarcoticsBridgeBodyCodec.Type.VISUAL_STOP));
        assertEquals("CLEARED", ClientVisualReportPolicy.ackStatus(NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL));
    }

    @Test
    void rejectedStopAndClearCommandsDoNotEmitVisualStartErrors() {
        assertTrue(ClientVisualReportPolicy.shouldReportStartError(NarcoticsBridgeBodyCodec.Type.VISUAL_START));
        assertFalse(ClientVisualReportPolicy.shouldReportStartError(NarcoticsBridgeBodyCodec.Type.VISUAL_STOP));
        assertFalse(ClientVisualReportPolicy.shouldReportStartError(NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL));
    }

    @Test
    void rejectionReasonsAreExplicitAndNeverBlank() {
        assertEquals("visual-manager-unavailable", ClientVisualReportPolicy.errorReason(false, ""));
        assertEquals("server-visuals-disabled", ClientVisualReportPolicy.errorReason(true, "server-visuals-disabled"));
        assertEquals("visual-capacity-limit", ClientVisualReportPolicy.errorReason(true, "visual-capacity-limit"));
        assertEquals("visual-effect-mismatch", ClientVisualReportPolicy.errorReason(true, "visual-effect-mismatch"));
        assertEquals("visual-application-rejected", ClientVisualReportPolicy.errorReason(true, "  "));
    }
}
