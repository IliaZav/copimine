package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Resolves the actual server-published floor, never the player's changing jump height. */
public final class BlackFogChamberRuntime {
    private static Object world;
    private static String event = "";
    private static long generation, searchedAt;
    private static double floor = Double.NaN;
    private BlackFogChamberRuntime() {}

    public static double floorTop(Minecraft client, EndEventBlackFogManager fog, long now) {
        if (world != client.level || !event.equals(fog.eventId()) || generation != fog.generation()) {
            world = client.level; event = fog.eventId(); generation = fog.generation();
            floor = Double.NaN; searchedAt = 0;
        }
        if (Double.isFinite(floor)) return floor;
        var arena = ClientBridgeProtocol.endEventArenaBounds(fog.eventId(), fog.generation());
        if (client.level == null || arena == null) return Double.NaN;
        // Safe-zone beams already carry the authoritative floor during the approach window.
        for (var beam : ClientBridgeProtocol.endEventWorldVfx().snapshots(fog.eventId(), fog.generation(), now)) {
            if (BlackFogGuidePolicy.isGuide(beam.instanceId())
                    && beam.dimension().equals(client.level.dimension().identifier().getPath())
                    && beam.start().y >= arena.minY() && beam.start().y <= arena.maxY() + 1) {
                floor = Math.floor(beam.start().y);
                return floor;
            }
        }
        if (searchedAt != 0 && now >= searchedAt && now - searchedAt < 500) return Double.NaN;
        searchedAt = now;
        // If the critical fog packet overtook cosmetic beams, inspect loaded published
        // emerald cells. Bounded reads; never loads a chunk or scans an unbounded world.
        int reads = 0;
        for (int y = Math.toIntExact(arena.minY()); y <= arena.maxY() && y < arena.minY() + 16; y++) {
            for (int x = Math.toIntExact(arena.minX()); x <= arena.maxX(); x++) for (int z = Math.toIntExact(arena.minZ()); z <= arena.maxZ(); z++) {
                if (++reads > 32768) return Double.NaN;
                BlockPos p = new BlockPos(x, y, z);
                if (client.level.hasChunkAt(p) && client.level.getBlockState(p).is(Blocks.EMERALD_BLOCK)) {
                    floor = y + 1.0; return floor;
                }
            }
        }
        return Double.NaN;
    }
}
