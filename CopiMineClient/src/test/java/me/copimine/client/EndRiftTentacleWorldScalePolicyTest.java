package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleWorldScalePolicyTest {
    @Test
    void importedKaguneRendersAtTheRequestedGuardianHeight() {
        // Kagune's supplied bounds are about 7.8681 model units. The importer
        // presents model coordinates in 1/16-pixel units to this policy.
        float authoredHeight = 7.8681F * EndRiftTentacleWorldScalePolicy.MODEL_UNITS_PER_BLOCK;
        float worldHeight = EndRiftTentacleWorldScalePolicy.worldHeightForAuthoredUnits(
                authoredHeight, 1.0F);
        assertEquals(0.40F, EndRiftTentacleWorldScalePolicy.rendererScaleForRig(1.0F), 0.0001F,
                "the imported Kagune stays compact beside the boss instead of occluding the arena");
        assertEquals(4.878222F, worldHeight, 0.002F,
                "the supplied tentacle should be about 4.9 blocks tall");
        assertTrue(worldHeight >= 4.5F && worldHeight <= 5.0F);
        assertEquals(worldHeight,
                authoredHeight / EndRiftTentacleWorldScalePolicy.MODEL_UNITS_PER_BLOCK
                        * EndRiftTentacleWorldScalePolicy.BASE_LENGTH_RENDER_SCALE,
                0.0001F,
                "visible height must use the renderer's vertical scale");
    }
}
