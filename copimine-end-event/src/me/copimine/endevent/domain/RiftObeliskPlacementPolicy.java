package me.copimine.endevent.domain;

import java.util.List;

/** Stable radial points used before the Bukkit safety checks choose a floor. */
public final class RiftObeliskPlacementPolicy {
    public static final double MIN_CORE_DISTANCE = 4.0D;
    public static final double MAX_ARENA_RADIUS = 19.0D;

    private static final List<Point> CANDIDATES = List.of(
            new Point(7, 0),
            new Point(-7, 0),
            new Point(0, 7),
            new Point(0, -7));

    private RiftObeliskPlacementPolicy() {
    }

    public static List<Point> candidates(int requested) {
        int count = Math.max(0, Math.min(RiftObeliskScalingPolicy.MAX_ACTIVE, requested));
        return CANDIDATES.subList(0, count);
    }

    public record Point(int xOffset, int zOffset) {
        public double horizontalDistance() {
            return Math.sqrt((double) xOffset * xOffset + (double) zOffset * zOffset);
        }
    }
}
