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

/** Routes A/S/D/F to prisoner abilities and swallows other gameplay keys while captured. */
@Mixin(Keyboard.class)
public abstract class KeyboardPrisonerModeMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void copimine$routePrisonerKey(long window, int key, int scancode,
                                           int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean exemptKey = key == GLFW.GLFW_KEY_ESCAPE
                || client.options.chatKey.matchesKey(key, scancode)
                || client.options.commandKey.matchesKey(key, scancode);
        if (!PrisonerKeyboardPolicy.shouldCaptureKeyEvent(
                ClientBridgeProtocol.isPrisonerModeActive(), client.currentScreen != null,
                exemptKey, action)) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            PrisonerHudController.Ability ability = abilityForKey(key);
            if (ability != null) {
                ClientBridgeProtocol.sendPrisonerAbility(ability);
            }
        }
        ci.cancel();
    }

    private static PrisonerHudController.Ability abilityForKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_A -> PrisonerHudController.Ability.HEAL;
            case GLFW.GLFW_KEY_S -> PrisonerHudController.Ability.BATTLE_SURGE;
            case GLFW.GLFW_KEY_D -> PrisonerHudController.Ability.GUARDIAN_LINK;
            case GLFW.GLFW_KEY_F -> PrisonerHudController.Ability.TURNCOAT;
            default -> null;
        };
    }
}
