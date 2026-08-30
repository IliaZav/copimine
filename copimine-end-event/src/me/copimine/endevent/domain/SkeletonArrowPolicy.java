package me.copimine.endevent.domain;

/**
 * Bounded payload selection for wave skeleton arrows.  The server owns the
 * projectile and damage event; this class only makes the chance and effect
 * duration contract deterministic and testable without Bukkit.
 */
public final class SkeletonArrowPolicy {
    public static final int STATUS_CHANCE_PERCENT = 20;
    public static final int STATUS_DURATION_TICKS = 7 * 20;
    public static final int STATUS_AMPLIFIER = 2; // Potion level III.
    public static final int EXPLOSIVE_DAMAGE_RADIUS_BLOCKS = 2;
    public static final float EXPLOSIVE_POWER = 1.8F;

    private SkeletonArrowPolicy() {
    }

    public enum ArrowKind {
        COMMON,
        POISON_NAUSEA,
        EXPLOSIVE
    }

    public static ArrowKind forShot(boolean elite, long roll) {
        if (elite) {
            return ArrowKind.EXPLOSIVE;
        }
        return isStatusRoll(roll) ? ArrowKind.POISON_NAUSEA : ArrowKind.COMMON;
    }

    /** Convert an arbitrary random value into the requested 20% bucket. */
    public static boolean isStatusRoll(long roll) {
        return Math.floorMod(roll, 100L) < STATUS_CHANCE_PERCENT;
    }

    public static boolean breaksBlocks() {
        return false;
    }
}
