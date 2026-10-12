package me.copimine.clientbridge;

import java.util.Locale;

public final class ClientVisualAckStatusPolicy {
    private ClientVisualAckStatusPolicy() {
    }

    public static boolean shouldReportNonIrisFallback(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("STARTED")
                && !normalized.contains("IRIS_SHADERPACK")
                && !normalized.contains("TRANSITION_DEFERRED");
    }
}
