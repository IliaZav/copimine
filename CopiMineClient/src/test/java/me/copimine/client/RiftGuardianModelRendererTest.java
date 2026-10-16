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
            assertEquals("textures/entity/end_rift_user_boss.png", texture.getPath());
        }
    }

    @Test
    void finalStrikeKeepsTheSuppliedBossTextureFile() {
        RiftGuardianModelRenderer renderer = new RiftGuardianModelRenderer();

        assertEquals(
                Identifier.of("copimineclient", "textures/entity/end_rift_user_boss.png"),
                renderer.textureForState("LAST_SEAL", "FINAL_STRIKE"));
    }

    @Test
    void suppliedArtistClipsAreLoadedForTheirRuntimeActions() {
        assertTrue(UserEndBossAnimationPlayer.hasClip("IDLE_BREATH"));
        assertTrue(UserEndBossAnimationPlayer.hasClip("CHEST_STRIKE"));
        assertTrue(UserEndBossAnimationPlayer.hasClip("GROUND_SLAM"));
        assertTrue(UserEndBossAnimationPlayer.clipLengthSeconds("CHEST_STRIKE") > 4.0F);
        assertTrue(UserEndBossAnimationPlayer.clipLengthSeconds("GROUND_SLAM") > 9.0F);
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
