package me.copimine.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared Paper service for the currently verified v3 client handshake. */
public interface ClientModSessionRegistry {
    /** Returns a session only while it is verified for the player's current server connection. */
    Optional<VerifiedClientSession> current(UUID playerUuid);

    /** Retires the current session and notifies all senders before dispatching a fresh v3 challenge. */
    void requestFreshChallenge(UUID playerUuid);

    /** Registers a sender that must synchronously retire all state when a session is invalidated. */
    ListenerRegistration addInvalidationListener(InvalidationListener listener);

    /** Removes a previously registered invalidation listener. */
    void removeInvalidationListener(InvalidationListener listener);

    record VerifiedClientSession(
            UUID playerUuid,
            UUID sessionUuid,
            String clientVersion,
            UUID connectionEpoch) {
        public VerifiedClientSession {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(sessionUuid, "sessionUuid");
            Objects.requireNonNull(connectionEpoch, "connectionEpoch");
            if (clientVersion == null || clientVersion.isBlank()) {
                throw new IllegalArgumentException("clientVersion must contain text");
            }
        }
    }

    enum InvalidationReason {
        FRESH_CHALLENGE,
        RECONNECT,
        QUIT,
        REJECTED,
        VERSION_CHANGED,
        SHUTDOWN
    }

    @FunctionalInterface
    interface InvalidationListener {
        void onSessionInvalidated(UUID playerUuid, InvalidationReason reason);
    }

    @FunctionalInterface
    interface ListenerRegistration extends AutoCloseable {
        @Override
        void close();
    }
}
