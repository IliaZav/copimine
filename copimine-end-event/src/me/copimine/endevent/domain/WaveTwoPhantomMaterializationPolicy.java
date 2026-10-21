package me.copimine.endevent.domain;

import java.util.HashSet;
import java.util.Set;

/** Pure state transitions for activating one Wave 2 phantom at a time as a real mob. */
public final class WaveTwoPhantomMaterializationPolicy {
    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_PHANTOM_ID_LENGTH = 128;

    private WaveTwoPhantomMaterializationPolicy() { }

    public enum Phase {
        WARNING,
        TARGETABLE,
        HIT,
        CANCELLED
    }

    public enum Action {
        WARNING_STARTED,
        WAITING_FOR_WARNING,
        MATERIALIZE_ENTITY,
        HIT_ACCEPTED,
        DUPLICATE_ACTIVATION,
        DUPLICATE_HIT,
        CANCELLED,
        REJECTED
    }

    /** Event generation and active group that own the candidate. */
    public record Fence(String eventId, long generation, int groupNumber) { }

    /**
     * The server supplies one selected phantom and its arena-position validation result.
     * The client must not author this identity, position decision, or activation.
     */
    public record Candidate(Fence fence, String phantomId, boolean validArenaPosition) { }

    /** Live pressure, late-activation allowance, and total Wave 2 reserve from the integration boundary. */
    public record Budget(int activePressureSlots, int maxPressureSlots,
                         int remainingActivationBudget, int remainingTotalBudget) {
        private boolean canMaterializeOne() {
            if (activePressureSlots < 0 || maxPressureSlots < 0
                    || activePressureSlots >= maxPressureSlots || remainingActivationBudget <= 0
                    || remainingTotalBudget <= 0) {
                return false;
            }
            return lateReinforcementCount(maxPressureSlots - activePressureSlots,
                    Math.min(remainingActivationBudget, remainingTotalBudget)) > 0;
        }
    }

    /** Immutable state retained by the caller for the current group, including IDs already consumed. */
    public record State(Fence fence, String phantomId, Phase phase, Set<String> usedPhantomIds) {
        public State {
            if (usedPhantomIds == null) {
                throw new IllegalArgumentException("Used phantom IDs are required");
            }
            usedPhantomIds = Set.copyOf(usedPhantomIds);
        }
    }

    /** The materialization action consumes exactly one caller-owned mob-budget slot. */
    public record Decision(State state, Action action, int entityBudgetCost) {
        public Decision {
            if (action == null || entityBudgetCost < 0 || entityBudgetCost > 1) {
                throw new IllegalArgumentException("Invalid phantom materialization decision");
            }
        }
    }

    /**
     * Start the warning for one safe phantom. Repeated activation of a used
     * phantom is idempotent; another ID is allowed after HIT or CANCELLED.
     */
    public static Decision begin(State current, Fence activeFence, Candidate candidate, Budget budget) {
        if (!validFence(activeFence) || candidate == null || !validFence(candidate.fence())
                || !activeFence.equals(candidate.fence()) || !validPhantomId(candidate.phantomId())
                || !candidate.validArenaPosition() || budget == null || !budget.canMaterializeOne()) {
            return rejected(current);
        }
        if (current != null) {
            if (!validState(current)) return rejected(current);
            if (current.phase() == Phase.WARNING || current.phase() == Phase.TARGETABLE) {
                if (sameLifecycle(current.fence(), activeFence)
                        && current.usedPhantomIds().contains(candidate.phantomId())) {
                    return new Decision(current, Action.DUPLICATE_ACTIVATION, 0);
                }
                return rejected(current);
            }
            if (!sameLifecycle(current.fence(), activeFence)) return rejected(current);
            if (current.usedPhantomIds().contains(candidate.phantomId())) {
                return new Decision(current, Action.DUPLICATE_ACTIVATION, 0);
            }
            Set<String> used = new HashSet<>(current.usedPhantomIds());
            used.add(candidate.phantomId());
            State warning = new State(activeFence, candidate.phantomId(), Phase.WARNING, used);
            return new Decision(warning, Action.WARNING_STARTED, 0);
        }
        State warning = new State(activeFence, candidate.phantomId(), Phase.WARNING,
                Set.of(candidate.phantomId()));
        return new Decision(warning, Action.WARNING_STARTED, 0);
    }

    /**
     * Complete only after the adapter's chosen warning reports completion.
     * Duration and animation scheduling remain caller-owned; position and
     * budget are revalidated immediately before the entity becomes targetable.
     */
    public static Decision finishWarning(State current, Fence activeFence,
                                         boolean warningComplete, boolean validArenaPosition,
                                         Budget budget) {
        if (!owns(current, activeFence) || current.phase() != Phase.WARNING) {
            return rejected(current);
        }
        if (!validArenaPosition || budget == null || !budget.canMaterializeOne()) {
            State cancelled = new State(current.fence(), current.phantomId(), Phase.CANCELLED,
                    current.usedPhantomIds());
            return new Decision(cancelled, Action.CANCELLED, 0);
        }
        if (!warningComplete) return new Decision(current, Action.WAITING_FOR_WARNING, 0);
        State targetable = new State(current.fence(), current.phantomId(), Phase.TARGETABLE,
                current.usedPhantomIds());
        return new Decision(targetable, Action.MATERIALIZE_ENTITY, 1);
    }

    /** Accept one terminal hit only for the owned, targetable materialization. */
    public static Decision acceptHit(State current, Fence activeFence, String phantomId) {
        if (current != null && owns(current, activeFence)
                && validPhantomId(phantomId) && current.phantomId().equals(phantomId)) {
            if (current.phase() == Phase.HIT) {
                return new Decision(current, Action.DUPLICATE_HIT, 0);
            }
            if (current.phase() == Phase.TARGETABLE) {
                State hit = new State(current.fence(), current.phantomId(), Phase.HIT,
                        current.usedPhantomIds());
                return new Decision(hit, Action.HIT_ACCEPTED, 0);
            }
        }
        return rejected(current);
    }

    /**
     * True while a delayed warning callback still owns the activation it captured.
     * CANCELLED is included because the callback may first finish the warning
     * transaction and then clear that same presentation.
     */
    public static boolean ownsActivation(State current, Fence fence, String phantomId) {
        return owns(current, fence) && validPhantomId(phantomId)
                && current.phantomId().equals(phantomId)
                && (current.phase() == Phase.WARNING || current.phase() == Phase.CANCELLED);
    }

    /** The current bounded pressure and total reserve cap any late additions at two. */
    public static int lateReinforcementCount(int maxPressureSlots, int remainingBudget) {
        return WaveTwoHuntPolicy.lateReinforcementCount(maxPressureSlots, remainingBudget);
    }

    private static boolean owns(State state, Fence activeFence) {
        return state != null && state.fence() != null && validFence(activeFence)
                && state.fence().equals(activeFence) && validPhantomId(state.phantomId())
                && state.phase() != null && state.usedPhantomIds() != null
                && state.usedPhantomIds().contains(state.phantomId());
    }

    private static boolean validState(State state) {
        return state.fence() != null && validFence(state.fence())
                && validPhantomId(state.phantomId()) && state.phase() != null
                && state.usedPhantomIds().contains(state.phantomId());
    }

    private static boolean sameLifecycle(Fence left, Fence right) {
        return left != null && right != null && left.eventId().equals(right.eventId())
                && left.generation() == right.generation();
    }

    private static boolean validFence(Fence fence) {
        return fence != null && fence.eventId() != null && !fence.eventId().isBlank()
                && fence.eventId().equals(fence.eventId().trim())
                && fence.eventId().length() <= MAX_EVENT_ID_LENGTH
                && fence.generation() > 0L && fence.groupNumber() > 0;
    }

    private static boolean validPhantomId(String phantomId) {
        return phantomId != null && !phantomId.isBlank()
                && phantomId.equals(phantomId.trim())
                && phantomId.length() <= MAX_PHANTOM_ID_LENGTH;
    }

    private static Decision rejected(State current) {
        return new Decision(current, Action.REJECTED, 0);
    }
}
