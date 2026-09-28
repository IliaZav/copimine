package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerHudControllerTest {
    private static final UUID PRISONER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ALLOWED_ALLY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ALLOWED_HOSTILE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID INVALID_TARGET = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void usesFencedServerStateForUnlocksCooldownsAndTargetAvailability() {
        PrisonerHudController hud = new PrisonerHudController();
        assertTrue(hud.applyState("event-a", 7L, PRISONER, 2,
                new long[]{0L, 10_000L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|" + ALLOWED_ALLY
                        + "|" + ALLOWED_HOSTILE), 1_000L));

        assertEquals(PrisonerHudController.HudState.LOCKED,
                hud.state(PrisonerHudController.Ability.TURNCOAT, PRISONER, 1_000L,
                        ALLOWED_HOSTILE).state());
        assertEquals(PrisonerHudController.HudState.COOLDOWN,
                hud.state(PrisonerHudController.Ability.BATTLE_SURGE, PRISONER,
                        1_500L, ALLOWED_ALLY).state());
        assertEquals(9_500L, hud.state(PrisonerHudController.Ability.BATTLE_SURGE,
                PRISONER, 1_500L, ALLOWED_ALLY).remainingMillis());
        assertEquals(PrisonerHudController.HudState.NO_TARGET,
                hud.state(PrisonerHudController.Ability.HEAL, PRISONER,
                        1_500L, INVALID_TARGET).state());
        assertEquals(PrisonerHudController.HudState.READY,
                hud.state(PrisonerHudController.Ability.HEAL, PRISONER,
                        1_500L, ALLOWED_ALLY).state());
        assertTrue(hud.applyState("event-a", 7L, PRISONER, 4,
                new long[]{0L, 0L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|" + ALLOWED_ALLY
                        + "|" + ALLOWED_HOSTILE), 1_500L));
        assertEquals(PrisonerHudController.HudState.NO_TARGET,
                hud.state(PrisonerHudController.Ability.TURNCOAT, PRISONER,
                        1_500L, ALLOWED_ALLY).state());
        assertEquals(PrisonerHudController.HudState.LOCKED,
                hud.state(PrisonerHudController.Ability.HEAL, OTHER,
                        1_500L, ALLOWED_ALLY).state());
        assertFalse(hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, INVALID_TARGET));
    }

    @Test
    void rejectsStaleMalformedAndDifferentEventPacketsAndClearsOnRelease() {
        PrisonerHudController hud = new PrisonerHudController();
        assertTrue(hud.applyState("event-a", 4L, PRISONER, 1,
                new long[]{0L, 0L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1||"), 100L));
        assertFalse(hud.applyState("event-a", 3L, PRISONER, 4,
                new long[]{0L, 0L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1||"), 200L));
        assertFalse(hud.applyState("event-b", 4L, PRISONER, 4,
                new long[]{0L, 0L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1||"), 200L));
        assertFalse(hud.applyState("event-a", 4L, PRISONER, 1,
                new long[]{0L, 61_000L, 0L, 0L},
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1||"), 200L));
        assertTrue(hud.clear("event-a", 4L));
        assertFalse(hud.activeFor(PRISONER));
        assertFalse(hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, ALLOWED_ALLY));
    }
}
