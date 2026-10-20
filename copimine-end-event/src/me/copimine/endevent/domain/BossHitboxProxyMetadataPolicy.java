package me.copimine.endevent.domain;

import java.util.Objects;
import java.util.UUID;

/** Pure ownership and slot-metadata checks for a boss Interaction proxy. */
public final class BossHitboxProxyMetadataPolicy {
    private BossHitboxProxyMetadataPolicy() {
    }

    /** Returns whether the kind/event tags are enough to attribute a proxy to this event. */
    public static boolean isCurrentEvent(Actual actual, String expectedKind,
                                         String expectedEventId) {
        return actual != null && expectedKind != null && expectedEventId != null
                && expectedKind.equals(actual.kind())
                && expectedEventId.equals(actual.eventId());
    }

    /** Requires every durable ownership and canonical slot field to match. */
    public static boolean matches(Actual actual, Expected expected) {
        return actual != null && expected != null
                && Objects.equals(expected.kind(), actual.kind())
                && Objects.equals(expected.eventId(), actual.eventId())
                && Objects.equals(expected.parentUuid(), actual.parentUuid())
                && expected.generation() == actual.generation()
                && expected.partId() == actual.partId()
                && expected.segmentIndex() == actual.segmentIndex();
    }

    public record Expected(String kind, String eventId, UUID parentUuid, long generation,
                           BossHitboxProfile.PartId partId, int segmentIndex) {
        public Expected {
            if (kind == null || kind.isBlank() || eventId == null || eventId.isBlank()
                    || parentUuid == null || partId == null || segmentIndex < 0) {
                throw new IllegalArgumentException("invalid expected boss proxy metadata");
            }
        }
    }

    /** Nullable fields intentionally model missing or malformed PDC values. */
    public record Actual(String kind, String eventId, UUID parentUuid, long generation,
                         BossHitboxProfile.PartId partId, int segmentIndex) {
    }
}
