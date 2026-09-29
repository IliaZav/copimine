package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RiftEventEndermanModelTest {
    @Test
    void suppliedOrdinarySkinUsesVanillaEndermanProportionsWithoutBlockyOverlays() {
        ModelPart expected = EndermanEntityModel.getTexturedModelData().createModel();
        RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(RiftEventEndermanModel.Variant.ORDINARY)
                        .createModel(), RiftEventEndermanModel.Variant.ORDINARY);
        ModelPart actual = ordinary.getPart();

        for (String name : java.util.List.of("head", "body", "left_arm", "right_arm",
                "left_leg", "right_leg")) {
            ModelPart source = expected.getChild(name);
            ModelPart imported = actual.getChild(name);
            assertEquals(source.pivotY, imported.pivotY, 0.0001F, name + " pivot");
            var sourceCube = source.getRandomCuboid(Random.create());
            var importedCube = imported.getRandomCuboid(Random.create());
            assertEquals(sourceCube.maxY - sourceCube.minY,
                    importedCube.maxY - importedCube.minY, 0.0001F, name + " cuboid height");
        }
        assertFalse(actual.getChild("rift_core").visible);
        assertFalse(actual.getChild("rift_shell").visible);
        assertFalse(actual.getChild("body").getChild("body_shell").visible);
        assertFalse(actual.getChild("left_arm").getChild("left_forearm").visible);
    }

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

    @Test
    void eachHumanoidRoleHasReadableSilhouetteParts() {
        RiftEventEndermanModel ordinary = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(false).createModel(), false);
        RiftEventEndermanModel elite = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(true).createModel(), true);

        assertNotNull(ordinary.getPart().getChild("body").getChild("body_shell"));
        assertNotNull(ordinary.getPart().getChild("body").getChild("chest_rift"));
        assertNotNull(ordinary.getPart().getChild("left_arm").getChild("left_forearm"));
        assertNotNull(ordinary.getPart().getChild("right_arm").getChild("right_forearm"));
        assertNotNull(ordinary.getPart().getChild("left_leg").getChild("left_shin"));
        assertNotNull(ordinary.getPart().getChild("right_leg").getChild("right_shin"));
        assertNotNull(elite.getPart().getChild("head").getChild("horn_left"));
        assertNotNull(elite.getPart().getChild("head").getChild("horn_right"));
        assertNotNull(elite.getPart().getChild("body").getChild("guardian_mantle"));
    }

    @Test
    void waveGuardianAndRitualGuardHaveDedicatedModelInstances() {
        RiftEventEndermanModelRenderer renderer = new RiftEventEndermanModelRenderer();

        assertNotNull(renderer.modelFor(EndermanRendererSelection.Kind.WAVE_GUARDIAN));
        assertNotNull(renderer.modelFor(EndermanRendererSelection.Kind.RITUAL_GUARD));
        assertNotSame(renderer.modelFor(EndermanRendererSelection.Kind.WAVE_GUARDIAN),
                renderer.modelFor(EndermanRendererSelection.Kind.RITUAL_GUARD));
    }

    @Test
    void everyEndermanRoleConstructsThroughTheCheckedUvPath() {
        for (RiftEventEndermanModel.Variant variant : RiftEventEndermanModel.Variant.values()) {
            RiftEventEndermanModel model = new RiftEventEndermanModel(
                    RiftEventEndermanModel.getTexturedModelData(variant).createModel(), variant);
            assertNotNull(model.getPart());
            assertNotNull(model.getPart().getChild("rift_core"));
        }
    }
}
