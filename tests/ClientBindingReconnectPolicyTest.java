import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Regression: a fresh Minecraft connection must receive a new END_BOSS_BIND,
 * even when the server still remembers the same player UUID from an earlier
 * connection.
 */
public final class ClientBindingReconnectPolicyTest {
    public static void main(String[] args) {
        UUID reconnectingPlayer = UUID.fromString("f5b63468-98e6-4f10-bce9-1a54462db73e");

        Set<UUID> readyForTestBoss = new HashSet<>();
        readyForTestBoss.add(reconnectingPlayer);
        require(prepareForPlayerJoin(readyForTestBoss, reconnectingPlayer, false, false, true, true),
                "a local test boss must resend bindings after a player reconnects");
        require(!readyForTestBoss.contains(reconnectingPlayer),
                "a reconnect must discard the previous connection readiness marker");

        Set<UUID> readyForCombat = new HashSet<>();
        readyForCombat.add(reconnectingPlayer);
        require(prepareForPlayerJoin(readyForCombat, reconnectingPlayer, true, false, false, true),
                "an active combat must resend bindings after a player reconnects");
        require(!readyForCombat.contains(reconnectingPlayer),
                "combat reconnects must not retain a stale readiness marker");

        Set<UUID> readyOutsideEvent = new HashSet<>();
        readyOutsideEvent.add(reconnectingPlayer);
        require(!prepareForPlayerJoin(readyOutsideEvent, reconnectingPlayer, false, false, false, false),
                "a player outside all event visuals must not receive a new binding");
        require(!readyOutsideEvent.contains(reconnectingPlayer),
                "inactive reconnects must still clear a stale readiness marker");

        Set<UUID> readyForAwakeningBoss = new HashSet<>();
        readyForAwakeningBoss.add(reconnectingPlayer);
        require(prepareForPlayerJoin(readyForAwakeningBoss, reconnectingPlayer, false, false, false, true),
                "an existing awakening boss must resend bindings outside combat and music phases");
        require(!readyForAwakeningBoss.contains(reconnectingPlayer),
                "awakening-boss reconnects must not retain a stale readiness marker");

        require(postJoinRetryDelayTicks(true) == 40L,
                "a reconnect that needs visuals must retry after the client channel is ready");
        require(postJoinRetryDelayTicks(false) == 0L,
                "an inactive reconnect must not schedule a redundant retry");

        System.out.println("ClientBindingReconnectPolicyTest OK");
    }

    private static boolean prepareForPlayerJoin(Set<UUID> readyPlayers, UUID player,
                                                 boolean combatPhase, boolean musicPhase,
                                                 boolean localTestBoss, boolean bossPresent) {
        try {
            Class<?> policy = Class.forName("me.copimine.endevent.domain.ClientBindingReconnectPolicy");
            Method method = policy.getMethod("prepareForPlayerJoin", Set.class, UUID.class,
                    boolean.class, boolean.class, boolean.class, boolean.class);
            return (boolean) method.invoke(null, readyPlayers, player,
                    combatPhase, musicPhase, localTestBoss, bossPresent);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("reconnect binding policy is missing", missing);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("reconnect binding policy cannot be invoked", failure);
        }
    }

    private static long postJoinRetryDelayTicks(boolean bindingRequired) {
        try {
            Class<?> policy = Class.forName("me.copimine.endevent.domain.ClientBindingReconnectPolicy");
            Method method = policy.getMethod("postJoinRetryDelayTicks", boolean.class);
            return (long) method.invoke(null, bindingRequired);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("reconnect binding policy is missing", missing);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("reconnect retry delay cannot be invoked", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
