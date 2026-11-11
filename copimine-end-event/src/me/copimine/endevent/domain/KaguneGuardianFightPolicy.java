package me.copimine.endevent.domain;

/**
 * Master contract 17.2 / 17.1: the large defensive Kagune must fight, push
 * players five to six blocks on a heavy strike, and must never be stun-locked
 * into permanent stillness by several players attacking at once.
 */
public final class KaguneGuardianFightPolicy {
    /** Contract 17.1/17.2: "push players approximately 5-6 blocks". */
    public static final double KNOCKBACK_MIN_BLOCKS = 5.0D;
    public static final double KNOCKBACK_MAX_BLOCKS = 6.0D;
    /** A heavy strike is a real, party-scaled hit. */
    public static final double BASE_STRIKE_DAMAGE = 8.0D;
    /** Hard ceiling so a full party cannot be one-shot by a defender. */
    public static final double MAX_STRIKE_DAMAGE = 14.0D;
    /** Damage flinch window; another hit inside it cannot restart it. */
    public static final int HIT_RECOVERY_TICKS = 5;
    /** Cooldown after a heavy strike so it is not a free instant push. */
    public static final int STRIKE_COOLDOWN_TICKS = 60;
    /** Bounded accumulated pressure: at most this many flinches may be dropped. */
    public static final int MAX_PENDING_FLINCHES = 3;
    /** Shared pressure: one player is struck by at most this many defenders at once. */
    public static final int MAX_CONCURRENT_ATTACKERS_PER_TARGET = 2;

    private KaguneGuardianFightPolicy() {
    }

    /** Party-scaled strike damage, bounded. */
    public static double strikeDamage(int admittedPlayers) {
        int players = Math.max(0, Math.min(20, admittedPlayers));
        if (players <= 0) {
            return 0.0D;
        }
        return Math.min(MAX_STRIKE_DAMAGE, BASE_STRIKE_DAMAGE + Math.max(0, players - 2) * 0.5D);
    }

    /** Horizontal impulse magnitude for a heavy strike, inside 5..6 blocks. */
    public static double knockbackSpeed(int admittedPlayers) {
        int players = Math.max(0, Math.min(20, admittedPlayers));
        double t = players <= 2 ? 0.0D : Math.min(1.0D, (players - 2) / 18.0D);
        return KNOCKBACK_MIN_BLOCKS + t * (KNOCKBACK_MAX_BLOCKS - KNOCKBACK_MIN_BLOCKS);
    }

    /**
     * Anti-stunlock: a fresh hit may start a flinch only when the previous one
     * has finished and the accumulated pressure budget is not spent.
     */
    public static boolean flinchAllowed(int recoveryTicksRemaining,
                                        int pendingFlinches) {
        if (recoveryTicksRemaining > 0) {
            return false;
        }
        return pendingFlinches < MAX_PENDING_FLINCHES;
    }

    /** An attack that is already committed still lands through a flinch. */
    public static boolean committedAttackSurvivesFlinch(boolean attackCommitted) {
        return attackCommitted;
    }

    /**
     * Shared pressure: how many defenders may attack this player now. It keeps
     * the whole cocoon from hitting one player on the same server tick.
     */
    public static boolean mayAttackTarget(int attackersOnTarget) {
        return attackersOnTarget < MAX_CONCURRENT_ATTACKERS_PER_TARGET;
    }

    /** Defenders choose independent targets; this spreads them fairly. */
    public static int spreadSlot(int guardianIndex, int guardianCount, int playerCount) {
        if (guardianCount <= 0 || playerCount <= 0) {
            return -1;
        }
        int safeIndex = Math.floorMod(guardianIndex, guardianCount);
        return safeIndex % playerCount;
    }

    /** A defender with no legal target keeps pressure on its cooldown. */
    public static int idleRetryTicks() {
        return 20;
    }
}