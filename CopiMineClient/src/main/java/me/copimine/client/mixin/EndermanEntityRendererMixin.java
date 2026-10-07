package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.CopiMineClientLogger;
import me.copimine.client.EndEventTextureCatalog;
import me.copimine.client.EndermanRendererSelection;
import me.copimine.client.RiftGuardianModelRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EndermanEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/** Uses event textures only for UUIDs explicitly bound by the End Rift server. */
@Mixin(EndermanEntityRenderer.class)
public abstract class EndermanEntityRendererMixin extends MobEntityRenderer<EndermanEntity, EntityModel<EndermanEntity>> {
    @Unique
    private static final Set<String> COPIMINE_LOGGED_RENDER_ENTITIES = new HashSet<>();
    @Unique
    private final RiftGuardianModelRenderer copimine$guardianRenderer = new RiftGuardianModelRenderer();

    protected EndermanEntityRendererMixin(EntityRendererFactory.Context context, EntityModel<EndermanEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    private static Identifier texture(String name) {
        return Identifier.of("copimineclient", "textures/entity/" + name + ".png");
    }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$guardianTexture(EndermanEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String entityUuid = entity.getUuid().toString();
        EndermanRendererSelection.Decision guardianSelection = copimine$guardianSelection(entityUuid);
        if (ClientBridgeProtocol.isBoundEndBoss(entity.getUuid().toString())) {
            if (guardianSelection.usesGuardianModel()) {
                cir.setReturnValue(guardianSelection.texture());
            }
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entityUuid);
        Identifier texture = EndEventTextureCatalog.textureForVisual(visual);
        if (!visual.isBlank() && COPIMINE_LOGGED_RENDER_ENTITIES.add(entity.getUuid().toString())) {
            boolean resourcePresent = EndEventTextureCatalog.isAvailable(texture);
            CopiMineClientLogger.info("End Rift renderer visual=" + visual
                    + ", uuid=" + entity.getUuid()
                    + ", texture=" + texture
                    + ", resourcePresent=" + resourcePresent);
        }
        EndEventTextureCatalog.logLookup("mob:" + visual, texture);
        if (texture != null && EndEventTextureCatalog.isAvailable(texture)) {
            cir.setReturnValue(texture);
        }
    }

    @Unique
    private EndermanRendererSelection.Decision copimine$guardianSelection(String entityUuid) {
        if (!ClientBridgeProtocol.isBoundEndBoss(entityUuid)) {
            return EndermanRendererSelection.select(entityUuid, null, null, false);
        }
        Identifier guardianTexture = copimine$guardianRenderer.textureForState(
                ClientBridgeProtocol.bossPhaseForEntity(entityUuid),
                ClientBridgeProtocol.bossAnimationForEntity(entityUuid));
        EndEventTextureCatalog.logLookup("boss", guardianTexture);
        return EndermanRendererSelection.select(
                entityUuid,
                entityUuid,
                guardianTexture,
                EndEventTextureCatalog.isAvailable(guardianTexture));
    }

}
