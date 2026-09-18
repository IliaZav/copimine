package me.copimine.endevent.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory bounded burst buffer.  It keeps a rolling pre-history and a
 * bounded post-history for a short time after an invariant failure.
 */
public final class BoundedDiagnosticBurstCapture implements DiagnosticBurstCapture {
    private static final int MAX_CAPACITY = 4_096;
    private static final long MAX_POST_CAPTURE_MILLIS = 30_000L;

    private final int capacity;
    private final long postCaptureMillis;
    private final Deque<EndRiftDiagnosticEvent> history = new ArrayDeque<>();
    private final Deque<EndRiftDiagnosticEvent> postHistory = new ArrayDeque<>();
    private boolean capturing;
    private long captureDeadlineMillis;
    private String correlationId = "";
    private String reason = "";

    public BoundedDiagnosticBurstCapture(int capacity, long postCaptureMillis) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Burst capacity must be between 1 and " + MAX_CAPACITY);
        }
        if (postCaptureMillis < 0L) {
            throw new IllegalArgumentException("Post-capture duration cannot be negative");
        }
        this.capacity = capacity;
        this.postCaptureMillis = postCaptureMillis;
    }

    @Override
    public synchronized void sample(EndRiftDiagnosticEvent event) {
        if (event == null) {
            return;
        }
        trimAdd(history, event);
        if (capturing) {
            trimAdd(postHistory, event);
            if (System.currentTimeMillis() >= captureDeadlineMillis) {
                capturing = false;
            }
        }
    }

    @Override
    public synchronized void trigger(String correlationId, String reason) {
        trigger(correlationId, reason, postCaptureMillis);
    }

    /** Explicit admin captures may shorten the bounded post-history window. */
    public synchronized void trigger(String correlationId, String reason, long requestedMillis) {
        this.correlationId = correlationId == null ? "" : correlationId;
        this.reason = reason == null ? "" : reason;
        postHistory.clear();
        long boundedMillis = Math.max(0L, Math.min(MAX_POST_CAPTURE_MILLIS, requestedMillis));
        captureDeadlineMillis = System.currentTimeMillis() + boundedMillis;
        capturing = boundedMillis > 0L;
    }

    public synchronized boolean isCapturing() {
        if (capturing && System.currentTimeMillis() >= captureDeadlineMillis) {
            capturing = false;
        }
        return capturing;
    }

    public synchronized List<EndRiftDiagnosticEvent> snapshot() {
        List<EndRiftDiagnosticEvent> result = new ArrayList<>(history);
        for (EndRiftDiagnosticEvent event : postHistory) {
            if (!result.contains(event)) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    public synchronized String correlationId() {
        return correlationId;
    }

    public synchronized String reason() {
        return reason;
    }

    public synchronized int size() {
        return history.size();
    }

    public synchronized void clear() {
        history.clear();
        postHistory.clear();
        capturing = false;
        correlationId = "";
        reason = "";
    }

    private void trimAdd(Deque<EndRiftDiagnosticEvent> target, EndRiftDiagnosticEvent event) {
        while (target.size() >= capacity) {
            target.removeFirst();
        }
        target.addLast(event);
    }
}
