package me.copimine.artifacts;

/** Pure state transitions for the five-use repair kit. */
public final class RepairKitMath {
    public static final int MAX_USES = 5;

    private RepairKitMath() {
    }

    /** Returns damage after restoring one quarter of the target maximum. */
    public static int repairedDamage(int currentDamage, int maxDurability) {
        if (maxDurability <= 0) {
            return currentDamage;
        }
        int boundedDamage = Math.min(maxDurability, Math.max(0, currentDamage));
        int restored = Math.max(1, (maxDurability + 3) / 4);
        return Math.max(0, boundedDamage - restored);
    }

    /** Decrements one successful use and never produces a negative state. */
    public static int remainingUsesAfterSuccess(int usesRemaining) {
        return Math.max(0, Math.min(MAX_USES, usesRemaining) - 1);
    }

    /**
     * Decides whether a successful repair may consume one kit use.  Metadata
     * and event ownership are checked by the plugin; these flags keep the
     * state transition itself fail-closed and independently testable.
     */
    public static boolean canRepair(
            int currentDamage,
            int maxDurability,
            int usesRemaining,
            boolean officialCustomTarget,
            boolean repairKitTarget,
            boolean eventCancelled
    ) {
        if (eventCancelled || officialCustomTarget || repairKitTarget
                || usesRemaining <= 0 || usesRemaining > MAX_USES
                || maxDurability <= 0 || currentDamage <= 0) {
            return false;
        }
        return repairedDamage(currentDamage, maxDurability) < currentDamage;
    }

    /** Maps remaining uses onto the kit's five-point vanilla durability bar. */
    public static int damageForRemainingUses(int maxDurability, int usesRemaining) {
        if (maxDurability <= 0) {
            return 0;
        }
        int boundedUses = Math.max(0, Math.min(MAX_USES, usesRemaining));
        return Math.min(maxDurability, MAX_USES - boundedUses);
    }
}
