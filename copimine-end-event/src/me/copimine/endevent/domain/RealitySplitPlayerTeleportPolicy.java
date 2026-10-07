package me.copimine.endevent.domain;

import java.util.Map;
import java.util.UUID;

/** Server-side admission rule for player teleports during Wave 7. */
public final class RealitySplitPlayerTeleportPolicy {
    /** Match the configured combat vertical band without depending on Bukkit. */
    public static final double MAX_VERTICAL_OFFSET = 3.0D;
    /** Recenter a player when knockback crosses the closed room's inner edge. */
    public static final double INNER_BOUNDARY_RECOVERY_BAND = 0.75D;

    private RealitySplitPlayerTeleportPolicy() {
    }

    /**
     * Return whether a player destination remains in their assigned room.
     * The Bukkit adapter performs world and exact arena checks; this method is
     * the deterministic room/generation-independent part that can be tested
     * without a live server.
     */
    public static boolean allows(UUID player,
                                 ChamberIsolationPolicy.Assignment assignment,
                                 double xOffset,
                                 double zOffset,
                                 double yOffset,
                                 boolean merged) {
        if (player == null || assignment == null
                || !assignment.chamberByPlayer().containsKey(player)
                || !finite(xOffset) || !finite(zOffset) || !finite(yOffset)
                || Math.abs(yOffset) > MAX_VERTICAL_OFFSET) {
            return false;
        }
        double radius = Math.hypot(xOffset, zOffset);
        if (!finite(radius) || radius < ChamberIsolationPolicy.INNER_RADIUS
                || radius > ChamberIsolationPolicy.OUTER_RADIUS) {
            return false;
        }
        if (merged) {
            return true;
        }
        int chamber = assignment.chamberByPlayer().getOrDefault(player, -1);
        return ChamberIsolationPolicy.containsPoint(chamber, xOffset, zOffset,
                assignment.chamberCount());
    }

    /**
     * Return whether a rejected move is the specific knockback case that can
     * otherwise pin a player to the legal inner edge forever.  The caller
     * performs the actual server teleport; this pure predicate only identifies
     * an inward crossing from an assigned room into the neutral Core column.
     */
    public static boolean requiresInnerBoundaryRecovery(
            UUID player,
            ChamberIsolationPolicy.Assignment assignment,
            double fromXOffset,
            double fromZOffset,
            double toXOffset,
            double toZOffset,
            boolean merged) {
        if (merged || player == null || assignment == null
                || !assignment.chamberByPlayer().containsKey(player)
                || !finite(fromXOffset) || !finite(fromZOffset)
                || !finite(toXOffset) || !finite(toZOffset)) {
            return false;
        }
        int chamber = assignment.chamberByPlayer().getOrDefault(player, -1);
        double fromRadius = Math.hypot(fromXOffset, fromZOffset);
        double toRadius = Math.hypot(toXOffset, toZOffset);
        if (!finite(fromRadius) || !finite(toRadius)
                || fromRadius < ChamberIsolationPolicy.INNER_RADIUS
                || fromRadius > ChamberIsolationPolicy.INNER_RADIUS
                + INNER_BOUNDARY_RECOVERY_BAND) {
            return false;
        }
        if (!ChamberIsolationPolicy.containsPoint(chamber, fromXOffset, fromZOffset,
                assignment.chamberCount())) {
            return false;
        }
        return toRadius < ChamberIsolationPolicy.INNER_RADIUS
                && toRadius < fromRadius - 0.000001D;
    }

    /**
     * Return whether a rejected move crosses either radial edge of a closed
     * room.  Both directions need the same recovery: replaying a legal outer
     * edge is just as capable of pinning a client as replaying the inner edge.
     */
    public static boolean requiresBoundaryRecovery(
            UUID player,
            ChamberIsolationPolicy.Assignment assignment,
            double fromXOffset,
            double fromZOffset,
            double toXOffset,
            double toZOffset,
            boolean merged) {
        return requiresInnerBoundaryRecovery(player, assignment,
                fromXOffset, fromZOffset, toXOffset, toZOffset, merged)
                || requiresOuterBoundaryRecovery(player, assignment,
                fromXOffset, fromZOffset, toXOffset, toZOffset, merged);
    }

    /** Return whether a rejected move crosses the closed room's outer edge. */
    public static boolean requiresOuterBoundaryRecovery(
            UUID player,
            ChamberIsolationPolicy.Assignment assignment,
            double fromXOffset,
            double fromZOffset,
            double toXOffset,
            double toZOffset,
            boolean merged) {
        if (merged || player == null || assignment == null
                || !assignment.chamberByPlayer().containsKey(player)
                || !finite(fromXOffset) || !finite(fromZOffset)
                || !finite(toXOffset) || !finite(toZOffset)) {
            return false;
        }
        int chamber = assignment.chamberByPlayer().getOrDefault(player, -1);
        double fromRadius = Math.hypot(fromXOffset, fromZOffset);
        double toRadius = Math.hypot(toXOffset, toZOffset);
        if (!finite(fromRadius) || !finite(toRadius)
                || fromRadius < ChamberIsolationPolicy.OUTER_RADIUS
                - INNER_BOUNDARY_RECOVERY_BAND
                || fromRadius > ChamberIsolationPolicy.OUTER_RADIUS) {
            return false;
        }
        if (!ChamberIsolationPolicy.containsPoint(chamber, fromXOffset, fromZOffset,
                assignment.chamberCount())) {
            return false;
        }
        return toRadius > ChamberIsolationPolicy.OUTER_RADIUS
                && toRadius > fromRadius + 0.000001D;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
