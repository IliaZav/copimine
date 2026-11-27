package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.RewardMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Chest.Type;

/** A published reward can use an existing chest or a clear floor cell. */
public final class DungeonRewardSite {
    private DungeonRewardSite() {}

    public static boolean valid(RewardMode mode, World world, int x, int y, int z) {
        if (world == null || y <= world.getMinHeight() || y >= world.getMaxHeight() - 1
                || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
        var block = world.getBlockAt(x, y, z);
        if (mode == RewardMode.CHEST) {
            return block.getType() == Material.CHEST
                    && block.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                    && data.getType() == Type.SINGLE && !data.isWaterlogged()
                    && block.getRelative(0, 1, 0).getType().isAir()
                    && block.getState() instanceof Chest chest
                    && chest.getLootTable() == null && chest.getBlockInventory().isEmpty();
        }
        return mode == RewardMode.DROPS && block.getType().isAir()
                && block.getRelative(0, 1, 0).getType().isAir()
                && block.getRelative(0, -1, 0).getType().isOccluding();
    }
}
