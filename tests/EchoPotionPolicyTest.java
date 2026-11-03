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

/**
 * A copy drinks the potions its owner actually carried.
 *
 * <p>Before this the classifier did not know POTION at all, so a player who
 * fought on Swiftness and Strength produced a copy that carried those bottles
 * as inert loot and never buffed once. The gap was invisible in every existing
 * test because none of them ever put a potion in a replica.</p>
 */
public final class EchoPotionPolicyTest {
    private static final Cooldowns COOLDOWNS = new Cooldowns(10L, 12L, 20L, 32L);

    public static void main(String[] args) {
        potionsAreClassifiedNotInert();
        drinkingSpendsExactlyOneBottle();
        lobbedPotionsAreNotDrunk();
        allotmentIsHalfTheOwnersBottles();
        aCopyWantsABuffOnlyInContactOrHurt();
        aLiveBuffIsNotReapplied();
        drinkingRespectsEveryActorGate();
        System.out.println("EchoPotionPolicyTest OK");
    }

    private static void potionsAreClassifiedNotInert() {
        check(EchoLoadoutState.classify("POTION", false) == Kind.POTION, "POTION is a drinkable potion");
        check(EchoLoadoutState.classify("SPLASH_POTION", false) == Kind.SPLASH_POTION,
                "SPLASH_POTION is kept distinct");
        check(EchoLoadoutState.classify("LINGERING_POTION", false) == Kind.LINGERING_POTION,
                "LINGERING_POTION is kept distinct");
        // Control: the three must not have swallowed an existing classification.
        check(EchoLoadoutState.classify("GOLDEN_APPLE", false) == Kind.GOLDEN_APPLE, "apples still apples");
        check(EchoLoadoutState.classify("IRON_SWORD", false) == Kind.MELEE, "swords still melee");
        check(EchoLoadoutState.classify("STONE", false) == Kind.INERT, "junk is still inert");
    }

    private static void drinkingSpendsExactlyOneBottle() {
        var replica = replica("POTION", 3);
        int slot = replica.find(Kind.POTION);
        check(slot >= 0, "a carried potion is found");
        var started = EchoDuelCombatPolicy.tryStartPotion(actor(50.0D), replica, slot,
                replica.revision(), 0L, COOLDOWNS);
        check(started.isPresent(), "a ready copy may start drinking");
        check(replica.remaining(slot) == 2, "drinking spends one bottle, got " + replica.remaining(slot));
        check(started.get().pendingUseUntilTick() == COOLDOWNS.consumableUseTicks(),
                "the use carries its own deadline");
        check(started.get().currentHealth() == 50.0D, "drinking is not instant healing");
    }

    private static void lobbedPotionsAreNotDrunk() {
        // A thrown potion is not a drink: drinking a splash bottle would waste
        // the owner's only healing throw on the copy's own chin.
        var replica = replica("SPLASH_POTION", 2);
        check(replica.find(Kind.POTION) < 0, "a splash bottle is not a drinkable potion slot");
        check(!EchoDuelCombatPolicy.hasPotion(replica, 0, replica.revision()),
                "a splash bottle is not admitted by the potion gate");
        check(EchoDuelCombatPolicy.tryStartPotion(actor(50.0D), replica, 0,
                replica.revision(), 0L, COOLDOWNS).isEmpty(),
                "a splash bottle cannot be drunk through the potion path");
        check(replica.remaining(0) == 2, "the refused splash bottle was not spent");
    }

    private static void allotmentIsHalfTheOwnersBottles() {
        check(EchoDuelCombatPolicy.potionAllotment(0) == 0, "no bottles, no allotment");
        check(EchoDuelCombatPolicy.potionAllotment(1) == 1, "a single bottle is still usable");
        check(EchoDuelCombatPolicy.potionAllotment(2) == 1, "two bottles allow one");
        check(EchoDuelCombatPolicy.potionAllotment(7) == 3, "seven bottles allow three");
        check(EchoDuelCombatPolicy.potionAllotment(64) == 32, "a full stack allows half");
    }

    private static void aCopyWantsABuffOnlyInContactOrHurt() {
        var ready = actor(50.0D);
        check(EchoDuelCombatPolicy.wantsPotionBuff(ready, false, false, 16.0D),
                "near the owner the copy drinks a buff");
        check(EchoDuelCombatPolicy.wantsPotionBuff(ready, false, true, 400.0D),
                "a wounded copy drinks even from far away");
        check(!EchoDuelCombatPolicy.wantsPotionBuff(ready, false, false, 400.0D),
                "a healthy copy across the arena does not pre-burn its allotment");
        check(!EchoDuelCombatPolicy.wantsPotionBuff(ready, true, false, 4.0D),
                "a live buff is not re-drunk");
        check(!EchoDuelCombatPolicy.wantsPotionBuff(ready, false, false, Double.NaN),
                "a non-finite distance fails closed");
    }

    private static void aLiveBuffIsNotReapplied() {
        var replica = replica("POTION", 4);
        int slot = replica.find(Kind.POTION);
        var started = EchoDuelCombatPolicy.tryStartPotion(actor(50.0D), replica, slot,
                replica.revision(), 0L, COOLDOWNS).orElseThrow();
        long buffUntil = started.pendingUseUntilTick() + EchoDuelCombatPolicy.POTION_BUFF_TICKS;
        check(EchoDuelCombatPolicy.POTION_BUFF_TICKS > COOLDOWNS.consumableUseTicks(),
                "the buff outlives the drinking animation, otherwise it can never be seen");
        check(buffUntil > started.pendingUseUntilTick(), "the buff has a bounded window");
    }

    private static void drinkingRespectsEveryActorGate() {
        var replica = replica("POTION", 4);
        int slot = replica.find(Kind.POTION);
        // Busy: another action already owns this tick.
        check(EchoDuelCombatPolicy.tryStartPotion(
                new ActorState(UUID.nameUUIDFromBytes("owner".getBytes()), 50.0D, 50.0D,
                        ActorLifecycle.ALIVE, 0L, 100L, 0L, 0L, 0L, 0L),
                replica, slot, replica.revision(), 0L, COOLDOWNS).isEmpty(),
                "a copy mid-action does not drink");
        // Dead: no action at all.
        check(EchoDuelCombatPolicy.tryStartPotion(
                new ActorState(UUID.nameUUIDFromBytes("owner".getBytes()), 0.0D, 50.0D,
                        ActorLifecycle.DEAD, 0L, 0L, 0L, 0L, 0L, 1L),
                replica, slot, replica.revision(), 0L, COOLDOWNS).isEmpty(),
                "a dead copy does not drink");
        // Stale replica revision: the snapshot no longer proves what is held.
        check(EchoDuelCombatPolicy.tryStartPotion(actor(50.0D), replica, slot,
                replica.revision() + 1L, 0L, COOLDOWNS).isEmpty(),
                "a stale replica revision is refused");
        check(replica.remaining(slot) == 4, "every refused start left the bottles untouched");
    }

    private static EchoLoadoutState replica(String material, int amount) {
        var source = new ArrayList<Item>();
        for (int i = 0; i < 41; i++) {
            source.add(Item.empty());
        }
        source.set(0, new Item(material, amount, 0, 0, Map.of(), "", false));
        return EchoLoadoutState.create(
                UUID.nameUUIDFromBytes("event".getBytes()),
                1L,
                UUID.nameUUIDFromBytes("duel".getBytes()),
                UUID.nameUUIDFromBytes("owner".getBytes()),
                source, 0);
    }

    private static ActorState actor(double currentHealth) {
        return new ActorState(UUID.nameUUIDFromBytes("owner".getBytes()), currentHealth, 50.0D,
                ActorLifecycle.ALIVE, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
