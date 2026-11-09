package me.copimine.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Camera test for the Wave 5 safe volumes.
 *
 * <p>The server publishes its safe areas as emerald floors before the blackout
 * starts, so the client reads the same blocks instead of duplicating the
 * layout over the bridge.
 *
 * <p>The vertical bounds deliberately mirror the authoritative damage
 * exemption in {@code CopiMineEndEvent.isCurrentSafeZonePlayer}, which accepts
 * any player within 2.0 blocks of the combat floor. A wider client volume
 * would let a player read the arena while the server is already damaging them.
 *
 * <p>The active renderer follows the latest distance-fog requirement: nearby
 * shapes remain readable and distant geometry disappears. Membership changes
 * the near fade distance without clearing the surrounding fog. The older
 * per-point {@link #maskFor} utility is retained for geometry callers; it is
 * not a per-pixel mask installed in the world renderer.
 */
public final class BlackFogSafeVolumePolicy {
    /** Camera height above the block the player stands on. */
    public static final double EYE_HEIGHT_BLOCKS = 1.62D;
    /** Server damage exemption: {@code abs(playerY - combatFloorY) <= 2.0}. */
    public static final double SAFE_VERTICAL_SLACK_BLOCKS = 2.0D;
    /**
     * Outward margin applied to the safe footprint before the fog wall starts.
     *
     * <p>This is not a view radius: without it a camera whose eye sits exactly
     * on the footprint boundary would clip the adjacent emerald face away and
     * the player could not see the floor they stand on. It keeps the readable
     * region congruent to the footprint instead of swallowing the arena.
     */
    public static final double SAFE_MARGIN_BLOCKS = 0.5D;

    private BlackFogSafeVolumePolicy() {
    }

    /** Widest footprint {@code BlackFogSafeZonePolicy} can publish (side 3). */
    public static final int MAX_FOOTPRINT_SIDE = 3;

    /**
     * Distance at which the safe-volume wall itself becomes fully opaque.
     *
     * <p>Derived from the footprint geometry rather than being an independent
     * "view radius": the farthest readable point of the widest published
     * footprint is its far corner plus the outward margin on both axes, i.e.
     * at most {@code sqrt(2) * (MAX_SIDE / 2 + margin)} from the centre of the
     * far cell. Anything past that must already be black, so a view ray
     * leaving the footprint cannot reach world geometry. The exact boundary is
     * refined per pixel by {@link #maskFor}; this distance is only the fallback
     * fog end and stays below the superseded uniform four-block radius.
     */
    public static final float SAFE_READ_DISTANCE_BLOCKS =
            (float) (Math.sqrt(2.0D) * (MAX_FOOTPRINT_SIDE / 2.0D + SAFE_MARGIN_BLOCKS));

    /**
     * Fog distance for the current frame. Being inside a safe volume never
     * restores vanilla visibility. Keep at least the bounded footprint read
     * distance and retain the current dense-fog distance when it is larger.
     */
    public static float fogEndBlocks(boolean insideSafeVolume, float blackoutEnd) {
        if (!Float.isFinite(blackoutEnd) || blackoutEnd < 0.0F) {
            throw new IllegalArgumentException("blackout fog end must be finite and non-negative");
        }
        return insideSafeVolume ? Math.max(SAFE_READ_DISTANCE_BLOCKS, blackoutEnd) : blackoutEnd;
    }
    /** Feet height implied by a camera at {@code cameraY}. */
    public static double feetY(double cameraY) {
        if (!Double.isFinite(cameraY)) {
            throw new IllegalArgumentException("camera height must be finite");
        }
        return cameraY - EYE_HEIGHT_BLOCKS;
    }

    /**
     * First safe-floor block worth testing. The block top sits one above the
     * block Y, so the floor under the feet can be a few blocks below.
     */
    public static int firstCandidateFloorY(double cameraY) {
        return (int) Math.floor(feetY(cameraY)) - 3;
    }

    public static int lastCandidateFloorY(double cameraY) {
        return (int) Math.floor(feetY(cameraY)) + 1;
    }

    /**
     * Whether a camera at {@code cameraY} is inside the volume above the safe
     * floor block at {@code floorBlockY}, using the server's own slack.
     */
    public static boolean insideSafeColumn(double cameraY, int floorBlockY) {
        if (!Double.isFinite(cameraY)) return false;
        double floorSurfaceY = floorBlockY + 1.0D;
        return Math.abs(feetY(cameraY) - floorSurfaceY) <= SAFE_VERTICAL_SLACK_BLOCKS;
    }

    /** Block column a camera occupies; the safe floor must share it exactly. */
    public static int blockX(double cameraX) {
        if (!Double.isFinite(cameraX)) {
            throw new IllegalArgumentException("camera x must be finite");
        }
        return (int) Math.floor(cameraX);
    }

    public static int blockZ(double cameraZ) {
        if (!Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("camera z must be finite");
        }
        return (int) Math.floor(cameraZ);
    }

    /**
     * Builds the readable region from the exact published safe cells.
     *
     * <p>Contract W5-017: a uniform visible radius is explicitly insufficient,
     * because a ray leaving the safe area must not expose world geometry, a
     * second island or entities on the far side of the fog. Each published
     * cell is expanded by {@link #SAFE_MARGIN_BLOCKS} so an occupant standing
     * on the footprint boundary still reads the floor under their feet; the
     * union of those squares is the only place a view ray may remain clear.
     */
    public static SafeMask maskFor(List<SafeCell> cells, int floorBlockY) {
        if (cells == null || cells.isEmpty()) return SafeMask.EMPTY;
        Set<SafeCell> unique = new LinkedHashSet<>(cells);
        List<SafeCell> snapshot = new ArrayList<>(unique.size());
        for (SafeCell cell : unique) {
            if (cell != null) snapshot.add(cell);
        }
        return new SafeMask(List.copyOf(snapshot), floorBlockY);
    }

    @FunctionalInterface
    public interface SafeFloor {
        boolean at(int x, int y, int z);
    }

    /**
     * Bounded published-floor snapshot. Refresh at most every 100ms for a
     * stationary player; camera bob, crouching and third-person offsets never
     * change membership. The source reads at most five cells in the player's
     * occupied column per refresh; neighbouring columns cannot grant safety.
     * Membership uses exact feet coordinates, matching server damage safety.
     */
    public static final class SafeFloorCache {
        private final boolean[] cells = new boolean[5];
        private Object world;
        private String event = "";
        private long generation;
        private long sampledAt = -1L;
        private int anchorX, anchorZ, firstY;

        public boolean contains(Object nextWorld, String nextEvent, long nextGeneration,
                                long now, double x, double feetY, double z, SafeFloor source) {
            if (nextWorld == null || source == null || !Double.isFinite(x)
                    || !Double.isFinite(feetY) || !Double.isFinite(z)) return false;
            int columnX = blockX(x), columnZ = blockZ(z);
            if (world != nextWorld || !java.util.Objects.equals(event, nextEvent)
                    || generation != nextGeneration || sampledAt < 0L || now < sampledAt
                    || now - sampledAt >= 100L || columnX != anchorX || columnZ != anchorZ
                    || feetY < firstY || feetY > firstY + 6.0D) {
                world = nextWorld;
                event = nextEvent;
                generation = nextGeneration;
                sampledAt = now;
                anchorX = columnX;
                anchorZ = columnZ;
                firstY = (int) Math.floor(feetY) - 3;
                for (int dy = 0; dy < 5; dy++) {
                    cells[dy] = source.at(anchorX, firstY + dy, anchorZ);
                }
            }
            for (int dy = 0; dy < 5; dy++) {
                if (cells[dy]
                        && Math.abs(feetY - (firstY + dy + 1.0D)) <= SAFE_VERTICAL_SLACK_BLOCKS) {
                    return true;
                }
            }
            return false;
        }

        public void clear() {
            world = null;
            event = "";
            sampledAt = -1L;
        }
    }

    /** One published safe column, in block coordinates. */
    public record SafeCell(int x, int z) {
    }

    /**
     * The readable region for one camera, resolved from the published safe
     * cells. Server-owned geometry decides membership; this snapshot only
     * answers "may a view ray ending at this point stay clear?".
     */
    public static final class SafeMask {
        private static final SafeMask EMPTY = new SafeMask(List.of(), Integer.MIN_VALUE);

        private final List<SafeCell> cells;
        private final int floorBlockY;

        private SafeMask(List<SafeCell> cells, int floorBlockY) {
            this.cells = cells;
            this.floorBlockY = floorBlockY;
        }

        public int cellCount() {
            return cells.size();
        }

        /** Full three-coordinate readability test. */
        public boolean readable(double x, double y, double z, int requestedFloorBlockY) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
            if (cells.isEmpty() || requestedFloorBlockY != floorBlockY) return false;
            if (!insideSafeColumn(y, floorBlockY)) return false;
            double limit = 0.5D + SAFE_MARGIN_BLOCKS;
            for (SafeCell cell : cells) {
                if (Math.abs(x - (cell.x() + 0.5D)) <= limit
                        && Math.abs(z - (cell.z() + 0.5D)) <= limit) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Narrows the readable region to the island the camera actually
         * occupies.
         *
         * <p>Contract W5-017 also forbids seeing a second safe island across
         * the fog. That island is emerald too, so a membership test that scans
         * all published cells would clear it. Only cells reachable from the
         * camera's own column may stay clear; every other island, and the fog
         * between islands, stays black.
         *
         * @return a mask restricted to the camera's island, or {@link #EMPTY}
         *         when the camera is not standing on a published safe column
         */
        public SafeMask aroundCamera(double cameraX, double cameraY, double cameraZ) {
            if (cells.isEmpty() || !Double.isFinite(cameraX) || !Double.isFinite(cameraY)
                    || !Double.isFinite(cameraZ) || !insideSafeColumn(cameraY, floorBlockY)) {
                return EMPTY;
            }
            int anchorX = blockX(cameraX);
            int anchorZ = blockZ(cameraZ);
            Set<SafeCell> reachable = new LinkedHashSet<>();
            for (SafeCell cell : cells) {
                if (cell.x() == anchorX && cell.z() == anchorZ) reachable.add(cell);
            }
            if (reachable.isEmpty()) return EMPTY;
            // Grow through 4-neighbours so touching cells form one island while
            // a gap of one block already ends it.
            boolean changed = true;
            while (changed) {
                changed = false;
                for (SafeCell cell : cells) {
                    if (reachable.contains(cell)) continue;
                    if (reachable.contains(new SafeCell(cell.x() - 1, cell.z()))
                            || reachable.contains(new SafeCell(cell.x() + 1, cell.z()))
                            || reachable.contains(new SafeCell(cell.x(), cell.z() - 1))
                            || reachable.contains(new SafeCell(cell.x(), cell.z() + 1))) {
                        reachable.add(cell);
                        changed = true;
                    }
                }
            }
            return new SafeMask(List.copyOf(reachable), floorBlockY);
        }
    }
}
