package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.WitherSkeletonEntityRenderer;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * N23-P1-09: the Phase 4 infernal Wither Skeleton wears an authored skin only
 * after the server has bound its UUID. V6 9E.06 approves this one mobile ground
 * skeleton; the 14.6 tower/turret ban is untouched.
 */
@Mixin(WitherSkeletonEntityRenderer.class)
public abstract class WitherSkeletonEntityRendererMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$infernoWitherTexture(WitherSkeletonEntity entity,
                                               CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        if (!"END_RIFT_INFERNO_WITHER_V1".equals(visual)) {
            return;
        }
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            EndEventTextureCatalog.logLookup("mob:" + visual, texture);
            cir.setReturnValue(texture);
        }
    }
}
