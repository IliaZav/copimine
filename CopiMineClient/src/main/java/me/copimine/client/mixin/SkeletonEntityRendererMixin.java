package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event skeletons receive a texture only after a server UUID binding. */
@Mixin(SkeletonEntityRenderer.class)
public abstract class SkeletonEntityRendererMixin {
    private static final Identifier COPIMINE_SKELETON_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_skeleton.png");
    private static final Identifier COPIMINE_ELITE_SKELETON_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_elite_skeleton.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$skeletonTexture(SkeletonEntity entity,
                                          CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        Identifier texture = switch (visual) {
            case "END_RIFT_ELITE_SKELETON_V1" -> COPIMINE_ELITE_SKELETON_TEXTURE;
            case "END_RIFT_SKELETON_V1" -> COPIMINE_SKELETON_TEXTURE;
            default -> null;
        };
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            cir.setReturnValue(texture);
        }
    }
}
