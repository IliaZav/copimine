package me.copimine.client;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleModelTest {
    private static final float EPSILON = 0.0005F;

    @Test
    void exposesTheSixArtistBonesAndAllRuntimeAnimationAliases() {
        assertTrue(EndRiftTentacleModel.isServerCustomModelData(830017));
        assertTrue(!EndRiftTentacleModel.isServerCustomModelData(830018));
        assertEquals(EndRiftTentacleRig.REQUIRED_BONES, EndRiftTentacleModel.REQUIRED_BONES);
        assertEquals(java.util.List.of("1layer", "1layer2", "2layer", "2layer2", "3layer", "3layer2"),
                EndRiftTentacleModel.REQUIRED_BONES);
        for (String animation : new String[]{
                "EMERGING", "READY", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD",
                "THROW", "MISS_RECOVERY", "HIT_RECOVERY", "DYING", "DEAD_RESPAWN",
                "RETRACT", "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL", "RECOVERY"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(animation), animation);
            assertTrue(EndRiftTentacleModel.pose(animation, 0.5F).isFinite(), animation);
        }
    }

    @Test
    void usesTheAuthoredTelegraphAndGrabMotionInSourceModelUnits() {
        EndRiftTentaclePose.TentaclePose telegraph = EndRiftTentacleAnimator.poseFor(
                "TELEGRAPH_GRAB", 1.0F, 11L);
        assertEquals(Math.toRadians(-45.0), telegraph.base().yaw(), EPSILON);
        assertEquals(Math.toRadians(-42.5), telegraph.seg_02().pitch(), EPSILON);
        assertEquals(Math.toRadians(-47.5), telegraph.seg_02().yaw(), EPSILON);
        assertEquals(0.375F, telegraph.seg_02().translationX(), EPSILON);
        assertEquals(-0.225F, telegraph.seg_02().translationZ(), EPSILON);

        EndRiftTentaclePose.TentaclePose sourceThrow = EndRiftTentacleAnimator.poseFor(
                "THROW", 1.0F, 11L);
        assertEquals(Math.toRadians(-17.2215), sourceThrow.seg_04().pitch(), EPSILON);
        assertEquals(Math.toRadians(-20.6887), sourceThrow.seg_04().yaw(), EPSILON);
        assertEquals(Math.toRadians(15.4524), sourceThrow.seg_04().roll(), EPSILON);
    }

    @Test
    void mapsEveryRuntimeCueToTheSuppliedAnimationAndUsesItsDuration() {
        assertEquals(80, EndRiftTentacleAnimator.durationTicks("READY"));
        assertEquals(25, EndRiftTentacleAnimator.durationTicks("TELEGRAPH_GRAB"));
        assertEquals(17, EndRiftTentacleAnimator.durationTicks("GRAB_SUCCESS"));
        assertEquals(18, EndRiftTentacleAnimator.durationTicks("THROW"));
        assertEquals(25, EndRiftTentacleAnimator.durationTicks("HOLD"));
        assertTrue(EndRiftTentacleAnimator.loops("READY"));
        assertTrue(EndRiftTentacleAnimator.loops("SHIELD_CHANNEL"));
        assertTrue(!EndRiftTentacleAnimator.loops("HOLD"));
        for (String alias : new String[]{"IDLE", "EMERGE", "GRAB_MISS", "HURT", "DEATH"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(alias), alias);
            assertTrue(EndRiftTentacleModel.pose(alias, 0.5F).isFinite(), alias);
        }
        assertEquals(0.0F, EndRiftTentacleAnimator.normalizedProgress("READY", 80L), EPSILON);
        assertEquals(1.0F, EndRiftTentacleAnimator.normalizedProgress("HOLD", 25L), EPSILON);
    }

    @Test
    void rendersOnlyTheEventVisualAndKeepsTheGrabSocketApiStable() {
        assertNotNull(EndRiftTentacleRenderer.textureForVisual(EndRiftTentacleModel.VISUAL_ID));
        assertEquals(null, EndRiftTentacleRenderer.textureForVisual("VANILLA"));
        assertTrue(EndRiftTentacleRenderer.poseFor(
                EndRiftTentacleModel.VISUAL_ID, "HOLD", 20L).isFinite());
        assertTrue(EndRiftTentacleModel.pose("HOLD", 0.7F).socketY() > 0.0F);
    }

    @Test
    void targetFacingUsesTheAuthoredPlusZForwardAxis() {
        assertEquals(0.0F, EndRiftTentacleRenderer.targetYaw(
                new Vec3d(0.0D, 0.0D, 0.0D), new Vec3d(0.0D, 0.0D, 1.0D)), EPSILON);
        assertEquals((float) (Math.PI / 2.0D), EndRiftTentacleRenderer.targetYaw(
                new Vec3d(0.0D, 0.0D, 0.0D), new Vec3d(1.0D, 0.0D, 0.0D)), EPSILON);
    }

    @Test
    void keepsVanillaCarrierVisibleUntilTheBridgeBindingArrives() {
        assertTrue(!EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(false, true));
        assertTrue(EndRiftTentacleCarrierPolicy.suppressVanillaCarrier(true, true));
    }

    @Test
    void onlyTheHurtRecoveryAnimationUsesTheRedDamageFlash() {
        assertTrue(EndRiftTentacleRenderer.showsHurtFlash("HIT_RECOVERY"));
        for (String state : new String[]{"READY", "DAMAGED", "CRITICAL", "DEAD", "DYING"}) {
            assertFalse(EndRiftTentacleRenderer.showsHurtFlash(state), state);
        }
    }
}
