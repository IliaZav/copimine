package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.RiftRamVisualPolicy;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.client.renderer.entity.state.RavagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the Wave 6 Rift Ram armour sheet to the event-owned Ravager only.
 *
 * <p>Vanilla {@code RavagerRenderer} matches every Ravager in the world, so this
 * mixin deliberately returns early unless the render state carries a server
 * binding for a Rift Ram visual. A natural Ravager raided from the world keeps
 * its vanilla texture, which is why the check is on the visual id rather than on
 * the entity type.</p>
 */
@Mixin(RavagerRenderer.class)
public abstract class RavagerEntityRendererMixin extends MobRenderer<Ravager, RavagerRenderState,
        RavagerModel> {

    protected RavagerEntityRendererMixin(EntityRendererProvider.Context context,
                                         RavagerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/RavagerRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$riftRamTexture(RavagerRenderState state,
                                         CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String uuid = bound.copimine$uuid();
        if (uuid == null || uuid.isBlank()) return;
        String visual = bound.copimine$visual();
        if (!RiftRamVisualPolicy.isRiftRamVisual(visual)) return;
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        EndEventTextureCatalog.logLookup("ravager:" + visual, texture);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            cir.setReturnValue(texture);
        }
    }
}
