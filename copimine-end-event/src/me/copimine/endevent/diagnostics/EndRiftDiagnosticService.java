package me.copimine.endevent.diagnostics;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Central End Rift diagnostic façade.  Gameplay code publishes lifecycle
 * decisions here instead of constructing ad-hoc JSON or log strings.
 */
public final class EndRiftDiagnosticService implements AutoCloseable {
    private static final Set<String> ESSENTIAL_CATEGORIES = Set.of(
            "EVENT", "PHASE", "WAVE", "OBJECTIVE", "PERSISTENCE", "RECOVERY",
            "CLEANUP", "ASSERTION", "ERROR", "PERFORMANCE", "ENTITY", "TASK",
            "BOSS", "BOSS_HITBOX", "RITUAL_PROJECTILE", "RITUAL_PRISONER",
            "RITUAL_ZONE", "RITUAL_CONTROL", "RITUAL_CASTER", "WAVE7_BARRIER",
            "PACKET");
    private static final Map<String, Set<String>> ESSENTIAL_DETAIL_EXCLUSIONS = Map.of(
            "EVENT", Set.of("SNAPSHOT"),
            "RITUAL_PROJECTILE", Set.of("STEER", "STEER_SAMPLE"),
            "RITUAL_PRISONER", Set.of("SNAPSHOT"),
            "BOSS_HITBOX", Set.of("SNAPSHOT"));

    private final String eventId;
    private final EndRiftDiagnosticSink sink;
    private final BoundedDiagnosticBurstCapture burstCapture;
    private final EndRiftDiagnosticInvariantMonitor invariantMonitor;
    private volatile EndRiftDiagnosticMode mode;

    public EndRiftDiagnosticService(Path dataFolder, Logger logger, String eventId,
                                    EndRiftDiagnosticMode mode) {
        this(dataFolder.resolve("diagnostics").resolve("end-rift-events.jsonl"),
                logger, eventId, mode, EndRiftDiagnosticSink.DEFAULT_QUEUE_CAPACITY,
                EndRiftDiagnosticSink.DEFAULT_MAX_FILE_BYTES);
    }

    public EndRiftDiagnosticService(Path path, Logger logger, String eventId,
                                    EndRiftDiagnosticMode mode, int queueCapacity,
                                    long maxFileBytes) {
        this.eventId = eventId == null ? "" : eventId;
        this.mode = mode == null ? EndRiftDiagnosticMode.ESSENTIAL : mode;
        this.sink = new EndRiftDiagnosticSink(path, logger, queueCapacity, maxFileBytes);
        this.burstCapture = new BoundedDiagnosticBurstCapture(512, 8_000L);
        this.invariantMonitor = new EndRiftDiagnosticInvariantMonitor(sink, burstCapture, this.eventId);
    }

    public EndRiftDiagnosticSink sink() {
        return sink;
    }

    public BoundedDiagnosticBurstCapture burstCapture() {
        return burstCapture;
    }

    public EndRiftDiagnosticInvariantMonitor invariants() {
        return invariantMonitor;
    }

    public EndRiftDiagnosticMode mode() {
        return mode;
    }

    public void mode(EndRiftDiagnosticMode nextMode) {
        mode = nextMode == null ? EndRiftDiagnosticMode.ESSENTIAL : nextMode;
    }

    public boolean emit(long serverTick, long generation, Integer wave, String phase,
                        String category, String action, String severity,
                        UUID playerId, UUID entityId, UUID relatedEntityId,
                        String correlationId, String reason, Map<String, Object> fields) {
        String safeCategory = category == null ? "" : category;
        String safeAction = action == null ? "" : action;
        String safeSeverity = severity == null ? "INFO" : severity;
        if (!shouldEmit(safeCategory, safeAction, safeSeverity)) {
            return false;
        }
        EndRiftDiagnosticEvent event = new EndRiftDiagnosticEvent(
                0L, Instant.now(), Math.max(0L, serverTick), Math.max(0L, generation),
                eventId, safeCategory, safeAction, safeSeverity, wave, phase, playerId,
                entityId, relatedEntityId, correlationId, reason, fields);
        try {
            boolean accepted = sink.publish(event);
            burstCapture.sample(event);
            return accepted;
        } catch (RuntimeException ignored) {
            // An observational channel must never change gameplay control flow.
            return false;
        }
    }

    public boolean stateTransition(long serverTick, long generation, Integer wave,
                                   String phase, String stateBefore, String stateAfter,
                                   String trigger, boolean accepted, String reason,
                                   String correlationId) {
        return emit(serverTick, generation, wave, phase, "PHASE",
                accepted ? "UPDATE" : "REJECT", accepted ? "INFO" : "WARN",
                null, null, null, correlationId, reason,
                Map.of("stateBefore", stateBefore == null ? "" : stateBefore,
                        "stateAfter", stateAfter == null ? "" : stateAfter,
                        "trigger", trigger == null ? "" : trigger,
                        "accepted", accepted));
    }

    public boolean exception(long serverTick, long generation, Integer wave, String phase,
                             String correlationId, String operation, Throwable error) {
        String type = error == null ? "Unknown" : error.getClass().getName();
        String message = error == null ? "unknown" : String.valueOf(error.getMessage());
        String stack = stackTrace(error);
        return emit(serverTick, generation, wave, phase, "ERROR", "FAIL", "ERROR",
                null, null, null, correlationId, operation,
                Map.of("exceptionType", type, "message", message,
                        "stackTrace", stack, "operation", operation == null ? "" : operation));
    }

    public boolean invariant(boolean condition, long serverTick, long generation,
                             Integer wave, String phase, String invariant,
                             String correlationId, Map<String, Object> fields) {
        if (condition) {
            return true;
        }
        // Invariant failures are always essential and retain their full
        // runtime context in one record; the monitor remains observational.
        return invariantMonitor.require(false, serverTick, generation, wave, phase,
                invariant, correlationId, fields);
    }

    public EndRiftDiagnosticSinkStats stats() {
        return sink.stats();
    }

    public Path path() {
        return sink.path();
    }

    /**
     * Write the latest canonical snapshot on explicit admin request.  The
     * periodic snapshot path remains memory-only; this method is intentionally
     * command-time I/O so a debug dump cannot add disk work to the tick loop.
     */
    public Path writeSnapshot(EndRiftDiagnosticSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            throw new IllegalArgumentException("Diagnostic snapshot is required");
        }
        Path target = sink.path().resolveSibling("snapshot.json");
        Files.writeString(target, snapshot.toJson() + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return target;
    }

    public void closeAndFlush() {
        sink.closeAndFlush();
    }

    @Override
    public void close() {
        closeAndFlush();
    }

    private boolean shouldEmit(String category, String action, String severity) {
        EndRiftDiagnosticMode current = mode;
        if (current == EndRiftDiagnosticMode.TRACE || current == EndRiftDiagnosticMode.VERBOSE) {
            return true;
        }
        if (current == EndRiftDiagnosticMode.ESSENTIAL) {
            Set<String> excludedActions = ESSENTIAL_DETAIL_EXCLUSIONS.get(category);
            return (ESSENTIAL_CATEGORIES.contains(category)
                    && (excludedActions == null || !excludedActions.contains(action)))
                    || "ERROR".equalsIgnoreCase(severity)
                    || "CRITICAL".equalsIgnoreCase(severity);
        }
        return "ERROR".equalsIgnoreCase(severity)
                || "CRITICAL".equalsIgnoreCase(severity)
                || "ASSERTION".equals(category)
                || "ERROR".equals(category);
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        java.io.StringWriter buffer = new java.io.StringWriter(1024);
        error.printStackTrace(new java.io.PrintWriter(buffer));
        return buffer.toString();
    }
}
