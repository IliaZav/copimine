package me.copimine.endevent.domain;

/**
 * Group-combat intent selected from the bounded boss perception snapshot.
 * Intent is a gameplay decision, not a client animation name.
 */
public enum BossIntent {
    PRESSURE,
    FLANK,
    PUNISH_STACK,
    PUNISH_SPREAD,
    CREATE_SPACE,
    RANGED_PRESSURE,
    CONTROL,
    SUMMON,
    CHANNEL,
    RECOVER
}
