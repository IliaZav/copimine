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

    /** The second ability: a shorter, heavier bone volley. */
    public static final int BONE_RAIN_ARROWS = 2;
    public static final double BONE_RAIN_ARC_DEGREES = 24.0D;
    public static final double BONE_RAIN_SPEED = 1.9D;

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

    public static double[][] boneRainDirections(double aimX, double aimZ) {
        return spread(aimX, aimZ, BONE_RAIN_ARROWS, BONE_RAIN_ARC_DEGREES);
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
