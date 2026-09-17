package me.copimine.endevent.domain;

import java.util.UUID;

/** Pure state machine for the server-owned Wave 6 Ritual Sphere. */
public final class RitualSphereEncounterPolicy {
    private RitualSphereEncounterPolicy() {
    }

    public static State waiting(long generation, int participants) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("ritual sphere generation is required");
        }
        return new State(generation, null, RitualSphereScalingPolicy.forPlayers(participants),
                0, 0, -1L);
    }

    /** Compatibility entry point for callers that already have a captured prisoner. */
    public static State initial(long generation, UUID prisoner, int participants, long nowMillis) {
        if (prisoner == null || nowMillis < 0L) {
            throw new IllegalArgumentException("ritual sphere prisoner and capture time are required");
        }
        return capture(waiting(generation, participants), prisoner, nowMillis);
    }

    /** Capture the first prisoner once; later capture attempts are no-ops. */
    public static State capture(State state, UUID prisoner, long nowMillis) {
        if (state == null) {
            throw new IllegalArgumentException("ritual sphere state is required");
        }
        if (hasCaptured(state)) {
            return state;
        }
        if (prisoner == null || nowMillis < 0L) {
            throw new IllegalArgumentException("ritual sphere prisoner and capture time are required");
        }
        return new State(state.generation(), prisoner, state.profile(),
                state.successfulDrains(), state.intensity(), nowMillis);
    }

    /** Replace a captured participant without resetting the drain clock. */
    public static State reassignCapturedPrisoner(State state, UUID prisoner) {
        if (!hasCaptured(state) || prisoner == null) {
            throw new IllegalArgumentException("captured ritual sphere state and replacement are required");
        }
        if (prisoner.equals(state.prisoner())) {
            return state;
        }
        return new State(state.generation(), prisoner, state.profile(),
                state.successfulDrains(), state.intensity(), state.lastDrainMillis());
    }

    public static boolean hasCaptured(State state) {
        return state != null && state.prisoner() != null;
    }

    public static boolean drainDue(State state, long nowMillis) {
        return hasCaptured(state) && state.generation() > 0L
                && RitualPrisonerHealthPolicy.drainDue(nowMillis, state.lastDrainMillis());
    }

    public static DrainTransition advanceDrain(State state, double currentHealth, long nowMillis) {
        if (state == null || !drainDue(state, nowMillis)) {
            return new DrainTransition(state, new RitualPrisonerHealthPolicy.DrainResult(
                    Math.max(RitualPrisonerHealthPolicy.MIN_HEALTH,
                            Double.isFinite(currentHealth) ? currentHealth
                                    : RitualPrisonerHealthPolicy.MIN_HEALTH),
                    0.0D, 0), false);
        }
        RitualPrisonerHealthPolicy.DrainResult result = RitualPrisonerHealthPolicy.drain(currentHealth);
        int successfulDrains = state.successfulDrains() + result.intensityGain();
        int intensity = RitualSphereScalingPolicy.intensityForSuccessfulDrains(successfulDrains);
        State next = new State(state.generation(), state.prisoner(), state.profile(),
                successfulDrains, intensity, nowMillis);
        return new DrainTransition(next, result, result.intensityGain() > 0);
    }

    public static boolean abilityEnabled(State state, int casterSlot, boolean casterAlive) {
        return hasCaptured(state) && casterAlive && casterSlot >= 0
                && casterSlot < Ability.values().length;
    }

    public static boolean shouldComplete(int livingCasters, int livingGuards) {
        return Math.max(0, livingCasters) == 0 && Math.max(0, livingGuards) == 0;
    }

    public enum Ability {
        PROJECTILES,
        INFECTED_ZONE,
        REVERSE_MOVEMENT,
        CONTROL_SWAP
    }

    public record State(long generation, UUID prisoner,
                        RitualSphereScalingPolicy.Profile profile,
                        int successfulDrains, int intensity, long lastDrainMillis) {
        public State {
            if (generation <= 0L || profile == null
                    || successfulDrains < 0 || intensity < 0
                    || intensity > RitualSphereScalingPolicy.MAX_INTENSITY
                    || (prisoner == null ? lastDrainMillis >= 0L : lastDrainMillis < 0L)) {
                throw new IllegalArgumentException("invalid ritual sphere state");
            }
            if (prisoner == null && (successfulDrains != 0 || intensity != 0
                    || lastDrainMillis != -1L)) {
                throw new IllegalArgumentException("waiting ritual sphere state must be empty");
            }
            successfulDrains = Math.min(RitualSphereScalingPolicy.MAX_INTENSITY,
                    successfulDrains);
            intensity = RitualSphereScalingPolicy.intensityForSuccessfulDrains(intensity);
        }
    }

    public record DrainTransition(State state,
                                  RitualPrisonerHealthPolicy.DrainResult health,
                                  boolean applied) {
        public DrainTransition {
            if (health == null) {
                throw new IllegalArgumentException("ritual drain result is required");
            }
        }
    }
}
