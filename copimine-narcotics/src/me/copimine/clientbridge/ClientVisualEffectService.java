package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.protocol.NarcoticsBridgeBodyCodec;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Sends only signed v4 Narcotics visuals and correlates legacy-channel delivery reports. */
public final class ClientVisualEffectService {
    private static final long RETRY_INTERVAL_MILLIS = 2_000L;
    private static final long CLEANUP_RETRY_DELAY_MILLIS = 30_000L;
    private static final long RUNNING_TIMEOUT_GRACE_MILLIS = 30_000L;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final CopiMineNarcotics plugin;
    private final ClientCapabilityService capabilities;
    private final NarcoticsBridgeV4Sender bridgeV4Sender;
    private final ClientVisualCommandCorrelation correlations = new ClientVisualCommandCorrelation();
    private final LongSupplier clock;
    private final Function<UUID, Player> playerLookup;
    private final Map<CommandKey, ClientVisualCommand> pendingAcks = new ConcurrentHashMap<>();
    private final Map<CommandKey, ClientVisualCommand> runningCommands = new ConcurrentHashMap<>();
    private final Map<UUID, ClientVisualCommand> desiredVisuals = new ConcurrentHashMap<>();
    private final Map<UUID, ClientVisualCommand> cleanupIntents = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cleanupRetryAtMillis = new ConcurrentHashMap<>();
    private final Map<UUID, CommandKey> activeCommandByPlayer = new ConcurrentHashMap<>();
    private final Map<SessionKey, Long> lastSequenceBySession = new HashMap<>();
    private final Map<UUID, String> activeStreamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastAckByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastFinishedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastErrorByPlayer = new ConcurrentHashMap<>();
    private int tickerTaskId = -1;

    public ClientVisualEffectService(CopiMineNarcotics plugin, ClientCapabilityService capabilities,
                                    NarcoticsBridgeV4Sender bridgeV4Sender) {
        this(plugin, capabilities, bridgeV4Sender, System::currentTimeMillis, Bukkit::getPlayer, true);
    }

    ClientVisualEffectService(CopiMineNarcotics plugin, ClientCapabilityService capabilities,
                              NarcoticsBridgeV4Sender bridgeV4Sender, LongSupplier clock,
                              Function<UUID, Player> playerLookup) {
        this(plugin, capabilities, bridgeV4Sender, clock, playerLookup, false);
    }

    private ClientVisualEffectService(CopiMineNarcotics plugin, ClientCapabilityService capabilities,
                                     NarcoticsBridgeV4Sender bridgeV4Sender, LongSupplier clock,
                                     Function<UUID, Player> playerLookup, boolean scheduleTicker) {
        this.plugin = plugin;
        this.capabilities = capabilities;
        this.bridgeV4Sender = bridgeV4Sender;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.tickerTaskId = scheduleTicker
                ? Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L).getTaskId()
                : -1;
    }

    public boolean canUse(Player player, String effectId) {
        return player != null && bridgeV4Sender.isReady(player)
                && capabilities.supportsEffect(player, effectId);
    }

    public long sendVisualStart(Player player, String effectId, String shaderpack, int seconds,
            float intensity, int fadeInMillis, int fadeOutMillis, String source,
            ClientVisualCommand.FallbackHandler fallbackHandler,
            ClientVisualCommand.FinishHandler finishHandler) {
        return sendVisualStartInternal(player, effectId, shaderpack, seconds, intensity,
                fadeInMillis, fadeOutMillis, source, fallbackHandler, finishHandler, true);
    }

    public long sendVisualRefresh(Player player, String effectId, String shaderpack, int seconds,
            float intensity, int fadeInMillis, int fadeOutMillis, String source,
            ClientVisualCommand.FallbackHandler fallbackHandler,
            ClientVisualCommand.FinishHandler finishHandler) {
        return sendVisualStartInternal(player, effectId, shaderpack, seconds, intensity,
                fadeInMillis, fadeOutMillis, source, fallbackHandler, finishHandler, false);
    }

    private synchronized long sendVisualStartInternal(Player player, String effectId, String shaderpack,
            int seconds, float intensity, int fadeInMillis, int fadeOutMillis, String source,
            ClientVisualCommand.FallbackHandler fallbackHandler,
            ClientVisualCommand.FinishHandler finishHandler, boolean replaceAll) {
        if (player == null || !player.isOnline() || !canUse(player, effectId)) {
            return -1L;
        }
        ClientCapabilityState capability = capabilities.state(player);
        UUID sessionUuid = canonicalSession(capability.sessionId());
        if (sessionUuid == null) {
            return -1L;
        }
        ClientVisualCommand prior = desiredVisuals.get(player.getUniqueId());
        UUID instanceId = !replaceAll && prior != null && prior.sessionId().equals(sessionUuid.toString())
                ? prior.body().visualInstanceId() : UUID.randomUUID();
        long seq = nextSequence(player.getUniqueId(), sessionUuid);
        if (seq <= 0L || !correlations.canRegister(player.getUniqueId(), sessionUuid, seq, clock.getAsLong())) {
            return -1L;
        }
        String normalizedEffect = effectId.toUpperCase(Locale.ROOT);
        String normalizedSource = normalizeSource(source);
        int durationSeconds = Math.max(1, Math.min(600, seconds));
        long now = positiveNow();
        if (now <= 0L) {
            return -1L;
        }
        boolean refresh = !replaceAll && prior != null && prior.sessionId().equals(sessionUuid.toString());
        NarcoticsBridgeBodyCodec.Body requested = new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_START, seq, now, sessionUuid,
                normalizedEffect, normalizeShaderpack(shaderpack), durationSeconds * 1_000L,
                ClientBridgePayloads.clampIntensity(intensity), clampFade(fadeInMillis), clampFade(fadeOutMillis),
                "CLIENT_MOD", refresh ? "PRESERVE" : "REPLACE_ALL_FULLSCREEN", normalizedSource,
                "", "", instanceId, ZERO_UUID, 0L);

        Optional<NarcoticsBridgeBodyCodec.Body> sent = bridgeV4Sender.sendVisualStartTracked(player, requested, true);
        if (sent.isEmpty()) {
            return -1L;
        }
        String streamId = bridgeV4Sender.activeStreamId(player).orElse(null);
        NarcoticsBridgeBodyCodec.Body body = sent.get();
        if (streamId == null || !correlations.register(player.getUniqueId(), sessionUuid, streamId,
                body, now)) {
            bridgeV4Sender.retire(player.getUniqueId());
            return -1L;
        }
        activeStreamByPlayer.put(player.getUniqueId(), streamId);
        retirePriorCommand(player.getUniqueId(), prior);
        ClientVisualCommand previousCleanup = cleanupIntents.remove(player.getUniqueId());
        if (previousCleanup != null) {
            retirePriorCommand(player.getUniqueId(), previousCleanup);
        }
        cleanupRetryAtMillis.remove(player.getUniqueId());
        ClientVisualCommand command = new ClientVisualCommand(seq, player.getUniqueId(), player.getName(),
                sessionUuid.toString(), normalizedEffect, normalizeShaderpack(shaderpack), durationSeconds,
                ClientBridgePayloads.clampIntensity(intensity), clampFade(fadeInMillis), clampFade(fadeOutMillis),
                normalizedSource, body, streamId, now, fallbackHandler, finishHandler);
        command.markSent(now, RETRY_INTERVAL_MILLIS);
        CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, seq);
        pendingAcks.put(key, command);
        desiredVisuals.put(player.getUniqueId(), command);
        activeCommandByPlayer.put(player.getUniqueId(), key);
        lastAckByPlayer.put(player.getUniqueId(), "waiting:" + normalizedEffect + "#" + seq);
        lastErrorByPlayer.remove(player.getUniqueId());
        return seq;
    }

    public synchronized void sendVisualStop(Player player, String effectId, String reason) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ClientVisualCommand active = desiredVisuals.get(player.getUniqueId());
        if (active == null) {
            ClientVisualCommand cleanup = cleanupIntents.get(player.getUniqueId());
            if (cleanup != null && cleanup.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_STOP
                    && cleanup.effectId().equalsIgnoreCase(effectId)) {
                UUID sessionUuid = canonicalSession(cleanup.sessionId());
                if (sessionUuid != null) {
                    CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, cleanup.seq());
                    if (!pendingAcks.containsKey(key)) {
                        long now = positiveNow();
                        if (now > 0L) {
                            cleanupRetryAtMillis.put(player.getUniqueId(), now);
                            retryCleanupIntents(now);
                        }
                    }
                }
            }
            return;
        }
        if (!active.effectId().equalsIgnoreCase(effectId)) {
            return;
        }
        if (!bridgeV4Sender.isReady(player)) {
            retirePriorCommand(player.getUniqueId(), active);
            desiredVisuals.remove(player.getUniqueId(), active);
            return;
        }
        UUID sessionUuid = canonicalSession(active.sessionId());
        long seq = nextSequence(player.getUniqueId(), sessionUuid);
        long now = positiveNow();
        if (seq <= 0 || now <= 0 || !correlations.canRegister(player.getUniqueId(), sessionUuid, seq, now)) {
            desiredVisuals.remove(player.getUniqueId(), active);
            return;
        }
        NarcoticsBridgeBodyCodec.Body requested = new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_STOP, seq, now, sessionUuid, active.effectId(), "",
                0L, 0.0F, 0L, 0L, "CLIENT_MOD", "", "", normalizeReason(reason), "",
                ZERO_UUID, active.body().visualInstanceId(), 0L);
        Optional<NarcoticsBridgeBodyCodec.Body> sent = bridgeV4Sender.stopVisualTracked(player, requested);
        if (sent.isEmpty()) {
            desiredVisuals.remove(player.getUniqueId(), active);
            return;
        }
        String streamId = bridgeV4Sender.activeStreamId(player).orElse(null);
        NarcoticsBridgeBodyCodec.Body body = sent.get();
        if (streamId == null || !correlations.register(player.getUniqueId(), sessionUuid, streamId, body, now)) {
            bridgeV4Sender.retire(player.getUniqueId());
            retirePriorCommand(player.getUniqueId(), active);
            desiredVisuals.remove(player.getUniqueId(), active);
            return;
        }
        retirePriorCommand(player.getUniqueId(), active);
        desiredVisuals.remove(player.getUniqueId(), active);
        ClientVisualCommand stop = new ClientVisualCommand(seq, player.getUniqueId(), player.getName(),
                sessionUuid.toString(), active.effectId(), "", 0, 0.0F, 0, 0,
                "SYSTEM", body, streamId, now, null, active.finishHandler());
        stop.markSent(now, RETRY_INTERVAL_MILLIS);
        CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, seq);
        pendingAcks.put(key, stop);
        activeCommandByPlayer.put(player.getUniqueId(), key);
        cleanupIntents.put(player.getUniqueId(), stop);
        cleanupRetryAtMillis.put(player.getUniqueId(), now + CLEANUP_RETRY_DELAY_MILLIS);
    }

    public void clearVisuals(Player player) {
        clearVisuals(player, "server_clear");
    }

    public synchronized void clearVisuals(Player player, String reason) {
        if (player == null) {
            return;
        }
        ClientCapabilityState capability = capabilities.state(player);
        UUID sessionUuid = capability == null ? null : canonicalSession(capability.sessionId());
        ClientVisualCommand active = desiredVisuals.remove(player.getUniqueId());
        if (sessionUuid == null || !player.isOnline() || !bridgeV4Sender.isReady(player)) {
            forgetCommands(player.getUniqueId());
            return;
        }
        long seq = nextSequence(player.getUniqueId(), sessionUuid);
        long now = positiveNow();
        if (seq <= 0 || now <= 0 || !correlations.canRegister(player.getUniqueId(), sessionUuid, seq, now)) {
            forgetCommands(player.getUniqueId());
            return;
        }
        NarcoticsBridgeBodyCodec.Body requested = new NarcoticsBridgeBodyCodec.Body(
                NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL, seq, now, sessionUuid,
                "", "", 0L, 0.0F, 0L, 0L, "CLIENT_MOD", "REPLACE_ALL_FULLSCREEN", "SYSTEM",
                normalizeReason(reason), "", ZERO_UUID, ZERO_UUID, 1L);
        Optional<NarcoticsBridgeBodyCodec.Body> sent = bridgeV4Sender.clearVisualsTracked(player, requested, true);
        if (sent.isEmpty()) {
            forgetCommands(player.getUniqueId());
            return;
        }
        String streamId = bridgeV4Sender.activeStreamId(player).orElse(null);
        NarcoticsBridgeBodyCodec.Body body = sent.get();
        if (streamId == null || !correlations.register(player.getUniqueId(), sessionUuid, streamId, body, now)) {
            bridgeV4Sender.retire(player.getUniqueId());
            forgetCommands(player.getUniqueId());
            return;
        }
        if (active != null) {
            retirePriorCommand(player.getUniqueId(), active);
        }
        ClientVisualCommand previousCleanup = cleanupIntents.remove(player.getUniqueId());
        if (previousCleanup != null) {
            retirePriorCommand(player.getUniqueId(), previousCleanup);
        }
        ClientVisualCommand clear = new ClientVisualCommand(seq, player.getUniqueId(), player.getName(),
                sessionUuid.toString(), "", "", 0, 0.0F, 0, 0, "SYSTEM",
                body, streamId, now, null, null);
        clear.markSent(now, RETRY_INTERVAL_MILLIS);
        CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, seq);
        pendingAcks.put(key, clear);
        activeCommandByPlayer.put(player.getUniqueId(), key);
        cleanupIntents.put(player.getUniqueId(), clear);
        cleanupRetryAtMillis.put(player.getUniqueId(), now + CLEANUP_RETRY_DELAY_MILLIS);
    }

    public synchronized void shutdown() {
        if (tickerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickerTaskId);
            tickerTaskId = -1;
        }
        pendingAcks.clear();
        runningCommands.clear();
        activeCommandByPlayer.clear();
        desiredVisuals.clear();
        cleanupIntents.clear();
        cleanupRetryAtMillis.clear();
        lastSequenceBySession.clear();
        activeStreamByPlayer.clear();
        lastAckByPlayer.clear();
        lastFinishedByPlayer.clear();
        lastErrorByPlayer.clear();
    }

    public synchronized void handleMessage(Player player, ClientBridgePayloads.Message message) {
        if (player == null || message == null) {
            return;
        }
        ClientCapabilityState capability = capabilities.state(player);
        UUID sessionUuid = capability == null ? null : canonicalSession(capability.sessionId());
        String streamId = bridgeV4Sender.activeStreamId(player).orElse(null);
        if (sessionUuid == null || streamId == null) {
            return;
        }
        Optional<ClientVisualCommandCorrelation.CorrelatedReport> accepted = correlations.accept(
                player.getUniqueId(), sessionUuid, streamId, message, clock.getAsLong());
        if (accepted.isEmpty()) {
            return;
        }
        ClientVisualCommandCorrelation.CorrelatedReport report = accepted.get();
        CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, report.body().commandSeq());
        ClientVisualCommand command = pendingAcks.get(key);
        if (report.kind() == ClientVisualCommandCorrelation.ReportKind.ACK) {
            String status = report.status();
            lastAckByPlayer.put(player.getUniqueId(), status + ":" + commandName(report.body()) + "#" + report.body().commandSeq());
            if (report.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START) {
                if (!pendingAcks.containsKey(key) || command == null || !pendingAcks.remove(key, command)) {
                    return;
                }
                if (ClientVisualAckStatusPolicy.shouldReportNonIrisFallback(status)) {
                    String route = status.substring(ClientBridgePayloads.STATUS_STARTED.length() + 1);
                    lastErrorByPlayer.put(player.getUniqueId(), command.effectId() + ":client-route-" + route.toLowerCase(Locale.ROOT));
                    notifyPlayer(player, ChatColor.YELLOW, "Шейдер для эффекта " + command.effectId()
                            + " запущен через маршрут клиента: " + describeRoute(route) + ".");
                    plugin.getLogger().warning("CopiMineClient visual route fallback for " + player.getName()
                            + ": effect=" + command.effectId() + ", route=" + route);
                } else {
                    lastErrorByPlayer.remove(player.getUniqueId());
                }
                runningCommands.put(key, command);
                activeCommandByPlayer.put(player.getUniqueId(), key);
                return;
            }
            pendingAcks.remove(key);
            runningCommands.remove(key);
            activeCommandByPlayer.remove(player.getUniqueId(), key);
            if (report.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_STOP
                    || report.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL) {
                if (command != null && cleanupIntents.remove(player.getUniqueId(), command)) {
                    cleanupRetryAtMillis.remove(player.getUniqueId());
                }
            }
            lastFinishedByPlayer.put(player.getUniqueId(), status.toLowerCase(Locale.ROOT));
            if (command != null && command.finishHandler() != null
                    && report.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_STOP) {
                command.finishHandler().onFinished(player.getUniqueId(), command.effectId(), command.source(), "stopped");
            }
            return;
        }
        if (command == null) {
            command = runningCommands.get(key);
        }
        if (command == null) {
            return;
        }
        pendingAcks.remove(key);
        runningCommands.remove(key);
        desiredVisuals.remove(player.getUniqueId(), command);
        activeCommandByPlayer.remove(player.getUniqueId(), key);
        if (report.kind() == ClientVisualCommandCorrelation.ReportKind.FINISHED) {
            lastFinishedByPlayer.put(player.getUniqueId(), command.effectId() + ":" + report.reason());
            if (command.finishHandler() != null) {
                command.finishHandler().onFinished(player.getUniqueId(), command.effectId(), command.source(), report.reason());
            }
        } else {
            String reason = report.reason();
            lastErrorByPlayer.put(player.getUniqueId(), command.effectId() + ":" + reason);
            notifyPlayer(player, ChatColor.RED, "Клиентский визуальный эффект " + command.effectId()
                    + " не запустился: " + describeReason(reason) + ".");
            if (command.fallbackHandler() != null) {
                command.fallbackHandler().onFallback(player.getUniqueId(), command.effectId(),
                        command.seconds(), command.intensity(), command.source(), reason);
            }
        }
    }

    public synchronized void onBridgeStateChanged(Player player) {
        if (player == null || !player.isOnline() || !bridgeV4Sender.isReady(player)) {
            return;
        }
        String newStream = bridgeV4Sender.activeStreamId(player).orElse(null);
        if (newStream == null) {
            return;
        }
        String previous = activeStreamByPlayer.put(player.getUniqueId(), newStream);
        if (previous == null || previous.equals(newStream)) {
            return;
        }
        ClientVisualCommand desired = desiredVisuals.get(player.getUniqueId());
        ClientVisualCommand cleanup = cleanupIntents.get(player.getUniqueId());
        UUID sessionUuid = desired != null ? canonicalSession(desired.sessionId())
                : cleanup == null ? null : canonicalSession(cleanup.sessionId());
        if (sessionUuid != null) {
            correlations.retireStream(player.getUniqueId(), sessionUuid, previous);
        }
        removeCommandsForPlayer(player.getUniqueId());
        if (desired == null && cleanup != null && sessionUuid != null) {
            cleanupRetryAtMillis.put(player.getUniqueId(), clock.getAsLong());
            retryCleanupIntents(clock.getAsLong());
            return;
        }
        if (desired == null || sessionUuid == null) {
            return;
        }
        long actualRemaining = desired.createdAtMillis() + desired.body().durationMillis() - clock.getAsLong();
        if (actualRemaining <= 0L) {
            desiredVisuals.remove(player.getUniqueId(), desired);
            return;
        }
        long resentSeq = sendVisualStartInternal(player, desired.effectId(), desired.shaderpack(),
                (int) Math.min(600L, (actualRemaining + 999L) / 1_000L), desired.intensity(),
                desired.fadeInMillis(), desired.fadeOutMillis(), desired.source(),
                desired.fallbackHandler(), desired.finishHandler(), false);
        if (resentSeq <= 0L) {
            retireDesiredAfterResendFailure(player, desired);
        }
    }

    private void retireDesiredAfterResendFailure(Player player, ClientVisualCommand desired) {
        UUID playerUuid = desired.playerUuid();
        if (!desiredVisuals.remove(playerUuid, desired)) {
            return;
        }
        retirePriorCommand(playerUuid, desired);
        String reason = "stream-resend-failed";
        lastErrorByPlayer.put(playerUuid, desired.effectId() + ":" + reason);
        if (player != null && player.isOnline()) {
            notifyPlayer(player, ChatColor.RED, "Клиентский визуальный эффект " + desired.effectId()
                    + " не восстановился после переподключения. Используется серверный маршрут.");
        }
        ClientVisualCommand.FallbackHandler fallbackHandler = desired.fallbackHandler();
        if (fallbackHandler != null) {
            fallbackHandler.onFallback(playerUuid, desired.effectId(), desired.seconds(),
                    desired.intensity(), desired.source(), reason);
        }
    }

    public synchronized String sessionsSummary() {
        return "pending=" + pendingAcks.size() + ", running=" + runningCommands.size()
                + ", correlated=" + correlations.sizeTotal();
    }

    public synchronized String playerSummary(Player player) {
        if (player == null) {
            return "player-missing";
        }
        CommandKey key = activeCommandByPlayer.get(player.getUniqueId());
        String lastAck = lastAckByPlayer.getOrDefault(player.getUniqueId(), "-");
        String lastFinished = lastFinishedByPlayer.getOrDefault(player.getUniqueId(), "-");
        String lastError = lastErrorByPlayer.getOrDefault(player.getUniqueId(), "-");
        if (key == null) {
            return "none, bridgeReady=" + bridgeV4Sender.isReady(player) + ", lastAck=" + lastAck
                    + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
        }
        ClientVisualCommand command = pendingAcks.get(key);
        if (command != null) {
            return "pending_ack:" + commandName(command.body()) + "#" + command.seq()
                    + ", attempts=" + command.attempts() + ", lastAck=" + lastAck
                    + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
        }
        command = runningCommands.get(key);
        if (command != null) {
            return "running:" + command.effectId() + "#" + command.seq() + ", source=" + command.source()
                    + ", lastAck=" + lastAck + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
        }
        return "stale, lastAck=" + lastAck + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
    }

    public void forgetPlayer(Player player) {
        if (player != null) {
            forgetPlayer(player.getUniqueId(), "player-left");
        }
    }

    public void forgetPlayer(Player player, String reason) {
        if (player != null) {
            forgetPlayer(player.getUniqueId(), reason);
        }
    }

    public synchronized void forgetPlayer(UUID playerUuid, String reason) {
        forgetPlayer(playerUuid, true);
    }

    /** Clears death-bound visual state while keeping command IDs monotonic for the live client session. */
    public synchronized void forgetPlayerPreservingSessionSequence(Player player) {
        if (player != null) {
            forgetPlayer(player.getUniqueId(), false);
        }
    }

    private void forgetPlayer(UUID playerUuid, boolean clearSessionSequence) {
        if (playerUuid == null) {
            return;
        }
        forgetCommands(playerUuid);
        desiredVisuals.remove(playerUuid);
        activeStreamByPlayer.remove(playerUuid);
        lastAckByPlayer.remove(playerUuid);
        lastFinishedByPlayer.remove(playerUuid);
        lastErrorByPlayer.remove(playerUuid);
        correlations.clearPlayer(playerUuid);
        if (clearSessionSequence) {
            lastSequenceBySession.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        }
    }

    synchronized void tick() {
        long now = clock.getAsLong();
        List<ClientVisualCommand> deliveryTimeouts = new ArrayList<>();
        for (ClientVisualCommand command : pendingAcks.values()) {
            if (command.ackDeadlineMillis() > now) {
                continue;
            }
            Player player = playerLookup.apply(command.playerUuid());
            UUID sessionUuid = canonicalSession(command.sessionId());
            String streamId = player == null ? null : bridgeV4Sender.activeStreamId(player).orElse(null);
            CommandKey key = new CommandKey(command.playerUuid(), sessionUuid, command.seq());
            if (player != null && player.isOnline() && bridgeV4Sender.isReady(player)
                    && sessionUuid != null && streamId != null
                    && correlations.mayRetry(command.playerUuid(), sessionUuid, command.seq(), streamId, now)) {
                Optional<NarcoticsBridgeBodyCodec.Body> resent = resend(player, command);
                if (resent.isPresent()) {
                    command.markSent(now, RETRY_INTERVAL_MILLIS);
                    continue;
                }
            }
            deliveryTimeouts.add(command);
        }
        for (ClientVisualCommand command : deliveryTimeouts) {
            CommandKey key = new CommandKey(command.playerUuid(), canonicalSession(command.sessionId()), command.seq());
            pendingAcks.remove(key, command);
            correlations.removeCommand(command.playerUuid(), canonicalSession(command.sessionId()), command.seq());
            activeCommandByPlayer.remove(command.playerUuid(), key);
            if (cleanupIntents.get(command.playerUuid()) == command) {
                cleanupRetryAtMillis.put(command.playerUuid(), now);
            }
            if (command.body().type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START) {
                desiredVisuals.remove(command.playerUuid(), command);
                Player player = playerLookup.apply(command.playerUuid());
                if (player != null && player.isOnline()) {
                    notifyPlayer(player, ChatColor.RED, "Клиент не подтвердил запуск визуального эффекта "
                            + command.effectId() + ". Серверный маршрут останется без клиентского визуала.");
                }
                if (command.fallbackHandler() != null) {
                    command.fallbackHandler().onFallback(command.playerUuid(), command.effectId(),
                            command.seconds(), command.intensity(), command.source(), "no-ack");
                }
                lastErrorByPlayer.put(command.playerUuid(), command.effectId() + ":no-ack");
            }
        }
        retryCleanupIntents(now);

        List<ClientVisualCommand> expiredVisuals = new ArrayList<>();
        for (ClientVisualCommand command : runningCommands.values()) {
            long expiry = command.createdAtMillis() + command.body().durationMillis() + RUNNING_TIMEOUT_GRACE_MILLIS;
            Player player = playerLookup.apply(command.playerUuid());
            if (now >= expiry || player == null || !player.isOnline()) {
                expiredVisuals.add(command);
            }
        }
        for (ClientVisualCommand command : expiredVisuals) {
            CommandKey key = new CommandKey(command.playerUuid(), canonicalSession(command.sessionId()), command.seq());
            runningCommands.remove(key, command);
            activeCommandByPlayer.remove(command.playerUuid(), key);
            Player player = playerLookup.apply(command.playerUuid());
            boolean durationElapsed = now >= command.createdAtMillis() + command.body().durationMillis();
            if (player != null && player.isOnline() && durationElapsed
                    && bridgeV4Sender.isReady(player)) {
                sendVisualStop(player, command.effectId(), "timeout-cleanup");
            }
            desiredVisuals.remove(command.playerUuid(), command);
        }
        correlations.prune(now);
    }

    private Optional<NarcoticsBridgeBodyCodec.Body> resend(Player player, ClientVisualCommand command) {
        return switch (command.body().type()) {
            case VISUAL_START -> bridgeV4Sender.sendVisualStartTracked(player, command.body(), false);
            case VISUAL_STOP -> bridgeV4Sender.stopVisualTracked(player, command.body());
            case VISUAL_CLEAR_ALL -> bridgeV4Sender.clearVisualsTracked(player, command.body(), false);
        };
    }

    private void retryCleanupIntents(long now) {
        for (Map.Entry<UUID, ClientVisualCommand> entry : cleanupIntents.entrySet()) {
            UUID playerUuid = entry.getKey();
            ClientVisualCommand intent = entry.getValue();
            UUID sessionUuid = canonicalSession(intent.sessionId());
            CommandKey key = sessionUuid == null ? null : new CommandKey(playerUuid, sessionUuid, intent.seq());
            if (key != null && pendingAcks.containsKey(key)) {
                continue;
            }
            if (cleanupRetryAtMillis.getOrDefault(playerUuid, Long.MAX_VALUE) > now) {
                continue;
            }
            Player player = playerLookup.apply(playerUuid);
            if (player == null || !player.isOnline()) {
                cleanupRetryAtMillis.put(playerUuid, now + CLEANUP_RETRY_DELAY_MILLIS);
                continue;
            }
            ClientCapabilityState capability = capabilities.state(player);
            UUID currentSession = capability == null ? null : canonicalSession(capability.sessionId());
            if (sessionUuid == null || !sessionUuid.equals(currentSession)) {
                cleanupIntents.remove(playerUuid, intent);
                cleanupRetryAtMillis.remove(playerUuid);
                continue;
            }
            if (!bridgeV4Sender.isReady(player) || sendFreshCleanup(player, intent, sessionUuid, now) == null) {
                cleanupRetryAtMillis.put(playerUuid, now + CLEANUP_RETRY_DELAY_MILLIS);
            }
        }
    }

    private ClientVisualCommand sendFreshCleanup(Player player, ClientVisualCommand intent,
                                                  UUID sessionUuid, long now) {
        long seq = nextSequence(player.getUniqueId(), sessionUuid);
        if (seq <= 0L || now <= 0L
                || !correlations.canRegister(player.getUniqueId(), sessionUuid, seq, now)) {
            return null;
        }
        NarcoticsBridgeBodyCodec.Body requested = copyWithSequenceAndTimestamp(intent.body(), seq, now);
        Optional<NarcoticsBridgeBodyCodec.Body> sent = switch (requested.type()) {
            case VISUAL_STOP -> bridgeV4Sender.stopVisualTracked(player, requested);
            case VISUAL_CLEAR_ALL -> bridgeV4Sender.clearVisualsTracked(player, requested, true);
            case VISUAL_START -> Optional.empty();
        };
        if (sent.isEmpty()) {
            return null;
        }
        String streamId = bridgeV4Sender.activeStreamId(player).orElse(null);
        NarcoticsBridgeBodyCodec.Body body = sent.get();
        if (streamId == null || !correlations.register(player.getUniqueId(), sessionUuid, streamId, body, now)) {
            bridgeV4Sender.retire(player.getUniqueId());
            return null;
        }
        ClientVisualCommand retry = new ClientVisualCommand(seq, player.getUniqueId(), player.getName(),
                sessionUuid.toString(), intent.effectId(), intent.shaderpack(), intent.seconds(), intent.intensity(),
                intent.fadeInMillis(), intent.fadeOutMillis(), intent.source(), body, streamId, now,
                null, intent.finishHandler());
        retry.markSent(now, RETRY_INTERVAL_MILLIS);
        CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, seq);
        pendingAcks.put(key, retry);
        activeCommandByPlayer.put(player.getUniqueId(), key);
        cleanupIntents.put(player.getUniqueId(), retry);
        cleanupRetryAtMillis.put(player.getUniqueId(), now + CLEANUP_RETRY_DELAY_MILLIS);
        return retry;
    }

    private static NarcoticsBridgeBodyCodec.Body copyWithSequenceAndTimestamp(
            NarcoticsBridgeBodyCodec.Body body, long seq, long now) {
        return new NarcoticsBridgeBodyCodec.Body(body.type(), seq, now, body.sessionUuid(), body.effectId(),
                body.shaderpackId(), body.durationMillis(), body.intensity(), body.fadeInMillis(),
                body.fadeOutMillis(), body.mode(), body.clearPolicy(), body.source(), body.reason(), body.status(),
                body.visualInstanceId(), body.targetVisualInstanceId(), body.clearThroughCounter());
    }

    private long nextSequence(UUID playerUuid, UUID sessionUuid) {
        if (playerUuid == null || sessionUuid == null) {
            return -1L;
        }
        SessionKey key = new SessionKey(playerUuid, sessionUuid);
        long previous = lastSequenceBySession.getOrDefault(key, 0L);
        if (previous == Long.MAX_VALUE) {
            return -1L;
        }
        long next = previous + 1L;
        lastSequenceBySession.put(key, next);
        return next;
    }

    private void retirePriorCommand(UUID playerUuid, ClientVisualCommand prior) {
        if (prior == null) {
            return;
        }
        UUID sessionUuid = canonicalSession(prior.sessionId());
        if (sessionUuid != null) {
            correlations.removeCommand(playerUuid, sessionUuid, prior.seq());
            CommandKey key = new CommandKey(playerUuid, sessionUuid, prior.seq());
            pendingAcks.remove(key);
            runningCommands.remove(key);
            activeCommandByPlayer.remove(playerUuid, key);
        }
    }

    private void forgetCommands(UUID playerUuid) {
        pendingAcks.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        runningCommands.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        activeCommandByPlayer.remove(playerUuid);
        cleanupIntents.remove(playerUuid);
        cleanupRetryAtMillis.remove(playerUuid);
    }

    private void removeCommandsForPlayer(UUID playerUuid) {
        pendingAcks.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        runningCommands.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        activeCommandByPlayer.remove(playerUuid);
    }

    private static UUID canonicalSession(String session) {
        try {
            UUID value = UUID.fromString(session);
            return value.toString().equals(session) ? value : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private long positiveNow() {
        long now = clock.getAsLong();
        return now > 0L ? now : -1L;
    }

    private static int clampFade(int fade) {
        return Math.max(0, Math.min(10_000, fade));
    }

    private static String normalizeSource(String source) {
        if (source == null || !source.matches("[A-Za-z0-9_.:-]{1,32}")) {
            return "NARCOTICS";
        }
        return source;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || !reason.matches("[A-Za-z0-9_.:-]{1,256}")) {
            return "server_clear";
        }
        return reason;
    }

    private static String normalizeShaderpack(String shaderpack) {
        if (shaderpack == null || shaderpack.isEmpty()) {
            return "";
        }
        return SetHolder.SHADERPACKS.contains(shaderpack) ? shaderpack : "";
    }

    private static String commandName(NarcoticsBridgeBodyCodec.Body body) {
        return switch (body.type()) {
            case VISUAL_START -> body.effectId();
            case VISUAL_STOP -> body.effectId() + ":stop";
            case VISUAL_CLEAR_ALL -> "clear-all";
        };
    }

    private void notifyPlayer(Player player, ChatColor color, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.DARK_AQUA + "[CopiMine] " + color + message);
        }
    }

    private String describeRoute(String route) {
        return switch (route.toUpperCase(Locale.ROOT)) {
            case "FALLBACK_POST_PROCESS" -> "post-process fallback";
            case "CANVAS_UNAVAILABLE" -> "Canvas недоступен";
            case "OPTIFINE_UNAVAILABLE" -> "OptiFine runtime недоступен";
            case "CUSTOM_UNAVAILABLE" -> "custom runtime недоступен";
            default -> route;
        };
    }

    private String describeReason(String reason) {
        return switch (reason.toLowerCase(Locale.ROOT)) {
            case "no-ack" -> "клиент не отправил ACK";
            case "client-error" -> "клиент вернул общую ошибку";
            case "client-unavailable" -> "клиентский runtime пропал";
            case "server-visuals-disabled" -> "клиентские визуалы отключены";
            case "visual-capacity-limit" -> "достигнут предел активных визуальных эффектов";
            case "iris-runtime-unavailable" -> "Iris runtime недоступен";
            case "missing-exported-zip" -> "shaderpack не экспортирован";
            case "zip-not-iris-compatible", "profile-not-iris-runtime" -> "shaderpack несовместим с Iris";
            default -> reason;
        };
    }

    private record CommandKey(UUID playerUuid, UUID sessionUuid, long sequence) {
    }

    private record SessionKey(UUID playerUuid, UUID sessionUuid) {
    }

    private static final class SetHolder {
        static final java.util.Set<String> SHADERPACKS = java.util.Set.of("trippy_shaderpack.zip", "ctr_vcr.zip",
                "cursed_metamorphopsia.zip", "lsd_shader.zip", "crucify.zip", "nms_1_6.zip",
                "acid_shaders.zip", "white_sharp_1_2.zip");
    }
}
