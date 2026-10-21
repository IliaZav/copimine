package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Deterministic candidates for the server-owned stationary Wave 2 phantom presentation. */
public final class WaveTwoPhantomLayoutPolicy {
    public static final int TARGET_PHANTOM_COUNT = 32;
    private static final int CANDIDATES_PER_RING = TARGET_PHANTOM_COUNT;
    private static final double[] RADII = {6.0D, 8.0D, 10.0D, 12.0D};

    private WaveTwoPhantomLayoutPolicy() { }

    /**
     * Resolve deterministic candidates through the adapter's real arena and clearance checks.
     * Invalid candidates are skipped, resolved duplicates collapse, and IDs follow the final order.
     */
    public static List<Phantom> selectSafe(Position anchor, long seed,
                                           Function<Position, Position> safeResolver) {
        if (!finiteWorldPosition(anchor) || safeResolver == null) return List.of();
        double phase = phase(seed);
        Set<Position> distinct = new LinkedHashSet<>(TARGET_PHANTOM_COUNT);
        for (int ring = 0; ring < RADII.length && distinct.size() < TARGET_PHANTOM_COUNT; ring++) {
            double ringOffset = (ring & 1) == 0 ? 0.0D : Math.PI / CANDIDATES_PER_RING;
            for (int index = 0; index < CANDIDATES_PER_RING && distinct.size() < TARGET_PHANTOM_COUNT; index++) {
                double angle = phase + ringOffset + Math.PI * 2.0D * index / CANDIDATES_PER_RING;
                Position candidate = new Position(
                        anchor.x() + StrictMath.cos(angle) * RADII[ring],
                        anchor.y(),
                        anchor.z() + StrictMath.sin(angle) * RADII[ring]);
                if (!finiteWorldPosition(candidate)) continue;
                Position resolved = safeResolver.apply(candidate);
                if (finiteWorldPosition(resolved)) distinct.add(resolved);
            }
        }
        if (distinct.isEmpty()) return List.of();
        List<Phantom> result = new ArrayList<>(distinct.size());
        int index = 0;
        for (Position position : distinct) {
            result.add(new Phantom(String.format(Locale.ROOT, "phantom-%02d", index++), position));
        }
        return List.copyOf(result);
    }

    private static double phase(long seed) {
        long mixed = mixSeed(seed);
        double unit = (mixed >>> 11) * 0x1.0p-53;
        return unit * Math.PI * 2.0D;
    }

    private static long mixSeed(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static boolean finiteWorldPosition(Position position) {
        return position != null && Double.isFinite(position.x())
                && Double.isFinite(position.y()) && Double.isFinite(position.z())
                && Math.abs(position.x()) <= 29_999_998.0D
                && position.y() >= -4_096.0D && position.y() <= 4_096.0D
                && Math.abs(position.z()) <= 29_999_998.0D;
    }

    public record Position(double x, double y, double z) { }

    public record Phantom(String id, Position position) { }
}
