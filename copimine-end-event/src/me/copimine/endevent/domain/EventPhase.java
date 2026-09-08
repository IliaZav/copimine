package me.copimine.endevent.domain;

public enum EventPhase {
    UNCONFIGURED,
    COLLECTING,
    READY_FOR_PLAYERS,
    /** V2 ritual hold after all unique player runes are occupied. */
    START_RITUAL,
    /** Legacy ritual hold name retained while the V2 controller is migrated. */
    @Deprecated
    COUNTDOWN,
    WAVE_1,
    INTERMISSION_1,
    WAVE_2,
    INTERMISSION_2,
    WAVE_3,
    INTERMISSION_3,
    WAVE_4,
    INTERMISSION_4,
    WAVE_5,
    /** V2 transition-rune hold between Wave 5 and chamber combat. */
    INTERMISSION_5,
    /** V2 chamber-combat wave. */
    WAVE_6,
    /** V2 safe cooldown after chamber combat and before the boss cinematic. */
    PRE_BOSS_COOLDOWN,
    /** Durable hand-off after Wave V and before a combat entity exists. */
    BOSS_CINEMATIC,
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
