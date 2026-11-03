package me.copimine.endevent.domain.wave7;

import java.util.Optional;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;

/**
 * Deterministic combat gates for an official personal Echo duel.
 *
 * This policy has no Bukkit or entity access. Its only mutable argument is the private,
 * bounded duel replica, where a successful action atomically records its own resource cost.
 */
public final class EchoDuelCombatPolicy {
    /**
     * Share of maximum health restored by a completed consumable use. A copy that
     * remembered the owner's real regeneration does not exist: the heal is a
     * bounded, single, late effect.
     */
    public static final double CONSUMABLE_HEAL_FRACTION = 0.2D;

    private EchoDuelCombatPolicy() {
    }

    /** Positive tick durations make every accepted action leave a replay-resistant gate. */
    public record Cooldowns(long sharedActionTicks, long meleeTicks,
                            long rangedTicks, long consumableUseTicks,
                            long shieldHoldTicks, long shieldCooldownTicks,
                            long jumpCooldownTicks) {
        public Cooldowns(long sharedActionTicks, long meleeTicks,
                         long rangedTicks, long consumableUseTicks) {
            this(sharedActionTicks, meleeTicks, rangedTicks, consumableUseTicks, 20L, 100L, 60L);
        }

        public Cooldowns {
            requireDuration(sharedActionTicks, "shared action cooldown");
            requireDuration(meleeTicks, "melee cooldown");
            requireDuration(rangedTicks, "ranged cooldown");
            requireDuration(consumableUseTicks, "consumable use duration");
            requireDuration(shieldHoldTicks, "shield hold duration");
            requireDuration(shieldCooldownTicks, "shield cooldown");
            requireDuration(jumpCooldownTicks, "jump cooldown");
        }
    }

    /** Starts a melee action without mutating the actor when any action gate is closed. */
    public static Optional<ActorState> tryStartMelee(ActorState actor, long tick, Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns) || tick < actor.meleeCooldownUntilTick()) {
            return Optional.empty();
        }
        return advance(actor, tick, cooldowns.sharedActionTicks(),
                deadline(tick, cooldowns.meleeTicks()), actor.rangedCooldownUntilTick(),
                actor.pendingUseUntilTick(), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
    }

    /**
     * Bottle share of an owner's potions that a copy may actually reach.
     *
     * <p>A copy is not entitled to the whole vial rack: without a bound a
     * potion-stocked player would produce an Echo that out-buffs every real duel.
     * Half, rounded down with a floor of one, keeps the copy recognisably the
     * owner's habit while leaving the fight winnable.</p>
     */
    public static final int POTION_COPY_DIVISOR = 2;

    /** How many bottles of one stack the copy is allowed to burn during the duel. */
    public static int potionAllotment(int ownerBottles) {
        if (ownerBottles <= 0) return 0;
        return Math.max(1, ownerBottles / POTION_COPY_DIVISOR);
    }

    /**
     * Longest a self-buff stays live before the copy will not reapply it.
     *
     * <p>Measured in ticks like every other duel deadline. Twenty seconds is
     * shorter than a vanilla potion so a fight cannot be won by stacking
     * buffs, and long enough that a buff is worth spending a bottle on.</p>
     */
    public static final long POTION_BUFF_TICKS = 400L;

    /**
     * Starts drinking a buff potion.
     *
     * <p>Shares the {@code CONSUMABLE} action shape with food on purpose: the
     * client already animates a main-hand use for that pose, so drinking looks
     * exactly like eating — which is what a player does. The copy has no
     * independent potion-withdrawn state, so the allotment is enforced by
     * consuming the bottle here, exactly like a food ration.</p>
     */
    public static Optional<ActorState> tryStartPotion(ActorState actor, EchoLoadoutState replica,
                                                      int potionSlot, long expectedReplicaRevision,
                                                      long tick, Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns)
                || !hasPotion(replica, potionSlot, expectedReplicaRevision)) {
            return Optional.empty();
        }
        Optional<ActorState> updated = advance(actor, tick, cooldowns.sharedActionTicks(),
                actor.meleeCooldownUntilTick(), actor.rangedCooldownUntilTick(),
                deadline(tick, cooldowns.consumableUseTicks()), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
        if (updated.isEmpty() || !replica.consume(potionSlot, 1, expectedReplicaRevision)) {
            return Optional.empty();
        }
        return updated;
    }

    /**
     * A copy only drinks when it is actually hurt or actually preparing to fight.
     *
     * <p>Without this a buffed owner produced a copy that re-drank Swiftness on
     * cooldown from the first tick, burning its whole allotment before contact.</p>
     */
    public static boolean wantsPotionBuff(ActorState actor, boolean buffLive, boolean ownHealthLow,
                                          double distanceSquared) {
        if (actor == null || actor.lifecycle() != ActorLifecycle.ALIVE || buffLive) return false;
        if (ownHealthLow) return true;
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
                && distanceSquared <= 64.0D;
    }

    /** A falling attack shares melee wear/cooldown but is separately admitted by the runtime. */
    public static Optional<ActorState> tryStartCriticalMelee(ActorState actor, long tick,
                                                              Cooldowns cooldowns) {
        return tryStartMelee(actor, tick, cooldowns);
    }

    /**
     * Starts a ranged action and spends exactly one arrow from duel-local supplies.
     * Failed gates, a wrong slot, or a stale replica revision leave both states unchanged.
     */
    public static Optional<ActorState> tryStartRanged(ActorState actor, EchoLoadoutState replica,
                                                       int weaponSlot, int ammoSlot,
                                                       long expectedReplicaRevision, long tick,
                                                       Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns) || tick < actor.rangedCooldownUntilTick()
                || !hasRangedWeapon(replica, weaponSlot, expectedReplicaRevision)
                || !hasKind(replica, ammoSlot, expectedReplicaRevision, EchoLoadoutState.Kind.AMMO)) {
            return Optional.empty();
        }
        Optional<ActorState> updated = advance(actor, tick, cooldowns.sharedActionTicks(),
                actor.meleeCooldownUntilTick(), deadline(tick, cooldowns.rangedTicks()),
                actor.pendingUseUntilTick(), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
        if (updated.isEmpty() || !replica.consume(ammoSlot, 1, expectedReplicaRevision)) {
            return Optional.empty();
        }
        return updated;
    }

    /**
     * Begins a consumable use and spends one allowed food/apple from duel-local supplies.
     * The actor snapshot stores the use deadline so reconnect/restart cannot refund or replay it.
     */
    public static Optional<ActorState> tryStartConsumable(ActorState actor, EchoLoadoutState replica,
                                                           int consumableSlot, long expectedReplicaRevision,
                                                           long tick, Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns)
                || !hasConsumable(replica, consumableSlot, expectedReplicaRevision)) {
            return Optional.empty();
        }
        Optional<ActorState> updated = advance(actor, tick, cooldowns.sharedActionTicks(),
                actor.meleeCooldownUntilTick(), actor.rangedCooldownUntilTick(),
                deadline(tick, cooldowns.consumableUseTicks()), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
        if (updated.isEmpty() || !replica.consume(consumableSlot, 1, expectedReplicaRevision)) {
            return Optional.empty();
        }
        return updated;
    }

    /**
     * Resolves a consumable use once its window elapsed.
     *
     * <p>The supply was spent when the use began, so this is the only place a
     * heal may happen. Before this existed the food was consumed immediately and
     * healed immediately, which let a copy heal at the start of the animation and
     * again on reconnect. Completing an already completed snapshot fails, so a
     * restore or a replay cannot heal twice.
     *
     * @param tick the current tick; the use resolves only at or after its deadline
     */
    public static Optional<ActorState> completeConsumable(ActorState actor, long tick,
                                                          Cooldowns cooldowns) {
        if (actor == null || cooldowns == null || tick < 0L
                || tick > RealitySplitEchoDuelState.MAX_COUNTER
                || actor.lifecycle() != ActorLifecycle.ALIVE
                || actor.pendingUseUntilTick() <= 0L
                || tick < actor.pendingUseUntilTick()
                || actor.revision() >= RealitySplitEchoDuelState.MAX_COUNTER) {
            return Optional.empty();
        }
        double restored = Math.min(actor.maximumHealth(),
                actor.currentHealth() + consumableHealAmount(actor.maximumHealth()));
        return Optional.of(new ActorState(actor.actorUuid(), restored, actor.maximumHealth(),
                actor.lifecycle(), actor.revision() + 1L, actor.nextActionTick(),
                actor.meleeCooldownUntilTick(), actor.rangedCooldownUntilTick(),
                0L, actor.deathSerial(), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick()));
    }

    /**
     * Ends a use that was interrupted (the copy moved, was hit or died).
     *
     * <p>No heal happens and the spent supply is deliberately not refunded:
     * returning it would let a copy farm unlimited healing by starting and
     * cancelling uses in a loop.
     */
    public static ActorState cancelConsumable(ActorState actor) {
        if (actor == null || actor.pendingUseUntilTick() <= 0L
                || actor.revision() >= RealitySplitEchoDuelState.MAX_COUNTER) {
            return actor;
        }
        return new ActorState(actor.actorUuid(), actor.currentHealth(), actor.maximumHealth(),
                actor.lifecycle(), actor.revision() + 1L, actor.nextActionTick(),
                actor.meleeCooldownUntilTick(), actor.rangedCooldownUntilTick(),
                0L, actor.deathSerial(), actor.shieldActiveUntilTick(),
                actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick());
    }

    /** A healed copy restores a bounded share of its maximum health, never an unlimited amount. */
    private static double consumableHealAmount(double maximumHealth) {
        return Math.max(0.0D, maximumHealth) * CONSUMABLE_HEAL_FRACTION;
    }

    /** Starts a finite shield window without spending shield durability until a real hit is blocked. */
    public static Optional<ActorState> tryStartShield(ActorState actor, EchoLoadoutState replica,
                                                       int shieldSlot, long expectedReplicaRevision,
                                                       long tick, long holdTicks, Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns) || tick < actor.shieldCooldownUntilTick()
                || holdTicks <= 0L || holdTicks > cooldowns.shieldHoldTicks()
                || !hasKind(replica, shieldSlot, expectedReplicaRevision, EchoLoadoutState.Kind.SHIELD)
                || replica.item(shieldSlot).maximumDamage() <= 0) {
            return Optional.empty();
        }
        long activeUntil = deadline(tick, holdTicks);
        long cooldownUntil = deadline(tick, cooldowns.shieldCooldownTicks());
        if (activeUntil < 0L || cooldownUntil < activeUntil) return Optional.empty();
        return advance(actor, tick, cooldowns.sharedActionTicks(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), activeUntil,
                cooldownUntil, actor.jumpCooldownUntilTick());
    }

    /** Starts a bounded vertical hop; horizontal motion remains under the room-scoped pathfinder. */
    public static Optional<ActorState> tryStartJump(ActorState actor, long tick,
                                                     Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns) || tick < actor.jumpCooldownUntilTick()) {
            return Optional.empty();
        }
        long jumpCooldownUntil = deadline(tick, cooldowns.jumpCooldownTicks());
        if (jumpCooldownUntil < 0L) return Optional.empty();
        return advance(actor, tick, cooldowns.sharedActionTicks(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(),
                actor.shieldActiveUntilTick(), actor.shieldCooldownUntilTick(), jumpCooldownUntil);
    }

    /** Block only an admitted, owner-originated, front-facing hit during a persisted shield window. */
    public static boolean tryBlockWithShield(ActorState actor, EchoLoadoutState replica,
                                              int shieldSlot, long expectedReplicaRevision,
                                              long tick, boolean ownerAttackAdmitted,
                                              boolean attackerInFront, double incomingDamage) {
        if (actor == null || actor.lifecycle() != ActorLifecycle.ALIVE
                || tick < 0L || tick >= actor.shieldActiveUntilTick()
                || !ownerAttackAdmitted || !attackerInFront
                || !Double.isFinite(incomingDamage) || incomingDamage <= 0.0D
                || incomingDamage > 1_000_000.0D
                || !hasKind(replica, shieldSlot, expectedReplicaRevision, EchoLoadoutState.Kind.SHIELD)) {
            return false;
        }
        EchoLoadoutState.Item shield = replica.item(shieldSlot);
        int wear = (int) Math.min(10_000L, 1L + (long) Math.floor(incomingDamage));
        return shield.maximumDamage() > 0
                && tryApplyGearWear(replica, shieldSlot, wear, expectedReplicaRevision);
    }

    /** Records native-computed wear only on a usable, durable piece of copied duel gear. */
    public static boolean tryApplyGearWear(EchoLoadoutState replica, int gearSlot,
                                           int wearAmount, long expectedReplicaRevision) {
        if (replica == null || gearSlot < 0 || gearSlot >= 42 || wearAmount <= 0 || wearAmount > 10_000
                || replica.revision() != expectedReplicaRevision) {
            return false;
        }
        EchoLoadoutState.Item item = replica.item(gearSlot);
        EchoLoadoutState.Kind kind = item.kind();
        if (item.maximumDamage() <= 0 || !item.usable()
                || kind != EchoLoadoutState.Kind.MELEE
                && kind != EchoLoadoutState.Kind.ARMOR
                && kind != EchoLoadoutState.Kind.BOW
                && kind != EchoLoadoutState.Kind.CROSSBOW
                && kind != EchoLoadoutState.Kind.SHIELD) {
            return false;
        }
        return replica.damage(gearSlot, wearAmount, expectedReplicaRevision);
    }

    private static boolean ready(ActorState actor, long tick, Cooldowns cooldowns) {
        return actor != null && cooldowns != null && tick >= 0L
                && tick <= RealitySplitEchoDuelState.MAX_COUNTER
                && actor.lifecycle() == ActorLifecycle.ALIVE
                && actor.revision() < RealitySplitEchoDuelState.MAX_COUNTER
                && tick >= actor.nextActionTick()
                && tick >= actor.pendingUseUntilTick();
    }

    private static boolean hasConsumable(EchoLoadoutState replica, int slot, long expectedRevision) {
        if (!validReplicaSlot(replica, slot, expectedRevision)) return false;
        EchoLoadoutState.Kind kind = replica.item(slot).kind();
        return (kind == EchoLoadoutState.Kind.FOOD || kind == EchoLoadoutState.Kind.GOLDEN_APPLE)
                && replica.remaining(slot) > 0;
    }

    /**
     * The drink gate is strict on purpose: only a drinkable POTION passes.
     *
     * <p>A thrown bottle is a weapon, not a ration. Admitting SPLASH_POTION
     * here made the copy "drink" its own splash bottle — the animation plays,
     * the bottle is spent, and nothing lands anywhere. An earlier version of
     * this gate accepted all three potion kinds and only the test caught it.</p>
     */
    public static boolean hasPotion(EchoLoadoutState replica, int slot, long expectedRevision) {
        if (!validReplicaSlot(replica, slot, expectedRevision)) return false;
        return replica.item(slot).kind() == EchoLoadoutState.Kind.POTION
                && replica.remaining(slot) > 0;
    }

    private static boolean hasKind(EchoLoadoutState replica, int slot, long expectedRevision,
                                   EchoLoadoutState.Kind kind) {
        return validReplicaSlot(replica, slot, expectedRevision)
                && replica.item(slot).kind() == kind && replica.remaining(slot) > 0;
    }

    private static boolean hasRangedWeapon(EchoLoadoutState replica, int slot, long expectedRevision) {
        if (!validReplicaSlot(replica, slot, expectedRevision)) return false;
        EchoLoadoutState.Kind kind = replica.item(slot).kind();
        return (kind == EchoLoadoutState.Kind.BOW || kind == EchoLoadoutState.Kind.CROSSBOW)
                && replica.remaining(slot) > 0;
    }

    private static boolean validReplicaSlot(EchoLoadoutState replica, int slot, long expectedRevision) {
        return replica != null && slot >= 0 && slot < 42
                && replica.revision() == expectedRevision;
    }

    private static Optional<ActorState> advance(ActorState actor, long tick, long sharedTicks,
                                                long meleeUntil, long rangedUntil, long pendingUseUntil,
                                                long shieldActiveUntil, long shieldCooldownUntil,
                                                long jumpCooldownUntil) {
        long nextAction = deadline(tick, sharedTicks);
        if (nextAction < 0L || meleeUntil < 0L || rangedUntil < 0L || pendingUseUntil < 0L
                || shieldActiveUntil < 0L || shieldCooldownUntil < shieldActiveUntil
                || jumpCooldownUntil < 0L) {
            return Optional.empty();
        }
        return Optional.of(new ActorState(actor.actorUuid(), actor.currentHealth(), actor.maximumHealth(),
                actor.lifecycle(), actor.revision() + 1L, nextAction, meleeUntil,
                rangedUntil, pendingUseUntil, actor.deathSerial(), shieldActiveUntil,
                shieldCooldownUntil, jumpCooldownUntil));
    }

    /** Returns -1 if a deadline would exceed the persisted counter bounds. */
    private static long deadline(long tick, long duration) {
        if (tick < 0L || duration <= 0L || tick > RealitySplitEchoDuelState.MAX_COUNTER - duration) {
            return -1L;
        }
        return tick + duration;
    }

    private static void requireDuration(long duration, String label) {
        if (duration <= 0L || duration > RealitySplitEchoDuelState.MAX_COUNTER) {
            throw new IllegalArgumentException("invalid Echo " + label);
        }
    }
}
