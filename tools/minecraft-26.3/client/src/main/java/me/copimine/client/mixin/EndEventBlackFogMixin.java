package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wave 5 modifies the extracted fog state so the deferred renderer keeps the black fog. */
@Mixin(FogRenderer.class)
public abstract class EndEventBlackFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private void copimine$applyWaveFiveBlackFog(Camera camera, int viewDistance,
                                                 DeltaTracker deltaTracker, float partialTick,
                                                 ClientLevel level,
                                                 CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        float fogEnd = activeFogEnd(level);
        if (fog == null || fogEnd <= 0.0F) return;
        fog.environmentalStart = 0.0F;
        fog.renderDistanceStart = 0.0F;
        fog.environmentalEnd = fogEnd;
        fog.renderDistanceEnd = fogEnd;
        fog.skyEnd = fogEnd;
        fog.cloudEnd = fogEnd;
        fog.color.set(0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static float activeFogEnd(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || level == null || client.level != level) return 0.0F;
        String dimension = level.dimension().identifier().getPath();
        return ClientBridgeProtocol.endEventBlackFogEndBlocks(dimension, System.currentTimeMillis());
    }
}
