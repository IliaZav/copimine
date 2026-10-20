package me.copimine.client;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import me.copimine.protocol.BridgeEnvelopeCodec;

/** Raw, bounded transport for exact signed v4 envelope bytes. */
public record BridgeV4Payload(byte[] exactEnvelopeBytes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BridgeV4Payload> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("copimine", "client_bridge_v4"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeV4Payload> CODEC =
            CustomPacketPayload.codec(BridgeV4Payload::write, BridgeV4Payload::read);

    public BridgeV4Payload {
        exactEnvelopeBytes = Objects.requireNonNull(exactEnvelopeBytes, "exactEnvelopeBytes").clone();
        if (exactEnvelopeBytes.length == 0 || exactEnvelopeBytes.length > BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Invalid v4 bridge envelope payload size");
        }
    }

    @Override
    public byte[] exactEnvelopeBytes() {
        return exactEnvelopeBytes.clone();
    }

    /** Returns the owned payload to the protocol receiver, which must copy it only after admission. */
    byte[] exactEnvelopeBytesForProtocol() {
        return exactEnvelopeBytes;
    }

    @Override
    public Type<BridgeV4Payload> type() {
        return ID;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBytes(exactEnvelopeBytes);
    }

    private static BridgeV4Payload read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length <= 0 || length > BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Invalid v4 bridge envelope payload size");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new BridgeV4Payload(bytes);
    }
}
