package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    private static final long RUNNING_TIMEOUT_GRACE_MILLIS = 2_000L;
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
            String shaderpack,
            int seconds,
            float intensity,
            int fadeInMillis,
            int fadeOutMillis,
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
                shaderpack == null ? "" : shaderpack,
                Math.max(1, Math.min(600, seconds)),
                ClientBridgePayloads.clampIntensity(intensity),
                Math.max(0, Math.min(10_000, fadeInMillis)),
                Math.max(0, Math.min(10_000, fadeOutMillis)),
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
                ClientBridgePayloads.encodeVisualStart(
                        seq,
                        state.sessionId(),
                        command.effectId(),
                        command.shaderpack(),
                        command.seconds(),
                        command.intensity(),
                        command.fadeInMillis(),
                        command.fadeOutMillis(),
                        command.source()
                )
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
        lastAckByPlayer.remove(playerUuid);
        lastFinishedByPlayer.remove(playerUuid);
        lastErrorByPlayer.remove(playerUuid);
    }

    private void handleAck(Player player, ClientBridgePayloads.Message message) {
        ClientVisualCommand pending = pendingAcks.get(message.seq());
        if (!commandMatchesPlayerSession(player, message, pending)) {
            return;
        }
        if (!pendingAcks.remove(message.seq(), pending)) {
            return;
        }
        String status = message.status().toUpperCase(Locale.ROOT);
        lastAckByPlayer.put(player.getUniqueId(), status + ":" + pending.effectId() + "#" + pending.seq());
        if (status.startsWith(ClientBridgePayloads.STATUS_STARTED)) {
            if (!status.contains("IRIS_SHADERPACK")) {
                String route = status.replace(ClientBridgePayloads.STATUS_STARTED, "").replaceFirst("^[:_\\-]+", "");
                if (route.isBlank()) {
                    route = "UNKNOWN_CLIENT_ROUTE";
                }
                lastErrorByPlayer.put(player.getUniqueId(), pending.effectId() + ":client-route-" + route.toLowerCase(Locale.ROOT));
                notifyPlayer(player, ChatColor.YELLOW, "Шейдер для эффекта " + pending.effectId() + " не поднялся через Iris. Активирован маршрут: " + describeRoute(route) + ".");
                plugin.getLogger().warning("CopiMineClient visual started without Iris shaderpack for "
                        + player.getName()
                        + ": effect=" + pending.effectId()
                        + ", route=" + route
                        + ", shaderpack=" + pending.shaderpack());
            } else {
                lastErrorByPlayer.remove(player.getUniqueId());
            }
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
        ClientVisualCommand command = popOwnedCommand(player, message);
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
        ClientVisualCommand command = popOwnedCommand(player, message);
        if (command == null) {
            return;
        }
        activeSeqByPlayer.remove(player.getUniqueId(), command.seq());
        String reason = message.reason().isBlank() ? "client-error" : message.reason();
        lastErrorByPlayer.put(player.getUniqueId(), command.effectId() + ":" + reason);
        notifyPlayer(player, ChatColor.RED, "Клиентский визуальный эффект " + command.effectId() + " не запустился: " + describeReason(reason) + ".");
        if (command.fallbackHandler() != null) {
            command.fallbackHandler().onFallback(
                    player.getUniqueId(),
                    command.effectId(),
                    command.seconds(),
                    command.intensity(),
                    command.source(),
                    reason
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
                        ClientBridgePayloads.encodeVisualStart(
                                command.seq(),
                                command.sessionId(),
                                command.effectId(),
                                command.shaderpack(),
                                command.seconds(),
                                command.intensity(),
                                command.fadeInMillis(),
                                command.fadeOutMillis(),
                                command.source()
                        )
                );
                continue;
            }
            timedOut.add(command);
        }
        for (ClientVisualCommand command : timedOut) {
            pendingAcks.remove(command.seq());
            activeSeqByPlayer.remove(command.playerUuid(), command.seq());
            Player player = Bukkit.getPlayer(command.playerUuid());
            if (player != null && player.isOnline()) {
                notifyPlayer(player, ChatColor.RED, "Клиент не подтвердил запуск визуального эффекта " + command.effectId() + ". Оставлен только серверный fallback.");
            }
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
            boolean expired = now > (command.createdAtMillis() + (command.seconds() * 1_000L) + RUNNING_TIMEOUT_GRACE_MILLIS);
            if (expired || player == null || !player.isOnline() || state == null || !state.sessionId().equals(command.sessionId())) {
                staleRunning.add(command);
            }
        }
        for (ClientVisualCommand command : staleRunning) {
            runningCommands.remove(command.seq());
            activeSeqByPlayer.remove(command.playerUuid(), command.seq());
            Player player = Bukkit.getPlayer(command.playerUuid());
            boolean commandExpired = now > (command.createdAtMillis() + (command.seconds() * 1_000L) + RUNNING_TIMEOUT_GRACE_MILLIS);
            if (commandExpired) {
                if (player != null && player.isOnline()) {
                    sendVisualStop(player, command.effectId(), "timeout-cleanup");
                }
                if (command.finishHandler() != null) {
                    command.finishHandler().onFinished(command.playerUuid(), command.effectId(), command.source(), "timeout-cleanup");
                }
                lastFinishedByPlayer.put(command.playerUuid(), command.effectId() + ":timeout-cleanup");
                continue;
            }
            if (player != null && player.isOnline() && command.fallbackHandler() != null) {
                command.fallbackHandler().onFallback(
                        command.playerUuid(),
                        command.effectId(),
                        command.seconds(),
                        command.intensity(),
                        command.source(),
                        "client-unavailable"
                );
            }
            lastFinishedByPlayer.put(command.playerUuid(), command.effectId() + ":client-unavailable");
        }
    }

    private void notifyPlayer(Player player, ChatColor color, String message) {
        if (player == null || !player.isOnline() || message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(ChatColor.DARK_AQUA + "[CopiMine] " + color + message);
    }

    private String describeRoute(String route) {
        return switch (route.toUpperCase(Locale.ROOT)) {
            case "FALLBACK_POST_PROCESS" -> "post-process fallback";
            case "CANVAS_UNAVAILABLE" -> "Canvas недоступен";
            case "OPTIFINE_UNAVAILABLE" -> "OptiFine runtime недоступен";
            case "CUSTOM_UNAVAILABLE" -> "кастомный runtime недоступен";
            case "UNKNOWN_CLIENT_ROUTE" -> "неизвестный клиентский маршрут";
            default -> route;
        };
    }

    private String describeReason(String reason) {
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "no-ack" -> "клиент не отправил ACK";
            case "client-error" -> "клиент вернул общую ошибку";
            case "client-unavailable" -> "клиентский runtime пропал во время эффекта";
            case "server-visuals-disabled" -> "серверные визуалы отключены в клиентском моде";
            case "iris-runtime-unavailable" -> "Iris runtime недоступен";
            case "missing-exported-zip" -> "shaderpack не был экспортирован";
            case "zip-not-iris-compatible", "profile-not-iris-runtime" -> "shaderpack не совместим с Iris";
            case "pack-not-active-after-switch", "switch-failed", "pipeline-failed" -> "runtime не подтвердил переключение shaderpack";
            default -> reason == null || reason.isBlank() ? "неизвестная причина" : reason;
        };
    }

    private void removePlayerCommands(UUID playerUuid) {
        Long activeSeq = activeSeqByPlayer.remove(playerUuid);
        if (activeSeq != null) {
            pendingAcks.remove(activeSeq);
            runningCommands.remove(activeSeq);
        }
    }

    private ClientVisualCommand popOwnedCommand(Player player, ClientBridgePayloads.Message message) {
        ClientVisualCommand running = runningCommands.get(message.seq());
        if (commandMatchesPlayerSession(player, message, running)) {
            return runningCommands.remove(message.seq(), running) ? running : null;
        }
        ClientVisualCommand pending = pendingAcks.get(message.seq());
        if (commandMatchesPlayerSession(player, message, pending)) {
            return pendingAcks.remove(message.seq(), pending) ? pending : null;
        }
        return null;
    }

    private boolean commandMatchesPlayerSession(Player player, ClientBridgePayloads.Message message, ClientVisualCommand command) {
        return player != null
                && message != null
                && command != null
                && player.getUniqueId().equals(command.playerUuid())
                && command.sessionId().equals(message.sessionId());
    }
}
