package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.PhysicalGateTransaction;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The only dungeon class that reads/writes real blocks. It adapts a live
 * {@link World} to the pure {@link PhysicalGateTransaction.GateWorld} contract so
 * the gate snapshot/open/close logic stays testable without a server.
 */
public final class BukkitGateWorld implements PhysicalGateTransaction.GateWorld {

    private final World world;

    public BukkitGateWorld(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    @Override
    public boolean chunkLoaded(int x, int y, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override
    public String blockKey(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData().getAsString();
    }

    @Override
    public void setBlock(int x, int y, int z, String key) {
        Block block = world.getBlockAt(x, y, z);
        block.setBlockData(Bukkit.createBlockData(key), false);
    }
}
