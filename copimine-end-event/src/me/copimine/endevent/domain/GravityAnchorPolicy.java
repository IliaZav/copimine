package me.copimine.endevent.domain;

import java.util.UUID;

/** Pure target and phase contract for the Wave 6 Gravity Anchor cast. */
public final class GravityAnchorPolicy {
    public enum Phase {
        IDLE,
        TELEGRAPH,
        ANCHOR,
        PULL,
        EXPLOSION,
        LANDING,
        COMPLETE
    }

    /** A captured world-space anchor point. Spell timing and movement stay in the adapter. */
    public record AnchorPoint(double x, double y, double z) {
        public AnchorPoint {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("finite Gravity Anchor point required");
            }
        }
    }

    /** Target identity and position are frozen for the full cast to prevent retarget drift. */
    public record State(long generation, Phase phase, UUID targetPlayer,
                        AnchorPoint anchor, UUID crystalEntity) {
        public State {
            if (phase == null) {
                throw new IllegalArgumentException("Gravity Anchor phase required");
            }
            boolean hasTarget = targetPlayer != null && anchor != null;
            boolean hasPartialTarget = (targetPlayer == null) != (anchor == null);
            if (hasPartialTarget
                    || phase == Phase.IDLE && (hasTarget || crystalEntity != null)
                    || phase == Phase.TELEGRAPH && (!hasTarget || crystalEntity != null)
                    || phase != Phase.IDLE && phase != Phase.TELEGRAPH
                    && (!hasTarget || crystalEntity == null)) {
                throw new IllegalArgumentException("Gravity Anchor state has invalid target or crystal ownership");
            }
        }
    }

    public record StartResult(boolean accepted, State state) { }

    private GravityAnchorPolicy() { }

    public static State idle(long generation) {
        return new State(generation, Phase.IDLE, null, null, null);
    }

    /**
     * Begin only for an active wave, an eligible target, and an available shared spell slot.
     * No undocumented telegraph, pull, explosion, or landing durations are imposed here.
     */
    public static StartResult start(State current,
                                    long eventGeneration,
                                    UUID targetPlayer,
                                    AnchorPoint anchor,
                                    boolean waveActive,
                                    boolean targetEligible,
                                    boolean sharedSpellSlotFree) {
        State state = current == null ? idle(eventGeneration) : current;
        if (state.generation() != eventGeneration
                || state.phase() != Phase.IDLE && state.phase() != Phase.COMPLETE
                || targetPlayer == null
                || anchor == null
                || !waveActive
                || !targetEligible
                || !sharedSpellSlotFree) {
            return new StartResult(false, state);
        }
        return new StartResult(true,
                new State(eventGeneration, Phase.TELEGRAPH, targetPlayer, anchor, null));
    }

    /** Bind the one real End Crystal created for this cast before pull begins. */
    public static State anchorSpawned(State state, long eventGeneration, UUID crystalEntity) {
        if (state == null || state.generation() != eventGeneration
                || state.phase() != Phase.TELEGRAPH || crystalEntity == null) {
            return state;
        }
        return new State(eventGeneration, Phase.ANCHOR, state.targetPlayer(), state.anchor(), crystalEntity);
    }

    /** Advance exactly one declared phase; invalid or stale transitions leave state untouched. */
    public static State advanceTo(State state, long eventGeneration, Phase nextPhase) {
        if (state == null || state.generation() != eventGeneration || nextPhase == null) {
            return state;
        }
        Phase expected = switch (state.phase()) {
            case IDLE, COMPLETE -> null;
            case TELEGRAPH -> null;
            case ANCHOR -> Phase.PULL;
            case PULL -> Phase.EXPLOSION;
            case EXPLOSION -> Phase.LANDING;
            case LANDING -> Phase.COMPLETE;
        };
        if (nextPhase != expected) {
            return state;
        }
        return new State(eventGeneration, nextPhase, state.targetPlayer(), state.anchor(), state.crystalEntity());
    }

    public static State clear(State state, long eventGeneration) {
        if (state == null || state.generation() != eventGeneration) {
            return state;
        }
        return idle(eventGeneration);
    }
}
