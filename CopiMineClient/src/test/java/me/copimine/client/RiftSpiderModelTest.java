package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftSpiderModelTest {
    @Test
    void constructsVanillaSkeletonAndAdaptedEventParts() {
        RiftSpiderModel model = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData().createModel());
        ModelPart root = model.getPart();

        assertNotNull(root.getChild("head"));
        assertNotNull(root.getChild("right_hind_leg"));
        assertNotNull(root.getChild("left_front_leg"));
        assertNotNull(root.getChild("rift_core"));
        assertNotNull(root.getChild("rift_shell"));
        assertNotNull(root.getChild("rift_spines"));
        assertTrue(root.traverse().filter(part -> !part.isEmpty()).count() >= 12);
    }

    @Test
    void usesTheSuppliedSpiderTextureAtlasDimensions() {
        assertEquals(64, RiftSpiderModel.TEXTURE_WIDTH);
        assertEquals(32, RiftSpiderModel.TEXTURE_HEIGHT);
    }

    @Test
    void ordinarySpiderShowsTheSuppliedSkinOnItsBaseBodyWithoutDuplicateShells() {
        RiftSpiderModel ordinary = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ORDINARY).createModel(),
                RiftSpiderModel.Variant.ORDINARY);
        RiftSpiderModel elite = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.ELITE).createModel(),
                RiftSpiderModel.Variant.ELITE);

        assertFalse(ordinary.getPart().getChild("rift_core").visible);
        assertFalse(ordinary.getPart().getChild("rift_shell").visible);
        assertFalse(ordinary.getPart().getChild("rift_spines").visible);
        assertTrue(elite.getPart().getChild("elite_carapace").visible);

        RiftSpiderModel guardian = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.WAVE_GUARDIAN).createModel(),
                RiftSpiderModel.Variant.WAVE_GUARDIAN);
        RiftSpiderModel ritualGuard = new RiftSpiderModel(
                RiftSpiderModel.getTexturedModelData(RiftSpiderModel.Variant.RITUAL_GUARD).createModel(),
                RiftSpiderModel.Variant.RITUAL_GUARD);
        assertTrue(guardian.getPart().getChild("guardian_spine").visible);
        assertTrue(ritualGuard.getPart().getChild("guard_seal").visible);
        assertTrue(ritualGuard.getPart().getChild("ritual_focus").visible);
    }

    @Test
    void everySpiderRoleConstructsThroughTheCheckedUvPath() {
        for (RiftSpiderModel.Variant variant : RiftSpiderModel.Variant.values()) {
            RiftSpiderModel model = new RiftSpiderModel(
                    RiftSpiderModel.getTexturedModelData(variant).createModel(), variant);
            assertNotNull(model.getPart());
            assertNotNull(model.getPart().getChild("body"));
        }
    }
}
