package me.copimine.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (String visual : new String[] {"END_RIFT_ENDERMAN_V1",
                "END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", "END_RIFT_RITUAL_GUARD_ENDERMAN_V1",
                "END_RIFT_RITUAL_CASTER_V1"}) {
            assertTexture(visual, "end_rift_user_enderman.png");
        }
        for (String visual : new String[] {"END_RIFT_SPIDER_V1",
                "END_RIFT_WAVE_GUARDIAN_SPIDER_V1", "END_RIFT_RITUAL_GUARD_SPIDER_V1"}) {
            assertTexture(visual, "end_rift_user_spider.png");
        }
        for (String visual : new String[] {"END_RIFT_SKELETON_V1",
                "END_RIFT_WAVE_GUARDIAN_SKELETON_V1", "END_RIFT_RITUAL_GUARD_SKELETON_V1"}) {
            assertTexture(visual, "end_rift_user_skeleton.png");
        }
        assertTexture("END_RIFT_GUARDIAN_SHIELD_V1", "end_rift_guardian_shield_hd.png");
    }

    @Test
    void eliteMobsUseTheirDedicatedArchiveAtlases() {
        assertTexture("END_RIFT_ELITE_V1", "end_rift_elite.png");
        assertTexture("END_RIFT_ELITE_SKELETON_V1", "end_rift_elite_skeleton.png");
        assertTexture("END_RIFT_ELITE_SPIDER_V1", "end_rift_elite_spider.png");
    }

    @Test
    void serverBossVisualAliasResolvesToTheSuppliedBossSkin() {
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_boss.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_GUARDIAN").toString());
    }

    @Test
    void namedWave7ActorsResolveTheirServerVisualIdsToPackagedSkins() {
        assertWave7Texture("END_RIFT_WAVE7_WARDEN_V1", "end_rift_wave7_warden.png");
        assertWave7Texture("END_RIFT_WAVE7_MARKSMAN_V1", "end_rift_wave7_marksman.png");
        assertWave7Texture("END_RIFT_WAVE7_ARCHMAGE_V1", "end_rift_wave7_archmage.png");
    }

    @Test
    void namedWave7WardenAndArchmageTextureRenderersAreRegistered() throws Exception {
        try (var stream = EndEventTextureCatalogTest.class.getResourceAsStream("/copimineclient.mixins.json")) {
            assertNotNull(stream, "client mixin configuration is packaged");
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var clientMixins = config.getAsJsonArray("client");
            assertTrue(clientMixins.toString().contains("WardenEntityRendererMixin"));
            assertTrue(clientMixins.toString().contains("EvokerEntityRendererMixin"));
        }
    }

    private static void assertWave7Texture(String visualId, String fileName) {
        assertTexture(visualId, fileName);
        assertNotNull(EndEventTextureCatalogTest.class.getResource(
                "/assets/copimineclient/textures/entity/" + fileName),
                visualId + " points to a packaged texture");
    }

    private static void assertTexture(String visualId, String fileName) {
        var texture = EndEventTextureCatalog.textureForVisual(visualId);
        assertNotNull(texture, visualId);
        assertEquals("copimineclient:textures/entity/" + fileName, texture.toString());
    }
}
