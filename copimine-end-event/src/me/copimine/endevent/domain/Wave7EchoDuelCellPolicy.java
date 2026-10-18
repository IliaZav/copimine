package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic owner-local geometry inside a shared Wave 7 Echo chamber. */
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
