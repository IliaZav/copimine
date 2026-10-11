package me.copimine.client.mixin;

import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndRiftRenderStateAccess;
import me.copimine.client.EndermanRendererSelection;
import me.copimine.client.RiftGuardianModelRenderer;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/** Event textures are applied only to entities with a server-owned render-state binding. */
@Mixin(EndermanRenderer.class)
public abstract class EndermanEntityRendererMixin extends MobRenderer<Enderman, EndermanRenderState,
        EndermanModel<EndermanRenderState>> {
    @Unique private final RiftGuardianModelRenderer copimine$guardianRenderer = new RiftGuardianModelRenderer();

    protected EndermanEntityRendererMixin(EntityRendererProvider.Context context,
                                          EndermanModel<EndermanRenderState> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void copimine$eventTexture(EndermanRenderState state,
                                      CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof EndRiftRenderStateAccess bound)) return;
        String uuid = bound.copimine$uuid();
        if (uuid == null || uuid.isBlank()) return;
        String visual = bound.copimine$visual();
        Identifier texture;
        if (bound.copimine$endBoss()) {
            texture = copimine$guardianRenderer.textureForState(
                    bound.copimine$bossPhase(), bound.copimine$bossAnimation());
        } else {
            texture = EndEventTextureCatalog.textureForVisual(visual);
        }
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        boolean available = texture != null && EndEventTextureCatalog.isAvailable(texture);
        if (available) cir.setReturnValue(texture);
    }
}
