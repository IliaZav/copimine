package me.copimine.endevent.domain;

/**
 * Geometry and damage of the boss contact abilities.
 *
 * <p>The melee swipe and the ground slam are genuine contact moves: they resolve
 * against the boss body at the moment the animation commits. The chest strike is
 * a directed attack: after the authored launch tick a probe leaves the chest and
 * advances one step per tick, so the hit lands where the strike has actually
 * travelled instead of hitting a whole rectangular slab instantly.</p>
 */
public final class BossContactStrikePolicy {
    public static final double MELEE_SWIPE_RANGE = 4.5D;
    public static final double MELEE_SWIPE_FRONT_DOT = 0.1D;
    public static final double MELEE_SWIPE_DAMAGE = 8.0D;
    public static final double MELEE_SWIPE_KNOCKBACK = 0.35D;
    public static final double MELEE_SWIPE_LIFT = 0.3D;

    public static final double GROUND_SLAM_RANGE = 7.0D;
    public static final double GROUND_SLAM_VERTICAL = 4.0D;
    public static final double GROUND_SLAM_DAMAGE = 12.0D;
    public static final double GROUND_SLAM_KNOCKBACK = 0.55D;
    public static final double GROUND_SLAM_LIFT = 0.75D;

    public static final double CHEST_STRIKE_RANGE = 12.0D;
    public static final double CHEST_STRIKE_STEP = 1.2D;
    public static final double CHEST_STRIKE_RADIUS = 1.35D;
    public static final double CHEST_STRIKE_VERTICAL = 3.0D;
    public static final double CHEST_STRIKE_DAMAGE = 10.0D;
    public static final double CHEST_STRIKE_KNOCKBACK = 0.55D;
    public static final double CHEST_STRIKE_LIFT = 0.32D;
    /** Authored launch tick of the chest animation, counted from the cast resolution. */
    public static final int CHEST_STRIKE_LAUNCH_TICKS = 6;
    /** Chest origin relative to the boss body height. */
    public static final double CHEST_HEIGHT_RATIO = 0.62D;

    private BossContactStrikePolicy() {
    }

    public static double damage(EndRiftAiPolicy.BossSpell spell) {
        return switch (spell) {
            case MELEE_SWIPE -> MELEE_SWIPE_DAMAGE;
            case CHEST_STRIKE -> CHEST_STRIKE_DAMAGE;
            case GROUND_SLAM -> GROUND_SLAM_DAMAGE;
            default -> 0.0D;
        };
    }

    public static double knockback(EndRiftAiPolicy.BossSpell spell) {
        return switch (spell) {
            case MELEE_SWIPE -> MELEE_SWIPE_KNOCKBACK;
            case CHEST_STRIKE -> CHEST_STRIKE_KNOCKBACK;
            case GROUND_SLAM -> GROUND_SLAM_KNOCKBACK;
            default -> 0.0D;
        };
    }

    public static double lift(EndRiftAiPolicy.BossSpell spell) {
        return switch (spell) {
            case MELEE_SWIPE -> MELEE_SWIPE_LIFT;
            case CHEST_STRIKE -> CHEST_STRIKE_LIFT;
            case GROUND_SLAM -> GROUND_SLAM_LIFT;
            default -> 0.0D;
        };
    }

    /** Number of sweep steps the chest strike needs to cover its range. */
    public static int chestStrikeTicks() {
        return (int) Math.ceil(CHEST_STRIKE_RANGE / CHEST_STRIKE_STEP);
    }

    /** Probe distance from the chest after {@code tick} sweep steps (bounded by the range). */
    public static double chestProbeDistance(int tick) {
        if (tick <= 0) {
            return 0.0D;
        }
        return Math.min(CHEST_STRIKE_RANGE, tick * CHEST_STRIKE_STEP);
    }

    /**
     * A target is inside the chest strike when the travelled probe is level with
     * it: never behind the chest, never past the range, within the strike radius
     * laterally and inside the authored vertical band.
     */
    public static boolean chestStrikeHits(double probeDistance, double along, double lateral, double vertical) {
        if (along <= 0.0D || along > CHEST_STRIKE_RANGE) {
            return false;
        }
        if (Math.abs(along - probeDistance) > CHEST_STRIKE_RADIUS) {
            return false;
        }
        return lateral <= CHEST_STRIKE_RADIUS && Math.abs(vertical) <= CHEST_STRIKE_VERTICAL;
    }

    /**
     * Only damage that actually reached the target may produce a hit reaction:
     * an immune, invulnerable or cancelled hit leaves the creature untouched and
     * must not knock it back or flash it.
     */
    public static boolean landed(double healthBefore, double healthAfter, boolean dead) {
        return dead || healthAfter < healthBefore - 1.0E-6D;
    }
}