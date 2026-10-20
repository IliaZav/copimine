package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBridgeProtocolLegacyTrustTest {
    @Test
    void rejectsAllUnsignedLegacyMutationCommandsButKeepsReportsAndHandshakeMessages() {
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("END_EVENT:END_CONTROL_START"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("END_EVENT:END_ECHO_BIND"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("END_EVENT:UNKNOWN_FUTURE_CONTROL"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_start"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_stop"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_clear_all"));
        assertTrue(ClientBridgeProtocol.isLegacyProtectedMutationRejected("END_PRISONER_ABILITY_REQUEST"));

        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_ack"));
        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_finished"));
        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected("visual_error"));
        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected("ping"));
        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected("heartbeat"));
        assertFalse(ClientBridgeProtocol.isLegacyProtectedMutationRejected(null));
    }
}
