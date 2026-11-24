package me.copimine.dungeon.domain;

import java.util.Locale;

/**
 * Isolated dungeon-room lifecycle (spec OV4.4). Deliberately separate from the
 * End Rift event's wave/phase state so room counters and cleanup owners can
 * never be shared with the seven-wave event or the boss.
 *
 * <pre>
 * DRAFT -&gt; PUBLISHED_IDLE -&gt; ARMING -&gt; SEALING -&gt; FIGHTING -&gt; CLEAR_COMMITTED
 *        -&gt; OPENING -&gt; REWARD_READY -&gt; RESETTING -&gt; PUBLISHED_IDLE
 * </pre>
 */
public enum RoomState {
    DRAFT,
    PUBLISHED_IDLE,
    ARMING,
    SEALING,
    FIGHTING,
    CLEAR_COMMITTED,
    OPENING,
    REWARD_READY,
    RESETTING,
    ABORTING,
    RECOVERY_REQUIRED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RoomState fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT);
        for (RoomState state : values()) {
            if (state.name().equals(normalized)) {
                return state;
            }
        }
        return null;
    }

    /** Terminal-ish states that must route through RESETTING/RECOVERY before re-arming. */
    public boolean isRecoverable() {
        return this == ABORTING || this == RECOVERY_REQUIRED;
    }

    /** The room is actively consuming server resources and owning actors. */
    public boolean isActive() {
        return this == ARMING || this == SEALING || this == FIGHTING
                || this == CLEAR_COMMITTED || this == OPENING || this == REWARD_READY
                || this == ABORTING || this == RECOVERY_REQUIRED;
    }
}
