package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Phase 3 (RIFT, 60-45% HP) — the Sequential 3x3 Geyser Wave.
 *
 * <p>The existing Phase 3 hazard is a set of <em>independent</em> locked
 * geysers selected around the arena ({@link RiftFractureEruptionPolicy}). The
 * owner's assignment adds a second, distinct attack: the Guardian captures one
 * living player's ground position, then lays a single straight strip of
 * <em>adjacent</em> 3x3-block geyser sections from the boss toward that frozen
 * position. The sections erupt in order — the one nearest the boss first, then
 * the next, and so on until the captured position is reached — so the danger
 * walks toward the locked player instead of appearing under them at once.</p>
 *
 * <p>The attack is deliberately not a homing column. The captured position is
 * frozen when the strip is planned; the boss never re-aims afterward. A player
 * who moves sideways leaves the strip and survives, which is the whole point:
 * the wave rewards repositioning rather than punishing it.</p>
 *
 * <h2>Geometry contract</h2>
 * <ul>
 *   <li>Every section owns a 3x3 floor footprint. Consecutive sections share an
 *       edge, so the strip is contiguous even on a diagonal, where the path
 *       becomes a staircase with no corner pocket a player could stand in.</li>
 *   <li>The strip starts at the boss tile and stops at the first section whose
 *       footprint is not fully legal — that is what "respect walls, ceilings,
 *       safe zones and arena bounds" means in block terms. A legal route always
 *       ends on the captured target's own tile.</li>
 * </ul>
 *
 * <h2>Timing contract</h2>
 * <ul>
 *   <li>Initial warning before the first eruption: 14 server ticks.</li>
 *   <li>Activation interval between consecutive eruptions: 5 ticks.</li>
 *   <li>Individual eruption (the damaging window): 10 ticks.</li>
 *   <li>Every section's ground crack is visible from the first tick, so the
 *       last section warns for longer than the first — never shorter.</li>
 * </ul>
 *
 * <p>The class is pure: it plans sections, resolves the timeline, and answers
 * "does this eruption hurt this player, and how". Worlds, blocks, sounds and
 * {@code EntityDamageEvent} stay in the Bukkit adapter.</p>
 */
public final class SequentialGeyserWavePolicy {
    /** One section covers a 3x3 block footprint. */
    public static final int SECTION_SIZE = 3;
    /** Warning before the first section erupts. */
    public static final int INITIAL_WARNING_TICKS = 14;
    /** Gap between two consecutive section eruptions. */
    public static final int ACTIVATION_INTERVAL_TICKS = 5;
    /** The damaging window of one section. */
    public static final int ERUPTION_TICKS = 10;
    /** Visible collapse after the damaging window. */
    public static final int COLLAPSE_TICKS = 6;
    /** Restoration is attempted inside this window before a retry. */
    public static final int RESTORE_TICKS = 20;
    /**
     * Hard ceiling on the strip length. A four-connected diagonal needs
     * roughly two thirds of the path length in sections, so twenty covers a
     * ~42-block diagonal — the whole playable arena at the boss's aggressive
     * Phase 3 range — while keeping the durable journal and the per-tick
     * mutation bounded. A route that would need more is refused outright
     * rather than silently truncated, so a cast can never lie about coverage.
     */
    public static final int MAX_SECTIONS = 20;
    /** Damage radius of one section. Matches the Phase 3 geyser footprint. */
    public static final double GEYSER_RADIUS_BLOCKS = 1.9D;
    /** Bounded knock-up applied once per cast; never a stunlock. */
    public static final double ERUPTION_LAUNCH_VELOCITY = 1.1D;
    public static final double MAX_LAUNCH_Y = 0.7D;
    /** A section larger than this in either direction is never authored. */
    public static final int MAX_SECTION_EXTENT = SECTION_SIZE - 1;

    private SequentialGeyserWavePolicy() {
    }

    // --- geometry -----------------------------------------------------------

    /** One 3x3 floor footprint: blocks {@code [minX..minX+2] x [minZ..minZ+2]}. */
    public record Section(int index, int minX, int minZ) {
        public Section {
            if (index < 0) {
                throw new IllegalArgumentException("section index must be non-negative");
            }
        }

        /** World-space centre of the footprint, on the half-block. */
        public double centerX() {
            return minX + (SECTION_SIZE - 1) / 2.0D + 0.5D;
        }

        public double centerZ() {
            return minZ + (SECTION_SIZE - 1) / 2.0D + 0.5D;
        }

        /** The nine floor cells, row-major, starting at the minimum corner. */
        public List<Cell> cells() {
            List<Cell> cells = new ArrayList<>(SECTION_SIZE * SECTION_SIZE);
            for (int dx = 0; dx < SECTION_SIZE; dx++) {
                for (int dz = 0; dz < SECTION_SIZE; dz++) {
                    cells.add(new Cell(minX + dx, minZ + dz));
                }
            }
            return List.copyOf(cells);
        }

        /** True when the other section shares an edge (never only a corner). */
        public boolean adjacentTo(Section other) {
            if (other == null) {
                return false;
            }
            int dx = Math.abs(minX - other.minX);
            int dz = Math.abs(minZ - other.minZ);
            return (dx == SECTION_SIZE && dz == 0) || (dx == 0 && dz == SECTION_SIZE);
        }
    }

    /** A single floor cell of a section, in block coordinates. */
    public record Cell(int x, int z) {
    }

    /** The planned strip plus whether it really ends on the captured target. */
    public record Plan(List<Section> sections, boolean coversTarget) {
        public Plan {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        public boolean isViable() {
            return !sections.isEmpty();
        }
    }

    /**
     * Lays the contiguous strip from the caster to the captured position.
     *
     * <p>The walk is a four-connected Bresenham: exactly one axis moves per
     * step, so a diagonal route is a staircase of edge-adjacent sections
     * instead of a chain of corner-touching squares with an avoidable pocket at
     * every joint. {@code legal} is asked about each section in order; the strip
     * stops at the first refusal, which is how a wall, a ceiling, the Core or a
     * protected cell ends the wave without any tunnelling.</p>
     *
     * @param casterX caster ground X (block space, fractional)
     * @param casterZ caster ground Z
     * @param targetX frozen target ground X
     * @param targetZ frozen target ground Z
     * @param legal   per-cell legality, evaluated for the whole footprint
     */
    public static Plan plan(double casterX, double casterZ, double targetX, double targetZ,
                            Predicate<Section> legal) {
        if (!Double.isFinite(casterX) || !Double.isFinite(casterZ)
                || !Double.isFinite(targetX) || !Double.isFinite(targetZ) || legal == null) {
            return new Plan(List.of(), false);
        }
        int startTileX = Math.floorDiv((int) Math.floor(casterX), SECTION_SIZE);
        int startTileZ = Math.floorDiv((int) Math.floor(casterZ), SECTION_SIZE);
        int targetTileX = Math.floorDiv((int) Math.floor(targetX), SECTION_SIZE);
        int targetTileZ = Math.floorDiv((int) Math.floor(targetZ), SECTION_SIZE);

        List<Section> sections = new ArrayList<>();
        int x = startTileX;
        int z = startTileZ;
        int dx = Math.abs(targetTileX - startTileX);
        int dz = Math.abs(targetTileZ - startTileZ);
        int stepX = Integer.signum(targetTileX - startTileX);
        int stepZ = Integer.signum(targetTileZ - startTileZ);
        int err = dx - dz;
        boolean truncated = false;
        boolean reachedTarget = false;
        while (true) {
            Section candidate = new Section(sections.size(), x * SECTION_SIZE, z * SECTION_SIZE);
            if (!legal.test(candidate)) {
                break;
            }
            sections.add(candidate);
            if (x == targetTileX && z == targetTileZ) {
                reachedTarget = true;
                break;
            }
            if (sections.size() >= MAX_SECTIONS) {
                truncated = true;
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += stepX;
            } else if (e2 < dx) {
                err += dx;
                z += stepZ;
            }
        }
        // A route that ran out of budget did not reach the captured player, so
        // the adapter refuses the whole cast rather than hitting a partial lie.
        boolean coversTarget = reachedTarget && !truncated;
        return new Plan(sections, coversTarget);
    }

    // --- timeline -----------------------------------------------------------

    public enum Phase {
        WARNING,
        ERUPTION,
        COLLAPSE,
        RESTORE,
        DONE
    }

    /**
     * The absolute tick window of a whole strip, resolved once at cast start.
     *
     * <p>All warnings begin together; the first eruption is
     * {@link #INITIAL_WARNING_TICKS} later and each following section erupts
     * {@link #ACTIVATION_INTERVAL_TICKS} after the previous one. A section's
     * warning therefore lasts at least the initial 14 ticks and is longer the
     * further it sits down the strip, so a later section is never less fair
     * than the first.</p>
     */
    public record Timeline(long startTick, int sections) {
        public Timeline {
            startTick = Math.max(0L, startTick);
            sections = Math.max(0, sections);
        }

        public long eruptionStart(int index) {
            return startTick + INITIAL_WARNING_TICKS
                    + (long) Math.max(0, index) * ACTIVATION_INTERVAL_TICKS;
        }

        public long eruptionEnd(int index) {
            return eruptionStart(index) + ERUPTION_TICKS;
        }

        public long collapseEnd(int index) {
            return eruptionEnd(index) + COLLAPSE_TICKS;
        }

        public long restoreEnd(int index) {
            return collapseEnd(index) + RESTORE_TICKS;
        }

        /** The tick by which every section is restored; no hazard may outlive it. */
        public long endTick() {
            return sections <= 0 ? startTick : restoreEnd(sections - 1);
        }

        public Phase phase(int index, long nowTick) {
            if (sections <= 0 || index < 0 || index >= sections) {
                return Phase.DONE;
            }
            if (nowTick < eruptionStart(index)) {
                return nowTick < startTick ? Phase.WARNING : Phase.WARNING;
            }
            if (nowTick < eruptionEnd(index)) {
                return Phase.ERUPTION;
            }
            if (nowTick < collapseEnd(index)) {
                return Phase.COLLAPSE;
            }
            if (nowTick < restoreEnd(index)) {
                return Phase.RESTORE;
            }
            return Phase.DONE;
        }

        /** Crack width of a section: 0 at the cast, 1 on the eruption tick. */
        public double width(int index, long nowTick) {
            long from = startTick;
            long to = eruptionStart(index);
            if (nowTick <= from) {
                return 0.06D;
            }
            if (nowTick >= to) {
                return 1.0D;
            }
            double span = Math.max(1.0D, (double) (to - from));
            return 0.06D + 0.94D * (nowTick - from) / span;
        }

        /** Visible column height in blocks; zero outside the eruption window. */
        public double geyserHeight(int index, long nowTick) {
            Phase phase = phase(index, nowTick);
            if (phase == Phase.ERUPTION) {
                double span = Math.max(1.0D, (double) (collapseEnd(index) - eruptionEnd(index)));
                double settle = Math.min(1.0D, (nowTick - eruptionStart(index)) / span);
                return RiftFractureEruptionPolicy.MAX_GEYSER_HEIGHT - 1.2D * settle;
            }
            if (phase == Phase.COLLAPSE) {
                double span = Math.max(1.0D, (double) (restoreEnd(index) - collapseEnd(index)));
                double left = 1.0D - Math.min(1.0D, (nowTick - collapseEnd(index) + COLLAPSE_TICKS) / span);
                return RiftFractureEruptionPolicy.MIN_GEYSER_HEIGHT * Math.max(0.0D, left);
            }
            return 0.0D;
        }

        /** True only while this section is really erupting. */
        public boolean damages(int index, long nowTick) {
            return phase(index, nowTick) == Phase.ERUPTION;
        }

        /** The one authored hit keyframe of a section, used to scope the ledger. */
        public long eruptionTick(int index) {
            return eruptionStart(index);
        }
    }

    // --- damage -------------------------------------------------------------

    /**
     * Per-cast damage ledger.
     *
     * <p>The whole strip is one cast, so a player standing on a section joint is
     * never hit twice by two neighbouring eruptions. The first section that
     * covers the player scores the single hit; every later section skips them.</p>
     */
    public static String scopedKey(String castId, UUID player) {
        return (castId == null ? "" : castId) + ":" + (player == null ? "" : player);
    }

    public static boolean mayDamagePlayer(String castId, UUID player, java.util.Set<String> damaged) {
        if (player == null || damaged == null) {
            return false;
        }
        return damaged.add(scopedKey(castId, player));
    }

    /** Real damage of one section; identical to the Phase 3 geyser formula. */
    public static double eruptionDamage(int players) {
        return RiftFractureEruptionPolicy.eruptionDamage(players);
    }

    /** Bounded launch: horizontal away from the section centre, capped lift. */
    public record Launch(double x, double y, double z) {
    }

    public static Launch launch(double dx, double dz) {
        double lengthSq = dx * dx + dz * dz;
        if (!Double.isFinite(lengthSq) || lengthSq < 1.0E-4D) {
            return new Launch(0.0D, MAX_LAUNCH_Y, ERUPTION_LAUNCH_VELOCITY);
        }
        double scale = ERUPTION_LAUNCH_VELOCITY / Math.sqrt(lengthSq);
        return new Launch(dx * scale, MAX_LAUNCH_Y, dz * scale);
    }

    /** True when a body is inside the section's real damage cylinder. */
    public static boolean contains(Section section, int floorY,
                                   double x, double y, double z) {
        if (section == null) {
            return false;
        }
        double dx = x - section.centerX();
        double dz = z - section.centerZ();
        if (dx * dx + dz * dz > GEYSER_RADIUS_BLOCKS * GEYSER_RADIUS_BLOCKS) {
            return false;
        }
        double dy = y - (floorY + 1.0D);
        return dy >= -1.0D && dy <= RiftFractureEruptionPolicy.MAX_GEYSER_HEIGHT;
    }

    /**
     * How many sections a party may be threatened by at once.
     *
     * <p>Only the sections inside their own 10-tick window are considered
     * active, and a larger party is not given more simultaneous eruptions — the
     * strip length is the pressure, not the width. This keeps at most a
     * readable three-section front even for twenty players.</p>
     */
    public static int simultaneousEruptions() {
        return Math.max(1, (ERUPTION_TICKS + ACTIVATION_INTERVAL_TICKS - 1) / ACTIVATION_INTERVAL_TICKS);
    }

    /** The gap before a new wave may open, scaled gently by party size. */
    public static int waveGapTicks(int players) {
        int safe = Math.max(1, Math.min(20, players));
        return 300 + Math.min(6, safe) * 20;
    }

    /** Diagnostic label, so logs never invent a different attack name. */
    public static String describe(int players, Plan plan) {
        int sections = plan == null ? 0 : plan.sections().size();
        return "SEQUENTIAL_GEYSER_WAVE players=" + players
                + " sections=" + sections
                + " covered=" + (plan != null && plan.coversTarget())
                + " warning_ticks=" + INITIAL_WARNING_TICKS
                + " interval_ticks=" + ACTIVATION_INTERVAL_TICKS
                + " eruption_ticks=" + ERUPTION_TICKS
                + " simultaneous=" + simultaneousEruptions();
    }

    /** Convenience for diagnostics: the ordered footprint centres. */
    public static Map<Integer, Cell> centers(Plan plan) {
        Map<Integer, Cell> centers = new LinkedHashMap<>();
        if (plan == null) {
            return centers;
        }
        for (Section section : plan.sections()) {
            centers.put(section.index(), new Cell(
                    (int) Math.floor(section.centerX()), (int) Math.floor(section.centerZ())));
        }
        return centers;
    }
}
