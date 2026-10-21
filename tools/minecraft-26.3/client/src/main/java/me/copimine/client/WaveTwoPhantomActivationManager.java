package me.copimine.client;

import me.copimine.protocol.EndEventBridgeBodyCodec;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Fenced, bounded client presentation state for one server-selected Wave 2 phantom warning. */
final class WaveTwoPhantomActivationManager {
    private static final String VISUAL_ID = "WAVE2_PHANTOM_ACTIVATION_V1";
    private static final String INSTANCE_ID = "wave2-phantom-activation";
    private static final int MAX_EVENT_IDENTITIES = 129;
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_LIFETIME_MILLIS = 15_000;
    private static final int MAX_USED_PHANTOM_IDS = WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT;
    private static final Pattern SAFE_PHANTOM_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern COORDINATE = Pattern.compile(
            "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private static final class EventFence {
        private final long generation;
        private final Set<String> usedPhantomIds = new LinkedHashSet<>();
        private String dimension = "";
        private int cycle = -1;
        private long timestamp;
        private boolean retired;
        private Activation activation;

        private EventFence(long generation) {
            this.generation = generation;
        }
    }

    private final Map<String, EventFence> eventFences = new LinkedHashMap<>();
    private String eventId = "";
    private EventFence current;
    private long sessionTimestampHighWatermark;

    /** Applies a validated signed v4 body while retaining its exact duration and server position. */
    synchronized boolean apply(EndEventBridgeBodyCodec.Body body, long nowMillis) {
        if (!valid(body, nowMillis)) return false;
        String incomingEventId = body.eventUuid().toString();
        String[] mode = body.subjectOrMode().split("\\|", -1);
        int cycle = parseCycle(mode[2]);
        boolean active = "ACTIVE".equals(body.status());

        EventFence fence = eventFences.get(incomingEventId);
        if (fence != null && fence.retired) return false;
        if (fence != null && body.generation() < fence.generation) return false;
        boolean matchesCurrent = incomingEventId.equals(eventId);
        if (active && !matchesCurrent && body.bodyTimestampMillis() <= sessionTimestampHighWatermark) return false;
        if (fence == null && eventFences.size() >= MAX_EVENT_IDENTITIES) return false;
        if (fence == null || body.generation() > fence.generation) fence = new EventFence(body.generation());
        if (!fence.dimension.isEmpty() && !fence.dimension.equals(mode[0])) return false;
        if (cycle < fence.cycle || body.bodyTimestampMillis() <= fence.timestamp) return false;

        String phantomId = body.reservedOne();
        boolean newCycle = cycle > fence.cycle;
        Activation priorActivation = fence.activation;
        boolean sameActiveWarning = active && !newCycle && priorActivation != null
                && priorActivation.cycle() == cycle && priorActivation.phantomId().equals(phantomId);
        if (active && !newCycle && !sameActiveWarning && (fence.usedPhantomIds.contains(phantomId)
                || fence.usedPhantomIds.size() >= MAX_USED_PHANTOM_IDS
                || priorActivation != null)) {
            return false;
        }

        if (newCycle) {
            fence.usedPhantomIds.clear();
            fence.activation = null;
            fence.cycle = cycle;
            priorActivation = null;
            sameActiveWarning = false;
        }
        if (!active && cycle == fence.cycle && fence.activation != null
                && fence.activation.phantomId().equals(phantomId)) {
            fence.activation = null;
        }
        if (!fence.usedPhantomIds.contains(phantomId)) {
            if (fence.usedPhantomIds.size() >= MAX_USED_PHANTOM_IDS) return false;
            fence.usedPhantomIds.add(phantomId);
        }

        eventFences.put(incomingEventId, fence);
        if (active && !matchesCurrent) {
            if (current != null) {
                current.retired = true;
                current.activation = null;
            }
            eventId = incomingEventId;
            current = fence;
        } else if (matchesCurrent) {
            current = fence;
        }
        fence.dimension = mode[0];
        fence.cycle = cycle;
        fence.timestamp = body.bodyTimestampMillis();

        if (active) {
            WaveTwoPhantomLayout.Position position = parsePosition(body.controlId());
            long startedAt = sameActiveWarning ? priorActivation.warningTimestampMillis()
                    : body.bodyTimestampMillis();
            fence.activation = new Activation(incomingEventId, body.generation(), mode[0], cycle,
                    phantomId, position, startedAt, nowMillis, nowMillis + body.durationMillis());
        }
        if (current == fence) {
            sessionTimestampHighWatermark = Math.max(sessionTimestampHighWatermark, body.bodyTimestampMillis());
        }
        return true;
    }

    synchronized Activation active(String requestedDimension, long nowMillis) {
        if (nowMillis < 0L || current == null || current.activation == null) return null;
        if (current.activation.expiresAtMillis() <= nowMillis) {
            current.activation = null;
            return null;
        }
        return current.activation.dimension().equals(requestedDimension) ? current.activation : null;
    }

    /** Hides the current warning after a local mark, phase, world, or life-state transition. */
    synchronized void clearPresentation() {
        if (current != null) current.activation = null;
    }

    /** Clears transport identity fences only when the End Event connection is reset. */
    synchronized void reset() {
        eventFences.clear();
        eventId = "";
        current = null;
        sessionTimestampHighWatermark = 0L;
    }

    private static boolean valid(EndEventBridgeBodyCodec.Body body, long nowMillis) {
        if (body == null || body.type() != EndEventBridgeBodyCodec.Type.END_PLAYER_STATE
                || body.generation() <= 0L || body.bodyTimestampMillis() <= 0L || body.eventUuid() == null
                || nowMillis < 0L || !INSTANCE_ID.equals(body.instanceId()) || !VISUAL_ID.equals(body.visualId())
                || body.subjectOrMode().length() > 64 || body.durationMillis() < 0L
                || body.durationMillis() > MAX_LIFETIME_MILLIS || body.health() != 0L || body.maxHealth() != 0L
                || Float.floatToIntBits(body.progressOrIntensity()) != Float.floatToIntBits(0.0F)
                || !body.clearPolicy().isEmpty() || !body.reservedTwo().isEmpty()
                || !"ACTIVE".equals(body.status()) && !"CLEAR".equals(body.status())) {
            return false;
        }
        String[] mode = body.subjectOrMode().split("\\|", -1);
        if (mode.length != 3 || !validDimension(mode[0]) || !"wave2".equals(mode[1])
                || parseCycle(mode[2]) < 1 || !SAFE_PHANTOM_ID.matcher(body.reservedOne()).matches()) {
            return false;
        }
        if ("ACTIVE".equals(body.status())) {
            return body.durationMillis() > 0L
                    && nowMillis <= Long.MAX_VALUE - body.durationMillis()
                    && body.durationMillis() % 50L == 0L
                    && parsePosition(body.controlId()) != null;
        }
        return body.durationMillis() == 0L && parsePosition(body.controlId()) != null;
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

    record Activation(String eventId, long generation, String dimension, int cycle, String phantomId,
                      WaveTwoPhantomLayout.Position position, long warningTimestampMillis,
                      long acceptedAtMillis, long expiresAtMillis) {
        String warningToken() {
            return eventId + '|' + generation + '|' + cycle + '|' + phantomId + '|' + warningTimestampMillis;
        }
    }
}
