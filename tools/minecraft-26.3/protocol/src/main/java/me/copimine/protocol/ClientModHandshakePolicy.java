package me.copimine.protocol;

import java.util.Objects;

/** Shared validation rules for the CopiMineClient server challenge. */
public final class ClientModHandshakePolicy {
    public static final String MOD_ID = "copimineclient";
    public static final String CHALLENGE_MESSAGE_TYPE = "client_mod_challenge";
    public static final String DEFAULT_REQUIRED_VERSION = "0.1.2+26.3";
    public static final int PROTOCOL_VERSION = 3;

    private ClientModHandshakePolicy() {
    }

    public static boolean acceptsResponse(
            String expectedChallenge,
            String responseChallenge,
            long expiresAtMillis,
            long nowMillis,
            String actualModId,
            String expectedModId,
            String actualVersion,
            String expectedVersion,
            int actualProtocol,
            int expectedProtocol) {
        return hasText(expectedChallenge)
                && Objects.equals(expectedChallenge, responseChallenge)
                && nowMillis <= expiresAtMillis
                && hasText(actualModId)
                && Objects.equals(actualModId, expectedModId)
                && hasText(actualVersion)
                && Objects.equals(actualVersion, expectedVersion)
                && actualProtocol == expectedProtocol;
    }

    public static boolean isValidChallenge(
            String messageType,
            String expectedMessageType,
            String challengeId,
            String modId,
            String expectedModId,
            int protocol,
            int expectedProtocol) {
        return Objects.equals(messageType, expectedMessageType)
                && hasText(challengeId)
                && hasText(modId)
                && Objects.equals(modId, expectedModId)
                && protocol == expectedProtocol;
    }

    public static boolean shouldSendChallenge(
            boolean handshakePending,
            boolean clientListeningOnChannel,
            long expiresAtMillis,
            long nowMillis) {
        return handshakePending
                && clientListeningOnChannel
                && nowMillis <= expiresAtMillis;
    }

    public static boolean shouldWaitForClientChannel(
            boolean handshakePending,
            boolean clientListeningOnChannel,
            long expiresAtMillis,
            long nowMillis) {
        return handshakePending
                && !clientListeningOnChannel
                && nowMillis < expiresAtMillis;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
