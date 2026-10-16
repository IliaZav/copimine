package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Collision and target gates for the player-only Void Burst impact. */
public final class EliteProjectileImpactPolicy {
    public static final double MAX_VOID_BURST_RADIUS_BLOCKS = 3.0D;
    public static final double MAX_VOID_BURST_DAMAGE = 4.0D;

    private EliteProjectileImpactPolicy() { }

    public record Impact(boolean playerCollision, boolean eligiblePlayerCollision,
                         boolean blockCollision, boolean cancelled, boolean currentSession) {
        public boolean accepted() {
            return currentSession && !cancelled
                    && (blockCollision && !playerCollision
                    || playerCollision && eligiblePlayerCollision);
        }
    }

    /** Adapter for the Bukkit candidate checks and normal player damage event. */
    public interface Target {
        String id();
        boolean isPlayer();
        boolean isEligible();
        boolean isVisible();
        double distanceSquared();
        void damage(double amount);
    }

    /**
     * Apply one bounded impact transaction. The Bukkit adapter supplies only
     * candidates from the impact world and delegates each hit to Player#damage.
     */
    public static int applyVoidBurst(Impact impact, Collection<? extends Target> targets,
                                     double radius, double damage) {
        if (impact == null || !impact.accepted() || targets == null
                || !Double.isFinite(radius) || radius <= 0.0D
                || radius > MAX_VOID_BURST_RADIUS_BLOCKS
                || !Double.isFinite(damage) || damage <= 0.0D
                || damage > MAX_VOID_BURST_DAMAGE) {
            return 0;
        }
        double radiusSquared = radius * radius;
        Set<String> damagedTargets = new HashSet<>();
        int affected = 0;
        for (Target target : targets) {
            if (target == null || !target.isPlayer() || !target.isEligible()
                    || !target.isVisible() || !Double.isFinite(target.distanceSquared())
                    || target.distanceSquared() < 0.0D
                    || target.distanceSquared() > radiusSquared
                    || target.id() == null || !damagedTargets.add(target.id())) {
                continue;
            }
            target.damage(damage);
            affected++;
        }
        return affected;
    }
}
