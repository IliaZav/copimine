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
    private EchoDuelCombatPolicy() {
    }

    /** Positive tick durations make every accepted action leave a replay-resistant gate. */
    public record Cooldowns(long sharedActionTicks, long meleeTicks,
                            long rangedTicks, long consumableUseTicks) {
        public Cooldowns {
            requireDuration(sharedActionTicks, "shared action cooldown");
            requireDuration(meleeTicks, "melee cooldown");
            requireDuration(rangedTicks, "ranged cooldown");
            requireDuration(consumableUseTicks, "consumable use duration");
        }
    }

    /** Starts a melee action without mutating the actor when any action gate is closed. */
    public static Optional<ActorState> tryStartMelee(ActorState actor, long tick, Cooldowns cooldowns) {
        if (!ready(actor, tick, cooldowns) || tick < actor.meleeCooldownUntilTick()) {
            return Optional.empty();
        }
        return advance(actor, tick, cooldowns.sharedActionTicks(),
                deadline(tick, cooldowns.meleeTicks()), actor.rangedCooldownUntilTick(),
                actor.pendingUseUntilTick());
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
                actor.pendingUseUntilTick());
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
                deadline(tick, cooldowns.consumableUseTicks()));
        if (updated.isEmpty() || !replica.consume(consumableSlot, 1, expectedReplicaRevision)) {
            return Optional.empty();
        }
        return updated;
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
                                                long meleeUntil, long rangedUntil, long pendingUseUntil) {
        long nextAction = deadline(tick, sharedTicks);
        if (nextAction < 0L || meleeUntil < 0L || rangedUntil < 0L || pendingUseUntil < 0L) {
            return Optional.empty();
        }
        return Optional.of(new ActorState(actor.actorUuid(), actor.currentHealth(), actor.maximumHealth(),
                actor.lifecycle(), actor.revision() + 1L, nextAction, meleeUntil,
                rangedUntil, pendingUseUntil, actor.deathSerial()));
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
