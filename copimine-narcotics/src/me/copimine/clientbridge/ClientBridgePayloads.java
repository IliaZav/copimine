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
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_SUPPORTED_EFFECTS = 64;
    private static final int MAX_EFFECT_ID_LENGTH = 48;
    private static final int MAX_CLIENT_VERSION_LENGTH = 64;
    public static final String HANDSHAKE = "hello";
    public static final String VISUAL_START = "visual_start";
    public static final String VISUAL_STOP = "visual_stop";
    public static final String VISUAL_CLEAR_ALL = "visual_clear_all";

    private ClientBridgePayloads() {
    }

    public static byte[] encodeVisualStart(String effectId, int seconds, float intensity) {
        return encode(out -> {
            out.writeUTF(VISUAL_START);
            out.writeInt(PROTOCOL_VERSION);
            out.writeUTF(normalizeEffectId(effectId));
            out.writeInt(Math.max(1, Math.min(600, seconds)));
            out.writeFloat(Math.max(0.0F, Math.min(1.0F, intensity)));
        });
    }

    public static byte[] encodeVisualStop(String effectId) {
        return encode(out -> {
            out.writeUTF(VISUAL_STOP);
            out.writeInt(PROTOCOL_VERSION);
            out.writeUTF(normalizeEffectId(effectId));
        });
    }

    public static byte[] encodeClearAll() {
        return encode(out -> {
            out.writeUTF(VISUAL_CLEAR_ALL);
            out.writeInt(PROTOCOL_VERSION);
        });
    }

    public static ClientCapabilityState decodeHello(byte[] payload) throws Exception {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            String type = in.readUTF();
            if (!HANDSHAKE.equalsIgnoreCase(type)) {
                throw new IllegalArgumentException("Unsupported client bridge message type: " + type);
            }
            int protocol = in.readInt();
            if (protocol != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported client bridge protocol: " + protocol);
            }
            String clientVersion = in.readUTF();
            if (clientVersion.length() > MAX_CLIENT_VERSION_LENGTH) {
                throw new IllegalArgumentException("Client version is too long.");
            }
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
                if (effectId.length() > MAX_EFFECT_ID_LENGTH) {
                    throw new IllegalArgumentException("Effect id is too long.");
                }
                effects.add(effectId);
            }
            return new ClientCapabilityState(
                    protocol,
                    clientVersion,
                    clientVisuals,
                    clientOverlay,
                    clientShaderLike,
                    trueIrisShader,
                    Set.copyOf(effects),
                    System.currentTimeMillis()
            );
        }
    }

    private static byte[] encode(Writer writer) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to encode client bridge payload", error);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws Exception;
    }

    private static String normalizeEffectId(String effectId) {
        String normalized = effectId == null ? "CHAOS" : effectId.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            normalized = "CHAOS";
        }
        return normalized.length() > MAX_EFFECT_ID_LENGTH ? normalized.substring(0, MAX_EFFECT_ID_LENGTH) : normalized;
    }
}
