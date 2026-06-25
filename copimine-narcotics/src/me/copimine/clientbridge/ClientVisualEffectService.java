package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientVisualEffectService {
    private static final long ACK_TIMEOUT_MILLIS = 2_000L;
    private static final int MAX_SEND_ATTEMPTS = 3;

    private final CopiMineNarcotics plugin;
    private final ClientCapabilityService capabilities;
    private final AtomicLong nextSeq = new AtomicLong(1L);
    private final Map<Long, ClientVisualCommand> pendingAcks = new ConcurrentHashMap<>();
    private final Map<Long, ClientVisualCommand> runningCommands = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeSeqByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastAckByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastFinishedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastErrorByPlayer = new ConcurrentHashMap<>();
    private int tickerTaskId = -1;

    public ClientVisualEffectService(CopiMineNarcotics plugin, ClientCapabilityService capabilities) {
        this.plugin = plugin;
        this.capabilities = capabilities;
        this.tickerTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L).getTaskId();
    }

    public boolean canUse(Player player, String effectId) {
        return capabilities.supportsEffect(player, effectId);
    }

    public long sendVisualStart(
            Player player,
            String effectId,
            int seconds,
            float intensity,
            String source,
            ClientVisualCommand.FallbackHandler fallbackHandler,
            ClientVisualCommand.FinishHandler finishHandler
    ) {
        if (player == null || !player.isOnline()) {
            return -1L;
        }
        ClientCapabilityState state = capabilities.state(player);
        if (state == null || !state.supportsEffect(effectId)) {
            return -1L;
        }
        clearVisuals(player, "replace-before-start");
        long seq = nextSeq.getAndIncrement();
        long now = System.currentTimeMillis();
        ClientVisualCommand command = new ClientVisualCommand(
                seq,
                player.getUniqueId(),
                player.getName(),
                state.sessionId(),
                effectId.toUpperCase(Locale.ROOT),
                Math.max(1, Math.min(600, seconds)),
                ClientBridgePayloads.clampIntensity(intensity),
                source == null || source.isBlank() ? "NARCOTICS" : source,
                now,
                fallbackHandler,
                finishHandler
        );
        lastAckByPlayer.put(player.getUniqueId(), "waiting:" + command.effectId() + "#" + seq);
        lastErrorByPlayer.remove(player.getUniqueId());
        command.markSent(now, ACK_TIMEOUT_MILLIS);
        pendingAcks.put(seq, command);
        activeSeqByPlayer.put(player.getUniqueId(), seq);
        player.sendPluginMessage(
                plugin,
                ClientBridgePayloads.CHANNEL,
                ClientBridgePayloads.encodeVisualStart(seq, state.sessionId(), command.effectId(), command.seconds(), command.intensity(), command.source())
        );
        return seq;
    }

    public void sendVisualStop(Player player, String effectId, String reason) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ClientCapabilityState state = capabilities.state(player);
        String sessionId = state == null ? "" : state.sessionId();
        long seq = nextSeq.getAndIncrement();
        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodeVisualStop(seq, sessionId, effectId, reason));
        removePlayerCommands(player.getUniqueId());
    }

    public void clearVisuals(Player player) {
        clearVisuals(player, "server_clear");
    }

    public void clearVisuals(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        ClientCapabilityState state = capabilities.state(player);
        String sessionId = state == null ? "" : state.sessionId();
        long seq = nextSeq.getAndIncrement();
        if (player.isOnline()) {
            player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodeClearAll(seq, sessionId, reason));
        }
        removePlayerCommands(playerUuid);
    }

    public void shutdown() {
        if (tickerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickerTaskId);
            tickerTaskId = -1;
        }
        pendingAcks.clear();
        runningCommands.clear();
        activeSeqByPlayer.clear();
        lastAckByPlayer.clear();
        lastFinishedByPlayer.clear();
        lastErrorByPlayer.clear();
    }

    public void handleMessage(Player player, ClientBridgePayloads.Message message) {
        if (player == null || message == null) {
            return;
        }
        switch (message.type()) {
            case ClientBridgePayloads.VISUAL_ACK -> handleAck(player, message);
            case ClientBridgePayloads.VISUAL_FINISHED -> handleFinished(player, message);
            case ClientBridgePayloads.VISUAL_ERROR -> handleError(player, message);
            default -> {
            }
        }
    }

    public String sessionsSummary() {
        return "pending=" + pendingAcks.size() + ", running=" + runningCommands.size();
    }

    public String playerSummary(Player player) {
        if (player == null) {
            return "player-missing";
        }
        Long seq = activeSeqByPlayer.get(player.getUniqueId());
        String lastAck = lastAckByPlayer.getOrDefault(player.getUniqueId(), "-");
        String lastFinished = lastFinishedByPlayer.getOrDefault(player.getUniqueId(), "-");
        String lastError = lastErrorByPlayer.getOrDefault(player.getUniqueId(), "-");
        if (seq == null) {
            return "none, lastAck=" + lastAck + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
        }
        ClientVisualCommand command = pendingAcks.get(seq);
        if (command != null) {
            return "pending_ack:" + command.effectId() + "#" + command.seq()
                    + ", attempts=" + command.attempts()
                    + ", lastAck=" + lastAck
                    + ", lastFinished=" + lastFinished
                    + ", lastError=" + lastError;
        }
        command = runningCommands.get(seq);
        if (command != null) {
            return "running:" + command.effectId() + "#" + command.seq()
                    + ", source=" + command.source()
                    + ", lastAck=" + lastAck
                    + ", lastFinished=" + lastFinished
                    + ", lastError=" + lastError;
        }
        return "stale, lastAck=" + lastAck + ", lastFinished=" + lastFinished + ", lastError=" + lastError;
    }

    public void forgetPlayer(Player player) {
        if (player == null) {
            return;
        }
        forgetPlayer(player.getUniqueId(), "player-left");
    }

    public void forgetPlayer(Player player, String reason) {
        if (player == null) {
            return;
        }
        forgetPlayer(player.getUniqueId(), reason);
    }

    public void forgetPlayer(UUID playerUuid, String reason) {
        removePlayerCommands(playerUuid);
        if (reason != null && !reason.isBlank()) {
            lastFinishedByPlayer.put(playerUuid, reason);
        }
    }

    private void handleAck(Player player, ClientBridgePayloads.Message message) {
        ClientVisualCommand pending = pendingAcks.remove(message.seq());
        if (pending == null) {
            return;
        }
        if (!player.getUniqueId().equals(pending.playerUuid())) {
            activeSeqByPlayer.remove(pending.playerUuid(), pending.seq());
            return;
        }
        String status = message.status().toUpperCase(Locale.ROOT);
        lastAckByPlayer.put(player.getUniqueId(), status + ":" + pending.effectId() + "#" + pending.seq());
        if (ClientBridgePayloads.STATUS_STARTED.equals(status)) {
            runningCommands.put(pending.seq(), pending);
            activeSeqByPlayer.put(player.getUniqueId(), pending.seq());
            return;
        }
        activeSeqByPlayer.remove(player.getUniqueId(), pending.seq());
        lastFinishedByPlayer.put(player.getUniqueId(), status.toLowerCase(Locale.ROOT));
        if (pending.finishHandler() != null) {
            pending.finishHandler().onFinished(player.getUniqueId(), pending.effectId(), pending.source(), status.toLowerCase(Locale.ROOT));
        }
    }

    private void handleFinished(Player player, ClientBridgePayloads.Message message) {
        ClientVisualCommand command = runningCommands.remove(message.seq());
        if (command == null) {
            command = pendingAcks.remove(message.seq());
        }
        if (command == null) {
            return;
        }
        activeSeqByPlayer.remove(player.getUniqueId(), command.seq());
        lastFinishedByPlayer.put(player.getUniqueId(), command.effectId() + ":" + (message.reason().isBlank() ? "finished" : message.reason()));
        if (command.finishHandler() != null) {
            command.finishHandler().onFinished(player.getUniqueId(), command.effectId(), command.source(), message.reason().isBlank() ? "finished" : message.reason());
        }
    }

    private void handleError(Player player, ClientBridgePayloads.Message message) {
        ClientVisualCommand command = runningCommands.remove(message.seq());
        if (command == null) {
            command = pendingAcks.remove(message.seq());
        }
        if (command == null) {
            return;
        }
        activeSeqByPlayer.remove(player.getUniqueId(), command.seq());
        lastErrorByPlayer.put(player.getUniqueId(), command.effectId() + ":" + (message.reason().isBlank() ? "client-error" : message.reason()));
        if (command.fallbackHandler() != null) {
            command.fallbackHandler().onFallback(
                    player.getUniqueId(),
                    command.effectId(),
                    command.seconds(),
                    command.intensity(),
                    command.source(),
                    message.reason().isBlank() ? "client-error" : message.reason()
            );
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<ClientVisualCommand> timedOut = new ArrayList<>();
        for (ClientVisualCommand command : pendingAcks.values()) {
            if (command.ackDeadlineMillis() > now) {
                continue;
            }
            Player player = Bukkit.getPlayer(command.playerUuid());
            ClientCapabilityState state = player == null ? null : capabilities.state(player);
            if (player != null && player.isOnline() && state != null && state.sessionId().equals(command.sessionId()) && command.attempts() < MAX_SEND_ATTEMPTS) {
                command.markSent(now, ACK_TIMEOUT_MILLIS);
                player.sendPluginMessage(
                        plugin,
                        ClientBridgePayloads.CHANNEL,
                        ClientBridgePayloads.encodeVisualStart(command.seq(), command.sessionId(), command.effectId(), command.seconds(), command.intensity(), command.source())
                );
                continue;
            }
            timedOut.add(command);
        }
        for (ClientVisualCommand command : timedOut) {
            pendingAcks.remove(command.seq());
            activeSeqByPlayer.remove(command.playerUuid(), command.seq());
            if (command.fallbackHandler() != null) {
                command.fallbackHandler().onFallback(
                        command.playerUuid(),
                        command.effectId(),
                        command.seconds(),
                        command.intensity(),
                        command.source(),
                    "no-ack"
                );
            }
            lastErrorByPlayer.put(command.playerUuid(), command.effectId() + ":no-ack");
        }
        List<ClientVisualCommand> staleRunning = new ArrayList<>();
        for (ClientVisualCommand command : runningCommands.values()) {
            Player player = Bukkit.getPlayer(command.playerUuid());
            ClientCapabilityState state = player == null ? null : capabilities.state(player);
            if (player == null || !player.isOnline() || state == null || !state.sessionId().equals(command.sessionId())) {
                staleRunning.add(command);
            }
        }
        for (ClientVisualCommand command : staleRunning) {
            runningCommands.remove(command.seq());
            activeSeqByPlayer.remove(command.playerUuid(), command.seq());
            lastFinishedByPlayer.put(command.playerUuid(), command.effectId() + ":client-unavailable");
        }
    }

    private void removePlayerCommands(UUID playerUuid) {
        Long activeSeq = activeSeqByPlayer.remove(playerUuid);
        if (activeSeq != null) {
            pendingAcks.remove(activeSeq);
            runningCommands.remove(activeSeq);
        }
    }
}
