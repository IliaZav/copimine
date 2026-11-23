package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.1 Warden contract layered on top of the real vanilla Warden.
 *
 * <p>Nothing here replaces native anger, vibration sensing, movement, close
 * attacks, Sonic Boom or Darkness. The custom scheduler is a subordinate
 * spice layer: one bounded major cast at a time, with a fair response window
 * between a stun and the next burst.</p>
 */
public final class WardenV5CombatPolicy {
    private WardenV5CombatPolicy() {
    }

    /** The final V5.1 Warden custom actions. */
    public enum Action {
        /** 5 blocks across by 4 blocks forward committed crushing sweep. */
        FRONTAL_SWEEP,
        /** Self-centred, wall-piercing radius-6 sonic detonation. */
        ECHO_SPACE_EXPLOSION,
        /** Historic movement-trace trap. */
        SCULK_RESONANCE,
        /** Native vanilla behaviour and existing line strikes. */
        NATIVE,
        NONE
    }

    /** Old presentation ids mapped onto the final vocabulary. */
    public static Action action(String legacyId) {
        if (legacyId == null || legacyId.isBlank()) {
            return Action.NONE;
        }
        String canonical = legacyId.trim().toUpperCase(java.util.Locale.ROOT);
        if (canonical.startsWith("WAVE7_")) {
            canonical = canonical.substring("WAVE7_".length());
        }
        return switch (canonical) {
            case "WARDEN_WIDE_STRIKE", "WARDEN_FRONTAL_SWEEP", "WARDEN_SWEEP" ->
                    Action.FRONTAL_SWEEP;
            case "WARDEN_SLAM", "WARDEN_ECHO_SPACE", "WARDEN_ECHO_EXPLOSION" ->
                    Action.ECHO_SPACE_EXPLOSION;
            case "WARDEN_SCULK", "WARDEN_SCULK_RESONANCE" -> Action.SCULK_RESONANCE;
            case "WARDEN_NARROW_STRIKE", "WARDEN_SONIC_LUNGE", "WARDEN_NATIVE" -> Action.NATIVE;
            case "NONE", "NONE_INTERNAL" -> Action.NONE;
            default -> Action.NONE;
        };
    }

    // ------------------------------------------------------------------
    // 5 x 4 Frontal Crushing Sweep
    // ------------------------------------------------------------------

    /** Exactly five blocks across: 2.5 either side of the committed heading. */
    public static final double SWEEP_WIDTH_BLOCKS = 5.0D;
    public static final double SWEEP_HALF_WIDTH_BLOCKS = SWEEP_WIDTH_BLOCKS / 2.0D;
    /** Exactly four blocks forward. */
    public static final double SWEEP_FORWARD_BLOCKS = 4.0D;
    /** 0.7..1.0 s of full-body preparation. */
    public static final int SWEEP_PREP_TICKS = 40;
    /** Player hitbox vertical tolerance; a legal player hitbox must intersect. */
    public static final double SWEEP_VERTICAL_BLOCKS = 2.0D;
    /** Movement stun, owned and expiring. */
    public static final int SWEEP_STUN_TICKS = 20;
    /** Slowness after an accepted hit. */
    public static final int SWEEP_SLOW_TICKS = 60;
    /** Slowness amplifier for the 60-tick slow (Slowness II reads as heavy). */
    public static final int SWEEP_SLOW_AMPLIFIER = 1;
    /**
     * Movement-lock amplifier for the 20-tick stun. Slowness VI removes almost
     * all walking input; the runtime also zeroes velocity once on application so
     * the lock is immediate, and the effect expiry is the owner of the release.
     */
    public static final int SWEEP_STUN_SLOW_AMPLIFIER = 6;
    /** One accepted contact per target per cast, inside this window. */
    public static final int SWEEP_HIT_WINDOW_TICKS = 2;
    /** Base contact damage before party scaling. */
    public static final double SWEEP_DAMAGE = 11.0D;
    /** A sweep cannot be repeated before this. */
    public static final int SWEEP_COOLDOWN_TICKS = 140;

    /**
     * True when a point is inside the committed front volume.
     *
     * @param forward blocks in front of the frozen facing (must be > 0)
     * @param lateral signed sideways offset
     * @param vertical player's feet minus the Warden's feet
     */
    public static boolean sweepHits(double forward, double lateral, double vertical) {
        if (!Double.isFinite(forward) || !Double.isFinite(lateral)
                || !Double.isFinite(vertical)) {
            return false;
        }
        return forward > 0.0D && forward <= SWEEP_FORWARD_BLOCKS
                && Math.abs(lateral) <= SWEEP_HALF_WIDTH_BLOCKS
                && Math.abs(vertical) <= SWEEP_VERTICAL_BLOCKS;
    }

    /** The sweep volume as an explicit diagnostic description. */
    public static String sweepVolume() {
        return String.format(java.util.Locale.ROOT, "5x4x%.1f", SWEEP_VERTICAL_BLOCKS * 2.0D);
    }

    // ------------------------------------------------------------------
    // Echo Space Explosion
    // ------------------------------------------------------------------

    /** Self-centred sphere radius. */
    public static final double ECHO_RADIUS_BLOCKS = 6.0D;
    /** 1.5..2.0 s warning; 1.7 s is audible on both sides of a wall. */
    public static final int ECHO_WARNING_TICKS = 34;
    public static final double ECHO_DAMAGE = 10.0D;
    /** Vertical reach is the same sphere, not a cylinder. */
    public static final int ECHO_COOLDOWN_MIN_TICKS = 440;
    public static final int ECHO_COOLDOWN_MAX_TICKS = 700;
    public static final int ECHO_COOLDOWN_TICKS = 600;
    /** Real Sonic-Boom-style knockback on a landed detonation. */
    public static final double ECHO_KNOCKBACK = 0.85D;
    public static final double ECHO_LIFT = 0.42D;

    /**
     * The burst is a sphere and ignores physical cover on purpose: it is the
     * one Warden attack that is explicitly wall-piercing.
     */
    public static boolean echoHits(double dx, double dy, double dz) {
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(distanceSquared)
                && distanceSquared <= ECHO_RADIUS_BLOCKS * ECHO_RADIUS_BLOCKS;
    }

    // ------------------------------------------------------------------
    // Sculk Resonance / Resonant Footsteps
    // ------------------------------------------------------------------

    public static final int TRACE_MIN_BLOCKS = 3;
    public static final int TRACE_MAX_BLOCKS = 5;
    /** Recent history window the trace is allowed to read. */
    public static final int TRACE_HISTORY_TICKS = 20;
    /** 1.1..1.4 s warning, 1.25 s in the middle. */
    public static final int TRACE_WARNING_TICKS = 25;
    public static final int TRACE_COOLDOWN_TICKS = 500;
    /** A quiet player is not picked: sneak window checked before the lock. */
    public static final int TRACE_QUIET_SUPPRESSION_TICKS = 25;
    public static final double TRACE_DAMAGE = 6.0D;
    /** Maximum simultaneous tracks: one for a pair, two for a larger party. */
    public static final int TRACE_MAX_TRACKS = 2;
    /** Minimum separation between two selected tracks, in blocks. */
    public static final double TRACE_MIN_SEPARATION_BLOCKS = 3.0D;

    /** One recent footstep sample, already validated by the server. */
    public record Step(double x, double y, double z, int ageTicks, boolean sneaking,
                       boolean moving) {
    }

    /**
     * A player may be selected only when it is moving and has not been quiet
     * inside the suppression window. Sneaking before the lock is therefore a
     * real counter, and there is no late re-target after the lock.
     */
    public static boolean maySelect(List<Step> recent) {
        if (recent == null || recent.isEmpty()) {
            return false;
        }
        boolean moving = false;
        for (Step step : recent) {
            if (step.ageTicks() < 0 || step.ageTicks() > TRACE_HISTORY_TICKS) {
                continue;
            }
            if (step.sneaking() && step.ageTicks() <= TRACE_QUIET_SUPPRESSION_TICKS) {
                return false;
            }
            if (step.moving()) {
                moving = true;
            }
        }
        return moving;
    }

    /**
     * Shorten a recent path to the announced 3..5 block trace.
     *
     * <p>The trace is historic and already walked; it is not a prediction. The
     * caller freezes the returned points and the endpoint, draws them, and then
     * resolves damage against the announced segment only.</p>
     */
    public static List<double[]> tracePolyline(List<Step> recent, int blocks) {
        int wanted = Math.max(TRACE_MIN_BLOCKS, Math.min(TRACE_MAX_BLOCKS, blocks));
        List<double[]> points = new ArrayList<>();
        if (recent == null) {
            return points;
        }
        for (Step step : recent) {
            if (step.ageTicks() < 0 || step.ageTicks() > TRACE_HISTORY_TICKS) {
                continue;
            }
            if (points.isEmpty()) {
                points.add(new double[]{step.x(), step.y(), step.z()});
                continue;
            }
            double[] last = points.get(points.size() - 1);
            if (Math.hypot(step.x() - last[0], step.z() - last[2]) >= 0.75D) {
                points.add(new double[]{step.x(), step.y(), step.z()});
            }
            if (points.size() >= wanted + 1) {
                break;
            }
        }
        return List.copyOf(points);
    }

    /**
     * Damage is resolved against the frozen corridor between the last two
     * points: sidestepping perpendicular after the lock is a genuine dodge.
     */
    public static boolean traceHits(double pointX, double pointZ,
                                    double fromX, double fromZ,
                                    double toX, double toZ, double halfWidth) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0E-6D) {
            return Math.hypot(pointX - fromX, pointZ - fromZ) <= halfWidth;
        }
        double t = ((pointX - fromX) * dx + (pointZ - fromZ) * dz) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = fromX + dx * t;
        double closestZ = fromZ + dz * t;
        return Math.hypot(pointX - closestX, pointZ - closestZ) <= halfWidth;
    }

    // ------------------------------------------------------------------
    // Fair scheduling
    // ------------------------------------------------------------------

    /**
     * A stunned player must never be fed straight into the radial burst.
     *
     * <p>The burst needs a full recovery of the 20-tick stun plus one fair
     * reaction second before it can start its own warning.</p>
     */
    public static final int SWEEP_TO_ECHO_GAP_TICKS = SWEEP_STUN_TICKS + 20;

    public static boolean mayFollowSweepWithEcho(int ticksSinceSweepImpact) {
        return ticksSinceSweepImpact >= SWEEP_TO_ECHO_GAP_TICKS;
    }

    /** One custom major at a time. */
    public static boolean mayStartCustomCast(boolean customCastActive,
                                             boolean nativeAttackInProgress) {
        return !customCastActive;
    }

    /** Warning ticks for one final action. */
    public static int warningTicks(Action action) {
        return switch (action) {
            case FRONTAL_SWEEP -> SWEEP_PREP_TICKS;
            case ECHO_SPACE_EXPLOSION -> ECHO_WARNING_TICKS;
            case SCULK_RESONANCE -> TRACE_WARNING_TICKS;
            case NATIVE -> 0;
            case NONE -> 0;
        };
    }

    /** Authored clip label used by the pose packet and diagnostics. */
    public static String clip(Action action) {
        return switch (action) {
            case FRONTAL_SWEEP -> "WARDEN_SWEEP";
            case ECHO_SPACE_EXPLOSION -> "WARDEN_ECHO_SPACE";
            case SCULK_RESONANCE -> "WARDEN_SCULK_TRACE";
            case NATIVE -> "WARDEN_NARROW_STRIKE";
            case NONE -> "NONE";
        };
    }

    /** Sound cue name for the shared authored audio bank. */
    public static String cue(Action action) {
        return switch (action) {
            case FRONTAL_SWEEP -> "warden_swing";
            case ECHO_SPACE_EXPLOSION -> "warden_sonic";
            case SCULK_RESONANCE -> "warden_sonic";
            case NATIVE -> "warden_swing";
            case NONE -> "warden_swing";
        };
    }
}
