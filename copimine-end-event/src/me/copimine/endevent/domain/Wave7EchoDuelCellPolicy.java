package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic owner geometry inside the Wave 7 personal-Echo region.
 *
 * <p>ECHO-005: the region is SHARED. A lane is a starting position and a
 * stable identity for one owner's duel, not a walled cell: owners may cross
 * into a neighbouring lane because isolation is enforced by the server
 * owner-pair rule ({@code EchoCombatAdmission}), not by physical separators.
 * The radial-room helpers remain for legacy chamber layouts only.</p>
 */
public final class Wave7EchoDuelCellPolicy {
    public static final int MAX_OWNERS = 20;
    private static final double CHAMBER_EDGE_BUFFER_RADIANS = Math.toRadians(8.0D);

    private Wave7EchoDuelCellPolicy() { }

    /** A relative radial separator; a world adapter must still journal physical blocks. */
    public record Boundary(double innerX, double innerZ, double outerX, double outerZ) {
        public Boundary {
            if (!Double.isFinite(innerX) || !Double.isFinite(innerZ)
                    || !Double.isFinite(outerX) || !Double.isFinite(outerZ)) {
                throw new IllegalArgumentException("non-finite Echo duel cell boundary");
            }
        }

        public double length() { return Math.hypot(outerX - innerX, outerZ - innerZ); }
    }

    /** Ordered owner lane with exclusive angular bounds inside its parent chamber. */
    public record Cell(int chamber, UUID owner, int ordinal, int ownerCount,
                       double centerAngleRadians, double halfWidthRadians,
                       double innerRadius, double outerRadius) {
        public Cell {
            Objects.requireNonNull(owner, "owner");
            if (chamber < 0 || chamber >= ChamberIsolationPolicy.MAX_CHAMBERS
                    || ordinal < 0 || ownerCount <= 0 || ownerCount > MAX_OWNERS || ordinal >= ownerCount
                    || !Double.isFinite(centerAngleRadians) || !Double.isFinite(halfWidthRadians)
                    || halfWidthRadians <= 0.0D || !Double.isFinite(innerRadius)
                    || !Double.isFinite(outerRadius) || innerRadius < 0.0D || outerRadius <= innerRadius) {
                throw new IllegalArgumentException("invalid Echo duel cell");
            }
        }

        /** Coordinates are arena-relative offsets from the chamber center. */
        public boolean contains(double xOffset, double zOffset) {
            if (!Double.isFinite(xOffset) || !Double.isFinite(zOffset)) return false;
            double radius = Math.hypot(xOffset, zOffset);
            if (radius < innerRadius || radius > outerRadius) return false;
            double angle = Math.atan2(zOffset, xOffset);
            double delta = Math.atan2(Math.sin(angle - centerAngleRadians),
                    Math.cos(angle - centerAngleRadians));
            return Math.abs(delta) + 1.0E-12D < halfWidthRadians;
        }

        public boolean allowsOwner(UUID candidate) { return owner.equals(candidate); }

        public Boundary lowerBoundary() { return radialBoundary(centerAngleRadians - halfWidthRadians); }
        public Boundary upperBoundary() { return radialBoundary(centerAngleRadians + halfWidthRadians); }

        private Boundary radialBoundary(double angle) {
            return new Boundary(innerRadius * Math.cos(angle), innerRadius * Math.sin(angle),
                    outerRadius * Math.cos(angle), outerRadius * Math.sin(angle));
        }
    }

    /**
     * Spread the starting positions of one shared Echo region.
     *
     * <p>Owners are placed around the authored ring at evenly spaced angles.
     * Adjacent lanes share exactly one angular edge, so the union of the lanes
     * covers the whole usable ring instead of leaving gaps that a duel could
     * not reach. Owners are ordered by their UUID string so a restart or a
     * differently iterated roster yields identical positions.</p>
     */
    public static List<Cell> sharedRegionPlan(int chamber, int ownerCount, Collection<UUID> owners) {
        if (chamber < 0 || chamber >= ChamberIsolationPolicy.MAX_CHAMBERS
                || ownerCount <= 0 || ownerCount > MAX_OWNERS
                || owners == null || owners.size() != ownerCount
                || owners.stream().anyMatch(Objects::isNull)
                || new HashSet<>(owners).size() != owners.size()) {
            throw new IllegalArgumentException("valid shared Echo region roster required");
        }
        List<UUID> ordered = new ArrayList<>(owners);
        ordered.sort(Comparator.comparing(UUID::toString));
        double halfWidth = Math.PI / ownerCount;
        List<Cell> cells = new ArrayList<>(ownerCount);
        for (int ordinal = 0; ordinal < ownerCount; ordinal++) {
            double center = -Math.PI + (2.0D * Math.PI * (ordinal + 0.5D) / ownerCount);
            cells.add(new Cell(chamber, ordered.get(ordinal), ordinal, ownerCount, center, halfWidth,
                    ChamberIsolationPolicy.INNER_RADIUS, ChamberIsolationPolicy.OUTER_RADIUS));
        }
        return List.copyOf(cells);
    }

    /**
     * Whether {@code owner} may occupy a horizontal point of the shared region.
     *
     * <p>Every admitted owner may use the whole authored ring; the Core column
     * and everything outside the ring stay closed. Non-admitted players have no
     * region access at all.</p>
     */
    public static boolean allowsSharedRegionAccess(List<Cell> cells, UUID owner, double xOffset, double zOffset) {
        if (cells == null || cells.isEmpty() || cells.size() > MAX_OWNERS || owner == null
                || !Double.isFinite(xOffset) || !Double.isFinite(zOffset)) {
            return false;
        }
        boolean admitted = false;
        for (Cell cell : cells) {
            if (cell == null || cell.owner() == null) return false;
            if (cell.allowsOwner(owner)) admitted = true;
        }
        if (!admitted) return false;
        for (Cell cell : cells) {
            if (cell.contains(xOffset, zOffset)) return true;
        }
        return false;
    }

    /** Build stable owner lanes without depending on collection iteration order. */
    public static List<Cell> plan(int chamber, int chamberCount, Collection<UUID> owners) {
        if (chamberCount <= 0 || chamberCount > ChamberIsolationPolicy.MAX_CHAMBERS
                || chamber < 0 || chamber >= chamberCount || owners == null || owners.isEmpty()
                || owners.size() > MAX_OWNERS || owners.stream().anyMatch(Objects::isNull)
                || new HashSet<>(owners).size() != owners.size()) {
            throw new IllegalArgumentException("valid Wave 7 Echo room and unique owner roster required");
        }
        List<UUID> ordered = new ArrayList<>(owners);
        ordered.sort(Comparator.comparing(UUID::toString));
        double roomHalfWidth = Math.PI / chamberCount - CHAMBER_EDGE_BUFFER_RADIANS;
        double cellHalfWidth = roomHalfWidth / ordered.size();
        double chamberCenter = ChamberIsolationPolicy.chamberCenterAngle(chamber, chamberCount);
        List<Cell> cells = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            double offset = -roomHalfWidth + (ordinal + 0.5D) * (2.0D * roomHalfWidth / ordered.size());
            cells.add(new Cell(chamber, ordered.get(ordinal), ordinal, ordered.size(),
                    chamberCenter + offset, cellHalfWidth,
                    ChamberIsolationPolicy.INNER_RADIUS, ChamberIsolationPolicy.OUTER_RADIUS));
        }
        return List.copyOf(cells);
    }

    public static boolean allowsOwnerAccess(Cell cell, UUID owner) {
        return cell != null && owner != null && cell.allowsOwner(owner);
    }

    /** Both endpoints must remain inside the same owner-bound cell for one move. */
    public static boolean allowsTransition(Cell cell, UUID owner,
                                           double fromX, double fromZ,
                                           double toX, double toZ) {
        return allowsOwnerAccess(cell, owner) && cell.contains(fromX, fromZ)
                && cell.contains(toX, toZ);
    }
}
