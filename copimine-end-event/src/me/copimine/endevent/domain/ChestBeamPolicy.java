package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic rules for the approved signature ultimate: the GIANT
 * CENTER-CORE CHEST BEAM (specification section 7).
 *
 * <p>This is the owner's definitive choice and is deliberately not a plain
 * spherical area-of-effect. It is a real, dodgeable party mechanic: the boss
 * charges at the fixed Core anchor, locks a bounded number of readable lanes
 * at the frozen roster's current positions, fires finite swept rays along those
 * exact lanes, and only then staggers into recovery. A player who steps out of
 * an announced lane is safe; a player who stands in it is hit once.</p>
 *
 * <p>Pure by design: doubles and index ranges in, resolved {@link Aim} lanes
 * and hit tests out. Bukkit is not imported here, so the geometry, the lane
 * budget and the damage window are all exercised by plain Java tests.</p>
 */
public final class ChestBeamPolicy {

    /** Solo/duo: exactly one lane. */
    public static final int SOLO_LANES = 1;
    /** Upper bound for a full 20-player roster. */
    public static final int MAX_LANES = 3;
    /** Roster boundary below which only the solo lane budget applies. */
    public static final int SMALL_GROUP = 8;

    /** Swept ray length: the beam is finite and never reaches the whole world. */
    public static final double BEAM_RANGE = 26.0D;
    /** Swept capsule radius; a player outside it is safely clear of the lane. */
    public static final double BEAM_RADIUS = 1.7D;

    /** Charging silhouette: plates open, core exposed, aim locks. About 2.5 s. */
    public static final int CHARGE_TICKS = 50;
    /** The core fires. Damage is applied only inside this window. */
    public static final int FIRE_TICKS = 18;
    /** Aftermath: ribbons/shards decay, the Guardian staggers. */
    public static final int AFTERMATH_TICKS = 20;
    /** Whole authored ultimate, from charge start to control restored. */
    public static final int TOTAL_TICKS = CHARGE_TICKS + FIRE_TICKS + AFTERMATH_TICKS;
    /** Phase availability / noticeable cooldown so the ultimate cannot spam. */
    public static final int COOLDOWN_TICKS = 600;

    public static final double DAMAGE = 16.0D;
    public static final double KNOCKBACK = 0.85D;
    public static final double LIFT = 0.22D;

    /**
     * Owner's latest decision: the fired beam slowly hunts the selected player's
     * body instead of staying a completely frozen ray, but a player must still be
     * able to walk out of it. The per-tick turn is deliberately far slower than a
     * normal strafe: at the beam radius a body crosses the danger band in a few
     * ticks, while the lane needs many ticks to follow, so escape is real and the
     * damage stays dodgeable (specification 7.1.3: never follow perfectly).
     *
     * <p>0.0060 rad/tick is about 0.34 deg/tick, i.e. roughly 6 deg per second at
     * 20 TPS. It is visible across a whole charge but never a tracking laser.</p>
     */
    public static final double TRACK_RADIANS_PER_TICK = 0.0060D;
    /**
     * Extra velocity added toward the Core while a body is inside a live beam:
     * the beam gently pulls the chosen player in without locking them. It is far
     * weaker than the knockback on impact and weaker than normal walk speed, so a
     * determined player still leaves the lane.
     */
    public static final double PULL_PER_TICK = 0.028D;
    /** No pull is applied within this horizontal distance of the Core. */
    public static final double PULL_MIN_DISTANCE = 3.0D;

    private ChestBeamPolicy() {
    }

    /**
     * Rotate a lane toward a live target point by at most
     * {@link #TRACK_RADIANS_PER_TICK}. When the target already sits inside that
     * cone the direction snaps exactly onto it. A zero-length or non-finite
     * target keeps the previous direction, so a dead or teleported target can
     * never spin the lane.
     */
    public static Aim trackToward(Aim aim, double tx, double ty, double tz) {
        return trackToward(aim, tx, ty, tz, TRACK_RADIANS_PER_TICK);
    }

    /** As {@link #trackToward(Aim, double, double, double)} with an explicit cap. */
    public static Aim trackToward(Aim aim, double tx, double ty, double tz, double maxRadians) {
        if (aim == null) {
            return null;
        }
        double vx = tx - aim.ox();
        double vy = ty - aim.oy();
        double vz = tz - aim.oz();
        double targetLength = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (!(targetLength > 1.0E-6D) || !Double.isFinite(targetLength)) {
            return aim;
        }
        vx /= targetLength;
        vy /= targetLength;
        vz /= targetLength;
        double dot = aim.dx() * vx + aim.dy() * vy + aim.dz() * vz;
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        double angle = Math.acos(dot);
        double cap = Double.isFinite(maxRadians)
                ? Math.max(0.0D, maxRadians) : TRACK_RADIANS_PER_TICK;
        if (angle <= cap || angle < 1.0E-6D) {
            return new Aim(aim.ox(), aim.oy(), aim.oz(), vx, vy, vz);
        }
        double sinAngle = Math.sin(angle);
        if (sinAngle < 1.0E-9D) {
            // Exactly opposite: no single well-conditioned plane to rotate in.
            return aim;
        }
        double a = Math.sin(angle - cap) / sinAngle;
        double b = Math.sin(cap) / sinAngle;
        return new Aim(aim.ox(), aim.oy(), aim.oz(),
                aim.dx() * a + vx * b,
                aim.dy() * a + vy * b,
                aim.dz() * a + vz * b);
    }

    /**
     * Horizontal pull toward the Core while a body is inside the beam, returned
     * as {@code {vx, vy, vz}}. Null when there is no lane or the body already
     * stands on the Core volume, so a point-blank hit never becomes a hard grip.
     */
    public static double[] pullTowardCore(Aim aim, double px, double py, double pz) {
        if (aim == null) {
            return null;
        }
        double dx = aim.ox() - px;
        double dz = aim.oz() - pz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(distance) || distance <= PULL_MIN_DISTANCE) {
            return null;
        }
        return new double[]{dx / distance * PULL_PER_TICK, 0.0D, dz / distance * PULL_PER_TICK};
    }

    /**
     * One locked lane: an origin (the Core-side chest anchor) and a unit
     * direction captured at lock time. The direction never follows a fleeing
     * target afterwards, so the beam is a real mechanic to dodge.
     */
    public record Aim(double ox, double oy, double oz,
                      double dx, double dy, double dz) {
        public Aim {
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(length > 1.0E-6D) || !Double.isFinite(length)) {
                dx = 0.0D;
                dy = 0.0D;
                dz = 1.0D;
                length = 1.0D;
            }
            dx /= length;
            dy /= length;
            dz /= length;
        }

        /** The far end of the finite beam, for presentation. */
        public double tipX() {
            return ox + dx * BEAM_RANGE;
        }

        public double tipY() {
            return oy + dy * BEAM_RANGE;
        }

        public double tipZ() {
            return oz + dz * BEAM_RANGE;
        }
    }

    /** How many distinct lanes the ultimate may lock for a roster size. */
    public static int laneCount(int livingPlayers) {
        int players = Math.max(0, livingPlayers);
        if (players <= 2) {
            return SOLO_LANES;
        }
        if (players <= SMALL_GROUP) {
            return Math.min(MAX_LANES, 2);
        }
        return MAX_LANES;
    }

    /** Lock a lane from a beam origin onto a target point. */
    public static Aim aimAt(double ox, double oy, double oz,
                            double tx, double ty, double tz) {
        return new Aim(ox, oy, oz, tx - ox, ty - oy, tz - oz);
    }

    /**
     * A lane translated sideways and/or upward, keeping its direction.
     *
     * <p>The presentation layer draws two of these as the high-contrast edge
     * traces that mark the beam's real swept-capsule radius before release
     * (specification 9H.02, shot 3). Both rails are derived from the same
     * {@link Aim} the server damages with, so the drawn warning can never
     * disagree with the hit window: a player between the rails is inside the
     * lane, a player outside them is safe.</p>
     *
     * @param side offset along the lane's local right (positive = right)
     * @param up   offset along the lane's local up
     */
    public static Aim rail(Aim aim, double side, double up) {
        if (aim == null) {
            return null;
        }
        double dx = aim.dx(), dy = aim.dy(), dz = aim.dz();
        // Any vector not parallel to the axis works as the raw "right"; pick one
        // that stays well conditioned when the beam is near-vertical.
        double rx, ry, rz;
        if (Math.abs(dy) < 0.99D) {
            rx = -dz;
            ry = 0.0D;
            rz = dx;
        } else {
            rx = 0.0D;
            ry = -dz;
            rz = dy;
        }
        double rightLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (!(rightLength > 1.0E-6D)) {
            rx = 1.0D;
            ry = 0.0D;
            rz = 0.0D;
            rightLength = 1.0D;
        }
        rx /= rightLength;
        ry /= rightLength;
        rz /= rightLength;
        // Local up completes the frame: up = right x axis.
        double ux = ry * dz - rz * dy;
        double uy = rz * dx - rx * dz;
        double uz = rx * dy - ry * dx;
        return new Aim(
                aim.ox() + rx * side + ux * up,
                aim.oy() + ry * side + uy * up,
                aim.oz() + rz * side + uz * up,
                dx, dy, dz);
    }

    /** Distance along the lane axis; negative means the point is behind the core. */
    public static double along(Aim aim, double px, double py, double pz) {
        if (aim == null) {
            return Double.NaN;
        }
        return (px - aim.ox()) * aim.dx()
                + (py - aim.oy()) * aim.dy()
                + (pz - aim.oz()) * aim.dz();
    }

    /** Perpendicular distance from the lane axis: the swept-capsule radius test. */
    public static double perpendicular(Aim aim, double px, double py, double pz) {
        if (aim == null) {
            return Double.NaN;
        }
        double t = along(aim, px, py, pz);
        double cx = aim.ox() + aim.dx() * t;
        double cy = aim.oy() + aim.dy() * t;
        double cz = aim.oz() + aim.dz() * t;
        double ex = px - cx;
        double ey = py - cy;
        double ez = pz - cz;
        return Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    /**
     * True when a point is inside the finite swept ray. Never behind the core,
     * never past the range, always within the lane radius. This is the same
     * geometry the server applies to damage and the client draws as the core.
     */
    public static boolean beamHits(Aim aim, double px, double py, double pz) {
        if (aim == null) {
            return false;
        }
        double t = along(aim, px, py, pz);
        return t >= 0.0D && t <= BEAM_RANGE && perpendicular(aim, px, py, pz) <= BEAM_RADIUS;
    }

    /** True only during the charging silhouette. */
    public static boolean chargingAt(int elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < CHARGE_TICKS;
    }

    /**
     * True only during the firing window. The beam deals its single hit here,
     * so a lane stepped into after the window is harmless and a lane stepped
     * out of during the window is safe.
     */
    public static boolean damagesAt(int elapsedTicks) {
        return elapsedTicks >= CHARGE_TICKS && elapsedTicks < CHARGE_TICKS + FIRE_TICKS;
    }

    /** True during the decaying aftermath, when the Guardian staggers. */
    public static boolean aftermathAt(int elapsedTicks) {
        return elapsedTicks >= CHARGE_TICKS + FIRE_TICKS && elapsedTicks < TOTAL_TICKS;
    }

    /** Stage ordinal for the presentation layer: 0 charge, 1 fire, 2 aftermath. */
    public static int stageAt(int elapsedTicks) {
        if (elapsedTicks < CHARGE_TICKS) {
            return 0;
        }
        return elapsedTicks < CHARGE_TICKS + FIRE_TICKS ? 1 : 2;
    }

    /**
     * Distinct lane target indices for a roster, rotated by {@code seed} so the
     * ultimate does not always lock the same players first. Never returns more
     * than {@code lanes} entries and never duplicates an index.
     */
    public static int[] selectLaneIndices(int players, int lanes, long seed) {
        int boundedPlayers = Math.max(0, players);
        int boundedLanes = Math.max(0, Math.min(Math.min(lanes, MAX_LANES), boundedPlayers));
        if (boundedPlayers == 0 || boundedLanes == 0) {
            return new int[0];
        }
        int start = Math.floorMod(seed, boundedPlayers);
        List<Integer> picked = new ArrayList<>(boundedLanes);
        // Walk the roster from the rotated start so lanes stay distinct even
        // when a lane budget is smaller than the group.
        for (int step = 0; step < boundedPlayers && picked.size() < boundedLanes; step++) {
            picked.add(Math.floorMod(start + step, boundedPlayers));
        }
        int[] out = new int[picked.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = picked.get(i);
        }
        return out;
    }

    /**
     * Refuse the cast when the encounter cannot stage it fairly: no legal
     * center stand point, or a lethal full-arena firefront already running that
     * would overlap the aim window into unavoidable damage.
     */
    public static boolean mayCast(boolean hasLegalCenterStand, boolean lethalFirefrontActive) {
        return hasLegalCenterStand && !lethalFirefrontActive;
    }

    /** On cooldown until an absolute tick; also used for the live probe. */
    public static boolean onCooldown(long nowTick, long cooldownUntilTick) {
        return nowTick < cooldownUntilTick;
    }
}
