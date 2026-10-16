package me.copimine.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class EndEventBlackFogMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void copimine$applyWaveFiveBlackFog(CallbackInfo ci) {
        float fogEnd = activeFogEnd();
        if (fogEnd <= 0.0F) return;
        RenderSystem.setShaderFogStart(0.0F);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(0.0F, 0.0F, 0.0F);
    }

    @Inject(method = "applyFogColor", at = @At("TAIL"))
    private static void copimine$blackenWaveFiveFogColor(CallbackInfo ci) {
        if (activeFogEnd() > 0.0F) {
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    private static float activeFogEnd() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return 0.0F;
        String dimension = client.world.getRegistryKey().getValue().getPath();
        return ClientBridgeProtocol.endEventBlackFogEndBlocks(
                dimension, System.currentTimeMillis());
    }
}
