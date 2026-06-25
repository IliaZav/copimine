package me.copimine.client;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record BridgePayload(
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
) implements CustomPayload {
    public static final CustomPayload.Id<BridgePayload> ID = new CustomPayload.Id<>(Identifier.of("copimine", "client_bridge"));
    public static final PacketCodec<RegistryByteBuf, BridgePayload> CODEC = CustomPayload.codecOf(BridgePayload::write, BridgePayload::read);

    public BridgePayload {
        type = safe(type);
        sessionId = safe(sessionId);
        clientVersion = safe(clientVersion);
        supportedEffects = supportedEffects == null ? Set.of() : Set.copyOf(supportedEffects);
        effectId = normalizeEffectId(effectId);
        mode = safe(mode);
        clearPolicy = safe(clearPolicy);
        source = safe(source);
        reason = safe(reason);
        status = safe(status);
        durationMillis = clampDuration(durationMillis);
        intensity = clampIntensity(intensity);
    }

    public static BridgePayload hello(String sessionId, String version, Set<String> supportedEffects) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_HELLO,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                sessionId,
                version,
                true,
                true,
                true,
                false,
                normalizeEffects(supportedEffects),
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

    public static BridgePayload heartbeat(String sessionId) {
        return new BridgePayload(
                ClientBridgeProtocol.TYPE_HEARTBEAT,
                ClientBridgeProtocol.PROTOCOL_VERSION,
                0L,
                System.currentTimeMillis(),
                sessionId,
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
                0,
                0.0F,
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
                0,
                0.0F,
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
                0,
                0.0F,
                "CLIENT_MOD",
                "",
                "SYSTEM",
                safe(reason),
                "ERROR"
        );
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public int durationSeconds() {
        return Math.max(1, Math.min(600, durationMillis / 1000));
    }

    private void write(RegistryByteBuf buf) {
        try (DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(buf))) {
            out.writeUTF(type);
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
            out.writeInt(durationMillis);
            out.writeFloat(intensity);
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

    private static BridgePayload read(RegistryByteBuf buf) {
        try (DataInputStream in = new DataInputStream(new ByteBufInputStream(buf))) {
            String type = safe(in.readUTF());
            int protocol = in.readInt();
            long seq = Math.max(0L, in.readLong());
            long timestampMillis = Math.max(0L, in.readLong());
            String sessionId = safe(in.readUTF());
            String version = safe(in.readUTF());
            boolean clientVisuals = in.readBoolean();
            boolean clientOverlay = in.readBoolean();
            boolean clientShaderLike = in.readBoolean();
            boolean trueIrisShader = in.readBoolean();
            int count = Math.max(0, in.readInt());
            Set<String> supportedEffects = new LinkedHashSet<>();
            for (int index = 0; index < count && index < 64; index++) {
                supportedEffects.add(normalizeEffectId(in.readUTF()));
            }
            String effectId = normalizeEffectId(in.readUTF());
            int durationMillis = clampDuration(in.readInt());
            float intensity = clampIntensity(in.readFloat());
            String mode = safe(in.readUTF());
            String clearPolicy = safe(in.readUTF());
            String source = safe(in.readUTF());
            String reason = safe(in.readUTF());
            String status = safe(in.readUTF());
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
                    durationMillis,
                    intensity,
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

    private static String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeEffectId(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return "";
        }
        String normalized = effectId.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 48) {
            return normalized.substring(0, 48);
        }
        return normalized;
    }

    private static float clampIntensity(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampDuration(int durationMillis) {
        if (durationMillis <= 0) {
            return 1_000;
        }
        return Math.max(1_000, Math.min(600_000, durationMillis));
    }

    private static Set<String> normalizeEffects(Set<String> supportedEffects) {
        Set<String> normalized = new LinkedHashSet<>();
        if (supportedEffects != null) {
            for (String effectId : supportedEffects) {
                String effect = normalizeEffectId(effectId);
                if (!effect.isBlank() && ClientBridgeProtocol.SUPPORTED_EFFECTS.contains(effect)) {
                    normalized.add(effect);
                }
            }
        }
        return Set.copyOf(normalized);
    }
}
