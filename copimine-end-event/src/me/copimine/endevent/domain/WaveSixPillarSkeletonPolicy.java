package me.copimine.endevent.domain;

/**
 * Authoritative rules for the Wave 6 elite skeleton that holds the final pillar.
 *
 * <p>This is the ONLY skeleton-on-a-pillar mechanic in the whole event. The
 * master rebuild is explicit that no boss phase may add skeleton towers, so this
 * policy is deliberately scoped to Wave 6 and never consulted by the Rift
 * Guardian.</p>
 *
 * <p>The skeleton is a normal killable actor. It patrols the top platform,
 * fires real modelled bone projectiles with a triple shot and a second elite
 * ability, and threatens the party while they line the Ram up. When the final
 * pillar is destroyed a survivor drops to the arena floor and keeps fighting
 * rather than becoming an unreachable orphan on a pillar that no longer
 * exists.</p>
 */
public final class WaveSixPillarSkeletonPolicy {
    /** Where the skeleton stands, measured from the final pillar's centre. */
    public static final double PLATFORM_HEIGHT = 7.6D;
    /** How far it may wander across the top platform, in blocks. */
    public static final double PATROL_RADIUS = 2.6D;

    public static final int TRIPLE_SHOT_ARROWS = 3;
    /** Half-angle between the spread arrows, in degrees. */
    public static final double TRIPLE_SHOT_SPREAD_DEGREES = 7.0D;
    public static final double TRIPLE_SHOT_SPEED = 2.4D;

    /**
     * The second ability: real arrows that fall out of the air onto the target.
     *
     * <p>The shipped "bone rain" was a second horizontal fan fired from the
     * skeleton's bow, so it was the triple shot with a different spread and the
     * name promised a mechanic the wave never had. A rain volley now spawns its
     * arrows high above the target and lets gravity carry them down, which is a
     * genuinely different attack: it cannot be dodged sideways from cover and it
     * forces the party to keep moving rather than stand behind the Ram.</p>
     */
    public static final int BONE_RAIN_ARROWS = 3;
    /** Horizontal radius of the landing pattern around the target, in blocks. */
    public static final double BONE_RAIN_SPREAD = 1.7D;
    /** Spawn height above the target's feet, in blocks. */
    public static final double BONE_RAIN_HEIGHT = 11.0D;
    /** Initial downward push, so the drop is readable from the first tick. */
    public static final double BONE_RAIN_SPEED = 0.55D;
    /** How close to the floor the actor must be before a fall counts as landed. */
    public static final double FALL_LANDING_TOLERANCE = 0.9D;
    /**
     * How long a real fall may take before the runtime settles it on the floor.
     *
     * <p>The platform is 7.6 blocks up, so a clean fall resolves in well under
     * a second. This ceiling only exists so a lip or a slab under the pillar
     * cannot leave the marksman hanging above a pillar that no longer exists.</p>
     */
    public static final int FALL_TIMEOUT_TICKS = 60;

    public static final int TRIPLE_SHOT_COOLDOWN_TICKS = 70;
    public static final int BONE_RAIN_COOLDOWN_TICKS = 120;
    /** It repositions this often so it is never a stationary turret. */
    public static final int REPOSITION_TICKS = 50;
    /** Arrow lifetime ceiling, so a stray shot can never live forever. */
    public static final int PROJECTILE_LIFETIME_TICKS = 120;

    public static final double HEALTH = 60.0D;
    public static final double ARROW_DAMAGE = 4.0D;

    private WaveSixPillarSkeletonPolicy() {
    }

    /** Which elite ability the skeleton should use, if any. */
    public enum Ability { NONE, TRIPLE_SHOT, BONE_RAIN }

    /**
     * Choose the next ability.
     *
     * <p>The heavier volley is preferred once it is off cooldown so the two
     * abilities actually alternate instead of the triple shot monopolising the
     * fight. Both respect their own cooldown, and neither fires without a live
     * target.</p>
     *
     * @param nowTick      current encounter tick
     * @param lastTriple    tick of the last triple shot, or a sentinel before the first
     * @param lastBoneRain  tick of the last bone rain, or a sentinel before the first
     * @param hasTarget     whether an eligible player is visible and reachable
     */
    public static Ability nextAbility(int nowTick, int lastTriple, int lastBoneRain, boolean hasTarget) {
        if (!hasTarget || nowTick < 0) return Ability.NONE;
        if (elapsed(nowTick, lastBoneRain) >= BONE_RAIN_COOLDOWN_TICKS) return Ability.BONE_RAIN;
        if (elapsed(nowTick, lastTriple) >= TRIPLE_SHOT_COOLDOWN_TICKS) return Ability.TRIPLE_SHOT;
        return Ability.NONE;
    }

    private static int elapsed(int now, int last) {
        // A negative or future sentinel means "never used", which is always ready.
        if (last < 0 || last > now) return Integer.MAX_VALUE;
        return now - last;
    }

    /**
     * Horizontal offsets for the triple shot, as unit vectors.
     *
     * <p>The centre arrow is aimed straight at the target; the two flanking
     * arrows are rotated by the spread angle in opposite directions. The caller
     * supplies the already-normalised aim direction so this stays pure.</p>
     *
     * @return {x,z} pairs, always {@link #TRIPLE_SHOT_ARROWS} of them
     */
    public static double[][] tripleShotDirections(double aimX, double aimZ) {
        return spread(aimX, aimZ, TRIPLE_SHOT_ARROWS, TRIPLE_SHOT_SPREAD_DEGREES);
    }

    /**
     * Deterministic landing offsets for one rain volley.
     *
     * <p>The pattern is centred on the target and spread on a golden-angle ring,
     * so a volley always covers the same shape relative to the victim without
     * needing randomness. Offsets are horizontal only; the runtime adds
     * {@link #BONE_RAIN_HEIGHT} above the target's feet.</p>
     *
     * @return {@code {dx,dz}} pairs, always {@link #BONE_RAIN_ARROWS} of them
     */
    public static double[][] boneRainOffsets() {
        double[][] out = new double[BONE_RAIN_ARROWS][];
        for (int i = 0; i < BONE_RAIN_ARROWS; i++) {
            if (i == 0) {
                out[i] = new double[]{0.0D, 0.0D};
                continue;
            }
            double angle = (i - 1) * 2.399963D;
            double radius = BONE_RAIN_SPREAD;
            out[i] = new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
        }
        return out;
    }

    /**
     * Fan a set of unit directions symmetrically around the aim vector.
     *
     * <p>A degenerate aim produces no arrows rather than a burst along the axis
     * origin, so a skeleton that has lost its target cannot fire into itself.</p>
     */
    public static double[][] spread(double aimX, double aimZ, int count, double halfAngleDegrees) {
        double length = Math.hypot(aimX, aimZ);
        if (!Double.isFinite(length) || length < 1.0E-6D || count < 1) return new double[0][];
        double baseX = aimX / length;
        double baseZ = aimZ / length;
        double[][] out = new double[count][];
        for (int i = 0; i < count; i++) {
            // Spread evenly across [-half, +half].
            double offset = count == 1 ? 0.0D
                    : -halfAngleDegrees + (2.0D * halfAngleDegrees) * i / (count - 1);
            double radians = Math.toRadians(offset);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            out[i] = new double[]{
                    baseX * cos - baseZ * sin,
                    baseX * sin + baseZ * cos
            };
        }
        return out;
    }

    /** Whether the skeleton should pick a new spot on the platform. */
    public static boolean shouldReposition(int nowTick, int lastMoveTick) {
        if (nowTick < 0) return false;
        if (lastMoveTick < 0 || lastMoveTick > nowTick) return true;
        return nowTick - lastMoveTick >= REPOSITION_TICKS;
    }

    /**
     * A patrol offset across the platform, deterministic per step.
     *
     * <p>The skeleton must not stand still, and it must not wander off the
     * pillar either: the offset is always inside {@link #PATROL_RADIUS}.</p>
     */
    public static double[] patrolOffset(int step) {
        double angle = step * 2.399963D; // golden angle, so it never repeats a line
        double radius = PATROL_RADIUS * 0.75D;
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    /**
     * Whether a dropped skeleton has physically reached the arena floor.
     *
     * <p>The shipped drop teleported the actor to the floor and declared itself
     * landed in the same call, so the "fall" was a position write that a client
     * could never render and a checkpoint could record before any physics ran.
     * The fall is now real: gravity is enabled and this predicate observes the
     * actor's own ground contact near the floor before the drop is treated as
     * complete.</p>
     *
     * @param feetY    the actor's current feet Y after physics
     * @param floorY   the encounter floor Y
     * @param grounded whether the server reports the actor on the ground
     */
    public static boolean hasLanded(double feetY, double floorY, boolean grounded) {
        return grounded && Double.isFinite(feetY) && Double.isFinite(floorY)
                && feetY >= floorY - FALL_LANDING_TOLERANCE
                && feetY <= floorY + 1.0D + FALL_LANDING_TOLERANCE;
    }

    /**
     * Whether a survivor drops to the floor.
     *
     * <p>Every accepted collision on the final pillar is checked; only the one
     * that collapses it moves the skeleton. A skeleton already dead never drops,
     * and a drop can only be triggered once because the pillar is gone after.</p>
     */
    public static boolean shouldFall(int acceptedImpacts, boolean skeletonAlive, boolean alreadyFallen) {
        return skeletonAlive && !alreadyFallen && RiftRamPolicy.eliteSkeletonFallsOn(acceptedImpacts);
    }

    /**
     * The encounter may only complete once the skeleton has been dealt with.
     *
     * <p>The Ram cannot be killed while any plate holds, so in practice the
     * skeleton either dies on the pillar or lands on the floor; either way the
     * wave must not leave it behind as a live actor.</p>
     */
    public static boolean encounterMayComplete(boolean skeletonAlive, boolean skeletonOnPillar) {
        // A skeleton still up on a destroyed pillar is an orphan: refuse.
        return !skeletonOnPillar;
    }
}
