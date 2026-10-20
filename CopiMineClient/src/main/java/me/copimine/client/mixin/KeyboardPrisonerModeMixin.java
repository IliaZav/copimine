package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.PrisonerHudController;
import me.copimine.client.PrisonerKeyboardPolicy;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes Q/W/E/R to prisoner abilities and swallows other gameplay keys while captured. */
@Mixin(Keyboard.class)
public abstract class KeyboardPrisonerModeMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void copimine$routePrisonerKey(long window, int key, int scancode,
                                           int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean exemptKey = key == GLFW.GLFW_KEY_ESCAPE
                // Inspection keys remain available while gameplay controls are frozen.
                || key == GLFW.GLFW_KEY_F1 || key == GLFW.GLFW_KEY_F2 || key == GLFW.GLFW_KEY_F5
                || client.options.chatKey.matchesKey(key, scancode)
                || client.options.commandKey.matchesKey(key, scancode);
        boolean prisonerModeActive = ClientBridgeProtocol.isPrisonerModeActive();
        boolean screenOpen = client.currentScreen != null;
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
