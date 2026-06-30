package me.copimine.clientbridge;

import java.util.UUID;

public final class ClientVisualCommand {
    public interface FallbackHandler {
        void onFallback(UUID playerUuid, String effectId, int seconds, float intensity, String source, String reason);
    }

    public interface FinishHandler {
        void onFinished(UUID playerUuid, String effectId, String source, String reason);
    }

    private final long seq;
    private final UUID playerUuid;
    private final String playerName;
    private final String sessionId;
    private final String effectId;
    private final String shaderpack;
    private final int seconds;
    private final float intensity;
    private final int fadeInMillis;
    private final int fadeOutMillis;
    private final String source;
    private final long createdAtMillis;
    private final FallbackHandler fallbackHandler;
    private final FinishHandler finishHandler;
    private int attempts;
    private long sentAtMillis;
    private long ackDeadlineMillis;

    ClientVisualCommand(
            long seq,
            UUID playerUuid,
            String playerName,
            String sessionId,
            String effectId,
            String shaderpack,
            int seconds,
            float intensity,
            int fadeInMillis,
            int fadeOutMillis,
            String source,
            long createdAtMillis,
            FallbackHandler fallbackHandler,
            FinishHandler finishHandler
    ) {
        this.seq = seq;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.sessionId = sessionId;
        this.effectId = effectId;
        this.shaderpack = shaderpack;
        this.seconds = seconds;
        this.intensity = intensity;
        this.fadeInMillis = fadeInMillis;
        this.fadeOutMillis = fadeOutMillis;
        this.source = source;
        this.createdAtMillis = createdAtMillis;
        this.fallbackHandler = fallbackHandler;
        this.finishHandler = finishHandler;
    }

    long seq() {
        return seq;
    }

    UUID playerUuid() {
        return playerUuid;
    }

    String playerName() {
        return playerName;
    }

    String sessionId() {
        return sessionId;
    }

    String effectId() {
        return effectId;
    }

    String shaderpack() {
        return shaderpack;
    }

    int seconds() {
        return seconds;
    }

    float intensity() {
        return intensity;
    }

    int fadeInMillis() {
        return fadeInMillis;
    }

    int fadeOutMillis() {
        return fadeOutMillis;
    }

    String source() {
        return source;
    }

    long createdAtMillis() {
        return createdAtMillis;
    }

    FallbackHandler fallbackHandler() {
        return fallbackHandler;
    }

    FinishHandler finishHandler() {
        return finishHandler;
    }

    int attempts() {
        return attempts;
    }

    void markSent(long nowMillis, long ackTimeoutMillis) {
        attempts++;
        sentAtMillis = nowMillis;
        ackDeadlineMillis = nowMillis + ackTimeoutMillis;
    }

    long sentAtMillis() {
        return sentAtMillis;
    }

    long ackDeadlineMillis() {
        return ackDeadlineMillis;
    }
}
