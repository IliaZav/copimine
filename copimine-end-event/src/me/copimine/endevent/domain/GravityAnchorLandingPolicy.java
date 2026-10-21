package me.copimine.endevent.domain;

/** Pure eligibility rules for Wave 6 Gravity Anchor lift and landing damage. */
public final class GravityAnchorLandingPolicy {
    private GravityAnchorLandingPolicy() { }

    public static boolean shouldApplyImpulse(boolean projectedWithinArena,
                                             boolean movementPathClear) {
        return projectedWithinArena && movementPathClear;
    }

    public static boolean shouldRecordLift(boolean lineOfSight,
                                           boolean movementPathClear,
                                           double appliedVerticalVelocity) {
        return lineOfSight && movementPathClear
                && Double.isFinite(appliedVerticalVelocity)
                && appliedVerticalVelocity > 0.0D;
    }

    public static boolean shouldApplyLandingDamage(boolean liftedByAnchor,
                                                   boolean onGround,
                                                   boolean landingAlreadyResolved) {
        return liftedByAnchor && onGround && !landingAlreadyResolved;
    }

    public static boolean shouldSuppressVanillaFallDamage(boolean fallDamage,
                                                          boolean liftedByAnchor,
                                                          boolean landingAlreadyResolved) {
        return fallDamage && liftedByAnchor && !landingAlreadyResolved;
    }
}
