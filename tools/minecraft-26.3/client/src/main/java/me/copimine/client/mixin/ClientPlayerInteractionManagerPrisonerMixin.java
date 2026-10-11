package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops local attacks, item use and block interaction while prisoner input mode owns the controls. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerPrisonerMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerAttack(Player player, Entity target, CallbackInfo ci) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockAttack(BlockPos pos, Direction direction,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockBreakProgress(BlockPos pos, Direction direction,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerBlockInteraction(LocalPlayer player, InteractionHand hand,
                                                        BlockHitResult hit,
                                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerItemUse(Player player, InteractionHand hand,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerEntityInteractionAt(Player player, Entity entity,
                                                           EntityHitResult hit, InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "piercingAttack(Lnet/minecraft/world/item/component/SwingAnimation;Lnet/minecraft/world/item/component/PiercingWeapon;)V",
            at = @At("HEAD"), cancellable = true)
    private void copimine$blockPrisonerPiercingAttack(
            net.minecraft.world.item.component.SwingAnimation animation,
            net.minecraft.world.item.component.PiercingWeapon weapon,
            CallbackInfo ci) {
        if (ClientBridgeProtocol.isPrisonerModeActive()) {
            ci.cancel();
        }
    }
}
