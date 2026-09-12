package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;

/**
 * Generation-scoped Wave 7 room graph. Connectivity is stored per boundary,
 * so opening one passage cannot silently make every room's mobs, projectiles
 * or AoE global.
 */
public final class RealitySplitChamberController {
    private long generation = Long.MIN_VALUE;
    private ChamberIsolationPolicy.Assignment assignment =
            new ChamberIsolationPolicy.Assignment(0, Map.of());
    private final Map<UUID, Integer> chamberByEntity = new LinkedHashMap<>();
    private final Set<Boundary> openBoundaries = new LinkedHashSet<>();
    private final Set<Integer> completedChambers = new LinkedHashSet<>();

    public void begin(long generation, List<UUID> players) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.assignment = ChamberIsolationPolicy.assign(players);
        this.chamberByEntity.clear();
        this.openBoundaries.clear();
        this.completedChambers.clear();
    }

    public boolean owns(long generation) {
        return this.generation == generation && this.generation > 0L;
    }

    public ChamberIsolationPolicy.Assignment assignment() { return assignment; }

    public boolean assignEntity(long generation, UUID entity, int chamber) {
        if (!owns(generation) || entity == null || chamber < 0
                || chamber >= assignment.chamberCount()) return false;
        Integer previous = chamberByEntity.putIfAbsent(entity, chamber);
        return previous == null || previous == chamber;
    }

    public int chamberOfEntity(long generation, UUID entity) {
        if (!owns(generation) || entity == null) return -1;
        return chamberByEntity.getOrDefault(entity, -1);
    }

    /**
     * Record completion only for the current generation.  Completion is a
     * room-local fact; it never opens another room implicitly.
     */
    public boolean markChamberComplete(long generation, int chamber) {
        if (!owns(generation) || !validChamber(chamber)) {
            return false;
        }
        return completedChambers.add(chamber);
    }

    public boolean chamberComplete(long generation, int chamber) {
        return owns(generation) && validChamber(chamber) && completedChambers.contains(chamber);
    }

    public boolean allChambersComplete(long generation) {
        return owns(generation) && assignment.chamberCount() > 0
                && completedChambers.size() == assignment.chamberCount();
    }

    /** Open only an edge whose two endpoints have already completed. */
    public boolean openCompletedPassage(long generation, int firstChamber, int secondChamber) {
        if (!owns(generation) || !chamberComplete(generation, firstChamber)
                || !chamberComplete(generation, secondChamber)) {
            return false;
        }
        int before = openBoundaries.size();
        openBoundary(generation, firstChamber, secondChamber);
        return openBoundaries.size() > before;
    }

    public boolean allowsMobTarget(long generation, UUID entity, UUID target) {
        if (!owns(generation) || target == null) return false;
        int sourceChamber = chamberOfEntity(generation, entity);
        int targetChamber = assignment.chamberByPlayer().getOrDefault(target, -1);
        return connected(sourceChamber, targetChamber);
    }

    public boolean allowsInteraction(long generation, UUID source, UUID target) {
        if (!owns(generation) || source == null || target == null) return false;
        int sourceChamber = assignment.chamberByPlayer().getOrDefault(source, -1);
        int targetChamber = assignment.chamberByPlayer().getOrDefault(target, -1);
        return connected(sourceChamber, targetChamber);
    }

    public void openBoundary(long generation, int firstChamber, int secondChamber) {
        if (!owns(generation) || !validChamber(firstChamber) || !validChamber(secondChamber)
                || firstChamber == secondChamber) return;
        openBoundaries.add(new Boundary(firstChamber, secondChamber));
    }

    /** Open every boundary only for the explicit post-objective merge step. */
    public void openAllBoundariesAfterObjective(long generation) {
        if (!owns(generation)) return;
        for (int first = 0; first < assignment.chamberCount(); first++) {
            for (int second = first + 1; second < assignment.chamberCount(); second++) {
                openBoundaries.add(new Boundary(first, second));
            }
        }
    }

    public boolean boundaryOpen(int firstChamber, int secondChamber) {
        return openBoundaries.contains(new Boundary(firstChamber, secondChamber));
    }

    public int assignedEntityCount() { return chamberByEntity.size(); }

    public void clear() {
        generation = Long.MIN_VALUE;
        assignment = new ChamberIsolationPolicy.Assignment(0, Map.of());
        chamberByEntity.clear();
        openBoundaries.clear();
        completedChambers.clear();
    }

    private boolean validChamber(int chamber) {
        return chamber >= 0 && chamber < assignment.chamberCount();
    }

    private boolean connected(int source, int target) {
        if (!validChamber(source) || !validChamber(target)) return false;
        if (source == target) return true;
        // The current merge animation opens adjacent boundaries. A short BFS
        // lets a mob use an already connected chain without global leakage.
        Set<Integer> visited = new LinkedHashSet<>();
        Set<Integer> frontier = new LinkedHashSet<>();
        frontier.add(source);
        while (!frontier.isEmpty()) {
            int current = frontier.iterator().next();
            frontier.remove(current);
            if (!visited.add(current)) continue;
            if (current == target) return true;
            for (int next = 0; next < assignment.chamberCount(); next++) {
                if (!visited.contains(next) && boundaryOpen(current, next)) frontier.add(next);
            }
        }
        return false;
    }

    private record Boundary(int first, int second) {
        Boundary {
            if (first > second) {
                int swap = first;
                first = second;
                second = swap;
            }
        }
    }
}
