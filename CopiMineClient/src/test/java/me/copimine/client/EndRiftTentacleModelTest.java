package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.math.Vec3d;

class EndRiftTentacleModelTest {
    @Test
    void exposesTheArtistBriefSkeletonAndEveryAnimation() {
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("grab_socket"));
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("seg_04"));
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("seg_05"));
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("tip_claw_4"));
        for (String animation : new String[]{
                "EMERGING", "READY", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD",
                "THROW", "MISS_RECOVERY", "HIT_RECOVERY", "DYING", "DEAD_RESPAWN",
                "RETRACT",
                "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL", "RECOVERY"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(animation), animation);
            assertTrue(EndRiftTentacleModel.pose(animation, 0.5F).isFinite(), animation);
        }
        for (String state : new String[]{"READY", "EMERGING", "MISS_RECOVERY",
                "HIT_RECOVERY", "DYING"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(state), state);
        }
    }

    @Test
    void rendersOnlyTheEventVisualAndKeepsTheSocketInThePoseContract() {
        assertNotNull(EndRiftTentacleRenderer.textureForVisual(
                EndRiftTentacleModel.VISUAL_ID));
        assertEquals(null, EndRiftTentacleRenderer.textureForVisual("VANILLA"));
        assertTrue(EndRiftTentacleRenderer.poseFor(
                EndRiftTentacleModel.VISUAL_ID, "HOLD", 20L).isFinite());
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.indexOf("grab_socket")
                > EndRiftTentacleModel.REQUIRED_BONES.indexOf("tip"));
    }

    @org.junit.jupiter.api.Test
    void animatesFiveSegmentsAndFourClawsIndependently() {
        EndRiftTentaclePose.TentaclePose idle = EndRiftTentacleAnimator.poseFor(
                "READY", 0.37F, 11L);
        EndRiftTentaclePose.TentaclePose telegraph = EndRiftTentacleAnimator.poseFor(
                "TELEGRAPH_GRAB", 0.50F, 11L);
        assertTrue(idle.isFinite());
        assertTrue(telegraph.isFinite());
        assertEquals(4.75F, idle.socketY(), 0.02F);
        assertTrue(telegraph.tip_claw_1().roll() != telegraph.tip_claw_2().roll());
        assertTrue(telegraph.tip_claw_3().yaw() != telegraph.tip_claw_4().yaw());
        assertTrue(telegraph.seg_05().pitch() != telegraph.base().pitch());
    }

    @org.junit.jupiter.api.Test
    void usesTheArtistBriefMarkerOrderingAndStableHoldSocket() {
        assertTrue(EndRiftTentacleAnimator.durationTicks("GRAB_SUCCESS")
                * 0.55F <= 8.0F);
        assertTrue(EndRiftTentacleAnimator.durationTicks("GRAB_SUCCESS")
                * 0.65F >= 8.0F);
        assertEquals(2.25F,
                EndRiftTentacleAnimator.poseFor("HOLD", 0.10F, 22L).socketY(), 0.02F);
        assertEquals(2.25F,
                EndRiftTentacleAnimator.poseFor("HOLD", 0.90F, 22L).socketY(), 0.02F);
        assertTrue(EndRiftTentacleAnimator.poseFor("DYING", 1.0F, 22L).root()
                .translationY() < 0.0F);
        EndRiftTentaclePose.TentaclePose grabStart = EndRiftTentacleAnimator.poseFor(
                "GRAB_SUCCESS", 0.0F, 22L);
        EndRiftTentaclePose.TentaclePose grabEnd = EndRiftTentacleAnimator.poseFor(
                "GRAB_SUCCESS", 1.0F, 22L);
        assertEquals(4.75F, grabStart.socketY(), 0.02F);
        assertEquals(2.25F, grabEnd.socketY(), 0.02F);
        assertTrue(grabStart.socketY() > grabEnd.socketY());
    }

    @org.junit.jupiter.api.Test
    void targetFacingUsesTheAuthoredPlusZForwardAxis() {
        assertEquals(0.0F, EndRiftTentacleRenderer.targetYaw(
                new Vec3d(0.0D, 0.0D, 0.0D), new Vec3d(0.0D, 0.0D, 1.0D)), 0.001F);
        assertEquals((float) (Math.PI / 2.0D), EndRiftTentacleRenderer.targetYaw(
                new Vec3d(0.0D, 0.0D, 0.0D), new Vec3d(1.0D, 0.0D, 0.0D)), 0.001F);
    }

    @org.junit.jupiter.api.Test
    void artistAliasesResolveToCanonicalRuntimeAnimations() {
        for (String alias : new String[]{"IDLE", "EMERGE", "GRAB_MISS", "HURT", "DEATH"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(alias), alias);
            assertTrue(EndRiftTentacleModel.pose(alias, 0.5F).isFinite(), alias);
        }
    }
}
