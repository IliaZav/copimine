package me.copimine.endevent.domain;

import java.util.Locale;

/** Bounded cast actions and deterministic role slots for Wave 6 guards. */
public final class RitualGuardAbilityPolicy {
    private static final Profile WARDEN_SLAM = new Profile(
            Role.WARDEN_SLAM, 20, 260, 8.0D, 2.35D, 3.5D, 0);
    private static final Profile DEFENSIVE_VOLLEY = new Profile(
            Role.DEFENSIVE_VOLLEY, 20, 300, 18.0D, 0.85D, 2.8D, 0);
    private static final Profile SOUL_BRAND = new Profile(
            Role.SOUL_BRAND, 20, 260, 12.0D, 0.75D, 2.8D, 20);
    private static final Profile WEB_SNARE = new Profile(
            Role.WEB_SNARE, 24, 240, 10.0D, 2.1D, 2.0D, 30);
    public static final double ENDERMAN_MELEE_PRESSURE_BLOCKS = 2.25D;
    /** Ground slam only reaches targets close to the committed impact height. */
    public static final double WARDEN_SLAM_MAX_VERTICAL_BLOCKS = 1.5D;
    /** Keep native melee active across two pinned Paper attack intervals. */
    public static final long ENDERMAN_MELEE_BURST_TICKS = 40L;
    public static final long ENDERMAN_MELEE_COOLDOWN_TICKS = 120L;

    private RitualGuardAbilityPolicy() {
    }

    public static Profile forGuardSlot(int guardSlot) {
        return forGuardAction(guardSlot, 0);
    }

    /** Select the specialist action without changing the guard's roster role. */
    public static Profile forGuardAction(int roleSlot, int actionOrdinal) {
        return switch (roleSlot) {
            case 1 -> DEFENSIVE_VOLLEY;
            case 2 -> Math.floorMod(actionOrdinal, 2) == 0 ? WARDEN_SLAM : SOUL_BRAND;
            case 3 -> WEB_SNARE;
            default -> null;
        };
    }

    /** Pick an available role action at this distance, including the Enderman's longer fallback spell. */
    public static Profile forGuardActionAtRange(int roleSlot, int actionOrdinal,
                                                double distanceBlocks) {
        if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0.0D) return null;
        Profile preferred = forGuardAction(roleSlot, actionOrdinal);
        if (preferred != null && distanceBlocks <= preferred.maximumRangeBlocks()) {
            return preferred;
        }
        if (roleSlot == 2) {
            Profile alternate = forGuardAction(roleSlot, actionOrdinal + 1);
            if (alternate != null && distanceBlocks <= alternate.maximumRangeBlocks()) {
                return alternate;
            }
        }
        return null;
    }

    /** Maximum authored attack range used to admit ranged role targets. */
    public static double maximumRangeForGuardRole(int roleSlot) {
        return switch (roleSlot) {
            case 1 -> DEFENSIVE_VOLLEY.maximumRangeBlocks();
            case 2 -> Math.max(WARDEN_SLAM.maximumRangeBlocks(), SOUL_BRAND.maximumRangeBlocks());
            case 3 -> WEB_SNARE.maximumRangeBlocks();
            default -> 0.0D;
        };
    }

    public static boolean mayBeginSpiderWeb(double distanceBlocks) {
        return Double.isFinite(distanceBlocks)
                && distanceBlocks > SPIDER_BITE_RANGE_BLOCKS
                && distanceBlocks <= WEB_SNARE.maximumRangeBlocks();
    }

    public static final double SPIDER_BITE_RANGE_BLOCKS = 2.0D;

    /** Stable roster species for each deterministic Wave 6 role slot. */
    public static String entityTypeForGuardRole(int roleSlot) {
        return switch (roleSlot) {
            case 0 -> "ZOMBIE";
            case 1 -> "SKELETON";
            case 2 -> "ENDERMAN";
            case 3 -> "SPIDER";
            default -> throw new IllegalArgumentException("invalid ritual guard role slot");
        };
    }

    /** Giants keep Zombie as their Bukkit entity type and may replace only a tank slot. */
    public static boolean isGuardEntityTypeCompatible(int roleSlot, String actualEntityType,
                                                      boolean giantReplacement) {
        if (actualEntityType == null) return false;
        String actual = actualEntityType.trim().toUpperCase(Locale.ROOT);
        String expected;
        try {
            expected = entityTypeForGuardRole(roleSlot);
        } catch (IllegalArgumentException invalidRole) {
            return false;
        }
        return expected.equals(actual) && (!giantReplacement || roleSlot == 0);
    }

    public static Profile forRole(Role role) {
        if (role == null) return null;
        return switch (role) {
            case DEFENSIVE_VOLLEY -> DEFENSIVE_VOLLEY;
            case WARDEN_SLAM -> WARDEN_SLAM;
            case SOUL_BRAND -> SOUL_BRAND;
            case WEB_SNARE -> WEB_SNARE;
        };
    }

    public static boolean mayBeginEndermanMelee(double distanceBlocks,
                                                 long nowTick, long readyAtTick) {
        return Double.isFinite(distanceBlocks) && distanceBlocks >= 0.0D
                && distanceBlocks <= ENDERMAN_MELEE_PRESSURE_BLOCKS
                && nowTick >= 0L && nowTick >= readyAtTick;
    }

    public static boolean mayContinueEndermanMelee(double distanceBlocks,
                                                    long nowTick, long burstEndsAtTick) {
        return Double.isFinite(distanceBlocks) && distanceBlocks >= 0.0D
                && distanceBlocks <= ENDERMAN_MELEE_PRESSURE_BLOCKS
                && nowTick >= 0L && nowTick < burstEndsAtTick;
    }

    /** The shared action selector yields while the current native melee burst owns this guard. */
    public static boolean endermanMeleeOwnsController(long nowTick, long burstEndsAtTick) {
        return nowTick >= 0L && burstEndsAtTick > nowTick;
    }

    /** Spread four distinct guard roles across all five caster groups. */
    public static int roleSlot(int casterSlot, int guardSlot) {
        if (casterSlot < 0 || casterSlot >= 5 || guardSlot < 0 || guardSlot >= 3)
            throw new IllegalArgumentException("invalid caster/guard slot");
        return Math.floorMod(casterSlot + guardSlot, 4);
    }

    /** Replace only deterministic zombie-tank slots; never increase roster size. */
    public static boolean isGiantReplacement(int participants, int casterSlot, int guardSlot) {
        int boundedParticipants = Math.max(2, Math.min(20, participants));
        int casterCount = 5;
        int guardsPerCaster = boundedParticipants <= 4 ? 1 : boundedParticipants <= 8 ? 2 : 3;
        if (casterSlot < 0 || casterSlot >= casterCount
                || guardSlot < 0 || guardSlot >= guardsPerCaster
                || roleSlot(casterSlot, guardSlot) != 0) {
            return false;
        }
        int expected = boundedParticipants <= 4 ? 1 : boundedParticipants <= 8 ? 2 : 3;
        int zombieSlotOrdinal = 0;
        for (int caster = 0; caster < casterCount; caster++) {
            for (int slot = 0; slot < guardsPerCaster; slot++) {
                if (roleSlot(caster, slot) != 0) continue;
                if (caster == casterSlot && slot == guardSlot) {
                    return zombieSlotOrdinal < expected;
                }
                zombieSlotOrdinal++;
            }
        }
        return false;
    }

    public static Profile forEntityType(String entityType) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType.trim().toUpperCase(Locale.ROOT)) {
            case "ENDERMAN" -> WARDEN_SLAM;
            case "SKELETON" -> DEFENSIVE_VOLLEY;
            case "SPIDER" -> WEB_SNARE;
            default -> null;
        };
    }

    public static boolean isCompatibleWithMob(Profile profile, String entityType) {
        if (profile == null || entityType == null) return false;
        return switch (entityType.trim().toUpperCase(Locale.ROOT)) {
            case "ENDERMAN" -> profile.role() == Role.WARDEN_SLAM
                    || profile.role() == Role.SOUL_BRAND;
            case "SKELETON" -> profile.role() == Role.DEFENSIVE_VOLLEY;
            case "SPIDER" -> profile.role() == Role.WEB_SNARE;
            default -> false;
        };
    }

    public static boolean isHardControl(Profile profile) {
        return profile != null && profile.role() == Role.WEB_SNARE;
    }

    /** These hazard windows defer only guard actions that materially restrict escape. */
    public static boolean suppressesHardControl(boolean gravityAnchorWindow,
                                                boolean fallingAnvilWindow) {
        return gravityAnchorWindow || fallingAnvilWindow;
    }

    public static boolean mayCommit(Profile profile, boolean waveActive,
                                    boolean guardAlive, boolean targetEligible,
                                    boolean hardControlWindow, boolean reservationBusy,
                                    boolean coolingDown, double distanceBlocks) {
        return profile != null && waveActive && guardAlive && targetEligible
                && !(hardControlWindow && isHardControl(profile))
                && !reservationBusy && !coolingDown
                && Double.isFinite(distanceBlocks) && distanceBlocks >= 0.0D
                && distanceBlocks <= profile.maximumRangeBlocks();
    }

    public static boolean hits(Profile profile, double distanceSquared) {
        return profile != null && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0D
                && distanceSquared <= profile.impactRadiusBlocks() * profile.impactRadiusBlocks();
    }

    /** The ground slam requires its authored radius, a near-ground victim, and an unblocked ray. */
    public static boolean mayHitWardenSlamVictim(Profile profile, double horizontalDistanceSquared,
                                                  double verticalDistanceBlocks,
                                                  boolean hasLineOfSight) {
        return profile != null && profile.role() == Role.WARDEN_SLAM
                && hits(profile, horizontalDistanceSquared)
                && Double.isFinite(verticalDistanceBlocks) && verticalDistanceBlocks >= 0.0D
                && verticalDistanceBlocks <= WARDEN_SLAM_MAX_VERTICAL_BLOCKS
                && hasLineOfSight;
    }

    /** Bukkit damage is accepted only when health or absorption changed, or the hit was lethal. */
    public static boolean acceptedPlayerDamage(double beforeHealth, double beforeAbsorption,
                                                double afterHealth, double afterAbsorption,
                                                boolean dead) {
        if (!Double.isFinite(beforeHealth) || !Double.isFinite(beforeAbsorption)
                || !Double.isFinite(afterHealth) || !Double.isFinite(afterAbsorption)
                || beforeHealth < 0.0D || beforeAbsorption < 0.0D
                || afterHealth < 0.0D || afterAbsorption < 0.0D) return false;
        return dead && beforeHealth > 0.0D
                || afterHealth < beforeHealth || afterAbsorption < beforeAbsorption;
    }

    public enum Role {
        DEFENSIVE_VOLLEY,
        WARDEN_SLAM,
        SOUL_BRAND,
        WEB_SNARE
    }

    public record Profile(Role role, int telegraphTicks, int cooldownTicks,
                          double maximumRangeBlocks, double impactRadiusBlocks,
                          double damage, int slownessTicks) {
        public Profile {
            if (role == null || telegraphTicks < 10 || telegraphTicks > 30
                    || cooldownTicks < 240 || cooldownTicks > 300
                    || !Double.isFinite(maximumRangeBlocks) || maximumRangeBlocks < 4.0D
                    || maximumRangeBlocks > 24.0D
                    || !Double.isFinite(impactRadiusBlocks) || impactRadiusBlocks <= 0.0D
                    || impactRadiusBlocks > 2.5D
                    || !Double.isFinite(damage) || damage <= 0.0D || damage > 4.0D
                    || slownessTicks < 0 || slownessTicks > 30) {
                throw new IllegalArgumentException("invalid Ritual Guard ability profile");
            }
        }
    }
}
