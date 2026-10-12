package me.copimine.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.copimine.client.ArmorStackTracker;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the current item available while its equipment texture is extracted. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"))
    private void copimine$rememberArmor(PoseStack matrices, SubmitNodeCollector collector,
                                        ItemStack stack, EquipmentSlot slot, int light,
                                        HumanoidRenderState state, CallbackInfo ci) {
        ArmorStackTracker.remember(stack);
    }

    @Inject(method = "renderArmorPiece", at = @At("RETURN"))
    private void copimine$forgetArmor(PoseStack matrices, SubmitNodeCollector collector,
                                      ItemStack stack, EquipmentSlot slot, int light,
                                      HumanoidRenderState state, CallbackInfo ci) {
        ArmorStackTracker.clear();
    }
}
