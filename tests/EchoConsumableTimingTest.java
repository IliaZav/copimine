import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy;
import me.copimine.endevent.domain.wave7.EchoDuelCombatPolicy.Cooldowns;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;

/**
 * T-G04: a copy's food must not heal the instant the use starts. It heals once,
 * when the use actually completes, and an interrupted use heals nothing and
 * refunds nothing.
 */
public final class EchoConsumableTimingTest {
    private static final Cooldowns COOLDOWNS = new Cooldowns(10L, 12L, 20L, 32L);

    public static void main(String[] args) {
        noHealBeforeCompletion();
        completionHealsExactlyOnce();
        interruptionHealsNothingAndRefundsNothing();
        healingIsBoundedByMaximumHealth();
        System.out.println("EchoConsumableTimingTest OK");
    }

    private static void noHealBeforeCompletion() {
        var started = startEating(10.0D);
        check(started != null, "a healthy copy may start eating");
        // Starting the use must not restore any health by itself.
        check(started.currentHealth() == 10.0D,
                "starting a use heals nothing, got " + started.currentHealth());
        check(EchoDuelCombatPolicy.completeConsumable(started, 0L, COOLDOWNS).isEmpty(),
                "an unfinished use must not heal");
        check(EchoDuelCombatPolicy.completeConsumable(started,
                        started.pendingUseUntilTick() - 1L, COOLDOWNS).isEmpty(),
                "the tick before the deadline still has not healed");
    }

    private static void completionHealsExactlyOnce() {
        var started = startEating(10.0D);
        var healed = EchoDuelCombatPolicy.completeConsumable(started,
                started.pendingUseUntilTick(), COOLDOWNS);
        check(healed.isPresent(), "a completed use heals");
        double restored = healed.get().currentHealth();
        check(restored > started.currentHealth(),
                "completion restores health: " + started.currentHealth() + " -> " + restored);
        // The same snapshot cannot be completed again, so healing cannot replay.
        check(EchoDuelCombatPolicy.completeConsumable(healed.get(),
                        healed.get().pendingUseUntilTick(), COOLDOWNS).isEmpty(),
                "the same use cannot heal twice");
    }

    private static void interruptionHealsNothingAndRefundsNothing() {
        var replica = replica();
        var started = EchoDuelCombatPolicy.tryStartConsumable(
                actor(10.0D), replica, 0, 0L, 0L, COOLDOWNS).orElseThrow();
        check(replica.remaining(0) == 4, "starting the use spends the food");
        var interrupted = EchoDuelCombatPolicy.cancelConsumable(started);
        check(interrupted.currentHealth() == started.currentHealth(),
                "an interrupted use heals nothing");
        // The food was spent when the use began, so cancelling must not hand it
        // back: otherwise a copy could farm healing by spamming cancel.
        check(replica.remaining(0) == 4,
                "an interrupted use does not refund the food, got " + replica.remaining(0));
    }

    private static void healingIsBoundedByMaximumHealth() {
        var started = startEating(49.0D);
        var healed = EchoDuelCombatPolicy.completeConsumable(started,
                started.pendingUseUntilTick(), COOLDOWNS).orElseThrow();
        check(healed.currentHealth() <= healed.maximumHealth(),
                "healing never exceeds the copy's maximum health: " + healed.currentHealth());
    }

    private static ActorState startEating(double currentHealth) {
        return EchoDuelCombatPolicy.tryStartConsumable(
                actor(currentHealth), replica(), 0, 0L, 0L, COOLDOWNS).orElse(null);
    }

    private static EchoLoadoutState replica() {
        var source = new ArrayList<Item>();
        for (int i = 0; i < 41; i++) {
            source.add(Item.empty());
        }
        source.set(0, new Item("GOLDEN_APPLE", 5, 0, 0, Map.of(), "", false));
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