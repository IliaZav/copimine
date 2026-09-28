package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops local attacks, item use and block interaction while prisoner input mode owns the controls. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerPrisonerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockAttack(BlockPos pos, Direction direction,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockBreakProgress(BlockPos pos, Direction direction,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockInteraction(ClientPlayerEntity player, Hand hand,
                                                        BlockHitResult hit,
                                                        CallbackInfoReturnable<ActionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerItemUse(PlayerEntity player, Hand hand,
                                              CallbackInfoReturnable<ActionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerEntityInteraction(PlayerEntity player, Entity entity,
                                                        Hand hand,
                                                        CallbackInfoReturnable<ActionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerEntityInteractionAt(PlayerEntity player, Entity entity,
                                                           EntityHitResult hit, Hand hand,
                                                           CallbackInfoReturnable<ActionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
