package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrisonerTargetHighlightPolicyTest {
    private static final UUID PRISONER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ALLY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID HOSTILE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void eligibleHoveredAllyGetsCyanAndHoveredHostileGetsPurple() {
        PrisonerHudController hud = activeHud();
        assertEquals(0x35E1F5, color(hud, PRISONER, ALLY, ALLY, true, false));
        assertEquals(0xAC5DFF, color(hud, PRISONER, HOSTILE, HOSTILE, false, true));
    }

    @Test
    void otherEligibleEntitiesAndRoleMismatchesHaveNoPrisonerOutline() {
        PrisonerHudController hud = activeHud();
        assertEquals(0, color(hud, PRISONER, ALLY, HOSTILE, false, true));
        assertEquals(0, color(hud, PRISONER, HOSTILE, ALLY, true, false));
        assertEquals(0, color(hud, PRISONER, ALLY, ALLY, false, true));
        assertEquals(0, color(hud, PRISONER, HOSTILE, HOSTILE, true, false));
        assertEquals(0, color(hud, PRISONER, null, ALLY, true, false));
    }

    @Test
    void revokedServerAllowlistImmediatelyRemovesTheHoveredOutline() {
        PrisonerHudController hud = activeHud();
        hud.applyState("event-a", 7L, PRISONER, 4, new long[4],
                PrisonerTargetEligibility.empty(), 200L);
        assertEquals(0, color(hud, PRISONER, ALLY, ALLY, true, false));
        assertEquals(0, color(hud, PRISONER, HOSTILE, HOSTILE, false, true));
    }

    @Test
    void anotherPlayerOrClearedSessionCannotKeepAHoveredOutline() {
        PrisonerHudController hud = activeHud();
        assertEquals(0, color(hud, ALLY, HOSTILE, HOSTILE, false, true));
        hud.clear();
        assertEquals(0, color(hud, PRISONER, ALLY, ALLY, true, false));
        assertEquals(0, color(hud, PRISONER, HOSTILE, HOSTILE, false, true));
    }

    private static PrisonerHudController activeHud() {
        PrisonerHudController hud = new PrisonerHudController();
        hud.applyState("event-a", 7L, PRISONER, 4, new long[4],
                PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|" + ALLY + "|" + HOSTILE), 100L);
        return hud;
    }

    private static int color(PrisonerHudController hud, UUID player, UUID hover, UUID entity,
                             boolean ally, boolean hostile) {
        return PrisonerTargetHighlightPolicy.colorFor(hud, player, hover, entity, ally, hostile);
    }
}
