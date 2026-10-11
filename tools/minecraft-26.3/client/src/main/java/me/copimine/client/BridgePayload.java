package me.copimine.client;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BridgePayload(
        String messageType,
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
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BridgePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("copimine", "client_bridge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BridgePayload> CODEC = CustomPacketPayload.codec(BridgePayload::write, BridgePayload::read);
    private static final int MAX_SUPPORTED_EFFECTS = 64;
    private static final int MAX_MESSAGE_TYPE_BYTES = 128;
    private static final int MAX_SESSION_ID_BYTES = 768;
    private static final int MAX_CLIENT_VERSION_BYTES = 384;
    private static final int MAX_SUPPORTED_EFFECT_BYTES = 256;
    private static final int MAX_EFFECT_ID_BYTES = 256;
    private static final int MAX_SHADERPACK_BYTES = 1_024;
    private static final int MAX_METADATA_BYTES = 1_024;

    public BridgePayload {
        messageType = safe(messageType);
        sessionId = safe(sessionId);
        clientVersion = safe(clientVersion);
        supportedEffects = supportedEffects == null ? Set.of() : Set.copyOf(supportedEffects);
        effectId = normalizeEffectId(effectId);
        shaderpack = normalizeShaderpack(shaderpack);
        mode = safe(mode);
        clearPolicy = safe(clearPolicy);
        source = safe(source);
        reason = safe(reason);
        status = safe(status);
        durationMillis = clampDuration(durationMillis);
        intensity = clampIntensity(intensity);
        fadeInMillis = clampFade(fadeInMillis);
        fadeOutMillis = clampFade(fadeOutMillis);
    }

    public static BridgePayload hello(String sessionId, String version, Set<String> supportedEffects, boolean visualsAvailable, boolean shaderpackRuntimeAvailable, boolean irisShaderPackActive) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_HELLO,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                sessionId,
                version,
                visualsAvailable,
                visualsAvailable,
                shaderpackRuntimeAvailable,
                irisShaderPackActive,
                normalizeEffects(supportedEffects),
                "",
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                ""
        );
    }

    public static BridgePayload capabilitiesUpdate(String sessionId, String version, Set<String> supportedEffects, boolean visualsAvailable, boolean shaderpackRuntimeAvailable, boolean irisShaderPackActive) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_CAPABILITIES_UPDATE,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                sessionId,
                version,
                visualsAvailable,
                visualsAvailable,
                shaderpackRuntimeAvailable,
                irisShaderPackActive,
                normalizeEffects(supportedEffects),
                "",
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                ""
        );
    }

    public static BridgePayload heartbeat(String sessionId, boolean visualsAvailable, boolean shaderpackRuntimeAvailable, boolean irisShaderPackActive) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_HEARTBEAT,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                sessionId,
                "",
                visualsAvailable,
                visualsAvailable,
                shaderpackRuntimeAvailable,
                irisShaderPackActive,
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

    public static BridgePayload visualAck(String sessionId, long seq, String effectId, String status) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_VISUAL_ACK,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                seq,
                System.currentTimeMillis(),
                sessionId,
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                effectId,
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                "",
                safe(status).toUpperCase(Locale.ROOT)
        );
    }

    public static BridgePayload visualFinished(String sessionId, long seq, String effectId, String reason) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_VISUAL_FINISHED,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                seq,
                System.currentTimeMillis(),
                sessionId,
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                effectId,
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                safe(reason),
                ""
        );
    }

    public static BridgePayload visualError(String sessionId, long seq, String effectId, String reason) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_VISUAL_ERROR,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                seq,
                System.currentTimeMillis(),
                sessionId,
                "",
                false,
                false,
                false,
                false,
                Set.of(),
                effectId,
                "",
                0,
                0.0F,
                0,
                0,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                safe(reason),
                "ERROR"
        );
    }

    /** Encode one server-validated prisoner ability press in the shared v2 envelope. */
    public static BridgePayload prisonerAbilityRequest(String sessionId, long generation,
                                                        String eventId, String abilityId,
                                                        String targetUuid) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_PRISONER_ABILITY_REQUEST,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                Math.max(0L, generation),
                System.currentTimeMillis(),
                sessionId,
                CopiMineClient.CLIENT_VERSION,
                false,
                false,
                false,
                false,
                Set.of(),
                "",
                safe(eventId),
                1,
                0.0F,
                0,
                0,
                safe(abilityId),
                safe(targetUuid),
                "",
                "",
                ""
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public int durationSeconds() {
        return Math.max(1, Math.min(600, durationMillis / 1000));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        try (DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(buf))) {
            out.writeUTF(messageType);
            out.writeInt(protocol);
            out.writeLong(Math.max(0L, seq));
            out.writeLong(timestampMillis <= 0L ? System.currentTimeMillis() : timestampMillis);
            out.writeUTF(sessionId);
            out.writeUTF(clientVersion);
            out.writeBoolean(clientVisuals);
            out.writeBoolean(clientOverlay);
            out.writeBoolean(clientShaderLike);
            out.writeBoolean(trueIrisShader);
            out.writeInt(supportedEffects.size());
            for (String supportedEffect : supportedEffects) {
                out.writeUTF(supportedEffect.toUpperCase(Locale.ROOT));
            }
            out.writeUTF(effectId);
            out.writeUTF(shaderpack);
            out.writeInt(durationMillis);
            out.writeFloat(intensity);
            out.writeInt(fadeInMillis);
            out.writeInt(fadeOutMillis);
            out.writeUTF(mode);
            out.writeUTF(clearPolicy);
            out.writeUTF(source);
            out.writeUTF(reason);
            out.writeUTF(status);
            out.flush();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to write CopiMine client bridge payload", error);
        }
    }

    private static BridgePayload read(RegistryFriendlyByteBuf buf) {
        try (DataInputStream in = new DataInputStream(new ByteBufInputStream(buf))) {
            String type = safe(readBoundedUTF(in, "messageType", MAX_MESSAGE_TYPE_BYTES));
            int protocol = in.readInt();
            long seq = Math.max(0L, in.readLong());
            long timestampMillis = Math.max(0L, in.readLong());
            String sessionId = safe(readBoundedUTF(in, "sessionId", MAX_SESSION_ID_BYTES));
            String version = safe(readBoundedUTF(in, "clientVersion", MAX_CLIENT_VERSION_BYTES));
            boolean clientVisuals = in.readBoolean();
            boolean clientOverlay = in.readBoolean();
            boolean clientShaderLike = in.readBoolean();
            boolean trueIrisShader = in.readBoolean();
            int count = Math.max(0, in.readInt());
            if (count > MAX_SUPPORTED_EFFECTS) {
                throw new IllegalArgumentException("Too many supported effects: " + count);
            }
            Set<String> supportedEffects = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                supportedEffects.add(normalizeEffectId(
                        readBoundedUTF(in, "supportedEffect", MAX_SUPPORTED_EFFECT_BYTES)));
            }
            String effectId = normalizeEffectId(readBoundedUTF(in, "effectId", MAX_EFFECT_ID_BYTES));
            String shaderpack = normalizeShaderpack(
                    readBoundedUTF(in, "shaderpack", MAX_SHADERPACK_BYTES));
            int durationMillis = clampDuration(in.readInt());
            float intensity = clampIntensity(in.readFloat());
            int fadeInMillis = clampFade(in.readInt());
            int fadeOutMillis = clampFade(in.readInt());
            String mode = safe(readBoundedUTF(in, "mode", MAX_METADATA_BYTES));
            String clearPolicy = safe(readBoundedUTF(in, "clearPolicy", MAX_METADATA_BYTES));
            String source = safe(readBoundedUTF(in, "source", MAX_METADATA_BYTES));
            String reason = safe(readBoundedUTF(in, "reason", MAX_METADATA_BYTES));
            String status = safe(readBoundedUTF(in, "status", MAX_METADATA_BYTES));
            return new BridgePayload(
                    type,
                    protocol,
                    seq,
                    timestampMillis,
                    sessionId,
                    version,
                    clientVisuals,
                    clientOverlay,
                    clientShaderLike,
                    trueIrisShader,
                    supportedEffects,
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
        } catch (Exception error) {
            throw new IllegalStateException("Failed to read CopiMine client bridge payload", error);
        }
    }

    static String readBoundedUTF(DataInputStream in, String field, int maxEncodedBytes)
            throws java.io.IOException {
        if (maxEncodedBytes < 0 || maxEncodedBytes > 0xFFFF) {
            throw new IllegalArgumentException("Invalid modified UTF byte limit for " + field);
        }
        int encodedLength = in.readUnsignedShort();
        if (encodedLength > maxEncodedBytes) {
            throw new IllegalArgumentException("Bridge " + field + " exceeds its encoded byte limit");
        }
        byte[] framed = new byte[encodedLength + 2];
        framed[0] = (byte) ((encodedLength >>> 8) & 0xFF);
        framed[1] = (byte) (encodedLength & 0xFF);
        in.readFully(framed, 2, encodedLength);
        try (DataInputStream decoded = new DataInputStream(new ByteArrayInputStream(framed))) {
            return decoded.readUTF();
        }
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeEffectId(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return "";
        }
        String normalized = effectId.trim().toUpperCase(Locale.ROOT);
        return ClientBridgeProtocol.SUPPORTED_EFFECTS.contains(normalized) ? normalized : "CHAOS";
    }

    private static float clampIntensity(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampDuration(int durationMillis) {
        if (durationMillis <= 0) {
            return 1_000;
        }
        return Math.max(1_000, Math.min(600_000, durationMillis));
    }

    private static int clampFade(int fadeMillis) {
        return Math.max(0, Math.min(10_000, fadeMillis));
    }

    private static String normalizeShaderpack(String shaderpack) {
        if (shaderpack == null) {
            return "";
        }
        String normalized = shaderpack.trim();
        if (normalized.length() > 96) {
            return normalized.substring(0, 96);
        }
        return normalized;
    }

    private static Set<String> normalizeEffects(Set<String> supportedEffects) {
        Set<String> normalized = new LinkedHashSet<>();
        if (supportedEffects != null) {
            for (String effectId : supportedEffects) {
                if ("ECHO_PRESENTATION_V1".equalsIgnoreCase(effectId)) {
                    normalized.add("ECHO_PRESENTATION_V1");
                    continue;
                }
                String effect = normalizeEffectId(effectId);
                if (!effect.isBlank()) {
                    normalized.add(effect);
                }
            }
        }
        return Set.copyOf(normalized);
    }
}
