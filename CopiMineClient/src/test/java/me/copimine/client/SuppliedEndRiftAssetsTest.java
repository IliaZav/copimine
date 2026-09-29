package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks client resources to the exact files supplied in the End Rift archive. */
class SuppliedEndRiftAssetsTest {
    @Test
    void geometryAndEverySuppliedSkinRemainByteExact() {
        assertSha256(UserEndBossModelData.RESOURCE,
                "301583A2EFEA6C5B597C4FE2CADCED68D7838B80F630D1D16F8AAA8265964783");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_user_boss.png",
                "F298ED322335C5439C19DDDB8014AA0960B83F3FB27D692580A75E051516C45D");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_user_enderman.png",
                "A9A154F232919627451431E3F3874C9E850F23E531EAE2CFE2A4A9CC16EDF447");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_user_spider.png",
                "19C46FF4AA829E7101B25A50A55090CD1D8145C2F83B95D64C13A20F6B5C9ABF");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_user_skeleton.png",
                "4744A2C76285B1FA06F6FA64BFF5573BEA88D63AE7050912B138B830B573EE80");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_elite.png",
                "5923111B4AC459DAEE04AEEEA4930DCAC4B1156ED3D94BBAAA4B89B987FC6BDC");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_elite_skeleton.png",
                "DF29A577E2CC5896507044DB37349216C2401311E458576ECBB2E0FE8CE65514");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_elite_spider.png",
                "40AB699E7DC46D50A26728679539B30CED556269B795B06ED7BF498DD1B4D052");
        assertSha256("/assets/copimineclient/textures/entity/end_rift_tentacle_hd.png",
                "5817936653025968ABDD07003EBA29E4820B09B33EBAC4708E463F3B8B4F6BBC");
    }

    @Test
    void everySuppliedAnimationRemainsByteExact() {
        assertAnimation("dying.json", "DD1AB32FAE51E31346D06F3A9510B615E84A836D6BFE5AD841C817087B2D8B8C");
        assertAnimation("hurt.json", "A491645B15C9B00B9B7DA939181A9C811756AA9F5A64C7CD587C535FD8FB591B");
        assertAnimation("idle.json", "9E87CDB45B394F1676ECC6F619CA1E37A7292E2E670E7B235951DC95625D4C6C");
        assertAnimation("running.json", "5D9F42AA83E50B3D5D4819DF756B323CD7005843516502C76FD67D0868417DF2");
        assertAnimation("swipe.json", "9710B8766BF1B9D1382475F778E3D41105A88E41F768BDF5C2F56AEEAA322EEA");
        assertAnimation("udar_iz_grudi.json", "C53C68D133F42B61C50DF329BD87235E87835B1B4DB63FC80176539B26D87141");
        assertAnimation("udar_po_zemle.animation.json", "85D9AE6BC72BF09454BC1ABCBC9FFB716E7F06E0D8C0CE507C148C7C80E88CE1");
    }

    private static void assertAnimation(String name, String expectedHash) {
        assertSha256("/assets/copimineclient/models/entity/end_rift_guardian/animations/" + name,
                expectedHash);
    }

    private static void assertSha256(String resource, String expectedHash) {
        try (InputStream stream = SuppliedEndRiftAssetsTest.class.getResourceAsStream(resource)) {
            assertEquals(expectedHash, sha256(stream), resource);
        } catch (IOException error) {
            throw new AssertionError("Unable to read supplied End Rift resource " + resource, error);
        }
    }

    private static String sha256(InputStream stream) throws IOException {
        if (stream == null) {
            throw new AssertionError("Missing supplied End Rift resource");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("JVM does not provide SHA-256", error);
        }
        byte[] buffer = new byte[4096];
        for (int count; (count = stream.read(buffer)) >= 0; ) {
            digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02X", value));
        }
        return result.toString();
    }
}
