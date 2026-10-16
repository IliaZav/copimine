package me.copimine.endevent.diagnostics;

/** Immutable counters for one diagnostic writer lifetime. */
public record EndRiftDiagnosticSinkStats(
        long submitted,
        long accepted,
        long dropped,
        long written,
        long writeFailures,
        long maxQueueDepth,
        long bytesWritten,
        long rotations,
        boolean closed
) {
    public boolean complete() {
        return dropped == 0L && writeFailures == 0L;
    }
}
