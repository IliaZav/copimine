package me.copimine.clientbridge;

import me.copimine.protocol.ClientModHandshakePolicy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ClientBridgePayloads {
    public static final String CHANNEL = "copimine:client_bridge";
    public static final String V4_CHANNEL = "copimine:client_bridge_v4";
    public static final int PROTOCOL_VERSION = ClientModHandshakePolicy.PROTOCOL_VERSION;

    public static final String CLIENT_MOD_CHALLENGE = ClientModHandshakePolicy.CHALLENGE_MESSAGE_TYPE;
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
    private static final int MAX_SHADERPACK_LENGTH = 96;
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
            "CHAOS",
            "OVERDOSE",
            "ZHUZEVO_TRIP"
    );
    private static final Pattern REPORT_REASON = Pattern.compile("[A-Za-z0-9_.:-]{1,256}");
    private static final Pattern STARTED_ROUTE = Pattern.compile("STARTED_[A-Z0-9_-]{1,24}");

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
                "",
                0,
                0.0F,
                0,
                0,
                ClientModHandshakePolicy.MOD_ID,
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
                "",
                0,
                0.0F,
                0,
                0,
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
                "",
                0,
                0.0F,
                0,
                0,
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
                "",
                0,
                0.0F,
                0,
                0,
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
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                normalizeReason(reason),
                STATUS_ERROR
        );
    }

    public static byte[] encodeVisualStart(long seq, String sessionId, String effectId, String shaderpack, int seconds, float intensity, int fadeInMillis, int fadeOutMillis, String source) {
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
                normalizeShaderpack(shaderpack),
                clampDurationMillis(seconds * 1000),
                clampIntensity(intensity),
                clampFadeMillis(fadeInMillis),
                clampFadeMillis(fadeOutMillis),
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
                "",
                0,
                0.0F,
                0,
                0,
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
                "",
                0,
                0.0F,
                0,
                0,
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
                "",
                0,
                0.0F,
                0,
                0,
                "",
                "",
                "SYSTEM",
                "",
                ""
        ));
    }

    public static byte[] encodeClientModChallenge(String challengeId, String requiredClientModVersion) {
        return encode(new Message(
                CLIENT_MOD_CHALLENGE,
                PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                normalizeBounded(challengeId, MAX_SESSION_ID_LENGTH),
                normalizeBounded(requiredClientModVersion, MAX_CLIENT_VERSION_LENGTH),
                false,
                false,
                false,
                false,
                Set.of(),
                "",
                "",
                0,
                0.0F,
                0,
                0,
                ClientModHandshakePolicy.MOD_ID,
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
            out.writeUTF(normalizeShaderpack(message.shaderpack()));
            out.writeInt(clampDurationMillis(message.durationMillis()));
            out.writeFloat(clampIntensity(message.intensity()));
            out.writeInt(clampFadeMillis(message.fadeInMillis()));
            out.writeInt(clampFadeMillis(message.fadeOutMillis()));
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
            String shaderpack = normalizeShaderpack(in.readUTF());
            int durationMillis = clampDurationMillis(in.readInt());
            float intensity = clampIntensity(in.readFloat());
            int fadeInMillis = clampFadeMillis(in.readInt());
            int fadeOutMillis = clampFadeMillis(in.readInt());
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
                    shaderpack,
                    durationMillis,
                    intensity,
                    fadeInMillis,
                    fadeOutMillis,
                    mode,
                    clearPolicy,
                    source,
                    reason,
                    status
            );
        }
    }

    /** Parses the legacy-channel reports without normalization or clamping. */
    public static Message decodeStrictVisualReport(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > 4_096) {
            throw new IllegalArgumentException("Invalid client visual report length");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            String type = input.readUTF();
            int protocol = input.readInt();
            long sequence = input.readLong();
            long timestamp = input.readLong();
            String session = input.readUTF();
            String clientVersion = input.readUTF();
            boolean visuals = input.readBoolean();
            boolean overlay = input.readBoolean();
            boolean shaderLike = input.readBoolean();
            boolean iris = input.readBoolean();
            int effectCount = input.readInt();
            if (effectCount != 0) {
                throw new IllegalArgumentException("Visual reports cannot advertise capabilities");
            }
            String effect = input.readUTF();
            String shaderpack = input.readUTF();
            int duration = input.readInt();
            float intensity = input.readFloat();
            int fadeIn = input.readInt();
            int fadeOut = input.readInt();
            String mode = input.readUTF();
            String clearPolicy = input.readUTF();
            String source = input.readUTF();
            String reason = input.readUTF();
            String status = input.readUTF();
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing client visual report bytes");
            }
            if (!VISUAL_ACK.equals(type) && !VISUAL_FINISHED.equals(type) && !VISUAL_ERROR.equals(type)) {
                throw new IllegalArgumentException("Unsupported client visual report type");
            }
            if (protocol != PROTOCOL_VERSION || sequence <= 0 || timestamp <= 0
                    || !isCanonicalUuid(session) || !clientVersion.isEmpty()
                    || visuals || overlay || shaderLike || iris || effectCount != 0
                    || !shaderpack.isEmpty() || duration != 0
                    || Float.floatToRawIntBits(intensity) != Float.floatToRawIntBits(0.0f)
                    || fadeIn != 0 || fadeOut != 0 || !"CLIENT_MOD".equals(mode)
                    || !clearPolicy.isEmpty() || !"SYSTEM".equals(source)) {
                throw new IllegalArgumentException("Invalid client visual report fields");
            }
            if (VISUAL_ACK.equals(type)) {
                boolean clear = STATUS_CLEARED.equals(status) && effect.isEmpty();
                boolean start = (STATUS_STARTED.equals(status) || STARTED_ROUTE.matcher(status).matches())
                        && EFFECT_ALLOWLIST.contains(effect) && reason.isEmpty();
                boolean stop = STATUS_STOPPED.equals(status) && EFFECT_ALLOWLIST.contains(effect) && reason.isEmpty();
                if (!(clear || start || stop)) {
                    throw new IllegalArgumentException("Invalid client visual ACK status");
                }
            } else if (!EFFECT_ALLOWLIST.contains(effect) || !REPORT_REASON.matcher(reason).matches()) {
                throw new IllegalArgumentException("Invalid client visual terminal report");
            } else if (VISUAL_FINISHED.equals(type) && !status.isEmpty()) {
                throw new IllegalArgumentException("Finished report status must be empty");
            } else if (VISUAL_ERROR.equals(type) && !STATUS_ERROR.equals(status)) {
                throw new IllegalArgumentException("Visual error report status is invalid");
            }
            return new Message(type, protocol, sequence, timestamp, session, clientVersion,
                    visuals, overlay, shaderLike, iris, Set.of(), effect, shaderpack, duration,
                    intensity, fadeIn, fadeOut, mode, clearPolicy, source, reason, status);
        } catch (java.io.EOFException truncated) {
            throw new IllegalArgumentException("Truncated client visual report", truncated);
        }
    }

    private static boolean isCanonicalUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    public static int durationSeconds(Message message) {
        return Math.max(1, Math.min(600, message.durationMillis() / 1000));
    }

    public static float clampIntensity(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampDurationMillis(int durationMillis) {
        return Math.max(1_000, Math.min(600_000, durationMillis));
    }

    private static int clampFadeMillis(int fadeMillis) {
        return Math.max(0, Math.min(10_000, fadeMillis));
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

    private static String normalizeShaderpack(String shaderpack) {
        return normalizeBounded(shaderpack, MAX_SHADERPACK_LENGTH);
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
            String shaderpack,
            int durationMillis,
            float intensity,
            int fadeInMillis,
            int fadeOutMillis,
            String mode,
            String clearPolicy,
            String source,
            String reason,
            String status
    ) {
    }
}
