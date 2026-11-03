package me.copimine.endevent.runtime.wave7;

import java.util.Map;

/**
 * A clone duel started by the operator through {@code /cmend test echo clone}.
 *
 * <p>This exists because the user asked for a way to hand a friend a copy of
 * themselves and check the Echo behaviour, then the Limbo split, without
 * running a whole Wave 7 attempt. A clone is NOT an official Echo: it has no
 * roster entry, no chamber, no reward and no persistence. It is a local,
 * disposable body that runs the same policies so what the operator tests is
 * what ships.</p>
 *
 * <p>Everything a clone needs to be torn down safely is here, including its
 * own expiry: a clone left behind by a player who closed the game must not
 * outlive the operator's attention.</p>
 */
public final class EchoCloneSession {
    /** Hard ceiling on simultaneous clones, so a stuck command cannot flood the arena. */
    public static final int MAX_CONCURRENT_CLONES = 16;

    /** How long a clone lives: fifteen minutes of real play, then it leaves. */
    public static final long LIFETIME_TICKS = 20L * 60L * 15L;

    private final java.util.UUID eventId;
    private final long generation;
    private final long epoch;
    private final java.util.UUID duelId;
    private final java.util.UUID ownerId;
    private final java.util.UUID carrierId;
    private final java.util.UUID worldId;
    private final long startedTick;
    private final String ownerName;
    private final boolean ownsCombatPair;
    private boolean active = true;

    public EchoCloneSession(java.util.UUID eventId, long generation, long epoch,
                            java.util.UUID duelId, java.util.UUID ownerId,
                            java.util.UUID carrierId, java.util.UUID worldId,
                            long startedTick, String ownerName, boolean ownsCombatPair) {
        if (eventId == null || duelId == null || ownerId == null || carrierId == null
                || worldId == null || ownerName == null || ownerName.isBlank()
                || generation <= 0L || epoch < 0L || startedTick < 0L) {
            throw new IllegalArgumentException("invalid Echo clone session");
        }
        this.eventId = eventId;
        this.generation = generation;
        this.epoch = epoch;
        this.duelId = duelId;
        this.ownerId = ownerId;
        this.carrierId = carrierId;
        this.worldId = worldId;
        this.startedTick = startedTick;
        this.ownerName = ownerName;
        this.ownsCombatPair = ownsCombatPair;
    }

    public java.util.UUID eventId() {
        return eventId;
    }

    public long generation() {
        return generation;
    }

    public long epoch() {
        return epoch;
    }

    public java.util.UUID duelId() {
        return duelId;
    }

    public java.util.UUID ownerId() {
        return ownerId;
    }

    public java.util.UUID carrierId() {
        return carrierId;
    }

    public java.util.UUID worldId() {
        return worldId;
    }

    public long startedTick() {
        return startedTick;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean ownsCombatPair() {
        return ownsCombatPair;
    }

    /** False once the clone was stopped, expired or lost its owner. */
    public boolean active() {
        return active;
    }

    /** Marks the clone finished. Idempotent, so every teardown path can call it. */
    public void deactivate() {
        this.active = false;
    }

    /** True once the clone outlived its lifetime. */
    public boolean expired(long tick) {
        return tick - startedTick >= LIFETIME_TICKS;
    }

    /** The pair identity this clone's combat admission is scoped to. */
    public me.copimine.endevent.domain.wave7.EchoCombatAdmission.Pair pair() {
        return new me.copimine.endevent.domain.wave7.EchoCombatAdmission.Pair(eventId, generation,
                epoch, duelId, ownerId, carrierId, worldId);
    }

    /** Stable diagnostic view; used by the command output and the server log. */
    public Map<String, String> describe() {
        return Map.of(
                "duel", duelId.toString(),
                "carrier", carrierId.toString(),
                "owner", ownerName,
                "active", Boolean.toString(active));
    }
}
