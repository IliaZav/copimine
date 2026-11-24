package me.copimine.dungeon.editor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Server-issued authenticity token for the builder wand (spec OV4.3).
 *
 * <p>The wand is a custom-model-data item, but item lore, name and even the
 * texture are trivially forgeable by a player. Building privileges therefore
 * never come from the item's appearance: they come from a random MAC
 * (HMAC-SHA-256) that only the server can mint over the owner identity, the
 * schema version and a per-install secret. A forged or copied item without the
 * exact server token is rejected.</p>
 *
 * <p>Pure Java so the rejection behaviour is unit-testable without a server.</p>
 */
public final class WandToken {

    public static final int TOKEN_VERSION = 1;

    private WandToken() {
    }

    /**
     * Mints a token for an owner. Format: {@code v<version>:<hex-mac>}.
     *
     * @param secret  per-install server secret (never stored on the item)
     * @param ownerId canonical owner identity (UUID string)
     * @param version token schema version
     */
    public static String issue(String secret, String ownerId, int version) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Секрет сервера для метки жезла не задан.");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Не задан владелец жезла.");
        }
        String material = version + "|" + ownerId;
        return "v" + version + ":" + macHex(secret, material);
    }

    /**
     * Verifies a token against the expected owner and version using a
     * constant-time comparison so a partial forgery cannot be timing-probed.
     */
    public static boolean verify(String secret, String ownerId, int version, String token) {
        if (secret == null || secret.isEmpty() || ownerId == null || ownerId.isBlank()
                || token == null || token.isBlank()) {
            return false;
        }
        String expected;
        try {
            expected = issue(secret, ownerId, version);
        } catch (IllegalArgumentException error) {
            return false;
        }
        return constantTimeEquals(expected, token.trim());
    }

    /** True when the token string parses to the requested version prefix. */
    public static boolean matchesVersion(String token, int version) {
        if (token == null) {
            return false;
        }
        return token.trim().toLowerCase(Locale.ROOT).startsWith("v" + version + ":");
    }

    public static String tokenVersionPrefix(int version) {
        return "v" + version + ":";
    }

    private static String macHex(String secret, String material) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            throw new IllegalStateException("Не удалось вычислить метку жезла: " + error.getMessage(), error);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
