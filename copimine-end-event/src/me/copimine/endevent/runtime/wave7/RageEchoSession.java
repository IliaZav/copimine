package me.copimine.endevent.runtime.wave7;

import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle;

/**
 * §10.4.3 / E-01: a rare Personal Echo session owned by one live Rage-phase
 * participant.
 *
 * <p>The session holds a synthetic {@link DuelRecord} so the common combat
 * engine ({@link EchoDuelCombatRuntimeAdapter}) can drive it without the Wave 7
 * duel orchestrator: no room lock, no trial checkpoints, no rewards, no
 * teleport of the owner. The engine itself is unchanged; only the ownership
 * adapter differs.</p>
 *
 * <p>This class stays Bukkit-free (it holds an {@link EchoLoadoutState}, not a
 * live inventory) so the whole admission and lifetime contract can be proven by
 * the pure gate.</p>
 */
public final class RageEchoSession {
    /** The synthetic room index for a Rage summon; it never joins Wave 7 rooms. */
    public static final int ROOM = 0;

    private final UUID eventId;
    private final long generation;
    private final long epoch;
    private final UUID duelId;
    private final UUID ownerId;
    private final UUID carrierId;
    private final UUID worldId;
    private final long spawnTick;
    private EchoLoadoutState replica;
    private final EchoBehaviorStrategy strategy;
    private final double ownerMaxHealth;
    private final double ownerStartingHealth;
    private DuelRecord duel;
    private boolean active = true;
    private boolean totemSpent;
    private long lastSummonTick;

    public RageEchoSession(UUID eventId, long generation, long epoch,
                           UUID duelId, UUID ownerId, UUID carrierId,
                           UUID worldId, long spawnTick,
                           EchoLoadoutState replica,
                           EchoBehaviorStrategy strategy,
                           double ownerMaxHealth, double ownerStartingHealth) {
        if (eventId == null || duelId == null || ownerId == null || carrierId == null
                || worldId == null || replica == null || strategy == null
                || generation <= 0L || epoch < generation || spawnTick < 0L
                || !Double.isFinite(ownerMaxHealth) || ownerMaxHealth <= 0.0D
                || !Double.isFinite(ownerStartingHealth) || ownerStartingHealth <= 0.0D
                || ownerStartingHealth > ownerMaxHealth) {
            throw new IllegalArgumentException("invalid Rage Echo session");
        }
        if (duelId.equals(ownerId) || duelId.equals(carrierId) || ownerId.equals(carrierId)) {
            throw new IllegalArgumentException("Rage Echo identities must be distinct");
        }
        this.eventId = eventId;
        this.generation = generation;
        this.epoch = epoch;
        this.duelId = duelId;
        this.ownerId = ownerId;
        this.carrierId = carrierId;
        this.worldId = worldId;
        this.spawnTick = spawnTick;
        this.replica = replica;
        this.strategy = strategy;
        this.ownerMaxHealth = ownerMaxHealth;
        this.ownerStartingHealth = ownerStartingHealth;
        this.lastSummonTick = spawnTick;
        this.duel = buildDuel();
    }

    private DuelRecord buildDuel() {
        // §10.4.3: the copy's health is the owner's maximum scaled by the
        // proposal band (×1.5–2.0), never the Wave 7 duel's ×2.5. The duel
        // record's "owner" health fields are the copy's own pool, because the
        // Rage adapter never reads them as the player's (no owner health bar,
        // no duel reward, no forced personal reset); the shared engine only
        // reads the actor health. The record's cross-field invariant is
        // satisfied because both fields describe the same copy.
        double copyHealth = Math.max(1.0D, Math.min(
                me.copimine.endevent.domain.RageEchoSummonPolicy.summonHealth(ownerMaxHealth),
                RealitySplitEchoDuelState.MAX_HEALTH));
        ActorState actor = new ActorState(carrierId, copyHealth, copyHealth,
                ActorLifecycle.ALIVE, 0L, 0L, 0L, 0L, 0L, 0L);
        return new DuelRecord(eventId, generation, epoch, ROOM, duelId, ownerId,
                Lifecycle.ACTIVE, copyHealth, copyHealth,
                0L, 1L, actor, replica.revision(), replica.encode(), strategy);
    }

    public UUID eventId() { return eventId; }

    public long generation() { return generation; }

    public long epoch() { return epoch; }

    public UUID duelId() { return duelId; }

    public UUID ownerId() { return ownerId; }

    public UUID carrierId() { return carrierId; }

    public UUID worldId() { return worldId; }

    public long spawnTick() { return spawnTick; }

    public EchoLoadoutState replica() { return replica; }

    public EchoLoadoutState actionProposal() {
        return EchoLoadoutState.restore(duel.replicaState(), eventId, generation, duelId, ownerId, replica.revision());
    }

    public EchoBehaviorStrategy strategy() { return strategy; }

    public DuelRecord duel() { return duel; }

    public boolean active() { return active; }

    public long lastSummonTick() { return lastSummonTick; }

    /** Commit the accepted action and its private supplies as one actor-local state change. */
    public boolean commit(EchoDuelCombatRuntimeAdapter.Decision decision) {
        if (!active || decision == null || !duel.equals(decision.previousDuel())
                || replica.revision() != decision.previousReplica().revision()
                || !duel.replicaState().equals(decision.previousReplica().encode())) return false;
        DuelRecord next = decision.nextDuel();
        if (!eventId.equals(next.eventId()) || generation != next.attemptGeneration()
                || epoch != next.runtimeGeneration() || !duelId.equals(next.duelId())
                || !ownerId.equals(next.ownerUuid()) || !carrierId.equals(next.actor().actorUuid())
                || next.replicaRevision() != decision.replica().revision()
                || !next.replicaState().equals(decision.replica().encode())) return false;
        duel = next;
        replica = decision.replica();
        return true;
    }

    public boolean totemSpent() { return totemSpent; }

    /** Apply a native block or revival only after its exact supply cost is validated. */
    public boolean commitDefense(EchoLoadoutState supplies, double health, long shieldUntil, boolean totem) {
        ActorState actor = duel.actor();
        if (!active || supplies == null || supplies.revision() != replica.revision() + 1L
                || !Double.isFinite(health) || health <= 0.0D || health > actor.maximumHealth()
                || totem && totemSpent) return false;
        ActorState updated = new ActorState(carrierId, health, actor.maximumHealth(), actor.lifecycle(),
                actor.revision() + 1L, actor.nextActionTick(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), actor.deathSerial(),
                shieldUntil, actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick(),
                actor.rangedChargeUntilTick());
        DuelRecord next = duel.withActor(epoch, duel.ownerStartingHealth(), duel.ownerMaximumHealth(),
                duel.returnDeadlineMillis(), updated, duel.revision() + 1L, supplies.revision(),
                supplies.encode(), strategy);
        duel = next;
        replica = supplies;
        if (totem) totemSpent = true;
        return true;
    }

    /** The physical carrier owns actual health between decisions. */
    public void synchronizeHealth(double currentHealth) {
        ActorState actor = duel.actor();
        if (!active || !Double.isFinite(currentHealth) || currentHealth <= 0.0D) return;
        double health = Math.min(currentHealth, actor.maximumHealth());
        if (Double.compare(health, actor.currentHealth()) == 0) return;
        ActorState updated = new ActorState(carrierId, health, actor.maximumHealth(), actor.lifecycle(),
                actor.revision() + 1L, actor.nextActionTick(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), actor.deathSerial(),
                actor.shieldActiveUntilTick(), actor.shieldCooldownUntilTick(), actor.jumpCooldownUntilTick(),
                actor.rangedChargeUntilTick());
        duel = duel.withActor(epoch, duel.ownerStartingHealth(), duel.ownerMaximumHealth(),
                duel.returnDeadlineMillis(), updated, duel.revision() + 1L, replica.revision(),
                replica.encode(), strategy);
    }

    /**
     * Ends the session. The duel record is completed with a dead actor so the
     * shared engine refuses any further tick even if a caller keeps the handle.
     */
    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        if (duel != null && duel.lifecycle() == Lifecycle.ACTIVE
                && duel.actor() != null && duel.actor().lifecycle() == ActorLifecycle.ALIVE) {
            this.duel = duel.died(1L);
        }
    }

    public void touchSummonTick(long tick) { this.lastSummonTick = tick; }

    /** True when the Echo has exceeded its lifetime budget. */
    public boolean expired(long tick) {
        long lived = Math.max(0L, tick - spawnTick);
        return lived >= me.copimine.endevent.domain.RageEchoSummonPolicy.lifetimeTicks();
    }

    /** True when the actor is no longer alive in the duel record. */
    public boolean actorDead() {
        return duel == null || duel.actor() == null
                || duel.actor().lifecycle() != ActorLifecycle.ALIVE;
    }

    public boolean actorAlive() {
        return !actorDead();
    }

    /** True when the actor can be driven this tick. */
    public boolean canTick() {
        return active && duel != null && duel.lifecycle() == Lifecycle.ACTIVE && actorAlive();
    }

    /** The common-engine scope for this actor. All region flags are arena-true. */
    public EchoDuelCombatRuntimeAdapter.Scope scope() {
        return new EchoDuelCombatRuntimeAdapter.Scope(eventId, generation, epoch,
                ROOM, duelId, ownerId, carrierId, worldId,
                true, true, true, true);
    }

    /** Stable diagnostic view. */
    public Map<String, String> describe() {
        return Map.of(
                "duel", duelId.toString(),
                "carrier", carrierId.toString(),
                "owner", ownerId.toString(),
                "active", Boolean.toString(active),
                "spawnTick", Long.toString(spawnTick));
    }
}
