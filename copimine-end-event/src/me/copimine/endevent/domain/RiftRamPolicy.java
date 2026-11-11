package me.copimine.endevent.domain;

/**
 * Authoritative rules for the Wave 6 Rift Ram encounter.
 *
 * <p>Wave 6 used to be a ritual sphere with casters, guards and a prisoner. It
 * is replaced by a single oversized Ravager that the party must lure into the
 * pillar five times. The gameplay contract is deliberately narrow: the Ravager
 * charges along a LOCKED straight line, and each accepted charge can produce at
 * most one pillar impact. Everything here is pure so the rules can be tested
 * without a server, and the runtime only has to hold the state.</p>
 */
public final class RiftRamPolicy {
    /**
     * Ordinary pillars, each destroyed by a single accepted collision.
     *
     * <p>SUPERSEDES W6-03 (playtest 2026-11-10), which asked for "two or three
     * accepted column impacts". The 2026-11-11 master rebuild replaced that
     * single-pillar loop with a roster: "four ordinary pillars followed by one
     * very large final pillar", the final one requiring "three distinct
     * successful Ram collisions". The rendered arena therefore carries four
     * readable one-hit pillars as a rhythm, and then one much larger finale.</p>
     */
    public static final int MINOR_PILLARS = 4;
    /** Collisions the oversized final pillar needs before it collapses. */
    public static final int FINAL_PILLAR_IMPACTS = 3;
    /** Total pillars in the encounter, ordinary plus final. */
    public static final int PILLAR_COUNT = MINOR_PILLARS + 1;
    /**
     * Accepted collisions needed before the Ram's armour is fully gone.
     *
     * <p>One per ordinary pillar, three for the final one: the seventh and last
     * collision is the one that both collapses the last pillar and strips the
     * final plate. Deriving this keeps the roster, the armour ladder, the boss
     * bar and the damage gate from ever disagreeing.</p>
     */
    public static final int REQUIRED_IMPACTS = MINOR_PILLARS + FINAL_PILLAR_IMPACTS;
    /** Plate count on the body; one plate is shed per accepted impact. */
    public static final int ARMOUR_PLATES = REQUIRED_IMPACTS;
    /** The pillar that carries the elite marksman skeleton. */
    public static final int ELITE_SKELETON_PILLAR = PILLAR_COUNT;
    public static final int STOMP_SLOW_TICKS = 60;
    public static final int STOMP_SLOW_AMPLIFIER = 1;
    public static final double STOMP_RADIUS = 12.0D;
    public static final int STOMP_WARNING_TICKS = 20;
    public static final int STOMP_EXPANSION_TICKS = 28;
    public static final int STOMP_COOLDOWN_TICKS = 400;
    /** Vanilla Ravager is roughly 1.95 blocks tall; 1.5x keeps the hitbox honest. */
    public static final double SCALE = 1.5D;
    /**
     * Final body scale, W6-01.
     *
     * <p>The playtest asked for the Ram to be "a bit bigger". 1.5 was only
     * marginally taller than a vanilla Ravager and disappeared next to a
     * Netherite-armoured crowd; 1.7 reads as a boss while keeping the hitbox
     * inside the arena's authored clearance.</p>
     */
    public static final double BOSS_SCALE = 1.7D;
    public static final double MIN_HEALTH = 180.0D;
    public static final double MAX_HEALTH = 400.0D;
    /** Players supported before the health curve saturates. */
    public static final int HEALTH_SCALING_PLAYERS = 20;
    public static final double MELEE_DAMAGE = 6.0D;
    public static final double CHARGE_DAMAGE = 8.0D;
    /** The Ravager stands still and roars for this long before it commits. */
    public static final int WARNING_TICKS = 40;
    /**
     * Charge windup, W6-02.
     *
     * <p>The playtest wanted a longer, more readable tell: "red eyes and smoke
     * from the nostrils during the run-up". A tell that ends in less than a
     * second is not a tell, it is a surprise. This is the window in which the
     * eyes glow, the nostrils smoke and the roar plays, and it is deliberately
     * long enough to be read from the far side of the arena.</p>
     */
    public static final int CHARGE_WINDUP_TICKS = 46;
    /** Minimum gap between two accepted charges. */
    public static final int CHARGE_COOLDOWN_TICKS = 180;
    /** How long the Ravager lies stunned after cracking the pillar. */
    public static final int IMPACT_STUN_TICKS = 100;
    /** A charge sprints for at most this long before it is called a miss. */
    public static final int CHARGE_TIMEOUT_TICKS = 60;
    /** Blocks per tick during a charge. Fast, but a walking player can dodge. */
    public static final double CHARGE_SPEED = 1.1D;
    /**
     * Charge speed once the armour starts coming off, W6-02.
     *
     * <p>"Runs further and with effects" was the ask. A Ram that sprints faster
     * the more plates it has lost turns the fight around: the armour phase is
     * the tutorial and the exposed phase is the punishment. It is still under
     * two blocks per tick, so a player who reacts can sidestep.</p>
     */
    public static final double CHARGE_SPEED_EXPOSED = 1.45D;
    /** How close the Ravager must come to a player to hit during a charge. */
    public static final double CHARGE_HIT_RADIUS = 1.6D;
    /** Horizontal distance at which a charging Ravager counts as hitting the pillar. */
    public static final double PILLAR_IMPACT_RADIUS = 2.4D;
    /** Vertical slack above/below the pillar so a charge cannot be stepped over. */
    public static final double PILLAR_VERTICAL_TOLERANCE = 3.0D;

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
        /** Recovering from a pillar impact. */
        STUNNED,
        /** Every plate is gone: armour broken, full damage. */
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
     * Incoming damage multiplier by accepted pillar impacts.
     *
     * <p>The Ravager must survive until the last impact, but a hard immunity
     * makes players believe their hits are broken. Instead it takes real damage
     * that is heavily reduced early and reaches normal only once the armour is
     * gone, so attacks always register and always show a hit.</p>
     *
     * <p>The ladder is derived from {@link #REQUIRED_IMPACTS} rather than
     * spelled out, so changing the number of plates cannot leave a stale table
     * behind: every stage is an equal step from the first-hit reduction to full
     * damage.</p>
     */
    public static double damageMultiplier(int acceptedImpacts) {
        int impacts = Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts));
        double step = 1.0D / (REQUIRED_IMPACTS + 1.0D);
        return step * (impacts + 1.0D);
    }

    /**
     * Sprint speed for a charge at the current plate state, W6-02.
     *
     * <p>The Ram starts heavy and slow while the armour is bolted on and gets
     * faster with every plate it sheds, so losing the armour is a real change in
     * the fight rather than a buff to the party alone.</p>
     */
    public static double chargeSpeed(int acceptedImpacts) {
        if (acceptedImpacts <= 0) return CHARGE_SPEED;
        int impacts = Math.min(REQUIRED_IMPACTS, acceptedImpacts);
        double share = (double) impacts / (double) REQUIRED_IMPACTS;
        return CHARGE_SPEED + (CHARGE_SPEED_EXPOSED - CHARGE_SPEED) * share;
    }

    /** Charge windup in ticks; the red-eye and nostril-smoke window. */
    public static int chargeWindupTicks() {
        return CHARGE_WINDUP_TICKS;
    }

    /**
     * Boss-bar name for the current stage, W6-03.
     *
     * <p>The bar has to say what the fight is asking of the party right now, not
     * just how much health is left: "break the plates" and "he is open, burn him"
     * are different instructions and the player should not have to infer which
     * one applies.</p>
     */
    public static String bossBarTitle(int acceptedImpacts) {
        if (acceptedImpacts >= REQUIRED_IMPACTS) {
            return "Таран Разлома — броня сбита";
        }
        int ordinal = pillarOrdinal(acceptedImpacts);
        if (isFinalPillar(ordinal)) {
            // The finale is a different instruction from the ordinary pillars:
            // the party has to understand that one more collision will not do.
            return "Таран Разлома — опора треснула " + finalPillarStage(acceptedImpacts) + "/"
                    + FINAL_PILLAR_IMPACTS;
        }
        return "Таран Разлома — плит осталось: " + remainingPlates(acceptedImpacts)
                + " · опора " + ordinal + "/" + PILLAR_COUNT;
    }

    /**
     * Boss-bar fill, W6-03.
     *
     * <p>The bar tracks authoritative health. Armour is a separate visible
     * plate count; breaking a plate must not fabricate HP loss.</p>
     */
    public static double bossBarProgress(double health, double maximumHealth, int acceptedImpacts) {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
            return 0.0D;
        }
        double healthShare = Math.max(0.0D, Math.min(1.0D, health / maximumHealth));
        return healthShare;
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
     * pillar.
     *
     * <p>A charging Ravager moves more than a block per tick, so testing only
     * the current point lets it tunnel straight through the pillar without an
     * impact. The horizontal distance is solved along the segment, and the
     * vertical band is checked across the whole span so a charge that passes
     * above or below cannot count.</p>
     *
     * @return true when the segment passes within the pillar impact radius
     */
    public static boolean segmentHitsPillar(double fromX, double fromY, double fromZ,
                                          double toX, double toY, double toZ,
                                          double pillarX, double pillarY, double pillarZ) {
        if (!allFinite(fromX, fromY, fromZ, toX, toY, toZ, pillarX, pillarY, pillarZ)) {
            return false;
        }
        // The vertical band is inclusive on both ends, because the Ravager's
        // feet sit below the pillar centre and its head above it.
        double lowY = pillarY - PILLAR_VERTICAL_TOLERANCE;
        double highY = pillarY + PILLAR_VERTICAL_TOLERANCE;
        double lowestReached = Math.min(fromY, toY);
        double highestReached = Math.max(fromY, toY);
        if (highestReached < lowY || lowestReached > highY) {
            return false;
        }
        double deltaX = toX - fromX;
        double deltaZ = toZ - fromZ;
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        // A charge that does not move horizontally can only hit the pillar by
        // already standing in it, which the caller handles as a spawn guard.
        if (lengthSquared < 1.0E-12D) {
            return false;
        }
        double t = ((pillarX - fromX) * deltaX + (pillarZ - fromZ) * deltaZ) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = fromX + deltaX * t;
        double closestZ = fromZ + deltaZ * t;
        double closestY = fromY + (toY - fromY) * t;
        if (closestY < lowY || closestY > highY) return false;
        double distance = Math.hypot(closestX - pillarX, closestZ - pillarZ);
        return distance <= PILLAR_IMPACT_RADIUS + 1.0E-9D;
    }

    /** Swept annulus, including the space traversed since the preceding update. */
    public static boolean stompHits(double distance, double feetAboveFloor, boolean grounded,
                                    double previousRadius, double radius, boolean alreadyHit) {
        return !alreadyHit && grounded
                && allFinite(distance, feetAboveFloor, previousRadius, radius)
                && feetAboveFloor >= -0.25D && feetAboveFloor <= 0.55D
                && previousRadius >= 0 && radius >= previousRadius && radius <= STOMP_RADIUS
                && distance >= Math.max(0, previousRadius - 0.75D)
                && distance <= Math.min(STOMP_RADIUS, radius + 0.75D);
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

    /** True when the wave may complete: five impacts and a dead Ravager. */
    public static boolean isComplete(int acceptedImpacts, boolean ravagerAlive) {
        return acceptedImpacts >= REQUIRED_IMPACTS && !ravagerAlive;
    }

    // ------------------------------------------------------------------
    // Pillar roster
    // ------------------------------------------------------------------

    /** How many Endermites one shattered pillar releases, inclusive bounds. */
    public static final int ENDERMITES_MIN = 2;
    public static final int ENDERMITES_MAX = 4;
    /** Hard ceiling on living event-owned Endermites, so a spawn loop cannot run away. */
    public static final int ENDERMITES_CAP = 6;
    /** Ticks a shattered pillar site stays clear before the next pillar rises. */
    public static final int PILLAR_RERAISE_DELAY_TICKS = 40;

    /**
     * How many Endermites a shattered pillar may actually release.
     *
     * <p>The cap is a ceiling on the WHOLE encounter, not on one pillar: a party
     * that left four Endermites alive from earlier pillars has two slots left,
     * not four. Returning zero is normal and must not be treated as an error,
     * otherwise the last pillar would look broken.</p>
     *
     * @param livingEndermites Endermites already owned by this encounter
     * @param requested        the roll for this pillar
     */
    public static int endermitesToRelease(int livingEndermites, int requested) {
        int living = Math.max(0, livingEndermites);
        int wanted = Math.max(ENDERMITES_MIN, Math.min(ENDERMITES_MAX, requested));
        int room = ENDERMITES_CAP - living;
        if (room <= 0) return 0;
        return Math.min(wanted, room);
    }

    /** True only when a further pillar may still be raised. */
    public static boolean mayRaisePillar(int acceptedImpacts) {
        return acceptedImpacts >= 0 && acceptedImpacts < REQUIRED_IMPACTS;
    }

    /** The 1-based pillar number currently standing, or 0 when all five are gone. */
    public static int activePillarNumber(int acceptedImpacts) {
        return mayRaisePillar(acceptedImpacts) ? pillarOrdinal(acceptedImpacts) : 0;
    }

    /**
     * Which 1-based pillar is standing at this impact count.
     *
     * <p>The first four impacts each retire one ordinary pillar, so impact count
     * and pillar number coincide there. From the fourth impact onward the final
     * pillar absorbs three collisions, so it stays standing across impacts 4, 5
     * and 6 and is gone at 7.</p>
     *
     * @return the standing pillar's ordinal, clamped into the roster
     */
    public static int pillarOrdinal(int acceptedImpacts) {
        int impacts = Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts));
        if (impacts < MINOR_PILLARS) return impacts + 1;
        if (impacts < REQUIRED_IMPACTS) return PILLAR_COUNT;
        return PILLAR_COUNT;
    }

    /** Whether the given 1-based pillar ordinal is the oversized final pillar. */
    public static boolean isFinalPillar(int ordinal) {
        return ordinal == PILLAR_COUNT;
    }

    /** Collisions one pillar of this ordinal needs before it collapses. */
    public static int impactsRequiredForPillar(int ordinal) {
        if (ordinal < 1 || ordinal > PILLAR_COUNT) return 0;
        return isFinalPillar(ordinal) ? FINAL_PILLAR_IMPACTS : 1;
    }

    /**
     * Live collisions still required to bring down the pillar that is standing.
     *
     * <p>This is what the party watches during the finale: 3 on a fresh final
     * pillar, 2 after the shell cracks, 1 once the core is exposed, 0 when it
     * has collapsed.</p>
     */
    public static int impactsRemainingOnPillar(int acceptedImpacts) {
        int impacts = Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts));
        if (impacts >= REQUIRED_IMPACTS) return 0;
        if (impacts < MINOR_PILLARS) return 1;
        return REQUIRED_IMPACTS - impacts;
    }

    /**
     * Visual stage of the final pillar, 0..3.
     *
     * <p>0 = intact, 1 = outer shell cracked, 2 = a whole section broken and the
     * glowing core exposed, 3 = collapsed. The stages are enumerated rather than
     * inferred by the renderer so the model, the fractures and the boss bar can
     * never show different amounts of damage.</p>
     */
    public static int finalPillarStage(int acceptedImpacts) {
        int impacts = Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts));
        if (impacts <= MINOR_PILLARS) return 0;
        return impacts - MINOR_PILLARS;
    }

    /**
     * Whether the elite marksman skeleton is still standing at this impact count.
     *
     * <p>It rides the final pillar only, and it is a normal killable actor: once
     * players have killed it, it stays dead rather than being re-summoned by a
     * pillar. That is why the caller passes its own liveness.</p>
     */
    public static boolean eliteSkeletonAliveOn(int acceptedImpacts, boolean skeletonStillAlive) {
        if (!skeletonStillAlive) return false;
        int impacts = Math.max(0, Math.min(REQUIRED_IMPACTS, acceptedImpacts));
        // It appears when the final pillar rises, i.e. from the fourth impact on.
        return impacts >= MINOR_PILLARS && impacts < REQUIRED_IMPACTS
                && pillarOrdinal(impacts) == ELITE_SKELETON_PILLAR;
    }

    /**
     * Whether a surviving skeleton must drop from the collapsing final pillar.
     *
     * <p>True exactly when the final pillar is destroyed: the actor is then moved
     * to the arena floor instead of being orphaned on a pillar that no longer
     * exists.</p>
     */
    public static boolean eliteSkeletonFallsOn(int acceptedImpacts) {
        return acceptedImpacts >= REQUIRED_IMPACTS;
    }

    /**
     * Whether the Ravager may begin a seismic stomp now.
     *
     * <p>A stomp during a charge, a stun, the spawn frame or the completion
     * transition would either cancel a committed charge or fire at a party that
     * is still reading the charge line. Only ordinary combat qualifies.</p>
     */
    public static boolean mayStartStomp(State state, long nowMillis, long lastStompMillis) {
        return mayStartStomp(state, nowMillis, lastStompMillis, STOMP_COOLDOWN_TICKS);
    }

    /**
     * As {@link #mayStartStomp(State, long, long)}, with a party-scaled cooldown.
     *
     * <p>The runtime passes {@link #stompCooldownTicks(int)}, so a full raid
     * cannot chain stomps while a small squad keeps the base rhythm.</p>
     */
    public static boolean mayStartStomp(State state, long nowMillis, long lastStompMillis,
                                        int cooldownTicks) {
        if (state != State.CHASING && state != State.EXPOSED) return false;
        if (nowMillis < 0L || lastStompMillis < 0L) return false;
        int cooldown = Math.max(0, cooldownTicks);
        return nowMillis - lastStompMillis >= cooldown * 50L;
    }

    /** Slowness duration in ticks; three seconds is the authored value. */
    public static int stompSlowTicks() {
        return STOMP_SLOW_TICKS;
    }

    /**
     * Health the Ravager must keep while armour remains.
     *
     * <p>The party must land all five impacts, so the beast cannot be burned
     * down through its first stages. This returns a FLOOR rather than healing:
     * the runtime clamps the incoming damage, so a burst cannot drop it below
     * the floor and the health bar still traverses a wide range instead of
     * sitting pinned at full.</p>
     */
    public static double healthFloor(int acceptedImpacts, double maximumHealth) {
        if (maximumHealth <= 0.0D || !Double.isFinite(maximumHealth)) return 0.0D;
        if (acceptedImpacts >= REQUIRED_IMPACTS) return 0.0D;
        // One share of the bar per remaining plate keeps every stage visible
        // while guaranteeing the fight survives to the last pillar. The share is
        // the fraction of the bar the whole armour phase is allowed to give up,
        // divided across every plate, so it stays bounded when the roster grows.
        int remaining = REQUIRED_IMPACTS - Math.max(0, acceptedImpacts);
        double sharePerPlate = 0.5D / Math.max(1, ARMOUR_PLATES);
        return maximumHealth * sharePerPlate * remaining;
    }

    /**
     * Armour plates still bolted to the body, W6-03.
     *
     * <p>This is the number the custom boss bar shows and the number the client
     * uses to decide how many Netherite sections to draw. It is derived from the
     * authoritative impact count, so the bar, the model and the damage gate can
     * never disagree about how much armour is left.</p>
     */
    public static int remainingPlates(int acceptedImpacts) {
        return Math.max(0, ARMOUR_PLATES - Math.max(0, Math.min(ARMOUR_PLATES, acceptedImpacts)));
    }

    /** Whether the armour is fully gone and the Ram takes normal damage. */
    public static boolean armourBroken(int acceptedImpacts) {
        return acceptedImpacts >= REQUIRED_IMPACTS;
    }

    // ------------------------------------------------------------------
    // Bounded encounter difficulty, W6-03
    // ------------------------------------------------------------------

    /**
     * Extra Endermites a plate release may call in, by party size.
     *
     * <p>W6-03 asked to "increase encounter challenge with bounded scaling".
     * Unbounded scaling is what makes a raid wave unplayable: the adds double
     * with every extra player and the party drowns. This adds at most one extra
     * body per plate, and only once the party is big enough to actually split
     * damage, so a two-player run is exactly the fight it was and a full raid
     * feels denser without becoming a spawn flood.</p>
     *
     * <p>The hard {@link #ENDERMITES_CAP} still applies on top, so this can
     * never raise the ceiling -- it only decides whether the release asks for
     * the top of its existing roll.</p>
     */
    public static int releasePressureBonus(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        if (players < 4) return 0;
        if (players < 8) return 1;
        return 2;
    }

    /**
     * Charge cooldown for the current party, in ticks.
     *
     * <p>The base cooldown is pitched at a small squad. A large party surrounds
     * the Ram and can otherwise bait a charge every three seconds, which turns
     * the fight into a metronome. The cooldown only ever grows, and it is capped
     * so the beast never stands around long enough for the party to forget it
     * exists.</p>
     */
    public static int chargeCooldownTicks(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        int extra = Math.min(2, Math.max(0, (players - 2) / 3));
        return CHARGE_COOLDOWN_TICKS + extra * 40;
    }

    /**
     * Stomp cooldown for the current party, in ticks.
     *
     * <p>Same reasoning as the charge: a big party can bait stomps back to back.
     * The ceiling keeps the stomp a real period in the fight rather than a
     * one-off.</p>
     */
    public static int stompCooldownTicks(int livingPlayers) {
        int players = Math.max(1, livingPlayers);
        int extra = Math.min(2, Math.max(0, (players - 3) / 4));
        return STOMP_COOLDOWN_TICKS + extra * 60;
    }

    /** Maximum health for a party, including the armour-phase buffer. */
    public static double scaledMaximumHealth(int livingPlayers) {
        return healthForPlayers(livingPlayers);
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }
}
