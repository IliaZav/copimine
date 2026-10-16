package me.copimine.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict fixed-record codec for signed Narcotics v4 bodies. */
public final class NarcoticsBridgeBodyCodec {
    public static final int MESSAGE_SCHEMA = 4;
    public static final int MAX_BODY_BYTES = BridgeSigningPolicy.MAX_BODY_BYTES;

    private static final int SCHEMA = 1;
    private static final int MAX_IDENTIFIER_BYTES = 128;
    private static final int MAX_FADE_MILLIS = 10_000;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Set<String> EFFECT_IDS = Set.of("DESATURATE", "COLOR_CONVOLVE", "SCAN_PINCUSHION",
            "GREEN_NOISE", "INVERT", "WOBBLE", "BLOBS", "PENCIL", "CHAOS", "OVERDOSE", "ZHUZEVO_TRIP");
    private static final Set<String> SHADERPACK_IDS = Set.of("trippy_shaderpack.zip", "ctr_vcr.zip",
            "cursed_metamorphopsia.zip", "lsd_shader.zip", "crucify.zip", "nms_1_6.zip",
            "acid_shaders.zip", "white_sharp_1_2.zip");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    private NarcoticsBridgeBodyCodec() {
    }

    public static byte[] encode(Body body) {
        validate(body);
        return encodeInternal(body, true);
    }

    public static Body decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid Narcotics body length");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int schema = input.readUnsignedByte();
            Type type = Type.fromCode(input.readUnsignedByte());
            int messageSchema = input.readInt();
            if (schema != SCHEMA || messageSchema != MESSAGE_SCHEMA) {
                throw new IllegalArgumentException("Unsupported Narcotics body schema");
            }
            long commandSeq = input.readLong();
            long timestamp = input.readLong();
            UUID session = readUuid(input);
            String effectId = readString(input, MAX_IDENTIFIER_BYTES, "effect_id");
            String shaderpackId = readString(input, MAX_IDENTIFIER_BYTES, "shaderpack_id");
            long duration = Integer.toUnsignedLong(input.readInt());
            float intensity = input.readFloat();
            long fadeIn = Integer.toUnsignedLong(input.readInt());
            long fadeOut = Integer.toUnsignedLong(input.readInt());
            String mode = readString(input, MAX_IDENTIFIER_BYTES, "mode");
            String clearPolicy = readString(input, MAX_IDENTIFIER_BYTES, "clear_policy");
            String source = readString(input, MAX_IDENTIFIER_BYTES, "source");
            String reason = readString(input, 256, "reason");
            String status = readString(input, MAX_IDENTIFIER_BYTES, "status");
            UUID visualInstance = readUuid(input);
            UUID targetInstance = readUuid(input);
            long clearFence = input.readLong();
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing Narcotics body bytes");
            }
            Body body = new Body(type, commandSeq, timestamp, session, effectId, shaderpackId, duration,
                    intensity, fadeIn, fadeOut, mode, clearPolicy, source, reason, status,
                    visualInstance, targetInstance, clearFence);
            validate(body);
            return body;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Truncated Narcotics body", exception);
        }
    }

    /** Verifies that the signed body timestamp is the outer envelope issued-at timestamp. */
    public static void validateTimestampMatches(Body body, long issuedAtMillis) {
        validate(body);
        if (body.bodyTimestampMillis() != issuedAtMillis) {
            throw new IllegalArgumentException("Narcotics body timestamp does not match its envelope");
        }
    }

    /** Checks the original clear fence and retry fence rule against the signed stream counter. */
    public static void validateFenceForEnvelope(Body body, long envelopeCounter, boolean initialCommand) {
        validate(body);
        if (envelopeCounter <= 0) {
            throw new IllegalArgumentException("Invalid signed envelope counter");
        }
        if (body.type() == Type.VISUAL_START && body.clearPolicy().equals("PRESERVE")) {
            if (body.clearThroughCounter() != 0) {
                throw new IllegalArgumentException("PRESERVE start must not carry a clear fence");
            }
            return;
        }
        if (body.type() == Type.VISUAL_START || body.type() == Type.VISUAL_CLEAR_ALL) {
            boolean valid = initialCommand
                    ? body.clearThroughCounter() == envelopeCounter - 1
                    : body.clearThroughCounter() < envelopeCounter;
            if (!valid) {
                throw new IllegalArgumentException("Invalid Narcotics clear fence for envelope counter");
            }
        } else if (body.clearThroughCounter() != 0) {
            throw new IllegalArgumentException("Unused Narcotics clear fence must be zero");
        }
    }

    /**
     * Returns the exact logical body fields used for retransmission matching, omitting only the
     * timestamp. The command sequence, clear fence, IDs, and all other fields remain represented.
     */
    public static byte[] logicalComparisonBytes(Body body) {
        validate(body);
        return encodeInternal(body, false);
    }

    public static byte[] logicalFingerprint(Body body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(logicalComparisonBytes(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] encodeInternal(Body body, boolean includeTimestamp) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(SCHEMA);
            output.writeByte(body.type().code());
            output.writeInt(MESSAGE_SCHEMA);
            output.writeLong(body.commandSeq());
            if (includeTimestamp) {
                output.writeLong(body.bodyTimestampMillis());
            }
            writeUuid(output, body.sessionUuid());
            writeString(output, body.effectId(), MAX_IDENTIFIER_BYTES, "effect_id");
            writeString(output, body.shaderpackId(), MAX_IDENTIFIER_BYTES, "shaderpack_id");
            writeUInt32(output, body.durationMillis(), "duration_ms");
            output.writeFloat(body.intensity());
            writeUInt32(output, body.fadeInMillis(), "fade_in_ms");
            writeUInt32(output, body.fadeOutMillis(), "fade_out_ms");
            writeString(output, body.mode(), MAX_IDENTIFIER_BYTES, "mode");
            writeString(output, body.clearPolicy(), MAX_IDENTIFIER_BYTES, "clear_policy");
            writeString(output, body.source(), MAX_IDENTIFIER_BYTES, "source");
            writeString(output, body.reason(), 256, "reason");
            writeString(output, body.status(), MAX_IDENTIFIER_BYTES, "status");
            writeUuid(output, body.visualInstanceId());
            writeUuid(output, body.targetVisualInstanceId());
            output.writeLong(body.clearThroughCounter());
            output.flush();
            if (bytes.size() > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Narcotics body exceeds the protocol body limit");
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode Narcotics bridge body", exception);
        }
    }

    private static void validate(Body body) {
        if (body == null || body.type() == null || body.commandSeq() <= 0
                || body.bodyTimestampMillis() <= 0 || body.sessionUuid() == null) {
            throw new IllegalArgumentException("Invalid common Narcotics body fields");
        }
        requireString(body.effectId(), MAX_IDENTIFIER_BYTES, "effect_id");
        requireString(body.shaderpackId(), MAX_IDENTIFIER_BYTES, "shaderpack_id");
        requireString(body.mode(), MAX_IDENTIFIER_BYTES, "mode");
        requireString(body.clearPolicy(), MAX_IDENTIFIER_BYTES, "clear_policy");
        requireString(body.source(), MAX_IDENTIFIER_BYTES, "source");
        requireString(body.reason(), 256, "reason");
        requireString(body.status(), MAX_IDENTIFIER_BYTES, "status");
        if (!Float.isFinite(body.intensity()) || isNegativeZero(body.intensity())
                || body.intensity() < 0.0f || body.intensity() > 1.0f
                || body.durationMillis() < 0 || body.durationMillis() > 0xffff_ffffL
                || body.fadeInMillis() < 0 || body.fadeInMillis() > 0xffff_ffffL
                || body.fadeOutMillis() < 0 || body.fadeOutMillis() > 0xffff_ffffL
                || body.fadeInMillis() > MAX_FADE_MILLIS || body.fadeOutMillis() > MAX_FADE_MILLIS
                || body.clearThroughCounter() < 0 || body.visualInstanceId() == null
                || body.targetVisualInstanceId() == null) {
            throw new IllegalArgumentException("Narcotics numeric or UUID field is outside its bounds");
        }
        switch (body.type()) {
            case VISUAL_START -> validateStart(body);
            case VISUAL_STOP -> validateStop(body);
            case VISUAL_CLEAR_ALL -> validateClearAll(body);
        }
    }

    private static void validateStart(Body body) {
        if (!EFFECT_IDS.contains(body.effectId())
                || (!body.shaderpackId().isEmpty() && !SHADERPACK_IDS.contains(body.shaderpackId()))
                || body.durationMillis() < 1_000 || body.durationMillis() > 600_000
                || !body.mode().equals("CLIENT_MOD")
                || (!body.clearPolicy().equals("REPLACE_ALL_FULLSCREEN") && !body.clearPolicy().equals("PRESERVE"))
                || !matchesIdentifier(body.source(), 32) || !body.reason().isEmpty() || !body.status().isEmpty()
                || body.visualInstanceId().equals(ZERO_UUID) || !body.targetVisualInstanceId().equals(ZERO_UUID)) {
            throw new IllegalArgumentException("Invalid visual_start fields");
        }
        if (body.clearPolicy().equals("PRESERVE") && body.clearThroughCounter() != 0) {
            throw new IllegalArgumentException("PRESERVE visual_start requires a zero clear fence");
        }
        if (body.clearPolicy().equals("REPLACE_ALL_FULLSCREEN") && body.clearThroughCounter() == 0) {
            throw new IllegalArgumentException("REPLACE_ALL_FULLSCREEN start requires a clear fence");
        }
    }

    private static void validateStop(Body body) {
        if (!EFFECT_IDS.contains(body.effectId()) || !body.shaderpackId().isEmpty()
                || body.durationMillis() != 0 || body.intensity() != 0.0f
                || body.fadeInMillis() != 0 || body.fadeOutMillis() != 0
                || !body.mode().equals("CLIENT_MOD") || !body.clearPolicy().isEmpty()
                || !body.source().isEmpty() || !matchesIdentifier(body.reason(), 256) || !body.status().isEmpty()
                || !body.visualInstanceId().equals(ZERO_UUID) || body.targetVisualInstanceId().equals(ZERO_UUID)
                || body.clearThroughCounter() != 0) {
            throw new IllegalArgumentException("Invalid visual_stop fields");
        }
    }

    private static void validateClearAll(Body body) {
        if (!body.effectId().isEmpty() || !body.shaderpackId().isEmpty()
                || body.durationMillis() != 0 || body.intensity() != 0.0f
                || body.fadeInMillis() != 0 || body.fadeOutMillis() != 0
                || !body.mode().equals("CLIENT_MOD") || !body.clearPolicy().equals("REPLACE_ALL_FULLSCREEN")
                || !body.source().equals("SYSTEM") || !matchesIdentifier(body.reason(), 256) || !body.status().isEmpty()
                || !body.visualInstanceId().equals(ZERO_UUID) || !body.targetVisualInstanceId().equals(ZERO_UUID)
                || body.clearThroughCounter() == 0) {
            throw new IllegalArgumentException("Invalid visual_clear_all fields");
        }
    }

    private static boolean matchesIdentifier(String value, int maximumLength) {
        return value != null && value.length() <= maximumLength && IDENTIFIER.matcher(value).matches();
    }

    private static boolean isNegativeZero(float value) {
        return value == 0.0f && Float.floatToRawIntBits(value) != 0;
    }

    private static void requireString(String value, int maximumBytes, String fieldName) {
        if (value == null || value.length() > maximumBytes) {
            throw new IllegalArgumentException("Invalid " + fieldName + " length");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException(fieldName + " must be printable ASCII");
            }
        }
    }

    private static void writeString(DataOutputStream output, String value, int maximumBytes, String fieldName)
            throws IOException {
        requireString(value, maximumBytes, fieldName);
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximumBytes, String fieldName) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes) {
            throw new IllegalArgumentException("Overlong " + fieldName);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated " + fieldName);
        }
        for (byte value : bytes) {
            int character = Byte.toUnsignedInt(value);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException(fieldName + " must be printable ASCII");
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void writeUInt32(DataOutputStream output, long value, String fieldName) throws IOException {
        if (value < 0 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        output.writeInt((int) value);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        if (uuid == null) {
            throw new IllegalArgumentException("Missing Narcotics UUID field");
        }
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    public record Body(Type type, long commandSeq, long bodyTimestampMillis, UUID sessionUuid,
            String effectId, String shaderpackId, long durationMillis, float intensity,
            long fadeInMillis, long fadeOutMillis, String mode, String clearPolicy, String source,
            String reason, String status, UUID visualInstanceId, UUID targetVisualInstanceId,
            long clearThroughCounter) {
    }

    public enum Type {
        VISUAL_START(1),
        VISUAL_STOP(2),
        VISUAL_CLEAR_ALL(3);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        private static Type fromCode(int code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown Narcotics body type");
        }
    }
}
