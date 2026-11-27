package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * A validated spawn anchor with a small admission radius (spec OV4.4). Anchors
 * are refused when the room is occupied, the floor is solid or the actor would
 * clip a gate; this record only carries the durable definition.
 */
public record SpawnAnchor(String id, String roomId, String world,
                          int x, int y, int z, double radius, Cuboid zone) {

    public SpawnAnchor(String id, String roomId, String world, int x, int y, int z, double radius) {
        this(id, roomId, world, x, y, z, radius, null);
    }

    public static SpawnAnchor inZone(String id, String roomId, String world, Cuboid zone) {
        int[] center = zone.center();
        return new SpawnAnchor(id, roomId, world, center[0], center[1], center[2], 0, zone);
    }

    public Cuboid bounds() {
        return zone == null ? Cuboid.of(x, y, z, x, y, z) : zone;
    }

    public SpawnAnchor {
        if (zone != null && (!zone.contains(x, y, z) || (long) zone.maxX() - zone.minX() >= 64
                || (long) zone.maxY() - zone.minY() >= 16 || (long) zone.maxZ() - zone.minZ() >= 64)) {
            throw new IllegalArgumentException("Spawn zone must contain its anchor and fit within 64x16x64.");
        }
        id = requireId(id, "spawnId");
        roomId = requireId(roomId, "roomId");
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("У спавн-якоря должен быть мир.");
        }
        if (!Double.isFinite(radius) || radius < 0.0D) {
            throw new IllegalArgumentException("Радиус спавн-якоря не может быть отрицательным.");
        }
    }

    public static SpawnAnchor of(String id, String roomId, String world,
                                 int x, int y, int z, double radius) {
        return new SpawnAnchor(id, roomId, world, x, y, z, radius);
    }

    public int[] position() {
        return new int[]{x, y, z};
    }

    private static String requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Пустой идентификатор: " + label);
        }
        return trimmed;
    }
}
