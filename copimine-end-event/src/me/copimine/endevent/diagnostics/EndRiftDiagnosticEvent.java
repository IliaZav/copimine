package me.copimine.endevent.diagnostics;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One structured, correlated End Rift diagnostic event.
 *
 * <p>The record deliberately contains the fields needed to identify an event
 * attempt before the extensible {@code fields} map is considered.  Callers
 * may create an event with sequence {@code 0}; the diagnostic sink assigns
 * the process-wide monotonic sequence when it accepts the event.</p>
 */
public record EndRiftDiagnosticEvent(
        long sequence,
        Instant timestamp,
        long serverTick,
        long generation,
        String eventId,
        String category,
        String action,
        String severity,
        Integer wave,
        String phase,
        UUID playerId,
        UUID entityId,
        UUID relatedEntityId,
        String correlationId,
        String reason,
        Map<String, Object> fields
) {
    public EndRiftDiagnosticEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        eventId = safe(eventId);
        category = safe(category);
        action = safe(action);
        severity = safe(severity);
        phase = safe(phase);
        correlationId = safe(correlationId);
        reason = safe(reason);
        fields = immutableFields(fields);
    }

    /** Return the same event with a sink-assigned sequence number. */
    public EndRiftDiagnosticEvent withSequence(long nextSequence) {
        return new EndRiftDiagnosticEvent(nextSequence, timestamp, serverTick, generation,
                eventId, category, action, severity, wave, phase, playerId, entityId,
                relatedEntityId, correlationId, reason, fields);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> immutableFields(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
