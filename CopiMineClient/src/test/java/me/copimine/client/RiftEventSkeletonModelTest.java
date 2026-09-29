package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RiftEventSkeletonModelTest {
    @Test
    void segmentedLegsEndAtTheSkeletonGroundPlane() {
        for (RiftEventSkeletonModel.Variant variant : RiftEventSkeletonModel.Variant.values()) {
            ModelPart root = RiftEventSkeletonModel.getTexturedModelData(variant).createModel();
            for (String side : java.util.List.of("left", "right")) {
                ModelPart leg = root.getChild(side + "_leg");
                ModelPart lower = leg.getChild(side + "_lower_leg");
                float bottom = leg.pivotY + lower.pivotY
                        + lower.getRandomCuboid(Random.create()).maxY;
                assertEquals(24.0F, bottom, 0.001F, variant + " " + side + " foot");
            }
        }
    }

    @Test
    void eliteAndSpecialSkeletonsHaveReadableSilhouetteParts() {
        RiftEventSkeletonModel ordinary = new RiftEventSkeletonModel(
                RiftEventSkeletonModel.getTexturedModelData(false).createModel(), false);
        RiftEventSkeletonModel elite = new RiftEventSkeletonModel(
                RiftEventSkeletonModel.getTexturedModelData(true).createModel(), true);
        ModelPart ordinaryRoot = ordinary.getPart();
        ModelPart eliteRoot = elite.getPart();

        assertNotNull(ordinaryRoot.getChild("head").getChild("guard_crest"));
        assertNotNull(ordinaryRoot.getChild("body").getChild("guard_chest_seal"));
        assertNotNull(ordinaryRoot.getChild("body").getChild("guardian_spine"));
        assertNotNull(eliteRoot.getChild("body").getChild("elite_mantle"));
        assertNotNull(eliteRoot.getChild("head").getChild("elite_horn_crown"));
    }

    @Test
    void animatedSkeletonAccentsHaveRenderableCuboids() {
        ModelPart elite = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.ELITE).createModel();
        ModelPart waveGuardian = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.WAVE_GUARDIAN).createModel();
        ModelPart ritualGuard = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.RITUAL_GUARD).createModel();

        assertFalse(elite.getChild("body").getChild("chest_rift").isEmpty(), "chest rift");
        assertFalse(elite.getChild("head").getChild("elite_horn_left").isEmpty(), "left horn");
        assertFalse(elite.getChild("head").getChild("elite_horn_right").isEmpty(), "right horn");
        assertFalse(elite.getChild("left_arm").getChild("elite_shoulder_left").isEmpty(), "left shoulder");
        assertFalse(elite.getChild("right_arm").getChild("elite_shoulder_right").isEmpty(), "right shoulder");
        assertFalse(elite.getChild("body").getChild("elite_mantle").isEmpty(), "elite mantle");
        assertFalse(elite.getChild("head").getChild("elite_horn_crown").isEmpty(), "elite crown");
        assertFalse(waveGuardian.getChild("head").getChild("guard_crest").isEmpty(), "guardian crest");
        assertFalse(waveGuardian.getChild("body").getChild("guard_chest_seal").isEmpty(), "guardian chest seal");
        assertFalse(waveGuardian.getChild("body").getChild("guardian_spine").isEmpty(), "guardian spine");
        assertFalse(ritualGuard.getChild("head").getChild("guard_crest").isEmpty(), "ritual crest");
        assertFalse(ritualGuard.getChild("body").getChild("guard_chest_seal").isEmpty(), "ritual seal");
    }

    @Test
    void roleAccentsAreAttachedToPartsVisitedByTheBipedRenderer() {
        ModelPart guardian = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.WAVE_GUARDIAN).createModel();
        ModelPart ritualGuard = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.RITUAL_GUARD).createModel();
        ModelPart elite = RiftEventSkeletonModel.getTexturedModelData(
                RiftEventSkeletonModel.Variant.ELITE).createModel();

        assertFalse(guardian.getChild("head").getChild("guard_crest").isEmpty(),
                "guardian crest is part of the rendered head");
        assertFalse(guardian.getChild("body").getChild("guard_chest_seal").isEmpty(),
                "guardian seal is part of the rendered body");
        assertFalse(guardian.getChild("body").getChild("guardian_spine").isEmpty(),
                "guardian spine is part of the rendered body");
        assertFalse(ritualGuard.getChild("head").getChild("guard_crest").isEmpty(),
                "ritual crest is part of the rendered head");
        assertFalse(ritualGuard.getChild("body").getChild("guard_chest_seal").isEmpty(),
                "ritual seal is part of the rendered body");
        assertFalse(elite.getChild("body").getChild("elite_mantle").isEmpty(),
                "elite mantle is part of the rendered body");
        assertFalse(elite.getChild("head").getChild("elite_horn_crown").isEmpty(),
                "elite crown is part of the rendered head");
    }

    @Test
    void rendererRoutesSpecialSkeletonVisualIdsToDedicatedModels() {
        RiftEventSkeletonModelRenderer renderer = new RiftEventSkeletonModelRenderer();

        var ordinary = renderer.modelFor("END_RIFT_SKELETON_V1");
        var elite = renderer.modelFor("END_RIFT_ELITE_SKELETON_V1");
        var waveGuardian = renderer.modelFor("END_RIFT_WAVE_GUARDIAN_SKELETON_V1");
        var ritualGuard = renderer.modelFor("END_RIFT_RITUAL_GUARD_SKELETON_V1");
        assertNotNull(ordinary);
        assertNotNull(elite);
        assertNotNull(waveGuardian);
        assertNotNull(ritualGuard);
        assertNotSame(ordinary, elite);
        assertNotSame(elite, waveGuardian);
        assertNotSame(waveGuardian, ritualGuard);
        assertNotSame(ritualGuard, ordinary);
        assertSame(ordinary, renderer.modelFor("end_rift_skeleton_v1"),
                "normalized lookup returns the ordinary custom event geometry");
        assertNull(renderer.modelFor("END_RIFT_UNKNOWN_SKELETON_V1"));
    }

    @Test
    void everySkeletonRoleConstructsThroughTheCheckedUvPath() {
        for (RiftEventSkeletonModel.Variant variant : RiftEventSkeletonModel.Variant.values()) {
            RiftEventSkeletonModel model = new RiftEventSkeletonModel(
                    RiftEventSkeletonModel.getTexturedModelData(variant).createModel(), variant);
            assertNotNull(model.getPart());
            assertNotNull(model.getPart().getChild("body"));
        }
    }
}
