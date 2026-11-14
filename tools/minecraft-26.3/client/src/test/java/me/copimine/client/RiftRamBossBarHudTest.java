package me.copimine.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiftRamBossBarHudTest {
    /**
     * The Ram carries seven plates: four minor pillars plus three hits on the
     * final pillar ({@code RiftRamPolicy.REQUIRED_IMPACTS}). This test used to
     * pin three, from the earlier three-pillar design, and went stale when the
     * roster was widened -- the HUD formula was correct the whole time.
     */
    private static final int PLATES = 7;
    @Test void armourIconsFollowTheSignedBodyVisual() {
        for (int impacts = 0; impacts <= PLATES; impacts++)
            assertEquals(PLATES - impacts, RiftRamBossBarHud.remainingPlates("END_RIFT_RAM_ARMOR" + impacts + "_V1"));
        assertEquals(0, RiftRamBossBarHud.remainingPlates("END_RIFT_RAM_ARMOR" + PLATES + "_V1"));
    }
    @Test void unrelatedAndMalformedVisualsCannotEnableTheCustomBar() {
        for (String value : new String[]{"", "END_RIFT_SKELETON_V1", "END_RIFT_RAM_ARMOR" + (PLATES + 1) + "_V1",
                "END_RIFT_RAM_ARMOR01_V1", "END_RIFT_RAM_ARMOR-1_V1"})
            assertEquals(-1, RiftRamBossBarHud.remainingPlates(value));
        assertEquals(-1, RiftRamBossBarHud.remainingPlates(null));
    }
}