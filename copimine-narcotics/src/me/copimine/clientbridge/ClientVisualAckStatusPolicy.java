package me.copimine.clientbridge;

import java.util.Locale;

public final class ClientVisualAckStatusPolicy {
    private ClientVisualAckStatusPolicy() {
    }

    public static boolean shouldReportNonIrisFallback(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("STARTED_[A-Z0-9_-]{1,24}")
                && !normalized.equals("STARTED_IRIS_SHADERPACK")
                && !normalized.equals("STARTED_TRANSITION_DEFERRED");
    }
}
