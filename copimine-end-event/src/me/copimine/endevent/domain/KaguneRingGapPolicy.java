package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Master contract 20: the boss releases three sequential expanding rows of
 * Kagune. Each row is a closed ring around the boss with several
 * <b>two-block-wide gaps</b> that players escape through.
 *
 * <p>Hard rules owned here:</p>
 * <ul>
 *   <li>gap positions are random but deterministic for one cast seed;</li>
 *   <li>a gap is never centred on a player;</li>
 *   <li>the three rows never line up into one straight escape lane;</li>
 *   <li>more party members means more gaps;</li>
 *   <li>gaps stay well separated and the cast is never impossible;</li>
 *   <li>a gap never sits on a solid obstacle (passed in as blocked sectors);</li>
 *   <li>the layout is frozen at cast start and never re-rolled mid-cast.</li>
 * </ul>
 */
public final class KaguneRingGapPolicy {
    /** A gap is two blocks wide. */
    public static final double GAP_WIDTH_BLOCKS = 2.0D;
    /** Contract 20.1: three sequential rows. */
    public static final int ROW_COUNT = 3;
    /** Contract 20.1: the rows emerge 7.5 / 11 / 14.5 blocks out. */
    public static final double[] ROW_EMERGE_RADIUS = {7.5D, 11.0D, 14.5D};
    /** Contract 20.1: each row travels about five blocks before retracting. */
    public static final double ROW_TRAVEL_BLOCKS = 5.0D;
    /** Two gaps in one row must be at least this many blocks apart, edge to edge. */
    public static final double MIN_GAP_SEPARATION_BLOCKS = 3.0D;
    /** A gap centre must never sit closer than this to a player. */
    public static final double MIN_PLAYER_OFFSET_BLOCKS = 1.75D;
    /**
     * Smallest allowed angular distance between the gap centres of two
     * <b>different</b> rows, measured on the innermost ring. Rows stand 3.5 blocks
     * apart, so anything below this shares a straight corridor through both rows.
     */
    public static final double CROSS_ROW_FLOOR_RAD = 0.12D;
    /** Tolerance so float noise never makes a clean layout look aligned. */
    public static final double LANE_SLOP_RAD = 0.001D;
    private static final double[] DE_LANE_EMPTY = new double[0];
    /** Seed stride between attempts at laying one row. */
    private static final long ROW_SEED_STRIDE = 0x9E3779B97F4A7C15L;
    /** Attempts per slot when the seeded position lands on a player or a wall. */
    public static final int MAX_ATTEMPTS = 64;
    /** Slide steps tried on each side of a slot while breaking a lane. */
    public static final int SLIDE_STEPS = 16;

    private KaguneRingGapPolicy() {
    }

    /** Contract 20.2 gap counts by admitted party size. */
    public static int gapCount(int admittedPlayers) {
        int players = Math.max(0, admittedPlayers);
        // An empty party is refused outright: a ring with no escape gap is not a
        // challenge, it is an unavoidable death.
        if (players <= 0) {
            return 0;
        }
        if (players <= 3) {
            return 2;
        }
        if (players <= 5) {
            return 3;
        }
        if (players <= 10) {
            return 4;
        }
        return Math.min(7, 5 + (players - 11) / 5);
    }

    /** Width of the ring at the given radius. */
    public static double circumference(double radius) {
        return Math.max(0.0D, radius) * 2.0D * Math.PI;
    }

    /** Angular half-width of one two-block gap on a ring of that radius. */
    public static double halfWidthRad(double radius) {
        if (!(radius > 0.0D)) {
            return Math.PI;
        }
        return Math.min(Math.PI, (GAP_WIDTH_BLOCKS / 2.0D) / radius);
    }

    /**
     * Number of two-block gaps that still leave the minimum separation between
     * them on the innermost ring. This is what caps the contract's gap counts and
     * what makes "the pattern is never impossible" a fact rather than a hope.
     */
    public static int maxFittingGaps(double radius) {
        if (!(radius > 0.0D)) {
            return 0;
        }
        double needed = GAP_WIDTH_BLOCKS + MIN_GAP_SEPARATION_BLOCKS;
        int fitting = (int) Math.floor(circumference(radius) / needed);
        return Math.max(0, Math.min(7, fitting));
    }

    /** True when the party's gap count physically fits the innermost ring. */
    public static boolean patternPossible(int admittedPlayers, double radius) {
        int requested = gapCount(admittedPlayers);
        return requested > 0 && requested <= maxFittingGaps(radius);
    }

    /** Angular separation between two gap centres that keeps them apart in blocks. */
    public static double minCentreSeparationRad(double radius) {
        if (!(radius > 0.0D)) {
            return 2.0D * Math.PI;
        }
        return (GAP_WIDTH_BLOCKS + MIN_GAP_SEPARATION_BLOCKS) / radius;
    }

    /** Shortest angular distance between two directions, in radians. */
    public static double angleDistance(double first, double second) {
        return Math.abs(wrap(first - second));
    }

    /**
     * Two gaps are "the same lane" when their centres stand closer than
     * {@link #CROSS_ROW_FLOOR_RAD}. Such a pair would let a player queue straight
     * through two rows, which the contract forbids.
     */
    public static boolean sameDirection(double first, double second) {
        return angleDistance(first, second) < CROSS_ROW_FLOOR_RAD - LANE_SLOP_RAD;
    }

    /**
     * Build the frozen three-row layout for one cast.
     *
     * @param seed            frozen at cast start; the same seed always yields the
     *                        same layout
     * @param admittedPlayers admitted party size, which sets the gap count
     * @param playerAngles    current player directions, so a gap is never centred
     *                        on somebody
     * @param blockedSectors  solid-obstacle directions, so a row never passes
     *                        through a wall
     */
    public static Layout layout(long seed, int admittedPlayers,
                                List<Double> playerAngles, List<Double> blockedSectors) {
        int gaps = gapCount(admittedPlayers);
        double radius = ROW_EMERGE_RADIUS[0];
        if (gaps <= 0 || !patternPossible(admittedPlayers, radius)) {
            // An escape that does not exist is worse than no cast at all.
            return Layout.refused(seed);
        }
        double halfWidth = halfWidthRad(radius);
        List<Double> safeAngles = finiteOrEmpty(playerAngles);
        List<Double> safeBlocked = finiteOrEmpty(blockedSectors);
        List<Row> rows = new ArrayList<>(ROW_COUNT);
        double[] previous = null;
        for (int row = 0; row < ROW_COUNT; row++) {
            double[] centres = null;
            double[] first = rows.isEmpty() ? null : rows.get(0).gapCentres();
            for (int attempt = 0; attempt < MAX_ATTEMPTS && centres == null; attempt++) {
                double[] seeded = solveRow(seed + attempt * ROW_SEED_STRIDE, row, gaps,
                        halfWidth, safeAngles, safeBlocked);
                if (seeded != null) {
                    centres = row == 0 ? seeded
                            : deLane(seeded, gaps, halfWidth, safeAngles, safeBlocked,
                                    previous, first, row);
                }
            }
            if (centres == null) {
                return Layout.refused(seed);
            }
            rows.add(new Row(row, ROW_EMERGE_RADIUS[row], centres));
            previous = centres;
        }
        return new Layout(seed, List.copyOf(rows), true);
    }

    /**
     * Place one row's gap centres inside equal slots.
     *
     * <p>Slots keep two gaps of the same row far apart by construction. Inside its
     * slot a gap takes the seeded position; when that lands on a player or inside
     * a wall the row walks deterministic alternatives until it finds a free spot.
     * </p>
     */
    /**
     * Place one row's gap centres inside equal slots.
     *
     * <p>Slots keep two gaps of the same row far apart: the usable band inside a
     * slot is bounded by half of {@link #minCentreSeparationRad}, so even when two
     * neighbouring gaps sit at opposite edges of their slots they stay
     * {@link #MIN_GAP_SEPARATION_BLOCKS} blocks apart edge to edge. Inside its band
     * a gap takes the seeded position; when that lands on a player or inside a
     * wall the row walks deterministic alternatives until it finds a free spot.</p>
     */
    private static double[] solveRow(long seed, int row, int gaps, double halfWidth,
                                     List<Double> playerAngles, List<Double> blockedSectors) {
        double slotWidth = (2.0D * Math.PI) / gaps;
        double low = slotLow(slotWidth);
        double high = slotHigh(slotWidth);
        if (high <= low) {
            return null;
        }
        double[] centres = new double[gaps];
        for (int index = 0; index < gaps; index++) {
            double base = index * slotWidth;
            double chosen = Double.NaN;
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                double fraction = attempt == 0
                        ? unit(seed, row, index) : unit(seed, row, index, attempt);
                double candidate = base + low + fraction * (high - low);
                if (blocked(candidate, playerAngles, blockedSectors)) {
                    continue;
                }
                chosen = candidate;
                break;
            }
            if (Double.isNaN(chosen)) {
                return null;
            }
            centres[index] = chosen;
        }
        return centres;
    }

    /** Start of the usable band inside one slot, measured from the slot base. */
    private static double slotLow(double slotWidth) {
        double half = Math.min(minCentreSeparationRad(ROW_EMERGE_RADIUS[0]) / 2.0D,
                slotWidth / 4.0D);
        return Math.max(halfWidthRad(ROW_EMERGE_RADIUS[0]), half);
    }

    /** End of the usable band inside one slot, measured from the slot base. */
    private static double slotHigh(double slotWidth) {
        return slotWidth - slotLow(slotWidth);
    }

    /**
     * Break any straight escape lane between this row and the earlier ones.
     *
     * <p>A row is refused outright when a gap cannot be moved clear, because a
     * cast is laid out before it starts: the caller re-rolls a fresh seed rather
     * than widening a lane. Every gap keeps its own slot, so two gaps of one row
     * can never crowd each other no matter how far they slide.</p>
     */
    private static double[] deLane(double[] centres, int gaps, double halfWidth,
                                   List<Double> playerAngles, List<Double> blockedSectors,
                                   double[] previousRow, double[] firstRow, int row) {
        double[] placed = DE_LANE_EMPTY.clone();
        double slotWidth = (2.0D * Math.PI) / gaps;
        double low = slotLow(slotWidth);
        double high = slotHigh(slotWidth);
        double step = Math.max((high - low) / SLIDE_STEPS, 1.0e-6D);
        double[] settled = centres.clone();
        for (int index = 0; index < gaps; index++) {
            double[] targets = row == 0 ? previousRow : concat(firstRow, previousRow);
            targets = concat(targets, placed);
            if (shortfall(settled[index], targets) == 0.0D) {
                placed = concat(placed, new double[]{settled[index]});
                continue;
            }
            double base = index * slotWidth;
            double best = settled[index];
            double bestShortfall = shortfall(settled[index], targets);
            for (int direction = -1; direction <= 1; direction += 2) {
                for (int move = 1; move <= SLIDE_STEPS; move++) {
                    double candidate = settled[index] + direction * move * step;
                    if (candidate < base + low || candidate > base + high) {
                        break;
                    }
                    if (blocked(candidate, playerAngles, blockedSectors)) {
                        continue;
                    }
                    double need = shortfall(candidate, targets);
                    if (need < bestShortfall || (need == 0.0D && bestShortfall == 0.0D
                            && Math.abs(candidate - settled[index]) < Math.abs(best - settled[index]))) {
                        bestShortfall = need;
                        best = candidate;
                    }
                }
            }
            if (bestShortfall > 0.0D) {
                return null;
            }
            settled[index] = best;
            placed = concat(placed, new double[]{best});
        }
        return settled;
    }

    private static double[] concat(double[] first, double[] second) {
        double[] merged = new double[first.length + second.length];
        int index = 0;
        while (index < first.length) {
            merged[index] = first[index];
            index++;
        }
        int other = 0;
        while (other < second.length) {
            merged[index] = second[other];
            index++;
            other++;
        }
        return merged;
    }

    /** How far a candidate still falls short of clearing every target gap. */
    private static double shortfall(double candidate, double[] targets) {
        double shortfall = 0.0D;
        int index = 0;
        while (index < targets.length) {
            double need = CROSS_ROW_FLOOR_RAD
                    - KaguneRingGapPolicy.angleDistance(candidate, targets[index]);
            if (need > shortfall) {
                shortfall = need;
            }
            index++;
        }
        return shortfall;
    }

    private static double shortfall(double distance) {
        double need = CROSS_ROW_FLOOR_RAD - distance;
        return need > 0.0D ? need : 0.0D;
    }

    private static boolean clashes(double candidate, double[] previousRow) {
        int index = 0;
        while (index < previousRow.length) {
            if (sameDirection(candidate, previousRow[index])) {
                return true;
            }
            index++;
        }
        return false;
    }

    /** Smallest angular distance from one candidate to the previous row's gaps. */
    private static double laneDistance(double candidate, double[] previousRow) {
        double nearest = Math.PI;
        int index = 0;
        while (index < previousRow.length) {
            nearest = Math.min(nearest, angleDistance(candidate, previousRow[index]));
            index++;
        }
        return nearest;
    }

    /** A gap centre must stay off players and off solid obstacles. */
    private static boolean blocked(double direction, List<Double> playerAngles,
                                   List<Double> blockedSectors) {
        double radius = ROW_EMERGE_RADIUS[0];
        int index = 0;
        while (index < playerAngles.size()) {
            if (angleDistance(direction, playerAngles.get(index)) * radius
                    < MIN_PLAYER_OFFSET_BLOCKS) {
                return true;
            }
            index++;
        }
        index = 0;
        while (index < blockedSectors.size()) {
            if (angleDistance(direction, blockedSectors.get(index)) * radius
                    < GAP_WIDTH_BLOCKS) {
                return true;
            }
            index++;
        }
        return false;
    }

    private static List<Double> finiteOrEmpty(List<Double> values) {
        List<Double> safe = new ArrayList<>();
        if (values == null) {
            return safe;
        }
        int index = 0;
        while (index < values.size()) {
            Double value = values.get(index);
            if (value != null && Double.isFinite(value)) {
                safe.add(value);
            }
            index++;
        }
        return safe;
    }

    /** Deterministic pseudo-random unit value for one (seed, row, index, attempt). */
    private static double unit(long seed, int row, int index, int... extra) {
        long mixed = seed * 0x9E3779B97F4A7C15L + (row + 1L) * 0xD1B54A32D192ED03L
                + (index + 1L) * 0x94D049BB133111EBL;
        int step = 0;
        while (step < extra.length) {
            mixed = mixed * 0x9E3779B97F4A7C15L + (extra[step] + 7L);
            step++;
        }
        mixed += 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (Math.floorMod(mixed, 1_000_003L)) / 1_000_003.0D;
    }

    /** Wrap into (-pi, pi]. */
    public static double wrap(double angle) {
        double wrapped = angle % (2.0D * Math.PI);
        if (wrapped <= -Math.PI) {
            wrapped += 2.0D * Math.PI;
        } else if (wrapped > Math.PI) {
            wrapped -= 2.0D * Math.PI;
        }
        return wrapped;
    }

    /**
     * The ring hits a player who stands in the band of the currently out row,
     * unless that player is inside one of that row's two-block gaps.
     */
    public static boolean rowHits(Layout layout, int rowIndex, double playerRadius,
                                  double direction) {
        if (layout == null || !layout.feasible()) {
            return false;
        }
        Row row = layout.row(rowIndex);
        if (row == null || !rowReaches(row, playerRadius)) {
            return false;
        }
        return !insideGap(row, direction);
    }

    /** True when the row's outward sweep covers that player radius. */
    public static boolean rowReaches(Row row, double playerRadius) {
        if (row == null || !Double.isFinite(playerRadius)) {
            return false;
        }
        double outer = row.emergeRadius() + ROW_TRAVEL_BLOCKS;
        return playerRadius >= row.emergeRadius() - LANE_SLOP_RAD
                && playerRadius <= outer + LANE_SLOP_RAD;
    }

    /** True when the direction lies inside one of that row's gaps. */
    public static boolean insideGap(Row row, double direction) {
        if (row == null) {
            return false;
        }
        double halfWidth = halfWidthRad(row.emergeRadius());
        int index = 0;
        double[] centres = row.gapCentres();
        while (index < centres.length) {
            if (Math.abs(wrap(direction - centres[index])) <= halfWidth) {
                return true;
            }
            index++;
        }
        return false;
    }

    /**
     * Every row exposes at least one two-block gap, so a player who is hit by the
     * first row can still be standing in a gap when the next rows sweep past.
     * Combined with the per-row hit rule this makes the cast survivable.
     */
    public static boolean hasSurvivableGap(Layout layout) {
        if (layout == null || !layout.feasible()) {
            return false;
        }
        int row = 0;
        while (row < layout.rows().size()) {
            if (layout.rows().get(row).gapCount() <= 0) {
                return false;
            }
            row++;
        }
        return true;
    }

    /** One frozen row of the ring. */
    public record Row(int index, double emergeRadius, double[] gapCentres) {
        public Row {
            if (index < 0 || index >= ROW_COUNT) {
                throw new IllegalArgumentException("ring row index must be 0..2");
            }
            if (!Double.isFinite(emergeRadius) || emergeRadius <= 0.0D) {
                throw new IllegalArgumentException("ring row radius must be positive");
            }
            if (gapCentres == null) {
                throw new IllegalArgumentException("ring row needs gap centres");
            }
            gapCentres = gapCentres.clone();
            for (int slot = 0; slot < gapCentres.length; slot++) {
                if (!Double.isFinite(gapCentres[slot])) {
                    throw new IllegalArgumentException("gap centre must be finite");
                }
            }
        }

        @Override
        public double[] gapCentres() {
            return gapCentres.clone();
        }

        public int gapCount() {
            return gapCentres.length;
        }
    }

    /** Frozen layout for one cast; never re-rolled while the cast runs. */
    public record Layout(long seed, List<Row> rows, boolean feasible) {
        public Layout {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        static Layout refused(long seed) {
            return new Layout(seed, List.of(), false);
        }

        public Row row(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        /** Rows must never share one straight escape lane. */
        public boolean sharesEscapeLane() {
            int first = 0;
            while (first < rows.size()) {
                int second = first + 1;
                while (second < rows.size()) {
                    double[] left = rows.get(first).gapCentres();
                    double[] right = rows.get(second).gapCentres();
                    int leftIndex = 0;
                    while (leftIndex < left.length) {
                        int rightIndex = 0;
                        while (rightIndex < right.length) {
                            if (sameDirection(left[leftIndex], right[rightIndex])) {
                                return true;
                            }
                            rightIndex++;
                        }
                        leftIndex++;
                    }
                    second++;
                }
                first++;
            }
            return false;
        }
    }
}