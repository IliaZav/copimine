package me.copimine.client;

import java.util.Set;

/**
 * Current End Rift semantic packet after the shared bridge envelope has been
 * decoded.  The bridge has a fixed, shared v2 envelope, so this adapter keeps
 * the event-specific meaning out of shared bridge fields such as
 * {@code shaderpack}.
 */
public record EndEventPacket(
        String type,
        String eventId,
        long generation,
        String instanceId,
        long durationMillis,
        String subjectId,
        String visualId,
        String phaseId,
        String controlId) {
    private static final int MAX_STRING_LENGTH = 256;
    private static final long MAX_DURATION_MILLIS = 600_000L;
    private static final Set<String> TYPES = Set.of(
            "END_BOSS_BIND",
            "END_BOSS_UNBIND",
            "END_BOSS_PHASE",
            "END_BOSS_BAR",
            "END_ENTITY_BIND",
            "END_ENTITY_UNBIND",
            "END_ENTITY_PHASE",
            "END_CONTROL_START",
            "END_CONTROL_STOP",
            "END_WORLD_BEAM",
            "END_WORLD_VFX_CLEAR");

    public EndEventPacket {
        type = bounded(type, "type");
        eventId = bounded(eventId, "eventId");
        instanceId = bounded(instanceId, "instanceId");
        subjectId = bounded(subjectId, "subjectId");
        visualId = bounded(visualId, "visualId");
        phaseId = bounded(phaseId, "phaseId");
        controlId = bounded(controlId, "controlId");
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown End Rift event type: " + type);
        }
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("End Rift eventId is required");
        }
        if (generation <= 0L) {
            throw new IllegalArgumentException("End Rift generation must be positive");
        }
        if (durationMillis < 0L || durationMillis > MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("End Rift duration is outside the safe bound");
        }
    }

    /**
     * Maps the fixed shared-bridge fields to the current event semantics.
     * Bind packets use the payload id as a visual id; phase packets use it as
     * a phase/animation specification. The distinction is made once here,
     * at the protocol boundary.
     */
    static EndEventPacket fromBridgePayload(String eventType, BridgePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("End Rift bridge payload is required");
        }
        String payloadId = !payload.clearPolicy().isBlank()
                ? payload.clearPolicy() : payload.shaderpack();
        boolean visual = eventType.endsWith("_BIND");
        boolean phase = eventType.endsWith("_PHASE") || "END_BOSS_BAR".equals(eventType);
        return new EndEventPacket(
                eventType,
                payload.sessionId(),
                payload.seq(),
                payload.clientVersion(),
                payload.durationMillis(),
                payload.mode(),
                visual ? payloadId : "",
                phase ? payloadId : "",
                payload.source());
    }

    private static String bounded(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("End Rift " + field + " is too long");
        }
        return normalized;
    }
}
