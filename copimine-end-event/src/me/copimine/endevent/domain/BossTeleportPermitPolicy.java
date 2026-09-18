package me.copimine.endevent.domain;

import java.util.concurrent.atomic.AtomicBoolean;

/** A short-lived, single-use permit for one event generation and entity. */
public final class BossTeleportPermitPolicy {
    private BossTeleportPermitPolicy() {
    }

    public static Permit issue(String eventId, long generation, String entityId,
                               long issuedAtMillis, long expiresAtMillis) {
        return new Permit(eventId, generation, entityId, issuedAtMillis, expiresAtMillis);
    }

    public static boolean accept(Permit permit, String eventId, long generation,
                                 String entityId, long nowMillis) {
        if (permit == null || eventId == null || eventId.isBlank()
                || !eventId.equals(permit.eventId()) || generation <= 0L
                || generation != permit.generation()
                || entityId == null || entityId.isBlank()
                || !entityId.equals(permit.entityId())
                || nowMillis < permit.issuedAtMillis() || nowMillis > permit.expiresAtMillis()) {
            return false;
        }
        return permit.consumed.compareAndSet(false, true);
    }

    public static final class Permit {
        private final String eventId;
        private final long generation;
        private final String entityId;
        private final long issuedAtMillis;
        private final long expiresAtMillis;
        private final AtomicBoolean consumed = new AtomicBoolean(false);

        private Permit(String eventId, long generation, String entityId,
                       long issuedAtMillis, long expiresAtMillis) {
            this.eventId = eventId == null ? "" : eventId.trim();
            this.generation = generation;
            this.entityId = entityId == null ? "" : entityId.trim();
            this.issuedAtMillis = issuedAtMillis;
            this.expiresAtMillis = expiresAtMillis;
            if (this.eventId.isBlank() || generation <= 0L || this.entityId.isBlank()
                    || issuedAtMillis < 0L || expiresAtMillis < issuedAtMillis) {
                throw new IllegalArgumentException("invalid combat teleport permit");
            }
        }

        public String entityId() {
            return entityId;
        }

        public String eventId() {
            return eventId;
        }

        public long generation() {
            return generation;
        }

        public long issuedAtMillis() {
            return issuedAtMillis;
        }

        public long expiresAtMillis() {
            return expiresAtMillis;
        }
    }
}
