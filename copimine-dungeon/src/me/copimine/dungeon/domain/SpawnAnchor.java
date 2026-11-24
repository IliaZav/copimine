package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * A validated spawn anchor with a small admission radius (spec OV4.4). Anchors
 * are refused when the room is occupied, the floor is solid or the actor would
 * clip a gate; this record only carries the durable definition.
 */
public record SpawnAnchor(String id, String roomId, String world,
                          int x, int y, int z, double radius) {

    public SpawnAnchor {
        id = requireId(id, "spawnId");
        roomId = requireId(roomId, "roomId");
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("У спавн-якоря должен быть мир.");
        }
        if (radius < 0.0D) {
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
