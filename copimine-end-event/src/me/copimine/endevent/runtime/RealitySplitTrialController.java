package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import me.copimine.endevent.domain.ChamberIsolationPolicy;

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
        List<NamedTrial> pool = new ArrayList<>(List.of(NamedTrial.values()));
        List<NamedTrial> selected = new ArrayList<>();
        while (selected.size() < assignment.chamberCount()) {
            selected.add(pool.remove(random.nextInt(pool.size())));
        }
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
        for (int room = 0; room < selectedLayout.size(); room++) {
            initial.put(room, new NamedTrialState(room, selectedLayout.get(room), Stage.ACTIVE,
                    assignment.playersIn(room), Map.of()));
        }
        restoreNamed(eventId, generation, assignment,
                new NamedData(eventId, generation, selectedLayout, initial));
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
        }
        // NamedData has already validated its entire immutable layout and all receipts.
        generation = expectedGeneration;
        named = restored;
        trials.clear();
    }

    public synchronized NamedTrialState namedTrial(int chamber) {
        return named == null ? null : named.trials().get(chamber);
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

    /** Each owner's own Echo supplies one receipt; a multi-owner room needs every receipt. */
    public synchronized boolean completePersonalEcho(UUID eventId, long expectedGeneration,
                                                      int chamber, UUID owner) {
        if (!ownsNamed(eventId, expectedGeneration) || owner == null) return false;
        NamedTrialState state = named.trials().get(chamber);
        if (state == null || state.trial() != NamedTrial.PERSONAL_ECHO
                || state.stage() != Stage.ACTIVE || !state.owners().contains(owner)
                || state.echoReceipts().containsKey(owner)) return false;
        Map<UUID, EchoCompletionReceipt> receipts = new LinkedHashMap<>(state.echoReceipts());
        receipts.put(owner, new EchoCompletionReceipt(eventId, expectedGeneration, chamber, owner));
        replaceNamedTrial(new NamedTrialState(chamber, state.trial(),
                receipts.size() == state.owners().size() ? Stage.COMPLETE : Stage.ACTIVE,
                state.owners(), receipts));
        return true;
    }

    private void replaceNamedTrial(NamedTrialState replacement) {
        Map<Integer, NamedTrialState> updated = new LinkedHashMap<>(named.trials());
        updated.put(replacement.chamber(), replacement);
        named = new NamedData(named.eventId(), generation,
                named.selectedLayout(), updated);
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

    public record EchoCompletionReceipt(UUID eventId, long generation, int chamber, UUID owner) {
        public EchoCompletionReceipt {
            if (eventId == null || generation <= 0L || chamber < 0 || chamber >= 4 || owner == null) {
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
                             Map<Integer, RealitySplitTrialController.NamedTrialState> trials) {
        public NamedData {
            if (eventId == null || generation <= 0L || selectedLayout == null
                    || selectedLayout.isEmpty() || selectedLayout.size() > 4 || trials == null
                    || selectedLayout.stream().anyMatch(value -> value == null)
                    || new HashSet<>(selectedLayout).size() != selectedLayout.size()
                    || trials.size() != selectedLayout.size()) {
                throw new IllegalArgumentException("invalid named Wave 7 snapshot layout");
            }
            selectedLayout = List.copyOf(selectedLayout);
            Map<Integer, RealitySplitTrialController.NamedTrialState> validated = new LinkedHashMap<>();
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
                validated.put(room, state);
            }
            if (allOwners.size() > 20 || selectedLayout.size() != Math.min(4, allOwners.size())) {
                throw new IllegalArgumentException("named Wave 7 room count does not match roster");
            }
            trials = Map.copyOf(validated);
        }
    }
}
