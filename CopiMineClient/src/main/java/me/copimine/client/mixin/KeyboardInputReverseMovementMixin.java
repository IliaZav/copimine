package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the optional End Rift control effect after vanilla keyboard sampling. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputReverseMovementMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void copimine$reverseMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Input input = (Input) (Object) this;
        float sampledForward = input.movementForward;
        float sampledSideways = input.movementSideways;
        ClientBridgeProtocol.sendControlInput(sampledForward, sampledSideways);
        if (ClientBridgeProtocol.isReverseMovementActive()) {
            input.movementForward = -input.movementForward;
            input.movementSideways = -input.movementSideways;
        } else if (ClientBridgeProtocol.isControlSwapActive()) {
            // During a swap the server applies the bounded input to the paired
            // player's facing vector.  Freeze local vanilla movement so the
            // two clients cannot create an unauthorised third movement path.
            input.movementForward = 0.0F;
            input.movementSideways = 0.0F;
        }
    }
}
