package me.copimine.endevent.domain;

public enum EventPhase {
    UNCONFIGURED,
    COLLECTING,
    READY_FOR_PLAYERS,
    COUNTDOWN,
    WAVE_1,
    INTERMISSION_1,
    WAVE_2,
    INTERMISSION_2,
    WAVE_3,
    BOSS_ACTIVE,
    /** Canonical final threshold phase. */
    FINAL_DRAIN,
    FINAL_WAVE,
    BOSS_FINISH,
    /** Canonical durable victory saga phase. */
    VICTORY_PROCESSING,
    /** Legacy state names retained only for source/data compatibility. */
    @Deprecated
    FINAL_RITUAL,
    @Deprecated
    VICTORY,
    UNLOCKED,
    RECOVERY_REQUIRED
}
