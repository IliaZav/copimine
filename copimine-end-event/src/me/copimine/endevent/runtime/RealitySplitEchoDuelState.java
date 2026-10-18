package me.copimine.endevent.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;

/** Immutable, bounded checkpoint values for one owner-bound Wave 7 Echo duel. */
public final class RealitySplitEchoDuelState {
    public static final long MAX_COUNTER = 1_000_000_000L;
    /** Earliest accepted epoch-millis value (2001-09-09), rejecting tick/counter values. */
    public static final long MIN_RETURN_DEADLINE_MILLIS = 1_000_000_000_000L;
    public static final long MAX_RETURN_DEADLINE_MILLIS = 253_402_300_799_999L;
    public static final double MAX_HEALTH = 2_048.0D;
    private static final int MAX_REPLICA_FIELDS = 400;
    private static final int MAX_REPLICA_VALUE_LENGTH = 1_024;
    private static final int MAX_REPLICA_TOTAL_LENGTH = 40_000;

    public enum Lifecycle { PLANNED, ACTIVE, OWNER_OFFLINE, COMPLETE }
    public enum ActorLifecycle { ALIVE, DEAD }

    private RealitySplitEchoDuelState() { }

    /** Resume is permitted only after the owner has passed the event's explicit return gate. */
    public static boolean ownerMayResume(boolean online, boolean alive, boolean sameWorld,
                                         boolean clientReady, boolean combatAdmitted) {
        return online && alive && sameWorld && clientReady && combatAdmitted;
    }

    /**
     * Returns the original owner return deadline for another offline transition.
     * An expired or invalid deadline fails closed instead of starting a new grace window.
     */
    public static OptionalLong ownerOfflineDeadlineMillis(long originalDeadlineMillis,
                                                          long nowMillis,
                                                          long graceMillis) {
        if (nowMillis < MIN_RETURN_DEADLINE_MILLIS || nowMillis > MAX_RETURN_DEADLINE_MILLIS
                || graceMillis <= 0L) {
            return OptionalLong.empty();
        }
        if (originalDeadlineMillis == 0L) {
            if (nowMillis > MAX_RETURN_DEADLINE_MILLIS - graceMillis) return OptionalLong.empty();
            long initialDeadline = nowMillis + graceMillis;
            if (initialDeadline < MIN_RETURN_DEADLINE_MILLIS
                    || initialDeadline > MAX_RETURN_DEADLINE_MILLIS) return OptionalLong.empty();
            return OptionalLong.of(initialDeadline);
        }
        if (originalDeadlineMillis < MIN_RETURN_DEADLINE_MILLIS
                || originalDeadlineMillis > MAX_RETURN_DEADLINE_MILLIS
                || nowMillis >= originalDeadlineMillis) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(originalDeadlineMillis);
    }

    /** Current server carrier state. It contains no Bukkit entity reference. */
    public record ActorState(UUID actorUuid, double currentHealth, double maximumHealth,
                             ActorLifecycle lifecycle, long revision, long nextActionTick,
                             long meleeCooldownUntilTick, long rangedCooldownUntilTick,
                             long pendingUseUntilTick, long deathSerial) {
        public ActorState {
            Objects.requireNonNull(actorUuid, "actorUuid");
            Objects.requireNonNull(lifecycle, "lifecycle");
            requireHealth(currentHealth, maximumHealth, "actor");
            requireCounter(revision, "actor revision");
            requireCounter(nextActionTick, "next action tick");
            requireCounter(meleeCooldownUntilTick, "melee cooldown");
            requireCounter(rangedCooldownUntilTick, "ranged cooldown");
            requireCounter(pendingUseUntilTick, "pending use");
            requireCounter(deathSerial, "death serial");
            if (lifecycle == ActorLifecycle.ALIVE
                    && (!(currentHealth > 0.0D) || deathSerial != 0L)
                    || lifecycle == ActorLifecycle.DEAD
                    && (currentHealth != 0.0D || deathSerial == 0L)) {
                throw new IllegalArgumentException("actor lifecycle, health and death serial disagree");
            }
        }

        public ActorState died(long serial) {
            if (lifecycle != ActorLifecycle.ALIVE || serial != deathSerial + 1L
                    || revision >= MAX_COUNTER) {
                throw new IllegalArgumentException("actor death receipt is stale or duplicated");
            }
            return new ActorState(actorUuid, 0.0D, maximumHealth, ActorLifecycle.DEAD,
                    revision + 1L, nextActionTick, meleeCooldownUntilTick,
                    rangedCooldownUntilTick, pendingUseUntilTick, serial);
        }
    }

    /** One persisted duel for exactly one roster owner. */
    public record DuelRecord(UUID eventId, long attemptGeneration, long runtimeGeneration,
                             int room, UUID duelId, UUID ownerUuid, Lifecycle lifecycle,
                             double ownerStartingHealth, double ownerMaximumHealth,
                             long returnDeadlineMillis, long revision, ActorState actor,
                             long replicaRevision, Map<String, String> replicaState,
                             EchoBehaviorStrategy behaviorStrategy) {
        public DuelRecord {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(duelId, "duelId");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(lifecycle, "lifecycle");
            if (attemptGeneration <= 0L || attemptGeneration > MAX_COUNTER
                    || runtimeGeneration < attemptGeneration || runtimeGeneration > MAX_COUNTER
                    || room < 0 || room >= 4 || duelId.equals(ownerUuid)) {
                throw new IllegalArgumentException("invalid Echo duel identity");
            }
            requireReturnDeadline(returnDeadlineMillis);
            requireCounter(revision, "duel revision");
            requireCounter(replicaRevision, "replica revision");
            if (replicaState == null || replicaState.size() > MAX_REPLICA_FIELDS
                    || replicaState.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                    || entry.getValue() == null || entry.getKey().length() > 64
                    || entry.getValue().length() > MAX_REPLICA_VALUE_LENGTH)
                    || replicaState.entrySet().stream().mapToInt(entry -> entry.getKey().length()
                    + entry.getValue().length()).sum() > MAX_REPLICA_TOTAL_LENGTH) {
                throw new IllegalArgumentException("invalid bounded Echo replica state");
            }
            replicaState = Map.copyOf(replicaState);

            if (lifecycle == Lifecycle.PLANNED) {
                if (actor != null || ownerStartingHealth != 0.0D || ownerMaximumHealth != 0.0D
                        || returnDeadlineMillis != 0L || revision != 0L || replicaRevision != 0L
                        || !replicaState.isEmpty() || behaviorStrategy != null) {
                    throw new IllegalArgumentException("planned Echo duel cannot contain runtime state");
                }
            } else {
                requireHealth(ownerStartingHealth, ownerMaximumHealth, "owner");
                if (actor == null || actor.actorUuid().equals(ownerUuid)) {
                    throw new IllegalArgumentException("active Echo duel is missing its private actor");
                }
                if (behaviorStrategy == null) {
                    throw new IllegalArgumentException("active Echo duel requires its frozen owner behavior profile");
                }
                if (actor.maximumHealth() != ownerMaximumHealth) {
                    throw new IllegalArgumentException("Echo actor maximum health must match its owner");
                }
                validateReplica(eventId, attemptGeneration, duelId, ownerUuid,
                        replicaRevision, replicaState);
                if ((lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.OWNER_OFFLINE)
                        && actor.lifecycle() != ActorLifecycle.ALIVE) {
                    throw new IllegalArgumentException("live Echo duel has a non-live actor");
                }
                if (lifecycle == Lifecycle.OWNER_OFFLINE && returnDeadlineMillis <= 0L) {
                    throw new IllegalArgumentException("offline Echo owner requires the original return deadline");
                }
                if (lifecycle == Lifecycle.COMPLETE && actor.lifecycle() != ActorLifecycle.DEAD) {
                    throw new IllegalArgumentException("completed Echo duel requires its dead actor record");
                }
            }
        }

        public static DuelRecord planned(UUID eventId, long attemptGeneration, long runtimeGeneration,
                                         int room, UUID duelId, UUID ownerUuid) {
            return new DuelRecord(eventId, attemptGeneration, runtimeGeneration, room,
                    duelId, ownerUuid, Lifecycle.PLANNED, 0.0D, 0.0D,
                    0L, 0L, null, 0L, Map.of(), null);
        }

        public DuelRecord withActor(long newRuntimeGeneration, double ownerHealth,
                                    double ownerMaxHealth, long originalReturnDeadlineMillis,
                                    ActorState newActor, long newRevision, long newReplicaRevision,
                                    Map<String, String> newReplicaState,
                                    EchoBehaviorStrategy frozenBehaviorStrategy) {
            if (lifecycle == Lifecycle.COMPLETE || newRuntimeGeneration < runtimeGeneration
                    || newRevision <= revision || newReplicaRevision < replicaRevision
                    || frozenBehaviorStrategy == null) {
                throw new IllegalArgumentException("Echo duel cannot move to an older runtime or leave completion");
            }
            if (lifecycle != Lifecycle.PLANNED && (ownerHealth != ownerStartingHealth
                    || ownerMaxHealth != ownerMaximumHealth || actor == null || newActor == null
                    || !behaviorStrategy.equals(frozenBehaviorStrategy)
                    || newActor.revision() < actor.revision() || newActor.deathSerial() != actor.deathSerial()
                    || originalReturnDeadlineMillis != returnDeadlineMillis
                    || newActor.revision() == actor.revision() && !sameActorPayload(actor, newActor)
                    || newRuntimeGeneration == runtimeGeneration
                    && !actor.actorUuid().equals(newActor.actorUuid()))) {
                throw new IllegalArgumentException("Echo resume cannot reroll owner, actor or duel state");
            }
            return new DuelRecord(eventId, attemptGeneration, newRuntimeGeneration, room,
                    duelId, ownerUuid, Lifecycle.ACTIVE, ownerHealth, ownerMaxHealth,
                    originalReturnDeadlineMillis, newRevision, newActor, newReplicaRevision,
                    newReplicaState, frozenBehaviorStrategy);
        }

        public DuelRecord ownerOffline(long originalDeadlineMillis, long newRevision) {
            if (lifecycle != Lifecycle.ACTIVE || originalDeadlineMillis <= 0L
                    || newRevision <= revision || originalDeadlineMillis != 0L
                    && returnDeadlineMillis != 0L && originalDeadlineMillis != returnDeadlineMillis) {
                throw new IllegalArgumentException("invalid Echo owner-offline transition");
            }
            return new DuelRecord(eventId, attemptGeneration, runtimeGeneration, room,
                    duelId, ownerUuid, Lifecycle.OWNER_OFFLINE, ownerStartingHealth,
                    ownerMaximumHealth, originalDeadlineMillis, newRevision, actor,
                    replicaRevision, replicaState, behaviorStrategy);
        }

        public DuelRecord died(long deathSerial) {
            if (lifecycle != Lifecycle.ACTIVE || actor == null || revision >= MAX_COUNTER) {
                throw new IllegalArgumentException("Echo duel is not active for actor death");
            }
            return new DuelRecord(eventId, attemptGeneration, runtimeGeneration, room,
                    duelId, ownerUuid, Lifecycle.COMPLETE, ownerStartingHealth,
                    ownerMaximumHealth, returnDeadlineMillis, revision + 1L,
                    actor.died(deathSerial), replicaRevision, replicaState, behaviorStrategy);
        }
    }

    private static void validateReplica(UUID event, long attempt, UUID duel, UUID owner,
                                        long revision, Map<String, String> encoded) {
        if (encoded.isEmpty()) throw new IllegalArgumentException("active Echo duel requires bounded replica state");
        try {
            EchoLoadoutState restored = EchoLoadoutState.restore(encoded, event, attempt, duel, owner, revision);
            if (restored.revision() != revision || !restored.encode().equals(encoded)) {
                throw new IllegalArgumentException("non-canonical Echo replica revision or fields");
            }
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("invalid allowlisted Echo replica state", error);
        }
    }

    private static void requireHealth(double current, double maximum, String label) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum)
                || current < 0.0D || maximum <= 0.0D || current > maximum
                || maximum > MAX_HEALTH) {
            throw new IllegalArgumentException("invalid bounded " + label + " health");
        }
    }

    private static void requireCounter(long value, String label) {
        if (value < 0L || value > MAX_COUNTER) {
            throw new IllegalArgumentException("invalid bounded Echo " + label);
        }
    }

    private static void requireReturnDeadline(long value) {
        if (value != 0L && (value < MIN_RETURN_DEADLINE_MILLIS
                || value > MAX_RETURN_DEADLINE_MILLIS)) {
            throw new IllegalArgumentException("invalid bounded Echo owner return deadline");
        }
    }

    private static boolean sameActorPayload(ActorState left, ActorState right) {
        return left.currentHealth() == right.currentHealth()
                && left.maximumHealth() == right.maximumHealth()
                && left.lifecycle() == right.lifecycle()
                && left.revision() == right.revision()
                && left.nextActionTick() == right.nextActionTick()
                && left.meleeCooldownUntilTick() == right.meleeCooldownUntilTick()
                && left.rangedCooldownUntilTick() == right.rangedCooldownUntilTick()
                && left.pendingUseUntilTick() == right.pendingUseUntilTick()
                && left.deathSerial() == right.deathSerial();
    }
}
