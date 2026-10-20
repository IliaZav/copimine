package me.copimine.endevent.domain;

import java.util.Set;
import java.util.UUID;

/**
 * One authoritative Wave 6 spatial definition. The Bukkit adapter supplies the
 * world-space center; visual sampling, player containment and mob leash all
 * consume this same record.
 */
public final class CollapseRingDefinition {
    private CollapseRingDefinition() {
    }

    public static RingDefinition forRing(int id,
                                         double centerX,
                                         double centerY,
                                         double centerZ,
                                         Set<UUID> assignedPlayers,
                                         Set<UUID> assignedMobs) {
        if (id < 0 || id >= CollapseRingGeometryPolicy.RING_COUNT
                || !Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Wave 6 ring definition is invalid");
        }
        return new RingDefinition(id, centerX, centerY, centerZ,
                CollapseRingGeometryPolicy.ringRadius(id),
                centerY - 3.0D, centerY + 3.0D,
                assignedPlayers, assignedMobs,
                new VisualSettings(CollapseRingGeometryPolicy.visualPointCount(id),
                        switch (id) {
                            case 0 -> 0xC437FF;
                            case 1 -> 0x60DBFF;
                            default -> 0xFF4CD6;
                        }));
    }

    public record RingDefinition(int id,
                                 double centerX,
                                 double centerY,
                                 double centerZ,
                                 double radius,
                                 double verticalMin,
                                 double verticalMax,
                                 Set<UUID> assignedPlayers,
                                 Set<UUID> assignedMobs,
                                 VisualSettings visual) {
        public RingDefinition {
            if (id < 0 || id >= CollapseRingGeometryPolicy.RING_COUNT
                    || !Double.isFinite(centerX) || !Double.isFinite(centerY)
                    || !Double.isFinite(centerZ) || !Double.isFinite(radius)
                    || radius <= 0.0D || !Double.isFinite(verticalMin)
                    || !Double.isFinite(verticalMax) || verticalMax < verticalMin) {
                throw new IllegalArgumentException("Wave 6 ring definition is invalid");
            }
            assignedPlayers = Set.copyOf(assignedPlayers == null ? Set.of() : assignedPlayers);
            assignedMobs = Set.copyOf(assignedMobs == null ? Set.of() : assignedMobs);
            if (visual == null) {
                throw new IllegalArgumentException("Wave 6 ring visual settings are required");
            }
        }

        public boolean containsPlayer(double x, double y, double z) {
            return verticalContains(y)
                    && CollapseRingGeometryPolicy.inPlayerLane(id,
                    x - centerX, z - centerZ);
        }

        public boolean containsMob(double x, double y, double z) {
            return verticalContains(y)
                    && CollapseRingGeometryPolicy.inRingBand(id,
                    x - centerX, z - centerZ);
        }

        private boolean verticalContains(double y) {
            return Double.isFinite(y) && y >= verticalMin && y <= verticalMax;
        }
    }

    public record VisualSettings(int pointCount, int colorRgb) {
        public VisualSettings {
            if (pointCount < 1 || pointCount > CollapseRingGeometryPolicy.MAX_VISUAL_POINTS
                    || colorRgb < 0 || colorRgb > 0xFFFFFF) {
                throw new IllegalArgumentException("Wave 6 visual settings are invalid");
            }
        }
    }
}
