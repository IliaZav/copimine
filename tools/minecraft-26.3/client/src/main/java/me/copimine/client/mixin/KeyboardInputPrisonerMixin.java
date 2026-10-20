package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.WaveTwoControlInputPolicy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the captured prisoner stationary while the server-owned session is active. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPrisonerMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void copimine$lockPrisonerMovement(CallbackInfo ci) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = new Vec2(0.0F, 0.0F);
            return;
        }

        WaveTwoControlInputPolicy.applyIfReverseActive(
                this.keyPresses,
                ClientBridgeProtocol.endEventState(),
                System.currentTimeMillis())
                .ifPresent(applied -> {
                    this.keyPresses = applied.keyPresses();
                    this.moveVector = applied.moveVector();
                });
    }
}
