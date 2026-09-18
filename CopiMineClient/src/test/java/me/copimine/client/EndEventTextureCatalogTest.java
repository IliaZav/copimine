package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EndEventTextureCatalogTest {
    @Test
    void suppliedMobSkinsAreTheRuntimeBindings() {
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_enderman.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_ENDERMAN_V1").toString());
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_spider.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_SPIDER_V1").toString());
        assertEquals(
                "copimineclient:textures/entity/end_rift_ritual_caster.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_RITUAL_CASTER_V1").toString());
    }

    @Test
    void eliteAndSpecialMobSkinsHaveIndependentRuntimeBindings() {
        assertTexture("END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", "end_rift_wave_guardian_enderman.png");
        assertTexture("END_RIFT_WAVE_GUARDIAN_SKELETON_V1", "end_rift_wave_guardian_skeleton.png");
        assertTexture("END_RIFT_RITUAL_GUARD_ENDERMAN_V1", "end_rift_ritual_guard_enderman.png");
        assertTexture("END_RIFT_RITUAL_GUARD_SKELETON_V1", "end_rift_ritual_guard_skeleton.png");
        assertTexture("END_RIFT_ELITE_SPIDER_V1", "end_rift_elite_spider.png");
        assertTexture("END_RIFT_WAVE_GUARDIAN_SPIDER_V1", "end_rift_wave_guardian_spider.png");
        assertTexture("END_RIFT_RITUAL_GUARD_SPIDER_V1", "end_rift_ritual_guard_spider.png");
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
