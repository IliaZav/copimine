import me.copimine.artifacts.domain.EventRewardIdempotencyPolicy;

public final class EventRewardIdempotencyPolicyTest {
    public static void main(String[] args) {
        EventRewardIdempotencyPolicy.Payload original = payload(
                "event-a", "rift_core_shard", "player-a", "Player A", "EVENT_PLAYER", "", 0, 0, 0);
        require(EventRewardIdempotencyPolicy.sameRequest(original, payload(
                "event-a", "rift_core_shard", "player-a", "Player A", "EVENT_PLAYER", "", 0, 0, 0)),
                "an identical payload must be eligible for idempotent replay");
        require(!EventRewardIdempotencyPolicy.sameRequest(original, null),
                "a missing persisted payload must fail closed");
        requireMismatch(original, payload(
                "event-b", "rift_core_shard", "player-a", "Player A", "EVENT_PLAYER", "", 0, 0, 0),
                "event identity");
        requireMismatch(original, payload(
                "event-a", "night_cloak", "player-a", "Player A", "EVENT_PLAYER", "", 0, 0, 0),
                "item identity");
        requireMismatch(original, payload(
                "event-a", "rift_core_shard", "player-b", "Player B", "EVENT_PLAYER", "", 0, 0, 0),
                "recipient identity");
        requireMismatch(original, payload(
                "event-a", "rift_core_shard", "player-a", "Renamed Player", "EVENT_PLAYER", "", 0, 0, 0),
                "recipient name");
        requireMismatch(original, payload(
                "event-a", "rift_core_shard", "player-a", "Player A", "EVENT_WORLD", "world", 1, 2, 3),
                "delivery mode and world-drop location");
        requireMismatch(payload(
                        "event-a", "return_stone", "owner-a", "CopiMine End Event", "EVENT_WORLD", "world", 1, 2, 3),
                payload("event-a", "return_stone", "owner-a", "CopiMine End Event", "EVENT_WORLD", "world", 1, 2, 4),
                "drop coordinates");

        requireMismatch(payload("event-a", "return_stone", "owner-a", "CopiMine End Event",
                        "EVENT_WORLD", "world", 1, 2, 3),
                payload("event-a", "return_stone", "owner-a", "CopiMine End Event",
                        "EVENT_WORLD", "world", Double.NaN, 2, 3),
                "non-finite persisted coordinates");
        System.out.println("EventRewardIdempotencyPolicyTest OK");
    }

    private static EventRewardIdempotencyPolicy.Payload payload(
            String event, String item, String recipient, String name,
            String mode, String world, double x, double y, double z) {
        return new EventRewardIdempotencyPolicy.Payload(
                event, item, recipient, name, mode, world, x, y, z);
    }

    private static void requireMismatch(
            EventRewardIdempotencyPolicy.Payload expected,
            EventRewardIdempotencyPolicy.Payload existing, String field) {
        require(!EventRewardIdempotencyPolicy.sameRequest(expected, existing),
                "a reused key with a different " + field + " must be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private EventRewardIdempotencyPolicyTest() {
    }
}
