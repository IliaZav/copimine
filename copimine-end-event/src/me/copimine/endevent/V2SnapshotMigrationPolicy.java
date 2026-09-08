package me.copimine.endevent;

import me.copimine.endevent.domain.EventPhase;

/**
 * Lossless persistence-boundary migration for snapshots written by the
 * legacy event flow. Active attempts must never resume half-way through
 * combat after a plugin/server restart, so they recover to the V2
 * player-ready state while all durable progress remains unchanged.
 */
public final class V2SnapshotMigrationPolicy {
    private V2SnapshotMigrationPolicy() {
    }

    public static EventPhase canonicalPhase(EventPhase persisted) {
        if (persisted == null) {
            return EventPhase.RECOVERY_REQUIRED;
        }
        return switch (persisted) {
            case COUNTDOWN, START_RITUAL, WAVE_1, INTERMISSION_1, WAVE_2,
                    INTERMISSION_2, WAVE_3, INTERMISSION_3, WAVE_4,
                    INTERMISSION_4, WAVE_5, INTERMISSION_5, WAVE_6,
                    PRE_BOSS_COOLDOWN, BOSS_CINEMATIC, BOSS_ACTIVE,
                    FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH -> EventPhase.READY_FOR_PLAYERS;
            case VICTORY -> EventPhase.VICTORY_PROCESSING;
            default -> persisted;
        };
    }

    public static EventSnapshot migrate(EventSnapshot source, int targetSchemaVersion) {
        if (source == null) {
            return EventSnapshot.empty(Math.max(1, targetSchemaVersion));
        }
        return source.withSchemaAndPhase(Math.max(1, targetSchemaVersion),
                canonicalPhase(source.eventPhase()));
    }
}
