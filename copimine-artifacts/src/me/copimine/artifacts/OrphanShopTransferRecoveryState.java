package me.copimine.artifacts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Tracks repeated keyset scans without letting bad rows or transient refunds starve later rows. */
public final class OrphanShopTransferRecoveryState<T> {
    private Cursor cursor = new Cursor(Long.MIN_VALUE, "");
    private boolean scanComplete;
    private final LinkedHashMap<String, T> pending = new LinkedHashMap<>();

    public synchronized Cursor cursor() {
        return cursor;
    }

    /** Advance from every fetched row before callers validate or refund individual rows. */
    public synchronized void recordFetchedPage(List<Cursor> fetched, int pageLimit) {
        Objects.requireNonNull(fetched, "fetched");
        if (pageLimit < 1 || fetched.size() > pageLimit) {
            throw new IllegalArgumentException("Invalid orphan-transfer page size.");
        }
        Cursor previous = cursor;
        for (Cursor next : fetched) {
            Objects.requireNonNull(next, "cursor");
            if (next.compareTo(previous) <= 0) {
                throw new IllegalArgumentException("Orphan-transfer keyset page is not strictly ordered.");
            }
            previous = next;
        }
        if (!fetched.isEmpty()) {
            cursor = fetched.get(fetched.size() - 1);
        }
        if (fetched.size() < pageLimit) {
            scanComplete = true;
        }
    }

    public synchronized void defer(String transactionId, T transfer) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("A deferred orphan transfer must have a transaction ID.");
        }
        pending.put(transactionId, Objects.requireNonNull(transfer, "transfer"));
    }

    public synchronized Map<String, T> pendingSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pending));
    }

    public synchronized void resolved(String transactionId) {
        if (transactionId != null) {
            pending.remove(transactionId);
        }
    }

    /** Begin another bounded scan after the current page scan, replaying recent timestamps for late commits. */
    public synchronized void beginNextScan(long nowMillis, long replayWindowMillis) {
        if (replayWindowMillis < 0L) {
            throw new IllegalArgumentException("Orphan-transfer replay window cannot be negative.");
        }
        if (!scanComplete) {
            throw new IllegalStateException("The current orphan-transfer keyset scan is still in progress.");
        }
        long replayBase = Math.min(cursor.createdAt(), nowMillis);
        long replayFrom = replayBase < Long.MIN_VALUE + replayWindowMillis
                ? Long.MIN_VALUE
                : replayBase - replayWindowMillis;
        cursor = new Cursor(replayFrom, "");
        scanComplete = false;
    }

    public synchronized boolean isScanComplete() {
        return scanComplete;
    }

    public synchronized boolean isComplete() {
        return scanComplete && pending.isEmpty();
    }

    public record Cursor(long createdAt, String transactionId) implements Comparable<Cursor> {
        public Cursor {
            Objects.requireNonNull(transactionId, "transactionId");
        }

        @Override
        public int compareTo(Cursor other) {
            int timeOrder = Long.compare(createdAt, other.createdAt);
            return timeOrder != 0 ? timeOrder : transactionId.compareTo(other.transactionId);
        }
    }
}
