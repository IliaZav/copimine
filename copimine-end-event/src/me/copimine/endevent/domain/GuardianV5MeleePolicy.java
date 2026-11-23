package me.copimine.endevent.domain;

import java.util.List;

/**
 * V5.1 Guardian melee-first action contract.
 *
 * <p>The Guardian is a grounded physical fighter first and a spellcaster
 * second. This policy only describes the four physical moves and the short
 * separation combo; it never selects a V4 major spell, never touches
 * {@link BossPhasePolicy}, and never overrides the existing cast owners.</p>
 */
public final class GuardianV5MeleePolicy {
    private GuardianV5MeleePolicy() {
    }

    /** The four physical moves plus the separation pressure wave. */
    public enum Move {
        BASIC_LEFT,
        BASIC_RIGHT,
        FAN,
        HEAVY,
        SEPARATION
    }

    /** Which of the three post-separation branches is legal right now. */
    public enum Branch {
        /** Push -> one announced ranged beat -> sprint back into melee. */
        ONE_RANGED_BEAT,
        /** Push -> two distinct announced spells -> melee. */
        TWO_SPELLS,
        /** Push -> pivot -> melee immediately. */
        IMMEDIATE_RUSH
    }

    // ------------------------------------------------------------------
    // Basic left/right hand
    // ------------------------------------------------------------------

    public static final double BASIC_RANGE_BLOCKS = 3.5D;
    /** Half-angle of the basic contact, measured from the committed facing. */
    public static final double BASIC_HALF_ANGLE_DEGREES = 70.0D;
    /** 0.2..0.35 s: fast enough to be the most frequent attack. */
    public static final int BASIC_WINDUP_TICKS = 6;
    public static final int BASIC_RECOVERY_TICKS = 8;
    public static final double BASIC_DAMAGE = 8.0D;
    /** Minimum gap between two basic contacts. */
    public static final int BASIC_CADENCE_TICKS = 18;

    /** Alternating left/right so clear model poses can follow the strike. */
    public static Move basicMove(long acceptedBasicCount) {
        return acceptedBasicCount % 2L == 0L ? Move.BASIC_LEFT : Move.BASIC_RIGHT;
    }

    // ------------------------------------------------------------------
    // Multi-target Fan
    // ------------------------------------------------------------------

    /** 120..130 degrees total; 125 keeps the cone readable and not circular. */
    public static final double FAN_TOTAL_DEGREES = 125.0D;
    public static final double FAN_HALF_ANGLE_DEGREES = FAN_TOTAL_DEGREES / 2.0D;
    public static final double FAN_RANGE_MIN_BLOCKS = 4.5D;
    public static final double FAN_RANGE_MAX_BLOCKS = 5.0D;
    public static final int FAN_WINDUP_TICKS = 10;
    public static final int FAN_RECOVERY_TICKS = 14;
    public static final double FAN_DAMAGE = 7.0D;
    public static final int FAN_COOLDOWN_TICKS = 70;
    /** Vertical tolerance of the ground fan. */
    public static final double FAN_VERTICAL_BLOCKS = 2.2D;

    /**
     * Cone test in the committed frame.
     *
     * @param forward blocks in front of the frozen facing
     * @param lateral absolute sideways offset
     */
    public static boolean fanHits(double forward, double lateral) {
        if (!Double.isFinite(forward) || !Double.isFinite(lateral) || forward <= 0.0D) {
            return false;
        }
        double distance = Math.hypot(forward, lateral);
        if (distance > FAN_RANGE_MAX_BLOCKS || distance < 0.4D) {
            return false;
        }
        double angle = Math.toDegrees(Math.atan2(lateral, forward));
        return angle <= FAN_HALF_ANGLE_DEGREES;
    }

    // ------------------------------------------------------------------
    // Heavy overhead
    // ------------------------------------------------------------------

    /** 0.6..1.0 s preparation; 0.8 s is the readable committed middle. */
    public static final int HEAVY_WINDUP_TICKS = 16;
    /** Meaningful miss recovery the party can punish. */
    public static final int HEAVY_RECOVERY_TICKS = 16;
    public static final double HEAVY_RANGE_BLOCKS = 5.0D;
    /** Frontal heavy wedge, narrower than the Fan. */
    public static final double HEAVY_HALF_ANGLE_DEGREES = 35.0D;
    public static final double HEAVY_DAMAGE = 16.0D;
    public static final int HEAVY_COOLDOWN_TICKS = 110;
    public static final double HEAVY_VERTICAL_BLOCKS = 2.6D;

    public static boolean heavyHits(double forward, double lateral) {
        if (!Double.isFinite(forward) || !Double.isFinite(lateral) || forward <= 0.0D) {
            return false;
        }
        double distance = Math.hypot(forward, lateral);
        if (distance > HEAVY_RANGE_BLOCKS) {
            return false;
        }
        return Math.toDegrees(Math.atan2(Math.abs(lateral), forward))
                <= HEAVY_HALF_ANGLE_DEGREES;
    }

    /**
     * A committed heavy that found nobody leaves a punish window: the runtime
     * must not let the boss chain into the next action before it elapses.
     */
    public static int punishWindowTicks(boolean landed) {
        return landed ? 4 : HEAVY_RECOVERY_TICKS;
    }

    // ------------------------------------------------------------------
    // Separation knockback
    // ------------------------------------------------------------------

    /** ~4.5..5 block real close pressure wave. */
    public static final double SEPARATION_RADIUS_MIN_BLOCKS = 4.5D;
    public static final double SEPARATION_RADIUS_MAX_BLOCKS = 5.0D;
    /** Legal targets are displaced about 4..5 blocks. */
    public static final double SEPARATION_PUSH_MIN_BLOCKS = 4.0D;
    public static final double SEPARATION_PUSH_MAX_BLOCKS = 5.0D;
    /** 0.55..0.8 s warning. */
    public static final int SEPARATION_WARNING_TICKS = 13;
    public static final double SEPARATION_DAMAGE = 4.0D;
    public static final int SEPARATION_COOLDOWN_MIN_TICKS = 240;
    public static final int SEPARATION_COOLDOWN_MAX_TICKS = 360;
    public static final int SEPARATION_COOLDOWN_TICKS = 300;
    public static final double SEPARATION_VERTICAL_BLOCKS = 3.0D;

    public static double separationRadius() {
        return (SEPARATION_RADIUS_MIN_BLOCKS + SEPARATION_RADIUS_MAX_BLOCKS) / 2.0D;
    }

    public static double separationPushStrength() {
        // Bukkit velocity units: about 0.9 horizontal velocity carries a player
        // roughly four to five blocks with normal friction.
        return 0.9D;
    }

    public static boolean separationHits(double horizontalDistance, double verticalDistance) {
        return Double.isFinite(horizontalDistance)
                && horizontalDistance <= SEPARATION_RADIUS_MAX_BLOCKS
                && Math.abs(verticalDistance) <= SEPARATION_VERTICAL_BLOCKS;
    }

    // ------------------------------------------------------------------
    // Post-separation branches
    // ------------------------------------------------------------------

    /**
     * Choose the smallest legal branch. A refused spell never silences melee:
     * if nothing is legal, the answer is the immediate rush.
     */
    public static Branch chooseBranch(boolean oneRangedLegal, boolean secondSpellLegal) {
        if (oneRangedLegal && secondSpellLegal) {
            return Branch.TWO_SPELLS;
        }
        if (oneRangedLegal) {
            return Branch.ONE_RANGED_BEAT;
        }
        return Branch.IMMEDIATE_RUSH;
    }

    /** Hard cap of spell beats per branch. */
    public static int maxSpellBeats(Branch branch) {
        return switch (branch) {
            case ONE_RANGED_BEAT -> 1;
            case TWO_SPELLS -> 2;
            case IMMEDIATE_RUSH -> 0;
        };
    }

    /** Ticks the boss may spend waiting on a refused cast before rushing. */
    public static final int BRANCH_REFUSAL_GRACE_TICKS = 10;

    /**
     * The second spell beat must be a different announced action so the party
     * never eats the same lane twice in a row.
     */
    public static boolean distinctBeats(String first, String second) {
        return first != null && second != null && !first.equals(second);
    }

    // ------------------------------------------------------------------
    // Phase gating
    // ------------------------------------------------------------------

    /**
     * Grounded melee is the default in every phase except an intact cocoon.
     * During the cocoon the boss hovers and only casts; after the fall it
     * returns to melee immediately.
     */
    public static boolean meleeAllowed(BossPhase phase, boolean cocoonIntact) {
        if (phase == null) {
            return false;
        }
        return !cocoonIntact;
    }

    /** Whether a heavy or fan may interrupt the current pursuit cadence. */
    public static boolean mayUseCommittedMove(long nowMillis, long nextCommittedAtMillis,
                                              boolean bossCasting, boolean cocoonIntact) {
        return !bossCasting && !cocoonIntact && nowMillis >= nextCommittedAtMillis;
    }

    /** Rotation order of committed moves after the opening basic pair. */
    public static List<Move> committedRotation() {
        return List.of(Move.FAN, Move.HEAVY, Move.FAN);
    }
}
