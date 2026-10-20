package me.copimine.endevent.domain;

import java.util.Locale;

/** Bounded role actions for the three Wave 6 Ritual Guard mob types. */
public final class RitualGuardAbilityPolicy {
    private static final Profile WARDEN_SLAM = new Profile(
            Role.WARDEN_SLAM, 20, 180, 8.0D, 2.35D, 4.0D, 0);
    private static final Profile RIFT_BOLT = new Profile(
            Role.RIFT_BOLT, 20, 220, 20.0D, 0.85D, 3.5D, 0);
    private static final Profile WEB_SNARE = new Profile(
            Role.WEB_SNARE, 24, 240, 10.0D, 2.1D, 2.0D, 40);

    private RitualGuardAbilityPolicy() {
    }

    public static Profile forGuardSlot(int guardSlot) {
        return switch (guardSlot) {
            case 0 -> WARDEN_SLAM;
            case 1 -> RIFT_BOLT;
            case 2 -> WEB_SNARE;
            default -> null;
        };
    }

    /** Keep all three roles in a five-caster solo roster without adding guards. */
    public static int roleSlot(int casterSlot, int guardSlot) {
        if (casterSlot < 0 || casterSlot >= 5 || guardSlot < 0 || guardSlot >= 3)
            throw new IllegalArgumentException("invalid caster/guard slot");
        return Math.floorMod(casterSlot + guardSlot, 3);
    }

    public static Profile forEntityType(String entityType) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType.trim().toUpperCase(Locale.ROOT)) {
            case "ENDERMAN" -> WARDEN_SLAM;
            case "SKELETON" -> RIFT_BOLT;
            case "SPIDER" -> WEB_SNARE;
            default -> null;
        };
    }

    public static boolean mayCommit(Profile profile, boolean waveActive,
                                    boolean guardAlive, boolean targetEligible,
                                    boolean majorSpellBusy, boolean reservationBusy,
                                    boolean coolingDown, double distanceBlocks) {
        return profile != null && waveActive && guardAlive && targetEligible
                && !majorSpellBusy && !reservationBusy && !coolingDown
                && Double.isFinite(distanceBlocks) && distanceBlocks >= 0.0D
                && distanceBlocks <= profile.maximumRangeBlocks();
    }

    public static boolean hits(Profile profile, double distanceSquared) {
        return profile != null && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0D
                && distanceSquared <= profile.impactRadiusBlocks() * profile.impactRadiusBlocks();
    }

    public enum Role {
        WARDEN_SLAM,
        RIFT_BOLT,
        WEB_SNARE
    }

    public record Profile(Role role, int telegraphTicks, int cooldownTicks,
                          double maximumRangeBlocks, double impactRadiusBlocks,
                          double damage, int slownessTicks) {
        public Profile {
            if (role == null || telegraphTicks < 20 || telegraphTicks > 30
                    || cooldownTicks < 160 || cooldownTicks > 260
                    || !Double.isFinite(maximumRangeBlocks) || maximumRangeBlocks < 4.0D
                    || maximumRangeBlocks > 24.0D
                    || !Double.isFinite(impactRadiusBlocks) || impactRadiusBlocks <= 0.0D
                    || impactRadiusBlocks > 2.5D
                    || !Double.isFinite(damage) || damage <= 0.0D || damage > 4.0D
                    || slownessTicks < 0 || slownessTicks > 60) {
                throw new IllegalArgumentException("invalid Ritual Guard ability profile");
            }
        }
    }
}
