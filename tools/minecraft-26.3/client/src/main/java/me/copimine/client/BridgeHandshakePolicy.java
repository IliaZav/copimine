package me.copimine.client;

/** Pure checks for pairing a server acknowledgement with the active client hello. */
final class BridgeHandshakePolicy {
    private BridgeHandshakePolicy() {
    }

    static boolean acceptsAcknowledgement(boolean connected, boolean helloSent,
                                          String activeSessionId, String acknowledgedSessionId) {
        return connected
                && helloSent
                && activeSessionId != null
                && !activeSessionId.isBlank()
                && activeSessionId.equals(acknowledgedSessionId);
    }

    static boolean shouldAttemptHello(boolean connected, String challengeId,
                                      boolean helloAcknowledged, int attempts, int maxAttempts) {
        return connected
                && challengeId != null
                && !challengeId.isBlank()
                && !helloAcknowledged
                && attempts >= 0
                && maxAttempts > 0
                && attempts < maxAttempts;
    }
}
