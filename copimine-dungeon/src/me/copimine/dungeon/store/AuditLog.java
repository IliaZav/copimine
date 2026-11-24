package me.copimine.dungeon.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded, in-memory audit trail for editor/admin actions (spec OV4.3:
 * "optional builder permission grant is separate and audited"; administrator
 * publishes and approvals are auditable). The newest entry is first.
 */
public final class AuditLog {

    /** Default retained entry count. */
    public static final int DEFAULT_CAPACITY = 256;

    private final int capacity;
    private final Deque<String> entries = new ArrayDeque<>();

    public AuditLog() {
        this(DEFAULT_CAPACITY);
    }

    public AuditLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public void record(String actor, String action, String result) {
        String entry = (actor == null ? "?" : actor) + " | " + (action == null ? "" : action)
                + " | " + (result == null ? "" : result);
        entries.addFirst(entry);
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    public List<String> recent(int limit) {
        List<String> result = new ArrayList<>();
        int count = 0;
        for (String entry : entries) {
            if (count >= limit) {
                break;
            }
            result.add(entry);
            count++;
        }
        return result;
    }

    public List<String> all() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
