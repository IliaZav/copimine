package me.copimine.client;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Generation-fenced Wave 5 fog state; the server remains authoritative for timing. */
public final class EndEventBlackFogManager {
    private static final String EVENT_TYPE = ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_FOG_STATE";
    private static final String VISUAL_ID = "WAVE5_FOG_V1";
    private static final long MAX_LIFETIME_MILLIS = 10_000L;
    private static final int MAX_RETIRED_EVENTS = 128;
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_MODE_LENGTH = 32;
    private static final float MIN_INTENSITY = 0.5F;
    private static final float MAX_INTENSITY = 1.0F;

    private String eventId = "";
    private long generation;
    private long lastTimestampMillis;
    private long sessionTimestampHighWatermark;
    private final Set<String> retiredEventIds = new HashSet<>();
    private int currentCycle = -1;
    private int clearedCycle = -1;
    private boolean terminal;
    private long expiresAtMillis;
    private String dimension = "";
    private float intensity;

    public synchronized boolean apply(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L || !Objects.equals(payload.messageType(), EVENT_TYPE)
                || !VISUAL_ID.equals(payload.shaderpack())
                || payload.sessionId().isBlank() || payload.sessionId().length() > MAX_EVENT_ID_LENGTH
                || payload.seq() <= 0L
                || payload.timestampMillis() <= 0L) {
            return false;
        }
        boolean suspend = "SUSPEND".equalsIgnoreCase(payload.status());
        boolean clear = suspend || "CLEAR".equalsIgnoreCase(payload.status());
        if (!clear && (!"ACTIVE".equalsIgnoreCase(payload.status())
                || payload.durationMillis() <= 0
                || payload.durationMillis() > MAX_LIFETIME_MILLIS
                || !Float.isFinite(payload.intensity())
                || payload.intensity() < MIN_INTENSITY
                || payload.intensity() > MAX_INTENSITY)) {
            return false;
        }
        String[] state = null;
        if (!clear) {
            state = parseMode(payload.mode());
            if (state == null || state.length != 3 || !validDimension(state[0])
                    || !"wave5".equals(state[1]) || !validCycle(state[2])) {
                return false;
            }
        }
        int nextCycle = clear ? -1 : Integer.parseInt(state[2]);
        if (!acceptEnvelope(payload.sessionId(), payload.seq(), payload.timestampMillis(), clear, suspend, nextCycle)) {
            return false;
        }
        if (clear) {
            if (suspend) terminal = true;
            clearActiveState();
            return true;
        }
        dimension = state[0];
        intensity = payload.intensity();
        expiresAtMillis = nowMillis + Math.min(MAX_LIFETIME_MILLIS, payload.durationMillis());
        return true;
    }

    private boolean acceptEnvelope(String nextEventId, long nextGeneration, long timestampMillis,
                                   boolean clear, boolean suspend, int nextCycle) {
        if (clear) {
            // CLEAR has no cycle field in the shared protocol. It can only close
            // the currently known cycle, never adopt another event/generation.
            if (!eventId.equals(nextEventId) || nextGeneration != generation
                    || timestampMillis < lastTimestampMillis) {
                return false;
            }
            if (!suspend) clearedCycle = currentCycle;
        } else {
            if (!eventId.equals(nextEventId) && !eventId.isBlank()) {
                if (retiredEventIds.contains(nextEventId)
                        || retiredEventIds.size() >= MAX_RETIRED_EVENTS
                        || timestampMillis <= sessionTimestampHighWatermark) {
                    return false;
                }
            } else if (nextGeneration < generation
                    || (nextGeneration == generation && terminal)) {
                return false;
            }
            if (!eventId.equals(nextEventId) || nextGeneration > generation) {
                if (!eventId.equals(nextEventId) && !eventId.isBlank()) {
                    retiredEventIds.add(eventId);
                }
                eventId = nextEventId;
                generation = nextGeneration;
                lastTimestampMillis = 0L;
                currentCycle = -1;
                clearedCycle = -1;
                terminal = false;
                clearActiveState();
            }
            if (timestampMillis <= lastTimestampMillis || nextCycle < currentCycle
                    || nextCycle <= clearedCycle) {
                return false;
            }
            currentCycle = nextCycle;
        }
        lastTimestampMillis = timestampMillis;
        sessionTimestampHighWatermark = Math.max(sessionTimestampHighWatermark, timestampMillis);
        return true;
    }

    public synchronized void tick(long nowMillis) {
        if (nowMillis >= 0L && expiresAtMillis > 0L && expiresAtMillis <= nowMillis) {
            clearActiveState();
        }
    }

    public synchronized float fogEndBlocks(String requestedDimension, long nowMillis) {
        tick(nowMillis);
        if (dimension.isBlank() || !dimension.equals(requestedDimension)) {
            return 0.0F;
        }
        return 30.0F - 20.0F * intensity;
    }

    public synchronized boolean active() {
        return expiresAtMillis > 0L;
    }

    public synchronized String eventId() { return eventId; }
    public synchronized long generation() { return generation; }
    public synchronized String dimension() { return dimension; }

    public synchronized void clear() {
        // Death/world change ends this generation without forgetting it.
        terminal = true;
        clearActiveState();
    }

    public synchronized boolean resumeAfterLocalExit(String expectedEventId, long expectedGeneration, long timestamp) {
        if (!terminal || !Objects.equals(expectedEventId, eventId) || expectedGeneration != generation
                || timestamp <= lastTimestampMillis || timestamp <= sessionTimestampHighWatermark) return false;
        terminal = false;
        lastTimestampMillis = timestamp - 1L;
        sessionTimestampHighWatermark = timestamp;
        return true;
    }

    public synchronized void reset() {
        eventId = "";
        generation = 0L;
        lastTimestampMillis = 0L;
        sessionTimestampHighWatermark = 0L;
        retiredEventIds.clear();
        currentCycle = -1;
        clearedCycle = -1;
        terminal = false;
        clearActiveState();
    }

    private void clearActiveState() {
        expiresAtMillis = 0L;
        dimension = "";
        intensity = 0.0F;
    }

    private static boolean validDimension(String value) {
        return "overworld".equals(value) || "the_nether".equals(value) || "the_end".equals(value);
    }

    static String[] parseMode(String mode) {
        if (mode == null || mode.length() > MAX_MODE_LENGTH) {
            return null;
        }
        return mode.split("\\|", -1);
    }

    private static boolean validCycle(String value) {
        return "0".equals(value) || "1".equals(value) || "2".equals(value);
    }
}
