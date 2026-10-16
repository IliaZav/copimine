package me.copimine.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tracks active authenticated visual instances and their signed stream positions. */
final class AuthenticatedRemoteVisualRegistry {
    private final Map<Key, Entry> entries = new HashMap<>();

    Long put(String streamId, UUID visualInstanceId, long signedStartCounter, long commandSeq) {
        if (streamId == null || streamId.isBlank() || visualInstanceId == null
                || signedStartCounter <= 0 || commandSeq <= 0) {
            throw new IllegalArgumentException("Invalid authenticated visual identity");
        }
        Entry previous = entries.put(new Key(streamId, visualInstanceId),
                new Entry(signedStartCounter, commandSeq));
        return previous == null ? null : previous.commandSeq();
    }

    Long remove(String streamId, UUID visualInstanceId) {
        Entry entry = entries.remove(new Key(streamId, visualInstanceId));
        return entry == null ? null : entry.commandSeq();
    }

    Long get(String streamId, UUID visualInstanceId) {
        Entry entry = entries.get(new Key(streamId, visualInstanceId));
        return entry == null ? null : entry.commandSeq();
    }

    List<Long> clearThrough(String streamId, long clearThroughCounter) {
        return clearThrough(streamId, clearThroughCounter, null);
    }

    List<Long> clearThrough(String streamId, long clearThroughCounter, UUID excludedVisualInstanceId) {
        List<Long> removed = new ArrayList<>();
        entries.entrySet().removeIf(entry -> {
            if (entry.getKey().streamId().equals(streamId)
                    && !entry.getKey().visualInstanceId().equals(excludedVisualInstanceId)
                    && entry.getValue().signedStartCounter() <= clearThroughCounter) {
                removed.add(entry.getValue().commandSeq());
                return true;
            }
            return false;
        });
        return removed;
    }

    List<Long> clearAll() {
        List<Long> removed = entries.values().stream().map(Entry::commandSeq).toList();
        entries.clear();
        return removed;
    }

    boolean contains(String streamId, UUID visualInstanceId) {
        return entries.containsKey(new Key(streamId, visualInstanceId));
    }

    void prune(Set<Long> activeCommandSequences) {
        Set<Long> retained = new HashSet<>(Objects.requireNonNull(activeCommandSequences, "activeCommandSequences"));
        entries.entrySet().removeIf(entry -> !retained.contains(entry.getValue().commandSeq()));
    }

    private record Key(String streamId, UUID visualInstanceId) {
        private Key {
            Objects.requireNonNull(streamId, "streamId");
            Objects.requireNonNull(visualInstanceId, "visualInstanceId");
        }
    }

    private record Entry(long signedStartCounter, long commandSeq) {
    }
}
