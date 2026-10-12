package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModHandshakePolicyTest {
    private static final String CHALLENGE = "b5ed15bb-cc40-4965-9b67-8d73df238b87";
    private static final String MOD_ID = "copimineclient";
    private static final String VERSION = "0.1.2+26.3";
    private static final int PROTOCOL = 3;

    @Test
    void acceptsOnlyExactModIdVersionProtocolAndFreshChallenge() {
        assertTrue(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 10_000L,
                MOD_ID, MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));

        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, "stale-session", 10_000L, 9_000L,
                MOD_ID, MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 10_001L,
                MOD_ID, MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 9_000L,
                "anothermod", MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 9_000L,
                MOD_ID, MOD_ID, "0.1.1", VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 9_000L,
                MOD_ID, MOD_ID, VERSION, VERSION, 2, PROTOCOL));
    }

    @Test
    void rejectsBlankOrNullHandshakeIdentity() {
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                "", "", 10_000L, 9_000L,
                MOD_ID, MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 9_000L,
                null, MOD_ID, VERSION, VERSION, PROTOCOL, PROTOCOL));
        assertFalse(ClientModHandshakePolicy.acceptsResponse(
                CHALLENGE, CHALLENGE, 10_000L, 9_000L,
                MOD_ID, MOD_ID, VERSION, "", PROTOCOL, PROTOCOL));
    }

    @Test
    void waitsForTheClientToRegisterThePluginMessageChannelBeforeSendingChallenge() {
        assertTrue(ClientModHandshakePolicy.shouldWaitForClientChannel(
                true, false, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldWaitForClientChannel(
                false, false, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldWaitForClientChannel(
                true, true, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldWaitForClientChannel(
                true, false, 10_000L, 10_000L));
    }

    @Test
    void sendsChallengeOnlyForPendingHandshakeWithRegisteredChannelBeforeDeadline() {
        assertTrue(ClientModHandshakePolicy.shouldSendChallenge(
                true, true, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldSendChallenge(
                false, true, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldSendChallenge(
                true, false, 10_000L, 9_999L));
        assertFalse(ClientModHandshakePolicy.shouldSendChallenge(
                true, true, 10_000L, 10_001L));
    }
}
