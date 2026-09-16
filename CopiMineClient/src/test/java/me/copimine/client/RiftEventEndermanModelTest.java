package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RiftEventEndermanModelTest {
    @Test
    void ordinaryAndEliteUseSeparateCustomGeometryVariants() {
        RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(false).createModel(), false);
        RiftEventEndermanModel elite = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(true).createModel(), true);

        assertNotNull(ordinary.getPart().getChild("rift_core"));
        assertNotNull(ordinary.getPart().getChild("rift_shell"));
        assertNotNull(elite.getPart().getChild("variant_crest"));
        assertNotEquals(ordinary.isElite(), elite.isElite());
    }
}
