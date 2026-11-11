package me.copimine.endevent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.copimine.endevent.domain.BlackFogShelterMobPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogShelterMobPolicyTest {
    private final List<BlackFogShelterMobPolicy.Cell> cells = List.of(new BlackFogShelterMobPolicy.Cell(0, 0));
    @Test void spiderWhoseCentreIsOutsideStillCannotHangOverTheShelter() {
        assertTrue(BlackFogShelterMobPolicy.overlaps(1.5, .5, 1.4, cells));
        assertFalse(BlackFogShelterMobPolicy.overlaps(2.5, .5, 1.4, cells));
    }
    @Test void exitRespectsTheWholeBodyAndTheWorldCollisionPredicate() {
        var exit = BlackFogShelterMobPolicy.exit(.5, .5, 1.4, cells, (x, z) -> x >= 2 && z == 0);
        assertEquals(new BlackFogShelterMobPolicy.Cell(2, 0), exit);
        assertFalse(BlackFogShelterMobPolicy.overlaps(exit.x() + .5, exit.z() + .5, 1.4, cells));
    }
    @Test void blockedWorldHasBoundedSearchAndDoesNotInventAWalkableDestination() {
        var reads = new AtomicInteger();
        assertNull(BlackFogShelterMobPolicy.exit(.5, .5, 1.4, cells, (x, z) -> { reads.incrementAndGet(); return false; }));
        assertTrue(reads.get() <= 288);
    }
    @Test void outsideAndInvalidActorsCannotTriggerArbitraryMovement() {
        assertNull(BlackFogShelterMobPolicy.exit(5, 5, .6, cells, (x, z) -> fail("already outside")));
        assertNull(BlackFogShelterMobPolicy.exit(Double.NaN, 0, .6, cells, (x, z) -> true));
    }
}
