package me.copimine.endevent.domain;

import java.util.UUID;

/** Pure prisoner and roster state for the server-owned Wave 6 Ritual Sphere. */
public final class RitualSphereEncounterPolicy {
    private RitualSphereEncounterPolicy() {
    }

    public static State waiting(long generation, int participants) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("ritual sphere generation is required");
        }
        return new State(generation, null, RitualSphereScalingPolicy.forPlayers(participants));
    }

    /** Capture the first prisoner once; later capture attempts are no-ops. */
    public static State capture(State state, UUID prisoner) {
        if (state == null) {
            throw new IllegalArgumentException("ritual sphere state is required");
        }
        if (hasCaptured(state)) {
            return state;
        }
        if (prisoner == null) {
            throw new IllegalArgumentException("ritual sphere prisoner is required");
        }
        return new State(state.generation(), prisoner, state.profile());
    }

    /** Replace an unavailable prisoner without changing encounter scaling. */
    public static State reassignCapturedPrisoner(State state, UUID prisoner) {
        if (!hasCaptured(state) || prisoner == null) {
            throw new IllegalArgumentException("captured ritual sphere state and replacement are required");
        }
        if (prisoner.equals(state.prisoner())) {
            return state;
        }
        return new State(state.generation(), prisoner, state.profile());
    }

    public static boolean hasCaptured(State state) {
        return state != null && state.prisoner() != null;
    }

    public static boolean shouldComplete(int livingCasters, int livingGuards) {
        return Math.max(0, livingCasters) == 0 && Math.max(0, livingGuards) == 0;
    }

    public record State(long generation, UUID prisoner,
                        RitualSphereScalingPolicy.Profile profile) {
        public State {
            if (generation <= 0L || profile == null) {
                throw new IllegalArgumentException("invalid ritual sphere state");
            }
        }
    }
}
