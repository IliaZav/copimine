package me.copimine.endevent.domain;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Deterministic bounded capture of free players affected by a Wave 6 Gravity Anchor. */
public final class GravityAnchorCohortPolicy {
    public static final double RADIUS_BLOCKS = 5.0D;
    public static final int MAX_COHORT_SIZE = 20;
    private static final double HORIZONTAL_RADIUS_SQUARED = RADIUS_BLOCKS * RADIUS_BLOCKS;

    /** Runtime facts sampled when the fixed Crystal pull begins; radius uses X/Z only. */
    public record Candidate(UUID player,
                            double x,
                            double y,
                            double z,
                            boolean eligibleFreePlayer,
                            boolean sameWorld,
                            boolean lineOfSight) {
        public Candidate {
            if (player == null || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z)) {
                throw new IllegalArgumentException("finite player identity and position required");
            }
        }
    }

    private GravityAnchorCohortPolicy() { }

    /** Select each eligible visible player once, in UUID order, within the horizontal five-block radius. */
    public static List<UUID> capture(GravityAnchorPolicy.AnchorPoint anchor,
                                    List<Candidate> candidates) {
        if (anchor == null || candidates == null || candidates.isEmpty()) return List.of();
        TreeSet<UUID> selected = new TreeSet<>(Comparator.comparing(UUID::toString));
        for (Candidate candidate : candidates) {
            if (candidate == null || !candidate.eligibleFreePlayer()
                    || !candidate.sameWorld() || !candidate.lineOfSight()) continue;
            double dx = candidate.x() - anchor.x();
            double dz = candidate.z() - anchor.z();
            if (dx * dx + dz * dz <= HORIZONTAL_RADIUS_SQUARED) {
                selected.add(candidate.player());
            }
        }
        return selected.stream().limit(MAX_COHORT_SIZE).toList();
    }
}
