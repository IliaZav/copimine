package me.copimine.client;

import java.util.UUID;

/** Render-time color decision for the currently hovered server-eligible prisoner target. */
public final class PrisonerTargetHighlightPolicy {
    private PrisonerTargetHighlightPolicy() {
    }

    public static int colorFor(PrisonerHudController hud, UUID player, UUID hoveredTarget,
                                UUID renderedEntity, boolean ally, boolean hostile) {
        if (hud == null || !hud.activeFor(player) || hoveredTarget == null
                || !hoveredTarget.equals(renderedEntity)) {
            return 0;
        }
        if (ally && hud.isTargetAllowed(PrisonerHudController.Ability.HEAL, renderedEntity)) {
            return 0x35E1F5;
        }
        if (hostile && hud.isTargetAllowed(PrisonerHudController.Ability.TURNCOAT, renderedEntity)) {
            return 0xAC5DFF;
        }
        return 0;
    }
}
