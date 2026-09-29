package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EndEventTextureCatalogTest {
    @Test
    void suppliedEndermanAndSpiderSkinsAreTheRuntimeBindings() {
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_enderman.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_ENDERMAN_V1").toString());
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_spider.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_SPIDER_V1").toString());
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_enderman.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_RITUAL_CASTER_V1").toString());
    }

    @Test
    void everyMobRoleUsesTheMatchingAuthoredRuntimeSkin() {
        for (String visual : new String[] {"END_RIFT_ENDERMAN_V1", "END_RIFT_ELITE_V1",
                "END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", "END_RIFT_RITUAL_GUARD_ENDERMAN_V1",
                "END_RIFT_RITUAL_CASTER_V1"}) {
            assertTexture(visual, "end_rift_user_enderman.png");
        }
        for (String visual : new String[] {"END_RIFT_SPIDER_V1", "END_RIFT_ELITE_SPIDER_V1",
                "END_RIFT_WAVE_GUARDIAN_SPIDER_V1", "END_RIFT_RITUAL_GUARD_SPIDER_V1"}) {
            assertTexture(visual, "end_rift_user_spider.png");
        }
        // Every skeleton role uses the preserved user supplied atlas; role
        // geometry and equipment carry the visual distinction.
        for (String visual : new String[] {"END_RIFT_SKELETON_V1", "END_RIFT_ELITE_SKELETON_V1",
                "END_RIFT_WAVE_GUARDIAN_SKELETON_V1", "END_RIFT_RITUAL_GUARD_SKELETON_V1"}) {
            assertTexture(visual, "end_rift_user_skeleton.png");
        }
        assertTexture("END_RIFT_GUARDIAN_SHIELD_V1", "end_rift_guardian_shield_hd.png");
    }

    @Test
    void serverBossVisualAliasResolvesToTheSuppliedBossSkin() {
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_boss.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_GUARDIAN").toString());
    }

    private static void assertTexture(String visualId, String fileName) {
        var texture = EndEventTextureCatalog.textureForVisual(visualId);
        assertNotNull(texture, visualId);
        assertEquals("copimineclient:textures/entity/" + fileName, texture.toString());
    }
}
