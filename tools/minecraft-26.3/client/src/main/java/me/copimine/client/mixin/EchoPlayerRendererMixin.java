package me.copimine.client.mixin;

import me.copimine.client.EchoEyesFeature;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class EchoPlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer,
        AvatarRenderState, PlayerModel> {
    protected EchoPlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void copimine$echoEyes(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new EchoEyesFeature(this));
    }
}
