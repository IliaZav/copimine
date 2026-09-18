package me.copimine.endevent.domain;

/** Bounded overlay contributed by live, still-channeling ritual amplifiers. */
public final class RitualAmplifierPolicy {
    public static final int MAX_LIVE_AMPLIFIERS = 2;
    public static final int MAX_EFFECTIVE_PROJECTILES = 7;

    private RitualAmplifierPolicy() {
    }

    public static int boundedAmplifiers(int liveAmplifiers) {
        return Math.max(0, Math.min(MAX_LIVE_AMPLIFIERS, liveAmplifiers));
    }

    public static int effectiveIntensity(int successfulDrains, int liveAmplifiers) {
        return RitualSphereScalingPolicy.intensityForSuccessfulDrains(
                successfulDrains + boundedAmplifiers(liveAmplifiers));
    }

    public static int projectileCount(RitualSphereScalingPolicy.Profile profile,
                                      int liveAmplifiers) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        return Math.min(MAX_EFFECTIVE_PROJECTILES,
                profile.projectilesPerVolley() + boundedAmplifiers(liveAmplifiers));
    }

    public static double projectileDamageMultiplier(int successfulDrains,
                                                    int liveAmplifiers) {
        return RitualSphereScalingPolicy.projectileDamageMultiplier(
                effectiveIntensity(successfulDrains, liveAmplifiers));
    }

    public static double effectDurationMultiplier(int successfulDrains,
                                                  int liveAmplifiers) {
        return RitualSphereScalingPolicy.effectDurationMultiplier(
                effectiveIntensity(successfulDrains, liveAmplifiers));
    }

    public static long majorCooldownMillis(RitualSphereScalingPolicy.Profile profile,
                                           int successfulDrains,
                                           int liveAmplifiers) {
        return RitualSphereScalingPolicy.majorCooldownMillis(
                profile, effectiveIntensity(successfulDrains, liveAmplifiers));
    }
}
