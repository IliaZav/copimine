package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerTargetEligibilityTest {
    private static final UUID ALLY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID HOSTILE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNKNOWN = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void onlyServerListedTargetsAreEligibleForTheirAbilityFamily() {
        PrisonerTargetEligibility eligibility = PrisonerTargetEligibility.parse(
                "PRISONER_TARGETS_V1|" + ALLY + "|" + HOSTILE);

        assertTrue(eligibility.allows(PrisonerHudController.Ability.HEAL, ALLY));
        assertTrue(eligibility.allows(PrisonerHudController.Ability.BATTLE_SURGE, ALLY));
        assertTrue(eligibility.allows(PrisonerHudController.Ability.GUARDIAN_LINK, ALLY));
        assertFalse(eligibility.allows(PrisonerHudController.Ability.TURNCOAT, ALLY));
        assertTrue(eligibility.allows(PrisonerHudController.Ability.TURNCOAT, HOSTILE));
        assertFalse(eligibility.allows(PrisonerHudController.Ability.HEAL, HOSTILE));
        assertFalse(eligibility.allows(PrisonerHudController.Ability.HEAL, UNKNOWN));
    }

    @Test
    void emptyOrMalformedEligibilityFailsClosed() {
        assertFalse(PrisonerTargetEligibility.parse("").allows(
                PrisonerHudController.Ability.HEAL, ALLY));
        assertFalse(PrisonerTargetEligibility.parse("PRISONER_TARGETS_V0|" + ALLY + "|" + HOSTILE)
                .allows(PrisonerHudController.Ability.HEAL, ALLY));
        assertFalse(PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|not-a-uuid|" + HOSTILE)
                .allows(PrisonerHudController.Ability.TURNCOAT, HOSTILE));
    }
}
