package me.copimine.endevent.domain;

import java.util.List;

/**
 * V5.1 Crushing / Monstrous Gravity.
 *
 * <p>An arena-wide pressure field with no direct damage. Paper owns the actual
 * player motion, the real arrow physics and every restore; this policy only
 * decides whether the field may start, how long each beat lasts and which
 * concurrent hazards make it unfair.</p>
 */
public final class CrushingGravityPolicy {
    private CrushingGravityPolicy() {
    }

    /** Windup 1.6..2.2 s. */
    public static final int WARNING_MIN_TICKS = 32;
    public static final int WARNING_MAX_TICKS = 44;
    public static final int WARNING_TICKS = 38;
    /** Live field 5..7 s, plus a 1..2 s visible collapse at the end. */
    public static final int ACTIVE_MIN_TICKS = 100;
    public static final int ACTIVE_MAX_TICKS = 140;
    public static final int ACTIVE_TICKS = 120;
    public static final int COLLAPSE_TICKS = 30;
    /** Effective cooldown 60..90 s. */
    public static final int COOLDOWN_MIN_TICKS = 1200;
    public static final int COOLDOWN_MAX_TICKS = 1800;
    public static final int COOLDOWN_TICKS = 1500;
    /** The field deals exactly zero direct damage. */
    public static final double DIRECT_DAMAGE = 0.0D;

    /**
     * Downward acceleration applied to airborne players. Minecraft's vanilla
     * gravity is ~0.08 blocks/tick^2; this is a real additional pull, not a
     * potion label.
     */
    public static final double PLAYER_DOWNWARD_ACCELERATION = 0.085D;
    /** Jump impulse multiplier: jumping is suppressed but not removed. */
    public static final double JUMP_IMPULSE_SCALE = 0.55D;
    /** Horizontal steer stays intact: the player is always able to walk out. */
    public static final double HORIZONTAL_STEER_SCALE = 1.0D;
    /** Cap on the extra downward speed so nobody is slammed into the floor. */
    public static final double MAX_DOWNWARD_SPEED = -0.72D;

    /** Arrows fall under real physics: real gravity multiplier, not deletion. */
    public static final double ARROW_GRAVITY_MULTIPLIER = 3.0D;
    /** Newly fired arrows keep this fraction of their intended horizontal speed. */
    public static final double ARROW_HORIZONTAL_RETENTION = 0.55D;
    /** Existing in-flight arrows lose this fraction of horizontal speed once. */
    public static final double ARROW_INFLIGHT_BLEED = 0.55D;

    /** Hazard kinds that must not overlap an unavoidable gravity field. */
    public enum Hazard {
        CHEST_BEAM,
        ARENA_INFERNO,
        END_GEYSER,
        KAGUNE_VOID_GAP,
        FINAL_STRIKE,
        VOID_MARK_CHAIN
    }

    /** Controlled phases: Rift, Overload, Rage, and a grounded final stand. */
    public static boolean phaseEligible(BossPhase phase, boolean cocoonIntact) {
        if (phase == null || cocoonIntact) {
            return false;
        }
        return switch (phase) {
            case AWAKENING, HUNT -> false;
            case RIFT, OVERLOAD, RAGE -> true;
            case LAST_SEAL -> true;
        };
    }

    /** Never during an intact cocoon, for any reason. */
    public static boolean cocoonBlocks(boolean cocoonIntact) {
        return cocoonIntact;
    }

    /**
     * The coordinated hazard budget in one call: the field may start only when
     * no listed hazard already owns the arena and the cooldown has elapsed.
     */
    public static boolean mayCast(BossPhase phase, boolean cocoonIntact,
                                  long nowMillis, long readyAtMillis,
                                  List<Hazard> activeHazards) {
        if (!phaseEligible(phase, cocoonIntact) || nowMillis < readyAtMillis) {
            return false;
        }
        if (activeHazards == null || activeHazards.isEmpty()) {
            return true;
        }
        for (Hazard hazard : activeHazards) {
            if (hazard == null || hazard == Hazard.VOID_MARK_CHAIN) {
                // A bounded mark chain is escapable; it does not veto the field.
                continue;
            }
            return false;
        }
        return true;
    }

    /** The next legal start after this cast ends: full field plus cooldown. */
    public static long readyAt(long nowMillis) {
        return nowMillis + (ACTIVE_TICKS + COOLDOWN_TICKS) * 50L;
    }

    /** Total wall-clock lifetime of one cast, warning + field + collapse. */
    public static int totalTicks() {
        return WARNING_TICKS + ACTIVE_TICKS + COLLAPSE_TICKS;
    }

    /**
     * Phase of the field at {@code elapsedTicks}: the runtime draws a different
     * beat for each so the warning, the live field and the collapse never look
     * like one long constant effect.
     */
    public enum Beat {
        WARNING,
        LIVE,
        COLLAPSE,
        DONE
    }

    public static Beat beat(int elapsedTicks) {
        if (elapsedTicks < 0) {
            return Beat.DONE;
        }
        if (elapsedTicks < WARNING_TICKS) {
            return Beat.WARNING;
        }
        if (elapsedTicks < WARNING_TICKS + ACTIVE_TICKS) {
            return Beat.LIVE;
        }
        if (elapsedTicks < totalTicks()) {
            return Beat.COLLAPSE;
        }
        return Beat.DONE;
    }

    /** 0..1 progress of one beat; drives authored geometry without randomness. */
    public static double progress(int elapsedTicks) {
        Beat beat = beat(elapsedTicks);
        return switch (beat) {
            case WARNING -> elapsedTicks / (double) WARNING_TICKS;
            case LIVE -> (elapsedTicks - WARNING_TICKS) / (double) ACTIVE_TICKS;
            case COLLAPSE -> (elapsedTicks - WARNING_TICKS - ACTIVE_TICKS)
                    / (double) COLLAPSE_TICKS;
            case DONE -> 1.0D;
        };
    }

    /** The downward pull is only live during the field itself. */
    public static boolean pullActive(int elapsedTicks) {
        return beat(elapsedTicks) == Beat.LIVE;
    }

    /** A simple arena footprint used to scope every effect and every restore. */
    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || maxX <= minX || maxY <= minY || maxZ <= minZ) {
                throw new IllegalArgumentException("invalid gravity arena bounds");
            }
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public double centerX() {
            return (minX + maxX) / 2.0D;
        }

        public double centerY() {
            return (minY + maxY) / 2.0D;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0D;
        }

        public double radius() {
            return Math.max(maxX - minX, maxZ - minZ) / 2.0D;
        }
    }

    /** Extended downward pull for one airborne tick. */
    public static double nextVerticalVelocity(double current) {
        if (!Double.isFinite(current)) {
            return 0.0D;
        }
        return Math.max(MAX_DOWNWARD_SPEED, current - PLAYER_DOWNWARD_ACCELERATION);
    }

    /** Arrows fired while the field is live are slowed but still real. */
    public static double arrowHorizontalSpeed(double intended) {
        if (!Double.isFinite(intended) || intended <= 0.0D) {
            return 0.0D;
        }
        return intended * ARROW_HORIZONTAL_RETENTION;
    }

    /**
     * A field must never start when it would seal the only escape from a
     * Core-beam lane, a wall-to-wall inferno front, a geyser chain or a void
     * gap. The caller passes the live hazard set; this is the single veto.
     */
    public static boolean escapesRemain(List<Hazard> activeHazards, int liveEscapeRoutes) {
        if (liveEscapeRoutes <= 0) {
            return false;
        }
        if (activeHazards == null || activeHazards.isEmpty()) {
            return true;
        }
        for (Hazard hazard : activeHazards) {
            if (hazard == Hazard.CHEST_BEAM || hazard == Hazard.ARENA_INFERNO
                    || hazard == Hazard.END_GEYSER || hazard == Hazard.KAGUNE_VOID_GAP
                    || hazard == Hazard.FINAL_STRIKE) {
                return false;
            }
        }
        return true;
    }
}
