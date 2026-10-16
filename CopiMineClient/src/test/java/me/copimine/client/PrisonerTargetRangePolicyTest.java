package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerTargetRangePolicyTest {
    @Test
    void supportTargetsUseTheServerSupportRange() {
        assertTrue(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.HEAL, 24.0D));
        assertFalse(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.HEAL, 24.01D));
        assertTrue(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.GUARDIAN_LINK, 24.0D));
    }

    @Test
    void turncoatCanSelectTargetsThroughTheServerTurncoatRange() {
        assertTrue(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.TURNCOAT, 28.0D));
        assertFalse(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.TURNCOAT, 28.01D));
        assertFalse(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.TURNCOAT, Double.NaN));
        assertFalse(PrisonerTargetRangePolicy.allows(
                PrisonerHudController.Ability.TURNCOAT, -1.0D));
    }
}
