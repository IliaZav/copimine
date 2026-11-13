package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.GuardianSpellRenderer;
import me.copimine.client.GuardianSpellRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownItemRenderer.class)
public abstract class ThrownItemRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/ThrownItemRenderState;F)V", at=@At("TAIL"))
    private void copimine$captureSpell(Entity entity, ThrownItemRenderState state, float partialTick, CallbackInfo ci) {
        if (state instanceof GuardianSpellRenderStateAccess access)
            access.copimine$spellPose(GuardianSpellRenderer.thrownPose(entity,partialTick));
    }

    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ThrownItemRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at=@At("HEAD"), cancellable=true)
    private void copimine$drawSpell(ThrownItemRenderState state, PoseStack matrices,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (state instanceof GuardianSpellRenderStateAccess access
                && GuardianSpellRenderer.submit(access.copimine$spellPose(),matrices,collector)) ci.cancel();
    }
}
