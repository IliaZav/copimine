package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void everySpiderRoleConstructsThroughTheCheckedUvPath() {
        for (RiftSpiderModel.Variant variant : RiftSpiderModel.Variant.values()) {
            RiftSpiderModel model = new RiftSpiderModel(
                    RiftSpiderModel.getTexturedModelData(variant).createModel(), variant);
            assertNotNull(model.getPart());
            assertNotNull(model.getPart().getChild("body"));
        }
    }
}
