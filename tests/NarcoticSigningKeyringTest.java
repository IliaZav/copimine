package me.copimine.narcotics.item;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class NarcoticSigningKeyringTest {
    public static void main(String[] args) throws Exception {
        acceptsLegacySignaturesButSignsWithPrimaryOnly();
        ignoresDuplicatesAndEnforcesLegacyKeyLimit();
        rejectsInvalidKeys();
        System.out.println("NarcoticSigningKeyringTest OK");
    }

    private static void acceptsLegacySignaturesButSignsWithPrimaryOnly() throws Exception {
        byte[] primary = key(1);
        byte[] legacy = key(2);
        String payload = "instance-17|narcotic-a|PAPER|3|signature-v2";
        NarcoticSigningKeyring keyring = NarcoticSigningKeyring.fromBase64(
                encode(primary), List.of(encode(legacy)));

        String legacySignature = hmac(legacy, payload);
        String primarySignature = hmac(primary, payload);
        check(keyring.verify(payload, legacySignature), "legacy signature was rejected");
        check(keyring.verify(payload, primarySignature), "primary signature was rejected");
        check(primarySignature.equals(keyring.sign(payload)), "new signature did not use the primary key");
        check(!legacySignature.equals(keyring.sign(payload)), "new signature used a legacy key");
        check(!keyring.verify(payload, "forged-signature"), "forged signature was accepted");
        check(!keyring.verify(payload + "|modified", legacySignature), "signature for modified payload was accepted");
    }

    private static void ignoresDuplicatesAndEnforcesLegacyKeyLimit() {
        String primary = encode(key(10));
        String legacyOne = encode(key(11));
        NarcoticSigningKeyring duplicateKeys = NarcoticSigningKeyring.fromBase64(
                primary, List.of(primary, legacyOne, legacyOne));
        check(duplicateKeys.verificationKeyCount() == 2, "duplicate keys were retained");

        List<String> maximumLegacyKeys = new ArrayList<>();
        for (int index = 20; index < 28; index++) {
            maximumLegacyKeys.add(encode(key(index)));
        }
        NarcoticSigningKeyring maximumKeyring = NarcoticSigningKeyring.fromBase64(primary, maximumLegacyKeys);
        check(maximumKeyring.verificationKeyCount() == 9, "eight distinct legacy keys were not accepted");

        maximumLegacyKeys.add(encode(key(28)));
        expectInvalid(() -> NarcoticSigningKeyring.fromBase64(primary, maximumLegacyKeys),
                "more than eight legacy keys were accepted");
    }

    private static void rejectsInvalidKeys() {
        expectInvalid(() -> NarcoticSigningKeyring.fromBase64("not-base64", List.of()),
                "invalid primary key was accepted");
        expectInvalid(() -> NarcoticSigningKeyring.fromBase64(encode(new byte[31]), List.of()),
                "short primary key was accepted");
        expectInvalid(() -> NarcoticSigningKeyring.fromBase64(encode(key(40)), List.of("not-base64")),
                "invalid legacy key was accepted");
        expectInvalid(() -> NarcoticSigningKeyring.fromBase64(encode(key(41)), List.of(encode(new byte[31]))),
                "short legacy key was accepted");
    }

    private static byte[] key(int value) {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (value + index);
        }
        return result;
    }

    private static String encode(byte[] key) {
        return Base64.getEncoder().encodeToString(key);
    }

    private static String hmac(byte[] key, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static void expectInvalid(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
