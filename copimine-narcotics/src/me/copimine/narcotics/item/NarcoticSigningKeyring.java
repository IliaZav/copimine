package me.copimine.narcotics.item;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Primary-only signer with a bounded set of legacy verification keys. */
final class NarcoticSigningKeyring {
    private static final int MAX_LEGACY_KEYS = 8;

    private final byte[] primarySecret;
    private final List<byte[]> verificationSecrets;

    private NarcoticSigningKeyring(byte[] primarySecret, List<byte[]> verificationSecrets) {
        this.primarySecret = primarySecret.clone();
        this.verificationSecrets = List.copyOf(verificationSecrets);
    }

    static NarcoticSigningKeyring fromBase64(String primaryEncoded, Iterable<String> legacyEncoded) {
        byte[] primary = decodeSecret(primaryEncoded, "primary key");
        List<byte[]> keys = new ArrayList<>();
        keys.add(primary);
        int legacyKeyCount = 0;

        if (legacyEncoded != null) {
            for (String encoded : legacyEncoded) {
                if (encoded == null || encoded.isBlank()) {
                    continue;
                }
                byte[] candidate = decodeSecret(encoded, "legacy key");
                if (containsKey(keys, candidate)) {
                    continue;
                }
                if (legacyKeyCount >= MAX_LEGACY_KEYS) {
                    throw new IllegalArgumentException("Narcotics legacy signing keyring exceeds its maximum of "
                            + MAX_LEGACY_KEYS + " keys.");
                }
                keys.add(candidate);
                legacyKeyCount++;
            }
        }

        return new NarcoticSigningKeyring(primary, keys);
    }

    String sign(String payload) {
        return hmac(primarySecret, payload);
    }

    boolean verify(String payload, String actualSignature) {
        if (actualSignature == null) {
            return false;
        }
        byte[] actualBytes = actualSignature.getBytes(StandardCharsets.UTF_8);
        boolean valid = false;
        for (byte[] candidate : verificationSecrets) {
            String expected = hmac(candidate, payload);
            valid |= MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actualBytes);
        }
        return valid;
    }

    int verificationKeyCount() {
        return verificationSecrets.size();
    }

    private static boolean containsKey(List<byte[]> keys, byte[] candidate) {
        boolean duplicate = false;
        for (byte[] existing : keys) {
            duplicate |= MessageDigest.isEqual(existing, candidate);
        }
        return duplicate;
    }

    private static byte[] decodeSecret(String encoded, String description) {
        if (encoded != null) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encoded.trim());
                if (decoded.length >= 32) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // Do not use malformed or weak keys.
            }
        }
        throw new IllegalArgumentException(
                "Narcotics signing secret must be base64 and at least 32 bytes (" + description + ").");
    }

    private static String hmac(byte[] secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Narcotic signing is unavailable; refusing to issue an item.", error);
        }
    }
}
