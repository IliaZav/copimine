package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Durable Wave 3 capture progress for an exact event generation. */
public final class Wave3PortalProgressSnapshot {
    public static final String PREFIX = "wave3.portal.";
    private static final int PORTAL_COUNT = 3;

    private Wave3PortalProgressSnapshot() {
    }

    public record Data(List<PortalCapturePolicy.PortalState> states, boolean eliteSpawned) {
        public Data {
            if (states == null || states.size() != PORTAL_COUNT) {
                throw new IllegalArgumentException("Wave 3 checkpoint requires exactly three portals");
            }
            states = List.copyOf(states);
        }
    }

    /** Empty states mean the objective has not created its portals yet. */
    public static Map<String, String> encode(String eventId, long generation,
                                             List<PortalCapturePolicy.PortalState> states,
                                             boolean eliteSpawned) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || states == null) {
            throw new IllegalArgumentException("invalid Wave 3 checkpoint identity");
        }
        if (states.isEmpty()) return Map.of();
        if (states.size() != PORTAL_COUNT) {
            throw new IllegalArgumentException("Wave 3 checkpoint requires exactly three portals");
        }
        Map<String, String> encoded = new java.util.LinkedHashMap<>();
        encoded.put(PREFIX + "event", eventId);
        encoded.put(PREFIX + "generation", Long.toString(generation));
        encoded.put(PREFIX + "count", Integer.toString(PORTAL_COUNT));
        encoded.put(PREFIX + "elite-spawned", Boolean.toString(eliteSpawned));
        for (int index = 0; index < states.size(); index++) {
            PortalCapturePolicy.PortalState state = states.get(index);
            if (state == null) throw new IllegalArgumentException("portal state is required");
            long progress = state.progressMillis();
            if (progress < 0L || progress > PortalCapturePolicy.CAPTURE_MILLIS
                    || state.completed() != (progress == PortalCapturePolicy.CAPTURE_MILLIS)) {
                throw new IllegalArgumentException("portal progress is inconsistent");
            }
            encoded.put(PREFIX + index + ".completed", Boolean.toString(state.completed()));
            encoded.put(PREFIX + index + ".progress", Long.toString(progress));
        }
        return Map.copyOf(encoded);
    }

    /** Returns null when the snapshot has no Wave 3 keys (legacy checkpoint). */
    public static Data decode(Map<String, String> values, String eventId, long generation) {
        if (values == null || values.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return null;
        }
        try {
            if (!eventId.equals(values.get(PREFIX + "event"))
                    || generation != Long.parseLong(values.get(PREFIX + "generation"))
                    || Integer.parseInt(values.get(PREFIX + "count")) != PORTAL_COUNT) {
                throw new IllegalArgumentException("stale Wave 3 checkpoint");
            }
            String elite = values.get(PREFIX + "elite-spawned");
            if (!"true".equals(elite) && !"false".equals(elite)) {
                throw new IllegalArgumentException("invalid Wave 3 elite flag");
            }
            List<PortalCapturePolicy.PortalState> states = new ArrayList<>();
            for (int index = 0; index < PORTAL_COUNT; index++) {
                String completed = values.get(PREFIX + index + ".completed");
                if (!"true".equals(completed) && !"false".equals(completed)) {
                    throw new IllegalArgumentException("invalid Wave 3 portal completion flag");
                }
                long progress = Long.parseLong(values.get(PREFIX + index + ".progress"));
                if (progress < 0L || progress > PortalCapturePolicy.CAPTURE_MILLIS
                        || Boolean.parseBoolean(completed)
                        != (progress == PortalCapturePolicy.CAPTURE_MILLIS)) {
                    throw new IllegalArgumentException("invalid Wave 3 portal progress");
                }
                states.add(new PortalCapturePolicy.PortalState(
                        Boolean.parseBoolean(completed), progress, -1L, -1L));
            }
            return new Data(states, Boolean.parseBoolean(elite));
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException("incomplete Wave 3 checkpoint", invalid);
        }
    }
}