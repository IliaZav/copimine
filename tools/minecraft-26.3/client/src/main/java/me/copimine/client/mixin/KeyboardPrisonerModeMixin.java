package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.PrisonerHudController;
import me.copimine.client.PrisonerKeyboardPolicy;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes Q/W/E/R to prisoner abilities and swallows other gameplay keys while captured. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardPrisonerModeMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void copimine$routePrisonerKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (event == null) return;
        Minecraft client = Minecraft.getInstance();
        int key = event.key();
        boolean exemptKey = key == InputConstants.KEY_ESCAPE
                // Inspection keys remain available while gameplay controls are frozen.
                || key == InputConstants.KEY_F1 || key == InputConstants.KEY_F2 || key == InputConstants.KEY_F5
                || client.options.keyChat.matches(event)
                || client.options.keyCommand.matches(event);
        boolean prisonerModeActive = ClientBridgeProtocol.isPrisonerModeActive();
        boolean screenOpen = client.gui.screen() != null;
        if (!PrisonerKeyboardPolicy.shouldCaptureKeyEvent(
                prisonerModeActive, screenOpen,
                exemptKey, action)) {
            return;
        }
        PrisonerHudController.Ability ability = PrisonerKeyboardPolicy.abilityForKeyPress(
                prisonerModeActive, screenOpen, exemptKey, key, action);
        if (ability != null) {
            ClientBridgeProtocol.sendPrisonerAbility(ability);
        }
        ci.cancel();
    }
}
