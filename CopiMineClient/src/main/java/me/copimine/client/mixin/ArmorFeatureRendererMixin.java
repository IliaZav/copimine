package me.copimine.client.mixin;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the item-specific CopiMine armor layer when an artifact is equipped. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Unique
    private static final ThreadLocal<ItemStack> COPIMINE_CURRENT_ARMOR = new ThreadLocal<>();

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void copimine$rememberArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         LivingEntity entity, EquipmentSlot slot, int light,
                                         BipedEntityModel<?> model, CallbackInfo ci) {
        COPIMINE_CURRENT_ARMOR.set(entity.getEquippedStack(slot));
    }

    @Inject(method = "renderArmor", at = @At("RETURN"))
    private void copimine$forgetArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      LivingEntity entity, EquipmentSlot slot, int light,
                                      BipedEntityModel<?> model, CallbackInfo ci) {
        COPIMINE_CURRENT_ARMOR.remove();
    }

    @Redirect(
            method = "renderArmor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ArmorMaterial$Layer;getTexture(Z)Lnet/minecraft/util/Identifier;")
    )
    private Identifier copimine$artifactArmorTexture(ArmorMaterial.Layer vanillaLayer, boolean leggings) {
        ItemStack stack = COPIMINE_CURRENT_ARMOR.get();
        String itemId = copimine$itemId(stack);
        String texture = switch (itemId) {
            case "kaska_prorab_huev" -> "kaska_prorab_huev_layer_1";
            case "mne_pohuy_ya_v_tanke_vest" -> "mne_pohuy_ya_v_tanke_layer_1";
            case "treasurer_chestplate" -> "kaznacheyskiy_layer_1";
            case "kozyrny_tuz_pozdnyakova" -> "secret_items_layer_2";
            default -> "";
        };
        return texture.isEmpty()
                ? vanillaLayer.getTexture(leggings)
                : Identifier.of("copimine", "textures/models/armor/" + texture + ".png");
    }

    @Unique
    private static String copimine$itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return "";
        return customData.getNbt().getString("copimine:artifact_item_id").toLowerCase(java.util.Locale.ROOT);
    }
}
