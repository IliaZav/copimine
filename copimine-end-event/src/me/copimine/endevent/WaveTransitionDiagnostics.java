package me.copimine.endevent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Non-blocking diagnostics sink for wave transitions and main-thread stalls.
 * Gameplay only enqueues a short JSON line; disk I/O is performed by one
 * bounded worker so a slow Windows filesystem cannot stall Paper's tick loop.
 */
public final class WaveTransitionDiagnostics {
    private static final int QUEUE_CAPACITY = 256;
    private static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;

    private final Path path;
    private final Logger logger;
    private final Object fileLock = new Object();
    private final ConcurrentHashMap<String, Long> transitionStartedAtNanos = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writer;
    private volatile boolean closed;

    public WaveTransitionDiagnostics(Path dataFolder, Logger logger) {
        this.logger = logger;
        this.path = dataFolder.resolve("diagnostics").resolve("wave-transitions.jsonl");
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create End Rift diagnostics directory", error);
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "copimine-end-event-diagnostics");
            thread.setDaemon(true);
            return thread;
        };
        writer = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public Path path() {
        return path;
    }

    public void recordWaveTransitionStarted(String eventId, long generation,
                                             int fromWave, int toWave,
                                             int plannedMobs, int activeTasks, String reason) {
        transitionStartedAtNanos.put(transitionKey(eventId, generation, toWave), System.nanoTime());
        record("WAVE_TRANSITION_STARTED", "eventId", eventId,
                "generation", generation, "fromWave", fromWave,
                "toWave", toWave, "plannedMobs", plannedMobs,
                "activeTasks", Math.max(0, activeTasks), "operationMillis", 0L,
                "reason", reason);
    }

    public void recordWaveTransitionStarted(String eventId, long generation,
                                             int fromWave, int toWave,
                                             int plannedMobs, String reason) {
        recordWaveTransitionStarted(eventId, generation, fromWave, toWave,
                plannedMobs, 0, reason);
    }

    public void recordWaveTransitionCommitted(String eventId, long generation,
                                               int wave, int spawnedMobs,
                                               int activeTasks) {
        long operationMillis = finishTransition(eventId, generation, wave);
        record("WAVE_TRANSITION_COMMITTED", "eventId", eventId,
                "generation", generation, "wave", wave,
                "spawnedMobs", spawnedMobs,
                "activeTasks", Math.max(0, activeTasks),
                "operationMillis", operationMillis);
    }

    public void recordWaveTransitionCommitted(String eventId, long generation,
                                               int wave, int spawnedMobs) {
        recordWaveTransitionCommitted(eventId, generation, wave, spawnedMobs, 0);
    }

    public void recordWaveTransitionFailed(String eventId, long generation,
                                           int wave, int activeTasks, Throwable error) {
        String type = error == null ? "Unknown" : error.getClass().getSimpleName();
        String message = error == null ? "unknown" : error.getMessage();
        String stackTrace = stackTrace(error);
        record("WAVE_TRANSITION_FAILED", "eventId", eventId,
                "generation", generation, "wave", wave,
                "activeTasks", Math.max(0, activeTasks),
                "operationMillis", finishTransition(eventId, generation, wave),
                "errorType", type, "errorMessage", message,
                "stackTrace", stackTrace);
    }

    public void recordWaveTransitionFailed(String eventId, long generation,
                                           int wave, Throwable error) {
        recordWaveTransitionFailed(eventId, generation, wave, 0, error);
    }

    public void recordMainThreadStall(String eventId, String phase, int wave,
                                      long generation, long ageMillis, int activeTasks) {
        record("WAVE_MAIN_THREAD_STALL", "eventId", eventId, "phase", phase,
                "wave", wave, "generation", generation,
                "ageMillis", ageMillis, "activeTasks", Math.max(0, activeTasks));
    }

    public void recordMainThreadStall(String phase, int wave, long generation,
                                      long ageMillis) {
        recordMainThreadStall("", phase, wave, generation, ageMillis, 0);
    }

    private long finishTransition(String eventId, long generation, int wave) {
        Long startedAt = transitionStartedAtNanos.remove(transitionKey(eventId, generation, wave));
        if (startedAt == null) {
            return 0L;
        }
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    private static String transitionKey(String eventId, long generation, int wave) {
        return String.valueOf(eventId) + '|' + generation + '|' + wave;
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter buffer = new StringWriter(1024);
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    private void record(String event, Object... fields) {
        if (closed) {
            return;
        }
        String line = jsonLine(event, fields);
        try {
            writer.execute(() -> append(line));
        } catch (RejectedExecutionException ignored) {
            logger.warning("END_EVENT_DIAGNOSTICS_QUEUE_FULL event=" + event);
        }
    }

    private void append(String line) {
        synchronized (fileLock) {
            try {
                if (Files.exists(path) && Files.size(path) >= MAX_FILE_BYTES) {
                    Path rotated = path.resolveSibling("wave-transitions.jsonl.1");
                    Files.move(path, rotated, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(path, line + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException error) {
                logger.log(Level.WARNING, "END_EVENT_DIAGNOSTICS_WRITE_FAILED path=" + path, error);
            }
        }
    }

    private static String jsonLine(String event, Object... fields) {
        StringBuilder json = new StringBuilder(192)
                .append('{')
                .append("\"timestamp\":\"")
                .append(escape(Instant.now().toString()))
                .append("\",\"event\":\"")
                .append(escape(event))
                .append('"');
        for (int index = 0; index + 1 < fields.length; index += 2) {
            json.append(",\"").append(escape(String.valueOf(fields[index])))
                    .append("\":");
            Object value = fields[index + 1];
            if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public void closeAndFlush() {
        if (closed) {
            return;
        }
        closed = true;
        transitionStartedAtNanos.clear();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2L, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
