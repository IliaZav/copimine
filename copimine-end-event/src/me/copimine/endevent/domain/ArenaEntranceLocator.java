package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Arena-relative entrance geometry; the caller must validate real floor and collision. */
public final class ArenaEntranceLocator {
    private static final int[] EDGE_OFFSETS = {0, -2, 2, -4, 4};
    private static final int[] HEIGHT_OFFSETS = {0, -1, 1, -2, 2, -3, 3, -4, 4};
    private static final int MAX_WORLD_COORDINATE = 30_000_000;

    public record Bounds(String world, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {
        public Bounds {
            if (world == null || world.isBlank() || minX > maxX || minY > maxY || minZ > maxZ
                    || minX < -MAX_WORLD_COORDINATE + 2 || maxX > MAX_WORLD_COORDINATE - 2
                    || minZ < -MAX_WORLD_COORDINATE + 2 || maxZ > MAX_WORLD_COORDINATE - 2
                    || minY < -4096 || maxY > 4096) {
                throw new IllegalArgumentException("valid configured arena bounds required");
            }
        }
    }

    /** Block centre is used horizontally; y is the standing feet height. */
    public record Site(int x, int y, int z, float yaw) { }

    @FunctionalInterface
    public interface SiteProbe {
        boolean safe(String world, Site site);
    }

    private ArenaEntranceLocator() { }

    /**
     * Probe at most twenty nearby perimeter columns and nine heights per column.
     * Nothing inside the arena, its Core, or a different world is a fallback.
     * The predicate runs synchronously so the Bukkit adapter owns collision checks.
     */
    public static Optional<Site> find(Bounds arena, int preferredFeetY, SiteProbe probe) {
        if (arena == null || probe == null) return Optional.empty();
        int centreX = arena.minX() + (arena.maxX() - arena.minX()) / 2;
        int centreZ = arena.minZ() + (arena.maxZ() - arena.minZ()) / 2;
        int feetY = Math.max(arena.minY(), Math.min(arena.maxY(), preferredFeetY));
        Set<Site> candidates = new LinkedHashSet<>();
        for (int side = 0; side < 4; side++) {
            for (int offset : EDGE_OFFSETS) {
                int x = switch (side) {
                    case 2 -> arena.minX() - 2;
                    case 3 -> arena.maxX() + 2;
                    default -> Math.max(arena.minX(), Math.min(arena.maxX(), centreX + offset));
                };
                int z = switch (side) {
                    case 0 -> arena.minZ() - 2;
                    case 1 -> arena.maxZ() + 2;
                    default -> Math.max(arena.minZ(), Math.min(arena.maxZ(), centreZ + offset));
                };
                float yaw = switch (side) { case 1 -> 180F; case 2 -> -90F; case 3 -> 90F; default -> 0F; };
                for (int height : HEIGHT_OFFSETS) {
                    int y = feetY + height;
                    if (y >= arena.minY() && y <= arena.maxY()) candidates.add(new Site(x, y, z, yaw));
                }
            }
        }
        for (Site site : candidates) if (probe.safe(arena.world(), site)) return Optional.of(site);
        return Optional.empty();
    }
}
