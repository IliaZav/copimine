package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerKeyboardPolicyTest {
    @Test
    void qwerPressesRouteToTheExistingProtocolAbilities() {
        assertEquals(PrisonerHudController.Ability.HEAL,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'Q', 1));
        assertEquals(PrisonerHudController.Ability.BATTLE_SURGE,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'W', 1));
        assertEquals(PrisonerHudController.Ability.GUARDIAN_LINK,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'E', 1));
        assertEquals(PrisonerHudController.Ability.TURNCOAT,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'R', 1));
        for (int oldKey : new int[]{'A', 'S', 'D', 'F'}) {
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, oldKey, 1));
        }
        for (PrisonerHudController.Ability ability : PrisonerHudController.Ability.values()) {
            assertEquals(ability, PrisonerKeyboardPolicy.abilityForKeyPress(
                    true, false, false, ability.keyLabel().charAt(0), 1),
                    "The visible HUD key must dispatch the ability it labels");
        }
    }

    @Test
    void abilityKeysNeverDispatchOutsideCaptureOrOnRepeatOrRelease() {
        for (int key : new int[]{'Q', 'W', 'E', 'R'}) {
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(false, false, false, key, 1));
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, true, false, key, 1));
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, true, key, 1));
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, key, 0));
            assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, key, 2));
        }
    }

    @Test
    void passesKeyReleasesThroughSoBindingsCannotStickAfterCapture() {
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 0));
        assertTrue(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 1));
        assertTrue(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 2));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(false, false, false, 1));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, true, false, 1));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, true, 1));
    }
}
