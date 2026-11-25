package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.PhysicalGatePolicy;
import me.copimine.dungeon.domain.PhysicalGateTransaction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.util.BoundingBox;

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
    public boolean mutable(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        Block block = world.getBlockAt(x, y, z);
        return !(block.getState() instanceof TileState)
                && block.getType() != org.bukkit.Material.BEDROCK
                && block.getType() != org.bukkit.Material.BARRIER
                && block.getType() != org.bukkit.Material.END_PORTAL
                && block.getType() != org.bukkit.Material.END_PORTAL_FRAME
                && block.getType() != org.bukkit.Material.NETHER_PORTAL;
    }

    @Override
    public boolean blocked(Cuboid bounds) {
        BoundingBox box = new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        return !world.getNearbyEntities(box, entity -> entity instanceof org.bukkit.entity.LivingEntity
                || entity instanceof org.bukkit.entity.Vehicle).isEmpty();
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

    public void animateOpening(Plugin plugin, GateDefinition gate, List<String> keys, Set<UUID> owned) {
        List<int[]> mask = PhysicalGatePolicy.doorMask(gate);
        List<BlockDisplay> displays = new ArrayList<>();
        int stride = Math.max(1, (mask.size() + 63) / 64);
        int duration = 16;
        for (int i = 0; i < mask.size(); i += stride) {
            int[] cell = mask.get(i);
            var block = Bukkit.createBlockData(keys.get(i));
            if (block.getMaterial().isAir()) {
                continue;
            }
            Location at = new Location(world, cell[0], cell[1], cell[2]);
            BlockDisplay display = world.spawn(at, BlockDisplay.class, item -> {
                item.setPersistent(false);
                item.setBlock(block);
                item.setInterpolationDuration(duration);
            });
            displays.add(display);
            owned.add(display.getUniqueId());
        }
        world.playSound(new Location(world, gate.bounds().minX(), gate.bounds().minY(), gate.bounds().minZ()),
                Sound.BLOCK_PISTON_EXTEND, 0.9f, 0.65f);
        if (displays.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay display : displays) {
                if (!owned.contains(display.getUniqueId()) || !display.isValid()) {
                    continue;
                }
                Vector3f shift = animationOffset(gate, display.getLocation());
                float scale = switch (gate.style()) {
                    case COLLAPSING_BARRIER, ASSEMBLING_SEAL -> 0.08f;
                    default -> 1.0f;
                };
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(shift, new Quaternionf(),
                        new Vector3f(scale), new Quaternionf()));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay display : displays) {
                if (owned.remove(display.getUniqueId())) {
                    display.remove();
                }
            }
        }, duration + 2L);
    }

    private static Vector3f animationOffset(GateDefinition gate, Location at) {
        Cuboid bounds = gate.bounds();
        return switch (gate.style()) {
            case COLLAPSING_BARRIER -> new Vector3f(0.35f, -1.35f, -0.25f);
            case ASSEMBLING_SEAL -> new Vector3f(0.45f, 0.45f, 0.45f);
            case SECRET_DISGUISED -> new Vector3f(0, -bounds.sizeY(), 0);
            case RISING_PORTCULLIS, LIFTING_STONE_WALL -> new Vector3f(0, bounds.sizeY(), 0);
            case SPLIT_SLIDING_WALL -> {
                boolean alongX = bounds.sizeX() >= bounds.sizeZ();
                double min = alongX ? bounds.minX() : bounds.minZ();
                double max = alongX ? bounds.maxX() : bounds.maxZ();
                double position = alongX ? at.getX() : at.getZ();
                float shift = (float) ((max - min + 1) * (position <= (min + max) * 0.5 ? -0.5 : 0.5));
                yield alongX ? new Vector3f(shift, 0, 0) : new Vector3f(0, 0, shift);
            }
        };
    }

}
