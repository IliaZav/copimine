package me.copimine.endevent.runtime.wave7;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle;

/**
 * Selects one owner-bound action for the official named Echo tick and checkpoints every cost.
 * Native Bukkit effects are executed only after the returned duel has been durably saved.
 */
public final class EchoDuelCombatRuntimeAdapter {
    private static final int MELEE_RANGE_BLOCKS = 4;
    private static final int RANGED_RANGE_BLOCKS = 30;
    private static final int HEAL_THRESHOLD_PERMILLE = 550;
    private static final EchoDuelCombatPolicy.Cooldowns COOLDOWNS =
            new EchoDuelCombatPolicy.Cooldowns(10, 20, 40, 32);

    private EchoDuelCombatRuntimeAdapter() {
    }

    public enum Action { NONE, MELEE, RANGED, CONSUMABLE }

    /** The live context must exactly match the persisted event, room, actor and duel identity. */
    public record Scope(UUID eventId, long attemptGeneration, long runtimeGeneration, int room,
                        UUID duelId, UUID ownerUuid, UUID actorUuid, UUID worldId,
                        boolean combatAdmitted, boolean sameWorld,
                        boolean ownerInCell, boolean actorInCell) {
        public Scope {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(duelId, "duelId");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(actorUuid, "actorUuid");
            Objects.requireNonNull(worldId, "worldId");
            if (attemptGeneration <= 0 || attemptGeneration > RealitySplitEchoDuelState.MAX_COUNTER
                    || runtimeGeneration < attemptGeneration
                    || runtimeGeneration > RealitySplitEchoDuelState.MAX_COUNTER
                    || room < 0 || room >= 4 || ownerUuid.equals(actorUuid)) {
                throw new IllegalArgumentException("invalid live Echo combat scope");
            }
        }

        private boolean matches(DuelRecord duel) {
            return duel != null && combatAdmitted && sameWorld && ownerInCell && actorInCell
                    && eventId.equals(duel.eventId())
                    && attemptGeneration == duel.attemptGeneration()
                    && runtimeGeneration == duel.runtimeGeneration()
                    && room == duel.room() && duelId.equals(duel.duelId())
                    && ownerUuid.equals(duel.ownerUuid()) && duel.actor() != null
                    && actorUuid.equals(duel.actor().actorUuid());
        }
    }

    public record Observation(long tick, double distanceSquared, boolean lineOfSight) {
        public Observation {
            if (tick < 0 || tick > RealitySplitEchoDuelState.MAX_COUNTER
                    || !Double.isFinite(distanceSquared) || distanceSquared < 0.0D
                    || distanceSquared > 1_000_000.0D) {
                throw new IllegalArgumentException("invalid Echo combat observation");
            }
        }
    }

    /** Item values are frozen before any cost is applied so the current action can finish on a break. */
    public record Decision(Scope scope, Action action, DuelRecord previousDuel, DuelRecord nextDuel,
                           EchoLoadoutState previousReplica,
                           EchoLoadoutState replica, int itemSlot, int ammoSlot,
                           EchoLoadoutState.Item actionItem) {
        public Decision {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(previousDuel, "previousDuel");
            Objects.requireNonNull(nextDuel, "nextDuel");
            Objects.requireNonNull(previousReplica, "previousReplica");
            Objects.requireNonNull(replica, "replica");
            if (action == Action.NONE && (itemSlot != -1 || ammoSlot != -1 || actionItem != null)
                    || action == Action.MELEE && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
                    || action == Action.RANGED && (itemSlot < 0 || ammoSlot < 0 || actionItem == null)
                    || action == Action.CONSUMABLE && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)) {
                throw new IllegalArgumentException("invalid Echo combat decision");
            }
        }
    }

    /**
     * Returns empty for stale or incomplete adapters, and a NONE decision for a valid duel with
     * no action currently available. Resource mutations are isolated to the supplied snapshot.
     */
    public static Optional<Decision> tick(DuelRecord duel, EchoLoadoutState replica,
                                          Scope scope, Observation observation) {
        if (duel == null || replica == null || scope == null || observation == null
                || !scope.matches(duel) || duel.lifecycle() != Lifecycle.ACTIVE
                || duel.actor().lifecycle() != ActorLifecycle.ALIVE
                || duel.revision() >= RealitySplitEchoDuelState.MAX_COUNTER
                || replica.revision() != duel.replicaRevision()
                || !replica.encode().equals(duel.replicaState())) {
            return Optional.empty();
        }

        try {
            EchoLoadoutState baselineReplica = EchoLoadoutState.restore(replica.encode(),
                    duel.eventId(), duel.attemptGeneration(), duel.duelId(), duel.ownerUuid(), replica.revision());
            int meleeSlot = replica.find(EchoLoadoutState.Kind.MELEE);
            int bowSlot = replica.find(EchoLoadoutState.Kind.BOW);
            int crossbowSlot = replica.find(EchoLoadoutState.Kind.CROSSBOW);
            int rangedSlot = duel.behaviorStrategy().rangedPermille() >= duel.behaviorStrategy().meleePermille()
                    ? firstUsable(crossbowSlot, bowSlot) : firstUsable(bowSlot, crossbowSlot);
            int ammoSlot = replica.find(EchoLoadoutState.Kind.AMMO);

            int consumableSlot = replica.find(EchoLoadoutState.Kind.GOLDEN_APPLE);
            if (consumableSlot < 0) consumableSlot = replica.find(EchoLoadoutState.Kind.FOOD);
            boolean needsHealing = duel.actor().currentHealth() * 1_000.0D
                    <= duel.actor().maximumHealth() * HEAL_THRESHOLD_PERMILLE;
            if (needsHealing && duel.behaviorStrategy().sustainPermille() >= 250
                    && consumableSlot >= 0) {
                EchoLoadoutState.Item item = replica.item(consumableSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartConsumable(
                        duel.actor(), replica, consumableSlot, replica.revision(),
                        observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    double healing = item.kind() == EchoLoadoutState.Kind.GOLDEN_APPLE ? 4.0D : 2.0D;
                    ActorState healed = withHealth(actor.get(), Math.min(
                            actor.get().maximumHealth(), actor.get().currentHealth() + healing));
                    return decision(duel, baselineReplica, replica, scope, Action.CONSUMABLE, healed,
                            consumableSlot, -1, item);
                }
            }

            boolean inMeleeRange = observation.distanceSquared()
                    <= MELEE_RANGE_BLOCKS * (double) MELEE_RANGE_BLOCKS;
            if (inMeleeRange && meleeSlot >= 0
                    && duel.behaviorStrategy().meleePermille() >= duel.behaviorStrategy().rangedPermille()) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.MELEE, actor.get(),
                            meleeSlot, -1, item);
                }
            }

            if (rangedSlot >= 0 && ammoSlot >= 0 && observation.lineOfSight()
                    && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS) {
                EchoLoadoutState.Item item = replica.item(rangedSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartRanged(
                        duel.actor(), replica, rangedSlot, ammoSlot, replica.revision(),
                        observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    if (!EchoDuelCombatPolicy.tryApplyGearWear(
                            replica, rangedSlot, 1, replica.revision())) return Optional.empty();
                    return decision(duel, baselineReplica, replica, scope, Action.RANGED, actor.get(),
                            rangedSlot, ammoSlot, item);
                }
            }

            if (inMeleeRange && meleeSlot >= 0) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.MELEE, actor.get(),
                            meleeSlot, -1, item);
                }
            }

            return Optional.of(new Decision(scope, Action.NONE, duel, duel, baselineReplica,
                    replica, -1, -1, null));
        } catch (RuntimeException invalidSnapshot) {
            return Optional.empty();
        }
    }

    private static Optional<Decision> decision(DuelRecord current, EchoLoadoutState baselineReplica,
                                               EchoLoadoutState replica,
                                               Scope scope, Action action, ActorState actor,
                                               int itemSlot, int ammoSlot,
                                               EchoLoadoutState.Item actionItem) {
        if (actor.revision() <= current.actor().revision()
                || current.revision() >= RealitySplitEchoDuelState.MAX_COUNTER) return Optional.empty();
        DuelRecord next = current.withActor(current.runtimeGeneration(),
                current.ownerStartingHealth(), current.ownerMaximumHealth(), current.returnDeadlineMillis(),
                actor, current.revision() + 1L, replica.revision(), replica.encode(),
                current.behaviorStrategy());
        return Optional.of(new Decision(scope, action, current, next, baselineReplica,
                replica, itemSlot, ammoSlot, actionItem));
    }

    /**
     * Compensate a synchronously rejected native effect with a newer receipt revision.
     * The prior finite supplies and cooldowns are restored without reopening stale snapshots.
     */
    public static Optional<DuelRecord> rejectedActionCheckpoint(Decision decision) {
        if (decision == null || decision.action() == Action.NONE) return Optional.empty();
        DuelRecord prior = decision.previousDuel();
        DuelRecord charged = decision.nextDuel();
        if (prior.lifecycle() != Lifecycle.ACTIVE || charged.lifecycle() != Lifecycle.ACTIVE
                || prior.revision() >= RealitySplitEchoDuelState.MAX_COUNTER - 2L
                || charged.revision() >= RealitySplitEchoDuelState.MAX_COUNTER
                || charged.actor().revision() >= RealitySplitEchoDuelState.MAX_COUNTER - 1L
                || decision.replica().revision() >= RealitySplitEchoDuelState.MAX_COUNTER - 1L) {
            return Optional.empty();
        }
        try {
            EchoLoadoutState refundedReplica = decision.previousReplica()
                    .copyAtRevision(decision.replica().revision() + 1L);
            ActorState before = prior.actor();
            ActorState refundedActor = new ActorState(before.actorUuid(), before.currentHealth(),
                    before.maximumHealth(), before.lifecycle(), charged.actor().revision() + 1L,
                    before.nextActionTick(), before.meleeCooldownUntilTick(),
                    before.rangedCooldownUntilTick(), before.pendingUseUntilTick(), before.deathSerial());
            DuelRecord refunded = charged.withActor(charged.runtimeGeneration(),
                    prior.ownerStartingHealth(), prior.ownerMaximumHealth(), prior.returnDeadlineMillis(),
                    refundedActor, charged.revision() + 1L, refundedReplica.revision(),
                    refundedReplica.encode(), charged.behaviorStrategy());
            return Optional.of(refunded);
        } catch (RuntimeException invalidCompensation) {
            return Optional.empty();
        }
    }

    private static int firstUsable(int preferred, int fallback) {
        return preferred >= 0 ? preferred : fallback;
    }

    private static ActorState withHealth(ActorState actor, double health) {
        return new ActorState(actor.actorUuid(), health, actor.maximumHealth(), actor.lifecycle(),
                actor.revision(), actor.nextActionTick(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), actor.deathSerial());
    }
}
