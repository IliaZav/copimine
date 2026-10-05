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
    private final Map<UUID, ReturnStatus> wave7Returns = new LinkedHashMap<>();
    private boolean wave7ReturnAdmission;
    private boolean returnWindowOpen;
    private long returnWindowStartedNanos;
    private long returnWindowDurationNanos;
    private long returnWindowDeadlineMillis;
    private boolean wiping;
    private long wipeCount;

    public synchronized void begin(long generation, Set<UUID> roster) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.pendingNextGeneration = Long.MIN_VALUE;
        this.participants.clear();
        clearWave7Returns();
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
            if (status.registered() && status.active() && status.alive() && admitted(player)) result.add(player);
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
            if (status.registered() && status.active() && status.online() && status.alive() && admitted(player)) {
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
                && status.online() && status.alive() && status.eligibleForObjective() && admitted(player);
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
        if (current.online()) requireWave7Return(player);
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
                && current.online() && current.alive() && admitted(player);
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
                && online && current.alive() && admitted(player);
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
        requireWave7Return(player);
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
        clearWave7Returns();
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
        clearWave7Returns();
        wiping = false;
    }

    /** Enable only at the existing Wave 7 initialization, without resetting repeated launches. */
    public synchronized boolean enableWave7Returns(long expectedGeneration) {
        if (!acceptsCallback(expectedGeneration) || participants.isEmpty()) return false;
        if (wave7ReturnAdmission) return true;
        wave7ReturnAdmission = true;
        participants.forEach((owner, status) -> wave7Returns.put(owner, new ReturnStatus(1L,
                status.active() && status.alive() && status.online() ? ReturnStage.ADMITTED : ReturnStage.PENDING, 0L)));
        return true;
    }

    public synchronized boolean hasWave7Returns(long expectedGeneration) {
        return acceptsCallback(expectedGeneration) && wave7ReturnAdmission;
    }

    public synchronized boolean isCombatAdmitted(UUID owner, long expectedGeneration) {
        ParticipantStatus status = acceptsCallback(expectedGeneration) ? participants.get(owner) : null;
        return status != null && status.registered() && status.active() && status.online()
                && status.alive() && admitted(owner);
    }

    public synchronized boolean isReturnPending(UUID owner, long expectedGeneration) {
        ParticipantStatus status = acceptsCallback(expectedGeneration) ? participants.get(owner) : null;
        ReturnStatus claim = wave7Returns.get(owner);
        return wave7ReturnAdmission && status != null && status.registered() && status.active()
                && claim != null && claim.stage() == ReturnStage.PENDING;
    }

    public synchronized long incarnation(UUID owner, long expectedGeneration) {
        ReturnStatus claim = acceptsCallback(expectedGeneration) ? wave7Returns.get(owner) : null;
        return claim == null ? 0L : claim.incarnation();
    }

    public synchronized boolean incarnationMatches(UUID owner, long expectedGeneration, long incarnation) {
        return incarnation > 0L && incarnation(owner, expectedGeneration) == incarnation;
    }

    public synchronized ReturnToken beginReturn(UUID owner, long expectedGeneration, long nowTick, long stagingTicks) {
        if (!isReturnPending(owner, expectedGeneration) || nowTick < 0L || stagingTicks < 1L
                || stagingTicks > 60L || nowTick > Long.MAX_VALUE - stagingTicks) return null;
        ParticipantStatus status = participants.get(owner);
        if (!status.online() || !status.alive()) return null;
        ReturnStatus claim = wave7Returns.get(owner);
        wave7Returns.put(owner, new ReturnStatus(claim.incarnation(), ReturnStage.STAGING, nowTick + stagingTicks));
        return new ReturnToken(owner, expectedGeneration, claim.incarnation());
    }

    public synchronized boolean isReturnProtected(UUID owner, long expectedGeneration, long nowTick) {
        ReturnStatus claim = acceptsCallback(expectedGeneration) ? wave7Returns.get(owner) : null;
        ParticipantStatus status = participants.get(owner);
        return wave7ReturnAdmission && claim != null && claim.stage() == ReturnStage.STAGING
                && nowTick >= 0L && nowTick < claim.readyTick() && status != null && status.active()
                && status.online() && status.alive();
    }

    /** Adapter must revalidate the unchanged claim and destination before committing. */
    public synchronized boolean completeReturn(ReturnToken token, long nowTick, long nowNanos) {
        if (!validStaging(token) || nowTick < wave7Returns.get(token.owner()).readyTick()
                || returnWindowOpen && remainingReturnNanos(nowNanos) <= 0L) return false;
        ParticipantStatus current = participants.get(token.owner());
        if (!current.active() || !current.online() || !current.alive()) return false;
        wave7Returns.put(token.owner(), new ReturnStatus(token.incarnation(), ReturnStage.ADMITTED, 0L));
        // Physical/objective admission is rechecked by the existing adapter.
        participants.put(token.owner(), new ParticipantStatus(true, true, true, true, false));
        returnWindowOpen = false;
        return true;
    }

    public synchronized boolean cancelReturn(ReturnToken token) {
        if (!validStaging(token)) return false;
        requireWave7Return(token.owner());
        return true;
    }

    public synchronized Map<UUID, ReturnToken> stagingReturns(long expectedGeneration) {
        if (!hasWave7Returns(expectedGeneration)) return Map.of();
        Map<UUID, ReturnToken> result = new LinkedHashMap<>();
        wave7Returns.forEach((owner, state) -> {
            if (state.stage() == ReturnStage.STAGING) result.put(owner,
                    new ReturnToken(owner, expectedGeneration, state.incarnation()));
        });
        return Map.copyOf(result);
    }

    public synchronized boolean returnReady(ReturnToken token, long nowTick) {
        return validStaging(token) && nowTick >= wave7Returns.get(token.owner()).readyTick();
    }

    /** Repeated bed respawn/quit/join cannot extend this monotonic window. */
    public synchronized ReturnWindowStatus observeWave7ReturnWindow(long expectedGeneration,
                                                                    long nowNanos, long nowMillis, long graceMillis) {
        if (!hasWave7Returns(expectedGeneration)) return ReturnWindowStatus.NONE;
        requireGrace(graceMillis);
        if (!activeLivingOnlineRoster().isEmpty()) return ReturnWindowStatus.NONE;
        if (returnWindowOpen) return remainingReturnNanos(nowNanos) > 0L
                ? ReturnWindowStatus.WAITING : ReturnWindowStatus.EXPIRED;
        returnWindowOpen = true;
        returnWindowStartedNanos = nowNanos;
        returnWindowDurationNanos = graceMillis * 1_000_000L;
        returnWindowDeadlineMillis = Math.addExact(nowMillis, graceMillis);
        return ReturnWindowStatus.STARTED;
    }

    public synchronized Map<String, String> encodeWave7Returns(String event, Map<UUID, Integer> claims,
                                                               long nowNanos, long nowMillis, long graceMillis) {
        if (!wave7ReturnAdmission || wiping) return Map.of();
        requireGrace(graceMillis);
        if (event == null || event.isBlank() || claims == null || claims.isEmpty() || claims.size() > 20
                || !claims.keySet().equals(roster())) throw new IllegalArgumentException("invalid Wave 7 claims");
        Map<String, String> result = new LinkedHashMap<>();
        String prefix = "wave7-return.";
        result.put(prefix + "version", "1"); result.put(prefix + "event", event);
        result.put(prefix + "generation", Long.toString(generation));
        result.put(prefix + "saved-millis", Long.toString(nowMillis));
        // On a clean restart, formerly admitted owners must explicitly return.
        // Their grace starts at this checkpoint, never at the later boot time.
        long deadline = returnWindowOpen ? returnWindowDeadlineMillis : Math.addExact(nowMillis, graceMillis);
        long remainingMillis = returnWindowOpen ? Math.max(0L, remainingReturnNanos(nowNanos) / 1_000_000L) : graceMillis;
        remainingMillis = Math.min(remainingMillis, Math.max(0L, deadline - nowMillis));
        result.put(prefix + "deadline-millis", Long.toString(deadline));
        result.put(prefix + "remaining-millis", Long.toString(remainingMillis));
        for (var entry : claims.entrySet()) {
            UUID owner = entry.getKey(); ReturnStatus state = wave7Returns.get(owner);
            if (owner == null || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 3
                    || state == null) throw new IllegalArgumentException("invalid Wave 7 claim");
            String key = prefix + owner + ".";
            result.put(key + "claim", Integer.toString(entry.getValue()));
            result.put(key + "incarnation", Long.toString(state.incarnation()));
            result.put(key + "active", Boolean.toString(participants.get(owner).active()));
        }
        return Map.copyOf(result);
    }

    /** Strict aggregate checkpoint restore; never resumes a staged cast or grants outsider rights. */
    public synchronized boolean restoreWave7Returns(Map<String, String> encoded, String event, long expectedGeneration,
                                                     Map<UUID, Integer> claims, Set<UUID> officialRoster,
                                                     long nowNanos, long nowMillis, long graceMillis) {
        try {
            requireGrace(graceMillis);
            String prefix = "wave7-return.";
            if (encoded == null || event == null || event.isBlank() || expectedGeneration <= 0L
                    || claims == null || claims.isEmpty() || claims.size() > 20 || officialRoster == null
                    || officialRoster.size() > 20 || !officialRoster.containsAll(claims.keySet())
                    || !"1".equals(encoded.get(prefix + "version"))
                    || !event.equals(encoded.get(prefix + "event"))
                    || expectedGeneration != Long.parseLong(encoded.get(prefix + "generation"))) return false;
            long saved = Long.parseLong(encoded.get(prefix + "saved-millis"));
            long deadline = Long.parseLong(encoded.get(prefix + "deadline-millis"));
            long remaining = Long.parseLong(encoded.get(prefix + "remaining-millis"));
            if (saved <= 0L || nowMillis < saved || remaining < 0L || remaining > graceMillis
                    || deadline <= 0L || deadline > Math.addExact(saved, graceMillis)) return false;
            Map<UUID, ReturnStatus> restored = new LinkedHashMap<>();
            Map<UUID, ParticipantStatus> statuses = new LinkedHashMap<>();
            Set<String> expectedKeys = new LinkedHashSet<>(Set.of(prefix + "version", prefix + "event",
                    prefix + "generation", prefix + "saved-millis", prefix + "deadline-millis", prefix + "remaining-millis"));
            for (var entry : claims.entrySet()) {
                UUID owner = entry.getKey(); String key = prefix + owner + ".";
                if (owner == null || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 3
                        || entry.getValue() != Integer.parseInt(encoded.get(key + "claim"))) return false;
                long incarnation = Long.parseLong(encoded.get(key + "incarnation"));
                String active = encoded.get(key + "active");
                if (incarnation <= 0L || incarnation == Long.MAX_VALUE || !"true".equals(active)) return false;
                restored.put(owner, new ReturnStatus(incarnation + 1L, ReturnStage.PENDING, 0L));
                statuses.put(owner, new ParticipantStatus(true, true, false, false, false));
                expectedKeys.add(key + "claim"); expectedKeys.add(key + "incarnation"); expectedKeys.add(key + "active");
            }
            Set<String> actualKeys = new LinkedHashSet<>();
            encoded.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(actualKeys::add);
            if (!actualKeys.equals(expectedKeys)) return false;
            long elapsed = nowMillis - saved;
            long restoredRemaining = Math.min(Math.max(0L, remaining - elapsed), Math.max(0L, deadline - nowMillis));
            generation = expectedGeneration; pendingNextGeneration = Long.MIN_VALUE; wiping = false;
            participants.clear(); participants.putAll(statuses);
            wave7Returns.clear(); wave7Returns.putAll(restored); wave7ReturnAdmission = true;
            returnWindowOpen = true; returnWindowStartedNanos = nowNanos;
            returnWindowDurationNanos = restoredRemaining * 1_000_000L; returnWindowDeadlineMillis = deadline;
            return true;
        } catch (IllegalArgumentException | ArithmeticException failure) {
            return false;
        }
    }

    private boolean admitted(UUID owner) {
        ReturnStatus status = wave7Returns.get(owner);
        return !wave7ReturnAdmission || status != null && status.stage() == ReturnStage.ADMITTED;
    }

    private boolean validStaging(ReturnToken token) {
        if (token == null || !hasWave7Returns(token.generation())) return false;
        ReturnStatus claim = wave7Returns.get(token.owner());
        return claim != null && claim.stage() == ReturnStage.STAGING && claim.incarnation() == token.incarnation();
    }

    private void requireWave7Return(UUID owner) {
        ReturnStatus state = wave7Returns.get(owner);
        if (!wave7ReturnAdmission || state == null) return;
        if (state.incarnation() == Long.MAX_VALUE) throw new IllegalStateException("incarnation exhausted");
        wave7Returns.put(owner, new ReturnStatus(state.incarnation() + 1L, ReturnStage.PENDING, 0L));
    }

    private long remainingReturnNanos(long nowNanos) {
        // nanoTime subtraction remains valid across the signed wrap for bounded intervals.
        long elapsed = nowNanos - returnWindowStartedNanos;
        return elapsed < 0L ? 0L : returnWindowDurationNanos - Math.min(returnWindowDurationNanos, elapsed);
    }

    private void clearWave7Returns() {
        wave7Returns.clear(); wave7ReturnAdmission = false; returnWindowOpen = false;
        returnWindowStartedNanos = 0L; returnWindowDurationNanos = 0L; returnWindowDeadlineMillis = 0L;
    }

    /** Compatibility boundary once the existing post-Wave-7 handoff starts the boss. */
    public synchronized void endWave7Returns(long expectedGeneration) {
        if (acceptsCallback(expectedGeneration)) clearWave7Returns();
    }

    private static void requireGrace(long graceMillis) {
        if (graceMillis < 1000L || graceMillis > 120_000L) throw new IllegalArgumentException("invalid return grace");
    }

    public enum ReturnStage { ADMITTED, PENDING, STAGING }
    public enum ReturnWindowStatus { NONE, STARTED, WAITING, EXPIRED }
    private record ReturnStatus(long incarnation, ReturnStage stage, long readyTick) { }
    public record ReturnToken(UUID owner, long generation, long incarnation) { }

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
