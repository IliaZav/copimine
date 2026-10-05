package me.copimine.endevent.domain;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.RealitySplitChamberController.Passage;

/** Named Wave 7 permissions; geometry, LOS and attribution remain adapter checks. */
public final class Wave7AdmissionPolicy {
    public enum Trial { WARDEN, ECHO, MARKSMAN, ARCHMAGE }
    public enum Entry { INITIAL, OWNER_RETURN, WALK, HELPER_INTERACTION }

    private Wave7AdmissionPolicy() { }

    /** Explicit identities, never enum ordinals. Solo named probes may supply another map. */
    public static Map<Integer, Trial> defaultLayout(int count) {
        if (count < 1 || count > 4) throw new IllegalArgumentException("invalid named room count");
        Map<Integer, Trial> result = new LinkedHashMap<>();
        result.put(0, Trial.WARDEN);
        if (count >= 2) result.put(1, Trial.ECHO);
        if (count >= 3) result.put(2, Trial.MARKSMAN);
        if (count >= 4) result.put(3, Trial.ARCHMAGE);
        return Map.copyOf(result);
    }

    public static boolean mayEnter(State state, long generation, UUID player, int destination, Entry entry) {
        if (!owns(state, generation) || entry == null || !registered(state, player)
                || !state.trials().containsKey(destination)) return false;
        int claim = state.assignment().chamberByPlayer().get(player);
        if (state.trials().get(destination) == Trial.ECHO) {
            return claim == destination && !state.completedEchoOwners().contains(player)
                    && (entry == Entry.INITIAL || entry == Entry.OWNER_RETURN
                    || entry == Entry.WALK && physical(state, player) == destination);
        }
        if (claim == destination) return true;
        boolean helper = state.helpers().getOrDefault(destination, Set.of()).contains(player);
        if (entry == Entry.HELPER_INTERACTION) return helper;
        int from = physical(state, player);
        return entry == Entry.WALK && (helper || state.completedRooms().contains(destination))
                && completedOwnClaim(state, player) && connectedOrdinary(state, from, destination);
    }

    /** Entry interaction is explicitly authorized; a portal does not create an actor path. */
    public static boolean mayAdmitHelper(State state, long generation, UUID player, int trialRoom) {
        return owns(state, generation) && registered(state, player)
                && state.trials().containsKey(trialRoom) && state.trials().get(trialRoom) != Trial.ECHO
                && !state.completedRooms().contains(trialRoom)
                && state.assignment().chamberByPlayer().get(player) != trialRoom
                && completedOwnClaim(state, player);
    }

    public static boolean mayExitEcho(State state, long generation, UUID player) {
        if (!owns(state, generation) || !registered(state, player)) return false;
        int claim = state.assignment().chamberByPlayer().get(player);
        return state.trials().get(claim) == Trial.ECHO && physical(state, player) == claim
                && state.completedEchoOwners().contains(player);
    }

    public static boolean mayMoveActor(State state, long generation, int trialRoom, int destination) {
        if (!owns(state, generation) || !state.trials().containsKey(trialRoom)
                || !state.trials().containsKey(destination)) return false;
        if (trialRoom == destination) return true;
        return state.trials().get(trialRoom) == Trial.MARKSMAN
                && state.completedRooms().contains(destination)
                && connectedOrdinary(state, trialRoom, destination);
    }

    /** Call at selection and again at impact with current physical membership. */
    public static boolean mayTarget(State state, long generation, int trialRoom, int actorPhysicalRoom,
                                    UUID echoOwner, UUID target) {
        if (!owns(state, generation) || !registered(state, target)
                || !mayMoveActor(state, generation, trialRoom, actorPhysicalRoom)
                || state.completedRooms().contains(trialRoom)) return false;
        int targetRoom = physical(state, target);
        if (state.trials().get(trialRoom) == Trial.ECHO) {
            return echoOwner != null && echoOwner.equals(target) && targetRoom == trialRoom
                    && state.assignment().chamberByPlayer().get(target) == trialRoom
                    && !state.completedEchoOwners().contains(target);
        }
        if (echoOwner != null || !mayMoveActor(state, generation, trialRoom, targetRoom)) return false;
        return state.assignment().chamberByPlayer().get(target) == trialRoom
                || state.helpers().getOrDefault(trialRoom, Set.of()).contains(target);
    }

    /** A launched attack keeps its original room scope even if passages open later. */
    public static boolean mayResolveAttack(State state, AttackScope scope, int actorPhysicalRoom, UUID target) {
        return scope != null && owns(state, scope.generation())
                && scope.launchRegions().contains(actorPhysicalRoom)
                && scope.launchRegions().contains(physical(state, target))
                && mayTarget(state, scope.generation(), scope.trialRoom(), actorPhysicalRoom,
                        scope.echoOwner(), target);
    }

    public static boolean mayInteract(State state, long generation, UUID source, UUID target) {
        if (!owns(state, generation) || !registered(state, source) || !registered(state, target)) return false;
        int room = physical(state, source);
        if (room < 0 || room != physical(state, target)) return false;
        if (state.trials().get(room) == Trial.ECHO) return source.equals(target);
        return admitted(state, source, room) && admitted(state, target, room);
    }

    private static boolean admitted(State state, UUID player, int room) {
        return state.assignment().chamberByPlayer().get(player) == room
                || state.helpers().getOrDefault(room, Set.of()).contains(player);
    }

    private static boolean completedOwnClaim(State state, UUID player) {
        int claim = state.assignment().chamberByPlayer().get(player);
        return state.trials().get(claim) == Trial.ECHO
                ? state.completedEchoOwners().contains(player) : state.completedRooms().contains(claim);
    }

    private static boolean connectedOrdinary(State state, int source, int destination) {
        if (!state.trials().containsKey(source) || !state.trials().containsKey(destination)
                || state.trials().get(source) == Trial.ECHO
                || state.trials().get(destination) == Trial.ECHO) return false;
        var pending = new ArrayDeque<Integer>();
        var visited = new LinkedHashSet<Integer>();
        pending.add(source);
        while (!pending.isEmpty()) {
            int room = pending.removeFirst();
            if (!visited.add(room)) continue;
            if (room == destination) return true;
            for (Passage passage : state.passages()) {
                int next = passage.firstChamber() == room ? passage.secondChamber()
                        : passage.secondChamber() == room ? passage.firstChamber() : -1;
                if (next >= 0 && !visited.contains(next) && state.trials().get(next) != Trial.ECHO
                        && (next == destination || state.completedRooms().contains(next))) pending.add(next);
            }
        }
        return false;
    }

    private static boolean owns(State state, long generation) {
        return state != null && generation > 0L && state.generation() == generation;
    }
    private static boolean registered(State state, UUID player) {
        return player != null && state.assignment().chamberByPlayer().containsKey(player);
    }
    private static int physical(State state, UUID player) {
        return player == null ? -1 : state.physicalRooms().getOrDefault(player, -1);
    }

    public record AttackScope(long generation, int trialRoom, UUID echoOwner, Set<Integer> launchRegions) {
        public AttackScope {
            launchRegions = Set.copyOf(launchRegions == null ? Set.of() : launchRegions);
            if (generation <= 0L || trialRoom < 0 || trialRoom >= 4 || !launchRegions.contains(trialRoom)
                    || launchRegions.size() > 4 || launchRegions.stream().anyMatch(room -> room < 0 || room >= 4)) {
                throw new IllegalArgumentException("invalid attack admission scope");
            }
        }
    }

    /** Immutable bounded view of the existing chamber controller, without Bukkit objects. */
    public record State(long generation, ChamberIsolationPolicy.Assignment assignment,
                        Map<Integer, Trial> trials, Set<Integer> completedRooms,
                        Set<UUID> completedEchoOwners, Map<Integer, Set<UUID>> helpers,
                        Map<UUID, Integer> physicalRooms, Set<Passage> passages) {
        public State {
            if (generation <= 0L || assignment == null || assignment.chamberCount() < 1
                    || assignment.chamberByPlayer().isEmpty() || assignment.chamberByPlayer().size() > 20) {
                throw new IllegalArgumentException("invalid named admission roster");
            }
            trials = Map.copyOf(trials);
            completedRooms = Set.copyOf(completedRooms);
            completedEchoOwners = Set.copyOf(completedEchoOwners);
            physicalRooms = Map.copyOf(physicalRooms);
            passages = Set.copyOf(passages);
            Map<Integer, Set<UUID>> frozenHelpers = new LinkedHashMap<>();
            helpers.forEach((room, players) -> frozenHelpers.put(room, Set.copyOf(players)));
            helpers = Map.copyOf(frozenHelpers);
            int count = assignment.chamberCount();
            if (trials.size() != count || new LinkedHashSet<>(trials.values()).size() != count) {
                throw new IllegalArgumentException("each room requires a distinct named trial");
            }
            for (int room = 0; room < count; room++) {
                if (!trials.containsKey(room)) throw new IllegalArgumentException("missing named room");
            }
            if (!trials.keySet().containsAll(completedRooms)) throw new IllegalArgumentException("invalid room receipt");
            for (UUID owner : completedEchoOwners) {
                Integer claim = assignment.chamberByPlayer().get(owner);
                if (claim == null || trials.get(claim) != Trial.ECHO) {
                    throw new IllegalArgumentException("completion belongs to a foreign Echo owner");
                }
            }
            for (int room : completedRooms) {
                if (trials.get(room) == Trial.ECHO && (assignment.playersIn(room).isEmpty()
                        || !completedEchoOwners.containsAll(assignment.playersIn(room)))) {
                    throw new IllegalArgumentException("unresolved private duel cannot complete Echo room");
                }
            }
            for (var entry : helpers.entrySet()) {
                if (!trials.containsKey(entry.getKey()) || trials.get(entry.getKey()) == Trial.ECHO
                        || !assignment.chamberByPlayer().keySet().containsAll(entry.getValue())) {
                    throw new IllegalArgumentException("invalid helper admission");
                }
                for (UUID helper : entry.getValue()) {
                    int claim = assignment.chamberByPlayer().get(helper);
                    if (claim != entry.getKey() && !(trials.get(claim) == Trial.ECHO
                            ? completedEchoOwners.contains(helper) : completedRooms.contains(claim))) {
                        throw new IllegalArgumentException("unfinished owner cannot be an admitted helper");
                    }
                }
            }
            for (var entry : physicalRooms.entrySet()) {
                if (!assignment.chamberByPlayer().containsKey(entry.getKey()) || entry.getValue() < -1
                        || entry.getValue() >= count) throw new IllegalArgumentException("invalid physical membership");
            }
            for (Passage passage : passages) {
                if (!trials.containsKey(passage.firstChamber()) || !trials.containsKey(passage.secondChamber())
                        || trials.get(passage.firstChamber()) == Trial.ECHO
                        || trials.get(passage.secondChamber()) == Trial.ECHO
                        || RealitySplitBarrierPolicy.boundaryForPair(passage.firstChamber(),
                                passage.secondChamber(), count) < 0) {
                    throw new IllegalArgumentException("invalid ordinary passage");
                }
            }
        }
    }
}
