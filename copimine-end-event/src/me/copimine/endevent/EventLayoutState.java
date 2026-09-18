package me.copimine.endevent;

import java.util.Map;

/** Durable, bounded layout state kept separate from transient combat state. */
public record EventLayoutState(
        Point arenaPos1,
        Point arenaPos2,
        Point gatePos1,
        Point gatePos2,
        Map<String, String> gateSnapshot,
        String gateStatus,
        Portal portalRoom) {
    public EventLayoutState {
        gateSnapshot = Map.copyOf(gateSnapshot == null ? Map.of() : gateSnapshot);
        gateStatus = normalizeGateStatus(gateStatus);
    }

    public static EventLayoutState empty() {
        return new EventLayoutState(null, null, null, null, Map.of(), "UNSET", null);
    }

    private static String normalizeGateStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "UNSET", "PREVIEW", "OPENING", "OPENED", "CLOSING", "RESTORED", "RESTORED_ON_BOOT" -> normalized;
            default -> "UNSET";
        };
    }

    public record Point(String world, int x, int y, int z) {
        public Point {
            world = world == null ? "" : world.trim();
        }

        public boolean configured() {
            return !world.isBlank();
        }
    }

    public record Portal(String world, double x, double y, double z, float yaw, float pitch) {
        public Portal {
            world = world == null ? "" : world.trim();
        }

        public boolean configured() {
            return !world.isBlank() && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }
}
