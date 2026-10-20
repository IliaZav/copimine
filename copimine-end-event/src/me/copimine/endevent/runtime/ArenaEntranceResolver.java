package me.copimine.endevent.runtime;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import me.copimine.endevent.domain.ArenaEntranceLocator;
import me.copimine.endevent.domain.ArenaEntranceLocator.Bounds;
import me.copimine.endevent.domain.ArenaEntranceLocator.Site;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

/** Synchronous, loaded-chunk-only physical validation for an arena entrance. */
public final class ArenaEntranceResolver {
    private static final Set<String> UNSAFE = Set.of(
            "LAVA", "WATER", "BUBBLE_COLUMN", "MAGMA_BLOCK", "FIRE", "SOUL_FIRE",
            "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS", "WITHER_ROSE", "SWEET_BERRY_BUSH",
            "POWDER_SNOW", "COBWEB", "POINTED_DRIPSTONE", "NETHER_PORTAL", "END_PORTAL");
    private static final double EPSILON = 0.000001D;

    private ArenaEntranceResolver() { }

    public static Optional<Location> find(World world, Bounds arena, int preferredFeetY,
                                           Predicate<Block> temporaryDanger) {
        if (world == null || arena == null || temporaryDanger == null
                || !arena.world().equals(world.getName())) return Optional.empty();
        return ArenaEntranceLocator.find(arena, preferredFeetY,
                (name, site) -> safe(world, site, temporaryDanger))
                .map(site -> new Location(world, site.x() + .5D, site.y(),
                        site.z() + .5D, site.yaw(), 0F));
    }

    /** Recheck the exact earlier site; never silently move staging to another candidate. */
    public static boolean safe(Location location, Predicate<Block> temporaryDanger) {
        if (location == null || location.getWorld() == null || temporaryDanger == null
                || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())
                || Math.abs(location.getX() - location.getBlockX() - .5D) > EPSILON
                || Math.abs(location.getZ() - location.getBlockZ() - .5D) > EPSILON
                || Math.abs(location.getY() - location.getBlockY()) > EPSILON) return false;
        return safe(location.getWorld(), new Site(location.getBlockX(), location.getBlockY(),
                location.getBlockZ(), location.getYaw()), temporaryDanger);
    }

    private static boolean safe(World world, Site site, Predicate<Block> temporaryDanger) {
        if (site.y() - 1 < world.getMinHeight() || site.y() + 2 >= world.getMaxHeight()
                || !world.isChunkLoaded(site.x() >> 4, site.z() >> 4)) return false;
        for (double x : new double[]{.18D, .82D}) {
            for (double z : new double[]{.18D, .82D}) {
                if (!world.getWorldBorder().isInside(new Location(world,
                        site.x() + x, site.y(), site.z() + z, 0F, 0F))) return false;
            }
        }
        Block floor = world.getBlockAt(site.x(), site.y() - 1, site.z());
        Block feet = world.getBlockAt(site.x(), site.y(), site.z());
        Block head = world.getBlockAt(site.x(), site.y() + 1, site.z());
        if (unsafe(floor, temporaryDanger) || unsafe(feet, temporaryDanger)
                || unsafe(head, temporaryDanger)) return false;
        VoxelShape floorShape = floor.getCollisionShape();
        VoxelShape feetShape = feet.getCollisionShape();
        VoxelShape headShape = head.getCollisionShape();
        if (floorShape.getBoundingBoxes().size() > 16 || feetShape.getBoundingBoxes().size() > 16
                || headShape.getBoundingBoxes().size() > 16) return false;
        // Pinned Paper/CraftBlock exposes voxel boxes in block-local coordinates.
        // Require support beneath the full standing footprint, not only its centre.
        boolean supported = floorShape.getBoundingBoxes().stream().anyMatch(box ->
                box.getMinX() <= .18D && box.getMaxX() >= .82D
                        && box.getMinZ() <= .18D && box.getMaxZ() >= .82D
                        && Math.abs(box.getMaxY() - 1D) <= EPSILON);
        return supported
                && !floorShape.overlaps(body(1D + EPSILON, 2.8D))
                && !feetShape.overlaps(body(EPSILON, 1.8D))
                && !headShape.overlaps(body(-1D + EPSILON, .8D));
    }

    private static BoundingBox body(double minY, double maxY) {
        return new BoundingBox(.18D, minY, .18D, .82D, maxY, .82D);
    }

    private static boolean unsafe(Block block, Predicate<Block> temporaryDanger) {
        return block.isLiquid() || UNSAFE.contains(block.getType().name())
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()
                || temporaryDanger.test(block);
    }
}
