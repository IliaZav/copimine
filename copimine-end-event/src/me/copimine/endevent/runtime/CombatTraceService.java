package me.copimine.endevent.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import me.copimine.endevent.domain.CombatTraceRecord;

/** Bounded, in-memory diagnostic ring buffer for one local event attempt. */
public final class CombatTraceService {
    private static final int MAX_CAPACITY = 4_096;
    private final int capacity;
    private final Deque<CombatTraceRecord> records = new ArrayDeque<>();

    public CombatTraceService(int capacity) {
        this.capacity = Math.max(1, Math.min(MAX_CAPACITY, capacity));
    }

    public synchronized void record(CombatTraceRecord record) {
        if (record == null) {
            return;
        }
        while (records.size() >= capacity) {
            records.removeFirst();
        }
        records.addLast(record);
    }

    public synchronized List<CombatTraceRecord> snapshot() {
        return List.copyOf(records);
    }

    public synchronized void clear() {
        records.clear();
    }
}
