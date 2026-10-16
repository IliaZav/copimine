package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Durable sandbox policy; never substitutes the official reward roster. */
public final class SandboxWaveSessionSnapshot {
    private SandboxWaveSessionSnapshot() { }

    public static Map<String, String> encode(long generation, int wave,
                                             Set<UUID> roster, boolean combatMode) {
        validate(generation, wave, roster, combatMode);
        return Map.of("test-wave-generation", Long.toString(generation),
                "test-wave", Integer.toString(wave),
                "test-wave-roster", roster.stream().map(UUID::toString).sorted()
                        .collect(Collectors.joining(",")),
                "test-wave-mode", combatMode ? "combat" : "capture");
    }

    public static Data decode(Map<String, String> saved, long expectedGeneration) {
        if (saved == null || !saved.containsKey("test-wave")) {
            return new Data(0L, 0, Set.of(), false);
        }
        try {
            long generation = Long.parseLong(saved.get("test-wave-generation"));
            int wave = Integer.parseInt(saved.get("test-wave"));
            if (generation != expectedGeneration) throw new IllegalArgumentException("stale sandbox session");
            String mode = saved.get("test-wave-mode");
            if (!"capture".equals(mode) && !"combat".equals(mode)) {
                throw new IllegalArgumentException("unknown sandbox capture mode");
            }
            String rawRoster = saved.get("test-wave-roster");
            if (rawRoster == null || rawRoster.isBlank()) throw new IllegalArgumentException("missing sandbox roster");
            Set<UUID> roster = new LinkedHashSet<>();
            for (String rawPlayer : rawRoster.split(",", -1)) {
                if (!roster.add(UUID.fromString(rawPlayer))) {
                    throw new IllegalArgumentException("duplicate sandbox player");
                }
            }
            boolean combatMode = "combat".equals(mode);
            validate(generation, wave, roster, combatMode);
            return new Data(generation, wave, Set.copyOf(roster), combatMode);
        } catch (NullPointerException | NumberFormatException error) {
            throw new IllegalArgumentException("invalid sandbox session metadata", error);
        }
    }

    private static void validate(long generation, int wave, Set<UUID> roster, boolean combatMode) {
        if (generation <= 0L || (wave != 6 && wave != 7)
                || roster == null || roster.isEmpty() || roster.stream().anyMatch(java.util.Objects::isNull)
                || combatMode && wave != 6) {
            throw new IllegalArgumentException("invalid persistent sandbox context");
        }
    }

    public record Data(long generation, int wave, Set<UUID> roster, boolean combatMode) { }
}
