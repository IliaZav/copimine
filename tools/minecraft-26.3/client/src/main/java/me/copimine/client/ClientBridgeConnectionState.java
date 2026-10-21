package me.copimine.client;

import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.security.SecureRandom;

/**
 * Owns connection-scoped state for the authenticated client bridge.
 *
 * <p>All mutating and observing operations are synchronized because bridge packets are received on the
 * networking thread while queued mutations are rechecked on the client thread.</p>
 */
public final class ClientBridgeConnectionState {
    public static final int NONCE_BYTES = 32;
    public static final int STREAM_ID_BYTES = 16;
    public static final int MAX_STREAM_IDS_PER_SENDER = 256;

    private static final AtomicLong NEXT_LOCAL_EPOCH = new AtomicLong();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final EnumMap<Sender, SenderState> senders = new EnumMap<>(Sender.class);
    private long generationHighWater;
    private boolean hasGenerationHighWater;
    private final Set<String> seenStreamIds = new HashSet<>();
    private final EnumMap<Sender, Integer> seenStreamCounts = new EnumMap<>(Sender.class);

    private Connection connection;
    private UUID verifiedSessionId;
    private UUID controlHelloId;
    private long queueRevision;
    private boolean queueRevisionExhausted;

    public ClientBridgeConnectionState() {
        resetSenders();
        for (Sender sender : Sender.values()) {
            seenStreamCounts.put(sender, 0);
        }
    }

    /** Opens a new local play connection, retiring all state from any earlier connection. */
    public synchronized Connection open(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        close();

        long localEpoch = nextLocalEpoch();
        byte[] nonceBytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonceBytes);
        String nonce = BASE64_URL_ENCODER.encodeToString(nonceBytes);
        connection = new Connection(playerUuid, localEpoch, nonce);
        return connection;
    }

    /**
     * Applies a newly verified v3 client-mod session. Reasserting the current session is idempotent;
     * replacing it invalidates queued work and resets sender state while preserving connection replay
     * history and event generations.
     */
    public synchronized void verifiedSessionChanged(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        requireOpenConnection();

        if (sessionId.equals(verifiedSessionId)) {
            return;
        }
        if (queueRevisionExhausted || queueRevision == Long.MAX_VALUE) {
            queueRevisionExhausted = true;
            verifiedSessionId = null;
            controlHelloId = null;
            resetSenders();
            throw new IllegalStateException("Bridge queue revision exhausted; connection requires reconnect");
        }

        queueRevision++;
        verifiedSessionId = sessionId;
        controlHelloId = null;
        resetSenders();
    }

    /**
     * Invalidates the accepted v3 session while a different challenge is being negotiated.
     * The connection nonce, replay history, and generation high-water marks remain bound to
     * this play connection, but no queued mutation or sender stream remains usable.
     */
    public synchronized void invalidateVerifiedSession() {
        if (connection == null || verifiedSessionId == null) {
            return;
        }
        if (queueRevisionExhausted || queueRevision == Long.MAX_VALUE) {
            queueRevisionExhausted = true;
            verifiedSessionId = null;
            controlHelloId = null;
            resetSenders();
            throw new IllegalStateException("Bridge queue revision exhausted; connection requires reconnect");
        }
        queueRevision++;
        verifiedSessionId = null;
        controlHelloId = null;
        resetSenders();
    }

    /** Returns the immutable current connection binding, if one is open. */
    public synchronized Optional<Connection> currentConnection() {
        return Optional.ofNullable(connection);
    }

    /** Returns the current verified v3 session, if a challenge has been accepted. */
    public synchronized Optional<UUID> verifiedSession() {
        return Optional.ofNullable(verifiedSessionId);
    }

    /** Captures the connection and verified-session fence for a queued client-thread mutation. */
    public synchronized QueueTicket captureQueueTicket() {
        requireOpenConnection();
        return new QueueTicket(connection.localEpoch(), queueRevision, verifiedSessionId);
    }

    /** Checks a queued mutation's ticket immediately before applying it on the client thread. */
    public synchronized boolean isCurrent(QueueTicket ticket) {
        return ticket != null
                && connection != null
                && !queueRevisionExhausted
                && ticket.localEpoch() == connection.localEpoch()
                && ticket.queueRevision() == queueRevision
                && Objects.equals(ticket.verifiedSessionId(), verifiedSessionId);
    }

    /**
     * Installs a newly acknowledged sender stream. Stream IDs are canonical 16-byte Base64URL values,
     * unique across both senders for this play connection, and retained for the lifetime of the
     * connection. Installing a fresh END_EVENT stream also begins a fresh generation fence because
     * packets from the retired stream can no longer pass the stream binding check.
     * Returns false for an absent verified session, replay, malformed ID, or exhausted history.
     */
    public synchronized boolean installStream(Sender sender, String streamId) {
        Objects.requireNonNull(sender, "sender");
        if (connection == null || verifiedSessionId == null || !isCanonicalStreamId(streamId)) {
            return false;
        }

        SenderState state = senders.get(sender);
        if (streamId.equals(state.activeStreamId)) {
            return true;
        }
        if (seenStreamIds.contains(streamId)
                || seenStreamCounts.get(sender) >= MAX_STREAM_IDS_PER_SENDER) {
            return false;
        }

        seenStreamIds.add(streamId);
        seenStreamCounts.put(sender, seenStreamCounts.get(sender) + 1);
        state.activeStreamId = streamId;
        state.ready = false;
        state.outstandingRequestId = null;
        if (sender == Sender.END_EVENT) {
            generationHighWater = 0L;
            hasGenerationHighWater = false;
        }
        return true;
    }

    /** Retires active sender state but retains the request counter and connection stream history. */
    public synchronized void retireStream(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        SenderState state = senders.get(sender);
        state.activeStreamId = null;
        state.ready = false;
        state.outstandingRequestId = null;
    }

    public synchronized Optional<String> activeStream(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        return Optional.ofNullable(senders.get(sender).activeStreamId);
    }

    public synchronized boolean isReady(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        return senders.get(sender).ready;
    }

    /** Marks a sender ready only when it has a live connection, verified session, and active stream. */
    public synchronized void setReady(Sender sender, boolean ready) {
        Objects.requireNonNull(sender, "sender");
        SenderState state = senders.get(sender);
        state.ready = ready
                && connection != null
                && verifiedSessionId != null
                && state.activeStreamId != null
                && !state.counterExhausted;
    }

    /** Allocates the next positive per-sender request counter without wrapping. */
    public synchronized long nextRequestCounter(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        requireVerifiedConnection();
        SenderState state = senders.get(sender);
        if (state.counterExhausted || state.requestCounter >= Long.MAX_VALUE - 1) {
            state.counterExhausted = true;
            state.ready = false;
            state.activeStreamId = null;
            state.outstandingRequestId = null;
            throw new IllegalStateException("Bridge sender request counter exhausted; session replacement required");
        }

        state.requestCounter++;
        return state.requestCounter;
    }

    public synchronized long lastRequestCounter(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        return senders.get(sender).requestCounter;
    }

    /** Rolls back the most recently allocated counter when its request was definitely not sent. */
    public synchronized void rollbackUnsentRequestCounter(Sender sender, long counter) {
        Objects.requireNonNull(sender, "sender");
        SenderState state = senders.get(sender);
        if (counter <= 0 || state.requestCounter != counter || state.counterExhausted) {
            throw new IllegalStateException("Only the latest unsent bridge request counter can be rolled back");
        }
        state.requestCounter = counter - 1;
    }

    public synchronized void setOutstandingRequestId(Sender sender, UUID requestId) {
        Objects.requireNonNull(sender, "sender");
        requireVerifiedConnection();
        senders.get(sender).outstandingRequestId = Objects.requireNonNull(requestId, "requestId");
    }

    public synchronized Optional<UUID> outstandingRequestId(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        return Optional.ofNullable(senders.get(sender).outstandingRequestId);
    }

    public synchronized void setControlHelloId(UUID helloId) {
        requireVerifiedConnection();
        controlHelloId = Objects.requireNonNull(helloId, "helloId");
    }

    public synchronized Optional<UUID> controlHelloId() {
        return Optional.ofNullable(controlHelloId);
    }

    /** Records the monotonically increasing event generation within the active END_EVENT stream. */
    public synchronized boolean recordGeneration(long generation) {
        if (generation <= 0
                || generation == Long.MAX_VALUE
                || connection == null
                || verifiedSessionId == null
                || senders.get(Sender.END_EVENT).activeStreamId == null) {
            return false;
        }

        if (hasGenerationHighWater && generation < generationHighWater) {
            return false;
        }
        if (!hasGenerationHighWater || generation > generationHighWater) {
            generationHighWater = generation;
            hasGenerationHighWater = true;
        }
        return true;
    }

    /** Returns the event generation fence for the active END_EVENT stream, if one was recorded. */
    public synchronized OptionalLong generationHighWater() {
        return hasGenerationHighWater ? OptionalLong.of(generationHighWater) : OptionalLong.empty();
    }

    public synchronized int seenStreamCount(Sender sender) {
        Objects.requireNonNull(sender, "sender");
        return seenStreamCounts.get(sender);
    }

    /** Clears every connection-scoped state item and invalidates all previously captured tickets. */
    public synchronized void close() {
        connection = null;
        verifiedSessionId = null;
        controlHelloId = null;
        queueRevision = 0;
        queueRevisionExhausted = false;
        resetSenders();
        generationHighWater = 0L;
        hasGenerationHighWater = false;
        seenStreamIds.clear();
        for (Sender sender : Sender.values()) {
            seenStreamCounts.put(sender, 0);
        }
    }

    private void requireOpenConnection() {
        if (connection == null) {
            throw new IllegalStateException("Bridge play connection is not open");
        }
    }

    private void requireVerifiedConnection() {
        requireOpenConnection();
        if (verifiedSessionId == null) {
            throw new IllegalStateException("Bridge client-mod session is not verified");
        }
    }

    private void resetSenders() {
        senders.clear();
        for (Sender sender : Sender.values()) {
            senders.put(sender, new SenderState());
        }
    }

    private static long nextLocalEpoch() {
        return NEXT_LOCAL_EPOCH.updateAndGet(current -> {
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("Bridge local connection epoch exhausted");
            }
            return current + 1;
        });
    }

    private static boolean isCanonicalStreamId(String streamId) {
        if (streamId == null || streamId.length() != 22 || !streamId.matches("[A-Za-z0-9_-]{22}")) {
            return false;
        }
        try {
            byte[] decoded = BASE64_URL_DECODER.decode(streamId);
            return decoded.length == STREAM_ID_BYTES
                    && streamId.equals(BASE64_URL_ENCODER.encodeToString(decoded));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    public enum Sender {
        END_EVENT,
        NARCOTICS
    }

    /** Immutable player/epoch/nonce binding for one local Minecraft play connection. */
    public record Connection(UUID playerUuid, long localEpoch, String nonce) {
        public Connection {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(nonce, "nonce");
            if (localEpoch <= 0 || !isCanonicalNonce(nonce)) {
                throw new IllegalArgumentException("Invalid bridge connection binding");
            }
        }
    }

    /** Immutable fence attached to each queued main-thread mutation. */
    public record QueueTicket(long localEpoch, long queueRevision, UUID verifiedSessionId) {
        public QueueTicket {
            if (localEpoch <= 0 || queueRevision < 0) {
                throw new IllegalArgumentException("Invalid bridge queue ticket");
            }
        }
    }

    private static boolean isCanonicalNonce(String nonce) {
        if (nonce.length() != 43 || !nonce.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        try {
            byte[] decoded = BASE64_URL_DECODER.decode(nonce);
            return decoded.length == NONCE_BYTES
                    && nonce.equals(BASE64_URL_ENCODER.encodeToString(decoded));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static final class SenderState {
        private String activeStreamId;
        private UUID outstandingRequestId;
        private long requestCounter;
        private boolean counterExhausted;
        private boolean ready;
    }
}
