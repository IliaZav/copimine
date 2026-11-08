package me.copimine.endevent.domain;

/**
 * Authoritative rules for the Wave 6 Rift Ram encounter.
 *
 * <p>Wave 6 used to be a ritual sphere with casters, guards and a prisoner. It
 * is replaced by a single oversized Ravager that the party must lure into the
 * Core three times. The gameplay contract is deliberately narrow: the Ravager
 * charges along a LOCKED straight line, and each accepted charge can produce at
 * most one Core impact. Everything here is pure so the rules can be tested
 * without a server, and the runtime only has to hold the state.</p>
 */
public final class RiftRamPolicy {
    /** How many times the Ravager must hit the Core before it becomes exposed. */
    public static final int REQUIRED_IMPACTS = 3;
    /** Vanilla Ravager is roughly 1.95 blocks tall; 1.5x keeps the hitbox honest. */
    public static final double SCALE = 1.5D;
    public static final double MIN_HEALTH = 180.0D;
    public static final double MAX_HEALTH = 400.0D;
    /** Players supported before the health curve saturates. */
    public static final int HEALTH_SCALING_PLAYERS = 20;
    public static final double MELEE_DAMAGE = 6.0D;
    public static final double CHARGE_DAMAGE = 8.0D;
    /** The Ravager stands still and roars for this long before it commits. */
    public static final int WARNING_TICKS = 40;
    /** Minimum gap between two accepted charges. */
    public static final int CHARGE_COOLDOWN_TICKS = 180;
    /** How long the Ravager lies stunned after cracking the Core. */
    public static final int IMPACT_STUN_TICKS = 100;
    /** A charge sprints for at most this long before it is called a miss. */
    public static final int CHARGE_TIMEOUT_TICKS = 60;
    /** Blocks per tick during a charge. Fast, but a walking player can dodge. */
    public static final double CHARGE_SPEED = 1.1D;
    /** How close the Ravager must come to a player to hit during a charge. */
    public static final double CHARGE_HIT_RADIUS = 1.6D;
    /** Horizontal distance at which a charging Ravager counts as hitting the Core. */
    public static final double CORE_IMPACT_RADIUS = 2.4D;
    /** Vertical slack above/below the Core so a charge cannot be stepped over. */
    public static final double CORE_VERTICAL_TOLERANCE = 3.0D;

    private RiftRamPolicy() {
    }

    /** The compact authoritative state of the encounter. */
    public enum State {
        /** Ravager is being created; nothing is accepted yet. */
        SPAWNING,
        /** Prowling toward players, waiting for its charge to come off cooldown. */
        CHASING,
        /** Standing still and roaring; the direction is not locked yet. */
        WARNING,
        /** Direction is locked and the Ravager is sprinting. */
        CHARGING,
        /** Recovering from a Core impact. */
        STUNNED,
        /** All three impacts landed: armour gone, full damage. */
        EXPOSED,
        /** Encounter is over; the Ravager is dead or the wave was abandoned. */
        COMPLETED
    }

    /**
     * Health scales linearly with the party size between MIN_HEALTH and
     * MAX_HEALTH. A party of 1 gets the floor, a party of 20 gets the ceiling,
     * and larger parties stay at the ceiling so the fight cannot become
     * unwinnable.
     */
    public static double healthForPlayers(int livingPlayers) {
        int players = Math.max(1, Math.min(HEALTH_SCALING_PLAYERS, livingPlayers));
        if (players <= 1) {
            return MIN_HEALTH;
        }
        double share = (double) (players - 1) / (double) (HEALTH_SCALING_PLAYERS - 1);
        return MIN_HEALTH + (MAX_HEALTH - MIN_HEALTH) * share;
    }

    /**
     * Incoming damage multiplier by accepted Core impacts.
     *
     * <p>The Ravager must survive until the third impact, but a hard immunity
     * makes players believe their hits are broken. Instead it takes real damage
     * that is heavily reduced early and reaches normal only once the armour is
     * gone, so attacks always register and always show a hit.</p>
     */
    public static double damageMultiplier(int acceptedImpacts) {
        return switch (Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts))) {
            case 0 -> 0.20D;
            case 1 -> 0.40D;
            case 2 -> 0.65D;
            default -> 1.0D;
        };
    }

    /**
     * A charge may be accepted only from {@link State#CHASING}, only when the
     * cooldown has elapsed and only while impacts remain.
     */
    public static boolean mayStartCharge(State state, int acceptedImpacts,
                                         long nowMillis, long lastChargeMillis) {
        if (state != State.CHASING) return false;
        if (acceptedImpacts >= REQUIRED_IMPACTS) return false;
        if (nowMillis < 0L) return false;
        return nowMillis - lastChargeMillis >= cooldownMillis();
    }

    public static long cooldownMillis() {
        return CHARGE_COOLDOWN_TICKS * 50L;
    }

    /**
     * Whether a charge impact counts.
     *
     * <p>Duplicate callbacks are the failure mode that matters: Paper can report
     * an entity collision more than once in a tick. The runtime records the
     * accepted impact count BEFORE it applies effects, and {@code impactsBefore}
     * is asked again here, so a second callback for the same charge sees the
     * same state only if the caller never advanced it. This method therefore
     * also refuses a charge that already recorded an impact.</p>
     */
    public static boolean mayAcceptImpact(State state, int acceptedImpacts, boolean chargeAlreadyBanked) {
        if (state != State.CHARGING) return false;
        if (chargeAlreadyBanked) return false;
        return acceptedImpacts >= 0 && acceptedImpacts < REQUIRED_IMPACTS;
    }

    /** The state after an accepted impact. */
    public static State stateAfterImpact(int acceptedImpacts) {
        return acceptedImpacts >= REQUIRED_IMPACTS ? State.EXPOSED : State.STUNNED;
    }

    /**
     * The state the Ravager returns to once its stun has elapsed. It must never
     * jump straight back to CHARGING, because a charge needs a fresh warning.
     */
    public static State stateAfterStun(int acceptedImpacts) {
        return acceptedImpacts >= REQUIRED_IMPACTS ? State.EXPOSED : State.CHASING;
    }

    /**
     * Swept test between the Ravager's previous and current position and the
     * Core.
     *
     * <p>A charging Ravager moves more than a block per tick, so testing only
     * the current point lets it tunnel straight through the Core without an
     * impact. The horizontal distance is solved along the segment, and the
     * vertical band is checked across the whole span so a charge that passes
     * above or below cannot count.</p>
     *
     * @return true when the segment passes within the Core impact radius
     */
    public static boolean segmentHitsCore(double fromX, double fromY, double fromZ,
                                          double toX, double toY, double toZ,
                                          double coreX, double coreY, double coreZ) {
        if (!allFinite(fromX, fromY, fromZ, toX, toY, toZ, coreX, coreY, coreZ)) {
            return false;
        }
        // The vertical band is inclusive on both ends, because the Ravager's
        // feet sit below the Core centre and its head above it.
        double lowY = coreY - CORE_VERTICAL_TOLERANCE;
        double highY = coreY + CORE_VERTICAL_TOLERANCE;
        double lowestReached = Math.min(fromY, toY);
        double highestReached = Math.max(fromY, toY);
        if (highestReached < lowY || lowestReached > highY) {
            return false;
        }
        double deltaX = toX - fromX;
        double deltaZ = toZ - fromZ;
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        // A charge that does not move horizontally can only hit the Core by
        // already standing in it, which the caller handles as a spawn guard.
        if (lengthSquared < 1.0E-12D) {
            return false;
        }
        double t = ((coreX - fromX) * deltaX + (coreZ - fromZ) * deltaZ) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = fromX + deltaX * t;
        double closestZ = fromZ + deltaZ * t;
        double distance = Math.hypot(closestX - coreX, closestZ - coreZ);
        return distance <= CORE_IMPACT_RADIUS + 1.0E-9D;
    }

    /**
     * Whether a player standing on the charge's path is hit.
     *
     * <p>Only a charge hits; a prowling Ravager still uses its ordinary melee
     * attack. The caller passes the already recorded hit set so a single charge
     * can hit each player at most once, which is what keeps a team wipe from
     * being an accident.</p>
     */
    public static boolean chargeHitsPlayer(double fromX, double fromZ,
                                           double toX, double toZ,
                                           double playerX, double playerZ,
                                           boolean alreadyHitThisCharge) {
        if (alreadyHitThisCharge) return false;
        if (!allFinite(fromX, fromZ, toX, toZ, playerX, playerZ)) return false;
        double deltaX = toX - fromX;
        double deltaZ = toZ - fromZ;
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        double t;
        if (lengthSquared < 1.0E-12D) {
            t = 0.0D;
        } else {
            t = ((playerX - fromX) * deltaX + (playerZ - fromZ) * deltaZ) / lengthSquared;
            t = Math.max(0.0D, Math.min(1.0D, t));
        }
        double closestX = fromX + deltaX * t;
        double closestZ = fromZ + deltaZ * t;
        return Math.hypot(closestX - playerX, closestZ - playerZ) <= CHARGE_HIT_RADIUS + 1.0E-9D;
    }

    /**
     * The locked direction of a charge, as a unit horizontal vector.
     *
     * <p>The direction is captured ONCE, at the end of the warning. The Ravager
     * must not home afterwards: the whole encounter is built on a player being
     * able to read the frozen line and step out of it. A degenerate target
     * (same block) yields no charge rather than a cardinally snapped one.</p>
     *
     * @return a two-element {dx, dz} unit vector, or null when unusable
     */
    public static double[] lockChargeDirection(double fromX, double fromY, double fromZ,
                                               double targetX, double targetY, double targetZ) {
        if (!allFinite(fromX, fromY, fromZ, targetX, targetY, targetZ)) return null;
        double dx = targetX - fromX;
        double dz = targetZ - fromZ;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-6D) return null;
        return new double[]{dx / horizontal, dz / horizontal};
    }

    /** True when the wave may complete: three impacts and a dead Ravager. */
    public static boolean isComplete(int acceptedImpacts, boolean ravagerAlive) {
        return acceptedImpacts >= REQUIRED_IMPACTS && !ravagerAlive;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }
}
