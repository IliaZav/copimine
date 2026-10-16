package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.junit.jupiter.api.Test;

class ClientVisualCommandPolicyTest {
    @Test
    void disabledServerVisualsRejectStartsButAllowAuthenticatedCleanup() {
        assertFalse(ClientVisualCommandPolicy.shouldApply(
                false, NarcoticsBridgeBodyCodec.Type.VISUAL_START));
        assertTrue(ClientVisualCommandPolicy.shouldApply(
                false, NarcoticsBridgeBodyCodec.Type.VISUAL_STOP));
        assertTrue(ClientVisualCommandPolicy.shouldApply(
                false, NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL));
    }

    @Test
    void enabledServerVisualsAllowEveryVisualCommand() {
        for (NarcoticsBridgeBodyCodec.Type type : NarcoticsBridgeBodyCodec.Type.values()) {
            assertTrue(ClientVisualCommandPolicy.shouldApply(true, type));
        }
    }
}
