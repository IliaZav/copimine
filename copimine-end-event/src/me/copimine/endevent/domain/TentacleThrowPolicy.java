package me.copimine.endevent.domain;

import java.util.function.Predicate;

/** Computes a finite, readable launch before a tentacle locks its target. */
public final class TentacleThrowPolicy {
    public static final double HORIZONTAL_SPEED = 2.0D;
    public static final double VERTICAL_SPEED = 0.72D;
    /** Bukkit health points: 10 points deal five hearts before armor/effects. */
    public static final double RELEASE_DAMAGE = 10.0D;
    private static final double HORIZONTAL_AIR_DRAG = 0.91D;
    private static final double VERTICAL_AIR_DRAG = 0.98D;
    private static final double GRAVITY_PER_TICK = 0.08D;
    private static final double MIN_DIRECTION_LENGTH = 0.001D;

    private TentacleThrowPolicy() {
    }

    /**
     * Official damage is restricted to active participants in boss combat;
     * the local texture showroom may exercise the same hit without an event roster.
     */
    public static boolean shouldApplyReleaseDamage(boolean activeBossParticipant,
                                                   boolean officialBossPhase,
                                                   boolean localTestBossShowroom) {
        return activeBossParticipant && officialBossPhase || localTestBossShowroom;
    }

    /** Exactly five hearts of health loss, clamped to the player's current health. */
    public static double healthAfterReleaseDamage(double healthBefore) {
        double before = Double.isFinite(healthBefore) ? Math.max(0.0D, healthBefore) : 0.0D;
        return Math.max(0.0D, before - RELEASE_DAMAGE);
    }

    public static Launch launch(double rootX, double rootZ, double targetX, double targetZ) {
        double safeRootX = finiteOrZero(rootX);
        double safeRootZ = finiteOrZero(rootZ);
        double dx = finiteOrZero(targetX) - safeRootX;
        double dz = finiteOrZero(targetZ) - safeRootZ;
        return launchAlong(dx, dz);
    }

    /** Launch radially away from the arena center so a caught player is sent toward the wall. */
    public static Launch launchTowardArenaEdge(double arenaCenterX, double arenaCenterZ,
                                               double originX, double originZ) {
        double dx = finiteOrZero(originX) - finiteOrZero(arenaCenterX);
        double dz = finiteOrZero(originZ) - finiteOrZero(arenaCenterZ);
        return launchAlong(dx, dz);
    }

    private static Launch launchAlong(double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (!Double.isFinite(length) || length < MIN_DIRECTION_LENGTH) {
            dx = 0.0D;
            dz = 1.0D;
            length = 1.0D;
        }
        return new Launch(dx / length * HORIZONTAL_SPEED, VERTICAL_SPEED,
                dz / length * HORIZONTAL_SPEED);
    }

    /** Approximate the player landing distance using vanilla air drag and gravity. */
    public static double estimatedHorizontalTravel(Launch launch) {
        if (launch == null || !launch.isFinite()) {
            return 0.0D;
        }
        double height = 0.0D;
        double verticalSpeed = launch.y();
        double horizontalSpeed = launch.horizontalLength();
        double distance = 0.0D;
        for (int tick = 0; tick < 40; tick++) {
            distance += horizontalSpeed;
            height += verticalSpeed;
            if (tick > 0 && height <= 0.0D) {
                return distance;
            }
            horizontalSpeed *= HORIZONTAL_AIR_DRAG;
            verticalSpeed = (verticalSpeed - GRAVITY_PER_TICK) * VERTICAL_AIR_DRAG;
        }
        return distance;
    }

    /**
     * Return the strongest candidate whose landing is safe.  Terrain checks
     * are not monotonic with launch strength, so binary search can converge
     * on a tiny impulse even when a stronger, safe landing exists farther
     * along the same ray.
     */
    public static Launch strongestSafeLaunch(Launch launch, Predicate<Launch> landingIsSafe,
                                             double minimumHorizontalSpeed) {
        if (launch == null || !launch.isFinite() || landingIsSafe == null
                || !Double.isFinite(minimumHorizontalSpeed) || minimumHorizontalSpeed < 0.0D) {
            return null;
        }
        double requestedSpeed = launch.horizontalLength();
        if (requestedSpeed < minimumHorizontalSpeed || requestedSpeed <= 0.0D) {
            return null;
        }
        double minimumScale = minimumHorizontalSpeed / requestedSpeed;
        int attempts = (int) Math.ceil((1.0D - minimumScale) / 0.05D);
        for (int attempt = 0; attempt <= attempts; attempt++) {
            double scale = Math.max(minimumScale, 1.0D - attempt * 0.05D);
            Launch candidate = launch.withHorizontalScale(scale);
            if (candidate.horizontalLength() + 1.0E-9D >= minimumHorizontalSpeed
                    && landingIsSafe.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    public record Launch(double x, double y, double z) {
        public Launch {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            z = finiteOrZero(z);
        }

        public double horizontalLength() {
            return Math.hypot(x, z);
        }

        public Launch withHorizontalScale(double scale) {
            double safeScale = Double.isFinite(scale) ? Math.max(0.0D, Math.min(1.0D, scale)) : 0.0D;
            return new Launch(x * safeScale, y, z * safeScale);
        }

        public boolean isFinite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }
}
