package me.copimine.protocol;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeSigningPolicyTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final byte[] BODY = new byte[]{0x10, 0x00, (byte) 0xfe, 0x22, 0x7f};
    private static final UUID RECIPIENT = UUID.fromString("d8d4966c-f2c4-4a13-9e33-82940f1dc2d6");
    private static final String NONCE = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private static final String STREAM = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    private static KeyPair trusted;
    private static KeyPair untrusted;

    @BeforeAll
    static void generateEphemeralKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        trusted = generator.generateKeyPair();
        untrusted = generator.generateKeyPair();
    }

    @Test
    void pinsTheDedicatedCopiMinePublicKeyAndFingerprint() throws Exception {
        assertEquals("copimine-bridge-20261015-v1", BridgeSigningPolicy.PINNED_KEY_ID);
        assertEquals("MCowBQYDK2VwAyEAlud9gEV0EMTMJPDo5bQSDpd+giw+SD6/mpli1wr3l54=",
                BridgeSigningPolicy.PINNED_PUBLIC_KEY_X509_BASE64);
        assertEquals("2dadc8a92218471ad51721299974968c326e9ffd3b08ae8dd1e50ec7079dff62",
                BridgeSigningPolicy.PINNED_PUBLIC_KEY_SHA256);
        assertEquals("copimine:client-bridge:v4", BridgeSigningPolicy.DOMAIN);
        byte[] encoded = Base64.getDecoder().decode(BridgeSigningPolicy.PINNED_PUBLIC_KEY_X509_BASE64);
        assertEquals(BridgeSigningPolicy.PINNED_PUBLIC_KEY_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
        assertEquals("EdDSA", BridgeSigningPolicy.parsePinnedPublicKey().getAlgorithm());
    }

    @Test
    void canonicalV4InputMatchesTheSpecifiedBigEndianBytesExactly() throws Exception {
        BridgeEnvelope.Metadata metadata = metadata(9, NOW - 1_000, NOW + 10_000);
        byte[] expected = expectedCanonicalInput(metadata, BODY);

        assertArrayEquals(expected, BridgeSigningPolicy.canonicalV4SigningInput(metadata, BODY));
    }

    @Test
    void acceptsOnlyTheTrustedEphemeralSignerAndRawEd25519Signature() {
        BridgeEnvelope.Metadata metadata = metadata(9, NOW - 1_000, NOW + 10_000);
        byte[] signature = BridgeSigningPolicy.signV4(trusted.getPrivate(), metadata, BODY);
        BridgeEnvelope envelope = new BridgeEnvelope(metadata, BODY, signature);

        assertEquals(64, signature.length);
        assertEquals(BridgeSigningPolicy.VerificationResult.VALID,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), envelope, NOW));
        assertNotEquals(BridgeSigningPolicy.VerificationResult.VALID,
                BridgeSigningPolicy.verifyV4(untrusted.getPublic(), envelope, NOW));
    }

    @Test
    void signatureBindsTheExactBodyAndEveryEnvelopeMetadataField() {
        BridgeEnvelope.Metadata metadata = metadata(9, NOW - 1_000, NOW + 10_000);
        byte[] signature = BridgeSigningPolicy.signV4(trusted.getPrivate(), metadata, BODY);
        BridgeEnvelope original = new BridgeEnvelope(metadata, BODY, signature);

        assertEquals(BridgeSigningPolicy.VerificationResult.INVALID_SIGNATURE,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(),
                        new BridgeEnvelope(metadata, new byte[]{0x10, 0x00, (byte) 0xfd, 0x22, 0x7f}, signature), NOW));
        assertInvalidMetadata(metadata("wrong-key-id", 9, NOW - 1_000, NOW + 10_000), signature);
        assertInvalidSignature(new BridgeEnvelope.Metadata(4, BridgeSigningPolicy.PINNED_KEY_ID,
                Base64.getUrlEncoder().withoutPadding().encodeToString(sequence(32, 1)), RECIPIENT,
                STREAM, 9, NOW - 1_000, NOW + 10_000), signature);
        assertInvalidSignature(new BridgeEnvelope.Metadata(4, BridgeSigningPolicy.PINNED_KEY_ID, NONCE,
                UUID.fromString("0b6d7c9d-4fd9-4c3a-a6be-55007c3cbb76"), STREAM,
                9, NOW - 1_000, NOW + 10_000), signature);
        assertInvalidSignature(new BridgeEnvelope.Metadata(4, BridgeSigningPolicy.PINNED_KEY_ID, NONCE,
                RECIPIENT, Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0}), 9, NOW - 1_000, NOW + 10_000), signature);
        assertInvalidSignature(metadata(10, NOW - 1_000, NOW + 10_000), signature);
        assertInvalidSignature(metadata(9, NOW - 999, NOW + 10_000), signature);
        assertInvalidSignature(metadata(9, NOW - 1_000, NOW + 10_001), signature);
        assertEquals(BridgeSigningPolicy.VerificationResult.VALID,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), original, NOW));
    }

    @Test
    void rejectsV3DomainSignaturesWithoutTryingALegacyFallback() throws Exception {
        BridgeEnvelope.Metadata metadata = metadata(9, NOW - 1_000, NOW + 10_000);
        Signature legacy = Signature.getInstance("Ed25519");
        legacy.initSign(trusted.getPrivate());
        legacy.update("copimine:client-bridge:v3".getBytes(StandardCharsets.US_ASCII));
        legacy.update(BODY);

        assertEquals(BridgeSigningPolicy.VerificationResult.INVALID_SIGNATURE,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(),
                        new BridgeEnvelope(metadata, BODY, legacy.sign()), NOW));
    }

    @Test
    void rejectsExpiredFutureDatedAndOverlongWindowsAtExactBoundaries() {
        byte[] body = BODY;
        BridgeEnvelope.Metadata expired = metadata(9, NOW - 60_000, NOW);
        BridgeEnvelope expiredEnvelope = signed(trusted, expired, body);
        assertEquals(BridgeSigningPolicy.VerificationResult.EXPIRED,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), expiredEnvelope, NOW));

        long futureIssued = NOW + BridgeSigningPolicy.MAX_FUTURE_SKEW_MILLIS + 1;
        BridgeEnvelope.Metadata future = metadata(9, futureIssued, futureIssued + 1_000);
        assertEquals(BridgeSigningPolicy.VerificationResult.FUTURE_ISSUED_AT,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), signed(trusted, future, body), NOW));

        BridgeEnvelope.Metadata tooLong = metadata(9, NOW - 1_000,
                NOW - 1_000 + BridgeSigningPolicy.MAX_VALIDITY_MILLIS + 1);
        assertEquals(BridgeSigningPolicy.VerificationResult.VALIDITY_TOO_LONG,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), rawSigned(trusted, tooLong, body), NOW));

        BridgeEnvelope.Metadata boundary = metadata(9, NOW - 1_000,
                NOW - 1_000 + BridgeSigningPolicy.MAX_VALIDITY_MILLIS);
        assertEquals(BridgeSigningPolicy.VerificationResult.VALID,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), signed(trusted, boundary, body), NOW));
    }

    @Test
    void refusesNoncanonicalBase64UrlAndInvalidMetadataOrBody() {
        assertThrows(IllegalArgumentException.class,
                () -> BridgeSigningPolicy.signV4(trusted.getPrivate(),
                        metadata("not*base64url", 9, NOW - 1_000, NOW + 10_000), BODY));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeSigningPolicy.signV4(trusted.getPrivate(),
                        new BridgeEnvelope.Metadata(3, BridgeSigningPolicy.PINNED_KEY_ID, NONCE, RECIPIENT,
                                STREAM, 9, NOW - 1_000, NOW + 10_000), BODY));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeSigningPolicy.signV4(trusted.getPrivate(),
                        metadata(9, NOW - 1_000, NOW + 10_000), new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeSigningPolicy.signV4(trusted.getPrivate(),
                        metadata("wrong-key-id", 9, NOW - 1_000, NOW + 10_000), BODY));
    }

    private static void assertInvalidMetadata(BridgeEnvelope.Metadata metadata, byte[] signature) {
        assertEquals(BridgeSigningPolicy.VerificationResult.INVALID_METADATA,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), new BridgeEnvelope(metadata, BODY, signature), NOW));
    }

    private static void assertInvalidSignature(BridgeEnvelope.Metadata metadata, byte[] signature) {
        assertEquals(BridgeSigningPolicy.VerificationResult.INVALID_SIGNATURE,
                BridgeSigningPolicy.verifyV4(trusted.getPublic(), new BridgeEnvelope(metadata, BODY, signature), NOW));
    }

    private static BridgeEnvelope signed(KeyPair signer, BridgeEnvelope.Metadata metadata, byte[] body) {
        return new BridgeEnvelope(metadata, body, BridgeSigningPolicy.signV4(signer.getPrivate(), metadata, body));
    }

    private static BridgeEnvelope rawSigned(KeyPair signer, BridgeEnvelope.Metadata metadata, byte[] body) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(signer.getPrivate());
            signature.update(BridgeSigningPolicy.canonicalV4SigningInput(metadata, body));
            return new BridgeEnvelope(metadata, body, signature.sign());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static BridgeEnvelope.Metadata metadata(long counter, long issuedAt, long expiresAt) {
        return metadata(BridgeSigningPolicy.PINNED_KEY_ID, counter, issuedAt, expiresAt);
    }

    private static BridgeEnvelope.Metadata metadata(String keyId, long counter, long issuedAt, long expiresAt) {
        return new BridgeEnvelope.Metadata(4, keyId, NONCE, RECIPIENT, STREAM, counter, issuedAt, expiresAt);
    }

    private static byte[] expectedCanonicalInput(BridgeEnvelope.Metadata metadata, byte[] body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeLengthPrefixed(out, "copimine:client-bridge:v4".getBytes(StandardCharsets.US_ASCII));
        out.writeInt(1);
        out.writeByte(4);
        writeLengthPrefixed(out, BridgeSigningPolicy.PINNED_KEY_ID.getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(out, NONCE.getBytes(StandardCharsets.US_ASCII));
        out.writeLong(RECIPIENT.getMostSignificantBits());
        out.writeLong(RECIPIENT.getLeastSignificantBits());
        writeLengthPrefixed(out, STREAM.getBytes(StandardCharsets.US_ASCII));
        out.writeLong(metadata.counter());
        out.writeLong(metadata.issuedAtMillis());
        out.writeLong(metadata.expiresAtMillis());
        writeLengthPrefixed(out, body);
        return bytes.toByteArray();
    }

    private static void writeLengthPrefixed(DataOutputStream out, byte[] value) throws Exception {
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] sequence(int length, int start) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (start + i);
        }
        return bytes;
    }
}
