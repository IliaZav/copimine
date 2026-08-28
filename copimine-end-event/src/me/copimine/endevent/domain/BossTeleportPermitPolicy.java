package me.copimine.endevent.domain;

import java.util.concurrent.atomic.AtomicBoolean;

/** A short-lived, single-use permit for an event-owned combat teleport. */
public final class BossTeleportPermitPolicy {
    private BossTeleportPermitPolicy() {
    }

    public static Permit issue(String entityId, long issuedAtMillis, long expiresAtMillis) {
        return new Permit(entityId, issuedAtMillis, expiresAtMillis);
    }

    public static boolean accept(Permit permit, String entityId, long nowMillis) {
        if (permit == null || entityId == null || entityId.isBlank()
                || !entityId.equals(permit.entityId())
                || nowMillis < permit.issuedAtMillis() || nowMillis > permit.expiresAtMillis()) {
            return false;
        }
        return permit.consumed.compareAndSet(false, true);
    }

    public static final class Permit {
        private final String entityId;
        private final long issuedAtMillis;
        private final long expiresAtMillis;
        private final AtomicBoolean consumed = new AtomicBoolean(false);

        private Permit(String entityId, long issuedAtMillis, long expiresAtMillis) {
            this.entityId = entityId == null ? "" : entityId.trim();
            this.issuedAtMillis = issuedAtMillis;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String entityId() {
            return entityId;
        }

        public long issuedAtMillis() {
            return issuedAtMillis;
        }

        public long expiresAtMillis() {
            return expiresAtMillis;
        }
    }
}
