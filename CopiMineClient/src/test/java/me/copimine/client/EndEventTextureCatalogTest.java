package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void serverBossVisualAliasResolvesToTheSuppliedBossSkin() {
        assertEquals(
                "copimineclient:textures/entity/end_rift_user_boss.png",
                EndEventTextureCatalog.textureForVisual("END_RIFT_GUARDIAN").toString());
    }
}
