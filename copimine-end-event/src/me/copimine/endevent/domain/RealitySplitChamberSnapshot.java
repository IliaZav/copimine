package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.RealitySplitChamberController;

/** Strict codec for Wave 7 UUID-to-room assignments and passage progress. */
public final class RealitySplitChamberSnapshot {
    private static final String PREFIX = "reality-split.";
    private static final String GENERATION = PREFIX + "generation";
    private static final String CHAMBER_COUNT = PREFIX + "chamber-count";
    private static final String PLAYER_PREFIX = PREFIX + "player.";
    private static final String COMPLETED = PREFIX + "completed";
    private static final String OPEN = PREFIX + "open";

    private RealitySplitChamberSnapshot() {
    }

    public static Map<String, String> encode(long generation,
                                              ChamberIsolationPolicy.Assignment assignment,
                                              Set<Integer> completedChambers,
                                              Set<RealitySplitChamberController.Passage> openPassages) {
        return encode(generation, assignment, completedChambers, openPassages, false);
    }

    /**
     * Encode the disposable visual/runtime probe.  Its solo variant still
     * owns two physical chambers, but only one operator UUID is available;
     * that state is valid for a local probe and must not be accepted by the
     * official event codec.
     */
    public static Map<String, String> encodeDisposable(long generation,
                                                        ChamberIsolationPolicy.Assignment assignment,
                                                        Set<Integer> completedChambers,
                                                        Set<RealitySplitChamberController.Passage> openPassages) {
        return encode(generation, assignment, completedChambers, openPassages, true);
    }

    private static Map<String, String> encode(long generation,
                                              ChamberIsolationPolicy.Assignment assignment,
                                              Set<Integer> completedChambers,
                                              Set<RealitySplitChamberController.Passage> openPassages,
                                              boolean allowSinglePlayer) {
        if (generation <= 0L || assignment == null || assignment.chamberCount() < 2
                || assignment.chamberCount() > ChamberIsolationPolicy.MAX_CHAMBERS
                || (!allowSinglePlayer && assignment.chamberByPlayer().size() < 2)
                || (allowSinglePlayer && assignment.chamberByPlayer().isEmpty())) {
            throw new IllegalArgumentException("Wave 7 assignment is not encodable");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(GENERATION, Long.toString(generation));
        encoded.put(CHAMBER_COUNT, Integer.toString(assignment.chamberCount()));
        assignment.chamberByPlayer().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> encoded.put(PLAYER_PREFIX + entry.getKey(),
                        Integer.toString(entry.getValue())));
        encoded.put(COMPLETED, joinIntegers(completedChambers));
        encoded.put(OPEN, joinPassages(openPassages));
        return Map.copyOf(encoded);
    }

    public static Data decode(Map<String, String> encoded, long expectedGeneration) {
        return decode(encoded, expectedGeneration, false);
    }

    /** Decode a disposable probe snapshot, including its one-player variant. */
    public static Data decodeDisposable(Map<String, String> encoded, long expectedGeneration) {
        return decode(encoded, expectedGeneration, true);
    }

    private static Data decode(Map<String, String> encoded, long expectedGeneration,
                               boolean allowSinglePlayer) {
        if (encoded == null || encoded.isEmpty()
                || encoded.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return new Data(0L, null, Set.of(), Set.of());
        }
        long generation = parseLong(required(encoded, GENERATION), GENERATION);
        if (expectedGeneration <= 0L || generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 7 snapshot generation does not match event generation");
        }
        int chamberCount = parseInt(required(encoded, CHAMBER_COUNT), CHAMBER_COUNT);
        Map<UUID, Integer> players = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            if (!entry.getKey().startsWith(PLAYER_PREFIX)) {
                continue;
            }
            String rawUuid = entry.getKey().substring(PLAYER_PREFIX.length());
            UUID player = parseUuid(rawUuid, entry.getKey());
            if (players.put(player, parseInt(entry.getValue(), entry.getKey())) != null) {
                throw new IllegalArgumentException("Wave 7 snapshot duplicates a player");
            }
        }
        ChamberIsolationPolicy.Assignment assignment =
                new ChamberIsolationPolicy.Assignment(chamberCount, players);
        if ((!allowSinglePlayer && assignment.chamberByPlayer().size() < 2)
                || (allowSinglePlayer && assignment.chamberByPlayer().isEmpty())) {
            throw new IllegalArgumentException("Wave 7 snapshot has fewer than two players");
        }
        Set<Integer> completed = parseCompleted(required(encoded, COMPLETED), chamberCount);
        Set<RealitySplitChamberController.Passage> open = parsePassages(
                required(encoded, OPEN), chamberCount);
        return new Data(generation, assignment, completed, open);
    }

    private static String joinIntegers(Set<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static String joinPassages(Set<RealitySplitChamberController.Passage> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .sorted(Comparator.comparingInt(RealitySplitChamberController.Passage::firstChamber)
                        .thenComparingInt(RealitySplitChamberController.Passage::secondChamber))
                .map(value -> value.firstChamber() + ":" + value.secondChamber())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Set<Integer> parseCompleted(String raw, int chamberCount) {
        Set<Integer> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",", -1)) {
            int chamber = parseInt(item, COMPLETED);
            if (chamber < 0 || chamber >= chamberCount || !result.add(chamber)) {
                throw new IllegalArgumentException("Wave 7 snapshot has invalid completed chamber");
            }
        }
        return Set.copyOf(result);
    }

    private static Set<RealitySplitChamberController.Passage> parsePassages(
            String raw, int chamberCount) {
        Set<RealitySplitChamberController.Passage> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",", -1)) {
            String[] parts = item.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Wave 7 snapshot has invalid passage");
            }
            int first = parseInt(parts[0], OPEN);
            int second = parseInt(parts[1], OPEN);
            if (first < 0 || second < 0 || first >= chamberCount || second >= chamberCount
                    || first == second
                    || !result.add(new RealitySplitChamberController.Passage(first, second))) {
                throw new IllegalArgumentException("Wave 7 snapshot has invalid passage");
            }
        }
        return Set.copyOf(result);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Wave 7 snapshot is missing " + key);
        }
        return value;
    }

    private static UUID parseUuid(String raw, String key) {
        try {
            return UUID.fromString(raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 snapshot has invalid UUID in " + key, error);
        }
    }

    private static int parseInt(String raw, String key) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 snapshot has invalid integer in " + key, error);
        }
    }

    private static long parseLong(String raw, String key) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 snapshot has invalid long in " + key, error);
        }
    }

    public record Data(long generation,
                       ChamberIsolationPolicy.Assignment assignment,
                       Set<Integer> completedChambers,
                       Set<RealitySplitChamberController.Passage> openPassages) {
        public Data {
            completedChambers = Set.copyOf(completedChambers == null ? Set.of() : completedChambers);
            openPassages = Set.copyOf(openPassages == null ? Set.of() : openPassages);
        }
    }
}
