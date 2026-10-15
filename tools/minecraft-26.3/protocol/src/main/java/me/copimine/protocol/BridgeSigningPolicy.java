package me.copimine.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Domain-separated Ed25519 policy for the CopiMine client bridge protocol v4. */
public final class BridgeSigningPolicy {
    public static final String DOMAIN = "copimine:client-bridge:v4";
    public static final String PINNED_KEY_ID = "copimine-bridge-20261015-v1";
    public static final String PINNED_PUBLIC_KEY_X509_BASE64 =
            "MCowBQYDK2VwAyEAlud9gEV0EMTMJPDo5bQSDpd+giw+SD6/mpli1wr3l54=";
    public static final String PINNED_PUBLIC_KEY_SHA256 =
            "2dadc8a92218471ad51721299974968c326e9ffd3b08ae8dd1e50ec7079dff62";
    public static final int PROTOCOL_VERSION = 4;
    public static final int CANONICAL_FORMAT_VERSION = 1;
    public static final int ED25519_SIGNATURE_BYTES = 64;
    public static final int MAX_KEY_ID_BYTES = 64;
    public static final int CLIENT_NONCE_BYTES = 32;
    public static final int CLIENT_NONCE_BASE64URL_LENGTH = 43;
    public static final int STREAM_ID_BYTES = 16;
    public static final int STREAM_ID_BASE64URL_LENGTH = 22;
    public static final int MIN_BODY_BYTES = 1;
    public static final int MAX_BODY_BYTES = 65_536;
    public static final long MAX_VALIDITY_MILLIS = 60_000L;
    public static final long MAX_FUTURE_SKEW_MILLIS = 30_000L;

    private static final byte[] DOMAIN_BYTES = DOMAIN.getBytes(StandardCharsets.US_ASCII);

    private BridgeSigningPolicy() {
    }

    /** Returns the immutable public trust anchor after checking its configured fingerprint. */
    public static PublicKey parsePinnedPublicKey() {
        try {
            byte[] encoded = Base64.getDecoder().decode(PINNED_PUBLIC_KEY_X509_BASE64);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
            if (!PINNED_PUBLIC_KEY_SHA256.equals(fingerprint)) {
                throw new IllegalStateException("Pinned bridge public-key fingerprint mismatch");
            }
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            requireEd25519Key(key, "public");
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load pinned bridge public key", exception);
        }
    }

    /** Signs the exact v4 canonical input and returns the raw 64-byte Ed25519 signature. */
    public static byte[] signV4(PrivateKey privateKey, BridgeEnvelope.Metadata metadata, byte[] exactBodyBytes) {
        requireEd25519Key(privateKey, "private");
        byte[] canonical = canonicalV4SigningInput(metadata, exactBodyBytes);
        if (!hasValidWindow(metadata.issuedAtMillis(), metadata.expiresAtMillis())) {
            throw new IllegalArgumentException("Invalid v4 bridge validity window");
        }
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(canonical);
            byte[] signature = signer.sign();
            if (signature.length != ED25519_SIGNATURE_BYTES) {
                throw new IllegalStateException("Ed25519 provider returned an invalid signature length");
            }
            return signature;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign v4 bridge envelope", exception);
        }
    }

    /**
     * Verifies one v4 envelope against the caller-selected trust anchor. This method has no legacy
     * domain or format fallback: only the exact v4 canonical input is ever checked.
     */
    public static VerificationResult verifyV4(PublicKey publicKey, BridgeEnvelope envelope, long nowMillis) {
        if (publicKey == null || !isEd25519Key(publicKey)) {
            return VerificationResult.INVALID_KEY;
        }
        if (envelope == null || !isValidMetadata(envelope.metadata())) {
            return VerificationResult.INVALID_METADATA;
        }
        byte[] body = envelope.bodyBytes();
        if (!isValidBody(body)) {
            return VerificationResult.INVALID_BODY;
        }
        VerificationResult freshness = validateFreshness(
                envelope.metadata().issuedAtMillis(), envelope.metadata().expiresAtMillis(), nowMillis);
        if (freshness != VerificationResult.VALID) {
            return freshness;
        }
        byte[] signatureBytes = envelope.signature();
        if (signatureBytes == null || signatureBytes.length != ED25519_SIGNATURE_BYTES) {
            return VerificationResult.MALFORMED_SIGNATURE;
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonicalV4SigningInput(envelope.metadata(), body));
            return verifier.verify(signatureBytes)
                    ? VerificationResult.VALID
                    : VerificationResult.INVALID_SIGNATURE;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return VerificationResult.INVALID_SIGNATURE;
        }
    }

    /** Exposed for deterministic interoperability tests and non-secret protocol diagnostics. */
    public static byte[] canonicalV4SigningInput(BridgeEnvelope.Metadata metadata, byte[] exactBodyBytes) {
        requireCanonicalFields(metadata, exactBodyBytes);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    DOMAIN_BYTES.length + metadata.keyId().length() + metadata.clientNonce().length()
                            + metadata.streamId().length() + exactBodyBytes.length + 80);
            DataOutputStream output = new DataOutputStream(bytes);
            writeLengthPrefixed(output, DOMAIN_BYTES);
            output.writeInt(CANONICAL_FORMAT_VERSION);
            output.writeByte(PROTOCOL_VERSION);
            writeLengthPrefixed(output, metadata.keyId().getBytes(StandardCharsets.US_ASCII));
            writeLengthPrefixed(output, metadata.clientNonce().getBytes(StandardCharsets.US_ASCII));
            output.writeLong(metadata.recipientUuid().getMostSignificantBits());
            output.writeLong(metadata.recipientUuid().getLeastSignificantBits());
            writeLengthPrefixed(output, metadata.streamId().getBytes(StandardCharsets.US_ASCII));
            output.writeLong(metadata.counter());
            output.writeLong(metadata.issuedAtMillis());
            output.writeLong(metadata.expiresAtMillis());
            writeLengthPrefixed(output, exactBodyBytes);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode v4 bridge signing input", exception);
        }
    }

    static void requireEnvelopeFields(BridgeEnvelope.Metadata metadata, byte[] bodyBytes) {
        requireCanonicalFields(metadata, bodyBytes);
        if (!hasValidWindow(metadata.issuedAtMillis(), metadata.expiresAtMillis())) {
            throw new IllegalArgumentException("Invalid v4 bridge validity window");
        }
    }

    static boolean isValidMetadata(BridgeEnvelope.Metadata metadata) {
        if (metadata == null || metadata.protocolVersion() != PROTOCOL_VERSION
                || !PINNED_KEY_ID.equals(metadata.keyId()) || !isValidKeyId(metadata.keyId())
                || !isCanonicalBase64Url(metadata.clientNonce(), CLIENT_NONCE_BYTES,
                        CLIENT_NONCE_BASE64URL_LENGTH)
                || metadata.recipientUuid() == null
                || !isCanonicalBase64Url(metadata.streamId(), STREAM_ID_BYTES, STREAM_ID_BASE64URL_LENGTH)
                || metadata.counter() <= 0) {
            return false;
        }
        return metadata.issuedAtMillis() > 0 && metadata.expiresAtMillis() > metadata.issuedAtMillis();
    }

    static boolean isValidBody(byte[] bodyBytes) {
        return bodyBytes != null && bodyBytes.length >= MIN_BODY_BYTES && bodyBytes.length <= MAX_BODY_BYTES;
    }

    static boolean isCanonicalBase64Url(String value, int decodedBytes, int encodedChars) {
        if (value == null || value.length() != encodedChars) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z') && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9') && character != '_' && character != '-') {
                return false;
            }
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == decodedBytes
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isValidKeyId(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_KEY_ID_BYTES) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z') && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9') && character != '.' && character != '_'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static void requireCanonicalFields(BridgeEnvelope.Metadata metadata, byte[] bodyBytes) {
        if (!isValidMetadata(metadata)) {
            throw new IllegalArgumentException("Invalid v4 bridge metadata");
        }
        if (!isValidBody(bodyBytes)) {
            throw new IllegalArgumentException("Invalid v4 bridge body length");
        }
    }

    private static boolean hasValidWindow(long issuedAtMillis, long expiresAtMillis) {
        return issuedAtMillis > 0 && expiresAtMillis > issuedAtMillis
                && expiresAtMillis - issuedAtMillis <= MAX_VALIDITY_MILLIS;
    }

    private static VerificationResult validateFreshness(long issuedAtMillis, long expiresAtMillis, long nowMillis) {
        if (nowMillis < 0 || issuedAtMillis <= 0 || expiresAtMillis <= 0 || expiresAtMillis <= issuedAtMillis) {
            return VerificationResult.INVALID_TIME;
        }
        long validity = expiresAtMillis - issuedAtMillis;
        if (validity > MAX_VALIDITY_MILLIS) {
            return VerificationResult.VALIDITY_TOO_LONG;
        }
        if (issuedAtMillis > nowMillis && issuedAtMillis - nowMillis > MAX_FUTURE_SKEW_MILLIS) {
            return VerificationResult.FUTURE_ISSUED_AT;
        }
        if (expiresAtMillis <= nowMillis) {
            return VerificationResult.EXPIRED;
        }
        return VerificationResult.VALID;
    }

    private static void writeLengthPrefixed(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static boolean isEd25519Key(java.security.Key key) {
        if (!(key instanceof EdECKey edECKey) || edECKey.getParams() == null) {
            return false;
        }
        return "Ed25519".equalsIgnoreCase(edECKey.getParams().getName());
    }

    private static void requireEd25519Key(java.security.Key key, String kind) {
        if (!isEd25519Key(key)) {
            throw new IllegalArgumentException("Expected an Ed25519 " + kind + " key");
        }
    }

    public enum VerificationResult {
        VALID,
        INVALID_KEY,
        INVALID_METADATA,
        INVALID_BODY,
        INVALID_TIME,
        FUTURE_ISSUED_AT,
        EXPIRED,
        VALIDITY_TOO_LONG,
        MALFORMED_SIGNATURE,
        INVALID_SIGNATURE
    }
}
