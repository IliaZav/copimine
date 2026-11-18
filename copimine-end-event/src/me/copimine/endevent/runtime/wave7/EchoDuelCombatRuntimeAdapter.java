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
    /** A parry only makes sense against a body close enough to actually strike. */
    private static final double PARRY_RANGE_BLOCKS = 5.0D;
    private static final int HEAL_THRESHOLD_PERMILLE = 550;
    /**
     * The "never heals" floor for the recorded sustain share. Any player who
     * healed at least once while acting clears it; a profile with no healing at
     * all stays at zero and the copy never invents the habit.
     */
    private static final int SUSTAIN_FLOOR_PERMILLE = 1;
    private static final EchoDuelCombatPolicy.Cooldowns COOLDOWNS =
            new EchoDuelCombatPolicy.Cooldowns(10, 20, 40, 32, 20, 100, 60);

    private EchoDuelCombatRuntimeAdapter() {
    }

    public enum Action { NONE, MELEE, CRITICAL, RANGED, CONSUMABLE, SHIELD, JUMP, WIND_CHARGE, SPLASH }

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
                              boolean onGround, boolean criticalWindow,
                              long ownerStrikeIntervalTicks, long ticksSinceOwnerStrike) {
        public Observation(long tick, double distanceSquared, boolean lineOfSight) {
            this(tick, distanceSquared, lineOfSight, true, false, -1L, -1L);
        }

        public Observation(long tick, double distanceSquared, boolean lineOfSight,
                           boolean onGround, boolean criticalWindow) {
            this(tick, distanceSquared, lineOfSight, onGround, criticalWindow, -1L, -1L);
        }

        public Observation {
            if (tick < 0 || tick > RealitySplitEchoDuelState.MAX_COUNTER
                    || !Double.isFinite(distanceSquared) || distanceSquared < 0.0D
                    || distanceSquared > 1_000_000.0D
                    || ownerStrikeIntervalTicks < -1L
                    || ownerStrikeIntervalTicks > RealitySplitEchoDuelState.MAX_COUNTER
                    || ticksSinceOwnerStrike < -1L
                    || ticksSinceOwnerStrike > RealitySplitEchoDuelState.MAX_COUNTER) {
                throw new IllegalArgumentException("invalid Echo combat observation");
            }
        }

        /** True when the measured cadence says the owner's next strike is imminent or unknown. */
        boolean incomingStrikeKnown() { return ticksSinceOwnerStrike >= 0L; }
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
                    || (action == Action.WIND_CHARGE || action == Action.SPLASH)
                        && (itemSlot < 0 || ammoSlot != -1 || actionItem == null)
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
     * The legacy five-feature profile has no jump evidence and defaults
     * {@code jumpAttackPermille} to 250. Only a recorded rate above that
     * baseline is read as a real jump-attack habit; a copy of a baseline player
     * must not hop instead of closing.
     */
    private static final int JUMP_ATTACK_BASELINE_PERMILLE = 250;
    /**
     * A copy whose owner swapped weapons this often breaks off a swing to put a
     * shot in instead: swapping tools mid-fight is the visible habit, and a
     * per-tick window keeps it bounded and replayable.
     */
    private static final int WEAPON_SWITCH_FLOOR_PERMILLE = 500;

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
        return movementIntent(strategy, observation, false);
    }

    /**
     * @param wounded the copy is below its healing threshold. A wounded melee
     *                copy used to keep trading blows on the spot and simply
     *                died: healing only engaged when it still carried food.
     *                Breaking contact is the one retreat a real player makes,
     *                and it still cannot outrun a ranged owner because the
     *                ranged branch kites into the hold band as before.
     */
    static MovementIntent movementIntent(EchoBehaviorStrategy strategy, Observation observation,
                                         boolean wounded) {
        // The three-argument form is the case the retreat was written for: a
        // wounded copy that can still heal. A copy that cannot heal must not
        // take this path, see the four-argument overload.
        return movementIntent(strategy, observation, wounded, wounded);
    }

    /**
     * @param canHeal the copy carries a consumable its sustain habit will let it
     *                spend. Retreating is only worth anything when the gap is
     *                being opened to heal: a wounded brawler with an empty
     *                inventory used to kite forever, never re-enter melee reach
     *                and never attack again. That is exactly the "endless
     *                retreat without firing" failure the navigation rule forbids,
     *                and it is also how a player with no healing fights on.
     */
    static MovementIntent movementIntent(EchoBehaviorStrategy strategy, Observation observation,
                                         boolean wounded, boolean canHeal) {
        if (strategy == null || observation == null) return MovementIntent.HOLD;
        double distance = Math.sqrt(observation.distanceSquared());
        // Map 0..1000 onto a 1.5..12 block hold band inside the ranged envelope.
        double preferred = 1.5D + 10.5D * strategy.holdDistancePermille() / 1_000.0D;
        boolean wantsRange = strategy.rangedPermille() > strategy.meleePermille();
        // A brawler copy has to reach melee reach. The band is anchored on the
        // owner's measured distance, but its upper edge is clamped to the melee
        // envelope: a neutral 500 per mille produced a 6.75-block hold with a
        // 1.5-block tolerance, so a melee copy stopped at 8.25 blocks and only
        // ever fought opponents that walked into it, never closing itself.
        if (!wantsRange) {
            preferred = Math.min(preferred, MELEE_RANGE_BLOCKS - HOLD_TOLERANCE_BLOCKS);
        }
        // A wounded brawler breaks contact instead of trading blows it cannot
        // win, but only to buy the ticks its heal needs. A ranged copy already
        // kites, so this only changes melee copies.
        if (wounded && canHeal && !wantsRange) return MovementIntent.KITE;
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
     * BRAIN-013: a copy of a frequent weapon swapper alternates tools mid-fight.
     *
     * <p>{@code weaponSwitchPermille} was recorded and persisted but never read,
     * so every copy behaved as if its owner used a single weapon. High-switch
     * owners get a bounded window in which the copy puts a shot in from close
     * range instead of only swinging, which is the swap a real player makes.
     * The decision is a pure function of the tick and the profile, so it
     * replays identically and needs no random source.</p>
     */
    static boolean swapsWeaponThisTick(EchoBehaviorStrategy strategy, Observation observation) {
        if (strategy == null || observation == null) return false;
        int weight = strategy.weaponSwitchPermille();
        if (weight < WEAPON_SWITCH_FLOOR_PERMILLE) return false;
        return inProfileWindow(observation.tick(), weight);
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

            // Audit ECHO error: an in-flight consumable resolves HERE, at its own
            // deadline, and this is the only place its health is granted. Starting
            // the use only spends the item.
            if (duel.actor().pendingUseUntilTick() > 0L
                    && observation.tick() >= duel.actor().pendingUseUntilTick()) {
                Optional<ActorState> completed = EchoDuelCombatPolicy.completeConsumable(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (completed.isPresent()) {
                    // The supply was spent when the use STARTED, so by now the slot is
                    // empty and there is no item to report. The resolution is a state
                    // change, not a new item action, so it is reported as NONE with
                    // the healed actor: reporting CONSUMABLE without an item is the
                    // shape Decision rejects, and it silently swallowed this tick.
                    return decision(duel, baselineReplica, replica, scope, Action.NONE,
                            completed.get(), -1, -1, null);
                }
            }
            int consumableSlot = replica.find(EchoLoadoutState.Kind.GOLDEN_APPLE);
            if (consumableSlot < 0) consumableSlot = replica.find(EchoLoadoutState.Kind.FOOD);
            // Health and navigation are resolved before the shield short-circuit.
            // A copy holding up its own parry used to return NONE with no intent,
            // which the Decision constructor defaults to HOLD: for the whole
            // shield window (5-20 ticks) it stood still while its owner was free
            // to reposition, and the navigation contract calls that out as the
            // "frozen copy" failure. Reporting the intent it would have chosen
            // anyway keeps the guarded copy visibly moving, and it still grants
            // no action: the shield window stays the only thing happening.
            boolean wounded = duel.actor().currentHealth() * 1_000.0D
                    <= duel.actor().maximumHealth() * HEAL_THRESHOLD_PERMILLE;
            // The retreat is only worth taking when the heal branch below can
            // actually fire this fight: a consumable is carried and the recorded
            // sustain habit clears the floor. Otherwise the copy would kite
            // forever without attacking, which the navigation rule forbids.
            boolean canHeal = consumableSlot >= 0
                    && duel.behaviorStrategy().sustainPermille() >= SUSTAIN_FLOOR_PERMILLE;
            MovementIntent intent = movementIntent(duel.behaviorStrategy(), observation, wounded, canHeal);
            if (duel.actor().shieldActiveUntilTick() > observation.tick()) {
                return Optional.of(new Decision(scope, Action.NONE, duel, duel, baselineReplica,
                        replica, -1, -1, null, intent));
            }
            // Audit ECHO error: sustain is the share of sustained actions that were
            // heals. A player who genuinely heals while fighting lands far below
            // 250 per mille (three heals beside two hundred hits is 15), so the old
            // hard gate rejected real healers. Any recorded heal tendency above the
            // "never heals" floor now allows the copy to keep its own habit.
            if (wounded && duel.behaviorStrategy().sustainPermille() >= SUSTAIN_FLOOR_PERMILLE
                    && consumableSlot >= 0) {
                EchoLoadoutState.Item item = replica.item(consumableSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartConsumable(
                        duel.actor(), replica, consumableSlot, replica.revision(),
                        observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    // Audit ECHO error: the copy used to regain health on the very
                    // tick it STARTED eating, so a visible apple never had to finish
                    // and the health pool was never actually spent. Starting the use
                    // costs the item now; the health itself is applied when the use
                    // duration resolves at the top of the next tick via
                    // EchoDuelCombatPolicy.completeConsumable().
                    return decision(duel, baselineReplica, replica, scope, Action.CONSUMABLE, actor.get(),
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
            boolean inMeleeRange = observation.distanceSquared()
                    <= MELEE_RANGE_BLOCKS * (double) MELEE_RANGE_BLOCKS;
            // BRAIN-013: a copy of a player who swapped weapons constantly does
            // not lock itself to one tool. Inside its recorded switch window it
            // puts a shot in instead of swinging, but only when that shot is
            // actually legal this tick, so the swap can never waste a swing on a
            // bow it cannot draw.
            boolean canShootNow = rangedSlot >= 0 && ammoSlot >= 0 && observation.lineOfSight()
                    && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS
                    && observation.tick() >= duel.actor().rangedCooldownUntilTick()
                    && observation.tick() >= duel.actor().nextActionTick()
                    && observation.tick() >= duel.actor().pendingUseUntilTick();
            boolean swapsToRanged = canShootNow
                    && swapsWeaponThisTick(duel.behaviorStrategy(), observation);

            // Potions: a copy drinks what its owner actually carried. The buff
            // window is derived from the actor's own pending-use deadline, so a
            // restart can neither extend it nor grant a second application.
            boolean potionWindowLive = observation.tick()
                    < lastPotionTick(duel.actor()) + EchoDuelCombatPolicy.POTION_BUFF_TICKS;
            int potionSlot = replica.find(EchoLoadoutState.Kind.POTION);
            if (potionSlot >= 0 && EchoDuelCombatPolicy.wantsPotionBuff(duel.actor(), potionWindowLive,
                    wounded, observation.distanceSquared())) {
                EchoLoadoutState.Item item = replica.item(potionSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartPotion(
                        duel.actor(), replica, potionSlot, replica.revision(),
                        observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.CONSUMABLE, actor.get(),
                            potionSlot, -1, item, intent);
                }
            }

            int shieldSlot = replica.find(EchoLoadoutState.Kind.SHIELD);
            // Reactive parry: the copy raises the guard only for a strike that is
            // actually coming. The cadence is measured from the owner's own
            // admitted hits, so the window covers a real swing instead of a
            // fixed clock phase that has nothing to do with the fight.
            if (shieldSlot >= 0 && observation.distanceSquared()
                    <= PARRY_RANGE_BLOCKS * PARRY_RANGE_BLOCKS) {
                Optional<Long> parry = EchoDuelCombatPolicy.parryHoldTicks(observation.tick(),
                        true, predictedTicksToOwnerStrike(observation),
                        duel.actor().shieldActiveUntilTick(), duel.actor().shieldCooldownUntilTick());
                if (parry.isPresent()) {
                    Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartReactiveShield(
                            duel.actor(), replica, shieldSlot, replica.revision(), observation.tick(),
                            parry.get(), COOLDOWNS);
                    if (actor.isPresent()) {
                        return decision(duel, baselineReplica, replica, scope, Action.SHIELD, actor.get(),
                                shieldSlot, -1, replica.item(shieldSlot), intent);
                    }
                }
            }
            if (shieldSlot >= 0 && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS
                    && !observation.incomingStrikeKnown()
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
            if (inMeleeRange && meleeSlot >= 0 && !swapsToRanged
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

            // A wind charge burst is the copy's own knockback tool: it spends one
            // carried charge and keeps the fight at range the way its owner did.
            int chargeSlot = replica.find(EchoLoadoutState.Kind.WIND_CHARGE);
            if (chargeSlot >= 0 && observation.lineOfSight()
                    && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS) {
                EchoLoadoutState.Item item = replica.item(chargeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartWindCharge(
                        duel.actor(), replica, chargeSlot, replica.revision(), observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.WIND_CHARGE, actor.get(),
                            chargeSlot, -1, item, intent);
                }
            }

            // A thrown splash bottle is a weapon: the copy lobs one of the bottles
            // its owner carried, never a bottle it does not have.
            int splashSlot = replica.find(EchoLoadoutState.Kind.SPLASH_POTION);
            if (splashSlot >= 0 && observation.lineOfSight()
                    && observation.distanceSquared() <= RANGED_RANGE_BLOCKS * (double) RANGED_RANGE_BLOCKS) {
                EchoLoadoutState.Item item = replica.item(splashSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartSplash(
                        duel.actor(), replica, splashSlot, replica.revision(), observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.SPLASH, actor.get(),
                            splashSlot, -1, item, intent);
                }
            }

            // BRAIN-013 jump-attack habit: a copy of an owner who jumped into
            // its strikes closes the gap with a hop, so the arrival itself sets
            // up the falling critical. It runs only after every weapon branch
            // has declined this tick and only while the copy is still outside
            // melee reach, so it never trades a swing for a jump and never
            // substitutes for a legal shot.
            if (observation.onGround() && !inMeleeRange && meleeSlot >= 0
                    && intent == MovementIntent.APPROACH
                    && duel.behaviorStrategy().jumpAttackPermille() > JUMP_ATTACK_BASELINE_PERMILLE
                    && inProfileWindow(observation.tick(), duel.behaviorStrategy().jumpAttackPermille())) {
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartJump(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.JUMP, actor.get(),
                            -1, -1, null, intent);
                }
            }

            if (inMeleeRange && meleeSlot >= 0 && !swapsToRanged) {
                EchoLoadoutState.Item item = replica.item(meleeSlot);
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartMelee(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent() && EchoDuelCombatPolicy.tryApplyGearWear(
                        replica, meleeSlot, 1, replica.revision())) {
                    return decision(duel, baselineReplica, replica, scope, Action.MELEE, actor.get(),
                            meleeSlot, -1, item, intent);
                }
            }

            // BRAIN-006 mobility: a copy of a player who sprinted through their
            // fights still hops around, but hopping is a filler action, not a
            // substitute for a fight. This branch used to run before every
            // attack branch, and mobilityPermille is the owner's SPRINT share
            // (share(sprintSamples, movementSamples)): a player who sprinted in
            // half their movement samples produced a copy that was airborne on
            // half its ground ticks and rarely attacked at all. It now runs only
            // after every legal attack has declined this tick, so the copy still
            // visibly jumps when it has nothing to hit. The deliberate
            // jump-attack hop above is unchanged and still sets up the falling
            // critical.
            if (observation.onGround() && inProfileWindow(
                    observation.tick(), duel.behaviorStrategy().mobilityPermille())) {
                Optional<ActorState> actor = EchoDuelCombatPolicy.tryStartJump(
                        duel.actor(), observation.tick(), COOLDOWNS);
                if (actor.isPresent()) {
                    return decision(duel, baselineReplica, replica, scope, Action.JUMP, actor.get(),
                            -1, -1, null, intent);
                }
            }

            return Optional.of(new Decision(scope, Action.NONE, duel, duel, baselineReplica,
                    replica, -1, -1, null, intent));
        } catch (RuntimeException invalidSnapshot) {
            return Optional.empty();
        }
    }

    /**
     * When the last buff potion was drunk, or zero if it never was.
     *
     * <p>Derived rather than stored: the actor already records the tick its use
     * started through {@code pendingUseUntilTick}, and a buff window that
     * outlives a reconnect must not be inventable.</p>
     */
    private static long lastPotionTick(ActorState actor) {
        return actor == null ? 0L : Math.max(0L, actor.pendingUseUntilTick()
                - COOLDOWNS.consumableUseTicks());
    }

    /**
     * Ticks until the owner's next strike, from the cadence it has actually kept.
     *
     * <p>Zero means "imminent or unknown": the copy then guards as if the next
     * swing can land now, which is what a player does in a clinch before they
     * have read their opponent's rhythm.</p>
     *
     * @return a bounded non-negative prediction, or -1 when the owner has never struck
     */
    static long predictedTicksToOwnerStrike(Observation observation) {
        if (observation == null) return -1L;
        long since = observation.ticksSinceOwnerStrike();
        if (since < 0L) return -1L;
        long interval = observation.ownerStrikeIntervalTicks();
        if (interval <= 0L) return 0L;
        long phase = since % interval;
        return phase == 0L ? 0L : interval - phase;
    }

    /**
     * BRAIN-014: a copy lowers its body to stalk when it closes on an owner it
     * cannot see, and stands up again the moment the fight is in the open.
     */
    public static boolean sneaking(MovementIntent intent, boolean lineOfSight) {
        return intent == MovementIntent.APPROACH && !lineOfSight;
    }

    /**
     * Re-bases a restored actor's absolute tick deadlines onto the live counter.
     *
     * <p>Every deadline is written as {@code actionTick + duration}, so it is
     * only meaningful against the counter that produced it. The duel record is
     * persisted while {@code eventTickCounter} starts at zero on every boot and
     * is never saved: a copy restored after a restart kept, say,
     * {@code nextActionTick = 50_000} and every action gate refused it until
     * the fresh counter climbed to that value — tens of minutes of a frozen
     * copy, or hours on a long event. {@code Wave4ObeliskRecoveryPolicy
     * .restoreTiming} already solves exactly this for the obelisk timers by
     * rebasing the saved relative timers onto the fresh counter; this is the
     * same rule for the Echo duel, applied where the carrier is rebuilt.
     *
     * <p>A saved deadline is at most {@code saveTick + duration}, and every
     * duration is one of the bounded {@link EchoDuelCombatPolicy.Cooldowns}
     * fields, so the real remaining time can never exceed that duration.
     * Clamping each deadline to {@code counter + duration} therefore keeps a
     * cooldown that was still running and forbids inventing a long one. The
     * clamp is also a no-op for a deadline saved earlier on the same boot,
     * because such a value is always below the bound.
     *
     * @param actor   the persisted actor of a duel being re-registered
     * @param counter the live {@code eventTickCounter} the carrier is rebuilt in
     */
    public static ActorState rebaseRestoredDeadlines(ActorState actor, long counter) {
        if (actor == null || counter < 0L || counter > RealitySplitEchoDuelState.MAX_COUNTER) {
            throw new IllegalArgumentException("invalid Echo deadline rebase");
        }
        // The shield window can never outlast its own cooldown, so clamp the
        // cooldown first and keep the window inside the result.
        long shieldCooldownUntil = horizon(counter, COOLDOWNS.shieldCooldownTicks(),
                actor.shieldCooldownUntilTick());
        long shieldActiveUntil = Math.min(shieldCooldownUntil,
                horizon(counter, COOLDOWNS.shieldHoldTicks(), actor.shieldActiveUntilTick()));
        return new ActorState(actor.actorUuid(), actor.currentHealth(), actor.maximumHealth(),
                actor.lifecycle(), actor.revision(),
                horizon(counter, COOLDOWNS.sharedActionTicks(), actor.nextActionTick()),
                horizon(counter, COOLDOWNS.meleeTicks(), actor.meleeCooldownUntilTick()),
                horizon(counter, COOLDOWNS.rangedTicks(), actor.rangedCooldownUntilTick()),
                horizon(counter, COOLDOWNS.consumableUseTicks(), actor.pendingUseUntilTick()),
                actor.deathSerial(), shieldActiveUntil, shieldCooldownUntil,
                horizon(counter, COOLDOWNS.jumpCooldownTicks(), actor.jumpCooldownUntilTick()));
    }

    /** A saved deadline kept only while it still fits the live counter plus its own duration. */
    private static long horizon(long counter, long duration, long savedDeadline) {
        long latest = counter > RealitySplitEchoDuelState.MAX_COUNTER - duration
                ? RealitySplitEchoDuelState.MAX_COUNTER
                : counter + duration;
        return Math.min(savedDeadline, latest);
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
