package me.copimine.clientbridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ClientBridgePayloads {
    public static final String CHANNEL = "copimine:client_bridge";
    public static final int PROTOCOL_VERSION = 2;

    public static final String HELLO = "hello";
    public static final String HEARTBEAT = "heartbeat";
    public static final String CAPABILITIES_UPDATE = "capabilities_update";
    public static final String VISUAL_ACK = "visual_ack";
    public static final String VISUAL_FINISHED = "visual_finished";
    public static final String VISUAL_ERROR = "visual_error";

    public static final String VISUAL_START = "visual_start";
    public static final String VISUAL_STOP = "visual_stop";
    public static final String VISUAL_CLEAR_ALL = "visual_clear_all";
    public static final String PING = "ping";
    public static final String CONFIG_UPDATE = "config_update";

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_CLEARED = "CLEARED";
    public static final String STATUS_ERROR = "ERROR";

    public static final String CLEAR_POLICY_REPLACE_ALL_FULLSCREEN = "REPLACE_ALL_FULLSCREEN";

    private static final int MAX_SUPPORTED_EFFECTS = 64;
    private static final int MAX_TYPE_LENGTH = 32;
    private static final int MAX_EFFECT_ID_LENGTH = 48;
    private static final int MAX_CLIENT_VERSION_LENGTH = 64;
    private static final int MAX_SESSION_ID_LENGTH = 96;
    private static final int MAX_MODE_LENGTH = 48;
    private static final int MAX_REASON_LENGTH = 256;
    private static final int MAX_STATUS_LENGTH = 32;
    private static final int MAX_SOURCE_LENGTH = 32;
    private static final Set<String> EFFECT_ALLOWLIST = Set.of(
            "DESATURATE",
            "COLOR_CONVOLVE",
            "SCAN_PINCUSHION",
            "GREEN_NOISE",
            "INVERT",
            "WOBBLE",
            "BLOBS",
            "PENCIL",
            "CHAOS"
    );

    private ClientBridgePayloads() {
    }

    public static Message hello(String sessionId, String clientVersion, Set<String> supportedEffects) {
        return new Message(
                HELLO,
                PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                normalizeBounded(clientVersion, MAX_CLIENT_VERSION_LENGTH),
                true,
                true,
                true,
                false,
                normalizeSupportedEffects(supportedEffects),
                "",
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                ""
        );
    }

    public static Message heartbeat(String sessionId) {
        return new Message(
                HEARTBEAT,
                PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                true,
                true,
                true,
                false,
                Set.of(),
                "",
                0,
                0.0F,
                "",
                "",
                "SYSTEM",
                "",
                ""
        );
    }

    public static Message visualAck(long seq, String sessionId, String effectId, String status) {
        return new Message(
                VISUAL_ACK,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                normalizeEffectId(effectId),
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                normalizeBounded(status, MAX_STATUS_LENGTH).toUpperCase(Locale.ROOT)
        );
    }

    public static Message visualFinished(long seq, String sessionId, String effectId, String reason) {
        return new Message(
                VISUAL_FINISHED,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                normalizeEffectId(effectId),
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                normalizeReason(reason),
                ""
        );
    }

    public static Message visualError(long seq, String sessionId, String effectId, String reason) {
        return new Message(
                VISUAL_ERROR,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                normalizeEffectId(effectId),
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                normalizeReason(reason),
                STATUS_ERROR
        );
    }

    public static byte[] encodeVisualStart(long seq, String sessionId, String effectId, int seconds, float intensity, String source) {
        return encode(new Message(
                VISUAL_START,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                normalizeEffectId(effectId),
                clampDurationMillis(seconds * 1000),
                clampIntensity(intensity),
                "CLIENT_MOD",
                CLEAR_POLICY_REPLACE_ALL_FULLSCREEN,
                normalizeBounded(source, MAX_SOURCE_LENGTH).toUpperCase(Locale.ROOT),
                "",
                ""
        ));
    }

    public static byte[] encodeVisualStop(long seq, String sessionId, String effectId, String reason) {
        return encode(new Message(
                VISUAL_STOP,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                normalizeEffectId(effectId),
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                normalizeReason(reason),
                ""
        ));
    }

    public static byte[] encodeClearAll(long seq, String sessionId, String reason) {
        return encode(new Message(
                VISUAL_CLEAR_ALL,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                "",
                0,
                0.0F,
                "CLIENT_MOD",
                CLEAR_POLICY_REPLACE_ALL_FULLSCREEN,
                "SYSTEM",
                normalizeReason(reason),
                ""
        ));
    }

    public static byte[] encodePing(long seq, String sessionId) {
        return encode(new Message(
                PING,
                PROTOCOL_VERSION,
                Math.max(0L, seq),
                System.currentTimeMillis(),
                normalizeBounded(sessionId, MAX_SESSION_ID_LENGTH),
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                "",
                0,
                0.0F,
                "",
                "",
                "SYSTEM",
                "",
                ""
        ));
    }

    public static byte[] encode(Message message) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(normalizeBounded(message.type(), MAX_TYPE_LENGTH));
            out.writeInt(message.protocol());
            out.writeLong(Math.max(0L, message.seq()));
            out.writeLong(message.timestampMillis() <= 0L ? System.currentTimeMillis() : message.timestampMillis());
            out.writeUTF(normalizeBounded(message.sessionId(), MAX_SESSION_ID_LENGTH));
            out.writeUTF(normalizeBounded(message.clientVersion(), MAX_CLIENT_VERSION_LENGTH));
            out.writeBoolean(message.clientVisuals());
            out.writeBoolean(message.clientOverlay());
            out.writeBoolean(message.clientShaderLike());
            out.writeBoolean(message.trueIrisShader());
            Set<String> supportedEffects = normalizeSupportedEffects(message.supportedEffects());
            out.writeInt(supportedEffects.size());
            for (String supportedEffect : supportedEffects) {
                out.writeUTF(supportedEffect);
            }
            out.writeUTF(normalizeEffectId(message.effectId()));
            out.writeInt(clampDurationMillis(message.durationMillis()));
            out.writeFloat(clampIntensity(message.intensity()));
            out.writeUTF(normalizeBounded(message.mode(), MAX_MODE_LENGTH));
            out.writeUTF(normalizeBounded(message.clearPolicy(), MAX_MODE_LENGTH));
            out.writeUTF(normalizeBounded(message.source(), MAX_SOURCE_LENGTH));
            out.writeUTF(normalizeReason(message.reason()));
            out.writeUTF(normalizeBounded(message.status(), MAX_STATUS_LENGTH));
            out.flush();
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to encode client bridge payload", error);
        }
    }

    public static Message decode(byte[] payload) throws Exception {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            String type = normalizeBounded(in.readUTF(), MAX_TYPE_LENGTH);
            int protocol = in.readInt();
            if (protocol != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported client bridge protocol: " + protocol);
            }
            long seq = Math.max(0L, in.readLong());
            long timestampMillis = Math.max(0L, in.readLong());
            String sessionId = normalizeBounded(in.readUTF(), MAX_SESSION_ID_LENGTH);
            String clientVersion = normalizeBounded(in.readUTF(), MAX_CLIENT_VERSION_LENGTH);
            boolean clientVisuals = in.readBoolean();
            boolean clientOverlay = in.readBoolean();
            boolean clientShaderLike = in.readBoolean();
            boolean trueIrisShader = in.readBoolean();
            int count = Math.max(0, in.readInt());
            if (count > MAX_SUPPORTED_EFFECTS) {
                throw new IllegalArgumentException("Too many supported effects: " + count);
            }
            Set<String> effects = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                String effectId = normalizeEffectId(in.readUTF());
                if (!effectId.isBlank()) {
                    effects.add(effectId);
                }
            }
            String effectId = normalizeEffectId(in.readUTF());
            int durationMillis = clampDurationMillis(in.readInt());
            float intensity = clampIntensity(in.readFloat());
            String mode = normalizeBounded(in.readUTF(), MAX_MODE_LENGTH);
            String clearPolicy = normalizeBounded(in.readUTF(), MAX_MODE_LENGTH);
            String source = normalizeBounded(in.readUTF(), MAX_SOURCE_LENGTH).toUpperCase(Locale.ROOT);
            String reason = normalizeReason(in.readUTF());
            String status = normalizeBounded(in.readUTF(), MAX_STATUS_LENGTH).toUpperCase(Locale.ROOT);

            boolean effectRequired = VISUAL_START.equals(type)
                    || VISUAL_STOP.equals(type)
                    || VISUAL_FINISHED.equals(type)
                    || VISUAL_ERROR.equals(type)
                    || (VISUAL_ACK.equals(type) && !STATUS_CLEARED.equals(status));
            if (effectRequired && effectId.isBlank()) {
                throw new IllegalArgumentException("Effect id is required for type: " + type);
            }

            return new Message(
                    type,
                    protocol,
                    seq,
                    timestampMillis,
                    sessionId,
                    clientVersion,
                    clientVisuals,
                    clientOverlay,
                    clientShaderLike,
                    trueIrisShader,
                    Set.copyOf(effects),
                    effectId,
                    durationMillis,
                    intensity,
                    mode,
                    clearPolicy,
                    source,
                    reason,
                    status
            );
        }
    }

    public static int durationSeconds(Message message) {
        return Math.max(1, Math.min(600, message.durationMillis() / 1000));
    }

    public static float clampIntensity(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampDurationMillis(int durationMillis) {
        return Math.max(1_000, Math.min(600_000, durationMillis));
    }

    private static String normalizeEffectId(String effectId) {
        if (effectId == null) {
            return "";
        }
        String normalized = effectId.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > MAX_EFFECT_ID_LENGTH) {
            normalized = normalized.substring(0, MAX_EFFECT_ID_LENGTH);
        }
        return EFFECT_ALLOWLIST.contains(normalized) ? normalized : "CHAOS";
    }

    private static String normalizeReason(String reason) {
        return normalizeBounded(reason, MAX_REASON_LENGTH);
    }

    private static String normalizeBounded(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private static Set<String> normalizeSupportedEffects(Set<String> supportedEffects) {
        Set<String> normalized = new LinkedHashSet<>();
        if (supportedEffects != null) {
            for (String supportedEffect : supportedEffects) {
                String effectId = normalizeEffectId(supportedEffect);
                if (!effectId.isBlank()) {
                    normalized.add(effectId);
                }
            }
        }
        if (normalized.size() > MAX_SUPPORTED_EFFECTS) {
            return Set.copyOf(normalized.stream().limit(MAX_SUPPORTED_EFFECTS).toList());
        }
        return Set.copyOf(normalized);
    }

    public record Message(
            String type,
            int protocol,
            long seq,
            long timestampMillis,
            String sessionId,
            String clientVersion,
            boolean clientVisuals,
            boolean clientOverlay,
            boolean clientShaderLike,
            boolean trueIrisShader,
            Set<String> supportedEffects,
            String effectId,
            int durationMillis,
            float intensity,
            String mode,
            String clearPolicy,
            String source,
            String reason,
            String status
    ) {
    }
}
