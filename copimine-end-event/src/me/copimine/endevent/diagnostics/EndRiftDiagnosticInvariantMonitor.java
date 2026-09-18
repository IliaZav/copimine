package me.copimine.endevent.diagnostics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Observational invariant monitor; it never mutates encounter state. */
public final class EndRiftDiagnosticInvariantMonitor {
    private final EndRiftDiagnosticSink sink;
    private final DiagnosticBurstCapture burstCapture;
    private final String eventId;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile String lastFailure;

    public EndRiftDiagnosticInvariantMonitor(EndRiftDiagnosticSink sink,
                                             DiagnosticBurstCapture burstCapture) {
        this(sink, burstCapture, "end-rift");
    }

    public EndRiftDiagnosticInvariantMonitor(EndRiftDiagnosticSink sink,
                                             DiagnosticBurstCapture burstCapture,
                                             String eventId) {
        this.sink = sink;
        this.burstCapture = burstCapture;
        this.eventId = eventId == null ? "" : eventId;
    }

    public boolean require(boolean condition, String invariant, String correlationId,
                           Map<String, Object> fields) {
        if (condition) {
            return true;
        }
        return recordFailure(new EndRiftDiagnosticEvent(
                0L, Instant.now(), 0L, 0L, eventId, "ASSERTION", "INVARIANT_FAIL",
                "ERROR", null, "", null, null, null,
                correlationId, invariant, fields), invariant, correlationId, fields);
    }

    /** Context-preserving overload for runtime reports. */
    public boolean require(boolean condition, long serverTick, long generation, Integer wave,
                           String phase, String invariant, String correlationId,
                           Map<String, Object> fields) {
        if (condition) {
            return true;
        }
        return recordFailure(new EndRiftDiagnosticEvent(
                0L, Instant.now(), Math.max(0L, serverTick), Math.max(0L, generation),
                eventId, "ASSERTION", "INVARIANT_FAIL", "ERROR", wave, phase,
                null, null, null, correlationId, invariant, fields), invariant,
                correlationId, fields);
    }

    private boolean recordFailure(EndRiftDiagnosticEvent context, String invariant,
                                  String correlationId, Map<String, Object> fields) {
        String safeInvariant = invariant == null ? "UNKNOWN_INVARIANT" : invariant;
        String safeCorrelation = correlationId == null ? "" : correlationId;
        failures.incrementAndGet();
        lastFailure = safeInvariant;
        Map<String, Object> eventFields = new LinkedHashMap<>();
        if (fields != null) {
            eventFields.putAll(fields);
        }
        eventFields.put("invariant", safeInvariant);
        eventFields.put("accepted", false);
        EndRiftDiagnosticEvent event = new EndRiftDiagnosticEvent(
                context.sequence(), context.timestamp(), context.serverTick(), context.generation(),
                context.eventId(), context.category(), context.action(), context.severity(),
                context.wave(), context.phase(), context.playerId(), context.entityId(),
                context.relatedEntityId(), safeCorrelation, safeInvariant, eventFields);
        try {
            if (sink != null) {
                sink.publish(event);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics are strictly observational; a broken sink cannot
            // cancel an event, damage, cleanup, or any other gameplay action.
        }
        try {
            if (burstCapture != null) {
                burstCapture.trigger(safeCorrelation, safeInvariant);
            }
        } catch (RuntimeException ignored) {
            // The bounded capture is also best-effort and never gameplay authority.
        }
        return false;
    }

    public int failureCount() {
        return failures.get();
    }

    public Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }
}
