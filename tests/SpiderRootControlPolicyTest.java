import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.SpiderEffectLeasePolicy;
import me.copimine.endevent.domain.SpiderRootControlPolicy;

public final class SpiderRootControlPolicyTest {
    private SpiderRootControlPolicyTest() { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(SpiderRootControlPolicy.canApply(100L, 0L),
                "the first Web Shot may root an eligible player");
        long nextAllowed = SpiderRootControlPolicy.nextAllowedTick(100L);
        check(nextAllowed == 100L + SpiderRootControlPolicy.REAPPLY_IMMUNITY_TICKS,
                "one player cooldown is shared across every elite Spider");
        check(!SpiderRootControlPolicy.canApply(nextAllowed - 1L, nextAllowed),
                "another Spider cannot refresh root during immunity");
        check(SpiderRootControlPolicy.canApply(nextAllowed, nextAllowed),
                "root becomes eligible again at the bounded cooldown edge");

        boolean rejectedNegativeTick = false;
        try {
            SpiderRootControlPolicy.nextAllowedTick(-1L);
        } catch (IllegalArgumentException expected) {
            rejectedNegativeTick = true;
        }
        check(rejectedNegativeTick, "negative application ticks must be rejected");

        boolean rejectedOverflow = false;
        try {
            SpiderRootControlPolicy.nextAllowedTick(Long.MAX_VALUE);
        } catch (ArithmeticException expected) {
            rejectedOverflow = true;
        }
        check(rejectedOverflow, "cooldown arithmetic must not wrap");

        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<UUID, Long> rootedUntil = new HashMap<>();
        Map<UUID, Long> nextApplyTick = new HashMap<>();
        Map<UUID, SpiderEffectLeasePolicy.Owner> owners = new HashMap<>();
        rootedUntil.put(player, 200L);
        rootedUntil.put(other, 300L);
        nextApplyTick.put(player, 260L);
        nextApplyTick.put(other, 360L);
        owners.put(player, new SpiderEffectLeasePolicy.Owner("event-a", 4L));
        owners.put(other, new SpiderEffectLeasePolicy.Owner("event-a", 4L));
        clearPlayerState(player, rootedUntil, nextApplyTick, owners);
        check(!rootedUntil.containsKey(player) && !nextApplyTick.containsKey(player)
                        && !owners.containsKey(player),
                "quit, death, and world change clear all root state for the affected player");
        check(rootedUntil.get(other) == 300L && nextApplyTick.get(other) == 360L
                        && owners.containsKey(other),
                "clearing one player's root state leaves other players untouched");

        System.out.println("SpiderRootControlPolicyTest OK");
    }

    private static void clearPlayerState(UUID player,
                                         Map<UUID, Long> rootedUntil,
                                         Map<UUID, Long> nextApplyTick,
                                         Map<UUID, SpiderEffectLeasePolicy.Owner> owners) {
        SpiderRootControlPolicy.clearPlayerState(player, rootedUntil, nextApplyTick, owners);
    }
}
