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
        rangedDrawLifecycleCancelsAndCharges();
        rejectInvalidInputsWithoutSpending();
        System.out.println("EchoDuelCombatPolicyTest OK assertions=" + assertions);
    }

    private static void finiteResourcesWearAndRebindPersistence() {
        Fixture fixture = activeFixture();
        EchoLoadoutState supplies = restoreLoadout(fixture.duel());

        // E-05: a bow draw starts without spending the arrow, and only the release
        // at the recorded draw deadline spends it and opens the ranged cadence.
        ActorState drawing = EchoDuelCombatPolicy.tryStartRangedDraw(fixture.duel().actor(), supplies,
                3, 4, supplies.revision(), 100, cooldowns().rangedTicks(), cooldowns()).orElseThrow();
        check(drawing.rangedChargeUntilTick() == 110,
                "a bow draw records its own release deadline without spending the arrow");
        check(drawing.rangedCooldownUntilTick() == 0,
                "starting a draw does not open the ranged cadence yet");
        check(supplies.remaining(4) == 2, "starting a draw spends no arrows");
        check(EchoDuelCombatPolicy.tryReleaseRanged(drawing, supplies, 3, 4,
                        supplies.revision(), 105, cooldowns()).isEmpty(),
                "a shot before the charge deadline is refused");
        check(supplies.remaining(4) == 2, "an early release attempt spends no arrow");
        ActorState firstRanged = EchoDuelCombatPolicy.tryReleaseRanged(drawing, supplies, 3, 4,
                supplies.revision(), 110, cooldowns()).orElseThrow();
        check(firstRanged.rangedChargeUntilTick() == 0,
                "release clears the persisted charge");
        check(firstRanged.rangedCooldownUntilTick() == 110 && firstRanged.nextActionTick() == 112,
                "release spends one arrow and leaves the shared gate as the only recovery");
        check(supplies.remaining(4) == 1, "one of two arrows remains after a successful shot");
        check(EchoDuelCombatPolicy.tryStartRangedDraw(firstRanged, supplies, 3, 4,
                        supplies.revision(), 110, cooldowns().rangedTicks(), cooldowns()).isEmpty(),
                "same-tick redraw is rejected by the ranged action cooldown");
        check(supplies.remaining(4) == 1, "rejected draw does not consume another arrow");

        ActorState redraw = EchoDuelCombatPolicy.tryStartRangedDraw(firstRanged, supplies, 3, 4,
                supplies.revision(), 120, cooldowns().rangedTicks(), cooldowns()).orElseThrow();
        ActorState afterRanged = EchoDuelCombatPolicy.tryReleaseRanged(redraw, supplies, 3, 4,
                supplies.revision(), 130, cooldowns()).orElseThrow();
        check(supplies.remaining(4) == 0, "second accepted shot exhausts the arrow stack");
        check(EchoDuelCombatPolicy.tryStartRangedDraw(afterRanged, supplies, 3, 4,
                        supplies.revision(), 140, cooldowns().rangedTicks(), cooldowns()).isEmpty(),
                "empty ammunition prevents another draw");

        long staleRevision = supplies.revision() - 1;
        check(EchoDuelCombatPolicy.tryStartConsumable(afterRanged, supplies, 5,
                        staleRevision, 140, cooldowns()).isEmpty(),
                "stale replica revision cannot spend a consumable");
        check(supplies.remaining(5) == 1, "stale consumable attempt leaves food unchanged");
        ActorState afterFood = EchoDuelCombatPolicy.tryStartConsumable(afterRanged, supplies, 5,
                supplies.revision(), 140, cooldowns()).orElseThrow();
        check(supplies.remaining(5) == 0, "accepted food use spends exactly one food item");
        check(EchoDuelCombatPolicy.tryStartConsumable(afterFood, supplies, 5,
                        supplies.revision(), 150, cooldowns()).isEmpty(),
                "empty food stack cannot be duplicated");

        // The replica carries only what its owner held: the reserved slot is
        // never seeded with bonus golden apples (GEAR-005, AUD-13), so a
        // consumable request there is rejected instead of granting free healing.
        ActorState afterAppleAttempt = afterFood;
        check(EchoDuelCombatPolicy.tryStartConsumable(afterAppleAttempt, supplies, 41,
                        supplies.revision(), 150, cooldowns()).isEmpty(),
                "the empty reserved slot grants no healing the owner never carried");
        check(supplies.remaining(41) == 0, "the reserved slot holds no invented golden apples");

        long beforeWrongKind = supplies.revision();
        check(EchoDuelCombatPolicy.tryStartRangedDraw(afterAppleAttempt, supplies, 0, 4,
                        beforeWrongKind, 170, cooldowns().rangedTicks(), cooldowns()).isEmpty(),
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

        DuelRecord changed = persist(fixture.duel(), afterAppleAttempt, supplies);
        NamedData changedData = withDuel(fixture.data(), fixture.room(), fixture.owner(), changed);
        Map<String, String> encoded = RealitySplitTrialSnapshot.encodeNamed(changedData);
        NamedData decoded = RealitySplitTrialSnapshot.decodeNamed(encoded, EVENT, ATTEMPT, RUNTIME);
        DuelRecord restored = decoded.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        EchoLoadoutState restoredSupplies = restoreLoadout(restored);
        check(restoredSupplies.remaining(4) == 0 && restoredSupplies.remaining(5) == 0
                        && restoredSupplies.remaining(41) == 0,
                "snapshot restore keeps consumed ammunition, food, and the empty allowance slot");
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
        // E-05: the draw opens the shared gate and records the charge deadline
        // only; the ranged cadence opens at the actual release.
        ActorState drawing = EchoDuelCombatPolicy.tryStartRangedDraw(initial,
                restoreLoadout(fixture.duel()), 3, 4, 0, 100, cooldowns().rangedTicks(),
                cooldowns()).orElseThrow();
        check(drawing.nextActionTick() == 102 && drawing.rangedChargeUntilTick() == 110,
                "a draw records the shared gate and the charge deadline");
        check(drawing.rangedCooldownUntilTick() == 0,
                "a draw does not open the ranged cadence");
        check(drawing.meleeCooldownUntilTick() == 0 && drawing.pendingUseUntilTick() == 0,
                "a draw preserves unrelated action and use state");
        check(EchoDuelCombatPolicy.tryStartMelee(drawing, 101, cooldowns()).isEmpty(),
                "shared action gate blocks an early melee action");
        ActorState ranged = EchoDuelCombatPolicy.tryReleaseRanged(drawing,
                restoreLoadout(fixture.duel()), 3, 4, 0, 110, cooldowns()).orElseThrow();
        check(ranged.nextActionTick() == 112 && ranged.rangedCooldownUntilTick() == 110,
                "release records the shared gate without a second ranged lockout");
        // The recorded charge, not a duplicated recovery, paces the next shot:
        // the shared action gate is the only interval the release opens.
        check(EchoDuelCombatPolicy.tryStartRangedDraw(ranged, restoreLoadout(fixture.duel()),
                        3, 4, 0, 111, cooldowns().rangedTicks(), cooldowns()).isEmpty(),
                "the shared action gate blocks a redraw one tick after release");
        check(EchoDuelCombatPolicy.tryStartRangedDraw(ranged, restoreLoadout(fixture.duel()),
                        3, 4, 0, 112, cooldowns().rangedTicks(), cooldowns()).isPresent(),
                "a redraw is legal the tick the shared gate reopens");

        ActorState melee = EchoDuelCombatPolicy.tryStartMelee(ranged, 112, cooldowns()).orElseThrow();
        check(melee.meleeCooldownUntilTick() == 118 && melee.rangedCooldownUntilTick() == 110,
                "melee action sets only the melee deadline and preserves the release tick");
        check(EchoDuelCombatPolicy.tryStartMelee(melee, 114, cooldowns()).isEmpty(),
                "melee-specific deadline blocks an early follow-up swing");

        EchoLoadoutState supplies = restoreLoadout(fixture.duel());
        ActorState using = EchoDuelCombatPolicy.tryStartConsumable(melee, supplies, 5,
                supplies.revision(), 118, cooldowns()).orElseThrow();
        check(using.pendingUseUntilTick() == 123,
                "consumable start persists its pending-use completion tick");
        check(EchoDuelCombatPolicy.tryStartMelee(using, 122, cooldowns()).isEmpty(),
                "pending consumable use blocks attacks before completion");
        ActorState afterPending = EchoDuelCombatPolicy.tryStartMelee(using, 123, cooldowns()).orElseThrow();
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
                        && restoredActor.pendingUseUntilTick() == using.pendingUseUntilTick()
                        && restoredActor.rangedChargeUntilTick() == using.rangedChargeUntilTick(),
                "named snapshot restores all action and pending-use deadlines exactly");
        check(EchoDuelCombatPolicy.tryStartMelee(restoredActor, 122, cooldowns()).isEmpty(),
                "restored cooldown continues to block the action before its deadline");
        check(EchoDuelCombatPolicy.tryStartMelee(restoredActor, 123, cooldowns()).isPresent(),
                "restored melee action becomes available at the persisted deadline");
    }

    private static void rangedDrawLifecycleCancelsAndCharges() {
        Fixture fixture = activeFixture();
        EchoLoadoutState supplies = restoreLoadout(fixture.duel());
        // A draw in flight cancels without spending the arrow or opening the cadence.
        ActorState drawing = EchoDuelCombatPolicy.tryStartRangedDraw(fixture.duel().actor(), supplies,
                3, 4, supplies.revision(), 200, 25, cooldowns()).orElseThrow();
        check(drawing.rangedChargeUntilTick() == 225,
                "a draw records its crossbow-length charge deadline");
        check(drawing.rangedCooldownUntilTick() == 0, "a draw opens no ranged cadence");
        long arrowsBefore = supplies.remaining(4);
        ActorState cancelled = EchoDuelCombatPolicy.cancelRangedDraw(drawing).orElseThrow();
        check(cancelled.rangedChargeUntilTick() == 0, "cancelling a draw clears the charge");
        check(supplies.remaining(4) == arrowsBefore, "cancelling a draw spends no arrow");
        check(cancelled.rangedCooldownUntilTick() == 0, "cancelling a draw opens no ranged cadence");
        check(!EchoDuelCombatPolicy.rangedReadyToRelease(cancelled, 999),
                "a cancelled draw is never release-ready");
        check(EchoDuelCombatPolicy.cancelRangedDraw(cancelled).isEmpty(),
                "cancelling a draw twice fails closed");
        // A draw in flight cannot be restarted, and is only release-ready at its own deadline.
        check(EchoDuelCombatPolicy.tryStartRangedDraw(drawing, supplies, 3, 4, supplies.revision(),
                        210, 10, cooldowns()).isEmpty(), "a draw in flight blocks a second draw");
        check(!EchoDuelCombatPolicy.rangedReadyToRelease(drawing, 224),
                "a draw is not release-ready before its deadline");
        check(EchoDuelCombatPolicy.rangedReadyToRelease(drawing, 225),
                "a draw is release-ready at its exact deadline");

        DuelRecord charged = persist(fixture.duel(), drawing, supplies);
        NamedData data = withDuel(fixture.data(), fixture.room(), fixture.owner(), charged);
        NamedData decoded = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(data), EVENT, ATTEMPT, RUNTIME);
        ActorState restored = decoded.personalEchoDuels().get(fixture.room()).get(fixture.owner()).actor();
        check(restored.rangedChargeUntilTick() == 225,
                "a nonzero charge deadline survives the durable named snapshot");
    }

    private static void rejectInvalidInputsWithoutSpending() {
        Fixture fixture = activeFixture();
        EchoLoadoutState supplies = restoreLoadout(fixture.duel());
        check(EchoDuelCombatPolicy.tryStartRangedDraw(fixture.duel().actor(), supplies, 3, 4,
                        supplies.revision() + 1, 100, cooldowns().rangedTicks(), cooldowns()).isEmpty(),
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
