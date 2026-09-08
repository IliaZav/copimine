package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleModelTest {
    @Test
    void exposesTheArtistBriefSkeletonAndEveryAnimation() {
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("grab_socket"));
        assertTrue(EndRiftTentacleModel.REQUIRED_BONES.contains("seg_04"));
        for (String animation : new String[]{
                "IDLE", "EMERGE", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD",
                "THROW", "GRAB_MISS", "HURT", "DEATH", "RETRACT",
                "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL"}) {
            assertTrue(EndRiftTentacleModel.supportsAnimation(animation), animation);
            assertTrue(EndRiftTentacleModel.pose(animation, 0.5F).isFinite(), animation);
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
}
