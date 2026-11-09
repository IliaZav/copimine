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
    private static final float MIN_INTENSITY = 0.5F;
    private static final float MAX_INTENSITY = 1.0F;
    /** Latest Wave 5 protocol has exactly three cycles. */
    private static final int MAX_CYCLE = 3;

    private String eventId = "";
    private long generation;
    private long lastTimestampMillis;
    private long sessionTimestampHighWatermark;
    private final Set<String> retiredEventIds = new HashSet<>();
    private int currentCycle = -1;
    private int clearedCycle = -1;
    private boolean terminal;
    /** True once this generation has already reported a close-out. */
    private boolean cleared;
    /**
     * Set when a local exit (death, dimension change) was already resolved for
     * this generation. The viewer is re-synchronised from that point on, so a
     * CLEAR describing the pre-exit cycle no longer matches anything on screen.
     */
    private boolean resynchronized;
    private long expiresAtMillis;
    private String dimension = "";
    private float intensity;

    public synchronized boolean apply(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L || !Objects.equals(payload.type(), EVENT_TYPE)
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
            state = payload.mode().split("\\|", -1);
            if (state.length != 3 || !validDimension(state[0])
                    || !"wave5".equals(state[1]) || !validCycle(state[2])) {
                return false;
            }
        }
        int nextCycle = clear ? -1 : Integer.parseInt(state[2]);
        if (!acceptEnvelope(payload.sessionId(), payload.seq(), payload.timestampMillis(), clear, suspend, nextCycle)) {
            return false;
        }

        if (clear) {
            // A bare CLEAR acknowledges that a live hazard is over. Once this
            // generation has already reported a close-out, repeating the packet
            // must stay a successful no-op (replay is idempotent); before any
            // cycle ever ran there is nothing to acknowledge and reporting
            // success would let the caller believe it ended a hazard it never
            // showed. SUSPEND is the server withdrawing the fog and stays
            // accepted in every state.
            if (!suspend && cleared) {
                return true;
            }
            if (!suspend && (resynchronized || currentCycle < 0)) {
                return false;
            }
            if (suspend) terminal = true;
            cleared = true;
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
                cleared = false;
                clearActiveState();
            }
            if (timestampMillis <= lastTimestampMillis || nextCycle < currentCycle
                    || nextCycle <= clearedCycle) {
                return false;
            }
            currentCycle = nextCycle;
            // The close-out is per cycle: a fresh ACTIVE cycle reopens the
            // acknowledgement so its own CLEAR is recognised again.
            cleared = false;
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
        resynchronized = true;
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
        cleared = false;
        resynchronized = false;
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

    private static boolean validCycle(String value) {
        // Malformed input is a rejected packet, never an exception: a foreign or
        // corrupted bridge payload must not tear down the client tick loop.
        if (value == null || value.isEmpty() || value.length() > 2) return false;
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') return false;
        }
        return Integer.parseInt(value) >= 1 && Integer.parseInt(value) <= MAX_CYCLE;
    }
}
