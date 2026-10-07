package me.copimine.endevent.runtime;

/** Stable Stage 1 handoff boundary to the boss implementation. */
@FunctionalInterface
public interface BossStartGateway {
    void start(EncounterContext context);
}
