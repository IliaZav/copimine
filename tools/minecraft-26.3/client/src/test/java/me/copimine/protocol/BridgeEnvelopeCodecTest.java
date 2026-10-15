package me.copimine.protocol;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeEnvelopeCodecTest {
    private static final String NONCE = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private static final String STREAM = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    private static final UUID RECIPIENT = UUID.fromString("d8d4966c-f2c4-4a13-9e33-82940f1dc2d6");

    @Test
    void encodesTheExactEnvelopeLayoutAndRoundTripsWithoutNormalizingBytes() throws Exception {
        byte[] body = new byte[]{0x10, 0x00, (byte) 0xfe, 0x22};
        byte[] signature = new byte[BridgeSigningPolicy.ED25519_SIGNATURE_BYTES];
        for (int i = 0; i < signature.length; i++) {
            signature[i] = (byte) i;
        }
        BridgeEnvelope envelope = envelope(body, signature);

        byte[] encoded = BridgeEnvelopeCodec.encode(envelope);
        assertArrayEquals(expectedEnvelope(body, signature), encoded);
        BridgeEnvelope decoded = BridgeEnvelopeCodec.decode(encoded);
        assertEquals(envelope.metadata(), decoded.metadata());
        assertArrayEquals(body, decoded.bodyBytes());
        assertArrayEquals(signature, decoded.signature());
        assertArrayEquals(encoded, BridgeEnvelopeCodec.encode(decoded));
    }

    @Test
    void acceptsMinimumAndMaximumBodyLengthsButRejectsOneByteOutside() {
        byte[] signature = new byte[64];
        byte[] minimum = BridgeEnvelopeCodec.encode(envelope(new byte[]{1}, signature));
        assertEquals(1, BridgeEnvelopeCodec.decode(minimum).bodyBytes().length);

        byte[] maximum = BridgeEnvelopeCodec.encode(envelope(new byte[65_536], signature));
        assertEquals(65_536, BridgeEnvelopeCodec.decode(maximum).bodyBytes().length);

        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(envelope(new byte[0], signature)));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(envelope(new byte[65_537], signature)));
    }

    @Test
    void rejectsMalformedLengthsTruncationAndTrailingBytes() {
        byte[] valid = BridgeEnvelopeCodec.encode(envelope(new byte[]{1, 2, 3}, new byte[64]));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.decode(java.util.Arrays.copyOf(valid, valid.length - 1)));
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> BridgeEnvelopeCodec.decode(trailing));

        byte[] wrongProtocol = valid.clone();
        wrongProtocol[0] = 3;
        assertThrows(IllegalArgumentException.class, () -> BridgeEnvelopeCodec.decode(wrongProtocol));

        byte[] badNonceLength = valid.clone();
        badNonceLength[1 + 1 + BridgeSigningPolicy.PINNED_KEY_ID.length()] = 42;
        assertThrows(IllegalArgumentException.class, () -> BridgeEnvelopeCodec.decode(badNonceLength));

        byte[] overflowingBodyLength = valid.clone();
        int bodyLengthOffset = 1 + 1 + BridgeSigningPolicy.PINNED_KEY_ID.length() + 1 + 43 + 16 + 1 + 22 + 8 + 8 + 8;
        overflowingBodyLength[bodyLengthOffset] = (byte) 0xff;
        overflowingBodyLength[bodyLengthOffset + 1] = (byte) 0xff;
        overflowingBodyLength[bodyLengthOffset + 2] = (byte) 0xff;
        overflowingBodyLength[bodyLengthOffset + 3] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.decode(overflowingBodyLength));
    }

    @Test
    void rejectsNoncanonicalBase64UrlAndEnvelopeAboveTotalCap() {
        BridgeEnvelope.Metadata paddedNonce = new BridgeEnvelope.Metadata(4,
                BridgeSigningPolicy.PINNED_KEY_ID, NONCE + "=", RECIPIENT, STREAM, 1, 1_000, 2_000);
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(new BridgeEnvelope(paddedNonce, new byte[]{1}, new byte[64])));

        BridgeEnvelope.Metadata paddedStream = new BridgeEnvelope.Metadata(4,
                BridgeSigningPolicy.PINNED_KEY_ID, NONCE, RECIPIENT, STREAM + "=", 1, 1_000, 2_000);
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(new BridgeEnvelope(paddedStream, new byte[]{1}, new byte[64])));

        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.decode(new byte[BridgeEnvelopeCodec.MAX_ENVELOPE_BYTES + 1]));
    }

    @Test
    void rejectsWrongKeyIdAndSignatureLength() {
        BridgeEnvelope.Metadata wrongKeyId = new BridgeEnvelope.Metadata(4, "another-key", NONCE,
                RECIPIENT, STREAM, 1, 1_000, 2_000);
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(new BridgeEnvelope(wrongKeyId, new byte[]{1}, new byte[64])));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeEnvelopeCodec.encode(envelope(new byte[]{1}, new byte[63])));
    }

    private static BridgeEnvelope envelope(byte[] body, byte[] signature) {
        BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(4,
                BridgeSigningPolicy.PINNED_KEY_ID, NONCE, RECIPIENT, STREAM,
                0x0102030405060708L, 1_800_000_000_000L, 1_800_000_010_000L);
        return new BridgeEnvelope(metadata, body, signature);
    }

    private static byte[] expectedEnvelope(byte[] body, byte[] signature) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(4);
        writeByteString(out, BridgeSigningPolicy.PINNED_KEY_ID);
        writeByteString(out, NONCE);
        out.writeLong(RECIPIENT.getMostSignificantBits());
        out.writeLong(RECIPIENT.getLeastSignificantBits());
        writeByteString(out, STREAM);
        out.writeLong(0x0102030405060708L);
        out.writeLong(1_800_000_000_000L);
        out.writeLong(1_800_000_010_000L);
        out.writeInt(body.length);
        out.write(body);
        out.write(signature);
        return bytes.toByteArray();
    }

    private static void writeByteString(DataOutputStream out, String value) throws Exception {
        byte[] ascii = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeByte(ascii.length);
        out.write(ascii);
    }
}
