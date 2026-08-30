package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.CopiMineClientLogger;
import me.copimine.client.EndEventTextureCatalog;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/** Event skeletons receive a texture only after a server UUID binding. */
@Mixin(SkeletonEntityRenderer.class)
public abstract class SkeletonEntityRendererMixin {
    private static final Identifier COPIMINE_SKELETON_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_skeleton.png");
    private static final Identifier COPIMINE_ELITE_SKELETON_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_elite_skeleton.png");
    private static final Set<String> LOGGED_TEXTURE_BINDINGS = new HashSet<>();

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$skeletonTexture(AbstractSkeletonEntity entity,
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
        boolean resourcePresent = texture != null && EndEventTextureCatalog.isAvailable(texture);
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        // Keep this diagnostic set bounded by visual state rather than by
        // every transient server UUID seen during a long event session.
        String bindingKey = visual + "|" + resourcePresent;
        if (LOGGED_TEXTURE_BINDINGS.add(bindingKey)) {
            CopiMineClientLogger.info("SKELETON_TEXTURE_BIND uuid=" + entity.getUuid()
                    + " visual=" + visual + " texture=" + texture
                    + " resourcePresent=" + resourcePresent);
        }
        if (resourcePresent) {
            cir.setReturnValue(texture);
        }
    }
}
