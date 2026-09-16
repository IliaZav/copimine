package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;

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
    private final Set<Passage> openBoundaries = new LinkedHashSet<>();
    private final Set<Integer> completedChambers = new LinkedHashSet<>();

    public void begin(long generation, List<UUID> players) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.assignment = ChamberIsolationPolicy.assign(players);
        this.chamberByEntity.clear();
        this.openBoundaries.clear();
        this.completedChambers.clear();
    }

    /**
     * Build the disposable developer probe with two physical rooms even when
     * only one operator is online.  The official event continues to use
     * {@link #begin(long, List)} and therefore still requires its normal
     * multi-player roster; this method exists only for local visual/runtime
     * diagnostics and is never persisted as an official assignment.
     */
    public void beginDevSolo(long generation, List<UUID> players) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        List<UUID> unique = new java.util.ArrayList<>(
                new java.util.LinkedHashSet<>(players == null ? List.of() : players));
        unique.removeIf(value -> value == null);
        unique.sort(java.util.Comparator.comparing(UUID::toString));
        int chamberCount = Math.max(2, Math.min(ChamberIsolationPolicy.MAX_CHAMBERS, unique.size()));
        Map<UUID, Integer> mapping = new LinkedHashMap<>();
        for (int index = 0; index < unique.size(); index++) {
            mapping.put(unique.get(index), index % chamberCount);
        }
        this.generation = generation;
        this.assignment = new ChamberIsolationPolicy.Assignment(chamberCount, mapping);
        this.chamberByEntity.clear();
        this.openBoundaries.clear();
        this.completedChambers.clear();
    }

    public boolean owns(long generation) {
        return this.generation == generation && this.generation > 0L;
    }

    public ChamberIsolationPolicy.Assignment assignment() { return assignment; }

    /** Immutable completion state used by schema-4 Wave 7 checkpoints. */
    public Set<Integer> completedChambers() {
        return Set.copyOf(completedChambers);
    }

    /** Immutable passage state used by schema-4 Wave 7 checkpoints. */
    public Set<Passage> openPassages() {
        return Set.copyOf(openBoundaries);
    }

    /**
     * Restore the exact generation-scoped assignment after a process restart.
     * Entity indexes are intentionally not restored here; the Bukkit adapter
     * reattaches surviving PDC-owned entities separately.
     */
    public void restore(long generation,
                        ChamberIsolationPolicy.Assignment restoredAssignment,
                        Set<Integer> restoredCompletedChambers,
                        Set<Passage> restoredOpenPassages) {
        restore(generation, restoredAssignment, restoredCompletedChambers, restoredOpenPassages, false);
    }

    /** Restore a disposable local probe, which may have one operator UUID. */
    public void restoreDisposable(long generation,
                                  ChamberIsolationPolicy.Assignment restoredAssignment,
                                  Set<Integer> restoredCompletedChambers,
                                  Set<Passage> restoredOpenPassages) {
        restore(generation, restoredAssignment, restoredCompletedChambers, restoredOpenPassages, true);
    }

    private void restore(long generation,
                         ChamberIsolationPolicy.Assignment restoredAssignment,
                         Set<Integer> restoredCompletedChambers,
                         Set<Passage> restoredOpenPassages,
                         boolean allowSinglePlayer) {
        if (generation <= 0L || restoredAssignment == null
                || restoredAssignment.chamberCount() < 2
                || restoredAssignment.chamberCount() > ChamberIsolationPolicy.MAX_CHAMBERS
                || (!allowSinglePlayer && restoredAssignment.chamberByPlayer().size() < 2)
                || (allowSinglePlayer && restoredAssignment.chamberByPlayer().isEmpty())) {
            throw new IllegalArgumentException("Wave 7 assignment is not restorable");
        }
        this.generation = generation;
        this.assignment = restoredAssignment;
        this.chamberByEntity.clear();
        this.completedChambers.clear();
        this.openBoundaries.clear();
        if (restoredCompletedChambers != null) {
            for (Integer chamber : restoredCompletedChambers) {
                if (!validChamber(chamber == null ? -1 : chamber)) {
                    throw new IllegalArgumentException("Wave 7 completion has invalid chamber");
                }
                this.completedChambers.add(chamber);
            }
        }
        if (restoredOpenPassages != null) {
            for (Passage passage : restoredOpenPassages) {
                if (passage == null || !validChamber(passage.firstChamber())
                        || !validChamber(passage.secondChamber())
                        || passage.firstChamber() == passage.secondChamber()
                        || RealitySplitBarrierPolicy.boundaryForPair(
                                passage.firstChamber(), passage.secondChamber(),
                                restoredAssignment.chamberCount()) < 0) {
                    throw new IllegalArgumentException("Wave 7 passage is invalid");
                }
                this.openBoundaries.add(new Passage(passage.firstChamber(), passage.secondChamber()));
            }
        }
    }

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
     * Return how many event entities have actually been assigned to a room.
     * This is deliberately historical for the current generation: when a mob
     * dies its UUID remains evidence that the room had a real spawn, while a
     * room whose floor rejected every spawn stays distinguishable from a
     * cleared room.
     */
    public int assignedEntityCountForChamber(int chamber) {
        if (!validChamber(chamber)) {
            return 0;
        }
        int count = 0;
        for (Integer assigned : chamberByEntity.values()) {
            if (assigned != null && assigned == chamber) {
                count++;
            }
        }
        return count;
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
                || !chamberComplete(generation, secondChamber)
                || RealitySplitBarrierPolicy.boundaryForPair(
                        firstChamber, secondChamber, assignment.chamberCount()) < 0) {
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
                || firstChamber == secondChamber
                || RealitySplitBarrierPolicy.boundaryForPair(
                        firstChamber, secondChamber, assignment.chamberCount()) < 0) return;
        openBoundaries.add(new Passage(firstChamber, secondChamber));
    }

    /** Open every boundary only for the explicit post-objective merge step. */
    public void openAllBoundariesAfterObjective(long generation) {
        if (!owns(generation)) return;
        for (int first = 0; first < assignment.chamberCount(); first++) {
            for (int second = first + 1; second < assignment.chamberCount(); second++) {
                if (RealitySplitBarrierPolicy.boundaryForPair(
                        first, second, assignment.chamberCount()) >= 0) {
                    openBoundaries.add(new Passage(first, second));
                }
            }
        }
    }

    public boolean boundaryOpen(int firstChamber, int secondChamber) {
        return openBoundaries.contains(new Passage(firstChamber, secondChamber));
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

    public record Passage(int firstChamber, int secondChamber) {
        public Passage {
            if (firstChamber > secondChamber) {
                int swap = firstChamber;
                firstChamber = secondChamber;
                secondChamber = swap;
            }
        }
    }
}
