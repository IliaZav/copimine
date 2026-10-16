package me.copimine.endevent.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import me.copimine.protocol.BridgeControlBodyCodec;
import me.copimine.protocol.BridgeEnvelope;
import me.copimine.protocol.BridgeEnvelopeCodec;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.BridgeSigningPolicy;
import me.copimine.protocol.ClientModSessionRegistry;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import me.copimine.protocol.InboundBridgeRequestRateLimiter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** End Event's independent v4 stream, readiness gate, and request receiver. */
public final class EndEventBridgeV4Sender implements EndEventBridgeV4Access, PluginMessageListener {
    public static final String CHANNEL = "copimine:client_bridge_v4";
    private static final long HEARTBEAT_INTERVAL_MILLIS = 5_000L;
    private static final long ENVELOPE_VALIDITY_MILLIS = 30_000L;
    private static final int MAX_REQUEST_BYTES = BridgeRequestCodec.MAX_REQUEST_BYTES;
    private static final int MAX_STREAMS_PER_CONNECTION = 256;
    private static final long REOPEN_INTERVAL_MILLIS = 1_000L;
    private static final long CONTROL_HELLO_ACK_REPLAY_INTERVAL_MILLIS = 1_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientModSessionRegistry registry;
    private final BridgeSigner signer;
    private final LongSupplier clock;
    private final Transport transport;
    private final Function<UUID, EventContext> eventContext;
    private final BiConsumer<Player, PrisonerAbilityRequest> abilityHandler;
    private final InboundBridgeRequestRateLimiter inboundRateLimiter;
    private final Consumer<UUID> readyListener;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Map<UUID, Player> playerHandles = new HashMap<>();
    private final ClientModSessionRegistry.InvalidationListener invalidationListener = this::onInvalidated;
    private ClientModSessionRegistry.ListenerRegistration listenerRegistration;
    private Plugin plugin;
    private boolean closed;

    public EndEventBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
            LongSupplier clock, Transport transport, Function<UUID, EventContext> eventContext,
            BiConsumer<Player, PrisonerAbilityRequest> abilityHandler) {
        this(registry, signer, clock, transport, eventContext, abilityHandler,
                new InboundBridgeRequestRateLimiter(), playerUuid -> { });
    }

    public EndEventBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
            LongSupplier clock, Transport transport, Function<UUID, EventContext> eventContext,
            BiConsumer<Player, PrisonerAbilityRequest> abilityHandler,
            InboundBridgeRequestRateLimiter inboundRateLimiter) {
        this(registry, signer, clock, transport, eventContext, abilityHandler,
                inboundRateLimiter, playerUuid -> { });
    }

    public EndEventBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
            LongSupplier clock, Transport transport, Function<UUID, EventContext> eventContext,
            BiConsumer<Player, PrisonerAbilityRequest> abilityHandler,
            Consumer<UUID> readyListener) {
        this(registry, signer, clock, transport, eventContext, abilityHandler,
                new InboundBridgeRequestRateLimiter(), readyListener);
    }

    public EndEventBridgeV4Sender(ClientModSessionRegistry registry, BridgeSigner signer,
            LongSupplier clock, Transport transport, Function<UUID, EventContext> eventContext,
            BiConsumer<Player, PrisonerAbilityRequest> abilityHandler,
            InboundBridgeRequestRateLimiter inboundRateLimiter,
            Consumer<UUID> readyListener) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.eventContext = Objects.requireNonNull(eventContext, "eventContext");
        this.abilityHandler = Objects.requireNonNull(abilityHandler, "abilityHandler");
        this.inboundRateLimiter = Objects.requireNonNull(inboundRateLimiter, "inboundRateLimiter");
        this.readyListener = Objects.requireNonNull(readyListener, "readyListener");
    }

    /** Called by the owning plugin on the server thread. */
    public synchronized void tick() {
        if (closed) {
            return;
        }
        long now = clock.getAsLong();
        for (Player player : new ArrayList<>(playerHandles.values())) {
            PlayerState state = currentReadyState(player);
            if (state == null || !"0.1.3+26.3".equals(state.sessionVersion)) {
                continue;
            }
            StreamState stream = state.stream;
            if (now <= 0L || now < stream.nextHeartbeatAtMillis) {
                continue;
            }
            if (stream.serverCounter == Long.MAX_VALUE) {
                retireStream(state);
                continue;
            }
            byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderHeartbeat(
                    BridgeRequestCodec.Sender.END_EVENT, state.sessionUuid));
            try {
                SignedBytes signed = signAndSend(player, state, stream.streamId,
                        stream.serverCounter + 1L, now, body);
                if (signed == null) {
                    retireStream(state);
                } else if (state.stream == stream) {
                    stream.serverCounter++;
                    stream.nextHeartbeatAtMillis = now + HEARTBEAT_INTERVAL_MILLIS;
                }
            } catch (RuntimeException signingFailure) {
                retireStream(state);
            }
        }
    }

    @Override
    public synchronized void register(Plugin owner) {
        if (closed || plugin != null) {
            return;
        }
        plugin = Objects.requireNonNull(owner, "owner");
        listenerRegistration = registry.addInvalidationListener(invalidationListener);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        for (Player player : Bukkit.getOnlinePlayers()) {
            registerPlayer(player);
        }
    }

    /** Registers only a live server-side player handle; connection identity still comes from the registry. */
    public synchronized void registerPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        playerHandles.put(player.getUniqueId(), player);
    }

    @Override
    public synchronized void requestFreshChallenges(Collection<UUID> onlinePlayers) {
        if (closed || onlinePlayers == null) {
            return;
        }
        for (UUID playerUuid : new LinkedHashSet<>(onlinePlayers)) {
            if (playerUuid == null) {
                continue;
            }
            retireForFreshChallenge(playerUuid);
            registry.requestFreshChallenge(playerUuid);
        }
    }

    @Override
    public synchronized boolean onRequest(Player player, byte[] requestBytes) {
        return onRequest(player, requestBytes, true);
    }

    private synchronized boolean onRequest(Player player, byte[] requestBytes, boolean admitted) {
        if (closed || player == null || !player.isOnline() || requestBytes == null
                || requestBytes.length < BridgeRequestCodec.COMMON_HEADER_BYTES
                || requestBytes.length > MAX_REQUEST_BYTES) {
            return false;
        }
        UUID playerUuid = player.getUniqueId();
        if (playerUuid == null) {
            return false;
        }
        byte[] exactRequest = requestBytes.clone();
        final BridgeRequestCodec.Request request;
        try {
            request = BridgeRequestCodec.decode(exactRequest);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (!playerUuid.equals(request.playerUuid())) {
            return false;
        }
        if (request instanceof BridgeRequestCodec.ControlHello hello) {
            return admitted && handleControlHello(player, hello, exactRequest);
        }
        PlayerState state = players.get(playerUuid);
        if (!matchesCurrentBinding(player, state, request.clientModSessionUuid(), request.clientNonce())) {
            return false;
        }
        if (request instanceof BridgeRequestCodec.SenderReadyConfirm confirm) {
            return admitted && handleReadyConfirm(player, state, confirm, exactRequest);
        }
        if (request instanceof BridgeRequestCodec.SenderReady ready) {
            if (ready.sender() != BridgeRequestCodec.Sender.END_EVENT) {
                return false;
            }
            return handleCountedRequest(player, state, ready, exactRequest, admitted);
        }
        if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen) {
            if (reopen.sender() != BridgeRequestCodec.Sender.END_EVENT) {
                return false;
            }
            return handleCountedRequest(player, state, reopen, exactRequest, admitted);
        }
        if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest ability) {
            return handleCountedRequest(player, state, ability, exactRequest, admitted);
        }
        return false;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }
        if (player == null || !player.isOnline() || message == null || message.length > MAX_REQUEST_BYTES
                || !BridgeRequestCodec.classifyRecipient(message).includesEndEvent()) {
            return;
        }
        onRequest(player, message, inboundRateLimiter.allow(player.getUniqueId()));
    }

    @Override
    public synchronized boolean send(UUID recipient, String type, String instanceId, long durationMillis,
            String subjectOrMode, String visualId, int health, int maxHealth,
            float progress, String clearPolicy, String status) {
        return send(recipient, type, instanceId, durationMillis, subjectOrMode, visualId,
                health, maxHealth, progress, clearPolicy, status, "");
    }

    @Override
    public synchronized boolean send(UUID recipient, String type, String instanceId, long durationMillis,
            String subjectOrMode, String visualId, int health, int maxHealth,
            float progress, String clearPolicy, String status, String source) {
        Player player = playerFor(recipient);
        if (player == null || !player.isOnline()) {
            return false;
        }
        PlayerState state = currentReadyState(player);
        if (state == null) {
            return false;
        }
        EventContext context = currentEventContext(recipient);
        if (context == null || !ensureContextBound(player, state, context)) {
            return false;
        }
        final EndEventBridgeBodyCodec.Type bodyType;
        try {
            bodyType = EndEventBridgeBodyCodec.Type.valueOf(type);
        } catch (RuntimeException invalidType) {
            return false;
        }
        long timestamp = clock.getAsLong();
        if (timestamp <= 0) {
            return false;
        }
        float boundedProgress = Float.isFinite(progress) ? Math.max(0.0F, Math.min(1.0F, progress)) : 0.0F;
        long duration = Math.max(0L, Math.min(600_000L, durationMillis));
        EndEventBridgeBodyCodec.Body body = new EndEventBridgeBodyCodec.Body(bodyType,
                context.generation(), timestamp, context.eventUuid(), nullToEmpty(instanceId), nullToEmpty(source),
                nullToEmpty(visualId), duration, boundedProgress, Math.max(0, health), Math.max(0, maxHealth),
                nullToEmpty(subjectOrMode), nullToEmpty(clearPolicy), "", "", nullToEmpty(status));
        return sendApplication(player, state, body);
    }

    @Override
    public synchronized boolean bindContext(UUID recipient, EventContext context) {
        Player player = playerFor(recipient);
        if (player == null || !player.isOnline() || context == null || !validContext(context)) {
            return false;
        }
        PlayerState state = currentReadyState(player);
        return state != null && sendContextBind(player, state, context);
    }

    @Override
    public synchronized void unbindContext(UUID playerUuid) {
        if (closed || playerUuid == null) {
            return;
        }
        Player player = playerFor(playerUuid);
        PlayerState state = currentReadyState(player);
        if (player == null || state == null || state.stream == null || state.stream.boundContext == null) {
            return;
        }
        EventContext old = state.stream.boundContext;
        long timestamp = clock.getAsLong();
        if (timestamp <= 0) {
            return;
        }
        EndEventBridgeBodyCodec.Body body = new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.CONTEXT_UNBIND, old.generation(), timestamp, old.eventUuid(),
                "", "", "", 0L, 0.0F, 0L, 0L, "", "", "", "", "");
        if (sendApplication(player, state, body)) {
            state.stream.boundContext = null;
        }
    }

    @Override
    public synchronized boolean isReady(UUID playerUuid) {
        Player player = playerFor(playerUuid);
        PlayerState state = currentReadyState(player);
        return state != null && state.stream != null && state.stream.readyConfirmed;
    }

    @Override
    public synchronized boolean isCurrentVerifiedSession(UUID playerUuid, String sessionUuid) {
        if (closed || playerUuid == null || sessionUuid == null) {
            return false;
        }
        try {
            UUID expected = UUID.fromString(sessionUuid);
            return registry.current(playerUuid)
                    .filter(current -> current.playerUuid().equals(playerUuid)
                            && current.sessionUuid().equals(expected))
                    .isPresent();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (plugin != null) {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        } else {
            registry.removeInvalidationListener(invalidationListener);
        }
        players.clear();
        playerHandles.clear();
        inboundRateLimiter.clear();
    }

    private static int heartbeatFeatures(PlayerState state) {
        return "0.1.3+26.3".equals(state.sessionVersion)
                ? BridgeControlBodyCodec.SENDER_HEARTBEAT_FEATURE : 0;
    }

    private boolean handleControlHello(Player player, BridgeRequestCodec.ControlHello hello, byte[] exactRequest) {
        ClientModSessionRegistry.VerifiedClientSession current = registry.current(player.getUniqueId()).orElse(null);
        if (current == null || !current.playerUuid().equals(player.getUniqueId())
                || !current.sessionUuid().equals(hello.clientModSessionUuid())) {
            return false;
        }
        PlayerState state = players.get(player.getUniqueId());
        if (state == null || !state.connectionEpoch.equals(current.connectionEpoch())) {
            state = new PlayerState(player.getUniqueId(), current);
            players.put(player.getUniqueId(), state);
        } else if (!Objects.equals(state.sessionUuid, current.sessionUuid())) {
            retireStream(state);
            state.sessionUuid = current.sessionUuid();
            state.sessionVersion = current.clientVersion();
            state.requestCounter = 0L;
            state.lastRequestCounter = 0L;
            state.lastRequestBytes = null;
            state.lastResponseBytes = null;
            state.lastRequestWasDenied = false;
            state.controlHelloBytes = null;
            state.controlHelloResponse = null;
            state.lastControlHelloAckReplayAtMillis = Long.MIN_VALUE;
            state.stream = null;
        }
        if (state.nonce == null) {
            state.nonce = hello.clientNonce();
        } else if (!state.nonce.equals(hello.clientNonce())) {
            return false;
        }
        registerPlayer(player);
        if (state.controlHelloBytes != null && MessageDigest.isEqual(state.controlHelloBytes, exactRequest)) {
            if (state.controlHelloResponse != null) {
                resendControlHelloAck(player, state);
            } else if (state.stream == null) {
                startStream(player, state, 0, hello.helloId());
            }
            return true;
        }
        if (state.stream != null || state.controlHelloBytes != null) {
            return false;
        }
        state.controlHelloBytes = exactRequest.clone();
        return startStream(player, state, 0, hello.helloId());
    }

    private boolean startStream(Player player, PlayerState state, int requestKind, byte[] requestId) {
        if (state.seenStreams.size() >= MAX_STREAMS_PER_CONNECTION) {
            return false;
        }
        String streamId = newStreamId(state.seenStreams);
        if (streamId == null) {
            return false;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.END_EVENT, state.sessionUuid, requestKind, requestId,
                heartbeatFeatures(state)));
        SignedBytes signed = signEnvelope(player, state, streamId, 1L, clock.getAsLong(), body);
        if (signed == null) {
            return false;
        }
        state.seenStreams.add(streamId);
        StreamState stream = new StreamState(streamId);
        stream.serverCounter = 1L;
        stream.helloAckBytes = signed.envelopeBytes();
        stream.helloAckExpiresAtMillis = signed.expiresAtMillis();
        state.stream = stream;
        stream.helloAckRequestKind = requestKind;
        stream.helloAckRequestId = requestId.clone();
        if (requestKind == 0) {
            state.controlHelloResponse = signed.envelopeBytes();
            state.lastControlHelloAckReplayAtMillis = signed.issuedAtMillis();
        } else {
            state.lastResponseBytes = signed.envelopeBytes();
        }
        return sendSignedEnvelope(player, signed.envelopeBytes());
    }

    private boolean handleCountedRequest(Player player, PlayerState state,
            BridgeRequestCodec.Request request, byte[] exactRequest, boolean admitted) {
        long requestCounter = requestCounter(request);
        if (requestCounter <= 0) {
            return false;
        }
        if (requestCounter == state.lastRequestCounter && state.lastRequestBytes != null
                && MessageDigest.isEqual(state.lastRequestBytes, exactRequest)) {
            if (!admitted) {
                return false;
            }
            if (state.lastResponseBytes != null) {
                boolean renewed = request instanceof BridgeRequestCodec.StreamReopenRequest reopen
                        && renewExpiredReopenHelloAck(player, state, reopen, exactRequest);
                if (!renewed) {
                    sendExactResponse(player, state, state.lastResponseBytes);
                }
            } else if (request instanceof BridgeRequestCodec.SenderReady ready
                    && state.stream != null && state.stream.readyAckBytes == null) {
                state.lastRequestWasDenied = false;
                acceptSenderReady(player, state, ready, exactRequest);
            } else if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen) {
                boolean retryDeniedRequest = state.lastRequestWasDenied;
                state.lastRequestWasDenied = false;
                if (!acceptStreamReopen(player, state, reopen) && retryDeniedRequest) {
                    state.lastRequestWasDenied = true;
                }
            } else if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest ability
                    && state.lastRequestWasDenied) {
                state.lastRequestWasDenied = false;
                return acceptAbility(player, state, ability);
            }
            return true;
        }
        if (state.requestCounter == Long.MAX_VALUE || requestCounter != state.requestCounter + 1L) {
            return false;
        }
        state.requestCounter = requestCounter;
        state.lastRequestCounter = requestCounter;
        state.lastRequestBytes = exactRequest.clone();
        state.lastResponseBytes = null;
        state.lastRequestWasDenied = !admitted;

        if (!admitted) {
            return false;
        }

        if (request instanceof BridgeRequestCodec.SenderReady ready) {
            return acceptSenderReady(player, state, ready, exactRequest);
        }
        if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen) {
            return acceptStreamReopen(player, state, reopen);
        }
        if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest ability) {
            return acceptAbility(player, state, ability);
        }
        return false;
    }

    private boolean acceptSenderReady(Player player, PlayerState state,
            BridgeRequestCodec.SenderReady ready, byte[] exactRequest) {
        StreamState stream = state.stream;
        if (!matchesCurrentBinding(player, state, ready.clientModSessionUuid(), ready.clientNonce())
                || stream == null || !stream.streamId.equals(ready.streamId())
                || stream.readyAckBytes != null || stream.serverCounter != 1L
                || clock.getAsLong() >= stream.helloAckExpiresAtMillis
                || ready.requestCounter() != state.requestCounter
                || !MessageDigest.isEqual(sha256(stream.helloAckBytes), ready.ackEnvelopeSha256())) {
            return false;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyAck(
                BridgeRequestCodec.Sender.END_EVENT, state.sessionUuid, ready.requestId(), ready.requestCounter()));
        SignedBytes signed = signEnvelope(player, state, stream.streamId, 2L, clock.getAsLong(), body);
        if (signed == null) {
            return false;
        }
        stream.serverCounter = 2L;
        stream.readyRequestId = ready.requestId();
        stream.readyRequestCounter = ready.requestCounter();
        stream.readyAckBytes = signed.envelopeBytes();
        stream.readyAckDigest = sha256(signed.envelopeBytes());
        stream.readyAckExpiresAtMillis = signed.expiresAtMillis();
        stream.readyConfirmed = false;
        stream.boundContext = null;
        state.lastResponseBytes = signed.envelopeBytes();
        return sendSignedEnvelope(player, signed.envelopeBytes());
    }

    private boolean handleReadyConfirm(Player player, PlayerState state, BridgeRequestCodec.SenderReadyConfirm confirm,
            byte[] exactRequest) {
        StreamState stream = state.stream;
        if (confirm.sender() != BridgeRequestCodec.Sender.END_EVENT
                || !matchesCurrentBinding(player, state, confirm.clientModSessionUuid(), confirm.clientNonce())
                || stream == null || !stream.streamId.equals(confirm.streamId())
                || stream.readyAckBytes == null
                || !MessageDigest.isEqual(stream.readyRequestId, confirm.readyRequestId())
                || confirm.readyRequestCounter() != stream.readyRequestCounter
                || !MessageDigest.isEqual(stream.readyAckDigest, confirm.ackEnvelopeSha256())) {
            return false;
        }
        if (stream.readyConfirmRequestBytes != null) {
            if (!MessageDigest.isEqual(stream.readyConfirmRequestBytes, exactRequest)) {
                return false;
            }
            if (stream.readyConfirmAckBytes == null || clock.getAsLong() >= stream.readyConfirmAckExpiresAtMillis) {
                retireStream(state);
                return false;
            }
            if (!sendSignedEnvelope(player, stream.readyConfirmAckBytes)) {
                return false;
            }
            markReadyConfirmed(player, state, stream);
            return stream.readyConfirmed;
        }
        if (clock.getAsLong() >= stream.readyAckExpiresAtMillis) {
            retireStream(state);
            return false;
        }
        if (!isCurrentBinding(player, state)) {
            return false;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                BridgeRequestCodec.Sender.END_EVENT, state.sessionUuid, stream.readyRequestId,
                stream.readyRequestCounter, sha256(exactRequest)));
        SignedBytes signed = signEnvelope(player, state, stream.streamId, 3L, clock.getAsLong(), body);
        if (signed == null) {
            retireStream(state);
            return false;
        }
        stream.serverCounter = 3L;
        stream.readyConfirmRequestBytes = exactRequest.clone();
        stream.readyConfirmAckBytes = signed.envelopeBytes();
        stream.readyConfirmAckExpiresAtMillis = signed.expiresAtMillis();
        if (!sendSignedEnvelope(player, signed.envelopeBytes())) {
            return false;
        }
        markReadyConfirmed(player, state, stream);
        return stream.readyConfirmed;
    }

    private void markReadyConfirmed(Player player, PlayerState state, StreamState stream) {
        if (state.stream != stream || !isCurrentBinding(player, state)) {
            return;
        }
        boolean wasReady = stream.readyConfirmed;
        stream.readyConfirmed = true;
        PlayerState current = currentReadyState(player);
        if (current == null || current != state) {
            stream.readyConfirmed = false;
            return;
        }
        EventContext context = currentEventContext(player.getUniqueId());
        if (context != null) {
            ensureContextBound(player, state, context);
        }
        if (!wasReady) {
            stream.nextHeartbeatAtMillis = clock.getAsLong() + HEARTBEAT_INTERVAL_MILLIS;
            try {
                readyListener.accept(player.getUniqueId());
            } catch (RuntimeException failure) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING,
                            "End Event V4 ready callback failed for " + player.getUniqueId(), failure);
                }
            }
        }
    }

    private boolean acceptStreamReopen(Player player, PlayerState state, BridgeRequestCodec.StreamReopenRequest reopen) {
        if (!matchesCurrentBinding(player, state, reopen.clientModSessionUuid(), reopen.clientNonce())) {
            return false;
        }
        String currentStream = state.stream == null ? null : state.stream.streamId;
        Optional<String> expected = reopen.expectedStreamId();
        boolean unacceptedHelloAck = state.stream != null && state.stream.serverCounter == 1L
                && state.stream.readyAckBytes == null && !state.stream.readyConfirmed;
        boolean expectedMatches = expected.isEmpty()
                ? currentStream == null || unacceptedHelloAck
                : expected.get().equals(currentStream)
                        || currentStream == null && Objects.equals(expected.get(), state.mostRecentlyRetiredStream);
        if (!expectedMatches) {
            return false;
        }
        if (!consumeReopenSlot(state)) {
            return false;
        }
        if (currentStream != null) {
            retireStream(state);
        }
        state.controlHelloBytes = null;
        state.controlHelloResponse = null;
        state.lastControlHelloAckReplayAtMillis = Long.MIN_VALUE;
        return startStream(player, state, 1, reopen.requestId());
    }

    /** Renews only the exact counted reopen's expired, still-unaccepted HELLO_ACK. */
    private boolean renewExpiredReopenHelloAck(Player player, PlayerState state,
            BridgeRequestCodec.StreamReopenRequest reopen, byte[] exactRequest) {
        StreamState stream = state.stream;
        if (!matchesCurrentBinding(player, state, reopen.clientModSessionUuid(), reopen.clientNonce())
                || stream == null || stream.serverCounter != 1L || stream.readyAckBytes != null
                || stream.readyConfirmed || stream.helloAckBytes == null || state.lastResponseBytes == null
                || !MessageDigest.isEqual(state.lastResponseBytes, stream.helloAckBytes)
                || stream.helloAckRequestKind != 1 || stream.helloAckRequestId == null
                || !MessageDigest.isEqual(stream.helloAckRequestId, reopen.requestId())
                || state.lastRequestBytes == null || !MessageDigest.isEqual(state.lastRequestBytes, exactRequest)
                || clock.getAsLong() < stream.helloAckExpiresAtMillis) {
            return false;
        }
        byte[] body = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.END_EVENT, state.sessionUuid, 1, reopen.requestId(),
                heartbeatFeatures(state)));
        SignedBytes refreshed = signEnvelope(player, state, stream.streamId, 1L, clock.getAsLong(), body);
        if (refreshed != null) {
            stream.helloAckBytes = refreshed.envelopeBytes();
            stream.helloAckExpiresAtMillis = refreshed.expiresAtMillis();
            state.lastResponseBytes = refreshed.envelopeBytes();
            sendSignedEnvelope(player, refreshed.envelopeBytes());
        }
        // A transient signing failure keeps this same counted request and stream retryable.
        return true;
    }

    private boolean consumeReopenSlot(PlayerState state) {
        long now = clock.getAsLong();
        long last = state.lastReopenAtMillis;
        if (now <= 0L || (last != Long.MIN_VALUE
                && (now < last || now - last < REOPEN_INTERVAL_MILLIS))) {
            return false;
        }
        state.lastReopenAtMillis = now;
        return true;
    }

    private boolean resendControlHelloAck(Player player, PlayerState state) {
        long now = clock.getAsLong();
        long last = state.lastControlHelloAckReplayAtMillis;
        if (now <= 0L || (last != Long.MIN_VALUE
                && (now < last || now - last < CONTROL_HELLO_ACK_REPLAY_INTERVAL_MILLIS))) {
            return false;
        }
        if (!sendExactResponse(player, state, state.controlHelloResponse)) {
            return false;
        }
        state.lastControlHelloAckReplayAtMillis = now;
        return true;
    }

    private boolean acceptAbility(Player player, PlayerState state, BridgeRequestCodec.PrisonerAbilityRequest ability) {
        if (!matchesCurrentBinding(player, state, ability.clientModSessionUuid(), ability.clientNonce())) {
            return false;
        }
        StreamState stream = state.stream;
        if (stream == null || !stream.streamId.equals(ability.streamId()) || !stream.readyConfirmed) {
            return false;
        }
        EventContext context = currentEventContext(player.getUniqueId());
        if (context == null || !context.eventUuid().equals(ability.eventUuid())
                || context.generation() != ability.generation()
                || !ensureContextBound(player, state, context)) {
            return false;
        }
        if (currentReadyState(player) != state || state.stream != stream || !stream.readyConfirmed) {
            return false;
        }
        if (ability.targetUuid().isEmpty()) {
            return false;
        }
        abilityHandler.accept(player, new PrisonerAbilityRequest(
                ability.eventUuid(), ability.generation(), ability.abilityId(), ability.targetUuid()));
        return true;
    }

    private boolean ensureContextBound(Player player, PlayerState state, EventContext context) {
        if (!validContext(context) || state.stream == null || !state.stream.readyConfirmed) {
            return false;
        }
        if (context.equals(state.stream.boundContext)) {
            return true;
        }
        return sendContextBind(player, state, context);
    }

    private boolean sendContextBind(Player player, PlayerState state, EventContext context) {
        if (!validContext(context) || state.stream == null || !state.stream.readyConfirmed) {
            return false;
        }
        long timestamp = clock.getAsLong();
        if (timestamp <= 0) {
            return false;
        }
        String bounds = context.dimension() + "|" + context.minX() + "," + context.minY() + "," + context.minZ()
                + "|" + context.maxX() + "," + context.maxY() + "," + context.maxZ();
        EndEventBridgeBodyCodec.Body body = new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.CONTEXT_BIND, context.generation(), timestamp,
                context.eventUuid(), "", "", "", 0L, 0.0F, 0L, 0L,
                bounds, "", "", "", "");
        if (!sendApplication(player, state, body)) {
            return false;
        }
        state.stream.boundContext = context;
        return true;
    }

    private boolean sendApplication(Player player, PlayerState state, EndEventBridgeBodyCodec.Body body) {
        if (player == null || !player.isOnline() || state.stream == null || !state.stream.readyConfirmed
                || currentReadyState(player) != state || body == null) {
            return false;
        }
        final byte[] encodedBody;
        try {
            encodedBody = EndEventBridgeBodyCodec.encode(body, player.getUniqueId());
            EndEventBridgeBodyCodec.validateTimestampMatches(body, body.bodyTimestampMillis());
        } catch (IllegalArgumentException invalidBody) {
            return false;
        }
        StreamState stream = state.stream;
        if (stream.serverCounter == Long.MAX_VALUE) {
            retireStream(state);
            return false;
        }
        SignedBytes signed = signAndSend(player, state, stream.streamId, stream.serverCounter + 1L,
                body.bodyTimestampMillis(), encodedBody);
        if (signed == null) {
            retireStream(state);
            return false;
        }
        stream.serverCounter++;
        return true;
    }

    private SignedBytes signAndSend(Player player, PlayerState state,
            String streamId, long counter, byte[] exactBody) {
        return signAndSend(player, state, streamId, counter, clock.getAsLong(), exactBody);
    }

    private SignedBytes signAndSend(Player player, PlayerState state,
            String streamId, long counter, long issuedAt, byte[] exactBody) {
        SignedBytes signed = signEnvelope(player, state, streamId, counter, issuedAt, exactBody);
        return signed != null && sendSignedEnvelope(player, signed.envelopeBytes()) ? signed : null;
    }

    private SignedBytes signEnvelope(Player player, PlayerState state,
            String streamId, long counter, long issuedAt, byte[] exactBody) {
        if (!isCurrentBinding(player, state) || state.nonce == null || streamId == null || exactBody == null
                || counter <= 0 || issuedAt <= 0) {
            return null;
        }
        final long expiresAt;
        try {
            expiresAt = Math.addExact(issuedAt, ENVELOPE_VALIDITY_MILLIS);
        } catch (ArithmeticException overflow) {
            return null;
        }
        Optional<byte[]> encoded = signer.sign(state.nonce, state.playerUuid, streamId,
                counter, issuedAt, expiresAt, exactBody.clone());
        if (encoded.isEmpty()) {
            return null;
        }
        final BridgeEnvelope envelope;
        try {
            envelope = BridgeEnvelopeCodec.decode(encoded.get());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        BridgeEnvelope.Metadata metadata = envelope.metadata();
        if (metadata.protocolVersion() != BridgeSigningPolicy.PROTOCOL_VERSION
                || !BridgeSigningPolicy.PINNED_KEY_ID.equals(metadata.keyId())
                || !state.nonce.equals(metadata.clientNonce())
                || !state.playerUuid.equals(metadata.recipientUuid())
                || !streamId.equals(metadata.streamId()) || metadata.counter() != counter
                || metadata.issuedAtMillis() != issuedAt || metadata.expiresAtMillis() != expiresAt
                || !MessageDigest.isEqual(exactBody, envelope.bodyBytes())
                || envelope.signature().length != BridgeSigningPolicy.ED25519_SIGNATURE_BYTES
                || clock.getAsLong() >= expiresAt || !isCurrentBinding(player, state)) {
            return null;
        }
        return new SignedBytes(encoded.get().clone(), issuedAt, expiresAt);
    }

    private boolean sendSignedEnvelope(Player player, byte[] envelopeBytes) {
        try {
            transport.send(player.getUniqueId(), envelopeBytes.clone());
            return true;
        } catch (RuntimeException transportFailure) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "End Event v4 packet send failed", transportFailure);
            }
            return false;
        }
    }

    private boolean sendExactResponse(Player player, PlayerState state, byte[] response) {
        if (response == null || !isCurrentBinding(player, state)) {
            return false;
        }
        try {
            BridgeEnvelope envelope = BridgeEnvelopeCodec.decode(response);
            BridgeEnvelope.Metadata metadata = envelope.metadata();
            if (metadata.protocolVersion() != BridgeSigningPolicy.PROTOCOL_VERSION
                    || !BridgeSigningPolicy.PINNED_KEY_ID.equals(metadata.keyId())
                    || !state.nonce.equals(metadata.clientNonce())
                    || !state.playerUuid.equals(envelope.metadata().recipientUuid())
                    || state.stream == null || !state.stream.streamId.equals(metadata.streamId())
                    || metadata.counter() > state.stream.serverCounter
                    || clock.getAsLong() >= metadata.expiresAtMillis()
                    || !isCurrentBinding(player, state)) {
                return false;
            }
            transport.send(player.getUniqueId(), response.clone());
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private PlayerState currentReadyState(Player player) {
        if (player == null || !player.isOnline() || closed) {
            return null;
        }
        PlayerState state = players.get(player.getUniqueId());
        if (!isCurrentBinding(player, state) || state.stream == null || !state.stream.readyConfirmed) {
            return null;
        }
        return state;
    }

    private boolean matchesCurrentBinding(Player player, PlayerState state, UUID sessionUuid, String nonce) {
        return state != null && Objects.equals(state.sessionUuid, sessionUuid) && Objects.equals(state.nonce, nonce)
                && isCurrentBinding(player, state);
    }

    private boolean isCurrentBinding(Player player, PlayerState state) {
        if (closed || player == null || !player.isOnline() || state == null
                || !player.getUniqueId().equals(state.playerUuid)) {
            return false;
        }
        ClientModSessionRegistry.VerifiedClientSession current = registry.current(state.playerUuid).orElse(null);
        if (current == null || !current.playerUuid().equals(state.playerUuid)
                || !current.sessionUuid().equals(state.sessionUuid)
                || !current.connectionEpoch().equals(state.connectionEpoch)
                || !current.clientVersion().equals(state.sessionVersion)) {
            if (current == null || !current.connectionEpoch().equals(state.connectionEpoch)) {
                players.remove(state.playerUuid);
            } else {
                retireStream(state);
                state.sessionUuid = current.sessionUuid();
                state.sessionVersion = current.clientVersion();
                state.requestCounter = 0L;
                state.lastRequestCounter = 0L;
                state.lastRequestBytes = null;
                state.lastResponseBytes = null;
                state.lastRequestWasDenied = false;
                state.controlHelloBytes = null;
                state.controlHelloResponse = null;
                state.lastControlHelloAckReplayAtMillis = Long.MIN_VALUE;
            }
            return false;
        }
        return true;
    }

    private EventContext currentEventContext(UUID playerUuid) {
        try {
            return eventContext.apply(playerUuid);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean validContext(EventContext context) {
        return context != null && context.eventUuid() != null
                && context.generation() > 0 && context.generation() < Long.MAX_VALUE
                && Set.of("overworld", "the_nether", "the_end").contains(context.dimension())
                && context.minX() <= context.maxX() && context.minY() <= context.maxY()
                && context.minZ() <= context.maxZ();
    }

    private boolean retireStream(PlayerState state) {
        if (state.stream == null) {
            return false;
        }
        String retired = state.stream.streamId;
        state.stream = null;
        if (state.seenStreams.contains(retired)) {
            state.mostRecentlyRetiredStream = retired;
        }
        return true;
    }

    private void retireForFreshChallenge(UUID playerUuid) {
        PlayerState state = players.get(playerUuid);
        if (state != null) {
            retireStream(state);
            state.sessionUuid = null;
            state.sessionVersion = null;
            state.requestCounter = 0L;
            state.lastRequestCounter = 0L;
            state.lastRequestBytes = null;
            state.lastResponseBytes = null;
            state.lastRequestWasDenied = false;
            state.controlHelloBytes = null;
            state.controlHelloResponse = null;
            state.lastControlHelloAckReplayAtMillis = Long.MIN_VALUE;
        }
    }

    private synchronized void onInvalidated(UUID playerUuid, ClientModSessionRegistry.InvalidationReason reason) {
        if (playerUuid == null) {
            return;
        }
        if (reason == ClientModSessionRegistry.InvalidationReason.QUIT
                || reason == ClientModSessionRegistry.InvalidationReason.RECONNECT) {
            players.remove(playerUuid);
            playerHandles.remove(playerUuid);
            inboundRateLimiter.remove(playerUuid);
            return;
        }
        PlayerState state = players.get(playerUuid);
        if (state != null) {
            retireForFreshChallenge(playerUuid);
        }
    }

    private static long requestCounter(BridgeRequestCodec.Request request) {
        if (request instanceof BridgeRequestCodec.SenderReady ready) return ready.requestCounter();
        if (request instanceof BridgeRequestCodec.StreamReopenRequest reopen) return reopen.requestCounter();
        if (request instanceof BridgeRequestCodec.PrisonerAbilityRequest ability) return ability.requestCounter();
        return 0L;
    }

    private Player playerFor(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        Player cached = playerHandles.get(playerUuid);
        return cached != null && cached.isOnline() ? cached : Bukkit.getPlayer(playerUuid);
    }

    private static String newStreamId(Set<String> used) {
        byte[] random = new byte[BridgeSigningPolicy.STREAM_ID_BYTES];
        for (int attempt = 0; attempt < 8; attempt++) {
            RANDOM.nextBytes(random);
            String candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    public interface Transport {
        void send(UUID recipient, byte[] exactEnvelopeBytes);
    }

    private record SignedBytes(byte[] envelopeBytes, long issuedAtMillis, long expiresAtMillis) {
    }

    private static final class PlayerState {
        final UUID playerUuid;
        final UUID connectionEpoch;
        final LinkedHashSet<String> seenStreams = new LinkedHashSet<>();
        UUID sessionUuid;
        String sessionVersion;
        String nonce;
        long requestCounter;
        long lastRequestCounter;
        long lastReopenAtMillis = Long.MIN_VALUE;
        long lastControlHelloAckReplayAtMillis = Long.MIN_VALUE;
        byte[] lastRequestBytes;
        byte[] lastResponseBytes;
        boolean lastRequestWasDenied;
        byte[] controlHelloBytes;
        byte[] controlHelloResponse;
        String mostRecentlyRetiredStream;
        StreamState stream;

        PlayerState(UUID playerUuid, ClientModSessionRegistry.VerifiedClientSession session) {
            this.playerUuid = playerUuid;
            this.connectionEpoch = session.connectionEpoch();
            this.sessionUuid = session.sessionUuid();
            this.sessionVersion = session.clientVersion();
        }
    }

    private static final class StreamState {
        long nextHeartbeatAtMillis;
        final String streamId;
        int helloAckRequestKind;
        byte[] helloAckRequestId;
        long serverCounter;
        byte[] helloAckBytes;
        long helloAckExpiresAtMillis;
        byte[] readyRequestId;
        long readyRequestCounter;
        byte[] readyAckBytes;
        byte[] readyAckDigest;
        long readyAckExpiresAtMillis;
        byte[] readyConfirmRequestBytes;
        byte[] readyConfirmAckBytes;
        long readyConfirmAckExpiresAtMillis;
        boolean readyConfirmed;
        EventContext boundContext;

        StreamState(String streamId) {
            this.streamId = streamId;
        }
    }
}
