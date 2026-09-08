package me.copimine.endevent.runtime;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;

/**
 * Small generation-scoped adapter for Wave 6 assignments. Bukkit entity and
 * location mutation remains in the plugin; this controller owns only the
 * assignment snapshot and the passage gate.
 */
public final class WaveSixChamberController {
    private long generation = Long.MIN_VALUE;
    private ChamberIsolationPolicy.Assignment assignment =
            new ChamberIsolationPolicy.Assignment(0, java.util.Map.of());
    private final Map<UUID, Integer> chamberByEntity = new LinkedHashMap<>();
    private boolean passageOpen;

    public void begin(long generation, List<UUID> players) {
        this.generation = generation;
        this.assignment = ChamberIsolationPolicy.assign(players);
        this.chamberByEntity.clear();
        this.passageOpen = false;
    }

    public boolean owns(long generation) {
        return this.generation == generation && this.generation != Long.MIN_VALUE;
    }

    public ChamberIsolationPolicy.Assignment assignment() {
        return assignment;
    }

    /** Assign an event mob to one room for the lifetime of this generation. */
    public boolean assignEntity(long generation, UUID entity, int chamber) {
        if (!owns(generation) || entity == null
                || chamber < 0 || chamber >= assignment.chamberCount()) {
            return false;
        }
        Integer previous = chamberByEntity.putIfAbsent(entity, chamber);
        return previous == null || previous == chamber;
    }

    public int chamberOfEntity(long generation, UUID entity) {
        if (!owns(generation) || entity == null) {
            return -1;
        }
        return chamberByEntity.getOrDefault(entity, -1);
    }

    public boolean allowsMobTarget(long generation, UUID entity, UUID target) {
        return ChamberIsolationPolicy.allowsMobTarget(
                chamberOfEntity(generation, entity), target, assignment, passageOpen);
    }

    public int assignedEntityCount() {
        return chamberByEntity.size();
    }

    public void openPassage(long generation) {
        if (owns(generation)) passageOpen = true;
    }

    public boolean passageOpen() {
        return passageOpen;
    }

    public boolean allowsInteraction(long generation, UUID source, UUID target) {
        return owns(generation) && ChamberIsolationPolicy.allowsInteraction(
                source, target, assignment, passageOpen);
    }

    public void clear() {
        generation = Long.MIN_VALUE;
        assignment = new ChamberIsolationPolicy.Assignment(0, java.util.Map.of());
        chamberByEntity.clear();
        passageOpen = false;
    }
}
