package me.copimine.client;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogVolumePolicyTest {
    @Test void evenTheThinnestEddyObscuresTheWorldWithinTwoBlocks() {
        double transmission = Math.exp(-BlackFogVolumePolicy.EXTINCTION_PER_BLOCK * 0.72 * 2.0);
        assertTrue(transmission < 0.002, "at least 99.8 percent extinction beyond the safe boundary");
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
