package me.copimine.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Strict big-endian encoder/decoder for signed v4 envelopes. */
public final class BridgeEnvelopeCodec {
    public static final int MAX_ENVELOPE_BYTES = 66_000;

    private BridgeEnvelopeCodec() {
    }

    public static byte[] encode(BridgeEnvelope envelope) {
        if (envelope == null || envelope.metadata() == null) {
            throw new IllegalArgumentException("Missing v4 envelope");
        }
        BridgeEnvelope.Metadata metadata = envelope.metadata();
        byte[] body = envelope.bodyBytes();
        byte[] signature = envelope.signature();
        BridgeSigningPolicy.requireEnvelopeFields(metadata, body);
        if (signature == null || signature.length != BridgeSigningPolicy.ED25519_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("V4 envelope signature must be exactly 64 raw bytes");
        }

        byte[] keyId = metadata.keyId().getBytes(StandardCharsets.US_ASCII);
        byte[] nonce = metadata.clientNonce().getBytes(StandardCharsets.US_ASCII);
        byte[] streamId = metadata.streamId().getBytes(StandardCharsets.US_ASCII);
        long encodedLength = 1L + 1 + keyId.length + 1 + nonce.length + 16 + 1 + streamId.length
                + 8 + 8 + 8 + 4 + (long) body.length + signature.length;
        if (encodedLength > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("V4 envelope exceeds the total size limit");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) encodedLength);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(metadata.protocolVersion());
            writeLength8(output, keyId);
            writeLength8(output, nonce);
            writeUuid(output, metadata.recipientUuid());
            writeLength8(output, streamId);
            output.writeLong(metadata.counter());
            output.writeLong(metadata.issuedAtMillis());
            output.writeLong(metadata.expiresAtMillis());
            output.writeInt(body.length);
            output.write(body);
            output.write(signature);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode v4 envelope", exception);
        }
    }

    public static BridgeEnvelope decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Invalid v4 envelope length");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        try {
            int protocol = readU8(input);
            if (protocol != BridgeSigningPolicy.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported v4 envelope protocol");
            }
            String keyId = readAscii8(input, 1, BridgeSigningPolicy.MAX_KEY_ID_BYTES);
            String nonce = readAscii8(input, BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH,
                    BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH);
            UUID recipient = readUuid(input);
            String streamId = readAscii8(input, BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH,
                    BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH);
            requireRemaining(input, 8 + 8 + 8 + 4 + BridgeSigningPolicy.ED25519_SIGNATURE_BYTES);
            long counter = input.getLong();
            long issuedAtMillis = input.getLong();
            long expiresAtMillis = input.getLong();
            long bodyLength = Integer.toUnsignedLong(input.getInt());
            if (bodyLength < BridgeSigningPolicy.MIN_BODY_BYTES
                    || bodyLength > BridgeSigningPolicy.MAX_BODY_BYTES
                    || input.remaining() != bodyLength + BridgeSigningPolicy.ED25519_SIGNATURE_BYTES) {
                throw new IllegalArgumentException("Invalid v4 envelope body length");
            }

            BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(protocol, keyId, nonce, recipient,
                    streamId, counter, issuedAtMillis, expiresAtMillis);
            BridgeSigningPolicy.requireEnvelopeFields(metadata, new byte[]{1});

            // All attacker-controlled lengths and the exact trailing size are checked before allocation.
            byte[] body = new byte[(int) bodyLength];
            input.get(body);
            byte[] signature = new byte[BridgeSigningPolicy.ED25519_SIGNATURE_BYTES];
            input.get(signature);
            if (input.hasRemaining()) {
                throw new IllegalArgumentException("Trailing v4 envelope bytes");
            }
            return new BridgeEnvelope(metadata, body, signature);
        } catch (java.nio.BufferUnderflowException exception) {
            throw new IllegalArgumentException("Truncated v4 envelope", exception);
        }
    }

    private static int readU8(ByteBuffer input) {
        requireRemaining(input, 1);
        return Byte.toUnsignedInt(input.get());
    }

    private static String readAscii8(ByteBuffer input, int minimumLength, int maximumLength) {
        int length = readU8(input);
        if (length < minimumLength || length > maximumLength) {
            throw new IllegalArgumentException("Invalid v4 envelope string length");
        }
        requireRemaining(input, length);
        byte[] bytes = new byte[length];
        input.get(bytes);
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned < 0x21 || unsigned > 0x7e) {
                throw new IllegalArgumentException("V4 envelope strings must be printable ASCII");
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static UUID readUuid(ByteBuffer input) {
        requireRemaining(input, 16);
        return new UUID(input.getLong(), input.getLong());
    }

    private static void requireRemaining(ByteBuffer input, int count) {
        if (count < 0 || input.remaining() < count) {
            throw new IllegalArgumentException("Truncated v4 envelope");
        }
    }

    private static void writeLength8(DataOutputStream output, byte[] bytes) throws IOException {
        if (bytes.length > 255) {
            throw new IllegalArgumentException("V4 envelope field exceeds one-byte length");
        }
        output.writeByte(bytes.length);
        output.write(bytes);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }
}
