package me.copimine.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrisonerKeyboardPolicyTest {
    @Test
    void routesMinecraft263KeyboardScancodesToTheirExistingAbilities() {
        assertEquals(PrisonerHudController.Ability.HEAL,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, InputConstants.KEY_Q, 1));
        assertEquals(PrisonerHudController.Ability.BATTLE_SURGE,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, InputConstants.KEY_W, 1));
        assertEquals(PrisonerHudController.Ability.GUARDIAN_LINK,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, InputConstants.KEY_E, 1));
        assertEquals(PrisonerHudController.Ability.TURNCOAT,
                PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, InputConstants.KEY_R, 1));
    }

    @Test
    void doesNotTreatAsciiLettersAsMinecraft263KeyScancodes() {
        assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'Q', 1));
        assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'W', 1));
        assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'E', 1));
        assertNull(PrisonerKeyboardPolicy.abilityForKeyPress(true, false, false, 'R', 1));
    }
}
