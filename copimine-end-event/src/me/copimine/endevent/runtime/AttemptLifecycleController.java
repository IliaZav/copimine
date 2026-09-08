package me.copimine.endevent.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Generation fence and living-roster state for one V2 attempt.  World
 * cleanup is supplied by the Bukkit facade, but only this controller decides
 * whether a wipe may start and which generation follows it.
 */
public final class AttemptLifecycleController {
    public enum WipeStatus {
        ACCEPTED,
        ALREADY_IN_PROGRESS,
        STALE_GENERATION,
        NO_LIVING_PLAYERS
    }

    private long generation = Long.MIN_VALUE;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Set<UUID> living = new LinkedHashSet<>();
    private boolean wiping;
    private long wipeCount;

    public synchronized void begin(long generation, Set<UUID> roster) {
        this.generation = generation;
        this.roster.clear();
        if (roster != null) this.roster.addAll(roster);
        this.living.clear();
        this.living.addAll(this.roster);
        this.wiping = false;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Set<UUID> roster() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(roster));
    }

    public synchronized Set<UUID> living() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(living));
    }

    public synchronized boolean markDead(UUID player, long expectedGeneration) {
        if (!owns(expectedGeneration) || player == null || !roster.contains(player)) return false;
        return living.remove(player);
    }

    public synchronized boolean markAlive(UUID player, long expectedGeneration) {
        if (!owns(expectedGeneration) || player == null || !roster.contains(player)) return false;
        return living.add(player);
    }

    public synchronized boolean owns(long expectedGeneration) {
        return generation != Long.MIN_VALUE && generation == expectedGeneration;
    }

    public synchronized WipeResult performAttemptWipe(long expectedGeneration, String reason) {
        if (!owns(expectedGeneration)) {
            return new WipeResult(WipeStatus.STALE_GENERATION, generation, wipeCount, safeReason(reason));
        }
        if (wiping) {
            return new WipeResult(WipeStatus.ALREADY_IN_PROGRESS, generation, wipeCount, safeReason(reason));
        }
        if (!living.isEmpty()) {
            return new WipeResult(WipeStatus.NO_LIVING_PLAYERS, generation, wipeCount, safeReason(reason));
        }
        wiping = true;
        long next = generation == Long.MAX_VALUE ? 1L : generation + 1L;
        wipeCount++;
        generation = next;
        living.clear();
        living.addAll(roster);
        wiping = false;
        return new WipeResult(WipeStatus.ACCEPTED, next, wipeCount, safeReason(reason));
    }

    public synchronized long wipeCount() {
        return wipeCount;
    }

    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        roster.clear();
        living.clear();
        wiping = false;
    }

    private static String safeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }

    public record WipeResult(WipeStatus status, long nextGeneration,
                             long wipeCount, String reason) {
    }
}
