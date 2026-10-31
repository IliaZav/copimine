package me.copimine.client.mixin;

import me.copimine.client.BlackFogSafeVolumePolicy;
import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
        float blackoutEnd = activeFogEnd(level);
        if (fog == null || blackoutEnd <= 0.0F) return;
        // Inside a safe volume the view is shortened, never restored to vanilla
        // fog: the rest of the arena must stay black even from safety.
        float fogEnd = BlackFogSafeVolumePolicy.fogEndBlocks(
                standsInsideSafeVolume(level, camera), blackoutEnd);
        fog.environmentalStart = 0.0F;
        fog.renderDistanceStart = 0.0F;
        fog.environmentalEnd = fogEnd;
        fog.renderDistanceEnd = fogEnd;
        fog.skyEnd = fogEnd;
        fog.cloudEnd = fogEnd;
        fog.color.set(0.0F, 0.0F, 0.0F, 1.0F);
    }

    /**
     * Mirrors the server's emerald safe floors. Reading the published blocks
     * keeps the client and the authoritative hazard geometry in lockstep
     * without a second layout source that could drift or overflow a packet.
     */
    private static boolean standsInsideSafeVolume(ClientLevel level, Camera camera) {
        if (level == null || camera == null) return false;
        Vec3 position = camera.position();
        if (position == null) return false;
        int x = BlackFogSafeVolumePolicy.blockX(position.x);
        int z = BlackFogSafeVolumePolicy.blockZ(position.z);
        for (int floorY = BlackFogSafeVolumePolicy.firstCandidateFloorY(position.y);
             floorY <= BlackFogSafeVolumePolicy.lastCandidateFloorY(position.y); floorY++) {
            if (!BlackFogSafeVolumePolicy.insideSafeColumn(position.y, floorY)) continue;
            if (!level.hasChunkAt(new BlockPos(x, floorY, z))) continue;
            BlockState state = level.getBlockState(new BlockPos(x, floorY, z));
            if (state.getBlock() == Blocks.EMERALD_BLOCK) return true;
        }
        return false;
    }

    private static float activeFogEnd(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || level == null || client.level != level) return 0.0F;
        String dimension = level.dimension().identifier().getPath();
        return ClientBridgeProtocol.endEventBlackFogEndBlocks(dimension, System.currentTimeMillis());
    }
}