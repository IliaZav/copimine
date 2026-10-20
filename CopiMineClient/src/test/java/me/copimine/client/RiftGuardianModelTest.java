package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftGuardianModelTest {
    @Test
    void guardianRestPoseIsDirectGeometryNotVanillaEndermanCuboids() {
        RiftGuardianModel model = new RiftGuardianModel(
                RiftGuardianModel.getTexturedModelData().createModel());

        assertEquals(119, model.directGeometryCubeCount());
        assertEquals(714, model.directGeometryFaceCount());
        assertFalse(model.usesVanillaCuboidGuardianMesh());
        assertFalse(model.getPart().getChild("head").hasChild("source_cube_0"),
                "the Enderman compatibility carrier must not run the retired ModelPart geometry importer");
    }

    @Test
    void setAnglesSamplesTheSuppliedPoseForDirectRendering() {
        RiftGuardianModel model = new RiftGuardianModel(
                RiftGuardianModel.getTexturedModelData().createModel());
        model.setAnimation("IDLE_BREATH");
        model.setAnimationElapsedTicks(20.0F);

        model.setAngles(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        ChameleonGuardianGeometry.GuardianPose pose = model.currentPose();
        assertTrue(pose.hasBoneDelta("head"));
        assertTrue(pose.hasBoneDelta("body"));
        assertFalse(pose.hasBoneDelta("right_leg_low"));
    }

    @Test
    void unknownAnimationKeepsTheExactRestPose() {
        RiftGuardianModel model = new RiftGuardianModel(
                RiftGuardianModel.getTexturedModelData().createModel());
        model.setAnimation("UNSUPPORTED_TEST_CLIP");

        model.setAngles(null, 0.0F, 0.0F, 0.0F, 45.0F, 20.0F);

        assertEquals(ChameleonGuardianGeometry.GuardianPose.identity(), model.currentPose());
    }
}
