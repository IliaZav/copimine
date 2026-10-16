package me.copimine.clientbridge;

import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.BridgeSigningPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BridgePrivateKeyLoaderTest {
    private static final String NONCE = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[BridgeSigningPolicy.CLIENT_NONCE_BYTES]);
    private static final String STREAM = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[BridgeSigningPolicy.STREAM_ID_BYTES]);

    @TempDir
    Path tempDir;

    @Test
    void loadsOnlyAKeyThatMatchesTheExpectedPublicKeyAndReturnsSignedEnvelopeBytes() throws Exception {
        KeyPair pair = newEd25519KeyPair();
        Path keyFile = writeKeyFile(tempDir.resolve("signer.b64"), pair);

        Optional<BridgeSigner> loaded = BridgePrivateKeyLoader.load(
                keyFile, pair.getPublic(), fingerprint(pair.getPublic()));

        assertTrue(loaded.isPresent());
        long issuedAt = System.currentTimeMillis();
        byte[] encoded = loaded.orElseThrow().sign(
                NONCE, UUID.fromString("a7641699-b26c-4bf8-9525-64554f03e35a"), STREAM,
                1L, issuedAt, issuedAt + 10_000L,
                new byte[]{1, 2, 3}).orElseThrow();
        BridgeEnvelope envelope = BridgeEnvelopeCodec.decode(encoded);
        assertNotNull(envelope);
        assertArrayEquals(new byte[]{1, 2, 3}, envelope.bodyBytes());
        assertEquals(BridgeSigningPolicy.PINNED_KEY_ID, envelope.metadata().keyId());
        assertEquals(BridgeSigningPolicy.PROTOCOL_VERSION, envelope.metadata().protocolVersion());

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(BridgeSigningPolicy.canonicalV4SigningInput(envelope.metadata(), envelope.bodyBytes()));
        assertTrue(verifier.verify(envelope.signature()));
        loaded.orElseThrow().close();
        assertTrue(loaded.orElseThrow().sign(
                NONCE, UUID.randomUUID(), STREAM, 2L,
                System.currentTimeMillis(), System.currentTimeMillis() + 50L, new byte[]{1}).isEmpty());
    }

    @Test
    void acceptsOneOptionalTerminalLineEndingButRejectsInternalWhitespaceAndNonAscii() throws Exception {
        KeyPair pair = newEd25519KeyPair();
        byte[] canonical = encodedPrivateKey(pair);
        byte[] crlf = Arrays.copyOf(canonical, canonical.length + 2);
        crlf[canonical.length] = '\r';
        crlf[canonical.length + 1] = '\n';
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                crlf, pair.getPublic(), fingerprint(pair.getPublic())).isPresent());
        assertAllZero(crlf);

        byte[] withInternalSpace = insert(canonical, canonical.length / 2, (byte) ' ');
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                withInternalSpace, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(withInternalSpace);

        byte[] withNonAscii = insert(canonical, 0, (byte) 0xFF);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                withNonAscii, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(withNonAscii);
    }

    @Test
    void rejectsMissingEmptyMalformedOversizedAndMismatchedKeys() throws Exception {
        KeyPair pair = newEd25519KeyPair();
        KeyPair otherPair = newEd25519KeyPair();
        assertTrue(BridgePrivateKeyLoader.load(
                tempDir.resolve("missing.b64"), pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertTrue(BridgePrivateKeyLoader.load(
                tempDir, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());

        byte[] empty = new byte[0];
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                empty, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(empty);

        byte[] malformed = "not-base64".getBytes(StandardCharsets.US_ASCII);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                malformed, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(malformed);

        byte[] oversized = new byte[BridgePrivateKeyLoader.MAX_ENCODED_FILE_BYTES + 1];
        Arrays.fill(oversized, (byte) 'A');
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                oversized, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(oversized);

        byte[] decodedOversized = Base64.getEncoder().encode(new byte[BridgePrivateKeyLoader.MAX_DECODED_KEY_BYTES + 1]);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                decodedOversized, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(decodedOversized);

        byte[] wrongPrivateKey = encodedPrivateKey(otherPair);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                wrongPrivateKey, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(wrongPrivateKey);

        byte[] wrongFingerprint = encodedPrivateKey(pair);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                wrongFingerprint, pair.getPublic(), "00".repeat(32)).isEmpty());
        assertAllZero(wrongFingerprint);
    }

    @Test
    void rejectsSymbolicLinksEvenWhenTheyTargetAValidKeyFile() throws Exception {
        KeyPair pair = newEd25519KeyPair();
        Path target = writeKeyFile(tempDir.resolve("signer-target.b64"), pair);
        Path link = tempDir.resolve("signer-link.b64");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            assumeTrue(false, "symbolic links are unavailable on this test host");
        }

        assertTrue(Files.isSymbolicLink(link));
        assertTrue(BridgePrivateKeyLoader.load(
                link, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
    }

    @Test
    void zeroesCallerOwnedEncodedBytesAfterSuccessfulAndFailedAttempts() throws Exception {
        KeyPair pair = newEd25519KeyPair();
        byte[] valid = encodedPrivateKey(pair);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                valid, pair.getPublic(), fingerprint(pair.getPublic())).isPresent());
        assertAllZero(valid);

        byte[] invalid = "x".getBytes(StandardCharsets.US_ASCII);
        assertTrue(BridgePrivateKeyLoader.fromEncodedFileBytes(
                invalid, pair.getPublic(), fingerprint(pair.getPublic())).isEmpty());
        assertAllZero(invalid);
    }

    private static KeyPair newEd25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private Path writeKeyFile(Path path, KeyPair pair) throws Exception {
        Files.write(path, encodedPrivateKey(pair));
        return path;
    }

    private static byte[] encodedPrivateKey(KeyPair pair) {
        return Base64.getEncoder().encode(pair.getPrivate().getEncoded());
    }

    private static String fingerprint(java.security.PublicKey key) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
    }

    private static byte[] insert(byte[] source, int index, byte value) {
        byte[] result = new byte[source.length + 1];
        System.arraycopy(source, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(source, index, result, index + 1, source.length - index);
        return result;
    }

    private static void assertAllZero(byte[] bytes) {
        for (byte value : bytes) {
            assertFalse(value != 0, "sensitive input byte array was not cleared");
        }
    }
}
