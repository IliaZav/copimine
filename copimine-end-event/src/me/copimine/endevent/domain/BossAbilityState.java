package me.copimine.endevent.domain;

/**
 * Short visual/ability timeline state. It is never used as a generic damage
 * immunity switch; only the explicit lifecycle reason is allowed to gate a
 * boss hit.
 */
public enum BossAbilityState {
    NONE,
    TELEGRAPHING,
    EXECUTING,
    RECOVERY
}
