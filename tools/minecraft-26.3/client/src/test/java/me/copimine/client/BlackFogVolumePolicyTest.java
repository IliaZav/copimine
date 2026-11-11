package me.copimine.client;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogVolumePolicyTest {
    @Test void extinctionUsesTheUniformDistanceEnvelopeInsteadOfOneBlockBlackout() {
        // Actual GLSL envelope is executed in test_wave5_uniform_volume.py.
        assertTrue(BlackFogVolumePolicy.EXTINCTION_PER_BLOCK >= 2.0F
                && BlackFogVolumePolicy.EXTINCTION_PER_BLOCK <= 2.5F);
        assertEquals(4.0F, BlackFogVolumePolicy.FOG_HEIGHT);
    }
    @Test void everyPositionOnPlatformFindsItsExactClearBox() {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            AtomicInteger reads = new AtomicInteger();
            var box = BlackFogVolumePolicy.find(x, z, (cx, cz) -> {
                reads.incrementAndGet();
                return cx >= 0 && cx < 3 && cz >= 0 && cz < 3;
            });
            assertEquals(new BlackFogVolumePolicy.Box(0, 0, 3, 3, 9), box);
            assertTrue(reads.get() <= 25);
        }
    }
    @Test void anotherPlatformNeverBecomesAHoleThroughTheFog() {
        var box = BlackFogVolumePolicy.find(0, 0, (x,z) -> z == 0 && (x == 0 || x == 2));
        assertEquals(new BlackFogVolumePolicy.Box(0,0,1,1,1), box);
    }
    @Test void outsidePlayerHasNoClearVolume() {
        assertEquals(0, BlackFogVolumePolicy.find(-1, -1, (x,z) -> false).cells());
    }
}
