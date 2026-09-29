package me.copimine.endevent.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
    private final Map<UUID, ParticipantStatus> participants = new LinkedHashMap<>();
    private boolean wiping;
    private long wipeCount;

    public synchronized void begin(long generation, Set<UUID> roster) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.pendingNextGeneration = Long.MIN_VALUE;
        this.participants.clear();
        if (roster != null) {
            roster.stream().filter(value -> value != null).forEach(player ->
                    this.participants.put(player, new ParticipantStatus(true, true, true, true, true)));
        }
        this.wiping = false;
    }

    public synchronized long generation() { return generation; }

    public synchronized Set<UUID> roster() {
        Set<UUID> result = new LinkedHashSet<>();
        participants.forEach((player, status) -> {
            if (status.registered() && status.active()) result.add(player);
        });
        return Collections.unmodifiableSet(result);
    }

    public synchronized Set<UUID> living() {
        Set<UUID> result = new LinkedHashSet<>();
        participants.forEach((player, status) -> {
            if (status.registered() && status.active() && status.alive()) result.add(player);
        });
        return Collections.unmodifiableSet(result);
    }

    /**
     * The participants currently required to complete a live transition hold.
     * Reward membership remains frozen separately; absent or dead members must
     * not be expected to stand on a rune they cannot reach.
     */
    public synchronized Set<UUID> activeLivingOnlineRoster() {
        Set<UUID> result = new LinkedHashSet<>();
        participants.forEach((player, status) -> {
            if (status.registered() && status.active() && status.online() && status.alive()) {
                result.add(player);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    /** The immutable state snapshot for one member of this generation's roster, or {@code null}. */
    public synchronized ParticipantStatus status(UUID player) {
        return participants.get(player);
    }

    public synchronized boolean isRegistered(UUID player, long expectedGeneration) {
        ParticipantStatus status = owns(expectedGeneration) ? participants.get(player) : null;
        return status != null && status.registered();
    }

    public synchronized boolean isObjectiveEligible(UUID player, long expectedGeneration) {
        ParticipantStatus status = owns(expectedGeneration) ? participants.get(player) : null;
        return status != null && status.registered() && status.active()
                && status.online() && status.alive() && status.eligibleForObjective();
    }

    public synchronized boolean markOnline(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null) return false;
        participants.put(player, new ParticipantStatus(
                current.registered(), current.active(), true, current.alive(), false));
        return true;
    }

    public synchronized boolean markOffline(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null) return false;
        participants.put(player, new ParticipantStatus(
                current.registered(), current.active(), false, current.alive(), false));
        return true;
    }

    /** Recompute arena/objective eligibility without changing the death lifecycle. */
    public synchronized boolean markObjectiveEligible(UUID player, long expectedGeneration,
                                                       boolean eligible) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null) return false;
        boolean accepted = eligible && current.registered() && current.active()
                && current.online() && current.alive();
        participants.put(player, new ParticipantStatus(current.registered(), current.active(),
                current.online(), current.alive(), accepted));
        return accepted;
    }

    public synchronized boolean refreshObjectiveEligibility(UUID player, long expectedGeneration,
                                                              boolean online, boolean eligible) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null) return false;
        boolean accepted = eligible && current.registered() && current.active()
                && online && current.alive();
        participants.put(player, new ParticipantStatus(current.registered(), current.active(),
                online, current.alive(), accepted));
        return accepted;
    }

    public synchronized boolean setActive(UUID player, long expectedGeneration, boolean active) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null) return false;
        participants.put(player, new ParticipantStatus(current.registered(), active,
                current.online(), current.alive(), current.eligibleForObjective() && active));
        return true;
    }

    public synchronized boolean markDead(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null || !current.alive()) return false;
        participants.put(player, new ParticipantStatus(current.registered(), current.active(),
                current.online(), false, false));
        return true;
    }

    public synchronized boolean markAlive(UUID player, long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || player == null) return false;
        ParticipantStatus current = participants.get(player);
        if (current == null || !current.active()) return false;
        participants.put(player, new ParticipantStatus(current.registered(), current.active(),
                true, true, false));
        return !current.alive();
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
            return new WipeResult(WipeStatus.ALREADY_IN_PROGRESS, pendingNextGeneration,
                    wipeCount, safeReason(reason));
        }
        if (!living().isEmpty()) {
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
        participants.replaceAll((player, ignored) -> new ParticipantStatus(true, true, true, true, true));
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
        participants.clear();
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

    public record ParticipantStatus(boolean registered, boolean active, boolean online,
                                    boolean alive, boolean eligibleForObjective) { }

    public record WipeResult(WipeStatus status, long nextGeneration, long wipeCount, String reason) { }
}
