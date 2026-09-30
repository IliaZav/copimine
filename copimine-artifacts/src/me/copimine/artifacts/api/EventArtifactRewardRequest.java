package me.copimine.artifacts.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable request accepted by the End Rift reward boundary. */
public record EventArtifactRewardRequest(
        String eventId,
        String idempotencyKey,
        String itemId,
        UUID recipientUuid,
        String recipientName,
        boolean worldDrop) {

    public EventArtifactRewardRequest {
        eventId = requireText(eventId, "eventId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        itemId = requireText(itemId, "itemId").toLowerCase(java.util.Locale.ROOT);
        recipientName = recipientName == null ? "" : recipientName.trim();
        if (worldDrop && recipientUuid != null) {
            throw new IllegalArgumentException("world-drop reward cannot be owner-bound");
        }
        if (!worldDrop && recipientUuid == null) {
            throw new IllegalArgumentException("player reward requires a recipient UUID");
        }
    }

    public static EventArtifactRewardRequest toPlayer(
            String eventId,
            String idempotencyKey,
            String itemId,
            UUID recipientUuid,
            String recipientName) {
        return new EventArtifactRewardRequest(
                eventId, idempotencyKey, itemId, Objects.requireNonNull(recipientUuid),
                recipientName, false);
    }

    public static EventArtifactRewardRequest worldDrop(
            String eventId, String idempotencyKey, String itemId) {
        return new EventArtifactRewardRequest(
                eventId, idempotencyKey, itemId, null, "", true);
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
