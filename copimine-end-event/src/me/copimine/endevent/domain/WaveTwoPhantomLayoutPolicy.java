package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;

/** Stable randomized positions for the server-owned Wave 2 phantom presentation. */
public final class WaveTwoPhantomLayoutPolicy {
    public static final int TARGET_PHANTOM_COUNT = 70;
    /** The 11-November contract matches the client: seventy preferred, fifty safe fallback. */
    public static final int MIN_PHANTOM_COUNT = 50;
    private static final int MAX_CANDIDATE_ATTEMPTS = TARGET_PHANTOM_COUNT * 10;
    private static final double MAX_FIELD_RADIUS = 96.0D;

    private WaveTwoPhantomLayoutPolicy() { }

    /**
     * Resolve deterministic candidates through the adapter's real arena and clearance checks.
     * Invalid candidates are skipped, resolved duplicates collapse, and IDs follow the final order.
     */
    public static List<Phantom> selectSafe(Position anchor, long seed, double fieldRadius,
                                           Function<Position, Position> safeResolver) {
        if (!finiteWorldPosition(anchor) || safeResolver == null
                || !Double.isFinite(fieldRadius) || fieldRadius <= 0.0D) return List.of();
        double radius = Math.min(MAX_FIELD_RADIUS, fieldRadius);
        double innerRadius = Math.min(radius * 0.35D, Math.max(1.0D, radius * 0.18D));
        double innerRadiusSquared = innerRadius * innerRadius;
        double radiusSquared = radius * radius;
        // Phantom positions must look scattered across the whole arena, so the angle and the
        // radius are both drawn uniformly - no rings, no spokes. Uniform sampling alone still
        // lets two candidates land almost on top of each other, and a pair standing shoulder to
        // shoulder reads as a "cluster" instead of a field of separated figures. Enforce a
        // floor on the horizontal separation and simply reject candidates that violate it.
        //
        // The floor scales with the field: a ring of this area holds TARGET_PHANTOM_COUNT
        // points at roughly sqrt(area / TARGET_PHANTOM_COUNT) spacing, so half of that is the
        // tightest gap that still leaves the field looking evenly scattered. The attempt cap is
        // generous because rejection sampling can retry, and the layout is allowed to fall back
        // to MIN_PHANTOM_COUNT when the arena is cramped.
        double minSeparation = minimumSeparation(radius, innerRadius);
        double minSeparationSquared = minSeparation * minSeparation;
        SplittableRandom random = new SplittableRandom(mixSeed(seed));
        Set<Position> distinct = new LinkedHashSet<>(TARGET_PHANTOM_COUNT);
        for (int attempt = 0; attempt < MAX_CANDIDATE_ATTEMPTS
                && distinct.size() < TARGET_PHANTOM_COUNT; attempt++) {
            double distance = StrictMath.sqrt(innerRadiusSquared
                    + random.nextDouble() * (radiusSquared - innerRadiusSquared));
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            Position candidate = new Position(
                    anchor.x() + StrictMath.cos(angle) * distance,
                    anchor.y(),
                    anchor.z() + StrictMath.sin(angle) * distance);
            if (!finiteWorldPosition(candidate)) continue;
            Position resolved = safeResolver.apply(candidate);
            if (!finiteWorldPosition(resolved)) continue;
            double resolvedX = resolved.x() - anchor.x();
            double resolvedZ = resolved.z() - anchor.z();
            if (resolvedX * resolvedX + resolvedZ * resolvedZ > radiusSquared) continue;
            if (tooClose(resolved, distinct, minSeparationSquared)) continue;
            distinct.add(resolved);
        }
        if (distinct.isEmpty()) return List.of();
        List<Phantom> result = new ArrayList<>(distinct.size());
        int index = 0;
        for (Position position : distinct) {
            result.add(new Phantom(String.format(Locale.ROOT, "phantom-%02d", index++), position));
        }
        return List.copyOf(result);
    }

    /**
     * Tightest allowed horizontal gap between two phantom figures.
     *
     * A ring of this area holds {@link #TARGET_PHANTOM_COUNT} points at roughly
     * {@code sqrt(area / count)} spacing; half of that leaves the field looking scattered while
     * still leaving room to place every figure. The result is clamped so a crowded arena does not
     * end up demanding more separation than it can provide.
     */
    static double minimumSeparation(double radius, double innerRadius) {
        double area = StrictMath.PI * (radius * radius - innerRadius * innerRadius);
        if (!(area > 0.0D)) return 0.0D;
        double evenSpacing = StrictMath.sqrt(area / TARGET_PHANTOM_COUNT);
        return evenSpacing * 0.5D;
    }

    private static boolean tooClose(Position candidate, Set<Position> accepted, double minSquared) {
        if (minSquared <= 0.0D) return false;
        for (Position taken : accepted) {
            double dx = candidate.x() - taken.x();
            double dz = candidate.z() - taken.z();
            if (dx * dx + dz * dz < minSquared) return true;
        }
        return false;
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
