package me.copimine.endevent.domain;

import java.util.UUID;

/**
 * Pure fail-closed validation for the dedicated Wave 6 sphere projectile.
 *
 * <p>The older ritual marker is shared by several ritual arrow effects.  The
 * dedicated sphere barrage therefore needs this stricter second boundary:
 * every sphere arrow must carry a finite origin, an owner matching its
 * logical shooter, an immutable intended target matching the hit, and a
 * future expiry tick.</p>
 */
public final class RitualSphereProjectileProvenancePolicy {
    private RitualSphereProjectileProvenancePolicy() {
    }

    public static boolean accepts(boolean ritualMarker,
                                  UUID owner,
                                  UUID shooter,
                                  UUID intendedTarget,
                                  UUID hitTarget,
                                  Vec3 origin,
                                  long expiresAtTick,
                                  long nowTick) {
        return ritualMarker
                && owner != null
                && shooter != null
                && owner.equals(shooter)
                && intendedTarget != null
                && hitTarget != null
                && intendedTarget.equals(hitTarget)
                && finite(origin)
                && expiresAtTick > nowTick;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x())
                && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    public record Vec3(double x, double y, double z) {
    }
}
