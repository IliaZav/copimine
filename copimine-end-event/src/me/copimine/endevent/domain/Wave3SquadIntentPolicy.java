package me.copimine.endevent.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure assignment policy for Wave 3 gate-focused squads. */
public final class Wave3SquadIntentPolicy {
    private static final int MAX_GATE_SQUAD_SHARE_DIVISOR = 3;

    private Wave3SquadIntentPolicy() {
    }

    public enum GateState {
        ACTIVE,
        CLOSED,
        CAPTURED
    }

    /** The state snapshot for one zero-based portal/gate index. */
    public record GateTarget(int index, GateState state) { }

    /**
     * Assign distinct active gate indexes to a bounded subset of stable squad IDs.
     * Squads absent from the returned map retain their ordinary player-pressure intent.
     * Existing assignments are retained while both the squad and its gate remain
     * eligible, so an unchanged tick does not rotate the objective squads.
     *
     * @param squadIdentities stable, non-negative WaveCombatCoordinator squad identities
     * @param gates current gate states; only ACTIVE gates are eligible
     * @param maximumGateSquads caller-selected upper bound for gate-focused squads; must be positive
     * @param currentAssignments prior squad-to-gate assignments, if available
     * @return immutable gate-index-by-squad map in deterministic identity order
     */
    public static Map<Long, Integer> gateAssignments(List<Long> squadIdentities,
                                                     List<GateTarget> gates,
                                                     int maximumGateSquads,
                                                     Map<Long, Integer> currentAssignments) {
        if (maximumGateSquads <= 0)
            throw new IllegalArgumentException("maximum gate squad count must be positive");

        List<Long> squads = squadIdentities == null ? List.of() : squadIdentities.stream()
                .filter(identity -> identity != null && identity >= 0L)
                .distinct().sorted().toList();
        List<Integer> activeGateIndexes = activeGateIndexes(gates);
        if (squads.isEmpty() || activeGateIndexes.isEmpty()) return Map.of();

        int shareLimit = squads.size() == 1 ? 1
                : Math.max(1, squads.size() / MAX_GATE_SQUAD_SHARE_DIVISOR);
        int assignmentCount = Math.min(Math.min(squads.size(), activeGateIndexes.size()),
                Math.min(maximumGateSquads, shareLimit));

        Map<Long, Integer> prior = currentAssignments == null ? Map.of() : currentAssignments;
        Map<Long, Integer> gateBySquad = new LinkedHashMap<>();
        Set<Integer> usedGates = new HashSet<>();
        for (Long squad : squads) {
            Integer previousGate = prior.get(squad);
            if (gateBySquad.size() >= assignmentCount) break;
            if (previousGate != null && activeGateIndexes.contains(previousGate)
                    && usedGates.add(previousGate)) {
                gateBySquad.put(squad, previousGate);
            }
        }

        int nextGate = 0;
        for (Long squad : squads) {
            if (gateBySquad.size() >= assignmentCount) break;
            if (gateBySquad.containsKey(squad)) continue;
            while (nextGate < activeGateIndexes.size()
                    && usedGates.contains(activeGateIndexes.get(nextGate))) nextGate++;
            if (nextGate >= activeGateIndexes.size()) break;
            int gate = activeGateIndexes.get(nextGate++);
            gateBySquad.put(squad, gate);
            usedGates.add(gate);
        }
        return Collections.unmodifiableMap(gateBySquad);
    }

    /** Convenience overload for the first tick, when no prior assignments exist. */
    public static Map<Long, Integer> gateAssignments(List<Long> squadIdentities,
                                                     List<GateTarget> gates,
                                                     int maximumGateSquads) {
        return gateAssignments(squadIdentities, gates, maximumGateSquads, Map.of());
    }

    private static List<Integer> activeGateIndexes(List<GateTarget> gates) {
        if (gates == null || gates.isEmpty()) return List.of();
        Map<Integer, GateState> states = new HashMap<>();
        Set<Integer> conflictingIndexes = new HashSet<>();
        for (GateTarget gate : gates) {
            if (gate == null || gate.index() < 0) continue;
            if (gate.state() == null) {
                conflictingIndexes.add(gate.index());
                continue;
            }
            GateState previous = states.putIfAbsent(gate.index(), gate.state());
            if (previous != null && previous != gate.state())
                conflictingIndexes.add(gate.index());
        }
        return states.entrySet().stream()
                .filter(entry -> entry.getValue() == GateState.ACTIVE
                        && !conflictingIndexes.contains(entry.getKey()))
                .map(Map.Entry::getKey).sorted().toList();
    }
}
