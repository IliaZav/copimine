package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic roster assignment and cross-room isolation rules for Wave 6. */
public final class ChamberIsolationPolicy {
    public static final int MAX_CHAMBERS = 4;
    /** Keep a narrow neutral column around Core out of chamber destinations. */
    public static final double INNER_RADIUS = 3.5D;
    /** The room sectors are deliberately smaller than the arena edge. */
    public static final double OUTER_RADIUS = 18.0D;

    private ChamberIsolationPolicy() {
    }

    public static int chamberCount(int players) {
        int safe = Math.max(0, Math.min(20, players));
        if (safe <= 1) return safe;
        if (safe == 2) return 2;
        if (safe == 3) return 3;
        return MAX_CHAMBERS;
    }

    public static Assignment assign(List<UUID> players) {
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(players == null ? List.of() : players));
        unique.removeIf(value -> value == null);
        unique.sort(java.util.Comparator.comparing(UUID::toString));
        int count = chamberCount(unique.size());
        Map<UUID, Integer> mapping = new LinkedHashMap<>();
        for (int index = 0; index < unique.size(); index++) {
            mapping.put(unique.get(index), count == 0 ? -1 : index % count);
        }
        return new Assignment(count, mapping);
    }

    public static boolean sameRoom(UUID first, UUID second, Assignment assignment) {
        if (first == null || second == null || assignment == null) return false;
        Integer left = assignment.chamberByPlayer().get(first);
        Integer right = assignment.chamberByPlayer().get(second);
        return left != null && left >= 0 && left.equals(right);
    }

    public static boolean allowsInteraction(UUID source, UUID target, Assignment assignment,
                                            boolean passageOpen) {
        return passageOpen || sameRoom(source, target, assignment);
    }

    /**
     * A Wave 6 mob may only select a player assigned to the same closed room.
     * Once the passage is open the room gate no longer filters interactions.
     */
    public static boolean allowsMobTarget(int mobChamber, UUID target,
                                          Assignment assignment, boolean passageOpen) {
        if (passageOpen) {
            return target != null && assignment != null
                    && assignment.chamberByPlayer().containsKey(target);
        }
        return target != null && assignment != null
                && mobChamber >= 0 && mobChamber < assignment.chamberCount()
                && assignment.chamberByPlayer().getOrDefault(target, -1) == mobChamber;
    }

    /**
     * Return whether a horizontal point belongs to a chamber sector.  The
     * center column is intentionally rejected: it is reserved for the Core
     * and the closed passage, so mobs cannot path through one another's room.
     */
    public static boolean containsPoint(int chamber, double xOffset, double zOffset,
                                        int chamberCount) {
        if (chamberCount <= 0 || chamberCount > MAX_CHAMBERS
                || chamber < 0 || chamber >= chamberCount
                || !Double.isFinite(xOffset) || !Double.isFinite(zOffset)) {
            return false;
        }
        double radius = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
        if (radius < INNER_RADIUS || radius > OUTER_RADIUS) {
            return false;
        }
        double angle = Math.atan2(zOffset, xOffset);
        double center = chamberCenterAngle(chamber, chamberCount);
        double delta = Math.atan2(Math.sin(angle - center), Math.cos(angle - center));
        // Leave a small angular buffer on the room boundary so pathing and
        // entity hitboxes cannot straddle two rooms.
        double halfSector = Math.PI / chamberCount - Math.toRadians(8.0D);
        return Math.abs(delta) <= Math.max(0.05D, halfSector);
    }

    public static double chamberCenterAngle(int chamber, int chamberCount) {
        if (chamberCount <= 0 || chamber < 0 || chamber >= chamberCount) {
            return 0.0D;
        }
        return -Math.PI / 2.0D + (Math.PI * 2.0D * chamber / chamberCount);
    }

    public static double sectorRadius(int chamberCount, double preferred) {
        if (chamberCount <= 0) {
            return 0.0D;
        }
        double max = Math.max(INNER_RADIUS + 0.5D, OUTER_RADIUS - 0.5D);
        return Math.max(INNER_RADIUS + 0.5D, Math.min(max,
                Double.isFinite(preferred) ? preferred : INNER_RADIUS + 1.0D));
    }

    public record Assignment(int chamberCount, Map<UUID, Integer> chamberByPlayer) {
        public Assignment {
            chamberCount = Math.max(0, Math.min(MAX_CHAMBERS, chamberCount));
            chamberByPlayer = Map.copyOf(chamberByPlayer == null ? Map.of() : chamberByPlayer);
            for (Integer chamber : chamberByPlayer.values()) {
                if (chamber == null || chamber < 0 || chamber >= chamberCount) {
                    throw new IllegalArgumentException("assignment contains invalid chamber");
                }
            }
        }

        public Set<UUID> playersIn(int chamber) {
            Set<UUID> result = new LinkedHashSet<>();
            if (chamber < 0 || chamber >= chamberCount) return result;
            chamberByPlayer.forEach((player, value) -> {
                if (value == chamber) result.add(player);
            });
            return Set.copyOf(result);
        }
    }
}
