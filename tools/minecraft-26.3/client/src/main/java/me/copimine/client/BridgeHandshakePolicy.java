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
}
