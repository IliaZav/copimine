package me.copimine.endevent.domain;

import java.util.List;

/**
 * Timing contract for the End Rift tentacle asset.  The client may interpolate
 * poses, but these state names and gameplay markers are owned by the server.
 */
public final class TentacleAnimationPolicy {
    public enum State {
        IDLE,
        EMERGE,
        TELEGRAPH_GRAB,
        GRAB_SUCCESS,
        HOLD,
        THROW,
        GRAB_MISS,
        HURT,
        DEATH,
        RETRACT,
        SPAWN_UNDER_PLAYER,
        SHIELD_CHANNEL
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
        if (state == null) {
            return 20;
        }
        return switch (state) {
            case IDLE -> 60;                 // 3.0 seconds, looping
            case EMERGE -> 18;               // 0.9 seconds
            case TELEGRAPH_GRAB -> 20;       // 1.0 second
            case GRAB_SUCCESS -> 14;         // 0.7 seconds
            case HOLD -> 20;                 // 1.0 second loop
            case THROW -> 12;                // 0.6 seconds
            case GRAB_MISS -> 14;             // 0.7 seconds
            case HURT -> 7;                   // 0.35 seconds
            case DEATH -> 20;                 // 1.0 second
            case RETRACT -> 16;               // 0.8 seconds
            case SPAWN_UNDER_PLAYER -> 7;     // 0.35 seconds
            case SHIELD_CHANNEL -> 40;        // 2.0 seconds, looping
        };
    }

    public static boolean loops(State state) {
        return state == State.IDLE || state == State.HOLD || state == State.SHIELD_CHANNEL;
    }

    public static List<Marker> grabMarkers() {
        return GRAB_MARKERS;
    }

    /**
     * Timeline offsets are measured from the beginning of the complete server
     * grab attempt.  They deliberately match the supplied artist brief: 0,
     * about 0.3s, 1.0s, 1.2s and 1.8s.
     */
    public static int markerTick(Marker marker) {
        if (marker == null) {
            return -1;
        }
        return switch (marker) {
            case CONTACT -> 0;
            case HOLD_LOCK -> 6;
            case THROW_RELEASE -> 20;
            case RECOVERY_START -> 24;
            case HIDE_BELOW_FLOOR -> 36;
        };
    }

    public static State next(State current, boolean grabSucceeded) {
        if (current == null) {
            return State.IDLE;
        }
        return switch (current) {
            case IDLE -> State.TELEGRAPH_GRAB;
            case EMERGE -> State.IDLE;
            case TELEGRAPH_GRAB -> grabSucceeded ? State.GRAB_SUCCESS : State.GRAB_MISS;
            case GRAB_SUCCESS -> State.HOLD;
            case HOLD -> State.THROW;
            case THROW, GRAB_MISS, HURT, DEATH, RETRACT, SPAWN_UNDER_PLAYER -> State.RETRACT;
            case SHIELD_CHANNEL -> State.SHIELD_CHANNEL;
        };
    }

    public static double progress(State state, long elapsedTicks) {
        int duration = Math.max(1, durationTicks(state));
        double normalized = Math.max(0.0D, Math.min(1.0D,
                Math.max(0L, elapsedTicks) / (double) duration));
        return normalized;
    }
}
