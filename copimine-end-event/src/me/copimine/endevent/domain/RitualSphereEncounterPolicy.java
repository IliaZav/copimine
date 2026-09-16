package me.copimine.endevent.domain;

import java.util.UUID;

/** Pure state machine for the server-owned Wave 6 Ritual Sphere. */
public final class RitualSphereEncounterPolicy {
    private RitualSphereEncounterPolicy() {
    }

    public static State initial(long generation, UUID prisoner, int participants, long nowMillis) {
        if (generation <= 0L || prisoner == null || nowMillis < 0L) {
            throw new IllegalArgumentException("ritual sphere identity and start time are required");
        }
        return new State(generation, prisoner,
                RitualSphereScalingPolicy.forPlayers(participants), 0, 0, nowMillis);
    }

    public static boolean drainDue(State state, long nowMillis) {
        return state != null && state.generation() > 0L
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
        return state != null && casterAlive && casterSlot >= 0
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
            if (generation <= 0L || prisoner == null || profile == null
                    || successfulDrains < 0 || intensity < 0
                    || intensity > RitualSphereScalingPolicy.MAX_INTENSITY
                    || lastDrainMillis < 0L) {
                throw new IllegalArgumentException("invalid ritual sphere state");
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
