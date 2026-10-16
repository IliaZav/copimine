import me.copimine.endevent.domain.EventMusicSyncPolicy;

import java.util.UUID;

public final class EventMusicSyncPolicyTest {
    public static void main(String[] args) {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EventMusicSyncPolicy policy = new EventMusicSyncPolicy();

        policy.markSynchronized(first, "copimine:wave_1");
        assertFalse(policy.shouldSync(first, "copimine:wave_1", "copimine:wave_1"),
                "retry for an already-synchronized player must not restart the track");
        assertTrue(policy.shouldSync(second, "copimine:wave_1", "copimine:wave_1"),
                "a joining player must receive the active track once");
        assertFalse(policy.shouldSync(second, "copimine:wave_1", "copimine:wave_1"),
                "repeated readiness retries must not restart the joining player's track");
        assertFalse(policy.shouldSync(second, "copimine:wave_2", "copimine:wave_1"),
                "a stale requested track must not be played");
        assertTrue(policy.shouldSync(first, "copimine:wave_2", "copimine:wave_2"),
                "a new active track must sync once");

        policy.clearPlayer(first);
        assertTrue(policy.shouldSync(first, "copimine:wave_2", "copimine:wave_2"),
                "a player whose playback was stopped must be eligible for resync");
        policy.clear();
        assertTrue(policy.shouldSync(second, "copimine:wave_2", "copimine:wave_2"),
                "global music cleanup must allow a future session to sync");
        System.out.println("EventMusicSyncPolicyTest OK");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }
}
