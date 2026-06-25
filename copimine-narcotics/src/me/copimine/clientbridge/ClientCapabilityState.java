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
        String sessionId,
        long receivedAtMillis,
        long lastSeenMillis
) {
    public boolean supportsEffect(String effectId) {
        return effectId != null && supportedEffects.contains(effectId.toUpperCase(Locale.ROOT));
    }

    public boolean expired(long ttlMillis) {
        return ttlMillis > 0L && (lastSeenMillis + ttlMillis) < System.currentTimeMillis();
    }

    public ClientCapabilityState touch(long nowMillis) {
        return new ClientCapabilityState(
                protocolVersion,
                clientVersion,
                clientModVisuals,
                clientOverlay,
                clientShaderLike,
                trueIrisShader,
                supportedEffects,
                sessionId,
                receivedAtMillis,
                nowMillis
        );
    }

    public static ClientCapabilityState fromMessage(ClientBridgePayloads.Message message) {
        long now = System.currentTimeMillis();
        return new ClientCapabilityState(
                message.protocol(),
                message.clientVersion(),
                message.clientVisuals(),
                message.clientOverlay(),
                message.clientShaderLike(),
                message.trueIrisShader(),
                message.supportedEffects(),
                message.sessionId(),
                now,
                now
        );
    }
}
