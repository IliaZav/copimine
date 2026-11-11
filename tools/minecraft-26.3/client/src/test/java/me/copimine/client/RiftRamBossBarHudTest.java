package me.copimine.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiftRamBossBarHudTest {
    @Test void armourIconsFollowTheSignedBodyVisual() {
        for (int impacts = 0; impacts <= 3; impacts++)
            assertEquals(3 - impacts, RiftRamBossBarHud.remainingPlates("END_RIFT_RAM_ARMOR" + impacts + "_V1"));
    }
    @Test void unrelatedAndMalformedVisualsCannotEnableTheCustomBar() {
        for (String value : new String[]{"", "END_RIFT_SKELETON_V1", "END_RIFT_RAM_ARMOR4_V1", "END_RIFT_RAM_ARMOR01_V1", "END_RIFT_RAM_ARMOR-1_V1"})
            assertEquals(-1, RiftRamBossBarHud.remainingPlates(value));
        assertEquals(-1, RiftRamBossBarHud.remainingPlates(null));
    }
}
