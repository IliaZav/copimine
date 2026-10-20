package me.copimine.client;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;

/** Applies the local server-visual preference to authenticated remote visual commands. */
final class ClientVisualCommandPolicy {
    private ClientVisualCommandPolicy() {
    }

    static boolean shouldApply(boolean serverVisualsAllowed, NarcoticsBridgeBodyCodec.Type type) {
        if (type == null) {
            return false;
        }
        return serverVisualsAllowed
                || type == NarcoticsBridgeBodyCodec.Type.VISUAL_STOP
                || type == NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL;
    }
}
