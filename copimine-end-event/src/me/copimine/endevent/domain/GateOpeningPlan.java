package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable, bounded coordinate plan for removing a gate one horizontal layer
 * at a time. It contains no Bukkit state and is therefore safe to test before
 * a Paper world is touched.
 */
public final class GateOpeningPlan {
    private final Point first;
    private final Point second;
    private final long volume;
    private final List<Layer> layersDescending;

    private GateOpeningPlan(Point first, Point second, long volume, List<Layer> layersDescending) {
        this.first = first;
        this.second = second;
        this.volume = volume;
        this.layersDescending = List.copyOf(layersDescending);
    }

    public static GateOpeningPlan from(Point first, Point second, long maxVolume) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("gate points are required");
        }
        if (first.world() == null || second.world() == null
                || first.world().isBlank() || !first.world().equalsIgnoreCase(second.world())) {
            throw new IllegalArgumentException("gate points must be in one world");
        }
        if (maxVolume < 1L) {
            throw new IllegalArgumentException("gate volume ceiling must be positive");
        }
        long width = Math.abs((long) first.x() - second.x()) + 1L;
        long height = Math.abs((long) first.y() - second.y()) + 1L;
        long depth = Math.abs((long) first.z() - second.z()) + 1L;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(width, height), depth);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("gate volume overflows the bounded range", overflow);
        }
        if (volume > maxVolume) {
            throw new IllegalArgumentException("gate volume exceeds the bounded ceiling");
        }

        int minX = Math.min(first.x(), second.x());
        int maxX = Math.max(first.x(), second.x());
        int minY = Math.min(first.y(), second.y());
        int maxY = Math.max(first.y(), second.y());
        int minZ = Math.min(first.z(), second.z());
        int maxZ = Math.max(first.z(), second.z());
        List<Layer> layers = new ArrayList<>((int) height);
        for (int y = maxY; y >= minY; y--) {
            List<Point> blocks = new ArrayList<>((int) (width * depth));
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(new Point(first.world(), x, y, z));
                }
            }
            blocks.sort(Comparator.comparingInt(Point::x).thenComparingInt(Point::z));
            layers.add(new Layer(y, blocks));
        }
        return new GateOpeningPlan(first, second, volume, layers);
    }

    public Point first() {
        return first;
    }

    public Point second() {
        return second;
    }

    public long volume() {
        return volume;
    }

    public List<Layer> layersDescending() {
        return layersDescending;
    }

    public record Point(String world, int x, int y, int z) {
    }

    public record Layer(int y, List<Point> blocks) {
        public Layer {
            blocks = List.copyOf(blocks);
        }
    }
}
