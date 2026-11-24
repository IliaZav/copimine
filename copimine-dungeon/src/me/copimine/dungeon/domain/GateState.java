package me.copimine.dungeon.domain;

/**
 * Physical gate collision/state contract (spec OV4.5). All transitions are
 * generation-tagged and monotonic; a player occupying the door volume defers
 * closure into BLOCKED_BY_ENTITY rather than entombing them.
 */
public enum GateState {
    CLOSED,
    CLOSING,
    OPEN,
    OPENING,
    BLOCKED_BY_ENTITY,
    RECOVERY_REQUIRED
}
