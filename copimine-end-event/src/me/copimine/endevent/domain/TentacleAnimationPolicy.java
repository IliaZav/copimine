package me.copimine.endevent.domain;

import java.util.List;
import java.util.Locale;

/**
 * Server-owned timing and lifecycle contract for the End Rift tentacle.
 *
 * The names are deliberately explicit about whether the tentacle is emerging,
 * ready, recovering or dying. Unknown persisted/client values fail closed to
 * the neutral READY pose.
 */
public final class TentacleAnimationPolicy {
    public enum Kind {
        PERMANENT,
        TEMPORARY,
        /** A short-lived eruption whose only job is to emerge below a player. */
        UNDER_PLAYER
    }

    public enum State {
        EMERGING,
        READY,
        TELEGRAPH_GRAB,
        GRAB_SUCCESS,
        HOLD,
        THROW,
        MISS_RECOVERY,
        HIT_RECOVERY,
        DYING,
        DEAD_RESPAWN,
        RETRACT,
        SPAWN_UNDER_PLAYER,
        SHIELD_CHANNEL,
        RECOVERY
    }

    public enum Marker {
        CONTACT,
        HOLD_LOCK,
        THROW_RELEASE,
        RECOVERY_START,
        HIDE_BELOW_FLOOR
    }

    private static final List<Marker> GRAB_MARKERS = List.of(
            Marker.CONTACT, Marker.HOLD_LOCK, Marker.THROW_RELEASE,
            Marker.RECOVERY_START, Marker.HIDE_BELOW_FLOOR);

    private TentacleAnimationPolicy() {
    }

    public static int durationTicks(State state) {
        State canonical = canonical(state);
        if (canonical == null) {
            return 20;
        }
        return switch (canonical) {
            case READY -> 60;                // 3.0 seconds, looping
            case EMERGING -> 18;             // 0.9 seconds
            case TELEGRAPH_GRAB -> 20;      // 1.0 second
            case GRAB_SUCCESS -> 14;        // 0.7 seconds
            case HOLD -> 20;                // 1.0 second loop
            case THROW -> 12;               // 0.6 seconds
            case MISS_RECOVERY -> 14;       // 0.7 seconds
            case HIT_RECOVERY -> 7;         // 0.35 seconds
            case DYING -> 20;                // 1.0 second
            case DEAD_RESPAWN -> 40;        // bounded hidden/respawn hold
            case RETRACT -> 16;             // 0.8 seconds
            case SPAWN_UNDER_PLAYER -> 7;   // 0.35 seconds
            case SHIELD_CHANNEL -> 40;      // 2.0 seconds, looping
            case RECOVERY -> 16;            // 0.8 seconds
        };
    }

    public static boolean loops(State state) {
        State canonical = canonical(state);
        return canonical == State.READY || canonical == State.HOLD
                || canonical == State.SHIELD_CHANNEL;
    }

    public static List<Marker> grabMarkers() {
        return GRAB_MARKERS;
    }

    /** Marker offset in the animation that owns the marker. */
    public static int markerTick(State state, Marker marker) {
        if (state == null || marker == null) {
            return -1;
        }
        State canonical = canonical(state);
        return switch (marker) {
            case CONTACT -> canonical == State.GRAB_SUCCESS ? 8 : -1;
            case HOLD_LOCK -> canonical == State.GRAB_SUCCESS ? 12 : -1;
            case THROW_RELEASE -> canonical == State.THROW ? 8 : -1;
            case RECOVERY_START -> canonical == State.RECOVERY ? 0 : -1;
            case HIDE_BELOW_FLOOR -> canonical == State.DYING ? 16
                    : canonical == State.RETRACT ? durationTicks(State.RETRACT) : -1;
        };
    }

    /** Context-aware server/client transition contract. */
    public static State next(Kind kind, State current, boolean grabSucceeded) {
        Kind safeKind = kind == null ? Kind.TEMPORARY : kind;
        State canonical = canonical(current);
        if (canonical == null) {
            return State.READY;
        }
        return switch (canonical) {
            case EMERGING -> safeKind == Kind.PERMANENT ? State.SHIELD_CHANNEL
                    : safeKind == Kind.UNDER_PLAYER ? State.RECOVERY : State.READY;
            case READY -> safeKind == Kind.TEMPORARY ? State.TELEGRAPH_GRAB : State.READY;
            case TELEGRAPH_GRAB -> grabSucceeded ? State.GRAB_SUCCESS : State.MISS_RECOVERY;
            case GRAB_SUCCESS -> State.HOLD;
            case HOLD -> State.THROW;
            case THROW, MISS_RECOVERY, HIT_RECOVERY -> State.RECOVERY;
            case RECOVERY -> safeKind == Kind.PERMANENT ? State.SHIELD_CHANNEL : State.RETRACT;
            case SHIELD_CHANNEL -> State.SHIELD_CHANNEL;
            case DYING -> State.DEAD_RESPAWN;
            case DEAD_RESPAWN -> safeKind == Kind.PERMANENT
                    ? State.SHIELD_CHANNEL : State.RETRACT;
            case RETRACT -> State.RETRACT;
            case SPAWN_UNDER_PLAYER -> safeKind == Kind.UNDER_PLAYER
                    ? State.RECOVERY : State.RETRACT;
        };
    }

    public static State next(State current, boolean grabSucceeded) {
        return next(Kind.TEMPORARY, current, grabSucceeded);
    }

    public static double progress(State state, long elapsedTicks) {
        int duration = Math.max(1, durationTicks(state));
        return Math.max(0.0D, Math.min(1.0D,
                Math.max(0L, elapsedTicks) / (double) duration));
    }

    /** Return the canonical lifecycle state used by the server and client. */
    public static State canonical(State state) {
        return state;
    }

    /** Parse a persisted/wire value and fail closed to the neutral pose. */
    public static State fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return State.READY;
        }
        try {
            return State.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return State.READY;
        }
    }

    /** Only canonical names are emitted in PDC and END_ENTITY_PHASE. */
    public static String wireName(State state) {
        State safe = canonical(state);
        return (safe == null ? State.READY : safe).name();
    }
}
