package me.copimine.endevent.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Pure candidate selection for the Rift Guardian movement controller.
 * Bukkit/world inspection stays in the adapter; this class only scores the
 * already bounded candidates and rejects unsafe destinations.
 */
public final class BossMovementPolicy {
    private BossMovementPolicy() {
    }

    public static Candidate chooseSafeDestination(Anchor anchor, Target target,
                                                   List<Candidate> candidates,
                                                   double radius, double verticalRadius,
                                                   double minCoreDistance,
                                                   Set<String> blockedMaterials) {
        if (anchor == null || !finite(anchor.x()) || !finite(anchor.y()) || !finite(anchor.z())
                || !finite(radius) || radius <= 0.0D || !finite(verticalRadius) || verticalRadius < 0.0D
                || !finite(minCoreDistance) || minCoreDistance < 0.0D) {
            return null;
        }
        Set<String> blocked = blockedMaterials == null ? Set.of() : blockedMaterials.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return (candidates == null ? List.<Candidate>of() : candidates).stream()
                .filter(candidate -> safe(anchor, candidate, radius, verticalRadius, minCoreDistance, blocked))
                .min(Comparator.<Candidate>comparingDouble(candidate -> score(anchor, target, candidate, radius))
                        .thenComparing(Candidate::id, Comparator.nullsFirst(String::compareTo)))
                .orElse(null);
    }

    /** Pick a deterministic outer-ring position when the boss is stuck. */
    public static Candidate chooseStuckFallback(Anchor anchor, List<Candidate> candidates,
                                                double radius, double verticalRadius,
                                                double minCoreDistance, Set<String> blockedMaterials) {
        if (anchor == null) {
            return null;
        }
        Set<String> blocked = blockedMaterials == null ? Set.of() : blockedMaterials;
        return (candidates == null ? List.<Candidate>of() : candidates).stream()
                .filter(candidate -> safe(anchor, candidate, radius, verticalRadius, minCoreDistance,
                        blocked.stream().filter(value -> value != null).map(value -> value.toUpperCase(java.util.Locale.ROOT))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .max(Comparator.<Candidate>comparingDouble(candidate -> horizontalDistanceSquared(anchor, candidate))
                        .thenComparing(Candidate::id, Comparator.nullsFirst(String::compareTo).reversed()))
                .orElse(null);
    }

    public static double horizontalDistance(Anchor anchor, Candidate candidate) {
        return Math.sqrt(horizontalDistanceSquared(anchor, candidate));
    }

    private static boolean safe(Anchor anchor, Candidate candidate, double radius, double verticalRadius,
                                double minCoreDistance, Set<String> blockedMaterials) {
        if (candidate == null || candidate.id() == null || candidate.id().isBlank()
                || !finite(candidate.x()) || !finite(candidate.y()) || !finite(candidate.z())
                || !candidate.feetPassable() || !candidate.headPassable() || !candidate.floorSolid()
                || candidate.liquid() || candidate.fire() || candidate.web() || candidate.temporary()
                || candidate.core()) {
            return false;
        }
        double distanceSquared = horizontalDistanceSquared(anchor, candidate);
        return distanceSquared <= radius * radius
                && distanceSquared >= minCoreDistance * minCoreDistance
                && Math.abs(candidate.y() - anchor.y()) <= verticalRadius
                && (candidate.material() == null
                || !blockedMaterials.contains(candidate.material().trim().toUpperCase(java.util.Locale.ROOT)));
    }

    private static double score(Anchor anchor, Target target, Candidate candidate, double radius) {
        double distance = Math.sqrt(horizontalDistanceSquared(anchor, candidate));
        double targetDistance = target == null ? radius - distance : distanceSquared(target, candidate);
        // Lower is better.  A small target distance creates pressure, while
        // the ring term prevents the controller from collapsing back to Core.
        double ringPenalty = Math.abs(Math.max(3.5D, radius * 0.55D) - distance) * 0.08D;
        return targetDistance + ringPenalty;
    }

    private static double horizontalDistanceSquared(Anchor anchor, Candidate candidate) {
        double dx = anchor.x() - candidate.x();
        double dz = anchor.z() - candidate.z();
        return dx * dx + dz * dz;
    }

    private static double distanceSquared(Target target, Candidate candidate) {
        double dx = target.x() - candidate.x();
        double dy = target.y() - candidate.y();
        double dz = target.z() - candidate.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public record Anchor(double x, double y, double z) {
    }

    public record Target(double x, double y, double z) {
    }

    public record Candidate(String id, double x, double y, double z,
                            boolean feetPassable, boolean headPassable, boolean floorSolid,
                            boolean liquid, boolean fire, boolean web, boolean temporary,
                            boolean core, String material) {
        public Candidate withFire(boolean value) {
            return new Candidate(id, x, y, z, feetPassable, headPassable, floorSolid,
                    liquid, value, web, temporary, core, material);
        }

        public Candidate withMaterial(String value) {
            return new Candidate(id, x, y, z, feetPassable, headPassable, floorSolid,
                    liquid, fire, web, temporary, core, value);
        }
    }
}
