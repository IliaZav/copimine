import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedData;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;

/** Focused deterministic tests for official personal Echo action and resource policy. */
public final class EchoDuelCombatPolicyTest {
    private static final UUID EVENT = id(700);
    private static final long ATTEMPT = 71;
    private static final long RUNTIME = 73;
    private static int assertions;

    public static void main(String[] args) {
        finiteResourcesWearAndRebindPersistence();
        actionCooldownsGateAndRestore();
        rejectInvalidInputsWithoutSpending();
        System.out.println("EchoDuelCombatPolicyTest OK assertions=" + assertions);
    }

    private static void finiteResourcesWearAndRebindPersistence() {
        Fixture fixture = activeFixture();
        EchoLoadoutState supplies = restoreLoadout(fixture.duel());

        ActorState firstRanged = EchoDuelCombatPolicy.tryStartRanged(fixture.duel().actor(), supplies, 3, 4,
                supplies.revision(), 100, cooldowns()).orElseThrow();
        check(firstRanged.rangedCooldownUntilTick() == 110,
                "ranged action atomically spends one finite arrow and records its deadline");
        check(supplies.remaining(4) == 1, "one of two arrows remains after a successful shot");
        check(EchoDuelCombatPolicy.tryStartRanged(firstRanged, supplies, 3, 4,
                        supplies.revision(), 100, cooldowns()).isEmpty(),
                "same-tick second shot is rejected by the action cooldown");
        check(supplies.remaining(4) == 1, "rejected shot does not consume another arrow");

        ActorState afterRanged = EchoDuelCombatPolicy.tryStartRanged(firstRanged, supplies, 3, 4,
                supplies.revision(), 110, cooldowns()).orElseThrow();
        check(supplies.remaining(4) == 0, "second accepted shot exhausts the arrow stack");
        check(EchoDuelCombatPolicy.tryStartRanged(afterRanged, supplies, 3, 4,
                        supplies.revision(), 120, cooldowns()).isEmpty(),
                "empty ammunition prevents another ranged action");

        long staleRevision = supplies.revision() - 1;
        check(EchoDuelCombatPolicy.tryStartConsumable(afterRanged, supplies, 5,
                        staleRevision, 120, cooldowns()).isEmpty(),
                "stale replica revision cannot spend a consumable");
        check(supplies.remaining(5) == 1, "stale consumable attempt leaves food unchanged");
        ActorState afterFood = EchoDuelCombatPolicy.tryStartConsumable(afterRanged, supplies, 5,
                supplies.revision(), 120, cooldowns()).orElseThrow();
        check(supplies.remaining(5) == 0, "accepted food use spends exactly one food item");
        check(EchoDuelCombatPolicy.tryStartConsumable(afterFood, supplies, 5,
                        supplies.revision(), 130, cooldowns()).isEmpty(),
                "empty food stack cannot be duplicated");

        ActorState afterAppleOne = EchoDuelCombatPolicy.tryStartConsumable(afterFood, supplies, 41,
                supplies.revision(), 140, cooldowns()).orElseThrow();
        check(supplies.remaining(41) == 1, "first allowed golden apple is spent");
        ActorState afterAppleTwo = EchoDuelCombatPolicy.tryStartConsumable(afterAppleOne, supplies, 41,
                supplies.revision(), 150, cooldowns()).orElseThrow();
        check(supplies.remaining(41) == 0, "the fixed allowance contains only two golden apples");
        check(EchoDuelCombatPolicy.tryStartConsumable(afterAppleTwo, supplies, 41,
                        supplies.revision(), 160, cooldowns()).isEmpty(),
                "golden apples cannot be replenished after the allowance is spent");

        long beforeWrongKind = supplies.revision();
        check(EchoDuelCombatPolicy.tryStartRanged(afterAppleTwo, supplies, 0, 4,
                        beforeWrongKind, 170, cooldowns()).isEmpty(),
                "melee gear cannot act as the ranged weapon");
        check(supplies.revision() == beforeWrongKind, "wrong resource kind does not advance revision");

        check(EchoDuelCombatPolicy.tryApplyGearWear(supplies, 0, 249, supplies.revision()),
                "wear is recorded for copied durable melee gear");
        check(supplies.item(0).damage() == 249, "gear wear is visible in duel inventory state");
        check(EchoDuelCombatPolicy.tryApplyGearWear(supplies, 0, 1, supplies.revision()),
                "final durability point breaks the replica item");
        check(supplies.remaining(0) == 0, "broken gear is removed from the usable replica slot");
        check(!EchoDuelCombatPolicy.tryApplyGearWear(supplies, 0, 1, supplies.revision()),
                "broken gear cannot receive repair-like wear updates");
        check(!EchoDuelCombatPolicy.tryApplyGearWear(supplies, 5, 1, supplies.revision()),
                "food cannot receive gear durability updates");

        DuelRecord changed = persist(fixture.duel(), afterAppleTwo, supplies);
        NamedData changedData = withDuel(fixture.data(), fixture.room(), fixture.owner(), changed);
        Map<String, String> encoded = RealitySplitTrialSnapshot.encodeNamed(changedData);
        NamedData decoded = RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT, RUNTIME);
        DuelRecord restored = decoded.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        EchoLoadoutState restoredSupplies = restoreLoadout(restored);
        check(restoredSupplies.remaining(4) == 0 && restoredSupplies.remaining(5) == 0
                        && restoredSupplies.remaining(41) == 0,
                "snapshot restore keeps consumed ammunition, food, and apple quantities");
        check("250".equals(restoredSupplies.encode().get("slot.0.wear"))
                        && restoredSupplies.remaining(0) == 0,
                "snapshot restore keeps final gear wear and broken state");
        check(restoredSupplies.revision() == supplies.revision(),
                "replica revision survives the durable named snapshot");

        ActorState reboundActor = copyActorWithUuid(restored.actor(), id(799), restored.actor().revision() + 1);
        DuelRecord rebound = restored.withActor(RUNTIME + 1, restored.ownerStartingHealth(),
                restored.ownerMaximumHealth(), restored.returnDeadlineMillis(), reboundActor,
                restored.revision() + 1, restored.replicaRevision(), restored.replicaState(),
                restored.behaviorStrategy());
        NamedData reboundData = withDuel(decoded, fixture.room(), fixture.owner(), rebound);
        NamedData reboundDecoded = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(reboundData), EVENT, ATTEMPT);
        DuelRecord afterRestart = reboundDecoded.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        EchoLoadoutState afterRestartSupplies = restoreLoadout(afterRestart);
        check(afterRestart.actor().actorUuid().equals(id(799)), "cold restart binds a new actor identity");
        check(afterRestartSupplies.remaining(4) == 0 && afterRestartSupplies.remaining(5) == 0
                        && afterRestartSupplies.remaining(41) == 0,
                "cold rebind does not refill spent supplies");
        check("250".equals(afterRestartSupplies.encode().get("slot.0.wear")),
                "cold rebind does not repair replica equipment");
    }

    private static void actionCooldownsGateAndRestore() {
        Fixture fixture = activeFixture();
        ActorState initial = fixture.duel().actor();
        ActorState ranged = EchoDuelCombatPolicy.tryStartRanged(initial, restoreLoadout(fixture.duel()), 3, 4,
                0, 100, cooldowns()).orElseThrow();
        check(ranged.nextActionTick() == 102 && ranged.rangedCooldownUntilTick() == 110,
                "ranged start records shared and ranged deadlines");
        check(ranged.meleeCooldownUntilTick() == 0 && ranged.pendingUseUntilTick() == 0,
                "ranged start preserves unrelated action and use state");
        check(EchoDuelCombatPolicy.tryStartMelee(ranged, 101, cooldowns()).isEmpty(),
                "shared action gate blocks an early melee action");
        check(EchoDuelCombatPolicy.tryStartRanged(ranged, restoreLoadout(fixture.duel()), 3, 4,
                        0, 102, cooldowns()).isEmpty(),
                "ranged-specific deadline blocks an early follow-up shot");

        ActorState melee = EchoDuelCombatPolicy.tryStartMelee(ranged, 102, cooldowns()).orElseThrow();
        check(melee.meleeCooldownUntilTick() == 108 && melee.rangedCooldownUntilTick() == 110,
                "melee action sets only the melee deadline and preserves ranged deadline");
        check(EchoDuelCombatPolicy.tryStartMelee(melee, 104, cooldowns()).isEmpty(),
                "melee-specific deadline blocks an early follow-up swing");

        EchoLoadoutState supplies = restoreLoadout(fixture.duel());
        ActorState using = EchoDuelCombatPolicy.tryStartConsumable(melee, supplies, 5,
                supplies.revision(), 104, cooldowns()).orElseThrow();
        check(using.pendingUseUntilTick() == 109,
                "consumable start persists its pending-use completion tick");
        check(EchoDuelCombatPolicy.tryStartMelee(using, 108, cooldowns()).isEmpty(),
                "pending consumable use blocks attacks before completion");
        ActorState afterPending = EchoDuelCombatPolicy.tryStartMelee(using, 109, cooldowns()).orElseThrow();
        check(afterPending.pendingUseUntilTick() == using.pendingUseUntilTick(),
                "completed pending use remains recorded while the next action begins");
        check(afterPending.revision() == using.revision() + 1,
                "each accepted action advances the actor revision once");

        DuelRecord changed = persist(fixture.duel(), using, supplies);
        NamedData saved = withDuel(fixture.data(), fixture.room(), fixture.owner(), changed);
        NamedData restoredData = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(saved), EVENT, ATTEMPT, RUNTIME);
        ActorState restoredActor = restoredData.personalEchoDuels().get(fixture.room())
                .get(fixture.owner()).actor();
        check(restoredActor.nextActionTick() == using.nextActionTick()
                        && restoredActor.meleeCooldownUntilTick() == using.meleeCooldownUntilTick()
                        && restoredActor.rangedCooldownUntilTick() == using.rangedCooldownUntilTick()
                        && restoredActor.pendingUseUntilTick() == using.pendingUseUntilTick(),
                "named snapshot restores all action and pending-use deadlines exactly");
        check(EchoDuelCombatPolicy.tryStartMelee(restoredActor, 108, cooldowns()).isEmpty(),
                "restored cooldown continues to block the action before its deadline");
        check(EchoDuelCombatPolicy.tryStartMelee(restoredActor, 110, cooldowns()).isPresent(),
                "restored melee action becomes available at the persisted deadline");
    }

    private static void rejectInvalidInputsWithoutSpending() {
        Fixture fixture = activeFixture();
        EchoLoadoutState supplies = restoreLoadout(fixture.duel());
        check(EchoDuelCombatPolicy.tryStartRanged(fixture.duel().actor(), supplies, 3, 4,
                        supplies.revision() + 1, 100, cooldowns()).isEmpty(),
                "future replica revision fails closed");
        check(supplies.remaining(4) == 2, "revision mismatch leaves all arrows intact");
        check(EchoDuelCombatPolicy.tryStartMelee(null, 100, cooldowns()).isEmpty(),
                "missing actor fails closed");
        check(EchoDuelCombatPolicy.tryStartMelee(fixture.duel().actor(), -1, cooldowns()).isEmpty(),
                "negative server tick fails closed");
        expectRejected(() -> new EchoDuelCombatPolicy.Cooldowns(-1, 1, 1, 1),
                "negative cooldown configuration is rejected");
        ActorState dead = fixture.duel().actor().died(1);
        check(EchoDuelCombatPolicy.tryStartMelee(dead, 100, cooldowns()).isEmpty(),
                "dead actor cannot begin another action");
    }

    private static Fixture activeFixture() {
        var controller = new RealitySplitTrialController();
        ChamberIsolationPolicy.Assignment assignment = ChamberIsolationPolicy.assign(roster());
        controller.beginNamed(EVENT, ATTEMPT, assignment, List.of(
                NamedTrial.WARDEN, NamedTrial.PERSONAL_ECHO,
                NamedTrial.MOBILE_MARKSMAN, NamedTrial.RIFT_ARCHMAGE));
        NamedData data = controller.namedSnapshot();
        int room = 1;
        UUID owner = data.trials().get(room).owners().iterator().next();
        DuelRecord planned = data.personalEchoDuels().get(room).get(owner);

        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(0, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        items.set(1, new EchoLoadoutState.Item("IRON_HELMET", 1, 165, 0, Map.of(), "", false));
        items.set(2, new EchoLoadoutState.Item("SHIELD", 1, 336, 0, Map.of(), "", false));
        items.set(3, new EchoLoadoutState.Item("BOW", 1, 384, 0, Map.of(), "", false));
        items.set(4, new EchoLoadoutState.Item("ARROW", 2, 0, 0, Map.of(), "", false));
        items.set(5, new EchoLoadoutState.Item("BREAD", 1, 0, 0, Map.of(), "", false));
        EchoLoadoutState supplies = EchoLoadoutState.create(
                EVENT, ATTEMPT, planned.duelId(), owner, items, 0);
        ActorState actor = new ActorState(id(701), 18.0D, 20.0D, ActorLifecycle.ALIVE,
                0, 0, 0, 0, 0, 0);
        DuelRecord active = planned.withActor(RUNTIME, 18.0D, 20.0D, 0L,
                actor, 1L, supplies.revision(), supplies.encode(), profile());
        return new Fixture(withDuel(data, room, owner, active), room, owner, active);
    }

    private static DuelRecord persist(DuelRecord current, ActorState actor, EchoLoadoutState supplies) {
        return current.withActor(current.runtimeGeneration(), current.ownerStartingHealth(),
                current.ownerMaximumHealth(), current.returnDeadlineMillis(), actor,
                current.revision() + 1, supplies.revision(), supplies.encode(), current.behaviorStrategy());
    }

    private static EchoLoadoutState restoreLoadout(DuelRecord duel) {
        return EchoLoadoutState.restore(duel.replicaState(), duel.eventId(), duel.attemptGeneration(),
                duel.duelId(), duel.ownerUuid(), duel.replicaRevision());
    }

    private static NamedData withDuel(NamedData data, int room, UUID owner, DuelRecord duel) {
        var rooms = new LinkedHashMap<>(data.personalEchoDuels());
        var duels = new LinkedHashMap<>(rooms.get(room));
        duels.put(owner, duel);
        rooms.put(room, duels);
        return new NamedData(data.eventId(), data.generation(), data.selectedLayout(), data.trials(), rooms);
    }

    private static ActorState copyActorWithUuid(ActorState actor, UUID uuid, long revision) {
        return new ActorState(uuid, actor.currentHealth(), actor.maximumHealth(), actor.lifecycle(),
                revision, actor.nextActionTick(), actor.meleeCooldownUntilTick(),
                actor.rangedCooldownUntilTick(), actor.pendingUseUntilTick(), actor.deathSerial());
    }

    private static EchoBehaviorStrategy profile() {
        return EchoBehaviorStrategy.from(new EchoBehaviorStrategy.ProfileCounts(
                0, 0, 0, 0, 0, 0, 0, true, 0.0D));
    }

    private static EchoDuelCombatPolicy.Cooldowns cooldowns() {
        return new EchoDuelCombatPolicy.Cooldowns(2, 6, 10, 5);
    }

    private static List<UUID> roster() { return List.of(id(1), id(2), id(3), id(4)); }
    private static UUID id(long value) { return new UUID(0, value); }

    private record Fixture(NamedData data, int room, UUID owner, DuelRecord duel) { }

    private static void expectRejected(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        check(false, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
