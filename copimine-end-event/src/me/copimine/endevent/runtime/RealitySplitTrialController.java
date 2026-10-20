package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy;
import me.copimine.endevent.domain.Wave7EchoDuelCellPolicy;

/**
 * Generation-scoped state for the four independent Wave 7 trials. Bukkit
 * events report validated outcomes here; this controller owns their order
 * and completion, never the global encounter phase.
 */
public final class RealitySplitTrialController {
    public enum Trial {
        WARDEN,
        RIFT_REFLECTION,
        JUGGERNAUT,
        RIFT_HUNTER
    }

    public enum Stage {
        ACTIVE,
        EXPOSED,
        COMPLETE
    }

    /** New identities have their own schema; these never alias the legacy Trial enum. */
    public enum NamedTrial { WARDEN, PERSONAL_ECHO, MOBILE_MARKSMAN, RIFT_ARCHMAGE }

    /**
     * Server-side proof that a concrete actor entity reached its death event.
     * The Paper adapter constructs this only after validating the owned entity
     * and its persisted actor identity checkpoint.
     */
    public record NamedActorDeathReceipt(UUID eventId, long generation, int chamber,
                                         NamedTrial trial, UUID actorUuid) {
        public NamedActorDeathReceipt {
            if (eventId == null || generation <= 0L || chamber < 0 || chamber >= 4
                    || trial == null || trial == NamedTrial.PERSONAL_ECHO || actorUuid == null) {
                throw new IllegalArgumentException("invalid named actor death receipt");
            }
        }
    }

    // Stable legacy identities. Enum ordering must never migrate saved rooms.
    private static final Trial[] ROOM_TRIALS = {
            Trial.WARDEN, Trial.RIFT_REFLECTION, Trial.JUGGERNAUT, Trial.RIFT_HUNTER
    };
    private static final int REQUIRED_SEALS = 3;
    private static final int REQUIRED_ANCHORS = 3;

    private long generation = Long.MIN_VALUE;
    private final Map<Integer, TrialState> trials = new LinkedHashMap<>();
    private NamedData named;

    /** Select distinct trial types once, without replacement, before the first actor spawns. */
    public synchronized void beginNamed(UUID eventId, long generation,
                                        ChamberIsolationPolicy.Assignment assignment,
                                        RandomGenerator random) {
        validateNamedAssignment(assignment);
        if (eventId == null || generation <= 0L || random == null) {
            throw new IllegalArgumentException("invalid named Wave 7 identity or random source");
        }
        if (ownsNamed(eventId, generation)) {
            validateNamedOwners(assignment, named);
            return;
        }
        List<UUID> participants = new ArrayList<>(assignment.chamberByPlayer().keySet());
        Wave7RoomLayoutPolicy.Snapshot selectedRooms =
                Wave7RoomLayoutPolicy.select(generation, participants, random);
        if (!selectedRooms.roomByPlayer().equals(assignment.chamberByPlayer())) {
            throw new IllegalArgumentException("shared Wave 7 layout changed the assigned room owners");
        }
        List<NamedTrial> selected = selectedRooms.rooms().stream().map(room -> switch (room.trial()) {
            case WARDEN -> NamedTrial.WARDEN;
            case ECHO -> NamedTrial.PERSONAL_ECHO;
            case MARKSMAN -> NamedTrial.MOBILE_MARKSMAN;
            case ARCHMAGE -> NamedTrial.RIFT_ARCHMAGE;
        }).toList();
        beginNamed(eventId, generation, assignment, selected);
    }

    /** Starts an already selected layout. Restore must use restoreNamed, never sample again. */
    public synchronized void beginNamed(UUID eventId, long generation,
                                        ChamberIsolationPolicy.Assignment assignment,
                                        List<NamedTrial> selectedLayout) {
        validateNamedAssignment(assignment);
        if (selectedLayout == null || selectedLayout.size() != assignment.chamberCount()) {
            throw new IllegalArgumentException("selected layout must match assigned rooms");
        }
        if (ownsNamed(eventId, generation)) {
            validateNamedOwners(assignment, named);
            if (!named.selectedLayout().equals(selectedLayout)) {
                throw new IllegalArgumentException("selected named Wave 7 layout is immutable within its attempt");
            }
            return;
        }
        Map<Integer, NamedTrialState> initial = new LinkedHashMap<>();
        Map<Integer, Map<UUID, RealitySplitEchoDuelState.DuelRecord>> echoDuels = new LinkedHashMap<>();
        for (int room = 0; room < selectedLayout.size(); room++) {
            Set<UUID> owners = assignment.playersIn(room);
            initial.put(room, new NamedTrialState(room, selectedLayout.get(room), Stage.ACTIVE,
                    owners, Map.of()));
            if (selectedLayout.get(room) == NamedTrial.PERSONAL_ECHO) {
                Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomDuels = new LinkedHashMap<>();
                for (UUID owner : owners) {
                    UUID duelId = stableDuelId(eventId, generation, room, owner);
                    roomDuels.put(owner, RealitySplitEchoDuelState.DuelRecord.planned(
                            eventId, generation, generation, room, duelId, owner));
                }
                echoDuels.put(room, roomDuels);
            }
        }
        restoreNamed(eventId, generation, assignment,
                new NamedData(eventId, generation, selectedLayout, initial, echoDuels));
    }

    /** Validate every owner, room and receipt before replacing a live generation. */
    public synchronized void restoreNamed(UUID eventId, long expectedGeneration,
                                          ChamberIsolationPolicy.Assignment assignment,
                                          NamedData restored) {
        validateNamedAssignment(assignment);
        if (restored == null || eventId == null || !eventId.equals(restored.eventId())
                || expectedGeneration <= 0L || restored.generation() != expectedGeneration
                || restored.trials().size() != assignment.chamberCount()) {
            throw new IllegalArgumentException("named Wave 7 snapshot identity does not match");
        }
        validateNamedOwners(assignment, restored);
        if (ownsNamed(eventId, expectedGeneration)) {
            if (!named.selectedLayout().equals(restored.selectedLayout())) {
                throw new IllegalArgumentException("selected named Wave 7 layout is immutable within its attempt");
            }
            validateNamedOwners(assignment, named);
            for (int room = 0; room < assignment.chamberCount(); room++) {
                var current = named.trials().get(room);
                var replacement = restored.trials().get(room);
                if (current.stage() == Stage.COMPLETE && replacement.stage() != Stage.COMPLETE
                        || !replacement.echoReceipts().entrySet().containsAll(current.echoReceipts().entrySet())) {
                    throw new IllegalArgumentException("restoration cannot erase durable named completion receipts");
                }
            }
            for (var roomEntry : named.personalEchoDuels().entrySet()) {
                Map<UUID, RealitySplitEchoDuelState.DuelRecord> replacement =
                        restored.personalEchoDuels().get(roomEntry.getKey());
                for (var duelEntry : roomEntry.getValue().entrySet()) {
                    var current = duelEntry.getValue();
                    var next = replacement == null ? null : replacement.get(duelEntry.getKey());
                    if (next == null || !canRestoreDuel(current, next)) {
                        throw new IllegalArgumentException("restoration cannot erase or rewind durable Echo duel state");
                    }
                }
            }
        }
        // NamedData has already validated its entire immutable layout and all receipts.
        generation = expectedGeneration;
        named = restored;
        trials.clear();
    }

    public synchronized NamedTrialState namedTrial(int chamber) {
        return named == null ? null : named.trials().get(chamber);
    }

    /** Deterministic logical cells for an Echo room; world adapters still own physical barriers. */
    public synchronized List<Wave7EchoDuelCellPolicy.Cell> echoDuelCells(int chamber) {
        if (named == null) return List.of();
        NamedTrialState state = named.trials().get(chamber);
        if (state == null || state.trial() != NamedTrial.PERSONAL_ECHO) return List.of();
        return Wave7EchoDuelCellPolicy.plan(chamber, named.selectedLayout().size(), state.owners());
    }

    public synchronized NamedData namedSnapshot() {
        if (named == null) throw new IllegalStateException("named Wave 7 trial layout is not active");
        return named;
    }

    public synchronized boolean ownsNamed(UUID eventId, long expectedGeneration) {
        return named != null && owns(expectedGeneration) && named.eventId().equals(eventId);
    }

    /** The adapter must supply a real validated actor death, never entity absence. */
    public synchronized boolean completeNamedActor(UUID eventId, long expectedGeneration,
                                                    int chamber, NamedTrial expectedTrial) {
        if (!ownsNamed(eventId, expectedGeneration) || expectedTrial == null
                || expectedTrial == NamedTrial.PERSONAL_ECHO) return false;
        NamedTrialState state = named.trials().get(chamber);
        if (state == null || state.trial() != expectedTrial || state.stage() != Stage.ACTIVE) return false;
        replaceNamedTrial(new NamedTrialState(chamber, state.trial(), Stage.COMPLETE,
                state.owners(), Map.of()));
        return true;
    }

    /** Apply a completion only from a concrete, already validated Paper entity death. */
    public synchronized boolean completeNamedActor(NamedActorDeathReceipt receipt) {
        if (receipt == null || !ownsNamed(receipt.eventId(), receipt.generation())) return false;
        NamedTrialState state = named.trials().get(receipt.chamber());
        if (state == null || state.trial() != receipt.trial() || state.stage() != Stage.ACTIVE) return false;
        replaceNamedTrial(new NamedTrialState(receipt.chamber(), state.trial(), Stage.COMPLETE,
                state.owners(), Map.of()));
        return true;
    }

    /**
     * Record a persisted owner duel checkpoint. Caller must have validated the actor and
     * allowlisted replica state before entering the controller.
     */
    public synchronized boolean registerPersonalEchoDuel(RealitySplitEchoDuelState.DuelRecord update) {
        if (named == null || update == null || !update.eventId().equals(named.eventId())
                || update.attemptGeneration() != generation) return false;
        NamedTrialState state = named.trials().get(update.room());
        Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomDuels = named.personalEchoDuels().get(update.room());
        if (state == null || state.trial() != NamedTrial.PERSONAL_ECHO || roomDuels == null
                || !state.owners().contains(update.ownerUuid())) return false;
        RealitySplitEchoDuelState.DuelRecord current = roomDuels.get(update.ownerUuid());
        if (current == null || !current.duelId().equals(update.duelId())
                || current.lifecycle() == RealitySplitEchoDuelState.Lifecycle.COMPLETE
                || update.lifecycle() == RealitySplitEchoDuelState.Lifecycle.COMPLETE
                || update.revision() <= current.revision()
                || !isAllowedDuelTransition(current.lifecycle(), update.lifecycle())
                || update.runtimeGeneration() < current.runtimeGeneration()
                || update.replicaRevision() < current.replicaRevision()
                || current.lifecycle() == RealitySplitEchoDuelState.Lifecycle.PLANNED
                && (update.actor() == null
                || update.behaviorStrategy() == null
                || update.actor().currentHealth() != update.ownerStartingHealth()
                || update.actor().maximumHealth() != update.ownerMaximumHealth())) return false;
        if (current.lifecycle() != RealitySplitEchoDuelState.Lifecycle.PLANNED
                && (update.ownerStartingHealth() != current.ownerStartingHealth()
                || update.ownerMaximumHealth() != current.ownerMaximumHealth()
                || !update.behaviorStrategy().equals(current.behaviorStrategy())
                || current.actor() == null || update.actor() == null
                || update.actor().revision() < current.actor().revision()
                || update.actor().deathSerial() != current.actor().deathSerial()
                || update.actor().revision() == current.actor().revision()
                && !sameActorPayload(current.actor(), update.actor())
                || update.lifecycle() == RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE
                && current.returnDeadlineMillis() > 0L
                && update.returnDeadlineMillis() != current.returnDeadlineMillis()
                || update.lifecycle() != RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE
                && update.returnDeadlineMillis() != current.returnDeadlineMillis()
                || update.replicaRevision() == current.replicaRevision()
                && !update.replicaState().equals(current.replicaState())
                || update.runtimeGeneration() == current.runtimeGeneration()
                && !update.actor().actorUuid().equals(current.actor().actorUuid()))) return false;
        if (current.lifecycle() == RealitySplitEchoDuelState.Lifecycle.PLANNED
                && update.lifecycle() != RealitySplitEchoDuelState.Lifecycle.ACTIVE) return false;
        Set<UUID> rosterOwners = new HashSet<>();
        named.trials().values().forEach(room -> rosterOwners.addAll(room.owners()));
        if (rosterOwners.contains(update.duelId())
                || update.actor() != null && rosterOwners.contains(update.actor().actorUuid())) return false;
        for (var existingRoom : named.personalEchoDuels().values()) {
            for (var existing : existingRoom.values()) {
                if (existing == current) continue;
                if (existing.duelId().equals(update.duelId())
                        || existing.actor() != null && update.actor() != null
                        && existing.actor().actorUuid().equals(update.actor().actorUuid())) return false;
            }
        }
        Map<Integer, Map<UUID, RealitySplitEchoDuelState.DuelRecord>> allDuels =
                new LinkedHashMap<>(named.personalEchoDuels());
        Map<UUID, RealitySplitEchoDuelState.DuelRecord> replacement = new LinkedHashMap<>(roomDuels);
        replacement.put(update.ownerUuid(), update);
        allDuels.put(update.room(), replacement);
        named = new NamedData(named.eventId(), generation, named.selectedLayout(), named.trials(), allDuels);
        return true;
    }

    /**
     * Build an idempotent receipt only for the exact live actor named by the current owner duel.
     * Callers must invoke this from a server-confirmed actor death callback.
     */
    public synchronized EchoCompletionReceipt receiptForConfirmedActorDeath(
            UUID eventId, long expectedGeneration, int chamber, UUID owner, UUID deadActor) {
        if (!ownsNamed(eventId, expectedGeneration) || owner == null || deadActor == null) return null;
        NamedTrialState trial = named.trials().get(chamber);
        Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomDuels = named.personalEchoDuels().get(chamber);
        RealitySplitEchoDuelState.DuelRecord duel = roomDuels == null ? null : roomDuels.get(owner);
        if (trial == null || trial.trial() != NamedTrial.PERSONAL_ECHO || trial.stage() != Stage.ACTIVE
                || !trial.owners().contains(owner) || trial.echoReceipts().containsKey(owner)
                || duel == null || duel.lifecycle() != RealitySplitEchoDuelState.Lifecycle.ACTIVE
                || !duel.eventId().equals(eventId) || duel.attemptGeneration() != expectedGeneration
                || duel.room() != chamber || !duel.ownerUuid().equals(owner) || duel.actor() == null
                || duel.actor().lifecycle() != RealitySplitEchoDuelState.ActorLifecycle.ALIVE
                || !duel.actor().actorUuid().equals(deadActor)
                || duel.actor().deathSerial() >= RealitySplitEchoDuelState.MAX_COUNTER) return null;
        return new EchoCompletionReceipt(eventId, expectedGeneration, duel.runtimeGeneration(),
                chamber, duel.duelId(), owner, deadActor, duel.actor().deathSerial() + 1L);
    }

    /** A personal receipt must identify the current persisted actor and its next death serial. */
    public synchronized boolean completePersonalEcho(EchoCompletionReceipt receipt) {
        if (named == null || receipt == null || !ownsNamed(receipt.eventId(), receipt.generation())) return false;
        int chamber = receipt.chamber();
        UUID owner = receipt.owner();
        NamedTrialState state = named.trials().get(chamber);
        Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomDuels = named.personalEchoDuels().get(chamber);
        RealitySplitEchoDuelState.DuelRecord duel = roomDuels == null ? null : roomDuels.get(owner);
        if (state == null || state.trial() != NamedTrial.PERSONAL_ECHO
                || state.stage() != Stage.ACTIVE || !state.owners().contains(owner)
                || state.echoReceipts().containsKey(owner) || duel == null
                || duel.lifecycle() != RealitySplitEchoDuelState.Lifecycle.ACTIVE
                || !duel.eventId().equals(receipt.eventId())
                || duel.attemptGeneration() != receipt.generation()
                || duel.runtimeGeneration() != receipt.runtimeGeneration()
                || duel.room() != chamber || !duel.duelId().equals(receipt.duelId())
                || !duel.ownerUuid().equals(owner) || duel.actor() == null
                || !duel.actor().actorUuid().equals(receipt.actorUuid())
                || duel.revision() >= RealitySplitEchoDuelState.MAX_COUNTER
                || duel.actor().revision() >= RealitySplitEchoDuelState.MAX_COUNTER
                || receipt.deathSerial() != duel.actor().deathSerial() + 1L) return false;
        RealitySplitEchoDuelState.DuelRecord completed = duel.died(receipt.deathSerial());
        Map<Integer, Map<UUID, RealitySplitEchoDuelState.DuelRecord>> allDuels =
                new LinkedHashMap<>(named.personalEchoDuels());
        Map<UUID, RealitySplitEchoDuelState.DuelRecord> replacement = new LinkedHashMap<>(roomDuels);
        replacement.put(owner, completed);
        allDuels.put(chamber, replacement);
        Map<UUID, EchoCompletionReceipt> receipts = new LinkedHashMap<>(state.echoReceipts());
        receipts.put(owner, receipt);
        Map<Integer, NamedTrialState> updatedTrials = new LinkedHashMap<>(named.trials());
        updatedTrials.put(chamber, new NamedTrialState(chamber, state.trial(),
                receipts.size() == state.owners().size() ? Stage.COMPLETE : Stage.ACTIVE,
                state.owners(), receipts));
        named = new NamedData(named.eventId(), generation, named.selectedLayout(), updatedTrials, allDuels);
        return true;
    }

    /** Owner-only completions lack an actor identity and are intentionally rejected. */
    public synchronized boolean completePersonalEcho(UUID eventId, long expectedGeneration,
                                                      int chamber, UUID owner) {
        return false;
    }

    private void replaceNamedTrial(NamedTrialState replacement) {
        Map<Integer, NamedTrialState> updated = new LinkedHashMap<>(named.trials());
        updated.put(replacement.chamber(), replacement);
        named = new NamedData(named.eventId(), generation,
                named.selectedLayout(), updated, named.personalEchoDuels());
    }

    private static UUID stableDuelId(UUID eventId, long generation, int room, UUID owner) {
        String identity = eventId + ":" + generation + ":" + room + ":" + owner;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isAllowedDuelTransition(RealitySplitEchoDuelState.Lifecycle current,
                                                    RealitySplitEchoDuelState.Lifecycle replacement) {
        return switch (current) {
            case PLANNED -> replacement == RealitySplitEchoDuelState.Lifecycle.ACTIVE;
            case ACTIVE -> replacement == RealitySplitEchoDuelState.Lifecycle.ACTIVE
                    || replacement == RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE;
            case OWNER_OFFLINE -> replacement == RealitySplitEchoDuelState.Lifecycle.ACTIVE
                    || replacement == RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE;
            case COMPLETE -> false;
        };
    }

    private static boolean canRestoreDuel(RealitySplitEchoDuelState.DuelRecord current,
                                           RealitySplitEchoDuelState.DuelRecord replacement) {
        if (!current.duelId().equals(replacement.duelId())
                || replacement.revision() < current.revision()
                || replacement.runtimeGeneration() < current.runtimeGeneration()
                || replacement.replicaRevision() < current.replicaRevision()) return false;
        if (current.lifecycle() == RealitySplitEchoDuelState.Lifecycle.COMPLETE) return current.equals(replacement);
        if (current.lifecycle() != RealitySplitEchoDuelState.Lifecycle.PLANNED
                && (replacement.ownerStartingHealth() != current.ownerStartingHealth()
                || replacement.ownerMaximumHealth() != current.ownerMaximumHealth()
                || !replacement.behaviorStrategy().equals(current.behaviorStrategy())
                || current.actor() == null || replacement.actor() == null
                || replacement.actor().revision() < current.actor().revision()
                || replacement.actor().deathSerial() < current.actor().deathSerial()
                || replacement.actor().revision() == current.actor().revision()
                && !sameActorPayload(current.actor(), replacement.actor())
                || replacement.replicaRevision() == current.replicaRevision()
                && !replacement.replicaState().equals(current.replicaState())
                || replacement.lifecycle() == RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE
                && current.returnDeadlineMillis() > 0L
                && replacement.returnDeadlineMillis() != current.returnDeadlineMillis()
                || replacement.lifecycle() != RealitySplitEchoDuelState.Lifecycle.OWNER_OFFLINE
                && current.lifecycle() != RealitySplitEchoDuelState.Lifecycle.PLANNED
                && replacement.returnDeadlineMillis() != current.returnDeadlineMillis()
                || replacement.runtimeGeneration() == current.runtimeGeneration()
                && !replacement.actor().actorUuid().equals(current.actor().actorUuid()))) return false;
        return replacement.revision() != current.revision() || replacement.equals(current);
    }

    private static boolean sameActorPayload(RealitySplitEchoDuelState.ActorState left,
                                            RealitySplitEchoDuelState.ActorState right) {
        return left.currentHealth() == right.currentHealth()
                && left.maximumHealth() == right.maximumHealth()
                && left.lifecycle() == right.lifecycle()
                && left.revision() == right.revision()
                && left.nextActionTick() == right.nextActionTick()
                && left.meleeCooldownUntilTick() == right.meleeCooldownUntilTick()
                && left.rangedCooldownUntilTick() == right.rangedCooldownUntilTick()
                && left.pendingUseUntilTick() == right.pendingUseUntilTick()
                && left.deathSerial() == right.deathSerial();
    }

    private static void validateNamedAssignment(ChamberIsolationPolicy.Assignment assignment) {
        if (assignment == null || assignment.chamberByPlayer().isEmpty()
                || assignment.chamberByPlayer().size() > 20
                || assignment.chamberCount() != ChamberIsolationPolicy.chamberCount(
                        assignment.chamberByPlayer().size())) {
            throw new IllegalArgumentException("invalid named Wave 7 room allocation");
        }
        for (int room = 0; room < assignment.chamberCount(); room++) {
            if (assignment.playersIn(room).isEmpty()) {
                throw new IllegalArgumentException("named Wave 7 room has no owner");
            }
        }
    }

    private static void validateNamedOwners(ChamberIsolationPolicy.Assignment assignment,
                                             NamedData data) {
        if (data.trials().size() != assignment.chamberCount()) {
            throw new IllegalArgumentException("named Wave 7 snapshot room count does not match");
        }
        for (int room = 0; room < assignment.chamberCount(); room++) {
            if (!data.trials().get(room).owners().equals(assignment.playersIn(room))) {
                throw new IllegalArgumentException("named Wave 7 snapshot owner assignment does not match");
            }
        }
    }

    /** Activate one appropriate trial per assigned chamber, up to four. */
    public synchronized void begin(long generation,
                                   ChamberIsolationPolicy.Assignment assignment) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (assignment == null || assignment.chamberCount() < 1
                || assignment.chamberCount() > ROOM_TRIALS.length) {
            throw new IllegalArgumentException("at least one valid trial room is required");
        }
        this.generation = generation;
        named = null;
        trials.clear();
        for (int chamber = 0; chamber < assignment.chamberCount(); chamber++) {
            Trial trial = ROOM_TRIALS[chamber];
            int required = trial == Trial.RIFT_REFLECTION ? REQUIRED_SEALS
                    : trial == Trial.JUGGERNAUT ? REQUIRED_ANCHORS : 1;
            trials.put(chamber, new TrialState(chamber, trial, Stage.ACTIVE, 0, required));
        }
    }

    /** Restore progress only after the owning encounter snapshot has validated. */
    public synchronized void restore(long generation,
                                     ChamberIsolationPolicy.Assignment assignment,
                                     Map<Integer, TrialState> restoredTrials) {
        if (generation <= 0L || assignment == null || assignment.chamberCount() < 1
                || assignment.chamberCount() > ROOM_TRIALS.length) {
            throw new IllegalArgumentException("invalid Wave 7 trial generation or assignment");
        }
        if (restoredTrials == null || restoredTrials.size() != assignment.chamberCount()) {
            throw new IllegalArgumentException("every Wave 7 chamber needs one trial state");
        }
        Map<Integer, TrialState> validated = new LinkedHashMap<>();
        for (int chamber = 0; chamber < assignment.chamberCount(); chamber++) {
            TrialState state = restoredTrials.get(chamber);
            if (state == null || state.chamber() != chamber
                    || state.trial() != ROOM_TRIALS[chamber]
                    || state.required() != requiredActions(state.trial())
                    || state.progress() > state.required()
                    || !validStage(state)) {
                throw new IllegalArgumentException("Wave 7 trial snapshot is inconsistent");
            }
            validated.put(chamber, state);
        }
        // Publish only after validating the whole replacement, including the
        // final room. Failed recovery must not erase current outcomes.
        this.generation = generation;
        named = null;
        trials.clear();
        trials.putAll(validated);
    }

    public synchronized boolean owns(long expectedGeneration) {
        return generation == expectedGeneration && generation > 0L;
    }

    public synchronized int activeTrialCount() {
        return named == null ? trials.size() : named.trials().size();
    }

    public synchronized TrialState trial(int chamber) {
        return trials.get(chamber);
    }

    public synchronized Map<Integer, TrialState> snapshot() {
        return Map.copyOf(trials);
    }

    public synchronized boolean allComplete(long expectedGeneration) {
        if (named != null) return owns(expectedGeneration) && named.trials().values().stream()
                .allMatch(state -> state.stage() == Stage.COMPLETE);
        return owns(expectedGeneration) && !trials.isEmpty()
                && trials.values().stream().allMatch(state -> state.stage() == Stage.COMPLETE);
    }

    public synchronized ActionResult defeatWarden(long expectedGeneration, int chamber) {
        TrialState state = activeTrial(expectedGeneration, chamber, Trial.WARDEN);
        return state == null ? rejected(expectedGeneration, chamber, "WARDEN_NOT_ACTIVE")
                : complete(state, "WARDEN_DEFEATED");
    }

    /** A normal melee hit is deliberately not a valid Reflection seal action. */
    public synchronized ActionResult hitReflectionSeal(long expectedGeneration, int chamber,
                                                       int seal, boolean reflectedProjectile) {
        TrialState state = activeTrial(expectedGeneration, chamber, Trial.RIFT_REFLECTION);
        if (state == null) return rejected(expectedGeneration, chamber, "REFLECTION_NOT_ACTIVE");
        if (!reflectedProjectile) return result(false, state, "REFLECTED_PROJECTILE_REQUIRED");
        if (seal != state.progress()) return result(false, state, "SEAL_NOT_ACTIVE");
        int progress = state.progress() + 1;
        if (progress >= state.required()) {
            TrialState completed = new TrialState(chamber, state.trial(), Stage.COMPLETE,
                    progress, state.required());
            trials.put(chamber, completed);
            return result(true, completed, "REFLECTION_COMPLETE");
        }
        TrialState advanced = new TrialState(chamber, state.trial(), Stage.ACTIVE,
                progress, state.required());
        trials.put(chamber, advanced);
        return result(true, advanced, "ACTIVE_SEAL_REFLECTED");
    }

    /** Only a real charge collision against the next anchor removes armor. */
    public synchronized ActionResult hitJuggernautAnchor(long expectedGeneration, int chamber,
                                                          int anchor, boolean chargeCollision) {
        TrialState state = activeTrial(expectedGeneration, chamber, Trial.JUGGERNAUT);
        if (state == null) return rejected(expectedGeneration, chamber, "JUGGERNAUT_NOT_ARMORED");
        if (!chargeCollision) return result(false, state, "CHARGE_COLLISION_REQUIRED");
        if (anchor != state.progress()) return result(false, state, "ANCHOR_NOT_ACTIVE");
        int progress = state.progress() + 1;
        Stage stage = progress == state.required() ? Stage.EXPOSED : Stage.ACTIVE;
        TrialState advanced = new TrialState(chamber, state.trial(), stage,
                progress, state.required());
        trials.put(chamber, advanced);
        return result(true, advanced, stage == Stage.EXPOSED
                ? "JUGGERNAUT_EXPOSED" : "ARMOR_STAGE_BROKEN");
    }

    public synchronized boolean mayDamageJuggernaut(long expectedGeneration, int chamber) {
        TrialState state = trialForGeneration(expectedGeneration, chamber);
        return state != null && state.trial() == Trial.JUGGERNAUT
                && state.stage() == Stage.EXPOSED;
    }

    public synchronized ActionResult defeatJuggernaut(long expectedGeneration, int chamber) {
        TrialState state = trialForGeneration(expectedGeneration, chamber);
        if (state == null || state.trial() != Trial.JUGGERNAUT
                || state.stage() != Stage.EXPOSED) {
            return rejected(expectedGeneration, chamber, "JUGGERNAUT_NOT_EXPOSED");
        }
        return complete(state, "JUGGERNAUT_DEFEATED");
    }

    public synchronized ActionResult defeatHunter(long expectedGeneration, int chamber) {
        TrialState state = activeTrial(expectedGeneration, chamber, Trial.RIFT_HUNTER);
        return state == null ? rejected(expectedGeneration, chamber, "HUNTER_NOT_ACTIVE")
                : complete(state, "HUNTER_DEFEATED");
    }

    /** Reconcile a durable completed-room checkpoint written by the chamber graph. */
    public synchronized boolean restoreCompleted(long expectedGeneration, int chamber) {
        TrialState state = trialForGeneration(expectedGeneration, chamber);
        if (state == null) return false;
        int progress = state.trial() == Trial.RIFT_REFLECTION
                || state.trial() == Trial.JUGGERNAUT ? state.required() : state.progress();
        trials.put(chamber, new TrialState(chamber, state.trial(), Stage.COMPLETE,
                progress, state.required()));
        return true;
    }

    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        named = null;
        trials.clear();
    }

    private TrialState activeTrial(long expectedGeneration, int chamber, Trial expectedTrial) {
        TrialState state = trialForGeneration(expectedGeneration, chamber);
        return state != null && state.trial() == expectedTrial && state.stage() == Stage.ACTIVE
                ? state : null;
    }

    private TrialState trialForGeneration(long expectedGeneration, int chamber) {
        return owns(expectedGeneration) ? trials.get(chamber) : null;
    }

    private ActionResult complete(TrialState previous, String reason) {
        TrialState completed = new TrialState(previous.chamber(), previous.trial(),
                Stage.COMPLETE, previous.progress(), previous.required());
        trials.put(previous.chamber(), completed);
        return result(true, completed, reason);
    }

    private ActionResult rejected(long expectedGeneration, int chamber, String reason) {
        TrialState state = trialForGeneration(expectedGeneration, chamber);
        return new ActionResult(false, false, state == null ? null : state.stage(),
                state == null ? 0 : state.progress(), state == null ? 0 : state.required(), reason);
    }

    private ActionResult result(boolean accepted, TrialState state, String reason) {
        return new ActionResult(accepted, state.stage() == Stage.COMPLETE, state.stage(),
                state.progress(), state.required(), reason);
    }

    private static int requiredActions(Trial trial) {
        return trial == Trial.RIFT_REFLECTION ? REQUIRED_SEALS
                : trial == Trial.JUGGERNAUT ? REQUIRED_ANCHORS : 1;
    }

    private static boolean validStage(TrialState state) {
        return switch (state.trial()) {
            case WARDEN, RIFT_HUNTER -> state.stage() == Stage.ACTIVE && state.progress() == 0
                    || state.stage() == Stage.COMPLETE && state.progress() == 0;
            case RIFT_REFLECTION -> state.stage() == Stage.ACTIVE
                    && state.progress() < state.required()
                    || state.stage() == Stage.COMPLETE
                    && state.progress() == state.required();
            case JUGGERNAUT -> state.stage() == Stage.ACTIVE
                    && state.progress() < state.required()
                    || state.stage() == Stage.EXPOSED
                    && state.progress() == state.required()
                    || state.stage() == Stage.COMPLETE
                    && state.progress() == state.required();
        };
    }

    public record TrialState(int chamber, Trial trial, Stage stage, int progress, int required) {
        public TrialState {
            if (chamber < 0 || trial == null || stage == null || required < 1
                    || progress < 0 || progress > required) {
                throw new IllegalArgumentException("invalid Wave 7 trial state");
            }
        }
    }

    public record EchoCompletionReceipt(UUID eventId, long generation, long runtimeGeneration,
                                        int chamber, UUID duelId, UUID owner, UUID actorUuid,
                                        long deathSerial) {
        public EchoCompletionReceipt {
            if (eventId == null || generation <= 0L || generation > RealitySplitEchoDuelState.MAX_COUNTER
                    || runtimeGeneration < generation || runtimeGeneration > RealitySplitEchoDuelState.MAX_COUNTER
                    || chamber < 0 || chamber >= 4 || duelId == null || owner == null
                    || actorUuid == null || actorUuid.equals(owner) || deathSerial <= 0L
                    || deathSerial > RealitySplitEchoDuelState.MAX_COUNTER) {
                throw new IllegalArgumentException("invalid personal Echo completion receipt");
            }
        }
    }

    public record NamedTrialState(int chamber, NamedTrial trial, Stage stage, Set<UUID> owners,
                                  Map<UUID, EchoCompletionReceipt> echoReceipts) {
        public NamedTrialState {
            if (chamber < 0 || chamber >= 4 || trial == null || stage == null || stage == Stage.EXPOSED
                    || owners == null || owners.isEmpty() || owners.size() > 20
                    || owners.stream().anyMatch(owner -> owner == null)
                    || echoReceipts == null) {
                throw new IllegalArgumentException("invalid named Wave 7 trial state");
            }
            owners = Set.copyOf(owners);
            echoReceipts = Map.copyOf(echoReceipts);
            if (trial != NamedTrial.PERSONAL_ECHO && !echoReceipts.isEmpty()) {
                throw new IllegalArgumentException("only personal Echo owns owner completion receipts");
            }
            for (var entry : echoReceipts.entrySet()) {
                if (!owners.contains(entry.getKey()) || !entry.getKey().equals(entry.getValue().owner())
                        || entry.getValue().chamber() != chamber) {
                    throw new IllegalArgumentException("foreign personal Echo completion receipt");
                }
            }
            if (trial == NamedTrial.PERSONAL_ECHO
                    && (stage == Stage.COMPLETE) != (echoReceipts.size() == owners.size())) {
                throw new IllegalArgumentException("Echo room completion requires every owner receipt");
            }
        }
    }

    public record ActionResult(boolean accepted, boolean complete, Stage stage,
                               int progress, int required, String reason) {
        public ActionResult {
            reason = reason == null ? "" : reason;
        }
    }

    /** Trial order and original owners are durable; they are never reselected during restore. */
    public record NamedData(UUID eventId, long generation,
                             List<RealitySplitTrialController.NamedTrial> selectedLayout,
                             Map<Integer, RealitySplitTrialController.NamedTrialState> trials,
                             Map<Integer, Map<UUID, RealitySplitEchoDuelState.DuelRecord>> personalEchoDuels) {
        public NamedData(UUID eventId, long generation, List<NamedTrial> selectedLayout,
                         Map<Integer, NamedTrialState> trials) {
            this(eventId, generation, selectedLayout, trials, Map.of());
        }

        public NamedData {
            if (eventId == null || generation <= 0L || generation > RealitySplitEchoDuelState.MAX_COUNTER
                    || selectedLayout == null
                    || selectedLayout.isEmpty() || selectedLayout.size() > 4 || trials == null
                    || personalEchoDuels == null
                    || selectedLayout.stream().anyMatch(value -> value == null)
                    || new HashSet<>(selectedLayout).size() != selectedLayout.size()
                    || trials.size() != selectedLayout.size()) {
                throw new IllegalArgumentException("invalid named Wave 7 snapshot layout");
            }
            selectedLayout = List.copyOf(selectedLayout);
            Map<Integer, RealitySplitTrialController.NamedTrialState> validated = new LinkedHashMap<>();
            Map<Integer, Map<UUID, RealitySplitEchoDuelState.DuelRecord>> validatedDuels = new LinkedHashMap<>();
            Set<UUID> allDuels = new HashSet<>();
            Set<UUID> allActors = new HashSet<>();
            Set<UUID> allOwners = new HashSet<>();
            for (int room = 0; room < selectedLayout.size(); room++) {
                var state = trials.get(room);
                if (state == null || state.chamber() != room || state.trial() != selectedLayout.get(room)) {
                    throw new IllegalArgumentException("named Wave 7 room does not match selected layout");
                }
                for (UUID owner : state.owners()) {
                    if (!allOwners.add(owner)) throw new IllegalArgumentException("owner assigned to multiple rooms");
                }
                for (var receipt : state.echoReceipts().values()) {
                    if (!receipt.eventId().equals(eventId) || receipt.generation() != generation) {
                        throw new IllegalArgumentException("personal Echo receipt belongs to a stale attempt");
                    }
                }
                Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomDuels = personalEchoDuels.get(room);
                if (state.trial() == NamedTrial.PERSONAL_ECHO) {
                    // Keep the derived physical layout representable during snapshot restore.
                    Wave7EchoDuelCellPolicy.plan(room, selectedLayout.size(), state.owners());
                    if (roomDuels == null || roomDuels.size() != state.owners().size()
                            || !roomDuels.keySet().equals(state.owners())) {
                        throw new IllegalArgumentException("every Echo owner requires exactly one duel record");
                    }
                    Map<UUID, RealitySplitEchoDuelState.DuelRecord> roomCopy = new LinkedHashMap<>();
                    for (UUID owner : state.owners()) {
                        var duel = roomDuels.get(owner);
                        if (duel == null || !duel.eventId().equals(eventId)
                                || duel.attemptGeneration() != generation || duel.room() != room
                                || !duel.ownerUuid().equals(owner) || !allDuels.add(duel.duelId())
                                || duel.actor() != null && !allActors.add(duel.actor().actorUuid())) {
                            throw new IllegalArgumentException("personal Echo duel identity is missing, duplicated or foreign");
                        }
                        EchoCompletionReceipt receipt = state.echoReceipts().get(owner);
                        if ((duel.lifecycle() == RealitySplitEchoDuelState.Lifecycle.COMPLETE) != (receipt != null)) {
                            throw new IllegalArgumentException("Echo completion requires its matching completed actor");
                        }
                        if (receipt != null && (!receipt.eventId().equals(eventId)
                                || receipt.generation() != generation || receipt.runtimeGeneration() != duel.runtimeGeneration()
                                || receipt.chamber() != room || !receipt.duelId().equals(duel.duelId())
                                || !receipt.owner().equals(owner) || duel.actor() == null
                                || !receipt.actorUuid().equals(duel.actor().actorUuid())
                                || duel.actor().lifecycle() != RealitySplitEchoDuelState.ActorLifecycle.DEAD
                                || receipt.deathSerial() != duel.actor().deathSerial())) {
                            throw new IllegalArgumentException("personal Echo receipt is not bound to its completed actor");
                        }
                        roomCopy.put(owner, duel);
                    }
                    validatedDuels.put(room, Map.copyOf(roomCopy));
                } else if (roomDuels != null || !state.echoReceipts().isEmpty()) {
                    throw new IllegalArgumentException("non-Echo room cannot contain personal duel state");
                }
                validated.put(room, state);
            }
            for (Integer room : personalEchoDuels.keySet()) {
                if (room == null || room < 0 || room >= selectedLayout.size()
                        || selectedLayout.get(room) != NamedTrial.PERSONAL_ECHO) {
                    throw new IllegalArgumentException("Echo duel state is attached to a non-Echo or unknown room");
                }
            }
            if (allOwners.size() > 20 || selectedLayout.size() != Math.min(4, allOwners.size())) {
                throw new IllegalArgumentException("named Wave 7 room count does not match roster");
            }
            if (allDuels.stream().anyMatch(allOwners::contains)
                    || allActors.stream().anyMatch(allOwners::contains)) {
                throw new IllegalArgumentException("private Echo identities cannot alias roster owners");
            }
            trials = Map.copyOf(validated);
            personalEchoDuels = Map.copyOf(validatedDuels);
        }
    }
}
