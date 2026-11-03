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
    /**
     * Safety valve for the island flood fill. The published footprint is at
     * most 3x3, so this only triggers on a corrupted or foreign floor layout
     * and keeps a single frame bounded.
     */
    private static final int MAX_ISLAND_CELLS = 64;

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private void copimine$applyWaveFiveBlackFog(Camera camera, int viewDistance,
                                                 DeltaTracker deltaTracker, float partialTick,
                                                 ClientLevel level,
                                                 CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        float blackoutEnd = activeFogEnd(level);
        if (fog == null || blackoutEnd <= 0.0F) return;
        // Inside a safe volume the view is shortened, never restored to vanilla
        // fog: the rest of the arena must stay black even from safety. The
        // shortened distance is derived from the published footprint in
        // BlackFogSafeVolumePolicy, not from an independent view radius
        // (contract W5-017), so a view ray leaving the footprint stays black.
        float fogEnd = BlackFogSafeVolumePolicy.fogEndBlocks(
                standsInsideSafeVolume(level, camera), blackoutEnd);
        fog.environmentalStart = 0.0F;
        fog.renderDistanceStart = 0.0F;
        fog.environmentalEnd = fogEnd;
        fog.renderDistanceEnd = fogEnd;
        fog.skyEnd = fogEnd;
        fog.cloudEnd = fogEnd;
        // A pure black read as "the screen is off" rather than as a place. The
        // faintest possible blue-black tint keeps depth cues (the sky behind
        // the wall, the player's own torch falloff) while staying effectively
        // opaque — "чёрный густой", not grey.
        fog.color.set(0.008F, 0.010F, 0.016F, 1.0F);
        // Start the density ramp at the eye instead of a block away, so the
        // wall has no visible "edge" the player can look at.
        fog.environmentalStart = -1.0F;
        fog.renderDistanceStart = -1.0F;
    }

    /**
     * Mirrors the server's emerald safe floors. Reading the published blocks
     * keeps the client and the authoritative hazard geometry in lockstep
     * without a second layout source that could drift or overflow a packet.
     *
     * <p>The readable island is resolved through
     * {@link BlackFogSafeVolumePolicy.SafeMask#aroundCamera}: a second safe
     * island across the fog is emerald too, so a test that accepted any safe
     * column would clear the view through the wall.
     */
    private static boolean standsInsideSafeVolume(ClientLevel level, Camera camera) {
        if (level == null || camera == null) return false;
        Vec3 position = camera.position();
        if (position == null) return false;
        int floorY = currentSafeFloorY(level, position);
        if (floorY == Integer.MIN_VALUE) return false;
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(
                publishedSafeCells(level, position, floorY), floorY);
        return mask.aroundCamera(position.x, position.y, position.z)
                .readable(position.x, position.y, position.z, floorY);
    }

    /** The safe floor block under the camera, or {@code MIN_VALUE} when there is none. */
    private static int currentSafeFloorY(ClientLevel level, Vec3 position) {
        int x = BlackFogSafeVolumePolicy.blockX(position.x);
        int z = BlackFogSafeVolumePolicy.blockZ(position.z);
        for (int floorY = BlackFogSafeVolumePolicy.firstCandidateFloorY(position.y);
             floorY <= BlackFogSafeVolumePolicy.lastCandidateFloorY(position.y); floorY++) {
            if (!BlackFogSafeVolumePolicy.insideSafeColumn(position.y, floorY)) continue;
            if (!level.hasChunkAt(new BlockPos(x, floorY, z))) continue;
            if (level.getBlockState(new BlockPos(x, floorY, z)).getBlock() == Blocks.EMERALD_BLOCK) {
                return floorY;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Cells of the island the camera stands on, read from the published
     * emerald floors. A bounded flood fill keeps this O(zone) instead of
     * scanning the arena every frame.
     */
    private static java.util.List<BlackFogSafeVolumePolicy.SafeCell> publishedSafeCells(
            ClientLevel level, Vec3 position, int floorY) {
        int startX = BlackFogSafeVolumePolicy.blockX(position.x);
        int startZ = BlackFogSafeVolumePolicy.blockZ(position.z);
        java.util.Map<Long, BlackFogSafeVolumePolicy.SafeCell> found = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        open.add(new int[]{startX, startZ});
        while (!open.isEmpty() && found.size() < MAX_ISLAND_CELLS) {
            int[] cell = open.poll();
            long key = (((long) cell[0]) << 32) ^ (cell[1] & 0xFFFFFFFFL);
            if (found.containsKey(key)) continue;
            BlockPos pos = new BlockPos(cell[0], floorY, cell[1]);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != Blocks.EMERALD_BLOCK) continue;
            found.put(key, new BlackFogSafeVolumePolicy.SafeCell(cell[0], cell[1]));
            open.add(new int[]{cell[0] + 1, cell[1]});
            open.add(new int[]{cell[0] - 1, cell[1]});
            open.add(new int[]{cell[0], cell[1] + 1});
            open.add(new int[]{cell[0], cell[1] - 1});
        }
        return java.util.List.copyOf(found.values());
    }

    private static float activeFogEnd(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || level == null || client.level != level) return 0.0F;
        String dimension = level.dimension().identifier().getPath();
        return ClientBridgeProtocol.endEventBlackFogEndBlocks(dimension, System.currentTimeMillis());
    }
}