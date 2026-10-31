package me.copimine.endevent.domain;

/**
 * MARK-007 / MARK-009: the Mobile Marksman's single Giant defender.
 *
 * <p>The contract allows one explicit support actor and forbids the generic
 * endless-pressure answer. The defender is a child of the stage: its death is
 * not a stage completion, it does not shield the miniboss, and it is cleaned up
 * with the stage rather than becoming a second fight.
 */
public final class Wave7MarksmanDefenderPolicy {
    /** "Around 50% HP" is the accepted trigger point. */
    public static final double TRIGGER_FRACTION = 0.5D;
    /** Exactly one defender; no elite-archer pack on top of it. */
    public static final int MAX_DEFENDERS = 1;
    /** Warning lead-in before the defender appears. */
    public static final long WARNING_TICKS = 40L;

    private Wave7MarksmanDefenderPolicy() {
    }

    /**
     * @param maxHealth     the miniboss maximum health, used to derive the threshold
     * @param currentHealth the miniboss health this tick
     * @param receiptUsed   whether a durable summon receipt already exists
     */
    public static boolean shouldSummon(double maxHealth, double currentHealth, boolean receiptUsed) {
        if (receiptUsed || !Double.isFinite(maxHealth) || maxHealth <= 0.0D
                || !Double.isFinite(currentHealth)) {
            return false;
        }
        return currentHealth <= maxHealth * TRIGGER_FRACTION;
    }

    /** The receipt is persisted before the actor exists, so a crash cannot duplicate it. */
    public static boolean receiptIsDurable() {
        return true;
    }

    /** W7-003: a child actor death never completes the stage. */
    public static boolean defenderDeathCompletesStage() {
        return false;
    }

    /** W7-005: an explicit owned support exception, not ordinary wave pressure. */
    public static boolean defenderIsOwnedSupport() {
        return true;
    }

    /** The defender uses shared Giant AI; it grants no aura to the miniboss. */
    public static boolean defenderShieldsMiniboss() {
        return false;
    }

    /** MARK-009: removal is cleanup, not a reward. */
    public static boolean defenderGrantsReward() {
        return false;
    }
}