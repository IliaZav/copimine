package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossAnimationIdTest {
    @Test
    void artistAnimationNamesResolveToStableClientIds() {
        assertEquals(BossAnimationId.RUN, BossAnimationId.fromWire("Running2"));
        assertEquals(BossAnimationId.MELEE_SWIPE, BossAnimationId.fromWire("Swipe2"));
        assertEquals(BossAnimationId.HURT, BossAnimationId.fromWire("Hurt2"));
        assertEquals(BossAnimationId.DYING, BossAnimationId.fromWire("Dying2"));
        assertEquals(BossAnimationId.CHEST_STRIKE, BossAnimationId.fromWire("udar_iz_grudi"));
        assertEquals(BossAnimationId.GROUND_SLAM, BossAnimationId.fromWire("udar_po_zemle2"));
        assertEquals(BossAnimationId.CAST_CHARGE, BossAnimationId.fromWire("TELEGRAPHING"));
        assertEquals(BossAnimationId.CAST_RELEASE, BossAnimationId.fromWire("EXECUTING"));
        assertEquals(BossAnimationId.UNKNOWN, BossAnimationId.fromWire("missing_animation"));
    }

    @Test
    void catalogContainsEveryProductionPoseFamily() {
        for (BossAnimationId animation : BossAnimationId.values()) {
            assertTrue(BossAnimationId.isKnown(animation.wireId()), animation.wireId());
        }
        assertNotEquals(BossAnimationId.UNKNOWN,
                BossAnimationId.fromWire("SPELL_RIFT_OBELISKS"));
    }
}
