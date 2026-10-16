package me.copimine.clientbridge;

import me.copimine.protocol.ClientModHandshakePolicy;
import me.copimine.protocol.ClientModSessionRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Narcotics-owned publisher of verified v3 sessions shared with the Paper bridge senders. */
public final class ClientModSessionRegistryService implements ClientModSessionRegistry {
    private final Map<UUID, UUID> connectionEpochs = new ConcurrentHashMap<>();
    private final Map<UUID, PendingChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private final Map<UUID, VerifiedClientSession> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InvalidationListener> listeners = new CopyOnWriteArrayList<>();
    private final UUID processEpoch = UUID.randomUUID();
    private final AtomicLong nextConnectionEpoch = new AtomicLong(1L);
    private final Consumer<String> safeLog;
    private volatile Consumer<UUID> freshChallengeRequester;
    private volatile boolean shutdown;

    public ClientModSessionRegistryService(Consumer<String> safeLog) {
        this.safeLog = Objects.requireNonNull(safeLog, "safeLog");
    }

    @Override
    public synchronized Optional<VerifiedClientSession> current(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        VerifiedClientSession session = sessions.get(playerUuid);
        if (session == null) {
            return Optional.empty();
        }
        if (!Objects.equals(connectionEpochs.get(playerUuid), session.connectionEpoch())) {
            sessions.remove(playerUuid, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void requestFreshChallenge(UUID playerUuid) {
        if (playerUuid == null || shutdown) {
            return;
        }
        invalidate(playerUuid, InvalidationReason.FRESH_CHALLENGE);
        Consumer<UUID> requester = freshChallengeRequester;
        if (requester != null && !shutdown) {
            try {
                requester.accept(playerUuid);
            } catch (RuntimeException ignored) {
                logSafely("Unable to dispatch a fresh client-mod challenge.");
            }
        }
    }

    @Override
    public synchronized ListenerRegistration addInvalidationListener(InvalidationListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (shutdown) {
            throw new IllegalStateException("Client bridge session registry is shut down");
        }
        listeners.addIfAbsent(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public synchronized void removeInvalidationListener(InvalidationListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** Starts a new server connection epoch and invalidates all state from a prior connection. */
    public synchronized UUID openConnection(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        ensureRunning();
        invalidate(playerUuid, InvalidationReason.RECONNECT);
        UUID epoch = allocateConnectionEpoch();
        connectionEpochs.put(playerUuid, epoch);
        return epoch;
    }

    /** Stores the challenge that the existing v3 plugin-message handshake is about to send. */
    public synchronized void beginChallenge(
            UUID playerUuid, UUID challengeUuid, long expiresAtMillis, String requiredVersion) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(challengeUuid, "challengeUuid");
        ensureRunning();
        UUID connectionEpoch = connectionEpochs.get(playerUuid);
        if (connectionEpoch == null || expiresAtMillis <= 0L
                || requiredVersion == null || requiredVersion.isBlank()) {
            throw new IllegalStateException("Cannot begin a client-mod challenge without an active connection");
        }
        pendingChallenges.put(playerUuid,
                new PendingChallenge(challengeUuid, connectionEpoch, expiresAtMillis, requiredVersion));
    }

    /**
     * Validates the pending response with the existing v3 policy and publishes only its accepted
     * replacement session. A failed response never creates or replaces a verified session.
     */
    public synchronized boolean acceptChallengeResponse(
            UUID playerUuid,
            String expectedChallenge,
            String responseChallenge,
            long expiresAtMillis,
            long nowMillis,
            String actualModId,
            String actualVersion,
            int actualProtocol) {
        if (playerUuid == null || responseChallenge == null || shutdown) {
            return false;
        }
        UUID responseSessionUuid;
        try {
            responseSessionUuid = UUID.fromString(responseChallenge);
        } catch (IllegalArgumentException invalidSessionId) {
            return false;
        }
        if (!responseSessionUuid.toString().equals(responseChallenge)) {
            return false;
        }
        PendingChallenge pending = pendingChallenges.get(playerUuid);
        UUID currentEpoch = connectionEpochs.get(playerUuid);
        if (pending == null || currentEpoch == null
                || !pending.connectionEpoch().equals(currentEpoch)
                || !pending.challengeUuid().toString().equals(expectedChallenge)
                || pending.expiresAtMillis() != expiresAtMillis) {
            return false;
        }
        boolean accepted = ClientModHandshakePolicy.acceptsResponse(
                pending.challengeUuid().toString(), responseChallenge,
                pending.expiresAtMillis(), nowMillis,
                actualModId, ClientModHandshakePolicy.MOD_ID,
                actualVersion, pending.requiredVersion(),
                actualProtocol, ClientModHandshakePolicy.PROTOCOL_VERSION);
        if (!accepted) {
            return false;
        }
        if (!responseSessionUuid.equals(pending.challengeUuid())) {
            return false;
        }
        VerifiedClientSession verified = new VerifiedClientSession(
                playerUuid, responseSessionUuid, actualVersion, pending.connectionEpoch());
        if (!pendingChallenges.remove(playerUuid, pending)
                || !Objects.equals(connectionEpochs.get(playerUuid), pending.connectionEpoch())) {
            return false;
        }
        sessions.put(playerUuid, verified);
        return true;
    }

    /** Invalidates a challenge/session while preserving the current play connection epoch. */
    public synchronized void invalidate(UUID playerUuid, InvalidationReason reason) {
        if (playerUuid == null || shutdown && reason != InvalidationReason.SHUTDOWN) {
            return;
        }
        Objects.requireNonNull(reason, "reason");
        sessions.remove(playerUuid);
        pendingChallenges.remove(playerUuid);
        notifyInvalidationListeners(playerUuid, reason);
    }

    /** Closes the connection and retires its pending and verified bridge state. */
    public synchronized void closeConnection(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        invalidate(playerUuid, InvalidationReason.QUIT);
        connectionEpochs.remove(playerUuid);
    }

    /** Returns the opaque connection epoch for handshake coordination; senders use {@link #current(UUID)}. */
    public synchronized Optional<UUID> connectionEpoch(UUID playerUuid) {
        return playerUuid == null ? Optional.empty() : Optional.ofNullable(connectionEpochs.get(playerUuid));
    }

    /** Wires the v3 challenge dispatcher after the bridge listener has been constructed. */
    public synchronized void setFreshChallengeRequester(Consumer<UUID> requester) {
        ensureRunning();
        freshChallengeRequester = Objects.requireNonNull(requester, "requester");
    }

    /** Retires every player before clearing service state during plugin shutdown. */
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        Set<UUID> players = new HashSet<>();
        players.addAll(connectionEpochs.keySet());
        players.addAll(sessions.keySet());
        players.addAll(pendingChallenges.keySet());
        for (UUID playerUuid : new ArrayList<>(players)) {
            invalidate(playerUuid, InvalidationReason.SHUTDOWN);
        }
        shutdown = true;
        freshChallengeRequester = null;
        connectionEpochs.clear();
        pendingChallenges.clear();
        sessions.clear();
        listeners.clear();
    }

    private UUID allocateConnectionEpoch() {
        long value = nextConnectionEpoch.getAndIncrement();
        if (value <= 0L) {
            nextConnectionEpoch.set(Long.MIN_VALUE);
            throw new IllegalStateException("Client bridge connection epoch space exhausted");
        }
        return new UUID(processEpoch.getMostSignificantBits(), value);
    }

    private void notifyInvalidationListeners(UUID playerUuid, InvalidationReason reason) {
        for (InvalidationListener listener : listeners) {
            try {
                listener.onSessionInvalidated(playerUuid, reason);
            } catch (RuntimeException ignored) {
                logSafely("A client bridge sender failed during session retirement.");
            }
        }
    }

    private void logSafely(String message) {
        try {
            safeLog.accept(message);
        } catch (RuntimeException ignored) {
            // A diagnostic logger must never prevent another sender from retiring.
        }
    }

    private void ensureRunning() {
        if (shutdown) {
            throw new IllegalStateException("Client bridge session registry is shut down");
        }
    }

    private record PendingChallenge(
            UUID challengeUuid,
            UUID connectionEpoch,
            long expiresAtMillis,
            String requiredVersion) {
    }
}
