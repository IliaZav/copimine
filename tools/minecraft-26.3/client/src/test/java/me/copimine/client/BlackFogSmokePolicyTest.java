package me.copimine.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogSmokePolicyTest {
    @Test void budgetIsBoundedEvenAtInitialActivation() {
        assertEquals(96, BlackFogSmokePolicy.budget(0, true));
        assertEquals(8, BlackFogSmokePolicy.budget(0, false));
        assertEquals(1, BlackFogSmokePolicy.budget(255, true));
        assertEquals(0, BlackFogSmokePolicy.budget(256, false));
        assertEquals(0, BlackFogSmokePolicy.budget(300, true));
    }
    @Test void SmokeCannotOverlapEvenASingleCellSafeZone() {
        BlackFogVolumePolicy.Floor safe = (x,z) -> x == 0 && z == 0;
        assertFalse(BlackFogSmokePolicy.clearOfSafeFloor(0.5, 0.5, safe));
        assertFalse(BlackFogSmokePolicy.clearOfSafeFloor(2.1, 0.5, safe));
        assertTrue(BlackFogSmokePolicy.clearOfSafeFloor(2.3, 0.5, safe));
        assertFalse(BlackFogSmokePolicy.clearOfSafeFloor(-1.1, 0.5, safe));
        assertTrue(BlackFogSmokePolicy.clearOfSafeFloor(-1.3, 0.5, safe));
    }
}
