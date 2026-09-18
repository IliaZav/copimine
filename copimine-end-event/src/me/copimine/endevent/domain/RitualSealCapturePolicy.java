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
            return candidate.playerId();
        }
        return null;
    }

    public record Candidate(UUID playerId, boolean eligible, double x, double z) {
        public Candidate {
            if (playerId == null || !Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("invalid ritual seal candidate");
            }
        }
    }
}
