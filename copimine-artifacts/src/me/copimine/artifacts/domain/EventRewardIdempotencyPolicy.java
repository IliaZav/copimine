package me.copimine.artifacts.domain;

import java.util.Objects;

/** Exact request identity required before an event reward key may be replayed. */
public final class EventRewardIdempotencyPolicy {
    private EventRewardIdempotencyPolicy() {
    }

    public record Payload(String eventId, String itemId, String recipientUuid,
                          String recipientName, String deliveryMode, String worldName,
                          double dropX, double dropY, double dropZ) {
        public Payload {
            eventId = value(eventId);
            itemId = value(itemId);
            recipientUuid = value(recipientUuid);
            recipientName = value(recipientName);
            deliveryMode = value(deliveryMode);
            worldName = worldName == null ? "" : worldName;
        }

        private static String value(String value) {
            return value == null ? "" : value;
        }
    }

    public static boolean sameRequest(Payload expected, Payload existing) {
        return expected != null && existing != null
                && Objects.equals(expected.eventId(), existing.eventId())
                && Objects.equals(expected.itemId(), existing.itemId())
                && Objects.equals(expected.recipientUuid(), existing.recipientUuid())
                && Objects.equals(expected.recipientName(), existing.recipientName())
                && Objects.equals(expected.deliveryMode(), existing.deliveryMode())
                && Objects.equals(expected.worldName(), existing.worldName())
                && Double.isFinite(expected.dropX()) && Double.isFinite(expected.dropY())
                && Double.isFinite(expected.dropZ())
                && Double.isFinite(existing.dropX()) && Double.isFinite(existing.dropY())
                && Double.isFinite(existing.dropZ())
                && Double.compare(expected.dropX(), existing.dropX()) == 0
                && Double.compare(expected.dropY(), existing.dropY()) == 0
                && Double.compare(expected.dropZ(), existing.dropZ()) == 0;
    }
}
