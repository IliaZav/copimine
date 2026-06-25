package me.copimine.clientbridge;

import java.util.Locale;
import java.util.Set;

public record ClientCapabilityState(
        int protocolVersion,
        String clientVersion,
        boolean clientModVisuals,
        boolean clientOverlay,
        boolean clientShaderLike,
        boolean trueIrisShader,
        Set<String> supportedEffects,
        long receivedAtMillis
) {
    public boolean supportsEffect(String effectId) {
        return effectId != null && supportedEffects.contains(effectId.toUpperCase(Locale.ROOT));
    }

    public boolean expired(long ttlMillis) {
        return ttlMillis > 0L && (receivedAtMillis + ttlMillis) < System.currentTimeMillis();
    }
}
