package me.copimine.client.mixin;

import me.copimine.client.BlackFogSafeVolumePolicy;
import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.EndEventBlackFogManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Alters world FogData only; HUD, menus, hand rendering and asset bindings stay independent. */
@Mixin(FogRenderer.class)
public abstract class EndEventBlackFogMixin {
    @Unique
    private static final BlackFogSafeVolumePolicy.SafeFloorCache copimine$safeFloorCache =
            new BlackFogSafeVolumePolicy.SafeFloorCache();

    // Descriptor verified against the pinned Minecraft 26.3 FogRenderer class.
    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"), cancellable = true)
    private void copimine$applyWaveFiveBlackFog(Camera camera, int viewDistance,
                                               DeltaTracker deltaTracker, float partialTick,
                                               ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        Minecraft client = Minecraft.getInstance();
        if (fog == null || client == null || level == null || client.level != level) {
            copimine$safeFloorCache.clear();
            return;
        }
        long now = System.currentTimeMillis();
        String dimension = level.dimension().identifier().getPath();
        EndEventBlackFogManager manager = ClientBridgeProtocol.endEventBlackFog();
        var arena = ClientBridgeProtocol.endEventArenaBounds(manager.eventId(), manager.generation());
        if (arena == null || client.player == null || client.player.getX() < arena.minX()
                || client.player.getX() >= arena.maxX() + 1.0 || client.player.getZ() < arena.minZ()
                || client.player.getZ() >= arena.maxZ() + 1.0 || client.player.getY() < arena.minY()
                || client.player.getY() >= arena.maxY() + 1.0) return;
        float strength = manager.fadeStrength(dimension, now);
        if (strength <= 0.0F) {
            copimine$safeFloorCache.clear();
            return;
        }
        // Use authoritative player feet rather than the camera: eye height,
        // damage bob and third-person position cannot flicker safe membership.
        boolean safe = client.player != null && copimine$safeFloorCache.contains(
                level, manager.eventId(), manager.generation(), now,
                client.player.getX(), client.player.getY(), client.player.getZ(),
                (x, y, z) -> {
                    BlockPos pos = new BlockPos(x, y, z);
                    return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.EMERALD_BLOCK);
                });
        float end = BlackFogSafeVolumePolicy.fogEndBlocks(safe, manager.fogEndBlocks(dimension, now));
        float start = safe ? 2.0F : 1.25F;
        fog.environmentalStart = copimine$blend(fog.environmentalStart, start, strength);
        fog.renderDistanceStart = copimine$blend(fog.renderDistanceStart, start, strength);
        fog.environmentalEnd = copimine$blend(fog.environmentalEnd, end, strength);
        fog.renderDistanceEnd = copimine$blend(fog.renderDistanceEnd, end, strength);
        fog.skyEnd = copimine$blend(fog.skyEnd, end, strength);
        fog.cloudEnd = copimine$blend(fog.cloudEnd, end, strength);
        fog.color.set(copimine$blend(fog.color.x, 0.008F, strength),
                copimine$blend(fog.color.y, 0.010F, strength),
                copimine$blend(fog.color.z, 0.016F, strength), fog.color.w);
    }

    @Unique
    private static float copimine$blend(float vanilla, float target, float strength) {
        return vanilla + (target - vanilla) * strength;
    }
}
