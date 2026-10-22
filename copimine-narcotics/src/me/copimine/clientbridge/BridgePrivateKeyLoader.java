package me.copimine.clientbridge;

import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.BridgeSigningPolicy;

import javax.security.auth.Destroyable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded, fail-closed loader for the external CopiMine Ed25519 signing key. */
public final class BridgePrivateKeyLoader {
    public static final String PRIVATE_KEY_FILE_ENV = "COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE";
    public static final int MAX_ENCODED_FILE_BYTES = 4_096;
    public static final int MAX_DECODED_KEY_BYTES = 512;

    public enum FailureReason {
        NONE,
        CONFIGURATION_ABSENT,
        FILE_MISSING,
        FILE_UNREADABLE,
        INVALID_ENCODING,
        WRONG_KEY_TYPE,
        PUBLIC_KEY_MISMATCH,
        SIGNER_UNAVAILABLE
    }

    /** Safe, path-free result for startup diagnostics. Never includes key bytes or exception text. */
    public static final class LoadResult {
        private final BridgeSigner signer;
        private final FailureReason failureReason;

        private LoadResult(BridgeSigner signer, FailureReason failureReason) {
            this.signer = signer;
            this.failureReason = Objects.requireNonNull(failureReason, "failureReason");
            if ((signer != null) != (failureReason == FailureReason.NONE)) {
                throw new IllegalArgumentException("signer readiness and failure reason disagree");
            }
        }

        public Optional<BridgeSigner> signer() {
            return Optional.ofNullable(signer);
        }

        public FailureReason failureReason() {
            return failureReason;
        }

        @Override
        public String toString() {
            return "LoadResult[ready=" + (signer != null) + ", reason=" + failureReason + "]";
        }
    }

    private static final int STARTUP_CHALLENGE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] ED25519_OID = {(byte) 0x2B, (byte) 0x65, (byte) 0x70};

    private BridgePrivateKeyLoader() {
    }

    private static LoadResult failed(FailureReason reason) {
        return new LoadResult(null, reason);
    }

    private enum Pkcs8KeyType {
        ED25519,
        OTHER,
        INVALID
    }

    private static final class DerCursor {
        private final byte[] bytes;
        private int position;

        private DerCursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private int position() {
            return position;
        }

        private int readConstructedEnd(int expectedTag, int limit) {
            int length = readHeader(expectedTag, limit);
            int end = position + length;
            if (end > limit) {
                throw new IllegalArgumentException("DER value exceeds parent");
            }
            return end;
        }

        private byte[] readPrimitive(int expectedTag, int limit) {
            int length = readHeader(expectedTag, limit);
            int end = position + length;
            if (end > limit) {
                throw new IllegalArgumentException("DER value exceeds parent");
            }
            byte[] value = Arrays.copyOfRange(bytes, position, end);
            position = end;
            return value;
        }

        private int readHeader(int expectedTag, int limit) {
            if (position >= limit || (bytes[position++] & 0xFF) != expectedTag || position >= limit) {
                throw new IllegalArgumentException("Invalid DER tag");
            }
            int first = bytes[position++] & 0xFF;
            if (first < 0x80) {
                return first;
            }
            int count = first & 0x7F;
            if (count == 0 || count > 2 || position + count > limit || bytes[position] == 0) {
                throw new IllegalArgumentException("Invalid DER length");
            }
            int length = 0;
            for (int index = 0; index < count; index++) {
                length = (length << 8) | (bytes[position++] & 0xFF);
            }
            if (length < 0x80) {
                throw new IllegalArgumentException("Non-canonical DER length");
            }
            return length;
        }
    }

    /** Loads only the private key that proves possession of the pinned public trust anchor. */
    public static Optional<BridgeSigner> loadFromEnvironment() {
        String configuredPath = System.getenv(PRIVATE_KEY_FILE_ENV);
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }
        try {
            return load(Path.of(configuredPath), BridgeSigningPolicy.parsePinnedPublicKey(),
                    BridgeSigningPolicy.PINNED_PUBLIC_KEY_SHA256);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Loads from a caller-selected path, with the trust anchor supplied for testability. */
    static Optional<BridgeSigner> load(Path path, PublicKey expectedPublicKey, String expectedFingerprint) {
        if (path == null) {
            return Optional.empty();
        }
        Optional<byte[]> encoded = readBoundedFile(path);
        return encoded.map(bytes -> fromEncodedFileBytes(bytes, expectedPublicKey, expectedFingerprint))
                .orElseGet(Optional::empty);
    }

    /** Loads the environment key with safe reason codes and no path or exception details. */
    public static LoadResult loadFromEnvironmentWithDiagnostics() {
        String configuredPath = System.getenv(PRIVATE_KEY_FILE_ENV);
        if (configuredPath == null || configuredPath.isBlank()) {
            return failed(FailureReason.CONFIGURATION_ABSENT);
        }
        try {
            return loadConfiguredPath(configuredPath, BridgeSigningPolicy.parsePinnedPublicKey(),
                    BridgeSigningPolicy.PINNED_PUBLIC_KEY_SHA256);
        } catch (RuntimeException ignored) {
            return failed(FailureReason.SIGNER_UNAVAILABLE);
        }
    }

    static LoadResult loadConfiguredPath(String configuredPath, PublicKey expectedPublicKey,
            String expectedFingerprint) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return failed(FailureReason.CONFIGURATION_ABSENT);
        }
        try {
            return loadWithDiagnostics(Path.of(configuredPath), expectedPublicKey, expectedFingerprint);
        } catch (RuntimeException ignored) {
            return failed(FailureReason.FILE_UNREADABLE);
        }
    }

    static LoadResult loadWithDiagnostics(Path path, PublicKey expectedPublicKey, String expectedFingerprint) {
        if (path == null) {
            return failed(FailureReason.FILE_UNREADABLE);
        }
        final BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return failed(FailureReason.FILE_MISSING);
        } catch (IOException | SecurityException unreadable) {
            return failed(FailureReason.FILE_UNREADABLE);
        }
        if (!attributes.isRegularFile()) {
            return failed(FailureReason.FILE_UNREADABLE);
        }

        byte[] bounded = new byte[MAX_ENCODED_FILE_BYTES + 1];
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            int total = 0;
            int read;
            while ((read = input.read(bounded, total, bounded.length - total)) != -1) {
                total += read;
                if (total > MAX_ENCODED_FILE_BYTES || total == bounded.length) {
                    return failed(FailureReason.INVALID_ENCODING);
                }
            }
            return fromEncodedFileBytesWithDiagnostics(Arrays.copyOf(bounded, total),
                    expectedPublicKey, expectedFingerprint);
        } catch (NoSuchFileException missing) {
            return failed(FailureReason.FILE_MISSING);
        } catch (IOException | SecurityException unreadable) {
            return failed(FailureReason.FILE_UNREADABLE);
        } finally {
            Arrays.fill(bounded, (byte) 0);
        }
    }

    static LoadResult fromEncodedFileBytesWithDiagnostics(
            byte[] encodedFileBytes,
            PublicKey expectedPublicKey,
            String expectedFingerprint) {
        if (encodedFileBytes == null) {
            return failed(FailureReason.INVALID_ENCODING);
        }
        byte[] decodedKeyBytes = null;
        byte[] canonicalBase64Bytes = null;
        byte[] challenge = null;
        byte[] signatureBytes = null;
        PrivateKey parsedPrivateKey = null;
        boolean privateKeyTransferred = false;
        try {
            if (encodedFileBytes.length == 0 || encodedFileBytes.length > MAX_ENCODED_FILE_BYTES) {
                return failed(FailureReason.INVALID_ENCODING);
            }
            if (!isEd25519PublicKey(expectedPublicKey)) {
                return failed(FailureReason.WRONG_KEY_TYPE);
            }
            if (!hasExpectedFingerprint(expectedPublicKey, expectedFingerprint)) {
                return failed(FailureReason.PUBLIC_KEY_MISMATCH);
            }
            int encodedLength = stripOneTerminalLineEnding(encodedFileBytes);
            if (encodedLength <= 0 || !isAsciiBase64(encodedFileBytes, encodedLength)) {
                return failed(FailureReason.INVALID_ENCODING);
            }
            byte[] base64Bytes = Arrays.copyOf(encodedFileBytes, encodedLength);
            try {
                try {
                    decodedKeyBytes = Base64.getDecoder().decode(base64Bytes);
                } catch (IllegalArgumentException malformedBase64) {
                    return failed(FailureReason.INVALID_ENCODING);
                }
            } finally {
                Arrays.fill(base64Bytes, (byte) 0);
            }
            if (decodedKeyBytes.length == 0 || decodedKeyBytes.length > MAX_DECODED_KEY_BYTES) {
                return failed(FailureReason.INVALID_ENCODING);
            }
            canonicalBase64Bytes = Base64.getEncoder().encode(decodedKeyBytes);
            byte[] suppliedBase64Bytes = Arrays.copyOf(encodedFileBytes, encodedLength);
            try {
                if (!MessageDigest.isEqual(canonicalBase64Bytes, suppliedBase64Bytes)) {
                    return failed(FailureReason.INVALID_ENCODING);
                }
            } finally {
                Arrays.fill(suppliedBase64Bytes, (byte) 0);
            }

            Pkcs8KeyType keyType = classifyPkcs8KeyType(decodedKeyBytes);
            if (keyType == Pkcs8KeyType.INVALID) {
                return failed(FailureReason.INVALID_ENCODING);
            }
            if (keyType == Pkcs8KeyType.OTHER) {
                return failed(FailureReason.WRONG_KEY_TYPE);
            }
            try {
                parsedPrivateKey = KeyFactory.getInstance("Ed25519")
                        .generatePrivate(new PKCS8EncodedKeySpec(decodedKeyBytes));
            } catch (InvalidKeySpecException malformedKey) {
                return failed(FailureReason.INVALID_ENCODING);
            } catch (NoSuchAlgorithmException unavailableAlgorithm) {
                return failed(FailureReason.SIGNER_UNAVAILABLE);
            }
            if (!isEd25519PrivateKey(parsedPrivateKey)) {
                return failed(FailureReason.WRONG_KEY_TYPE);
            }

            challenge = new byte[STARTUP_CHALLENGE_BYTES];
            RANDOM.nextBytes(challenge);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(parsedPrivateKey);
            signer.update(challenge);
            signatureBytes = signer.sign();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(expectedPublicKey);
            verifier.update(challenge);
            if (signatureBytes.length != BridgeSigningPolicy.ED25519_SIGNATURE_BYTES
                    || !verifier.verify(signatureBytes)) {
                return failed(FailureReason.PUBLIC_KEY_MISMATCH);
            }

            BridgeSigner loaded = new LoadedBridgeSigner(parsedPrivateKey, expectedPublicKey);
            privateKeyTransferred = true;
            return new LoadResult(loaded, FailureReason.NONE);
        } catch (GeneralSecurityException | RuntimeException unavailable) {
            return failed(FailureReason.SIGNER_UNAVAILABLE);
        } finally {
            Arrays.fill(encodedFileBytes, (byte) 0);
            clear(decodedKeyBytes);
            clear(canonicalBase64Bytes);
            clear(challenge);
            clear(signatureBytes);
            if (!privateKeyTransferred) {
                destroyQuietly(parsedPrivateKey);
            }
        }
    }

    private static Pkcs8KeyType classifyPkcs8KeyType(byte[] encoded) {
        try {
            DerCursor cursor = new DerCursor(encoded);
            int outerEnd = cursor.readConstructedEnd(0x30, encoded.length);
            cursor.readPrimitive(0x02, outerEnd);
            int algorithmEnd = cursor.readConstructedEnd(0x30, outerEnd);
            byte[] oid = cursor.readPrimitive(0x06, algorithmEnd);
            if (!Arrays.equals(oid, ED25519_OID)) {
                return Pkcs8KeyType.OTHER;
            }
            return cursor.position() == algorithmEnd ? Pkcs8KeyType.ED25519 : Pkcs8KeyType.INVALID;
        } catch (IllegalArgumentException malformed) {
            return Pkcs8KeyType.INVALID;
        }
    }

    /**
     * Parses and consumes the supplied file bytes. The caller-owned array is zeroed on every exit;
     * this package-private boundary lets tests verify that property without using a real key file.
     */
    static Optional<BridgeSigner> fromEncodedFileBytes(
            byte[] encodedFileBytes,
            PublicKey expectedPublicKey,
            String expectedFingerprint) {
        if (encodedFileBytes == null) {
            return Optional.empty();
        }
        byte[] decodedKeyBytes = null;
        byte[] canonicalBase64Bytes = null;
        byte[] challenge = null;
        byte[] signatureBytes = null;
        PrivateKey parsedPrivateKey = null;
        boolean privateKeyTransferred = false;
        try {
            if (encodedFileBytes.length == 0 || encodedFileBytes.length > MAX_ENCODED_FILE_BYTES
                    || !isEd25519PublicKey(expectedPublicKey)
                    || !hasExpectedFingerprint(expectedPublicKey, expectedFingerprint)) {
                return Optional.empty();
            }
            int encodedLength = stripOneTerminalLineEnding(encodedFileBytes);
            if (encodedLength <= 0 || !isAsciiBase64(encodedFileBytes, encodedLength)) {
                return Optional.empty();
            }
            byte[] base64Bytes = Arrays.copyOf(encodedFileBytes, encodedLength);
            try {
                decodedKeyBytes = Base64.getDecoder().decode(base64Bytes);
            } finally {
                Arrays.fill(base64Bytes, (byte) 0);
            }
            if (decodedKeyBytes.length == 0 || decodedKeyBytes.length > MAX_DECODED_KEY_BYTES) {
                return Optional.empty();
            }
            canonicalBase64Bytes = Base64.getEncoder().encode(decodedKeyBytes);
            byte[] suppliedBase64Bytes = Arrays.copyOf(encodedFileBytes, encodedLength);
            try {
                if (!MessageDigest.isEqual(canonicalBase64Bytes, suppliedBase64Bytes)) {
                    return Optional.empty();
                }
            } finally {
                Arrays.fill(suppliedBase64Bytes, (byte) 0);
            }

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKeyBytes);
            parsedPrivateKey = KeyFactory.getInstance("Ed25519").generatePrivate(keySpec);
            if (!isEd25519PrivateKey(parsedPrivateKey)) {
                return Optional.empty();
            }
            challenge = new byte[STARTUP_CHALLENGE_BYTES];
            RANDOM.nextBytes(challenge);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(parsedPrivateKey);
            signer.update(challenge);
            signatureBytes = signer.sign();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(expectedPublicKey);
            verifier.update(challenge);
            if (signatureBytes.length != BridgeSigningPolicy.ED25519_SIGNATURE_BYTES
                    || !verifier.verify(signatureBytes)) {
                return Optional.empty();
            }
            BridgeSigner loaded = new LoadedBridgeSigner(parsedPrivateKey, expectedPublicKey);
            privateKeyTransferred = true;
            return Optional.of(loaded);
        } catch (GeneralSecurityException | RuntimeException ignored) {
            return Optional.empty();
        } finally {
            Arrays.fill(encodedFileBytes, (byte) 0);
            clear(decodedKeyBytes);
            clear(canonicalBase64Bytes);
            clear(challenge);
            clear(signatureBytes);
            if (!privateKeyTransferred) {
                destroyQuietly(parsedPrivateKey);
            }
        }
    }

    private static Optional<byte[]> readBoundedFile(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        byte[] bounded = new byte[MAX_ENCODED_FILE_BYTES + 1];
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            int total = 0;
            int read;
            while ((read = input.read(bounded, total, bounded.length - total)) != -1) {
                total += read;
                if (total > MAX_ENCODED_FILE_BYTES || total == bounded.length) {
                    Arrays.fill(bounded, (byte) 0);
                    return Optional.empty();
                }
            }
            byte[] encoded = Arrays.copyOf(bounded, total);
            Arrays.fill(bounded, (byte) 0);
            return Optional.of(encoded);
        } catch (IOException | SecurityException ignored) {
            return Optional.empty();
        } finally {
            Arrays.fill(bounded, (byte) 0);
        }
    }

    private static int stripOneTerminalLineEnding(byte[] bytes) {
        int length = bytes.length;
        if (bytes[length - 1] == '\n') {
            length--;
            if (length > 0 && bytes[length - 1] == '\r') {
                length--;
            }
        }
        return length;
    }

    private static boolean isAsciiBase64(byte[] bytes, int length) {
        boolean sawPadding = false;
        int padding = 0;
        for (int index = 0; index < length; index++) {
            int value = bytes[index] & 0xFF;
            boolean alphabet = value >= 'A' && value <= 'Z'
                    || value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9'
                    || value == '+' || value == '/';
            if (alphabet && !sawPadding) {
                continue;
            }
            if (value == '=' && index >= length - 2 && padding < 2) {
                sawPadding = true;
                padding++;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean hasExpectedFingerprint(PublicKey expectedPublicKey, String expectedFingerprint) {
        if (expectedFingerprint == null || !expectedFingerprint.matches("[0-9a-f]{64}")) {
            return false;
        }
        try {
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(expectedPublicKey.getEncoded());
            String actual = HexFormat.of().formatHex(fingerprint);
            Arrays.fill(fingerprint, (byte) 0);
            return expectedFingerprint.equals(actual);
        } catch (GeneralSecurityException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isEd25519PublicKey(PublicKey key) {
        return key instanceof EdECKey edECKey && edECKey.getParams() != null
                && "Ed25519".equalsIgnoreCase(edECKey.getParams().getName());
    }

    private static boolean isEd25519PrivateKey(PrivateKey key) {
        return key instanceof EdECKey edECKey && edECKey.getParams() != null
                && "Ed25519".equalsIgnoreCase(edECKey.getParams().getName());
    }

    private static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static void destroyQuietly(PrivateKey privateKey) {
        if (privateKey instanceof Destroyable destroyable) {
            try {
                destroyable.destroy();
            } catch (Exception ignored) {
                // The key object is no longer reachable from the loader on this failure path.
            }
        }
    }

    private static final class LoadedBridgeSigner implements BridgeSigner {
        private PrivateKey privateKey;
        private final PublicKey expectedPublicKey;

        private LoadedBridgeSigner(PrivateKey privateKey, PublicKey expectedPublicKey) {
            this.privateKey = privateKey;
            this.expectedPublicKey = expectedPublicKey;
        }

        @Override
        public synchronized boolean isAvailable() {
            return privateKey != null;
        }

        @Override
        public synchronized Optional<byte[]> sign(
                String nonce,
                UUID recipient,
                String streamId,
                long counter,
                long issuedAtMillis,
                long expiresAtMillis,
                byte[] exactBody) {
            if (privateKey == null || exactBody == null) {
                return Optional.empty();
            }
            byte[] bodyCopy = exactBody.clone();
            try {
                BridgeEnvelope.Metadata metadata = new BridgeEnvelope.Metadata(
                        BridgeSigningPolicy.PROTOCOL_VERSION,
                        BridgeSigningPolicy.PINNED_KEY_ID,
                        nonce,
                        recipient,
                        streamId,
                        counter,
                        issuedAtMillis,
                        expiresAtMillis);
                byte[] signature = BridgeSigningPolicy.signV4(privateKey, metadata, bodyCopy);
                try {
                    BridgeEnvelope envelope = new BridgeEnvelope(metadata, bodyCopy, signature);
                    if (BridgeSigningPolicy.verifyV4(expectedPublicKey, envelope, System.currentTimeMillis())
                            != BridgeSigningPolicy.VerificationResult.VALID) {
                        return Optional.empty();
                    }
                    return Optional.of(BridgeEnvelopeCodec.encode(envelope));
                } finally {
                    Arrays.fill(signature, (byte) 0);
                }
            } catch (RuntimeException ignored) {
                return Optional.empty();
            } finally {
                Arrays.fill(bodyCopy, (byte) 0);
            }
        }

        @Override
        public synchronized void close() {
            PrivateKey retiring = privateKey;
            privateKey = null;
            if (retiring != null) {
                destroyQuietly(retiring);
            }
        }
    }
}
