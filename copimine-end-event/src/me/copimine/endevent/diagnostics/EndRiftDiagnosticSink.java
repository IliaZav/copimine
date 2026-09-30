package me.copimine.endevent.diagnostics;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded, single-writer JSONL sink.  Publication is a non-blocking queue
 * offer, so a slow filesystem cannot change gameplay timing or decisions.
 */
public final class EndRiftDiagnosticSink implements AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 4_096;
    public static final long DEFAULT_MAX_FILE_BYTES = 4L * 1024L * 1024L;
    private static final Pattern SEQUENCE_FIELD = Pattern.compile(
            "\\\"sequence\\\"\\s*:\\s*(\\d+)");

    private final Path path;
    private final Logger logger;
    private final ArrayBlockingQueue<EndRiftDiagnosticEvent> queue;
    private final long maxFileBytes;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong maxQueueDepth = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong rotations = new AtomicLong();
    private final AtomicLong lastDropWarningNanos = new AtomicLong();
    private final AtomicLong lastWriteWarningNanos = new AtomicLong();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Thread writerThread;
    private volatile boolean closed;

    public EndRiftDiagnosticSink(Path path, Logger logger) {
        this(path, logger, DEFAULT_QUEUE_CAPACITY, DEFAULT_MAX_FILE_BYTES);
    }

    public EndRiftDiagnosticSink(Path path, Logger logger, int queueCapacity, long maxFileBytes) {
        if (path == null) {
            throw new IllegalArgumentException("Diagnostic path is required");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Diagnostic queue capacity must be positive");
        }
        if (maxFileBytes < 1L) {
            throw new IllegalArgumentException("Diagnostic file limit must be positive");
        }
        this.path = path;
        this.logger = logger == null ? Logger.getLogger(EndRiftDiagnosticSink.class.getName()) : logger;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.maxFileBytes = maxFileBytes;
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create diagnostic directory: " + path, error);
        }
        sequence.set(readExistingHighestSequence());
        writerThread = new Thread(this::writeLoop, "copimine-end-rift-diagnostic-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public Path path() {
        return path;
    }

    /**
     * Enqueue one event without waiting for disk I/O.  The sink owns the
     * sequence number even when a caller supplied a placeholder sequence.
     */
    public boolean publish(EndRiftDiagnosticEvent event) {
        submitted.incrementAndGet();
        if (event == null || closing.get()) {
            dropped.incrementAndGet();
            return false;
        }
        EndRiftDiagnosticEvent sequenced = event.withSequence(sequence.incrementAndGet());
        if (!queue.offer(sequenced)) {
            dropped.incrementAndGet();
            rateLimitedWarning(lastDropWarningNanos,
                    "END_RIFT_DIAGNOSTICS_QUEUE_FULL event=" + sequenced.category()
                            + "/" + sequenced.action() + " dropped=" + dropped.get());
            return false;
        }
        accepted.incrementAndGet();
        updateMaximum(maxQueueDepth, queue.size());
        return true;
    }

    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public EndRiftDiagnosticSinkStats stats() {
        return new EndRiftDiagnosticSinkStats(submitted.get(), accepted.get(), dropped.get(),
                written.get(), writeFailures.get(), maxQueueDepth.get(), bytesWritten.get(),
                rotations.get(), closed);
    }

    /** Wait for the bounded queue to drain; intended for tests and admin dumps. */
    public boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(2L);
        }
        // Let the single writer finish the item it may have taken just before
        // the queue became empty.
        while (written.get() + writeFailures.get() < accepted.get()
                && System.nanoTime() < deadline) {
            Thread.sleep(2L);
        }
        return queue.isEmpty() && written.get() + writeFailures.get() >= accepted.get();
    }

    /** Stop the writer after all accepted events have had a chance to flush. */
    public void closeAndFlush() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        try {
            writerThread.join(2_000L);
            if (writerThread.isAlive()) {
                writerThread.interrupt();
                writerThread.join(500L);
                if (writerThread.isAlive()) {
                    logger.warning("END_RIFT_DIAGNOSTICS_WRITER_CLOSE_TIMEOUT path=" + path);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writerThread.interrupt();
        } finally {
            closed = !writerThread.isAlive();
        }
    }

    @Override
    public void close() {
        closeAndFlush();
    }

    private void writeLoop() {
        while (!closing.get() || !queue.isEmpty()) {
            try {
                EndRiftDiagnosticEvent event = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (event != null) {
                    append(event);
                }
            } catch (InterruptedException interrupted) {
                if (closing.get() && queue.isEmpty()) {
                    break;
                }
                // Clear the interrupt before the next bounded poll.  Leaving
                // the flag set would make every following poll fail
                // immediately and could strand accepted records during a
                // close while the queue is still draining.
                Thread.interrupted();
            } catch (RuntimeException error) {
                writeFailures.incrementAndGet();
                rateLimitedWarning(lastWriteWarningNanos,
                        "END_RIFT_DIAGNOSTICS_WRITER_FAILURE path=" + path + " error=" + error);
            }
        }
        closed = true;
    }

    private void append(EndRiftDiagnosticEvent event) {
        byte[] bytes = (EndRiftDiagnosticJson.toJson(event) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        try {
            if (Files.exists(path) && Files.size(path) > 0L
                    && Files.size(path) + bytes.length > maxFileBytes) {
                Path rotated = path.resolveSibling(path.getFileName() + ".1");
                Files.move(path, rotated, StandardCopyOption.REPLACE_EXISTING);
                rotations.incrementAndGet();
            }
            Files.write(path, bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            written.incrementAndGet();
            bytesWritten.addAndGet(bytes.length);
        } catch (IOException | RuntimeException error) {
            writeFailures.incrementAndGet();
            rateLimitedWarning(lastWriteWarningNanos,
                    "END_RIFT_DIAGNOSTICS_WRITE_FAILED path=" + path + " error=" + error);
        }
    }

    /**
     * Continue the sequence after a plugin/server restart.  The central file
     * is append-only across writer lifetimes, so resetting to one would make
     * a later report reject otherwise valid post-restart evidence as a
     * duplicate sequence.  Startup I/O is outside the Paper tick loop and is
     * bounded to the active file plus its one rotated predecessor.
     */
    private long readExistingHighestSequence() {
        long highest = 0L;
        for (Path candidate : new Path[] {path, path.resolveSibling(path.getFileName() + ".1")}) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (BufferedReader lines = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    Matcher matcher = SEQUENCE_FIELD.matcher(line);
                    if (!matcher.find()) {
                        continue;
                    }
                    try {
                        highest = Math.max(highest, Long.parseLong(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                        logger.warning("END_RIFT_DIAGNOSTICS_SEQUENCE_READ_FAILED path=" + candidate);
                    }
                }
            } catch (IOException error) {
                logger.log(Level.WARNING,
                        "END_RIFT_DIAGNOSTICS_SEQUENCE_READ_FAILED path=" + candidate, error);
            }
        }
        return highest;
    }

    private void rateLimitedWarning(AtomicLong lastWarning, String message) {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        if (previous == 0L || now - previous >= TimeUnit.SECONDS.toNanos(5L)) {
            if (lastWarning.compareAndSet(previous, now)) {
                logger.log(Level.WARNING, message);
            }
        }
    }

    private static void updateMaximum(AtomicLong target, long value) {
        long previous;
        do {
            previous = target.get();
            if (value <= previous) {
                return;
            }
        } while (!target.compareAndSet(previous, value));
    }
}
