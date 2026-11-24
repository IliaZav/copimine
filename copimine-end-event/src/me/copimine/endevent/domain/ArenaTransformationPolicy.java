package me.copimine.endevent.domain;

import java.util.Objects;

/**
 * Pure core of the {@code ArenaTransformationController} named in V6 master
 * §OV4.10: "original square snapshot, approved circular hall reveal, phase-6
 * opening, arena-local portals, true void rescue, and reverse physical rebuild
 * at victory/wipe".
 *
 * <p>Two things make that transformation dangerous by hand: the block masks are
 * unbounded unless someone bounds them, and the reverse rebuild is only safe if
 * the original snapshot is exact and the steps cannot run out of order. This
 * class owns the deterministic parts: the exact, bounded geometry of the square
 * footprint and the circular hall, the ordered step ladder (monotone, one step
 * at a time, never backwards), the number of blocks a reveal actually changes
 * and a true void-rescue decision. It touches no world and mutates no block;
 * the caller applies the plan through its own journaled writer.</p>
 */
public final class ArenaTransformationPolicy {

    /** Hard ceiling on a mask's linear extent, so a bad config fails closed. */
    public static final int MAX_HALF_EXTENT = 96;
    /** Hard ceiling on a mask's block count, so a bad config fails closed. */
    public static final int MAX_BLOCKS = 400_000;

    /** The ordered transformation ladder. */
    public enum Step {
        /** Capture the original square before any mutation. */
        SNAPSHOT,
        /** Reveal the approved circular hall inside the square. */
        CIRCULAR_REVEAL,
        /** Open the phase-six boss opening. */
        PHASE_SIX_OPENING,
        /** Place arena-local portals. */
        PORTALS,
        /** Reverse physical rebuild back to the snapshot at victory/wipe. */
        REBUILD
    }

    public enum Advance {
        ADVANCED,
        ALREADY_SETTLED,
        OUT_OF_ORDER,
        NOT_SNAPSHOTTED
    }

    /** The two approved mask shapes. */
    public enum Shape {
        SQUARE,
        CIRCLE
    }

    /**
     * One validated, bounded horizontal mask with vertical inclusion.
     *
     * @param shape     square or circle
     * @param centerX   centre block X
     * @param centerZ   centre block Z
     * @param halfExtent inclusive half-extent (square) or radius (circle)
     * @param minY      inclusive lowest included Y
     * @param maxY      inclusive highest included Y
     */
    public record Mask(Shape shape, int centerX, int centerZ, int halfExtent, int minY, int maxY) {
        public Mask {
            Objects.requireNonNull(shape, "shape");
            if (halfExtent < 0 || halfExtent > MAX_HALF_EXTENT) {
                throw new IllegalArgumentException("Полуразмер маски вне допустимого диапазона: " + halfExtent);
            }
            if (maxY < minY) {
                throw new IllegalArgumentException("maxY must not be below minY");
            }
        }

        /** Number of horizontal cells, computed in closed form. */
        public long cellCount() {
            long side = 2L * halfExtent + 1L;
            if (shape == Shape.SQUARE) {
                return side * side;
            }
            long cells = 0;
            for (int dz = -halfExtent; dz <= halfExtent; dz++) {
                long span = (long) Math.floor(Math.sqrt((double) halfExtent * halfExtent - (double) dz * dz));
                cells += 2L * span + 1L;
            }
            return cells;
        }

        public long blockCount() {
            long layers = (long) maxY - minY + 1L;
            return cellCount() * layers;
        }

        public boolean contains(int x, int z) {
            long dx = (long) x - centerX;
            long dz = (long) z - centerZ;
            if (shape == Shape.SQUARE) {
                return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
            }
            return dx * dx + dz * dz <= (long) halfExtent * halfExtent;
        }
    }

    private final Mask square;
    private final Mask circle;
    private Step step = Step.SNAPSHOT;

    private ArenaTransformationPolicy(Mask square, Mask circle) {
        this.square = square;
        this.circle = circle;
    }

    /**
     * Builds a validated plan. The circle must sit inside the square footprint so
     * the reveal cannot silently reach outside the snapshot it has to restore,
     * and both masks must fit the block budget.
     */
    public static ArenaTransformationPolicy plan(Mask square, Mask circle) {
        Objects.requireNonNull(square, "square");
        Objects.requireNonNull(circle, "circle");
        if (square.shape() != Shape.SQUARE) {
            throw new IllegalArgumentException("the snapshot footprint must be a square");
        }
        if (circle.shape() != Shape.CIRCLE) {
            throw new IllegalArgumentException("the hall reveal must be a circle");
        }
        long reachX = Math.abs((long) circle.centerX() - square.centerX());
        long reachZ = Math.abs((long) circle.centerZ() - square.centerZ());
        if (reachX + circle.halfExtent() > square.halfExtent()
                || reachZ + circle.halfExtent() > square.halfExtent()) {
            throw new IllegalArgumentException("the circular hall must lie inside the snapshot");
        }
        if (square.blockCount() > MAX_BLOCKS || circle.blockCount() > MAX_BLOCKS) {
            throw new IllegalArgumentException("arena mask exceeds the block budget");
        }
        return new ArenaTransformationPolicy(square, circle);
    }

    public Mask square() {
        return square;
    }

    public Mask circle() {
        return circle;
    }

    public Step step() {
        return step;
    }

    /** Advances one step along the ladder; never backwards, never skipping. */
    public Advance advance(Step next) {
        Objects.requireNonNull(next, "next step");
        if (next == step) {
            return Advance.ALREADY_SETTLED;
        }
        if (next.ordinal() <= step.ordinal()) {
            return Advance.OUT_OF_ORDER;
        }
        if (next.ordinal() != step.ordinal() + 1) {
            return Advance.OUT_OF_ORDER;
        }
        step = next;
        return Advance.ADVANCED;
    }

    /**
     * Horizontal cells the circular reveal actually carves out of the square
     * that are not already inside the circle: the exact number of cells a
     * journaled writer has to touch. Purely derived from the two masks.
     */
    public long revealCellDelta() {
        long changed = 0;
        for (int dz = -square.halfExtent(); dz <= square.halfExtent(); dz++) {
            for (int dx = -square.halfExtent(); dx <= square.halfExtent(); dx++) {
                int x = square.centerX() + dx;
                int z = square.centerZ() + dz;
                if (!circle.contains(x, z)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    /** Every cell the reveal touches is inside the snapshot it must restore. */
    public boolean revealIsReversible() {
        for (int dz = -circle.halfExtent(); dz <= circle.halfExtent(); dz++) {
            for (int dx = -circle.halfExtent(); dx <= circle.halfExtent(); dx++) {
                int x = circle.centerX() + dx;
                int z = circle.centerZ() + dz;
                if (circle.contains(x, z) && !square.contains(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A true void rescue: a player whose Y has fallen below the void floor is
     * returned to the validated safe landing above it, never left in the void
     * and never teleported through geometry.
     */
    public static double voidRescueY(double currentY, int voidFloorY, int safeLandingY) {
        if (safeLandingY <= voidFloorY) {
            throw new IllegalArgumentException("the safe landing must be above the void floor");
        }
        if (!Double.isFinite(currentY)) {
            return safeLandingY;
        }
        return currentY < voidFloorY ? safeLandingY : currentY;
    }
}
