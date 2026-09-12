package me.copimine.endevent.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Generation fence and living-roster state for one encounter attempt.
 *
 * <p>A wipe has two distinct transactions: {@link #performAttemptWipe(long,
 * String)} only opens a frozen wipe window; {@link #commitWipe(long)} is the
 * point at which the next generation becomes visible. This prevents a stale
 * callback from observing a new attempt while entities, blocks and tasks from
 * the old attempt are still being removed.</p>
 */
public final class AttemptLifecycleController {
    public enum WipeStatus {
        ACCEPTED,
        ALREADY_IN_PROGRESS,
        STALE_GENERATION,
        NO_LIVING_PLAYERS,
        INVALID_GENERATION
    }

    private long generation = Long.MIN_VALUE;
    private long pendingNextGeneration = Long.MIN_VALUE;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Set<UUID> living = new LinkedHashSet<>();
    private boolean wiping;
    private long wipeCount;

    public synchronized void begin(long generation, Set<UUID> roster) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.pendingNextGeneration = Long.MIN_VALUE;
        this.roster.clear();
        if (roster != null) roster.stream().filter(value -> value != null).forEach(this.roster::add);
        this.living.clear();
        this.living.addAll(this.roster);
        this.wiping = false;
    }

    public synchronized long generation() { return generation; }

    public synchronized Set<UUID> roster() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(roster));
    }

    public synchronized Set<UUID> living() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(living));
    }

    public synchronized boolean markDead(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null || !roster.contains(player)) return false;
        return living.remove(player);
    }

    public synchronized boolean markAlive(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null || !roster.contains(player)) return false;
        return living.add(player);
    }

    public synchronized boolean owns(long expectedGeneration) {
        return generation != Long.MIN_VALUE && generation == expectedGeneration;
    }

    public synchronized boolean acceptsCallback(long expectedGeneration) {
        return owns(expectedGeneration) && !wiping;
    }

    public synchronized boolean wiping() { return wiping; }

    /** Begin the wipe but do not commit the next generation yet. */
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
        pendingNextGeneration = nextGeneration(generation);
        wiping = true;
        return new WipeResult(WipeStatus.ACCEPTED, pendingNextGeneration, wipeCount + 1L,
                safeReason(reason));
    }

    /** Commit the pending generation after external cleanup and persistence. */
    public synchronized boolean commitWipe(long expectedGeneration) {
        if (!wiping || !owns(expectedGeneration) || pendingNextGeneration <= 0L) return false;
        generation = pendingNextGeneration;
        pendingNextGeneration = Long.MIN_VALUE;
        living.clear();
        living.addAll(roster);
        wiping = false;
        wipeCount++;
        return true;
    }

    /**
     * Report a failed cleanup without publishing a new generation.
     *
     * <p>The old attempt intentionally remains frozen and the pending
     * generation is retained. A caller may retry cleanup and call
     * {@link #commitWipe(long)} once every owned resource has been removed.
     * Clearing this state here would let stale callbacks mutate the old
     * attempt after a partial wipe.</p>
     */
    public synchronized boolean abortWipe(long expectedGeneration) {
        if (!wiping || !owns(expectedGeneration)) return false;
        return true;
    }

    public synchronized long pendingNextGeneration() { return pendingNextGeneration; }
    public synchronized long wipeCount() { return wipeCount; }

    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        pendingNextGeneration = Long.MIN_VALUE;
        roster.clear();
        living.clear();
        wiping = false;
    }

    private static long nextGeneration(long current) {
        if (current <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("generation exhausted; administrative recovery required");
        }
        return current + 1L;
    }

    private static String safeReason(String reason) { return reason == null ? "" : reason.trim(); }

    public record WipeResult(WipeStatus status, long nextGeneration, long wipeCount, String reason) { }
}
