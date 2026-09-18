package me.copimine.endevent.domain;

/**
 * Pure swept-collision math for fast event-owned Rift Fireballs.
 *
 * <p>The Paper projectile is advanced by the server in larger steps than the
 * event runtime's bounded five-tick sampler.  Checking only the current point
 * can therefore tunnel through a real obelisk.  This policy tests the segment
 * between the previous and current positions while keeping the obelisk's
 * horizontal radius and vertical block range explicit.</p>
 */
public final class RiftFireballCollisionPolicy {
    private RiftFireballCollisionPolicy() {
    }

    /**
     * Return whether a segment intersects a vertical cylinder representing an
     * obelisk.  The vertical bounds are inclusive and are normalized so a
     * recovered/runtime record with reversed bounds remains safe.
     */
    public static boolean segmentIntersectsObelisk(double fromX, double fromY, double fromZ,
                                                   double toX, double toY, double toZ,
                                                   double centerX, double centerY, double centerZ,
                                                   double horizontalRadius,
                                                   double minimumY, double maximumY) {
        if (!allFinite(fromX, fromY, fromZ, toX, toY, toZ,
                centerX, centerY, centerZ, horizontalRadius, minimumY, maximumY)
                || horizontalRadius < 0.0D) {
            return false;
        }
        double lowY = Math.min(minimumY, maximumY);
        double highY = Math.max(minimumY, maximumY);
        double deltaY = toY - fromY;
        double lowT = 0.0D;
        double highT = 1.0D;
        if (Math.abs(deltaY) < 1.0E-9D) {
            if (fromY < lowY || fromY > highY) {
                return false;
            }
        } else {
            double first = (lowY - fromY) / deltaY;
            double second = (highY - fromY) / deltaY;
            lowT = Math.max(lowT, Math.min(first, second));
            highT = Math.min(highT, Math.max(first, second));
            if (lowT > highT) {
                return false;
            }
        }

        double deltaX = toX - fromX;
        double deltaZ = toZ - fromZ;
        double horizontalLengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        double candidateT;
        if (horizontalLengthSquared < 1.0E-12D) {
            candidateT = lowT;
        } else {
            candidateT = ((centerX - fromX) * deltaX + (centerZ - fromZ) * deltaZ)
                    / horizontalLengthSquared;
            candidateT = Math.max(lowT, Math.min(highT, candidateT));
        }
        double closestX = fromX + deltaX * candidateT;
        double closestZ = fromZ + deltaZ * candidateT;
        double radiusSquared = horizontalRadius * horizontalRadius;
        double distanceSquared = (closestX - centerX) * (closestX - centerX)
                + (closestZ - centerZ) * (closestZ - centerZ);
        return distanceSquared <= radiusSquared + 1.0E-9D;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
