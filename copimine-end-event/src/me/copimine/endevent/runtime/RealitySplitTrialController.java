package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
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

    // Stable legacy identities. Enum ordering must never migrate saved rooms.
    private static final Trial[] ROOM_TRIALS = {
            Trial.WARDEN, Trial.RIFT_REFLECTION, Trial.JUGGERNAUT, Trial.RIFT_HUNTER
    };
    private static final int REQUIRED_SEALS = 3;
    private static final int REQUIRED_ANCHORS = 3;

    private long generation = Long.MIN_VALUE;
    private final Map<Integer, TrialState> trials = new LinkedHashMap<>();

    /** Activate one appropriate trial per assigned chamber, up to four. */
    public synchronized void begin(long generation,
                                   ChamberIsolationPolicy.Assignment assignment) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (assignment == null || assignment.chamberCount() < 1
                || assignment.chamberCount() > ROOM_TRIALS.length) {
            throw new IllegalArgumentException("at least one valid trial room is required");
        }
        this.generation = generation;
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
        trials.clear();
        trials.putAll(validated);
    }

    public synchronized boolean owns(long expectedGeneration) {
        return generation == expectedGeneration && generation > 0L;
    }

    public synchronized int activeTrialCount() {
        return trials.size();
    }

    public synchronized TrialState trial(int chamber) {
        return trials.get(chamber);
    }

    public synchronized Map<Integer, TrialState> snapshot() {
        return Map.copyOf(trials);
    }

    public synchronized boolean allComplete(long expectedGeneration) {
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

    public record ActionResult(boolean accepted, boolean complete, Stage stage,
                               int progress, int required, String reason) {
        public ActionResult {
            reason = reason == null ? "" : reason;
        }
    }
}
