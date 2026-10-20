package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Shared radial walls between owner-local Echo cells. */
public final class Wave7EchoDuelCellBarrierPolicy {
    private static final double BOUNDARY_EPSILON = 1.0E-9D;
    private static final int MAX_HEIGHT = 8;
    private static final int MAX_HORIZONTAL_CELLS = 64;

    private Wave7EchoDuelCellBarrierPolicy() { }

    /** A separator belongs to the two adjacent cells, never to a room passage. */
    public record SharedBarrier(int ordinal, UUID lowerOwner, UUID upperOwner,
                                Wave7EchoDuelCellPolicy.Boundary boundary) {
        public SharedBarrier {
            Objects.requireNonNull(lowerOwner, "lowerOwner");
            Objects.requireNonNull(upperOwner, "upperOwner");
            Objects.requireNonNull(boundary, "boundary");
            if (ordinal < 0 || lowerOwner.equals(upperOwner)) {
                throw new IllegalArgumentException("invalid shared Echo cell barrier");
            }
        }
    }

    /** Stable adjacent pairs follow the deterministic owner ordering of the cell plan. */
    public static List<SharedBarrier> plan(int chamber, int chamberCount,
                                           Collection<UUID> owners) {
        List<Wave7EchoDuelCellPolicy.Cell> cells =
                Wave7EchoDuelCellPolicy.plan(chamber, chamberCount, owners);
        if (cells.size() < 2) return List.of();
        List<SharedBarrier> result = new ArrayList<>(cells.size() - 1);
        for (int ordinal = 0; ordinal + 1 < cells.size(); ordinal++) {
            var lower = cells.get(ordinal);
            var upper = cells.get(ordinal + 1);
            var first = lower.upperBoundary();
            var second = upper.lowerBoundary();
            if (!sameBoundary(first, second)) {
                throw new IllegalArgumentException("adjacent Echo cell separators do not align");
            }
            result.add(new SharedBarrier(ordinal, lower.owner(), upper.owner(), first));
        }
        return List.copyOf(result);
    }

    /**
     * Keep a shared wall while either adjacent duel remains active. Removing a
     * separator between one completed and one active owner would connect two
     * independently live duels through the newly empty cell.
     */
    public static List<SharedBarrier> remaining(List<SharedBarrier> barriers,
                                                 Set<UUID> completedOwners) {
        if (barriers == null || barriers.size() > Wave7EchoDuelCellPolicy.MAX_OWNERS - 1
                || completedOwners == null || completedOwners.size() > Wave7EchoDuelCellPolicy.MAX_OWNERS
                || completedOwners.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("invalid completed-owner barrier snapshot");
        }
        return barriers.stream().filter(barrier -> barrier != null
                && !(completedOwners.contains(barrier.lowerOwner())
                && completedOwners.contains(barrier.upperOwner()))).toList();
    }

    /**
     * A finished owner may use the connected group of finished cells whose
     * shared separators have opened. An active owner remains confined to
     * their own duel cell even if other owners have already won.
     */
    public static boolean allowsOwnerLocation(List<Wave7EchoDuelCellPolicy.Cell> cells,
                                               List<SharedBarrier> barriers,
                                               Set<UUID> completedOwners,
                                               UUID owner, double x, double z) {
        Set<UUID> accessible = accessibleOwners(cells, barriers, completedOwners, owner);
        return !accessible.isEmpty() && cells.stream().anyMatch(cell -> cell != null
                && accessible.contains(cell.owner()) && cell.contains(x, z));
    }

    /** Both endpoints must remain in the owner's currently connected cleared-cell component. */
    public static boolean allowsOwnerTransition(List<Wave7EchoDuelCellPolicy.Cell> cells,
                                                 List<SharedBarrier> barriers,
                                                 Set<UUID> completedOwners,
                                                 UUID owner,
                                                 double fromX, double fromZ,
                                                 double toX, double toZ) {
        return allowsOwnerLocation(cells, barriers, completedOwners, owner, fromX, fromZ)
                && allowsOwnerLocation(cells, barriers, completedOwners, owner, toX, toZ);
    }

    private static Set<UUID> accessibleOwners(List<Wave7EchoDuelCellPolicy.Cell> cells,
                                               List<SharedBarrier> barriers,
                                               Set<UUID> completedOwners,
                                               UUID owner) {
        if (cells == null || cells.isEmpty() || cells.size() > Wave7EchoDuelCellPolicy.MAX_OWNERS
                || barriers == null || barriers.size() > Wave7EchoDuelCellPolicy.MAX_OWNERS - 1
                || completedOwners == null || completedOwners.size() > Wave7EchoDuelCellPolicy.MAX_OWNERS
                || completedOwners.stream().anyMatch(Objects::isNull) || owner == null) {
            return Set.of();
        }
        Set<UUID> roster = new LinkedHashSet<>();
        for (Wave7EchoDuelCellPolicy.Cell cell : cells) {
            if (cell == null || !roster.add(cell.owner())) return Set.of();
        }
        if (!roster.contains(owner) || !roster.containsAll(completedOwners)) return Set.of();
        Set<UUID> accessible = new LinkedHashSet<>();
        accessible.add(owner);
        if (!completedOwners.contains(owner)) return Set.copyOf(accessible);

        boolean changed;
        do {
            changed = false;
            for (SharedBarrier barrier : barriers) {
                if (barrier == null || !roster.contains(barrier.lowerOwner())
                        || !roster.contains(barrier.upperOwner())) return Set.of();
                if (!completedOwners.contains(barrier.lowerOwner())
                        || !completedOwners.contains(barrier.upperOwner())) continue;
                if (accessible.contains(barrier.lowerOwner())) {
                    changed |= accessible.add(barrier.upperOwner());
                }
                if (accessible.contains(barrier.upperOwner())) {
                    changed |= accessible.add(barrier.lowerOwner());
                }
            }
        } while (changed);
        return Set.copyOf(accessible);
    }

    /** Rasterize the shared radial wall into cardinally connected block columns. */
    public static List<RealitySplitBarrierPolicy.Cell> cells(SharedBarrier barrier, int height) {
        Objects.requireNonNull(barrier, "barrier");
        if (height <= 0 || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Echo cell barrier height is outside the safe bound");
        }
        var segment = barrier.boundary();
        int startX = (int) Math.round(segment.innerX());
        int startZ = (int) Math.round(segment.innerZ());
        int targetX = (int) Math.round(segment.outerX());
        int targetZ = (int) Math.round(segment.outerZ());
        int dx = targetX - startX;
        int dz = targetZ - startZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps <= 0 || steps > MAX_HORIZONTAL_CELLS) {
            throw new IllegalArgumentException("Echo cell barrier segment has invalid length");
        }
        List<Point> path = cardinalPath(startX, startZ, targetX, targetZ, steps);
        if (path.size() > MAX_HORIZONTAL_CELLS) {
            throw new IllegalArgumentException("Echo cell barrier raster exceeds safe bound");
        }
        Set<RealitySplitBarrierPolicy.Cell> result = new LinkedHashSet<>();
        for (Point point : path) {
            for (int level = 1; level <= height; level++) {
                result.add(new RealitySplitBarrierPolicy.Cell(point.x(), point.z(), level));
            }
        }
        return List.copyOf(result);
    }

    public static Set<RealitySplitBarrierPolicy.Cell> cells(List<SharedBarrier> barriers,
                                                             Set<UUID> completedOwners,
                                                             int height) {
        Set<RealitySplitBarrierPolicy.Cell> result = new LinkedHashSet<>();
        for (SharedBarrier barrier : remaining(barriers, completedOwners)) {
            result.addAll(cells(barrier, height));
            if (result.size() > 4_096) {
                throw new IllegalArgumentException("Echo cell wall plan exceeds safe cell bound");
            }
        }
        return Set.copyOf(result);
    }

    private static List<Point> cardinalPath(int startX, int startZ,
                                            int targetX, int targetZ, int steps) {
        List<Point> result = new ArrayList<>();
        int x = startX;
        int z = startZ;
        result.add(new Point(x, z));
        for (int index = 1; index <= steps; index++) {
            int wantedX = (int) Math.round(startX + (targetX - startX) * (index / (double) steps));
            int wantedZ = (int) Math.round(startZ + (targetZ - startZ) * (index / (double) steps));
            while (x != wantedX || z != wantedZ) {
                int stepX = wantedX - x;
                int stepZ = wantedZ - z;
                if (stepX != 0 && (stepZ == 0 || Math.abs(stepX) >= Math.abs(stepZ))) {
                    x += Integer.signum(stepX);
                } else {
                    z += Integer.signum(stepZ);
                }
                result.add(new Point(x, z));
                if (result.size() > MAX_HORIZONTAL_CELLS) {
                    throw new IllegalArgumentException("Echo cell barrier raster exceeds safe bound");
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameBoundary(Wave7EchoDuelCellPolicy.Boundary left,
                                        Wave7EchoDuelCellPolicy.Boundary right) {
        return Math.abs(left.innerX() - right.innerX()) <= BOUNDARY_EPSILON
                && Math.abs(left.innerZ() - right.innerZ()) <= BOUNDARY_EPSILON
                && Math.abs(left.outerX() - right.outerX()) <= BOUNDARY_EPSILON
                && Math.abs(left.outerZ() - right.outerZ()) <= BOUNDARY_EPSILON;
    }

    private record Point(int x, int z) { }
}
