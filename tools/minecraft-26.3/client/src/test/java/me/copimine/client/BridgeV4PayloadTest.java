package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Base64;
import java.util.UUID;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigningPolicy;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BridgeV4PayloadTest {
    private static final String NONCE = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[BridgeSigningPolicy.CLIENT_NONCE_BYTES]);
    private static final String STREAM = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[BridgeSigningPolicy.STREAM_ID_BYTES]);
    private static final UUID RECIPIENT = UUID.fromString("d8d4966c-f2c4-4a13-9e33-82940f1dc2d6");

    @Test
    void payloadCodecPreservesTheRawEnvelopeBytesSentByPaper() {
        BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(
                BridgeSigningPolicy.PROTOCOL_VERSION,
                BridgeSigningPolicy.PINNED_KEY_ID,
                NONCE,
                RECIPIENT,
                STREAM,
                1,
                1_000,
                2_000);
        byte[] envelope = BridgeEnvelopeCodec.encode(new BridgeEnvelope(metadata, new byte[] {1}, new byte[64]));
        ByteBuf rawPayload = Unpooled.wrappedBuffer(envelope);
        RegistryFriendlyByteBuf frame = new RegistryFriendlyByteBuf(rawPayload, RegistryAccess.EMPTY);

        try {
            assertArrayEquals(envelope, BridgeV4Payload.CODEC.decode(frame).exactEnvelopeBytes());
            org.junit.jupiter.api.Assertions.assertEquals(0, frame.readableBytes());
        } finally {
            rawPayload.release();
        }
    }

    @Test
    void payloadCodecWritesRawRequestBytesForPaper() {
        byte[] request = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(
                NONCE, RECIPIENT, UUID.fromString("d65f25b3-f4e7-4d62-ae6a-d502e60c9339"), new byte[16]));
        ByteBuf rawPayload = Unpooled.buffer();
        RegistryFriendlyByteBuf frame = new RegistryFriendlyByteBuf(rawPayload, RegistryAccess.EMPTY);

        try {
            BridgeV4Payload.CODEC.encode(frame, new BridgeV4Payload(request));
            byte[] encodedPayload = new byte[rawPayload.readableBytes()];
            rawPayload.getBytes(rawPayload.readerIndex(), encodedPayload);
            assertArrayEquals(request, encodedPayload);
        } finally {
            rawPayload.release();
        }
    }

    @Test
    void payloadCodecRejectsEmptyAndOversizedRawPayloads() {
        ByteBuf empty = Unpooled.buffer();
        RegistryFriendlyByteBuf emptyFrame = new RegistryFriendlyByteBuf(empty, RegistryAccess.EMPTY);
        try {
            assertThrows(IllegalArgumentException.class, () -> BridgeV4Payload.CODEC.decode(emptyFrame));
        } finally {
            empty.release();
        }

        ByteBuf oversized = Unpooled.buffer(BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES + 1);
        oversized.writeZero(BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES + 1);
        RegistryFriendlyByteBuf oversizedFrame = new RegistryFriendlyByteBuf(oversized, RegistryAccess.EMPTY);
        try {
            assertThrows(IllegalArgumentException.class, () -> BridgeV4Payload.CODEC.decode(oversizedFrame));
        } finally {
            oversized.release();
        }
    }
}
