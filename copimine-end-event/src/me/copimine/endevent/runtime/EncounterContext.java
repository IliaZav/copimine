package me.copimine.endevent.runtime;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EndRiftObjective;

/** Immutable, server-independent facts shared by one End Rift encounter. */
public final class EncounterContext {
    private final String eventId;
    private final long generation;
    private final String worldName;
    private final int coreX;
    private final int coreY;
    private final int coreZ;
    private final ArenaBounds arena;
    private final Set<UUID> roster;
    private final Set<UUID> livingParticipants;
    private final EndRiftObjective.Objective objective;

    public EncounterContext(String eventId, long generation, String worldName,
                            int coreX, int coreY, int coreZ, ArenaBounds arena,
                            Set<UUID> roster, Set<UUID> livingParticipants,
                            EndRiftObjective.Objective objective) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("event id is required");
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("world is required");
        if (arena == null || !arena.contains(coreX, coreY, coreZ)) {
            throw new IllegalArgumentException("core must be inside the arena bounds");
        }
        this.eventId = eventId.trim();
        this.generation = generation;
        this.worldName = worldName.trim();
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.arena = arena;
        this.roster = immutablePlayers(roster, "roster");
        this.livingParticipants = immutablePlayers(livingParticipants, "living participants");
        if (!this.roster.containsAll(this.livingParticipants)) {
            throw new IllegalArgumentException("living participants must belong to the roster");
        }
        this.objective = objective;
    }

    public static EncounterContext forWave(String eventId, long generation, String worldName,
                                           int coreX, int coreY, int coreZ, ArenaBounds arena,
                                           Set<UUID> roster, Set<UUID> livingParticipants,
                                           int wave) {
        return new EncounterContext(eventId, generation, worldName, coreX, coreY, coreZ,
                arena, roster, livingParticipants, EndRiftObjective.objective(wave));
    }

    public String eventId() { return eventId; }
    public long generation() { return generation; }
    public String worldName() { return worldName; }
    public int coreX() { return coreX; }
    public int coreY() { return coreY; }
    public int coreZ() { return coreZ; }
    public ArenaBounds arena() { return arena; }
    public Set<UUID> roster() { return roster; }
    public Set<UUID> livingParticipants() { return livingParticipants; }
    public int livingPlayerCount() { return livingParticipants.size(); }
    public EndRiftObjective.Objective objective() { return objective; }

    public boolean isLivingParticipant(UUID player) {
        return player != null && livingParticipants.contains(player);
    }

    public boolean owns(String expectedEventId, long expectedGeneration) {
        return expectedEventId != null && eventId.equals(expectedEventId)
                && generation == expectedGeneration;
    }

    public boolean withinArena(int x, int y, int z) {
        return arena.contains(x, y, z);
    }

    public EncounterContext withLivingParticipants(Set<UUID> nextLiving) {
        return new EncounterContext(eventId, generation, worldName, coreX, coreY, coreZ,
                arena, roster, nextLiving, objective);
    }

    public EncounterContext withObjective(EndRiftObjective.Objective nextObjective) {
        return new EncounterContext(eventId, generation, worldName, coreX, coreY, coreZ,
                arena, roster, livingParticipants, nextObjective);
    }

    private static Set<UUID> immutablePlayers(Set<UUID> players, String label) {
        if (players == null) return Set.of();
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (UUID player : players) {
            if (player == null) throw new IllegalArgumentException(label + " contains null");
            result.add(player);
        }
        if (result.size() > 20) throw new IllegalArgumentException(label + " exceeds 20 players");
        return Set.copyOf(result);
    }

    public record ArenaBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public ArenaBounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("arena bounds are inverted");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }
}
