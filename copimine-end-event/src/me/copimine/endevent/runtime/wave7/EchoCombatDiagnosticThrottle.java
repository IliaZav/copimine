package me.copimine.endevent.runtime.wave7;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * E-06: bounded, per-actor throttling for the named Echo decision failure channel.
 *
 * <p>The adapter can reject a tick for an expected reason (an invalid snapshot)
 * or an unexpected one, and a permanently broken actor would otherwise write a
 * diagnostic every single tick. This keeps at most one report per actor per
 * window so the log stays readable and the diagnostic sink cannot be flooded by
 * one bad body.</p>
 *
 * <p>The window is measured in event ticks, not wall time, so a paused or
 * restarted event neither leaks reports nor silently drops a fresh one: a tick
 * that moves backwards (server restart resets the counter) is always allowed to
 * report. Entries are removed with the actor, so the map is bounded by the
 * roster size rather than by uptime.</p>
 */
public final class EchoCombatDiagnosticThrottle {
    /** Ten seconds at twenty ticks per second; long enough to be quiet, short enough to notice. */
    public static final long DEFAULT_INTERVAL_TICKS = 200L;

    private final long intervalTicks;
    private final Map<UUID, Long> lastEmitTick = new HashMap<>();

    public EchoCombatDiagnosticThrottle() {
        this(DEFAULT_INTERVAL_TICKS);
    }

    public EchoCombatDiagnosticThrottle(long intervalTicks) {
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException("diagnostic throttle interval must be positive");
        }
        this.intervalTicks = intervalTicks;
    }

    public long intervalTicks() {
        return intervalTicks;
    }

    /**
     * True when this actor has not reported inside the current window. A null
     * actor is never reported, and a tick that moved backwards (event restart)
     * is treated as a new window so a stale stamp cannot suppress a real fault.
     */
    public boolean shouldEmit(UUID actorId, long tick) {
        if (actorId == null) return false;
        Long last = lastEmitTick.get(actorId);
        return last == null || tick < last || tick - last >= intervalTicks;
    }

    /** Stamps a report for the actor at the given tick. */
    public void record(UUID actorId, long tick) {
        if (actorId != null) lastEmitTick.put(actorId, tick);
    }

    /** Drops the actor's window when its runtime is removed. */
    public void forget(UUID actorId) {
        if (actorId != null) lastEmitTick.remove(actorId);
    }

    /** Drops every window when the event tears down. */
    public void clear() {
        lastEmitTick.clear();
    }

    /** Number of tracked actors; used by diagnostics self-checks. */
    public int trackedActors() {
        return lastEmitTick.size();
    }
}
