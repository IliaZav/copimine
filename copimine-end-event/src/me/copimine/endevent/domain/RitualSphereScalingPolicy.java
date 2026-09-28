package me.copimine.endevent.domain;

/** Bounded Wave 6 pressure table with a fixed five-Caster progression. */
public final class RitualSphereScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int GUARDS_PER_CASTER = 3;
    public static final int MAX_INTENSITY = 10;
    public static final double MAX_PROJECTILE_DAMAGE_MULTIPLIER = 1.20D;
    public static final double MAX_EFFECT_DURATION_MULTIPLIER = 1.30D;
    public static final double MIN_COOLDOWN_MULTIPLIER = 0.75D;

    private RitualSphereScalingPolicy() {
    }

    public static Profile forPlayers(int players) {
        int count = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        if (count <= 4) {
            return new Profile(count, 5, 5, 1, 1, count == 2 ? 0 : 1, 13);
        }
        if (count <= 8) {
            return new Profile(count, 5, 10, 2, 1, 1, 12);
        }
        if (count <= 12) {
            return new Profile(count, 5, 15, 3, 2, 2, 11);
        }
        if (count <= 16) {
            return new Profile(count, 5, 15, 4, 2, 2, 10);
        }
        return new Profile(count, 5, 15, 5, 3, 3, 9);
    }

    public static int intensityForSuccessfulDrains(int successfulDrains) {
        return Math.max(0, Math.min(MAX_INTENSITY, successfulDrains));
    }

    public static double projectileDamageMultiplier(int successfulDrains) {
        int intensity = intensityForSuccessfulDrains(successfulDrains);
        return Math.min(MAX_PROJECTILE_DAMAGE_MULTIPLIER, 1.0D + intensity * 0.02D);
    }

    public static double effectDurationMultiplier(int successfulDrains) {
        int intensity = intensityForSuccessfulDrains(successfulDrains);
        return Math.min(MAX_EFFECT_DURATION_MULTIPLIER, 1.0D + intensity * 0.03D);
    }

    public static double cooldownMultiplier(int successfulDrains) {
        int intensity = intensityForSuccessfulDrains(successfulDrains);
        return Math.max(MIN_COOLDOWN_MULTIPLIER, 1.0D - intensity * 0.025D);
    }

    public static long majorCooldownMillis(Profile profile, int successfulDrains) {
        if (profile == null) {
            throw new IllegalArgumentException("ritual scaling profile is required");
        }
        long milliseconds = Math.round(profile.majorCooldownSeconds() * 1_000.0D
                * cooldownMultiplier(successfulDrains));
        return Math.max(1_000L, milliseconds);
    }

    public record Profile(int participants, int casterCount, int guardCount,
                          int projectilesPerVolley, int simultaneousZones,
                          int controlSwapPairs, int majorCooldownSeconds) {
        public Profile {
            if (participants < MIN_PLAYERS || participants > MAX_PLAYERS) {
                throw new IllegalArgumentException("ritual participants outside 2-20: " + participants);
            }
            if (casterCount != 5) {
                throw new IllegalArgumentException("ritual encounter requires exactly five casters");
            }
            if (guardCount % casterCount != 0
                    || guardCount / casterCount < 1
                    || guardCount / casterCount > GUARDS_PER_CASTER) {
                throw new IllegalArgumentException("ritual guard count must be one to three per caster");
            }
            if (projectilesPerVolley < 1 || projectilesPerVolley > 5
                    || simultaneousZones < 1 || simultaneousZones > 3
                    || controlSwapPairs < 0 || controlSwapPairs > 3
                    || majorCooldownSeconds < 9 || majorCooldownSeconds > 13) {
                throw new IllegalArgumentException("invalid ritual scaling profile");
            }
        }

        public int guardsPerCaster() {
            return guardCount / casterCount;
        }
    }
}
