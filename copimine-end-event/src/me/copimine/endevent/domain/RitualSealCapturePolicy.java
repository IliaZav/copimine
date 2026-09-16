package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/** Selects a physically present eligible participant for the Wave 6 seal. */
public final class RitualSealCapturePolicy {
    public static final double CAPTURE_RADIUS_BLOCKS = 1.25D;

    private RitualSealCapturePolicy() {
    }

    public static UUID select(List<Candidate> candidates, double sealX, double sealZ) {
        if (candidates == null || !Double.isFinite(sealX) || !Double.isFinite(sealZ)) {
            return null;
        }
        double radiusSquared = CAPTURE_RADIUS_BLOCKS * CAPTURE_RADIUS_BLOCKS;
        Candidate selected = null;
        double selectedDistanceSquared = 0.0D;
        for (Candidate candidate : candidates) {
            if (candidate == null || !candidate.eligible()) {
                continue;
            }
            double dx = candidate.x() - sealX;
            double dz = candidate.z() - sealZ;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
                selectedDistanceSquared = distanceSquared;
                continue;
            }
            // The caller supplies the deterministic participant order.  If
            // two eligible participants entered on the same boundary tick at
            // the same distance, UUID order makes the capture deterministic
            // without allowing an outside UUID to win first.
            if (Double.compare(distanceSquared, selectedDistanceSquared) == 0
                    && candidate.playerId().toString().compareTo(
                    selected.playerId().toString()) < 0) {
                selected = candidate;
            }
        }
        return selected == null ? null : selected.playerId();
    }

    public record Candidate(UUID playerId, boolean eligible, double x, double z) {
        public Candidate {
            if (playerId == null || !Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("invalid ritual seal candidate");
            }
        }
    }
}
