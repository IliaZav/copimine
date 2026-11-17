import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy.Cooldowns;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Kind;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.MovementIntent;

/**
 * The copy fights with the tools its owner actually carried.
 *
 * <p>Before this, a mace had no tier prefix and fell through to INERT, a stack
 * of wind charges was unusable loot, a splash bottle could never leave the
 * hand, and the shield was a fixed-clock wall rather than a timed parry. Each
 * of those is asserted here as an observable behaviour, not as a source token.</p>
 */
public final class EchoDuelCapabilityPolicyTest {
    private static final Cooldowns COOLDOWNS = new Cooldowns(10L, 12L, 20L, 32L);

    public static void main(String[] args) {
        maceAndWindChargeAreClassified();
        windChargeSpendsExactlyOneCharge();
        splashThrowsTheBottleItCarried();
        parryWaitsForTheMeasuredStrike();
        parryCarriesItsOwnRecovery();
        creepingIsLowerAndOnlyWhenUnseen();
        System.out.println("EchoDuelCapabilityPolicyTest OK");
    }

    private static void maceAndWindChargeAreClassified() {
        check(EchoLoadoutState.classify("MACE", false) == Kind.MELEE,
                "a mace is a held melee weapon");
        check(EchoLoadoutState.classify("WIND_CHARGE", false) == Kind.WIND_CHARGE,
                "a wind charge has its own kind");
        // Controls: the two new names must not have swallowed old classifications.
        check(EchoLoadoutState.classify("IRON_SWORD", false) == Kind.MELEE, "swords still melee");
        check(EchoLoadoutState.classify("SHIELD", false) == Kind.SHIELD, "shields still shields");
        check(EchoLoadoutState.classify("STONE", false) == Kind.INERT, "junk is still inert");
    }

    private static void windChargeSpendsExactlyOneCharge() {
        var replica = replica("WIND_CHARGE", 3);
        int slot = replica.find(Kind.WIND_CHARGE);
        check(slot >= 0, "a carried wind charge is found");
        check(EchoDuelCombatPolicy.hasWindCharge(replica, slot, replica.revision()),
                "the burst gate admits a carried charge");
        check(!EchoDuelCombatPolicy.hasWindCharge(replica, slot, replica.revision() + 1L),
                "a stale replica revision is refused");

        var started = EchoDuelCombatPolicy.tryStartWindCharge(
                actor(50.0D), replica, slot, replica.revision(), 100L, COOLDOWNS);
        check(started.isPresent(), "a ready copy may burst");
        check(replica.remaining(slot) == 2,
                "a burst spends exactly one charge, got " + replica.remaining(slot));
        check(started.get().rangedCooldownUntilTick() == 100L + COOLDOWNS.rangedTicks(),
                "a burst shares the ranged cadence");

        // A wind charge is not a potion and is not a consumable.
        check(!EchoDuelCombatPolicy.hasPotion(replica, slot, replica.revision()),
                "a wind charge is not a drinkable potion");
        // The persisted cooldown is what stops a different-shaped replay.
        check(EchoDuelCombatPolicy.tryStartWindCharge(
                started.get(), replica, slot, replica.revision(), 100L, COOLDOWNS).isEmpty(),
                "the burst cannot be replayed on the same tick");
        check(replica.remaining(slot) == 2, "the refused burst left the stack untouched");
    }

    private static void splashThrowsTheBottleItCarried() {
        var replica = replica("SPLASH_POTION", 2);
        int slot = replica.find(Kind.SPLASH_POTION);
        check(slot >= 0, "a carried splash bottle is found");
        check(EchoDuelCombatPolicy.hasSplash(replica, slot, replica.revision()),
                "the throw gate admits a splash bottle");
        check(!EchoDuelCombatPolicy.hasPotion(replica, slot, replica.revision()),
                "a splash bottle is never drunk");
        check(EchoDuelCombatPolicy.tryStartPotion(
                actor(50.0D), replica, slot, replica.revision(), 100L, COOLDOWNS).isEmpty(),
                "the drink path refuses a splash bottle");

        var thrown = EchoDuelCombatPolicy.tryStartSplash(
                actor(50.0D), replica, slot, replica.revision(), 100L, COOLDOWNS);
        check(thrown.isPresent(), "a ready copy may lob a bottle");
        check(replica.remaining(slot) == 1,
                "throwing spends one bottle, got " + replica.remaining(slot));
        check(thrown.get().rangedCooldownUntilTick() == 100L + COOLDOWNS.rangedTicks(),
                "a thrown bottle shares the ranged cadence");

        // A drinkable potion is not a weapon: it never leaves the hand.
        var drink = replica("POTION", 2);
        check(EchoDuelCombatPolicy.tryStartSplash(
                actor(50.0D), drink, 0, drink.revision(), 100L, COOLDOWNS).isEmpty(),
                "a drinkable potion is not thrown");
        check(drink.remaining(0) == 2, "the refused drinkable bottle was not spent");
    }

    private static void parryWaitsForTheMeasuredStrike() {
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, 0L, 0L, 0L).isPresent(),
                "a strike due now is parried");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, 4L, 0L, 0L).isPresent(),
                "a strike inside the reaction lead is parried");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, 5L, 0L, 0L).isEmpty(),
                "a strike still far away is not blocked early");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, false, 0L, 0L, 0L).isEmpty(),
                "no parry out of striking distance");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, -1L, 0L, 0L).isEmpty(),
                "an unknown strike fails closed");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, 0L, 150L, 0L).isEmpty(),
                "an already-open guard is not re-parried");
        check(EchoDuelCombatPolicy.parryHoldTicks(100L, true, 0L, 0L, 150L).isEmpty(),
                "a recovering guard cannot parry yet");

        long hold = EchoDuelCombatPolicy.parryHoldTicks(100L, true, 0L, 0L, 0L).orElseThrow();
        check(hold >= 1L && hold <= EchoDuelCombatPolicy.SHIELD_PARRY_HOLD_TICKS,
                "the parry window is bounded, got " + hold);
    }

    private static void parryCarriesItsOwnRecovery() {
        var replica = replicaWithShield();
        int slot = replica.find(Kind.SHIELD);
        check(slot >= 0, "the fixture carries a finite shield");

        var started = EchoDuelCombatPolicy.tryStartReactiveShield(
                actor(50.0D), replica, slot, replica.revision(), 100L, 2L, COOLDOWNS);
        check(started.isPresent(), "a parry starts a bounded window");
        check(started.get().shieldActiveUntilTick() == 102L,
                "the guard covers exactly the strike window");
        check(started.get().shieldCooldownUntilTick() > started.get().shieldActiveUntilTick(),
                "the parry carries its own recovery");

        // The recovery is shorter than a full shield cooldown so a parry repeats.
        check(started.get().shieldCooldownUntilTick() - 100L
                        <= EchoDuelCombatPolicy.SHIELD_PARRY_COOLDOWN_TICKS,
                "parry recovery stays inside its own bound");
        // During that recovery a second parry is refused.
        check(EchoDuelCombatPolicy.parryHoldTicks(105L, true, 0L,
                        started.get().shieldActiveUntilTick(),
                        started.get().shieldCooldownUntilTick()).isEmpty(),
                "a recovering guard cannot parry twice back to back");

        // No shield, no parry.
        var noShield = replica("POTION", 1);
        check(EchoDuelCombatPolicy.tryStartReactiveShield(
                actor(50.0D), noShield, 0, noShield.revision(), 100L, 2L, COOLDOWNS).isEmpty(),
                "without a shield nothing starts");

        // An overlong hold would be a wall, not a timed block. Chosen above the
        // configured maximum but below the full shield cooldown, so this guard
        // is the only one that can refuse it.
        long overlong = COOLDOWNS.shieldHoldTicks() + 5L;
        check(overlong < COOLDOWNS.shieldCooldownTicks(),
                "the probe hold must stay inside the shield cooldown to isolate the hold bound");
        check(EchoDuelCombatPolicy.tryStartReactiveShield(actor(50.0D), replica, slot,
                        replica.revision(), 100L, overlong, COOLDOWNS).isEmpty(),
                "an overlong parry is refused");
    }

    private static void creepingIsLowerAndOnlyWhenUnseen() {
        check(EchoDuelCombatRuntimeAdapter.sneaking(MovementIntent.APPROACH, false),
                "closing on an unseen owner lowers the body");
        check(!EchoDuelCombatRuntimeAdapter.sneaking(MovementIntent.APPROACH, true),
                "no crouch when the owner is in plain view");
        check(!EchoDuelCombatRuntimeAdapter.sneaking(MovementIntent.KITE, false),
                "retreating stays upright");
        check(!EchoDuelCombatRuntimeAdapter.sneaking(MovementIntent.HOLD, false),
                "standing still is upright");
    }

    private static EchoLoadoutState replica(String material, int amount) {
        return replica(material, amount, 0);
    }

    private static EchoLoadoutState replicaWithShield() {
        return replica("SHIELD", 1, 336);
    }

    private static EchoLoadoutState replica(String material, int amount, int maximumDamage) {
        var source = new ArrayList<Item>();
        for (int i = 0; i < 41; i++) {
            source.add(Item.empty());
        }
        source.set(0, new Item(material, amount, maximumDamage, 0, Map.of(), "", false));
        return EchoLoadoutState.create(
                UUID.nameUUIDFromBytes("event".getBytes()),
                1L,
                UUID.nameUUIDFromBytes("duel".getBytes()),
                UUID.nameUUIDFromBytes("owner".getBytes()),
                source, 0);
    }

    private static ActorState actor(double currentHealth) {
        return new ActorState(UUID.nameUUIDFromBytes("owner".getBytes()), currentHealth, 50.0D,
                ActorLifecycle.ALIVE, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}