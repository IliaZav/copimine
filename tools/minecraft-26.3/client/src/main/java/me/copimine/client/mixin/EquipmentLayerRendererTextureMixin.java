package me.copimine.client.mixin;

import java.util.function.Function;
import me.copimine.client.ArmorStackTracker;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies item-specific armor textures after vanilla's item-agnostic memoized lookup. */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererTextureMixin {
    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0))
    private Object copimine$useStackArmorTexture(Function<Object, Object> lookup, Object key) {
        String texture = ArmorStackTracker.currentTexture();
        if (texture != null && !texture.isEmpty()) {
            return Identifier.fromNamespaceAndPath(
                    "copimine", "textures/models/armor/" + texture + ".png");
        }
        return lookup.apply(key);
    }
}
