package me.copimine.clientbridge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small, deterministic admission state machine. Bukkit scheduling and player
 * messages are deliberately kept outside this class so reconnect races can be
 * tested without a live server.
 */
public final class ClientReadyAdmission {
    public static final int REQUIRED_PROTOCOL = 3;
    public static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;

    private final long timeoutMillis;
    private final Map<UUID, PendingClientAdmission> pending = new ConcurrentHashMap<>();

    public ClientReadyAdmission(long timeoutMillis) {
        this.timeoutMillis = Math.max(10_000L, timeoutMillis);
    }

    public PendingClientAdmission onJoin(UUID playerId, long startedAtMillis) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is required");
        }
        PendingClientAdmission admission = new PendingClientAdmission(
                playerId,
                UUID.randomUUID(),
                startedAtMillis,
                false,
                "",
                null,
                0,
                0L
        );
        pending.put(playerId, admission);
        return admission;
    }

    public ReadyDecision onReady(UUID playerId, ReadyRequest request, long receivedAtMillis) {
        PendingClientAdmission current = pending.get(playerId);
        if (current == null) {
            return decision(Decision.AFTER_TIMEOUT, null, false, 0, "CLIENT_READY_AFTER_TIMEOUT");
        }
        if (request == null || request.clientVersion() == null || request.clientVersion().isBlank()) {
            pending.remove(playerId);
            return decision(Decision.MALFORMED_READY, current, false, 0, "MALFORMED_READY");
        }

        PendingClientAdmission observed = current.withReady(request, receivedAtMillis);
        if (current.accepted()) {
            pending.put(playerId, observed);
            return decision(Decision.DUPLICATE_ACCEPTED, observed, true, request.protocolVersion(), "ACCEPTED");
        }
        if (request.protocolVersion() != REQUIRED_PROTOCOL) {
            pending.remove(playerId, current);
            return decision(Decision.PROTOCOL_MISMATCH, observed, false, request.protocolVersion(), "PROTOCOL_MISMATCH");
        }

        PendingClientAdmission accepted = observed.withAccepted(true);
        pending.put(playerId, accepted);
        return decision(Decision.ACCEPTED, accepted, true, request.protocolVersion(), "ACCEPTED");
    }

    public ReadyDecision onTimeout(UUID playerId, UUID expectedAttemptId, long nowMillis) {
        PendingClientAdmission current = pending.get(playerId);
        if (current == null || expectedAttemptId == null || !expectedAttemptId.equals(current.joinAttemptId())) {
            return decision(Decision.STALE_ATTEMPT, current, false, protocolOf(current), "STALE_JOIN_ATTEMPT");
        }
        if (current.accepted()) {
            return decision(Decision.ALREADY_ACCEPTED, current, true, protocolOf(current), "ACCEPTED");
        }
        if (nowMillis < current.startedAtMillis() + timeoutMillis) {
            return decision(Decision.NOT_DUE, current, false, protocolOf(current), "CLIENT_READY_PENDING");
        }
        pending.remove(playerId, current);
        return decision(Decision.TIMEOUT, current, false, protocolOf(current), "CLIENT_READY_TIMEOUT");
    }

    public void onQuit(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    public void clear() {
        pending.clear();
    }

    public PendingClientAdmission snapshot(UUID playerId) {
        return playerId == null ? null : pending.get(playerId);
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    private static int protocolOf(PendingClientAdmission admission) {
        return admission == null || admission.clientProtocol() == null ? 0 : admission.clientProtocol();
    }

    private static ReadyDecision decision(
            Decision decision,
            PendingClientAdmission state,
            boolean accepted,
            int receivedProtocol,
            String reasonCode) {
        return new ReadyDecision(
                decision,
                state,
                new ReadyAck(accepted, REQUIRED_PROTOCOL, receivedProtocol, reasonCode),
                reasonCode
        );
    }

    public enum Decision {
        ACCEPTED,
        DUPLICATE_ACCEPTED,
        PROTOCOL_MISMATCH,
        MALFORMED_READY,
        TIMEOUT,
        AFTER_TIMEOUT,
        STALE_ATTEMPT,
        ALREADY_ACCEPTED,
        NOT_DUE
    }

    public record ReadyRequest(int protocolVersion, String clientVersion) {
    }

    public record ReadyAck(boolean accepted, int requiredProtocol, int receivedProtocol, String reason) {
    }

    public record ReadyDecision(
            Decision decision,
            PendingClientAdmission state,
            ReadyAck ack,
            String reasonCode) {

        public boolean shouldKick() {
            return decision == Decision.PROTOCOL_MISMATCH
                    || decision == Decision.MALFORMED_READY
                    || decision == Decision.TIMEOUT;
        }
    }

    public record PendingClientAdmission(
            UUID playerId,
            UUID joinAttemptId,
            long startedAtMillis,
            boolean accepted,
            String clientVersion,
            Integer clientProtocol,
            int readyPacketCount,
            long lastReadyAtMillis) {

        private PendingClientAdmission withReady(ReadyRequest request, long receivedAtMillis) {
            return new PendingClientAdmission(
                    playerId,
                    joinAttemptId,
                    startedAtMillis,
                    accepted,
                    request.clientVersion(),
                    request.protocolVersion(),
                    readyPacketCount + 1,
                    receivedAtMillis
            );
        }

        private PendingClientAdmission withAccepted(boolean value) {
            return new PendingClientAdmission(
                    playerId,
                    joinAttemptId,
                    startedAtMillis,
                    value,
                    clientVersion,
                    clientProtocol,
                    readyPacketCount,
                    lastReadyAtMillis
            );
        }
    }
}
