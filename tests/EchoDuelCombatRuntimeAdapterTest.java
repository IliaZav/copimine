import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedData;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Action;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Decision;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Observation;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Scope;

/** Adapter-level regressions for the official named Echo five-tick runtime seam. */
public final class EchoDuelCombatRuntimeAdapterTest {
    private static final UUID EVENT = id(100);
    private static final long ATTEMPT = 81;
    private static final long RUNTIME = 84;
    private static int assertions;

    public static void main(String[] args) {
        acceptedRangedActionsAreScopedAndDurablySpendSupplies();
        meleeWearAndConsumableHealingAreCheckpoints();
        profileDrivenMobilitySchedulesBoundedJumpActions();
        profileDrivenDefenseSchedulesOwnerBoundShieldActions();
        criticalStrikeRequiresARealFallingWindow();
        schemaFourEchoStateUpgradesWithoutInventingActionCooldowns();
        rejectedNativeEffectsRestoreSuppliesAndCooldowns();
        rejectForeignOrIncompleteRuntimeContexts();
        System.out.println("EchoDuelCombatRuntimeAdapterTest OK assertions=" + assertions);
    }

    private static void acceptedRangedActionsAreScopedAndDurablySpendSupplies() {
        Fixture fixture = activeFixture(18.0D);
        EchoLoadoutState replica = restore(fixture.duel());
        Scope scope = scope(fixture.duel(), true, true, true, true);

        Decision shot = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), replica, scope,
                new Observation(100, 100.0D, true)).orElseThrow();
        check(shot.action() == Action.RANGED, "the official adapter selects a ranged action");
        check(replica.remaining(4) == 1, "ranged decision stages exactly one duel-local arrow");
        check(replica.item(3).damage() == 1, "ranged decision stages wear on the duel-local bow");
        check(shot.nextDuel().actor().rangedCooldownUntilTick() == 140,
                "ranged decision stages its cooldown in the actor checkpoint");
        check(shot.nextDuel().replicaRevision() == replica.revision()
                        && shot.nextDuel().replicaState().equals(replica.encode()),
                "the staged duel carries finite supplies and their revision for atomic persistence");

        check(fixture.controller().registerPersonalEchoDuel(shot.nextDuel()),
                "official controller accepts the exact resource and cooldown checkpoint");
        check(!fixture.controller().registerPersonalEchoDuel(fixture.duel()),
                "official controller rejects a stale pre-action snapshot");
        NamedData savedData = fixture.controller().namedSnapshot();
        NamedData restoredData = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(savedData), EVENT, ATTEMPT, RUNTIME);
        DuelRecord restoredDuel = restoredData.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        EchoLoadoutState restoredReplica = restore(restoredDuel);
        check(restoredReplica.remaining(4) == 1 && restoredReplica.item(3).damage() == 1
                        && restoredDuel.actor().rangedCooldownUntilTick() == 140,
                "snapshot restore keeps the accepted shot, finite arrow, bow wear and cooldown together");

        Decision cooldown = EchoDuelCombatRuntimeAdapter.tick(restoredDuel, restoredReplica,
                scope(restoredDuel, true, true, true, true), new Observation(105, 100.0D, true))
                .orElseThrow();
        check(cooldown.action() == Action.NONE && restoredReplica.remaining(4) == 1,
                "the five-tick official loop cannot replay a shot during its cooldown");
    }

    private static void meleeWearAndConsumableHealingAreCheckpoints() {
        Fixture fixture = activeFixture(18.0D);
        EchoLoadoutState replica = restore(fixture.duel());
        Decision melee = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), replica,
                scope(fixture.duel(), true, true, true, true), new Observation(200, 4.0D, true))
                .orElseThrow();
        check(melee.action() == Action.MELEE, "close range selects the frozen owner's melee weapon");
        check(replica.item(0).damage() == 1,
                "melee decision stages durability before its native damage attempt");
        check(melee.nextDuel().actor().meleeCooldownUntilTick() == 220,
                "melee decision stages its cooldown before the next official tick");

        DuelRecord wounded = withHealth(fixture.duel(), 8.0D);
        EchoLoadoutState supplies = restore(wounded);
        Decision food = EchoDuelCombatRuntimeAdapter.tick(wounded, supplies,
                scope(wounded, true, true, true, true), new Observation(300, 4.0D, true))
                .orElseThrow();
        check(food.action() == Action.CONSUMABLE, "low-health Echo selects its owner-bound finite apple allowance");
        check(supplies.remaining(41) == 1, "the consumable decision stages one golden apple allowance");
        check(food.nextDuel().actor().currentHealth() == 12.0D,
                "planned healing and its item cost are staged in the same duel snapshot");
        check(food.nextDuel().actor().pendingUseUntilTick() == 332,
                "pending-use time scopes the item action across reconnect and restart");
        NamedData woundedData = withDuel(fixture.data(), fixture.room(), fixture.owner(), wounded);
        NamedData restoredFoodData = RealitySplitTrialSnapshot.decodeNamed(RealitySplitTrialSnapshot.encodeNamed(
                withDuel(woundedData, fixture.room(), fixture.owner(), food.nextDuel())), EVENT, ATTEMPT, RUNTIME);
        DuelRecord restoredFood = restoredFoodData.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        check(restoredFood.actor().currentHealth() == 12.0D && restore(restoredFood).remaining(41) == 1
                        && restoredFood.actor().pendingUseUntilTick() == 332,
                "snapshot restore keeps the staged consumable, projected healing and pending-use state together");
    }

    private static void profileDrivenMobilitySchedulesBoundedJumpActions() {
        var mobility = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                500, 500, 1_000, 0, 0, 1_000, 0, true);
        Fixture fixture = activeFixture(18.0D, mobility);
        Decision jump = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true), new Observation(100, 10_000.0D, false))
                .orElseThrow();
        check("JUMP".equals(jump.action().name()),
                "high observed mobility selects a real bounded jump when no attack is available");
        check(jump.nextDuel().actor().nextActionTick() > 100,
                "jump action consumes the shared persisted action cooldown");

        DuelRecord saved = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(withDuel(fixture.data(), fixture.room(),
                        fixture.owner(), jump.nextDuel())), EVENT, ATTEMPT, RUNTIME)
                .personalEchoDuels().get(fixture.room()).get(fixture.owner());
        Decision cooldown = EchoDuelCombatRuntimeAdapter.tick(saved, restore(saved),
                scope(saved, true, true, true, true), new Observation(105, 10_000.0D, false))
                .orElseThrow();
        check(!"JUMP".equals(cooldown.action().name()),
                "restart snapshot and action cooldown prevent an immediate jump replay");
    }

    private static void profileDrivenDefenseSchedulesOwnerBoundShieldActions() {
        var defense = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                500, 500, 0, 1_000, 0, 1_000, 0, true);
        Fixture fixture = activeFixture(18.0D, defense);
        Decision shield = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true), new Observation(100, 100.0D, true))
                .orElseThrow();
        check("SHIELD".equals(shield.action().name()),
                "high observed defense selects a real shield raise inside the assigned duel cell");
        check(shield.itemSlot() == 2 && shield.nextDuel().actor().nextActionTick() > 100,
                "shield action binds the owner's copied shield and persists its shared action gate");
        check(shield.nextDuel().actor().shieldActiveUntilTick() == 120
                        && shield.nextDuel().actor().shieldCooldownUntilTick() == 200,
                "shield window and cooldown are checkpointed for reconnect and restart");

        DuelRecord saved = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(withDuel(fixture.data(), fixture.room(),
                        fixture.owner(), shield.nextDuel())), EVENT, ATTEMPT, RUNTIME)
                .personalEchoDuels().get(fixture.room()).get(fixture.owner());
        check(saved.actor().shieldActiveUntilTick() == 120
                        && saved.actor().shieldCooldownUntilTick() == 200,
                "snapshot restore retains the active shield window and its cooldown");
        Decision hold = EchoDuelCombatRuntimeAdapter.tick(saved, restore(saved),
                scope(saved, true, true, true, true), new Observation(105, 100.0D, true))
                .orElseThrow();
        check(hold.action() == Action.NONE,
                "an active persisted shield window cannot be replaced by another action");

        EchoLoadoutState blockedReplica = restore(saved);
        check(me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy.tryBlockWithShield(
                        saved.actor(), blockedReplica, 2, blockedReplica.revision(), 105, true, true, 5.0D),
                "an admitted owner hit from the front is stopped by the active personal shield");
        check(blockedReplica.item(2).damage() == 6,
                "a real shield block consumes finite copied durability based on incoming damage");
        check(!me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy.tryBlockWithShield(
                        saved.actor(), blockedReplica, 2, blockedReplica.revision(), 106, true, false, 3.0D)
                        && blockedReplica.item(2).damage() == 6,
                "rear attacks bypass the shield without consuming its durability");
        check(!me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy.tryBlockWithShield(
                        saved.actor(), blockedReplica, 2, blockedReplica.revision(), 106, false, true, 3.0D)
                        && blockedReplica.item(2).damage() == 6,
                "a foreign or unadmitted attack cannot use the owner-bound shield receipt");
    }

    private static void criticalStrikeRequiresARealFallingWindow() {
        Fixture fixture = activeFixture(18.0D);
        Decision critical = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(800, 4.0D, true, false, true)).orElseThrow();
        check(critical.action() == Action.CRITICAL,
                "the falling-only melee window selects a separately receipted critical strike");
        check(critical.nextDuel().actor().meleeCooldownUntilTick() == 820
                        && critical.replica().item(0).damage() == 1,
                "critical strike spends the same finite melee durability and cooldown");

        Decision grounded = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(900, 4.0D, true, true, false)).orElseThrow();
        check(grounded.action() == Action.MELEE,
                "a grounded Echo cannot claim the critical damage multiplier");
        Decision forgedWindow = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(901, 4.0D, true, true, true)).orElseThrow();
        check(forgedWindow.action() == Action.MELEE,
                "a caller cannot claim a critical multiplier while the Echo is grounded");
    }

    private static void schemaFourEchoStateUpgradesWithoutInventingActionCooldowns() {
        Fixture fixture = activeFixture(18.0D);
        Map<String, String> schemaFour = new LinkedHashMap<>(
                RealitySplitTrialSnapshot.encodeNamed(fixture.data()));
        schemaFour.put("reality-split-trial.schema", "4");
        String duelKey = "reality-split-trial.echo.duel." + fixture.room() + "." + fixture.owner();
        String[] duel = schemaFour.get(duelKey).split("\\|", -1);
        String[] actor = duel[11].split(":", -1);
        check(actor.length == 13, "current Echo checkpoints encode all bounded action deadlines");
        duel[11] = String.join(":", java.util.Arrays.copyOf(actor, 10));
        schemaFour.put(duelKey, String.join("|", duel));

        DuelRecord upgraded = RealitySplitTrialSnapshot.decodeNamed(schemaFour, EVENT, ATTEMPT, RUNTIME)
                .personalEchoDuels().get(fixture.room()).get(fixture.owner());
        check(upgraded.actor().shieldActiveUntilTick() == 0L
                        && upgraded.actor().shieldCooldownUntilTick() == 0L
                        && upgraded.actor().jumpCooldownUntilTick() == 0L,
                "known schema-four duels gain zeroed, canonical action deadlines without rerolling state");
        check(upgraded.duelId().equals(fixture.duel().duelId())
                        && upgraded.replicaState().equals(fixture.duel().replicaState()),
                "schema upgrade preserves duel identity and all prior finite supplies");
    }

    private static void rejectedNativeEffectsRestoreSuppliesAndCooldowns() {
        Fixture rangedFixture = activeFixture(18.0D);
        EchoLoadoutState rangedReplica = restore(rangedFixture.duel());
        Decision failedSpawn = EchoDuelCombatRuntimeAdapter.tick(rangedFixture.duel(), rangedReplica,
                scope(rangedFixture.duel(), true, true, true, true), new Observation(500, 100.0D, true))
                .orElseThrow();
        DuelRecord rangedRefund = EchoDuelCombatRuntimeAdapter.rejectedActionCheckpoint(failedSpawn).orElseThrow();
        check(rangedFixture.controller().registerPersonalEchoDuel(failedSpawn.nextDuel()),
                "controller durably accepts the attempted cost before native spawn");
        check(rangedFixture.controller().registerPersonalEchoDuel(rangedRefund),
                "controller accepts the monotonic refund after failed native spawn");
        EchoLoadoutState rangedAfterRestart = restore(rangedRefund);
        check(rangedRefund.revision() > failedSpawn.nextDuel().revision()
                        && rangedRefund.replicaRevision() > failedSpawn.nextDuel().replicaRevision(),
                "rejected spawn refunds advance duel and replica receipts monotonically");
        check(rangedAfterRestart.remaining(4) == 2 && rangedAfterRestart.item(3).damage() == 0
                        && rangedRefund.actor().rangedCooldownUntilTick() == 0,
                "failed spawn restores arrow, bow wear and cooldown");
        NamedData savedRefund = rangedFixture.controller().namedSnapshot();
        DuelRecord afterRefundRestore = RealitySplitTrialSnapshot.decodeNamed(
                RealitySplitTrialSnapshot.encodeNamed(savedRefund), EVENT, ATTEMPT, RUNTIME)
                .personalEchoDuels().get(rangedFixture.room()).get(rangedFixture.owner());
        check(restore(afterRefundRestore).remaining(4) == 2
                        && restore(afterRefundRestore).item(3).damage() == 0,
                "snapshot round-trip retains compensation without making the failed arrow spend replayable");

        Fixture meleeFixture = activeFixture(18.0D);
        Decision cancelledDamage = EchoDuelCombatRuntimeAdapter.tick(meleeFixture.duel(),
                restore(meleeFixture.duel()), scope(meleeFixture.duel(), true, true, true, true),
                new Observation(600, 4.0D, true)).orElseThrow();
        DuelRecord meleeRefund = EchoDuelCombatRuntimeAdapter.rejectedActionCheckpoint(cancelledDamage)
                .orElseThrow();
        check(restore(meleeRefund).item(0).damage() == 0
                        && meleeRefund.actor().meleeCooldownUntilTick() == 0,
                "cancelled or zero-damage native event refunds melee wear and cooldown");

        Fixture healFixture = activeFixture(8.0D);
        Decision failedHeal = EchoDuelCombatRuntimeAdapter.tick(healFixture.duel(),
                restore(healFixture.duel()), scope(healFixture.duel(), true, true, true, true),
                new Observation(700, 4.0D, true)).orElseThrow();
        DuelRecord healRefund = EchoDuelCombatRuntimeAdapter.rejectedActionCheckpoint(failedHeal)
                .orElseThrow();
        check(restore(healRefund).remaining(41) == 2
                        && healRefund.actor().currentHealth() == 8.0D
                        && healRefund.actor().pendingUseUntilTick() == 0,
                "failed health update refunds the apple, health projection and pending-use deadline");
    }

    private static void rejectForeignOrIncompleteRuntimeContexts() {
        Fixture fixture = activeFixture(18.0D);
        EchoLoadoutState original = restore(fixture.duel());
        Scope scope = scope(fixture.duel(), true, true, true, true);

        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        new Scope(id(999), ATTEMPT, RUNTIME, fixture.room(), fixture.duel().duelId(),
                                fixture.owner(), fixture.duel().actor().actorUuid(), id(900),
                                true, true, true, true), new Observation(400, 4.0D, true)).isEmpty(),
                "foreign event cannot issue an action in this duel");
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        new Scope(EVENT, ATTEMPT, RUNTIME + 1, fixture.room(), fixture.duel().duelId(),
                                fixture.owner(), fixture.duel().actor().actorUuid(), id(900),
                                true, true, true, true), new Observation(400, 4.0D, true)).isEmpty(),
                "stale runtime generation cannot issue an action");
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        new Scope(EVENT, ATTEMPT, RUNTIME, fixture.room(), fixture.duel().duelId(),
                                id(777), fixture.duel().actor().actorUuid(), id(900),
                                true, true, true, true), new Observation(400, 4.0D, true)).isEmpty(),
                "a different owner cannot spend this Echo's supplies");
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        scope(fixture.duel(), false, true, true, true), new Observation(400, 4.0D, true)).isEmpty(),
                "missing READY/combat admission fails closed");
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        scope(fixture.duel(), true, false, true, true), new Observation(400, 4.0D, true)).isEmpty(),
                "a server/world switch fails closed");
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), original,
                        scope(fixture.duel(), true, true, false, true), new Observation(400, 4.0D, true)).isEmpty(),
                "an owner outside their duel cell fails closed");
        check(original.remaining(4) == 2 && original.item(0).damage() == 0,
                "rejected contexts leave arrows and equipment unchanged");

        EchoLoadoutState stale = restore(fixture.duel());
        EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), stale, scope, new Observation(401, 4.0D, true))
                .orElseThrow();
        check(EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), stale, scope,
                        new Observation(402, 4.0D, true)).isEmpty(),
                "a mutated replica cannot be replayed against the old persisted receipt");
    }

    private static Fixture activeFixture(double health) {
        return activeFixture(health, new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                500, 500, 0, 0, 500, 0, 0, true));
    }

    private static Fixture activeFixture(double health,
            me.copimine.endevent.domain.wave7.EchoBehaviorStrategy strategy) {
        var controller = new RealitySplitTrialController();
        var assignment = ChamberIsolationPolicy.assign(List.of(id(1), id(2), id(3), id(4)));
        controller.beginNamed(EVENT, ATTEMPT, assignment, List.of(NamedTrial.WARDEN,
                NamedTrial.PERSONAL_ECHO, NamedTrial.MOBILE_MARKSMAN, NamedTrial.RIFT_ARCHMAGE));
        var data = controller.namedSnapshot();
        int room = 1;
        UUID owner = data.trials().get(room).owners().iterator().next();
        DuelRecord planned = data.personalEchoDuels().get(room).get(owner);
        var items = new ArrayList<EchoLoadoutState.Item>();
        for (int slot = 0; slot < 41; slot++) items.add(EchoLoadoutState.Item.empty());
        items.set(0, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        items.set(2, new EchoLoadoutState.Item("SHIELD", 1, 336, 0, Map.of(), "", false));
        items.set(3, new EchoLoadoutState.Item("BOW", 1, 384, 0, Map.of(), "", false));
        items.set(4, new EchoLoadoutState.Item("ARROW", 2, 0, 0, Map.of(), "", false));
        items.set(5, new EchoLoadoutState.Item("BREAD", 1, 0, 0, Map.of(), "", false));
        EchoLoadoutState supplies = EchoLoadoutState.create(EVENT, ATTEMPT,
                planned.duelId(), owner, items, 0);
        ActorState actor = new ActorState(id(701), health, 20.0D, ActorLifecycle.ALIVE,
                0, 0, 0, 0, 0, 0);
        DuelRecord duel = planned.withActor(RUNTIME, health, 20.0D, 0L, actor,
                1L, supplies.revision(), supplies.encode(), strategy);
        if (!controller.registerPersonalEchoDuel(duel)) {
            throw new AssertionError("fixture Echo duel must pass official controller admission");
        }
        return new Fixture(controller, controller.namedSnapshot(), room, owner, duel);
    }

    private static DuelRecord withHealth(DuelRecord current, double health) {
        ActorState actor = current.actor();
        ActorState changed = new ActorState(actor.actorUuid(), health, actor.maximumHealth(),
                actor.lifecycle(), actor.revision() + 1, actor.nextActionTick(),
                actor.meleeCooldownUntilTick(), actor.rangedCooldownUntilTick(),
                actor.pendingUseUntilTick(), actor.deathSerial());
        return current.withActor(current.runtimeGeneration(), current.ownerStartingHealth(),
                current.ownerMaximumHealth(), current.returnDeadlineMillis(), changed,
                current.revision() + 1, current.replicaRevision(), current.replicaState(),
                current.behaviorStrategy());
    }

    private static EchoLoadoutState restore(DuelRecord duel) {
        return EchoLoadoutState.restore(duel.replicaState(), duel.eventId(), duel.attemptGeneration(),
                duel.duelId(), duel.ownerUuid(), duel.replicaRevision());
    }

    private static Scope scope(DuelRecord duel, boolean admitted, boolean sameWorld,
                               boolean ownerInCell, boolean actorInCell) {
        return new Scope(duel.eventId(), duel.attemptGeneration(), duel.runtimeGeneration(), duel.room(),
                duel.duelId(), duel.ownerUuid(), duel.actor().actorUuid(), id(900),
                admitted, sameWorld, ownerInCell, actorInCell);
    }

    private static NamedData withDuel(NamedData data, int room, UUID owner, DuelRecord duel) {
        var rooms = new LinkedHashMap<>(data.personalEchoDuels());
        var duels = new LinkedHashMap<>(rooms.get(room));
        duels.put(owner, duel);
        rooms.put(room, duels);
        return new NamedData(data.eventId(), data.generation(), data.selectedLayout(), data.trials(), rooms);
    }

    private static UUID id(long value) { return new UUID(0, value); }

    private record Fixture(RealitySplitTrialController controller, NamedData data,
                           int room, UUID owner, DuelRecord duel) { }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
