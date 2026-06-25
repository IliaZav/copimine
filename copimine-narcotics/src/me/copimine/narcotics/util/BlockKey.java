package me.copimine.narcotics.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
