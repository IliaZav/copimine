package me.copimine.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Holds one immutable phantom layout for the lifetime of a local Wave 2 mark. */
final class WaveTwoPhantomSession {
    private MarkKey activeMark;
    private List<WaveTwoPhantomLayout.Position> positions = List.of();

    List<WaveTwoPhantomLayout.Position> positionsFor(
            MarkKey mark, Supplier<List<WaveTwoPhantomLayout.Position>> layoutFactory) {
        if (mark == null || !mark.valid() || layoutFactory == null) {
            clear();
            return List.of();
        }
        if (!mark.equals(activeMark)) {
            activeMark = mark;
            List<WaveTwoPhantomLayout.Position> created = layoutFactory.get();
            LinkedHashSet<WaveTwoPhantomLayout.Position> bounded = new LinkedHashSet<>();
            if (created != null) {
                for (WaveTwoPhantomLayout.Position position : created) {
                    if (!WaveTwoPhantomLayout.finite(position)) continue;
                    bounded.add(position);
                    if (bounded.size() >= WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT) break;
                }
            }
            positions = bounded.isEmpty() ? List.of() : List.copyOf(bounded);
        }
        return positions;
    }

    boolean hasActiveMark() {
        return activeMark != null;
    }

    void clear() {
        activeMark = null;
        positions = List.of();
    }

    record MarkKey(String eventId, long generation, UUID markedPlayer, long startedAtMillis) {
        MarkKey {
            eventId = eventId == null ? "" : eventId.trim();
        }

        boolean valid() {
            return !eventId.isBlank() && generation > 0L && markedPlayer != null && startedAtMillis >= 0L;
        }

        long stableSeed() {
            long seed = WaveTwoPhantomLayout.mixSeed(generation ^ startedAtMillis);
            seed = WaveTwoPhantomLayout.mixSeed(seed ^ markedPlayer.getMostSignificantBits());
            seed = WaveTwoPhantomLayout.mixSeed(seed ^ Long.rotateLeft(markedPlayer.getLeastSignificantBits(), 17));
            for (int index = 0; index < eventId.length(); index++) {
                seed = WaveTwoPhantomLayout.mixSeed(seed ^ eventId.charAt(index));
            }
            return seed;
        }
    }
}
