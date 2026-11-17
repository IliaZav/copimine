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
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.MovementIntent;
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
        carriedCapabilitiesAreActuallyUsed();
        shieldParriesTheMeasuredStrike();
        meleeCopiesCloseIntoMeleeReach();
        jumpAttackOwnersHopWhileClosingIntoTheFallingCrit();
        frequentWeaponSwitchersTradeASwingForAShot();
        sprintHeavyCopiesAttackBeforeTheyHop();
        woundedCopyWithoutFoodKeepsFighting();
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
        check(food.action() == Action.CONSUMABLE, "low-health Echo selects food its owner actually carried");
        check(supplies.remaining(5) == 0, "the consumable decision stages the owner's bread, not an invented allowance");
        // SUPERSEDED (ECHO-007 / spec MARK-005 "never immunity, never firing from
        // cover" family; found by the overnight audit): the decision used to
        // stage 8.0 + 2.0 immediately, so an Echo healed at the START of the
        // eating animation and a disconnect-and-reconnect during the window
        // healed it a second time. Healing now resolves only when the use
        // window elapses; the decision stages the cost and the deadline.
        check(food.nextDuel().actor().currentHealth() == 8.0D,
                "the consumable decision stages the item cost without an early heal");
        check(food.nextDuel().actor().pendingUseUntilTick() == 332,
                "pending-use time scopes the item action across reconnect and restart");

        // The heal lands exactly at the deadline, and only then.
        DuelRecord beforeDeadline = food.nextDuel();
        check(EchoDuelCombatRuntimeAdapter.tick(beforeDeadline, restore(beforeDeadline),
                scope(beforeDeadline, true, true, true, true), new Observation(331, 4.0D, true))
                .orElseThrow().nextDuel().actor().currentHealth() == 8.0D,
                "nothing heals one tick before the use window elapses");
        Decision resolution = EchoDuelCombatRuntimeAdapter.tick(beforeDeadline, restore(beforeDeadline),
                scope(beforeDeadline, true, true, true, true), new Observation(332, 4.0D, true))
                .orElseThrow();
        // The supply is spent at the START, so the resolving tick has no item to
        // report; it publishes the healed actor as a plain state change.
        check(resolution.action() == Action.NONE,
                "a resolving consumable is a state change, not a second item action");
        DuelRecord atDeadline = resolution.nextDuel();
        // The heal is a bounded share of MAXIMUM health (CONSUMABLE_HEAL_FRACTION),
        // not a flat food value, and it is clamped to the maximum.
        double expectedHealed = Math.min(atDeadline.actor().maximumHealth(),
                beforeDeadline.actor().currentHealth()
                        + beforeDeadline.actor().maximumHealth() * 0.2D);
        check(Math.abs(atDeadline.actor().currentHealth() - expectedHealed) < 1.0E-9D,
                "the bounded heal lands exactly when the use window elapses");
        check(atDeadline.actor().pendingUseUntilTick() == 0L,
                "a completed use clears its deadline so a replay cannot heal twice");
        NamedData woundedData = withDuel(fixture.data(), fixture.room(), fixture.owner(), wounded);
        NamedData restoredFoodData = RealitySplitTrialSnapshot.decodeNamed(RealitySplitTrialSnapshot.encodeNamed(
                withDuel(woundedData, fixture.room(), fixture.owner(), food.nextDuel())), EVENT, ATTEMPT, RUNTIME);
        DuelRecord restoredFood = restoredFoodData.personalEchoDuels().get(fixture.room()).get(fixture.owner());
        check(restoredFood.actor().currentHealth() == 8.0D && restore(restoredFood).remaining(5) == 0
                        && restoredFood.actor().pendingUseUntilTick() == 332,
                "snapshot restore keeps the staged consumable, its deadline and the not-yet-applied "
                        + "heal together, so a restart cannot double-heal");
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
        // A guarded copy still walks: the shield window used to return NONE with
        // no intent, which Decision defaults to HOLD, so the copy froze in place
        // for the whole 5-20 tick parry while its owner re-positioned for free.
        check(hold.intent() == MovementIntent.APPROACH,
                "a shielded copy keeps closing instead of freezing, got " + hold.intent());

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
        check(restore(healRefund).remaining(5) == 1
                        && healRefund.actor().currentHealth() == 8.0D
                        && healRefund.actor().pendingUseUntilTick() == 0,
                "failed health update refunds the food, health projection and pending-use deadline");
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

    /**
     * A copy whose owner fought with a mace, threw charges or lobbed bottles
     * must actually do those things. Each is asserted through the adapter, so
     * the check fails if the branch is removed or reordered into silence.
     */
    private static void carriedCapabilitiesAreActuallyUsed() {
        // A mace is selected as an ordinary melee weapon.
        Fixture maceFixture = activeFixture(18.0D, meleeStrategy(), Map.of(0,
                new EchoLoadoutState.Item("MACE", 1, 500, 0, Map.of(), "", false)));
        EchoLoadoutState maceReplica = restore(maceFixture.duel());
        Decision mace = EchoDuelCombatRuntimeAdapter.tick(maceFixture.duel(), maceReplica,
                scope(maceFixture.duel(), true, true, true, true),
                new Observation(100, 4.0D, true)).orElseThrow();
        check(mace.action() == Action.MELEE, "a carried mace is swung as melee, got " + mace.action());

        // A wind charge is burst with no bow carried, spending one charge.
        Fixture chargeFixture = activeFixture(18.0D, meleeStrategy(), Map.of(
                3, EchoLoadoutState.Item.empty(),
                4, EchoLoadoutState.Item.empty(),
                6, new EchoLoadoutState.Item("WIND_CHARGE", 2, 0, 0, Map.of(), "", false)));
        EchoLoadoutState chargeReplica = restore(chargeFixture.duel());
        Decision burst = EchoDuelCombatRuntimeAdapter.tick(chargeFixture.duel(), chargeReplica,
                scope(chargeFixture.duel(), true, true, true, true),
                new Observation(100, 100.0D, true)).orElseThrow();
        check(burst.action() == Action.WIND_CHARGE, "a carried wind charge is burst, got " + burst.action());
        check(chargeReplica.remaining(6) == 1, "the burst spends exactly one charge");
        check(burst.nextDuel().actor().rangedCooldownUntilTick() == 140,
                "the burst stages the ranged cadence");

        // A splash bottle is lobbed with no bow carried, spending one bottle.
        Fixture splashFixture = activeFixture(18.0D, meleeStrategy(), Map.of(
                3, EchoLoadoutState.Item.empty(),
                4, EchoLoadoutState.Item.empty(),
                7, new EchoLoadoutState.Item("SPLASH_POTION", 2, 0, 0, Map.of(), "", false)));
        EchoLoadoutState splashReplica = restore(splashFixture.duel());
        Decision lob = EchoDuelCombatRuntimeAdapter.tick(splashFixture.duel(), splashReplica,
                scope(splashFixture.duel(), true, true, true, true),
                new Observation(100, 100.0D, true)).orElseThrow();
        check(lob.action() == Action.SPLASH, "a carried splash bottle is lobbed, got " + lob.action());
        check(splashReplica.remaining(7) == 1, "the throw spends exactly one bottle");
    }

    /**
     * The shield is a timed parry against the owner's measured rhythm, not a
     * permanent wall. The guard comes up for an imminent strike and stays down
     * while the next strike is still far away.
     */
    private static void shieldParriesTheMeasuredStrike() {
        Fixture fixture = activeFixture(18.0D, meleeStrategy());
        EchoLoadoutState imminentReplica = restore(fixture.duel());
        // Owner strikes every 20 ticks; 18 ticks since the last one means the
        // next is 2 ticks away — inside the reaction lead.
        Decision parry = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), imminentReplica,
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 4.0D, true, true, false, 20L, 18L)).orElseThrow();
        check(parry.action() == Action.SHIELD, "an imminent strike is parried, got " + parry.action());
        check(parry.nextDuel().actor().shieldActiveUntilTick() > 100L,
                "the parry opens a real guard window");

        EchoLoadoutState distantReplica = restore(fixture.duel());
        // 1 tick since the last strike of a 20-tick rhythm: the next is 19 away.
        Decision upright = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), distantReplica,
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 4.0D, true, true, false, 20L, 1L)).orElseThrow();
        check(upright.action() != Action.SHIELD,
                "a strike far in the future is not parried, got " + upright.action());
        check(distantReplica.item(2).damage() == 0, "a refused parry wears nothing");
    }

    /**
     * A brawler copy has to reach melee reach on its own.
     *
     * <p>The hold band is anchored on the owner's measured distance, so a
     * neutral 500 per mille used to produce a 6.75-block hold with a 1.5-block
     * tolerance: the copy stopped at 8.25 blocks and only ever fought owners
     * that walked into it. The band is now clamped to the melee envelope for
     * melee-dominant profiles.</p>
     */
    private static void meleeCopiesCloseIntoMeleeReach() {
        var brawler = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 0, 0, 0, 1_000, 0, true);
        Fixture fixture = activeFixture(18.0D, brawler);

        Decision closing = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 49.0D, true)).orElseThrow();
        check(closing.intent() == MovementIntent.APPROACH,
                "a brawler copy closes from seven blocks instead of holding at range, got "
                        + closing.intent());

        Decision reach = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 9.0D, true)).orElseThrow();
        check(reach.intent() != MovementIntent.APPROACH,
                "inside melee reach the brawler stops closing, got " + reach.intent());
        check(reach.action() == Action.MELEE,
                "and it swings there, got " + reach.action());

        // A ranged copy keeps its own measured band: the clamp is melee-only.
        var archer = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                100, 900, 0, 0, 0, 1_000, 0, true,
                0, 950, 400, 0, 0);
        Fixture rangedFixture = activeFixture(18.0D, archer);
        Decision kiting = EchoDuelCombatRuntimeAdapter.tick(rangedFixture.duel(),
                restore(rangedFixture.duel()),
                scope(rangedFixture.duel(), true, true, true, true),
                new Observation(100, 9.0D, true)).orElseThrow();
        check(kiting.intent() == MovementIntent.KITE,
                "a melee-armed copy whose profile holds range still kites, got " + kiting.intent());
    }

    /**
     * `jumpAttackPermille` was recorded and persisted but never read, so no copy
     * ever set up the falling critical its owner actually played. A copy of an
     * owner whose jump-attack rate is above the legacy baseline now hops while
     * it closes, and only while it is still outside melee reach.
     */
    private static void jumpAttackOwnersHopWhileClosingIntoTheFallingCrit() {
        var jumper = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 0, 0, 0, 1_000, 0, true,
                800, 500, 0, 0, 0);
        // Sword only: with no ranged tool the copy has to close, which is the
        // situation the hop is for.
        Fixture fixture = activeFixture(18.0D, jumper, Map.of(
                3, EchoLoadoutState.Item.empty(),
                4, EchoLoadoutState.Item.empty()));

        Decision hop = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 49.0D, true)).orElseThrow();
        check(hop.action() == Action.JUMP,
                "a jump-attack copy hops while closing an empty-handed range, got " + hop.action());
        check(hop.intent() == MovementIntent.APPROACH,
                "the hop is part of closing, not a substitute for it, got " + hop.intent());
        check(hop.nextDuel().actor().jumpCooldownUntilTick() > 100L,
                "the setup hop is checkpointed against the persisted jump cooldown");

        // Inside reach it swings: a swing is never traded for a hop.
        Decision swing = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 9.0D, true)).orElseThrow();
        check(swing.action() == Action.MELEE,
                "inside reach the jump-attack copy swings instead of hopping, got " + swing.action());

        // The legacy 250 per mille default is not a habit: a baseline copy never
        // invents jump attacks its owner did not play.
        var baseline = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 0, 0, 0, 1_000, 0, true);
        Fixture baselineFixture = activeFixture(18.0D, baseline, Map.of(
                3, EchoLoadoutState.Item.empty(),
                4, EchoLoadoutState.Item.empty()));
        Decision unprovoked = EchoDuelCombatRuntimeAdapter.tick(baselineFixture.duel(),
                restore(baselineFixture.duel()),
                scope(baselineFixture.duel(), true, true, true, true),
                new Observation(100, 49.0D, true)).orElseThrow();
        check(unprovoked.action() == Action.NONE,
                "a baseline jump-attack rate is not a jump-attack habit, got " + unprovoked.action());
    }

    /**
     * `weaponSwitchPermille` was recorded and persisted but never read either, so
     * a copy of a player who swapped tools constantly still behaved like a
     * single-weapon fighter. A high-switch owner's copy now shoots from inside
     * melee reach inside its own switch window, but only when the shot is legal.
     */
    private static void frequentWeaponSwitchersTradeASwingForAShot() {
        var switcher = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                900, 100, 0, 0, 0, 1_000, 0, true,
                0, 500, 0, 0, 900);
        Fixture fixture = activeFixture(18.0D, switcher);
        EchoLoadoutState replica = restore(fixture.duel());
        Decision shot = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), replica,
                scope(fixture.duel(), true, true, true, true),
                new Observation(200, 9.0D, true)).orElseThrow();
        check(shot.action() == Action.RANGED,
                "a frequent swapper shoots from inside melee reach instead of only swinging, got "
                        + shot.action());
        check(replica.remaining(4) == 1 && shot.itemSlot() == 3,
                "the swap spends the owner's real arrow from the bow it reached for");

        var anchored = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                900, 100, 0, 0, 0, 1_000, 0, true,
                0, 500, 0, 0, 100);
        Fixture anchoredFixture = activeFixture(18.0D, anchored);
        Decision swing = EchoDuelCombatRuntimeAdapter.tick(anchoredFixture.duel(),
                restore(anchoredFixture.duel()),
                scope(anchoredFixture.duel(), true, true, true, true),
                new Observation(200, 9.0D, true)).orElseThrow();
        check(swing.action() == Action.MELEE,
                "an owner who never swapped keeps one weapon in its hands, got " + swing.action());
    }

    /**
     * `mobilityPermille` is the owner's SPRINT share, not a jump habit, and the
     * mobility jump used to run before every attack branch: a copy of a player
     * who sprinted through half their movement samples was airborne on half its
     * ground ticks and rarely hit anything. The hop is now a filler action taken
     * only after every legal attack has declined.
     */
    private static void sprintHeavyCopiesAttackBeforeTheyHop() {
        var sprinter = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 1_000, 0, 0, 1_000, 0, true);
        Fixture fixture = activeFixture(18.0D, sprinter);

        Decision swing = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 9.0D, true)).orElseThrow();
        check(swing.action() == Action.MELEE,
                "a copy with a legal swing swings instead of hopping, got " + swing.action());

        // The hop itself must survive: with nothing reachable to hit, the same
        // copy still jumps rather than freezing on the spot.
        Decision hop = EchoDuelCombatRuntimeAdapter.tick(fixture.duel(), restore(fixture.duel()),
                scope(fixture.duel(), true, true, true, true),
                new Observation(100, 10_000.0D, false)).orElseThrow();
        check(hop.action() == Action.JUMP,
                "with no legal attack the sprint-heavy copy still hops, got " + hop.action());
    }

    /**
     * A wounded melee copy with food heals; one with an empty inventory must not
     * kite forever. The retreat is a way to buy the heal's ticks, so a copy that
     * cannot heal must keep fighting - otherwise it is the "endless retreat
     * without firing" failure the navigation rule forbids.
     */
    private static void woundedCopyWithoutFoodKeepsFighting() {
        var brawler = new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 0, 0, 1_000, 0, 0, true);
        Fixture fed = activeFixture(5.0D, brawler);
        Decision heal = EchoDuelCombatRuntimeAdapter.tick(fed.duel(), restore(fed.duel()),
                scope(fed.duel(), true, true, true, true),
                new Observation(100, 100.0D, true)).orElseThrow();
        check(heal.action() == Action.CONSUMABLE,
                "a wounded brawler that carries food starts eating, got " + heal.action());

        Fixture empty = activeFixture(5.0D, brawler, Map.of(5, EchoLoadoutState.Item.empty()));
        Decision fights = EchoDuelCombatRuntimeAdapter.tick(empty.duel(), restore(empty.duel()),
                scope(empty.duel(), true, true, true, true),
                new Observation(100, 100.0D, true)).orElseThrow();
        check(fights.intent() != MovementIntent.KITE,
                "a wounded brawler with no heal keeps closing instead of fleeing, got "
                        + fights.intent());
    }

    private static me.copimine.endevent.domain.wave7.EchoBehaviorStrategy meleeStrategy() {
        // No ranged, mobility or defense weight: the capability branches are
        // exercised directly instead of hiding behind a profile window.
        return new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                1_000, 0, 0, 0, 1_000, 0, 0, true);
    }

    private static Fixture activeFixture(double health) {
        return activeFixture(health, new me.copimine.endevent.domain.wave7.EchoBehaviorStrategy(
                500, 500, 0, 0, 500, 0, 0, true));
    }

    private static Fixture activeFixture(double health,
            me.copimine.endevent.domain.wave7.EchoBehaviorStrategy strategy) {
        return activeFixture(health, strategy, Map.of());
    }

    private static Fixture activeFixture(double health,
            me.copimine.endevent.domain.wave7.EchoBehaviorStrategy strategy,
            Map<Integer, EchoLoadoutState.Item> extra) {
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
        for (var entry : extra.entrySet()) {
            items.set(entry.getKey(), entry.getValue());
        }
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
