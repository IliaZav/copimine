package me.copimine.endevent.runtime.ritual;

import java.util.List;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;

/** Confirms a Wave 6 Anvil impact only after every visual reaches its locked point. */
public final class FallingAnvilImpactConfirmation {
    private static final double POSITION_TOLERANCE = 0.01D;

    public enum Outcome {
        PENDING,
        CONFIRMED,
        FAILED
    }

    /** Result of the server's latest attempt to move one visual to its landing point. */
    public record VisualPosition(boolean teleportSucceeded,
                                 boolean expectedWorld,
                                 double x,
                                 double y,
                                 double z) {
    }

    private FallingAnvilImpactConfirmation() { }

    /**
     * Keeps a moving volley pending until its finite drop deadline, then fails any
     * impact that did not have a successful observed landing for every visual.
     */
    public static Outcome evaluate(double progress,
                                   List<LandingPoint> lockedPositions,
                                   List<VisualPosition> actualPositions) {
        if (!Double.isFinite(progress)) return Outcome.FAILED;
        if (progress < 1.0D) return Outcome.PENDING;
        return isConfirmed(progress, lockedPositions, actualPositions)
                ? Outcome.CONFIRMED : Outcome.FAILED;
    }

    public static boolean isConfirmed(double progress,
                                      List<LandingPoint> lockedPositions,
                                      List<VisualPosition> actualPositions) {
        if (!Double.isFinite(progress) || progress < 1.0D
                || lockedPositions == null || lockedPositions.isEmpty()
                || actualPositions == null || actualPositions.size() != lockedPositions.size()) {
            return false;
        }
        double toleranceSquared = POSITION_TOLERANCE * POSITION_TOLERANCE;
        for (int index = 0; index < lockedPositions.size(); index++) {
            LandingPoint locked = lockedPositions.get(index);
            VisualPosition actual = actualPositions.get(index);
            if (locked == null || actual == null || !actual.teleportSucceeded()
                    || !actual.expectedWorld()
                    || !Double.isFinite(actual.x()) || !Double.isFinite(actual.y())
                    || !Double.isFinite(actual.z())) {
                return false;
            }
            double dx = actual.x() - locked.x();
            double dy = actual.y() - locked.y();
            double dz = actual.z() - locked.z();
            if (dx * dx + dy * dy + dz * dz > toleranceSquared) return false;
        }
        return true;
    }
}
