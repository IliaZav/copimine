package me.copimine.client.mixin;

import me.copimine.client.EchoEyesFeature;
import me.copimine.client.EchoPlayerView;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class EchoPlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {
    protected EchoPlayerRendererMixin(EntityRendererFactory.Context context,
                                     PlayerEntityModel<AbstractClientPlayerEntity> model, float shadow) {
        super(context, model, shadow);
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void copimine$echoEyes(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        addFeature(new EchoEyesFeature(this));
    }
    @Inject(method = "hasLabel(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void copimine$hideDetachedName(AbstractClientPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof EchoPlayerView) cir.setReturnValue(false);
    }
}
