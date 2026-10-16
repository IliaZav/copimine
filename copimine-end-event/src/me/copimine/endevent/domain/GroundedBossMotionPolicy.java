package me.copimine.endevent.domain;

/**
 * Resolves the small movement invariants that keep the guardian grounded.
 *
 * <p>Navigation and collision remain Paper responsibilities. This policy only
 * decides whether a normal pursuit may carry a vertical impulse and which of
 * the supplied locomotion clips is visible to clients.</p>
 */
public final class GroundedBossMotionPolicy {
    /** A movement smaller than this is visually idle rather than a walk cycle. */
    public static final double RUN_HORIZONTAL_SPEED_THRESHOLD = 0.06D;

    private GroundedBossMotionPolicy() {
    }

    public static Resolution resolve(boolean ordinaryPursuit, boolean onGround,
                                     double horizontalSpeed, double currentVerticalVelocity) {
        double safeHorizontal = Double.isFinite(horizontalSpeed)
                ? Math.max(0.0D, horizontalSpeed) : 0.0D;
        double safeVertical = Double.isFinite(currentVerticalVelocity)
                ? currentVerticalVelocity : 0.0D;
        // The guardian has no authored jump/fly state. Clear upward impulses
        // during ordinary pursuit, but preserve downward velocity while it is
        // airborne so Paper gravity can bring it back to the arena floor. Zeroing
        // Y on every pursuit tick cancels gravity and leaves it suspended.
        // Scripted casts/teleports retain their explicit vertical state.
        double appliedVertical = !ordinaryPursuit ? safeVertical
                : onGround ? 0.0D : Math.min(0.0D, safeVertical);
        BossAnimationId animation = ordinaryPursuit
                && safeHorizontal >= RUN_HORIZONTAL_SPEED_THRESHOLD
                ? BossAnimationId.RUN : BossAnimationId.IDLE_BREATH;
        // Ordinary pathing never receives an escape teleport. Named encounter
        // relocations are explicit server actions outside this policy.
        return new Resolution(false, appliedVertical, animation);
    }

    public record Resolution(boolean allowTeleport, double appliedVerticalVelocity,
                             BossAnimationId animation) {
        public Resolution {
            appliedVerticalVelocity = Double.isFinite(appliedVerticalVelocity)
                    ? appliedVerticalVelocity : 0.0D;
            animation = animation == null ? BossAnimationId.IDLE_BREATH : animation;
        }
    }
}
