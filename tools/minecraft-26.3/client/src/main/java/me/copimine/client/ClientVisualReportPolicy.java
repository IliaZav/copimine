package me.copimine.client;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;

/** Maps authenticated visual commands to the v3 client report statuses consumed by Paper. */
final class ClientVisualReportPolicy {
    private ClientVisualReportPolicy() {
    }

    static String ackStatus(NarcoticsBridgeBodyCodec.Type type) {
        return switch (type) {
            case VISUAL_START -> "STARTED";
            case VISUAL_STOP -> "STOPPED";
            case VISUAL_CLEAR_ALL -> "CLEARED";
        };
    }

    static boolean shouldReportStartError(NarcoticsBridgeBodyCodec.Type type) {
        return type == NarcoticsBridgeBodyCodec.Type.VISUAL_START;
    }

    static String errorReason(boolean managerAvailable, String reason) {
        if (!managerAvailable) {
            return "visual-manager-unavailable";
        }
        if (reason == null || reason.isBlank()) {
            return "visual-application-rejected";
        }
        return reason;
    }
}
