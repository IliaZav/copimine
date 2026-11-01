package me.copimine.endevent.runtime.wave7;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
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
            new EchoDuelCombatPolicy.Cooldowns(10, 20, 40, 32, 20, 100, 60);

    private EchoDuelCombatRuntimeAdapter() {
    }

    public enum Action { NONE, MELEE, CRITICAL, RANGED, CONSUMABLE, SHIELD, JUMP }

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

    public record Observation(long tick, double distanceSquared, boolean lineOfSight,
                              boolean onGround, boolean criticalWindow) {
        public Observation(long tick, double distanceSquared, boolean lineOfSight) {
            this(tick, distanceSquared, lineOfSight, true, false);
        }

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
                           EchoLoadoutState.Item actionItem, MovementIntent intent) {
        /** Action-only decision that keeps the copy where it is. */
        public Decision(Scope scope, Action action, DuelRecord previousDuel, DuelRecord nextDuel,
                        EchoLoadoutState previousReplica, EchoLoadoutState replica,
                        int itemSlot, int ammoSlot, EchoLoadoutState.Item actionItem) {
            this(scope, action, previousDuel, nextDuel, previousReplica, replica,
                    itemSlot, ammoSlot, actionItem, MovementIntent.HOLD);
        }

        public Decision {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(previousDuel, "previousDuel");
            Objects.requireNonNull(nextDuel, "nextDuel");
            Objects.requireNonNull(previousReplica, "previousReplica");
            Objects.requireNonNull(replica, "replica");
            intent = intent == null ? MovementIntent.HOLD : intent;
            if (action == Action.NONE && (itemSlot != -1 || ammoSlot != -1 || actionItem != null)
                    || action == Action.MELEE && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
                    || action == Action.CRITICAL && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
                    || action == Action.RANGED && (itemSlot < 0 || ammoSlot < 0 || actionItem == null)
                    || action == Action.CONSUMABLE && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
                    || action == Action.SHIELD && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
                    || action == Action.JUMP && (itemSlot != -1 || ammoSlot != -1 || actionItem != null)) {
                throw new IllegalArgumentException("invalid Echo combat decision");
            }
        }
    }

    /**
     * Where the copy wants to be relative to its owner this tick.
     *
     * <p>BRAIN-005 keeps navigation separate from discrete action choice: the
     * runtime asks for one intent and moves the carrier itself, while combat
     * actions still go through the cooldown-checked policies. A copy that must
     * reposition does no action that tick, which is what makes a ranged owner's
     * Echo back off instead of standing in melee.</p>
     */
    public enum MovementIntent {
        /** Already in the owner's preferred band. */
        HOLD,
        /** Owner fights close; the copy closes the gap to melee reach. */
        APPROACH,
        /** Owner keeps distance; the copy opens the gap toward its hold band. */
        KITE,
        /** Sideways weave, used to break line-of-sight camping. */
        STRAFE_LEFT,
        STRAFE_RIGHT
    }

    /** Inside this band the copy stops repositioning and fights. */
    private static final double HOLD_TOLERANCE_BLOCKS = 1.5D;

    /**
     * BRAIN-006/011: pick a bounded movement intent from the compiled profile.
     *
     * <p>The band is derived from the owner's measured preferred distance
     * ({@code holdDistancePermille}) rather than a fixed radius, so a player who
     * always fights at range produces a copy that actually kites. Lateral work
     * is only requested while a ranged hold is being kept, mirroring how a real
     * archer strafes to stay evasive without abandoning the shot.</p>
     */
    static MovementIntent movementIntent(EchoBehaviorStrategy strategy, Observation observation) {
        if (strategy == null || observation == null) return MovementIntent.HOLD;
        double distance = Math.sqrt(observation.distanceSquared());
        // Map 0..1000 onto a 1.5..12 block hold band inside the ranged envelope.
        double preferred = 1.5D + 10.5D * strategy.holdDistancePermille() / 1_000.0D;
        boolean wantsRange = strategy.rangedPermille() > strategy.meleePermille();
        if (wantsRange) {
            if (distance < preferred - HOLD_TOLERANCE_BLOCKS) return MovementIntent.KITE;
            if (distance > preferred + HOLD_TOLERANCE_BLOCKS
                    && distance > RANGED_RANGE_BLOCKS) return MovementIntent.APPROACH;
            return lateralIntent(strategy, observation);
        }
        if (distance > preferred + HOLD_TOLERANCE_BLOCKS) return MovementIntent.APPROACH;
        return lateralIntent(strategy, observation);
    }

    /**
     * BRAIN-011: weave sideways with the owner's recorded rhythm. The choice is a
     * pure function of the tick and the profile, so it is reproducible on replay
     * and needs no per-copy random source.
     */
    private static MovementIntent lateralIntent(EchoBehaviorStrategy strategy, Observation observation) {
        int kite = strategy.kitePermille();
        int mobility = strategy.mobilityPermille();
        if (kite <= 0 || mobility <= 0) return MovementIntent.HOLD;
        // Weave in bursts: roughly every 12 ticks decide a side, weighted by how
        // often the owner strafed at all.
        long phase = observation.tick() / 12L;
        long window = phase % 10L;
        long threshold = Math.max(1L, 10L * kite / 1_000L);
        if (window >= threshold) return MovementIntent.HOLD;
        boolean left = (phase / 10L) % 2L == 0L;
        return left ? MovementIntent.STRAFE_LEFT : MovementIntent.STRAFE_RIGHT;
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
            if (duel.actor().shieldActiveUntilTick() > observation.tick()) {
                return Optional.of(new Decision(scope, Action.NONE, duel, duel, baselineReplica,
                        replica, -1, -1, null));
            }
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

            // BRAIN-005/011/013: navigation is chosen separately from the discrete
            // action, and the two are reported together. Reporting the intent
            // while still selecting the action keeps a ranged copy shooting while
            // it opens the gap — the "endless retreat without firing" failure the
            // contract forbids — and keeps a mid-jump or shielded copy visibly
            // repositioning instead of freezing on the spot. Only a legal current
            // action is selected here, so movement never grants range and never
            // bypasses a cooldown.
            MovementIntent intent = movementIntent(duel.behaviorStrategy(), observation);
            boolean inMeleeRange = observation.distanceSquared()
                    <= MELEE_RANGE_BLOCKS * (double) MELEE_RANGE_BLOCKS;

            int shieldSlot = replica.find(EchoLoadoutState.Kind.SHIELD);
            if (shieldSlot >= 0 && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS
                    && inProfileWindow(observation.tick(), duel.behaviorStrategy().defensePermille())) {
                long holdTicks = 5L + 15L * duel.behaviorStrategy().defensePermille() / 1_000L;
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartShield(
                        duel.actor(), replica, shieldSlot, replica.revision(), observation.tick(),
                        holdTicks, COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.SHIELD, actor.get(),
                            shieldSlot, -1, replica.item(shieldSlot), intent);
                }
            }

            if (observation.onGround() && inProfileWindow(
                    observation.tick(), duel.behaviorStrategy().mobilityPermille())) {
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartJump(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.JUMP, actor.get(),
                            -1, -1, null, intent);
                }
            }

            if (inMeleeRange && !observation.onGround() && observation.criticalWindow() && meleeSlot >= 0) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartCriticalMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.CRITICAL, actor.get(),
                            meleeSlot, -1, item, intent);
                }
            }
            if (inMeleeRange && meleeSlot >= 0
                    && duel.behaviorStrategy().meleePermille() >= duel.behaviorStrategy().rangedPermille()) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.MELEE, actor.get(),
                            meleeSlot, -1, item, intent);
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
                            rangedSlot, ammoSlot, item, intent);
                }
            }

            if (inMeleeRange && meleeSlot >= 0) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.MELEE, actor.get(),
                            meleeSlot, -1, item, intent);
                }
            }

            return Optional.of(new Decision(scope, Action.NONE, duel, duel, baselineReplica,
                    replica, -1, -1, null, intent));
        } catch (RuntimeException invalidSnapshot) {
            return Optional.empty();
        }
    }

    private static Optional<Decision> decision(DuelRecord current, EchoLoadoutState baselineReplica,
                                               EchoLoadoutState replica,
                                               Scope scope, Action action, ActorState actor,
                                               int itemSlot, int ammoSlot,
                                               EchoLoadoutState.Item actionItem) {
        return decision(current, baselineReplica, replica, scope, action, actor,
                itemSlot, ammoSlot, actionItem, MovementIntent.HOLD);
    }

    private static Optional<Decision> decision(DuelRecord current, EchoLoadoutState baselineReplica,
                                               EchoLoadoutState replica,
                                               Scope scope, Action action, ActorState actor,
                                               int itemSlot, int ammoSlot,
                                               EchoLoadoutState.Item actionItem,
                                               MovementIntent intent) {
        if (actor.revision() <= current.actor().revision()
                || current.revision() >= RealitySplitEchoDuelState.MAX_COUNTER) return Optional.empty();
        DuelRecord next = current.withActor(current.runtimeGeneration(),
                current.ownerStartingHealth(), current.ownerMaximumHealth(), current.returnDeadlineMillis(),
                actor, current.revision() + 1L, replica.revision(), replica.encode(),
                current.behaviorStrategy());
        return Optional.of(new Decision(scope, action, current, next, baselineReplica,
                replica, itemSlot, ammoSlot, actionItem, intent));
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
                    before.rangedCooldownUntilTick(), before.pendingUseUntilTick(), before.deathSerial(),
                    before.shieldActiveUntilTick(), before.shieldCooldownUntilTick(),
                    before.jumpCooldownUntilTick());
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

    private static boolean inProfileWindow(long tick, int weightPermille) {
        if (tick < 0L || weightPermille <= 0 || weightPermille > 1_000) return false;
        long window = Math.max(1L, (weightPermille + 9L) / 10L);
        return tick % 100L < window;
    }

    private static ActorState withHealth(ActorState actor, double health) {
        return new ActorState(actor.actorUuid(), health, actor.maximumHealth(), actor.lifecycle(),
                actor.revision(), actor.nextActionTick(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), actor.deathSerial(),
                actor.shieldActiveUntilTick(), actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
    }
}
