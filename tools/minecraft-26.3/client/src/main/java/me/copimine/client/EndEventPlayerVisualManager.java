package me.copimine.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Server-authored, bounded Wave 1/2 player presentation; never advances gameplay. */
public final class EndEventPlayerVisualManager {
    private static final String EVENT_TYPE = ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_PLAYER_STATE";
    private static final int MAX_EVENT_IDENTITIES = 129;
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_LIFETIME_MILLIS = 15_000;

    public enum Kind {
        CARRIER("WAVE1_CARRIER_V1", "wave1-carrier-charge", "wave1"),
        HUNT("WAVE2_HUNT_MARK_V1", "wave2-hunt-mark", "wave2");

        private final String visualId;
        private final String instanceId;
        private final String wave;

        Kind(String visualId, String instanceId, String wave) {
            this.visualId = visualId;
            this.instanceId = instanceId;
            this.wave = wave;
        }

        private static Kind from(BridgePayload payload) {
            for (Kind kind : values()) {
                if (kind.visualId.equals(payload.shaderpack()) && kind.instanceId.equals(payload.clientVersion())) {
                    return kind;
                }
            }
            return null;
        }
    }

    public record VisualState(Kind kind, long startedAtMillis, long expiresAtMillis) { }

    private static final class Channel {
        private int cycle = -1;
        private long timestamp;
        private long startedAtMillis;
        private long expiresAtMillis;
    }

    private static final class EventFence {
        private final long generation;
        private final Channel[] channels = {new Channel(), new Channel()};
        private String dimension = "";
        private boolean retired;
        private boolean locallySuspended;
        private long resumedAtTimestamp;

        private EventFence(long generation) { this.generation = generation; }

        private void clearPresentation() {
            for (Channel channel : channels) channel.expiresAtMillis = 0L;
        }
    }

    // Unknown CLEARs retain a tombstone without becoming the current event.
    // Retired ids are never evicted: capacity fails closed until transport reset.
    private final Map<String, EventFence> eventFences = new LinkedHashMap<>();
    private String eventId = "";
    private EventFence current;
    private long sessionTimestampHighWatermark;

    /** mode is dimension|wave1/2|cycle for ACTIVE, CLEAR and SUSPEND alike. */
    public synchronized boolean apply(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L || !EVENT_TYPE.equals(payload.messageType()) || payload.protocol() != 2
                || payload.sessionId().isBlank() || payload.sessionId().length() > MAX_EVENT_ID_LENGTH
                || payload.seq() <= 0L || payload.timestampMillis() <= 0L || payload.mode().length() > 64) {
            return false;
        }
        Kind kind = Kind.from(payload);
        boolean active = "ACTIVE".equalsIgnoreCase(payload.status());
        boolean suspend = "SUSPEND".equalsIgnoreCase(payload.status());
        boolean clear = suspend || "CLEAR".equalsIgnoreCase(payload.status());
        if (kind == null || !active && !clear || active && (payload.durationMillis() <= 0
                || payload.durationMillis() > MAX_LIFETIME_MILLIS
                || nowMillis > Long.MAX_VALUE - payload.durationMillis())) return false;

        String[] mode = payload.mode().split("\\|", -1);
        if (mode.length != 3 || !validDimension(mode[0]) || !kind.wave.equals(mode[1])) return false;
        int cycle = parseCycle(mode[2]);
        if (cycle < 0) return false;

        boolean matchesCurrent = eventId.equals(payload.sessionId());
        EventFence fence = eventFences.get(payload.sessionId());
        if (fence != null && (fence.retired || payload.seq() < fence.generation)) return false;
        if (clear && matchesCurrent && payload.seq() != current.generation) return false;
        if (active && !matchesCurrent && current != null
                && payload.timestampMillis() <= sessionTimestampHighWatermark) return false;
        if (fence == null && eventFences.size() >= MAX_EVENT_IDENTITIES) return false;

        // A higher generation atomically replaces both channel fences. It may
        // arrive after a server restart with a lower timestamp than before.
        if (fence == null || payload.seq() > fence.generation) fence = new EventFence(payload.seq());
        if (!fence.dimension.isBlank() && !fence.dimension.equals(mode[0])) return false;
        if (active && (fence.locallySuspended || payload.timestampMillis() < fence.resumedAtTimestamp)) return false;
        Channel channel = fence.channels[kind.ordinal()];
        if (cycle < channel.cycle || payload.timestampMillis() < channel.timestamp
                || active && payload.timestampMillis() == channel.timestamp) return false;
        if (clear && payload.timestampMillis() == channel.timestamp && channel.cycle != cycle) return false;

        boolean keepStart = active && channel.cycle == cycle && channel.expiresAtMillis > nowMillis;
        eventFences.put(payload.sessionId(), fence);
        if (active && !matchesCurrent) {
            if (current != null) {
                current.retired = true;
                current.clearPresentation();
            }
            eventId = payload.sessionId();
            current = fence;
        } else if (matchesCurrent) {
            current = fence;
        }
        fence.dimension = mode[0];
        channel.cycle = cycle;
        channel.timestamp = payload.timestampMillis();
        if (active) {
            if (!keepStart) channel.startedAtMillis = nowMillis;
            channel.expiresAtMillis = nowMillis + payload.durationMillis();
        } else {
            channel.expiresAtMillis = 0L;
            if (suspend) {
                fence.locallySuspended = true;
                fence.clearPresentation();
            }
        }
        if (current == fence) {
            sessionTimestampHighWatermark = Math.max(sessionTimestampHighWatermark, payload.timestampMillis());
        }
        return true;
    }

    /** Render only one restrained edge treatment, even if channel updates overlap. */
    public synchronized VisualState visualState(String requestedDimension, long nowMillis) {
        tick(nowMillis);
        if (current == null || current.locallySuspended || !current.dimension.equals(requestedDimension)) return null;
        Channel selected = null;
        Kind selectedKind = null;
        for (Kind kind : Kind.values()) {
            Channel channel = current.channels[kind.ordinal()];
            if (channel.expiresAtMillis > 0L && (selected == null || channel.timestamp >= selected.timestamp)) {
                selected = channel;
                selectedKind = kind;
            }
        }
        return selected == null ? null : new VisualState(selectedKind, selected.startedAtMillis, selected.expiresAtMillis);
    }

    public synchronized void tick(long nowMillis) {
        if (current == null || nowMillis < 0L) return;
        for (Channel channel : current.channels) {
            if (channel.expiresAtMillis > 0L && channel.expiresAtMillis <= nowMillis) channel.expiresAtMillis = 0L;
        }
    }

    /** Death/world exit blocks queued packets without losing their generation. */
    public synchronized void clear() {
        if (current == null) return;
        current.locallySuspended = true;
        current.clearPresentation();
    }

    /** The caller also verifies a living local player and the actual current world. */
    public synchronized boolean resumeAfterLocalExit(String expectedEventId, long expectedGeneration,
                                                     long timestamp, String currentDimension) {
        if (current == null || !current.locallySuspended || !Objects.equals(eventId, expectedEventId)
                || current.generation != expectedGeneration || !current.dimension.equals(currentDimension)
                || timestamp <= sessionTimestampHighWatermark) return false;
        current.locallySuspended = false;
        current.resumedAtTimestamp = timestamp;
        sessionTimestampHighWatermark = timestamp;
        return true;
    }

    /** Only JOIN/DISCONNECT reset transport-specific retired identity fences. */
    public synchronized void reset() {
        eventFences.clear();
        eventId = "";
        current = null;
        sessionTimestampHighWatermark = 0L;
    }

    public synchronized String eventId() { return eventId; }
    public synchronized long generation() { return current == null ? 0L : current.generation; }

    private static boolean validDimension(String value) {
        return "overworld".equals(value) || "the_nether".equals(value) || "the_end".equals(value);
    }

    private static int parseCycle(String value) {
        if (value.isEmpty() || value.length() > 10 || !value.matches("[0-9]+")) return -1;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
