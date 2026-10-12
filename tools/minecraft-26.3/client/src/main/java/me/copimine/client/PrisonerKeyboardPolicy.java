package me.copimine.client;

import com.mojang.blaze3d.platform.InputConstants;

/** Input routing rules for the client-only prisoner control lock. */
public final class PrisonerKeyboardPolicy {
    private static final int RELEASE_ACTION = 0;

    private PrisonerKeyboardPolicy() {
    }

    public static boolean shouldCaptureKeyEvent(boolean prisonerModeActive,
                                                boolean screenOpen,
                                                boolean exemptKey,
                                                int action) {
        return prisonerModeActive && !screenOpen && !exemptKey && action != RELEASE_ACTION;
    }

    public static PrisonerHudController.Ability abilityForKeyPress(boolean prisonerModeActive,
            boolean screenOpen, boolean exemptKey, int key, int action) {
        if (!shouldCaptureKeyEvent(prisonerModeActive, screenOpen, exemptKey, action) || action != 1) {
            return null;
        }
        return switch (key) {
            case InputConstants.KEY_Q -> PrisonerHudController.Ability.HEAL;
            case InputConstants.KEY_W -> PrisonerHudController.Ability.BATTLE_SURGE;
            case InputConstants.KEY_E -> PrisonerHudController.Ability.GUARDIAN_LINK;
            case InputConstants.KEY_R -> PrisonerHudController.Ability.TURNCOAT;
            default -> null;
        };
    }
}
