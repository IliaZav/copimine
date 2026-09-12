package me.copimine.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftGuardianModelRendererTest {
    @Test
    void everyBossPhaseResolvesToAConcreteTextureFile() {
        RiftGuardianModelRenderer renderer = new RiftGuardianModelRenderer();

        for (RiftGuardianModelRenderer.Phase phase : RiftGuardianModelRenderer.Phase.values()) {
            Identifier texture = renderer.textureForPhase(phase.name());
            assertEquals("copimineclient", texture.getNamespace());
            assertTrue(texture.getPath().startsWith("textures/entity/rift_guardian_"));
            assertTrue(texture.getPath().endsWith(".png"), "missing .png for " + phase);
        }
    }

    @Test
    void finalStrikeUsesItsDedicatedTextureFile() {
        RiftGuardianModelRenderer renderer = new RiftGuardianModelRenderer();

        assertEquals(
                Identifier.of("copimineclient", "textures/entity/rift_guardian_final_strike.png"),
                renderer.textureForState("LAST_SEAL", "FINAL_STRIKE"));
    }

    @Test
    void originalArtistAnimationNamesAreAcceptedByTheRenderer() {
        assertEquals("RUN", RiftGuardianModelRenderer.normalizeAnimationId("Running2"));
        assertEquals("MELEE_SWIPE", RiftGuardianModelRenderer.normalizeAnimationId("Swipe2"));
        assertEquals("HURT", RiftGuardianModelRenderer.normalizeAnimationId("Hurt2"));
        assertEquals("DYING", RiftGuardianModelRenderer.normalizeAnimationId("Dying2"));
        assertEquals("CHEST_STRIKE", RiftGuardianModelRenderer.normalizeAnimationId("udar_iz_grudi"));
        assertEquals("GROUND_SLAM", RiftGuardianModelRenderer.normalizeAnimationId("udar_po_zemle2"));
    }
}
