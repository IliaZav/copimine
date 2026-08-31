package me.copimine.client;

import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.mob.EndermanEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftGuardianModelTest {
    private static final String[] APPROVED_ANIMATIONS = {
            "IDLE_BREATH",
            "DAMAGED_FLINCH",
            "TELEPORT_RIP",
            "CAST_CHARGE",
            "CAST_RELEASE",
            "CAST_IMPACT",
            "PHASE_SHIFT",
            "FINAL_AWAKENING",
            "DEFEAT_COLLAPSE"
    };

    @Test
    void constructsAllRequiredNamedParts() {
        RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

        ModelPart root = model.getPart();
        ModelPart body = root.getChild("body");
        ModelPart leftArm = root.getChild("left_arm");
        ModelPart rightArm = root.getChild("right_arm");

        assertNotNull(body.getChild("jaw"));
        assertNotNull(body.getChild("core_eye"));
        assertNotNull(body.getChild("crown_left"));
        assertNotNull(body.getChild("crown_right"));
        assertNotNull(body.getChild("catastrophe_spine"));
        assertNotNull(leftArm.getChild("left_talon"));
        assertNotNull(rightArm.getChild("right_talon"));
    }

    @Test
    void approvedAnimationsProduceDeterministicDistinctAndBoundedPoses() {
        RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());
        PoseSnapshot idle = pose(model, Phase.CATASTROPHE, "IDLE", 18.0F);

        for (String animationId : APPROVED_ANIMATIONS) {
            PoseSnapshot first = pose(model, Phase.CATASTROPHE, animationId, 18.0F);
            PoseSnapshot second = pose(model, Phase.CATASTROPHE, animationId, 18.0F);

            assertEquals(first, second, "pose must be deterministic for " + animationId);
            assertNotSame(idle, first);
            assertTrue(first.differsFrom(idle), "animation must produce a distinct pose for " + animationId);
            assertTrue(first.isBounded(), "pose must stay bounded for " + animationId + ": " + first);
        }
    }

    @Test
    void onlyCatastropheRaisesFinalSilhouetteScale() {
        RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

        for (Phase phase : Phase.values()) {
            pose(model, phase, "IDLE", 24.0F);
            ModelPart root = model.getPart();
            boolean raised = root.xScale > 1.01F || root.yScale > 1.01F || root.zScale > 1.01F;
            assertEquals(phase == Phase.CATASTROPHE, raised, "unexpected silhouette scale for " + phase);
            assertTrue(root.xScale <= 1.30F && root.yScale <= 1.30F && root.zScale <= 1.30F,
                    "silhouette scale escaped safe bounds for " + phase);
        }
    }

    @Test
    void finalAdornmentPartsStayFoldedUntilFinalAwakening() {
        RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

        poseParts(model, Phase.HUNTER, "IDLE_BREATH");
        assertFinalPartsFolded(model);

        poseParts(model, Phase.AWAKENING, "FINAL_AWAKENING");
        assertFinalPartsRevealed(model);

        poseParts(model, Phase.CATASTROPHE, "IDLE");
        assertFinalPartsRevealed(model);
    }

    private static PoseSnapshot pose(RiftGuardianModel model, Phase phase, String animationId, float animationProgress) {
        model.setPhase(phase, 15_000L);
        model.setAnimation(animationId);
        model.setAngles((EndermanEntity) null, 0.7F, 0.85F, animationProgress, 12.0F, -8.0F);

        ModelPart root = model.getPart();
        ModelPart body = root.getChild("body");
        ModelPart leftArm = root.getChild("left_arm");
        ModelPart rightArm = root.getChild("right_arm");
        ModelPart jaw = body.getChild("jaw");
        ModelPart coreEye = body.getChild("core_eye");
        ModelPart crownLeft = body.getChild("crown_left");
        ModelPart crownRight = body.getChild("crown_right");
        ModelPart catastropheSpine = body.getChild("catastrophe_spine");
        ModelPart leftTalon = leftArm.getChild("left_talon");
        ModelPart rightTalon = rightArm.getChild("right_talon");

        return new PoseSnapshot(
                root.xScale, root.yScale, root.zScale, root.roll,
                body.pitch, body.yaw, body.roll,
                jaw.pitch, jaw.pivotY,
                coreEye.zScale, coreEye.yScale,
                crownLeft.pitch, crownLeft.yaw, crownRight.pitch, crownRight.yaw,
                leftArm.pitch, leftArm.roll, rightArm.pitch, rightArm.roll,
                leftTalon.pitch, rightTalon.pitch,
                catastropheSpine.pitch, catastropheSpine.yScale
        );
    }

    private static void poseParts(RiftGuardianModel model, Phase phase, String animationId) {
        model.setPhase(phase, 0L);
        model.setAnimation(animationId);
        model.setAngles((EndermanEntity) null, 0.0F, 0.0F, 18.0F, 0.0F, 0.0F);
    }

    private static void assertFinalPartsFolded(RiftGuardianModel model) {
        ModelPart body = model.getPart().getChild("body");
        ModelPart leftArm = model.getPart().getChild("left_arm");
        ModelPart rightArm = model.getPart().getChild("right_arm");

        assertFolded(body.getChild("crown_left"));
        assertFolded(body.getChild("crown_right"));
        assertFolded(leftArm.getChild("left_talon"));
        assertFolded(rightArm.getChild("right_talon"));
        assertFolded(body.getChild("catastrophe_spine"));
    }

    private static void assertFinalPartsRevealed(RiftGuardianModel model) {
        ModelPart body = model.getPart().getChild("body");
        ModelPart leftArm = model.getPart().getChild("left_arm");
        ModelPart rightArm = model.getPart().getChild("right_arm");

        assertRevealed(body.getChild("crown_left"));
        assertRevealed(body.getChild("crown_right"));
        assertRevealed(leftArm.getChild("left_talon"));
        assertRevealed(rightArm.getChild("right_talon"));
        assertRevealed(body.getChild("catastrophe_spine"));
    }

    private static void assertFolded(ModelPart part) {
        assertTrue(part.xScale <= 0.01F && part.yScale <= 0.01F && part.zScale <= 0.01F,
                "part should be folded into the base silhouette");
    }

    private static void assertRevealed(ModelPart part) {
        assertTrue(part.xScale >= 0.5F && part.yScale >= 0.5F && part.zScale >= 0.5F,
                "part should be revealed in the final state");
    }

    private record PoseSnapshot(
            float rootXScale,
            float rootYScale,
            float rootZScale,
            float rootRoll,
            float bodyPitch,
            float bodyYaw,
            float bodyRoll,
            float jawPitch,
            float jawPivotY,
            float coreEyeZScale,
            float coreEyeYScale,
            float crownLeftPitch,
            float crownLeftYaw,
            float crownRightPitch,
            float crownRightYaw,
            float leftArmPitch,
            float leftArmRoll,
            float rightArmPitch,
            float rightArmRoll,
            float leftTalonPitch,
            float rightTalonPitch,
            float catastropheSpinePitch,
            float catastropheSpineYScale) {

        boolean differsFrom(PoseSnapshot other) {
            return Math.abs(rootXScale - other.rootXScale) > 0.001F
                    || Math.abs(rootYScale - other.rootYScale) > 0.001F
                    || Math.abs(rootZScale - other.rootZScale) > 0.001F
                    || Math.abs(rootRoll - other.rootRoll) > 0.001F
                    || Math.abs(bodyPitch - other.bodyPitch) > 0.001F
                    || Math.abs(bodyYaw - other.bodyYaw) > 0.001F
                    || Math.abs(bodyRoll - other.bodyRoll) > 0.001F
                    || Math.abs(jawPitch - other.jawPitch) > 0.001F
                    || Math.abs(jawPivotY - other.jawPivotY) > 0.001F
                    || Math.abs(coreEyeZScale - other.coreEyeZScale) > 0.001F
                    || Math.abs(coreEyeYScale - other.coreEyeYScale) > 0.001F
                    || Math.abs(crownLeftPitch - other.crownLeftPitch) > 0.001F
                    || Math.abs(crownLeftYaw - other.crownLeftYaw) > 0.001F
                    || Math.abs(crownRightPitch - other.crownRightPitch) > 0.001F
                    || Math.abs(crownRightYaw - other.crownRightYaw) > 0.001F
                    || Math.abs(leftArmPitch - other.leftArmPitch) > 0.001F
                    || Math.abs(leftArmRoll - other.leftArmRoll) > 0.001F
                    || Math.abs(rightArmPitch - other.rightArmPitch) > 0.001F
                    || Math.abs(rightArmRoll - other.rightArmRoll) > 0.001F
                    || Math.abs(leftTalonPitch - other.leftTalonPitch) > 0.001F
                    || Math.abs(rightTalonPitch - other.rightTalonPitch) > 0.001F
                    || Math.abs(catastropheSpinePitch - other.catastropheSpinePitch) > 0.001F
                    || Math.abs(catastropheSpineYScale - other.catastropheSpineYScale) > 0.001F;
        }

        boolean isBounded() {
            return finite(rootXScale) && bounded(rootXScale, 0.8F, 1.30F)
                    && finite(rootYScale) && bounded(rootYScale, 0.8F, 1.30F)
                    && finite(rootZScale) && bounded(rootZScale, 0.8F, 1.30F)
                    && finite(rootRoll) && bounded(rootRoll, -0.75F, 0.75F)
                    && finite(bodyPitch) && bounded(bodyPitch, -1.5F, 1.5F)
                    && finite(bodyYaw) && bounded(bodyYaw, -0.75F, 0.75F)
                    && finite(bodyRoll) && bounded(bodyRoll, -0.75F, 0.75F)
                    && finite(jawPitch) && bounded(jawPitch, -1.3F, 1.3F)
                    && finite(jawPivotY) && bounded(jawPivotY, -48.0F, 32.0F)
                    && finite(coreEyeZScale) && bounded(coreEyeZScale, 0.7F, 1.8F)
                    && finite(coreEyeYScale) && bounded(coreEyeYScale, 0.7F, 1.8F)
                    && finite(crownLeftPitch) && bounded(crownLeftPitch, -1.4F, 1.4F)
                    && finite(crownLeftYaw) && bounded(crownLeftYaw, -1.4F, 1.4F)
                    && finite(crownRightPitch) && bounded(crownRightPitch, -1.4F, 1.4F)
                    && finite(crownRightYaw) && bounded(crownRightYaw, -1.4F, 1.4F)
                    && finite(leftArmPitch) && bounded(leftArmPitch, -1.8F, 1.8F)
                    && finite(leftArmRoll) && bounded(leftArmRoll, -1.2F, 1.2F)
                    && finite(rightArmPitch) && bounded(rightArmPitch, -1.8F, 1.8F)
                    && finite(rightArmRoll) && bounded(rightArmRoll, -1.2F, 1.2F)
                    && finite(leftTalonPitch) && bounded(leftTalonPitch, -1.6F, 1.6F)
                    && finite(rightTalonPitch) && bounded(rightTalonPitch, -1.6F, 1.6F)
                    && finite(catastropheSpinePitch) && bounded(catastropheSpinePitch, -1.4F, 1.4F)
                    && finite(catastropheSpineYScale) && bounded(catastropheSpineYScale, 0.8F, 1.8F);
        }

        private static boolean finite(float value) {
            return Float.isFinite(value);
        }

        private static boolean bounded(float value, float min, float max) {
            return value >= min && value <= max;
        }
    }
}
