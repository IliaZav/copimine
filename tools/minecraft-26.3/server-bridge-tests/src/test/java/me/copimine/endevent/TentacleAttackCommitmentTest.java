package me.copimine.endevent;

import me.copimine.endevent.domain.TentacleGuardianPolicy;
import me.copimine.endevent.domain.TentacleGuardianLayoutPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TentacleAttackCommitmentTest {
    @Test void repeatedHitsCannotPostponeAnAttackPastItsDeadline() {
        long deadline = 160;
        for (long now = 100; now < 250; now++) {
            assertEquals(now < deadline, TentacleGuardianPolicy.shouldFlinchBeforeAttack(now, deadline));
            assertEquals(deadline, TentacleGuardianPolicy.nextAttackTickAfterRecovery(deadline, now, 0));
        }
        assertTrue(TentacleGuardianPolicy.shouldFlinchBeforeAttack(100, -1));
    }

    @Test void eightCloserGuardiansStillHaveDistinctRoots() {
        for (int i = 0; i < 8; i++) {
            var a = TentacleGuardianLayoutPolicy.permanentOffset(i, 8);
            assertEquals(5.5, Math.hypot(a.x(), a.z()), 0.00001);
            for (int j = i + 1; j < 8; j++) {
                var b = TentacleGuardianLayoutPolicy.permanentOffset(j, 8);
                assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) > 2.75);
            }
        }
    }
}
