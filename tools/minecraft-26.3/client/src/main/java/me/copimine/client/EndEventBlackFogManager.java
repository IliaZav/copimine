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
    /** Initial visual tuning: nearby silhouettes survive, distant geometry is obscured. */
    private static final float DENSE_FOG_END_BLOCKS = 4.0F;
    public static final long FADE_MILLIS = 1_250L;
    public static final long ONSET_MILLIS = 300L;
    private static final int MAX_CYCLE = 10;

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
    private long startedAtMillis;
    private long initialLifetimeMillis;
    private long fadeOutAtMillis = -1L;
    private float fadeOutFrom;


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
        tick(nowMillis);
        int previousCycle = currentCycle;
        if (!acceptEnvelope(payload.sessionId(), payload.seq(), payload.timestampMillis(), clear, suspend, nextCycle)) {
            return false;
        }
        if (clear) {
            if (suspend) {
                terminal = true;
                clearActiveState();
            } else {
                beginFadeOut(nowMillis);
            }
            return true;
        }
        // A refresh extends the lease without resetting cycle progress.
        if (expiresAtMillis == 0L || previousCycle != nextCycle) {
            boolean continuingCycle = previousCycle == nextCycle && !dimension.isBlank();
            // A late refresh immediately restores full density, without rewinding motion.
            if (!continuingCycle) {
                startedAtMillis = nowMillis;
                initialLifetimeMillis = payload.durationMillis();
            }
            fadeOutAtMillis = -1L;
            fadeOutFrom = 0.0F;
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
        if (nowMillis < 0L) return;
        if (expiresAtMillis > 0L && expiresAtMillis <= nowMillis) {
            beginFadeOut(expiresAtMillis);
        }
        if (fadeOutAtMillis >= 0L && nowMillis - fadeOutAtMillis >= FADE_MILLIS) {
            clearActiveState();
        }
    }

    /** Target distance; rendering blends vanilla FogData using fadeStrength. */
    public synchronized float fogEndBlocks(String requestedDimension, long nowMillis) {
        tick(nowMillis);
        return !dimension.isBlank() && dimension.equals(requestedDimension) ? DENSE_FOG_END_BLOCKS : 0.0F;
    }

    public synchronized float fadeStrength(String requestedDimension, long nowMillis) {
        tick(nowMillis);
        if (dimension.isBlank() || !dimension.equals(requestedDimension)) return 0.0F;
        return currentStrength(nowMillis);
    }

    private float currentStrength(long nowMillis) {
        if (fadeOutAtMillis >= 0L) {
            return fadeOutFrom * (1.0F - smooth((float) (nowMillis - fadeOutAtMillis) / FADE_MILLIS));
        }
        // Admit the pass on the first frame, then roll the volume in without a black flash.
        // Refreshes retain the original onset, including recovery of a late lease.
        return expiresAtMillis > 0L
                ? 0.01F + 0.99F * smooth((float) (nowMillis - startedAtMillis) / ONSET_MILLIS)
                : 0.0F;
    }

    private static float smooth(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    private void beginFadeOut(long nowMillis) {
        // Duplicate CLEAR cannot restart or lengthen an existing fade.
        if (expiresAtMillis <= 0L) return;
        fadeOutFrom = currentStrength(nowMillis);
        fadeOutAtMillis = nowMillis;
        expiresAtMillis = 0L;
    }

    public synchronized boolean active() {
        return expiresAtMillis > 0L;
    }

    /** Progress uses the first lease, so repeated refreshes cannot rewind it. */
    public synchronized float cycleProgress(long nowMillis) {
        if (dimension.isBlank() || nowMillis < 0L || initialLifetimeMillis <= 0L) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F,
                (float) (nowMillis - startedAtMillis) / initialLifetimeMillis));
    }

    /** Normalised alert strength the server sent for this cycle (0.5..1.0). */
    public synchronized float intensity() {
        return expiresAtMillis > 0L ? intensity : 0.0F;
    }

    /** First-cycle age is stable across lease refreshes; CLEAR has no guide. */
    public synchronized long cycleAgeMillis(long nowMillis) {
        return expiresAtMillis > 0L ? Math.max(0L, nowMillis - startedAtMillis) : Long.MAX_VALUE;
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
        startedAtMillis = 0L;
        initialLifetimeMillis = 0L;
        fadeOutAtMillis = -1L;
        fadeOutFrom = 0.0F;
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
