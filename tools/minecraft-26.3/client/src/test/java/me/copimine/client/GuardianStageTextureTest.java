package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The boss visibly changes material as the fight grinds it down. */
class GuardianStageTextureTest {
    @Test
    void everyPhaseWearsItsOwnProgressiveStage() {
        RiftGuardianModelRenderer renderer = new RiftGuardianModelRenderer();
        String[] phases = {"AWAKENING", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL"};
        for (int stage = 0; stage < phases.length; stage++) {
            assertEquals(stage, RiftGuardianModelRenderer.stageFor(phases[stage]));
            assertEquals("textures/entity/end_rift_user_boss_stage" + stage + ".png",
                    renderer.textureForPhase(phases[stage]).getPath());
        }
        // Unknown/blank phases must not fall off the sheet.
        assertEquals(0, RiftGuardianModelRenderer.stageFor(null));
        assertEquals(0, RiftGuardianModelRenderer.stageFor("NOT_A_PHASE"));
    }

    @Test
    void everyStageSheetShipsInTheClientResources() throws Exception {
        for (int stage = 0; stage < 6; stage++) {
            String path = "/assets/copimineclient/textures/entity/end_rift_user_boss_stage"
                    + stage + ".png";
            try (InputStream stream = GuardianStageTextureTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                assertTrue(stream.read() >= 0, path);
            }
        }
    }
}