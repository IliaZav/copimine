package me.copimine.endevent.domain;

/**
 * The only live End Rift encounter graph. Older snapshots are decoded before
 * they reach this enum; obsolete gameplay states are not part of the runtime.
 */
public enum EventPhase {
    UNCONFIGURED,
    COLLECTING,
    READY_FOR_PLAYERS,
    START_RITUAL,
    WAVE_1,
    INTERMISSION_1,
    WAVE_2,
    INTERMISSION_2,
    WAVE_3,
    INTERMISSION_3,
    WAVE_4,
    CORE_RESTORATION,
    WAVE_5,
    INTERMISSION_5,
    WAVE_6,
    INTERMISSION_6,
    WAVE_7,
    PRE_BOSS_COOLDOWN,
    BOSS_CINEMATIC,
    BOSS_ACTIVE,
    BOSS_FINISH,
    VICTORY_PROCESSING,
    UNLOCKED,
    RECOVERY_REQUIRED
}
