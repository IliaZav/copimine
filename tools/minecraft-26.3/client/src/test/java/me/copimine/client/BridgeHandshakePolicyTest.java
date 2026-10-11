package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeHandshakePolicyTest {
    private static final String ACTIVE_SESSION = "6f893b31-e88d-4237-b02a-41c7fa8af5ef";

    @Test
    void acceptsOnlyAcknowledgementForSentHelloOnCurrentConnection() {
        assertTrue(BridgeHandshakePolicy.acceptsAcknowledgement(true, true, ACTIVE_SESSION, ACTIVE_SESSION));
        assertFalse(BridgeHandshakePolicy.acceptsAcknowledgement(false, true, ACTIVE_SESSION, ACTIVE_SESSION));
        assertFalse(BridgeHandshakePolicy.acceptsAcknowledgement(true, false, ACTIVE_SESSION, ACTIVE_SESSION));
        assertFalse(BridgeHandshakePolicy.acceptsAcknowledgement(true, true, ACTIVE_SESSION,
                "b4bfc0f3-fb42-450e-a6bd-98087b36b55c"));
        assertFalse(BridgeHandshakePolicy.acceptsAcknowledgement(true, true, "", ACTIVE_SESSION));
    }
}
