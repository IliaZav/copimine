package me.copimine.client;

import me.copimine.protocol.EndEventBridgeBodyCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Fenced, bounded client cache of server-authored Wave 2 phantom positions. */
final class WaveTwoPhantomLayoutManager {
    private static final String VISUAL_ID = "WAVE2_HUNT_MARK_V1";
    private static final String INSTANCE_ID = "wave2-hunt-mark";
    private static final int MAX_EVENT_IDENTITIES = 129;
    private static final Pattern PHANTOM_ID = Pattern.compile("phantom-(?:0[0-9]|[12][0-9]|3[0-4])");
    private static final Pattern COORDINATE = Pattern.compile(
            "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private final Map<String, Long> retiredEvents = new LinkedHashMap<>();
    private String eventId = "";
    private long generation;
    private String dimension = "";
    private int cycle = -1;
    private long timestamp;
    private long sessionTimestampHighWatermark;
    private boolean active;
    private final Map<String, Phantom> positions = new LinkedHashMap<>();

    /** Apply one signed HUNT mark update or one position item within its 35-entry layout. */
    synchronized boolean apply(EndEventBridgeBodyCodec.Body body, long nowMillis) {
        if (!valid(body, nowMillis)) return false;
        String incomingEventId = body.eventUuid().toString();
        String[] mode = body.subjectOrMode().split("\\|", -1);
        int incomingCycle = parseCycle(mode[2]);
        boolean incomingActive = "ACTIVE".equals(body.status());
        boolean hasPosition = !body.controlId().isEmpty();
        boolean matchesCurrent = incomingEventId.equals(eventId);

        if (retiredEvents.containsKey(incomingEventId)) return false;
        if (!matchesCurrent && !incomingActive && !eventId.isEmpty()) return false;
        if (matchesCurrent && body.generation() < generation) return false;
        if (incomingActive && !matchesCurrent && timestamp > 0L
                && body.bodyTimestampMillis() <= sessionTimestampHighWatermark) return false;
        if (!matchesCurrent && !eventId.isEmpty() && retiredEvents.size() >= MAX_EVENT_IDENTITIES) return false;
        if (matchesCurrent && body.generation() == generation
                && !dimension.isEmpty() && !dimension.equals(mode[0])) return false;
        if (matchesCurrent && body.generation() == generation && incomingCycle < cycle) return false;
        if (matchesCurrent && body.generation() == generation && incomingCycle == cycle
                && body.bodyTimestampMillis() < timestamp) return false;

        boolean newEvent = !matchesCurrent;
        boolean newGeneration = matchesCurrent && body.generation() > generation;
        boolean newCycle = matchesCurrent && body.generation() == generation && incomingCycle > cycle;
        if (newEvent || newGeneration || newCycle) {
            if (newEvent && !eventId.isEmpty()) retiredEvents.put(eventId, generation);
            eventId = incomingEventId;
            generation = body.generation();
            dimension = mode[0];
            cycle = incomingCycle;
            timestamp = 0L;
            active = false;
            positions.clear();
        }

        String phantomId = body.reservedOne();
        if (!incomingActive) {
            active = false;
            positions.clear();
            timestamp = body.bodyTimestampMillis();
            sessionTimestampHighWatermark = Math.max(sessionTimestampHighWatermark, timestamp);
            return true;
        }

        active = true;
        if (hasPosition) {
            WaveTwoPhantomLayout.Position position = parsePosition(body.controlId());
            if (position == null || !validPhantomId(phantomId)) return false;
            Phantom previous = positions.get(phantomId);
            if (previous != null && !previous.position().equals(position)) return false;
            if (previous == null && positions.size() >= WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT) return false;
            positions.putIfAbsent(phantomId, new Phantom(phantomId, position));
        }
        timestamp = Math.max(timestamp, body.bodyTimestampMillis());
        sessionTimestampHighWatermark = Math.max(sessionTimestampHighWatermark, timestamp);
        return true;
    }

    synchronized boolean isComplete(String requestedDimension, String expectedEventId,
                                    long expectedGeneration, int expectedCycle) {
        return matches(requestedDimension, expectedEventId, expectedGeneration, expectedCycle)
                && active && positions.size() >= WaveTwoPhantomLayout.MIN_PHANTOM_COUNT;
    }

    synchronized int cycle(String requestedDimension, String expectedEventId, long expectedGeneration) {
        return active && dimension.equals(requestedDimension) && eventId.equals(expectedEventId)
                && generation == expectedGeneration ? cycle : -1;
    }

    /** Return only entries for the active event, generation, dimension, and group cycle. */
    synchronized List<Phantom> layout(String requestedDimension, String expectedEventId,
                                      long expectedGeneration, int expectedCycle) {
        if (!matches(requestedDimension, expectedEventId, expectedGeneration, expectedCycle) || !active) {
            return List.of();
        }
        List<Phantom> ordered = new ArrayList<>(positions.values());
        ordered.sort((first, second) -> Integer.compare(
                phantomIndex(first.id()), phantomIndex(second.id())));
        return List.copyOf(ordered);
    }

    /** Clear local presentation while retaining identity/timestamp fences against delayed packets. */
    synchronized void clearPresentation() {
        active = false;
        positions.clear();
    }

    /** Transport reset is the only operation that forgets retired event identities. */
    synchronized void reset() {
        retiredEvents.clear();
        eventId = "";
        generation = 0L;
        dimension = "";
        cycle = -1;
        timestamp = 0L;
        sessionTimestampHighWatermark = 0L;
        active = false;
        positions.clear();
    }

    private boolean matches(String requestedDimension, String expectedEventId,
                            long expectedGeneration, int expectedCycle) {
        return active && dimension.equals(requestedDimension) && eventId.equals(expectedEventId)
                && generation == expectedGeneration && cycle == expectedCycle;
    }

    private static boolean valid(EndEventBridgeBodyCodec.Body body, long nowMillis) {
        if (body == null || body.type() != EndEventBridgeBodyCodec.Type.END_PLAYER_STATE
                || body.generation() <= 0L || body.bodyTimestampMillis() <= 0L || body.eventUuid() == null
                || nowMillis < 0L || !INSTANCE_ID.equals(body.instanceId()) || !VISUAL_ID.equals(body.visualId())
                || body.subjectOrMode().length() > 64 || body.durationMillis() < 0L
                || body.durationMillis() > 15_000L || body.health() != 0L || body.maxHealth() != 0L
                || Float.floatToIntBits(body.progressOrIntensity())
                != Float.floatToIntBits("ACTIVE".equals(body.status()) ? 0.25F : 0.0F)
                || !body.clearPolicy().isEmpty() || !body.reservedTwo().isEmpty()
                || !"ACTIVE".equals(body.status()) && !"CLEAR".equals(body.status())) return false;
        String[] mode = body.subjectOrMode().split("\\|", -1);
        if (mode.length != 3 || !validDimension(mode[0]) || !"wave2".equals(mode[1])
                || parseCycle(mode[2]) < 1) return false;
        boolean hasPosition = !body.controlId().isEmpty();
        boolean hasId = !body.reservedOne().isEmpty();
        if (!"ACTIVE".equals(body.status())) {
            return !hasPosition && !hasId && body.durationMillis() == 0L;
        }
        if (body.durationMillis() <= 0L || body.durationMillis() > 8_000L
                || nowMillis > Long.MAX_VALUE - body.durationMillis()) return false;
        return hasPosition == hasId && (!hasPosition
                || validPhantomId(body.reservedOne()) && parsePosition(body.controlId()) != null);
    }

    private static WaveTwoPhantomLayout.Position parsePosition(String value) {
        if (value == null || value.length() > 128) return null;
        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 3) return null;
        double[] parsed = new double[3];
        for (int index = 0; index < coordinates.length; index++) {
            if (!COORDINATE.matcher(coordinates[index]).matches()) return null;
            try {
                parsed[index] = Double.parseDouble(coordinates[index]);
            } catch (NumberFormatException invalid) {
                return null;
            }
            if (!Double.isFinite(parsed[index])) return null;
        }
        if (Math.abs(parsed[0]) > 29_999_998.0D || parsed[1] < -4_096.0D || parsed[1] > 4_096.0D
                || Math.abs(parsed[2]) > 29_999_998.0D) return null;
        return new WaveTwoPhantomLayout.Position(parsed[0], parsed[1], parsed[2]);
    }

    private static boolean validPhantomId(String value) {
        return value != null && PHANTOM_ID.matcher(value).matches();
    }

    private static int phantomIndex(String value) {
        if (!validPhantomId(value)) return Integer.MAX_VALUE;
        return Integer.parseInt(value.substring("phantom-".length()));
    }

    private static int parseCycle(String value) {
        if (value == null || value.isEmpty() || value.length() > 10
                || !value.matches("[1-9][0-9]*")) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean validDimension(String value) {
        return "overworld".equals(value) || "the_nether".equals(value) || "the_end".equals(value);
    }

    record Phantom(String id, WaveTwoPhantomLayout.Position position) { }
}
