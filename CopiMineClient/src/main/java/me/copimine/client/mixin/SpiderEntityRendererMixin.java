package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.entity.SpiderEntityRenderer;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Event spiders receive a texture only after a server UUID binding. */
@Mixin(SpiderEntityRenderer.class)
public abstract class SpiderEntityRendererMixin {
    private static final Identifier COPIMINE_SPIDER_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_spider.png");

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$spiderTexture(SpiderEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity != null && "END_RIFT_SPIDER_V1".equals(
                ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString()))) {
            cir.setReturnValue(COPIMINE_SPIDER_TEXTURE);
        }
    }
}
