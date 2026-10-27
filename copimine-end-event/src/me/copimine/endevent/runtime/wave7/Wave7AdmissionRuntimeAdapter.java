package me.copimine.endevent.runtime.wave7;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.Wave7AdmissionPolicy;
import me.copimine.endevent.domain.Wave7AdmissionPolicy.AttackScope;
import me.copimine.endevent.domain.Wave7AdmissionPolicy.State;
import me.copimine.endevent.domain.Wave7AdmissionPolicy.Trial;
import me.copimine.endevent.runtime.RealitySplitChamberController;
import me.copimine.endevent.runtime.RealitySplitChamberController.Passage;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrialState;
import me.copimine.endevent.runtime.RealitySplitTrialController.Stage;

/**
 * Feeds the existing chamber and named-trial controllers into
 * {@link Wave7AdmissionPolicy}. Bukkit-free: {@link Wave7AdmissionListener}
 * adapts entity events, the plugin supplies the player's physical room.
 */
public final class Wave7AdmissionRuntimeAdapter {
    /** IGNORE keeps waves 1-6, ordinary damage and unrelated entities untouched. */
    public enum Decision { IGNORE, ALLOW, DENY }

    private static final int MAX_SCOPES = 64;
    private final RealitySplitChamberController chambers;
    private final RealitySplitTrialController trials;
    private final LongSupplier generation;
    private final Function<UUID, Integer> roomOf;
    private final Map<UUID, Tracked> launched = new LinkedHashMap<>();

    private record Tracked(UUID actor, AttackScope scope) { }

    public Wave7AdmissionRuntimeAdapter(RealitySplitChamberController chambers,
                                        RealitySplitTrialController trials,
                                        LongSupplier generation,
                                        Function<UUID, Integer> roomOf) {
        this.chambers = Objects.requireNonNull(chambers);
        this.trials = Objects.requireNonNull(trials);
        this.generation = Objects.requireNonNull(generation);
        this.roomOf = Objects.requireNonNull(roomOf);
    }

    /**
     * Immutable view for this event, or null when the named Wave 7 layout is
     * absent, legacy or inconsistent. A null view never cancels anything.
     */
    public State snapshot() {
        long current = generation.getAsLong();
        if (current <= 0L || !chambers.owns(current) || !trials.owns(current)) return null;
        ChamberIsolationPolicy.Assignment assignment = chambers.assignment();
        int count = assignment.chamberCount();
        if (count < 1 || count > ChamberIsolationPolicy.MAX_CHAMBERS
                || assignment.chamberByPlayer().isEmpty()) return null;
        Map<Integer, Trial> layout = new LinkedHashMap<>();
        Set<Integer> completed = new LinkedHashSet<>();
        Set<UUID> echoOwners = new LinkedHashSet<>();
        for (int room = 0; room < count; room++) {
            NamedTrialState named = trials.namedTrial(room);
            if (named == null) return null;
            Trial trial = trial(named.trial());
            if (trial == null) return null;
            layout.put(room, trial);
            if (named.stage() == Stage.COMPLETE) completed.add(room);
            if (trial == Trial.ECHO) echoOwners.addAll(named.echoReceipts().keySet());
        }
        Map<UUID, Integer> physical = new LinkedHashMap<>();
        Map<Integer, Set<UUID>> helpers = new LinkedHashMap<>();
        for (UUID player : assignment.chamberByPlayer().keySet()) {
            Integer reported = roomOf.apply(player);
            int at = reported != null && reported >= 0 && reported < count ? reported : -1;
            physical.put(player, at);
            int claim = assignment.chamberByPlayer().get(player);
            if (at < 0 || at == claim || layout.get(at) == Trial.ECHO
                    || !claimComplete(assignment, layout, completed, echoOwners, player)) continue;
            helpers.computeIfAbsent(at, ignored -> new LinkedHashSet<>()).add(player);
        }
        // Echo rooms never join ordinary connectivity, so their passages are not
        // part of the walk graph and cannot invalidate the whole snapshot.
        Set<Passage> passages = new LinkedHashSet<>();
        for (Passage passage : chambers.openPassages()) {
            if (layout.get(passage.firstChamber()) != Trial.ECHO
                    && layout.get(passage.secondChamber()) != Trial.ECHO) passages.add(passage);
        }
        try {
            return new State(current, assignment, layout, completed, echoOwners,
                    helpers, physical, passages);
        } catch (IllegalArgumentException inconsistentAttempt) {
            return null;
        }
    }

    /** Selection or re-selection of a player target by a Wave 7 actor. */
    public Decision target(UUID actor, UUID target) {
        State state = snapshot();
        if (state == null) return Decision.IGNORE;
        int room = actorRoom(state, actor);
        if (room < 0) return Decision.IGNORE;
        return decide(state, room, actor, target);
    }

    /**
     * Freeze the actor's legal reach at launch. Later passages therefore cannot
     * extend a projectile that is already airborne.
     */
    public Decision launch(UUID projectile, UUID actor) {
        State state = snapshot();
        if (state == null || projectile == null) return Decision.IGNORE;
        int room = actorRoom(state, actor);
        if (room < 0) return Decision.IGNORE;
        if (launched.containsKey(projectile)) return Decision.ALLOW;
        boolean echo = state.trials().get(room) == Trial.ECHO;
        UUID echoOwner = echo ? echoOwner(state, room, actor) : null;
        if (echo && echoOwner == null || state.completedRooms().contains(room)) return Decision.DENY;
        Set<Integer> regions = new LinkedHashSet<>();
        for (int candidate = 0; candidate < state.trials().size(); candidate++) {
            if (Wave7AdmissionPolicy.mayMoveActor(state, state.generation(), room, candidate)) {
                regions.add(candidate);
            }
        }
        if (launched.size() >= MAX_SCOPES) {
            var oldest = launched.keySet().iterator();
            if (oldest.hasNext()) { oldest.next(); oldest.remove(); }
        }
        // shortcut: reach is derived from the trial room and the completed graph,
        // not live mob coordinates; upgrade with per-actor position tracking.
        launched.put(projectile, new Tracked(actor,
                new AttackScope(state.generation(), room, echoOwner, regions)));
        return Decision.ALLOW;
    }

    /** Melee (null projectile) or an already launched projectile. */
    public Decision damage(UUID projectile, UUID actor, UUID target) {
        State state = snapshot();
        if (state == null) return Decision.IGNORE;
        int room = actorRoom(state, actor);
        if (room < 0) return Decision.IGNORE;
        if (projectile == null) return decide(state, room, actor, target);
        Tracked tracked = launched.get(projectile);
        if (tracked == null || !tracked.actor().equals(actor)) return Decision.DENY;
        return Wave7AdmissionPolicy.mayResolveAttack(state, tracked.scope(), room, target)
                ? Decision.ALLOW : Decision.DENY;
    }

    public void clear() { launched.clear(); }
    public int trackedScopes() { return launched.size(); }

    /** An Echo replica whose duel is gone may not hit anyone. */
    private Decision decide(State state, int room, UUID actor, UUID target) {
        UUID echoOwner = echoOwner(state, room, actor);
        if (state.trials().get(room) == Trial.ECHO && echoOwner == null) return Decision.DENY;
        return Wave7AdmissionPolicy.mayTarget(state, state.generation(), room, room,
                echoOwner, target) ? Decision.ALLOW : Decision.DENY;
    }

    private int actorRoom(State state, UUID actor) {
        int room = chambers.chamberOfEntity(state.generation(), actor);
        return state.trials().containsKey(room) ? room : -1;
    }

    /** Owner bound to this Echo replica, or null when no live duel names it. */
    private UUID echoOwner(State state, int room, UUID actor) {
        if (state.trials().get(room) != Trial.ECHO || actor == null) return null;
        try {
            var duels = trials.namedSnapshot().personalEchoDuels().get(room);
            if (duels == null) return null;
            for (var entry : duels.entrySet()) {
                if (entry.getValue().actor() != null
                        && actor.equals(entry.getValue().actor().actorUuid())) return entry.getKey();
            }
        } catch (IllegalStateException inactiveNamedLayout) {
            return null;
        }
        return null;
    }

    private static boolean claimComplete(ChamberIsolationPolicy.Assignment assignment,
                                          Map<Integer, Trial> layout, Set<Integer> completed,
                                          Set<UUID> echoOwners, UUID player) {
        Integer claim = assignment.chamberByPlayer().get(player);
        if (claim == null) return false;
        return layout.get(claim) == Trial.ECHO ? echoOwners.contains(player) : completed.contains(claim);
    }

    private static Trial trial(NamedTrial named) {
        return switch (named) {
            case WARDEN -> Trial.WARDEN;
            case PERSONAL_ECHO -> Trial.ECHO;
            case MOBILE_MARKSMAN -> Trial.MARKSMAN;
            case RIFT_ARCHMAGE -> Trial.ARCHMAGE;
        };
    }
}