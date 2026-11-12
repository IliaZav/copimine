package me.copimine.endevent;

import me.copimine.endevent.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossCloseCombatPolicyTest {
    @Test void closeOpportunityDoesNotStealMajorCastOrHitThroughWalls() {
        assertTrue(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 80, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 101, 80, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 101, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 200, 90, 80, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 80, 200, 3, false, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 80, 200, 3, true, true));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 80, 200, 7, true, false));
        // LAST_SEAL is grounded melee fury after the cocoon falls. The runtime
        // owns the cocoon/fall gate; a blanket phase ban would freeze the finale.
        assertTrue(BossCloseCombatPolicy.canStrike(BossPhase.LAST_SEAL, 100, 90, 80, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.LAST_SEAL, 100, 90, 101, 200, 3, true, false));
        assertFalse(BossCloseCombatPolicy.canStrike(BossPhase.HUNT, 100, 90, 80, 200, Double.NaN, true, false));
    }
}
