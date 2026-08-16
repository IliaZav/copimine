package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CopiMineClientBridge implements Listener, PluginMessageListener {
    private static final int MAX_INBOUND_MESSAGE_BYTES = 4_096;
    private static final long INBOUND_MESSAGE_INTERVAL_MILLIS = 50L;
    private static final long READY_INBOUND_INTERVAL_MILLIS = 100L;

    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final ClientCapabilityService capabilities;
    private final ClientVisualEffectService visuals;
    private final Map<UUID, Long> nextInboundMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextReadyMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> admissionTimeoutTasks = new ConcurrentHashMap<>();
    private ClientReadyAdmission admission;
    private boolean clientModEnforcementWarningLogged = false;

    public CopiMineClientBridge(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.capabilities = new ClientCapabilityService();
        this.visuals = new ClientVisualEffectService(plugin, capabilities);
        this.admission = new ClientReadyAdmission(configService.clientGateTimeoutSeconds() * 1_000L);
        applyCapabilityTtl();
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ClientReadyPayloads.READY_CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ClientReadyPayloads.ACK_CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            visuals.clearVisuals(player, "plugin-disable");
            visuals.forgetPlayer(player);
            capabilities.clear(player.getUniqueId());
        }
        clearPendingAdmissions();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientReadyPayloads.READY_CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientReadyPayloads.ACK_CHANNEL);
        nextInboundMessageAt.clear();
        nextReadyMessageAt.clear();
        visuals.shutdown();
    }

    public void reload(NarcoticsConfigService configService) {
        clearPendingAdmissions();
        this.configService = configService;
        this.admission = new ClientReadyAdmission(configService.clientGateTimeoutSeconds() * 1_000L);
        applyCapabilityTtl();
    }

    public ClientCapabilityService capabilities() {
        return capabilities;
    }

    public ClientVisualEffectService visuals() {
        return visuals;
    }

    public boolean enabled() {
        return configService.clientBridgeEnabled();
    }

    public boolean requireClientMod() {
        return configService.requireClientMod();
    }

    public String statusFor(Player player) {
        return "Admission=" + admissionStatus(player) + ", visuals=" + visuals.playerSummary(player)
                + ", capability=" + capabilities.describe(player);
    }

    public String routeHint(Player player, String effectId) {
        return capabilities.routeHint(player, effectId);
    }

    private void applyCapabilityTtl() {
        long ttlMillis = Math.max(30L, configService.handshakeTimeoutSeconds() * 3L) * 1_000L;
        capabilities.setTtlMillis(ttlMillis);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled()) {
            return;
        }
        if (ClientReadyPayloads.READY_CHANNEL.equalsIgnoreCase(channel)) {
            handleClientReady(player, message);
            return;
        }
        if (!ClientBridgePayloads.CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }
        if (message == null || message.length > MAX_INBOUND_MESSAGE_BYTES) {
            capabilities.reportProblem(player, "payload-too-large");
            return;
        }
        if (!allowInboundMessage(player)) {
            return;
        }
        try {
            ClientBridgePayloads.Message payload = ClientBridgePayloads.decode(message);
            switch (payload.type()) {
                case ClientBridgePayloads.HELLO, ClientBridgePayloads.CAPABILITIES_UPDATE -> {
                    capabilities.update(player, ClientCapabilityState.fromMessage(payload));
                    if (player.isOnline()) {
                        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodePing(0L, payload.sessionId()));
                    }
                }
                case ClientBridgePayloads.HEARTBEAT -> capabilities.touch(player, payload.sessionId());
                case ClientBridgePayloads.VISUAL_ACK, ClientBridgePayloads.VISUAL_FINISHED, ClientBridgePayloads.VISUAL_ERROR -> {
                    if (capabilities.touch(player, payload.sessionId())) {
                        visuals.handleMessage(player, payload);
                    }
                }
                default -> capabilities.reportProblem(player, "unsupported-message-type:" + payload.type());
            }
        } catch (Exception error) {
            capabilities.reportProblem(player, error.getMessage());
            plugin.getLogger().warning("Invalid CopiMineClient payload from " + player.getName() + ": " + error.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        capabilities.clear(player.getUniqueId());
        if (!enabled() || !configService.clientGateRequired()) {
            return;
        }
        ClientReadyAdmission.PendingClientAdmission pending = admission.onJoin(player.getUniqueId(), System.currentTimeMillis());
        long delayTicks = Math.max(1L, (configService.clientGateTimeoutSeconds() * 20L) + 1L);
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> handleAdmissionTimeout(player.getUniqueId(), pending.joinAttemptId()),
                delayTicks);
        BukkitTask previous = admissionTimeoutTasks.put(player.getUniqueId(), timeoutTask);
        if (previous != null) {
            previous.cancel();
        }
        plugin.getLogger().fine("CLIENT_GATE_PENDING player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " attempt=" + pending.joinAttemptId()
                + " timeoutMs=" + admission.timeoutMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visuals.forgetPlayer(event.getPlayer());
        capabilities.clear(event.getPlayer().getUniqueId());
        nextInboundMessageAt.remove(event.getPlayer().getUniqueId());
        nextReadyMessageAt.remove(event.getPlayer().getUniqueId());
        BukkitTask timeout = admissionTimeoutTasks.remove(event.getPlayer().getUniqueId());
        if (timeout != null) {
            timeout.cancel();
        }
        admission.onQuit(event.getPlayer().getUniqueId());
        plugin.getLogger().fine("CLIENT_DISCONNECTED_DURING_CHECK player=" + event.getPlayer().getName()
                + " uuid=" + event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        visuals.clearVisuals(event.getEntity(), "death");
        visuals.forgetPlayer(event.getEntity(), "death");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        visuals.clearVisuals(event.getPlayer(), "world-change");
    }

    private void handleClientReady(Player player, byte[] message) {
        if (player == null || !configService.clientGateRequired() || !allowReadyMessage(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        ClientReadyAdmission.ReadyDecision decision;
        try {
            ClientReadyAdmission.ReadyRequest request = ClientReadyPayloads.decodeReady(message);
            decision = admission.onReady(player.getUniqueId(), request, now);
            plugin.getLogger().fine("CLIENT_READY_RECEIVED player=" + player.getName()
                    + " uuid=" + player.getUniqueId()
                    + " clientVersion=" + request.clientVersion()
                    + " clientProtocol=" + request.protocolVersion());
        } catch (ClientReadyPayloads.MalformedReadyException malformed) {
            decision = admission.onReady(player.getUniqueId(), null, now);
        } catch (RuntimeException error) {
            plugin.getLogger().severe("CLIENT_GATE_INTERNAL_ERROR player=" + player.getName()
                    + " uuid=" + player.getUniqueId() + " error=" + error.getClass().getSimpleName());
            if (player.isOnline()) {
                player.kickPlayer(userMessage("INTERNAL_GATE_ERROR"));
            }
            return;
        }

        sendReadyAck(player, decision);
        if (decision.decision() == ClientReadyAdmission.Decision.ACCEPTED
                || decision.decision() == ClientReadyAdmission.Decision.DUPLICATE_ACCEPTED) {
            cancelAdmissionTimeout(player.getUniqueId());
        }
        switch (decision.decision()) {
            case ACCEPTED, DUPLICATE_ACCEPTED -> plugin.getLogger().info(
                    "CLIENT_GATE_ACCEPT player=" + player.getName()
                            + " uuid=" + player.getUniqueId()
                            + " attempt=" + attemptOf(decision)
                            + " clientVersion=" + versionOf(decision)
                            + " clientProtocol=" + protocolOf(decision)
                            + " requiredProtocol=" + ClientReadyAdmission.REQUIRED_PROTOCOL
                            + " readyPackets=" + packetsOf(decision)
                            + " elapsedMs=" + elapsedOf(decision, now));
            case PROTOCOL_MISMATCH, MALFORMED_READY, AFTER_TIMEOUT -> plugin.getLogger().info(
                    "CLIENT_GATE_REJECT reason=" + decision.reasonCode()
                            + " player=" + player.getName()
                            + " uuid=" + player.getUniqueId()
                            + " attempt=" + attemptOf(decision)
                            + " clientVersion=" + versionOf(decision)
                            + " clientProtocol=" + protocolOf(decision)
                            + " requiredProtocol=" + ClientReadyAdmission.REQUIRED_PROTOCOL
                            + " elapsedMs=" + elapsedOf(decision, now));
            default -> {
            }
        }
        if (decision.shouldKick() && player.isOnline()) {
            player.kickPlayer(userMessage(decision.reasonCode()));
        }
    }

    private void sendReadyAck(Player player, ClientReadyAdmission.ReadyDecision decision) {
        try {
            ClientReadyAdmission.ReadyAck ack = decision.ack();
            player.sendPluginMessage(plugin, ClientReadyPayloads.ACK_CHANNEL,
                    ClientReadyPayloads.encodeAck(ack.accepted(), ack.requiredProtocol(), ack.receivedProtocol(), ack.reason()));
            plugin.getLogger().fine("CLIENT_READY_ACK_SENT player=" + player.getName()
                    + " uuid=" + player.getUniqueId()
                    + " accepted=" + ack.accepted()
                    + " reason=" + ack.reason());
        } catch (RuntimeException error) {
            plugin.getLogger().severe("CLIENT_GATE_INTERNAL_ERROR player=" + player.getName()
                    + " uuid=" + player.getUniqueId() + " error=" + error.getClass().getSimpleName());
            if (player.isOnline()) {
                player.kickPlayer(userMessage("INTERNAL_GATE_ERROR"));
            }
        }
    }

    private void handleAdmissionTimeout(UUID playerId, UUID expectedAttemptId) {
        admissionTimeoutTasks.remove(playerId);
        ClientReadyAdmission.ReadyDecision decision = admission.onTimeout(playerId, expectedAttemptId, System.currentTimeMillis());
        if (decision.decision() == ClientReadyAdmission.Decision.STALE_ATTEMPT) {
            plugin.getLogger().fine("STALE_JOIN_ATTEMPT_IGNORED playerUuid=" + playerId
                    + " attempt=" + expectedAttemptId);
            return;
        }
        if (decision.decision() != ClientReadyAdmission.Decision.TIMEOUT) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        String name = player == null ? playerId.toString() : player.getName();
        plugin.getLogger().info("CLIENT_GATE_REJECT reason=CLIENT_READY_TIMEOUT player=" + name
                + " uuid=" + playerId
                + " attempt=" + expectedAttemptId
                + " waitedMs=" + admission.timeoutMillis());
        if (player != null && player.isOnline() && configService.clientGateRequired()) {
            player.kickPlayer(userMessage("CLIENT_READY_TIMEOUT"));
        }
    }

    private void cancelAdmissionTimeout(UUID playerId) {
        BukkitTask timeout = admissionTimeoutTasks.remove(playerId);
        if (timeout != null) {
            timeout.cancel();
        }
    }

    private void clearPendingAdmissions() {
        for (BukkitTask task : admissionTimeoutTasks.values()) {
            task.cancel();
        }
        admissionTimeoutTasks.clear();
        admission.clear();
    }

    private String admissionStatus(Player player) {
        if (player == null) {
            return "player-missing";
        }
        ClientReadyAdmission.PendingClientAdmission state = admission.snapshot(player.getUniqueId());
        if (state == null) {
            return configService.clientGateRequired() ? "not-pending" : "disabled";
        }
        return (state.accepted() ? "ACCEPTED" : "PENDING")
                + ", attempt=" + state.joinAttemptId()
                + ", clientVersion=" + (state.clientVersion().isBlank() ? "-" : state.clientVersion())
                + ", clientProtocol=" + (state.clientProtocol() == null ? "-" : state.clientProtocol())
                + ", requiredProtocol=" + ClientReadyAdmission.REQUIRED_PROTOCOL
                + ", readyPackets=" + state.readyPacketCount()
                + ", elapsedMs=" + Math.max(0L, System.currentTimeMillis() - state.startedAtMillis())
                + ", lastReadyAt=" + state.lastReadyAtMillis();
    }

    private boolean allowReadyMessage(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long nextAllowed = nextReadyMessageAt.getOrDefault(playerId, 0L);
        if (nextAllowed > now) {
            return false;
        }
        nextReadyMessageAt.put(playerId, now + READY_INBOUND_INTERVAL_MILLIS);
        return true;
    }

    private static String versionOf(ClientReadyAdmission.ReadyDecision decision) {
        return decision.state() == null || decision.state().clientVersion().isBlank() ? "-" : decision.state().clientVersion();
    }

    private static int protocolOf(ClientReadyAdmission.ReadyDecision decision) {
        return decision.state() == null || decision.state().clientProtocol() == null ? 0 : decision.state().clientProtocol();
    }

    private static int packetsOf(ClientReadyAdmission.ReadyDecision decision) {
        return decision.state() == null ? 0 : decision.state().readyPacketCount();
    }

    private static UUID attemptOf(ClientReadyAdmission.ReadyDecision decision) {
        return decision.state() == null ? null : decision.state().joinAttemptId();
    }

    private static long elapsedOf(ClientReadyAdmission.ReadyDecision decision, long now) {
        return decision.state() == null ? 0L : Math.max(0L, now - decision.state().startedAtMillis());
    }

    private static String userMessage(String reason) {
        return switch (reason) {
            case "PROTOCOL_MISMATCH" -> "Ваша версия CopiMineClient несовместима с сервером. Обновите CopiMineClient.\nКод: PROTOCOL_MISMATCH";
            case "MALFORMED_READY" -> "Не удалось прочитать ответ CopiMineClient. Обновите клиент.\nКод: MALFORMED_READY";
            case "CLIENT_READY_TIMEOUT" -> "Для игры на CopiMine нужен актуальный CopiMineClient. Установите или обновите сборку через CopiMine Launcher либо вручную.\nКод: CLIENT_READY_TIMEOUT";
            default -> "Не удалось проверить CopiMineClient из-за ошибки сервера. Сообщите администрации.\nКод: INTERNAL_GATE_ERROR";
        };
    }

    public boolean handleCommand(CommandSender sender, String[] args, java.util.function.BiConsumer<Player, String> fallbackTest) {
        if (!sender.hasPermission("copimine.narcotics.visuals") && !sender.hasPermission("copimine.narcotics.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет доступа.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/cmclient check <игрок>");
            sender.sendMessage(ChatColor.GOLD + "/cmclient visualtest <игрок> <effectId> [seconds] [intensity]");
            sender.sendMessage(ChatColor.GOLD + "/cmclient fallbacktest <игрок> <effectId> [seconds]");
            sender.sendMessage(ChatColor.GOLD + "/cmclient stop <игрок> [effectId]");
            sender.sendMessage(ChatColor.GOLD + "/cmclient clear <игрок>");
            sender.sendMessage(ChatColor.GOLD + "/cmclient routes <игрок>");
            sender.sendMessage(ChatColor.GOLD + "/cmclient sessions");
            sender.sendMessage(ChatColor.GOLD + "/cmclient require client <true|false>");
            sender.sendMessage(ChatColor.GOLD + "/cmclient debug <игрок>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("sessions".equals(sub)) {
            sender.sendMessage(ChatColor.GRAY + "Client bridge sessions: " + visuals.sessionsSummary());
            return true;
        }
        if ("require".equals(sub) && args.length >= 3 && "client".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("copimine.narcotics.admin")) {
                sender.sendMessage(ChatColor.RED + "Для изменения требования клиента нужны права администратора наркотиков.");
                return true;
            }
            boolean required = Boolean.parseBoolean(args[2]);
            configService.setRequireClientMod(required);
            sender.sendMessage(ChatColor.GREEN + "Режим CopiMineClient: " + required);
            if (required && configService.kickIfMissingClient()) {
                sender.sendMessage(ChatColor.YELLOW + "Автоматическое исключение отключено: сообщение канала не доказывает наличие мода у клиента.");
            }
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Недостаточно аргументов.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден.");
            return true;
        }
        if ("check".equals(sub)) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + ": " + statusFor(target));
            return true;
        }
        if ("routes".equals(sub)) {
            String routes = configService.visualEffectIds().stream()
                    .sorted()
                    .map(effectId -> effectId + "=" + routeHint(target, effectId))
                    .collect(Collectors.joining(", "));
            sender.sendMessage(ChatColor.GRAY + target.getName() + ": " + routes);
            return true;
        }
        if ("debug".equals(sub)) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + ": " + statusFor(target));
            sender.sendMessage(ChatColor.GRAY + "Route hints: " + configService.visualEffectIds().stream()
                    .sorted()
                    .map(effectId -> effectId + "=" + routeHint(target, effectId))
                    .collect(Collectors.joining(", ")));
            return true;
        }
        if ("clear".equals(sub)) {
            visuals.clearVisuals(target, "admin-clear");
            sender.sendMessage(ChatColor.GREEN + "Клиентские визуалы очищены: " + target.getName());
            return true;
        }
        if ("stop".equals(sub)) {
            String effectId = args.length >= 3 ? args[2].toUpperCase(Locale.ROOT) : "CHAOS";
            visuals.sendVisualStop(target, effectId, "admin-stop");
            sender.sendMessage(ChatColor.GREEN + "Остановка отправлена: " + target.getName() + " / " + effectId);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Нужно указать effectId.");
            return true;
        }
        String effectId = args[2].toUpperCase(Locale.ROOT);
        int seconds = 30;
        float intensity = 1.0F;
        if (args.length >= 4) {
            try {
                seconds = Math.max(1, Math.min(600, Integer.parseInt(args[3])));
            } catch (Exception error) {
                sender.sendMessage(ChatColor.RED + "Некорректная длительность.");
                return true;
            }
        }
        if (args.length >= 5) {
            try {
                intensity = ClientBridgePayloads.clampIntensity(Float.parseFloat(args[4]));
            } catch (Exception error) {
                sender.sendMessage(ChatColor.RED + "Некорректная intensity.");
                return true;
            }
        }
        if ("visualtest".equals(sub)) {
            if (!visuals.canUse(target, effectId)) {
                sender.sendMessage(ChatColor.YELLOW + "У игрока нет поддержки CopiMineClient для эффекта " + effectId + ".");
                return true;
            }
            visuals.sendVisualStart(
                    target,
                    effectId,
                    "",
                    seconds,
                    intensity,
                    1_500,
                    2_500,
                    "ADMIN_TEST",
                    (playerUuid, effect, fallbackSeconds, fallbackIntensity, source, reason) -> fallbackTest.accept(target, effect + ":" + fallbackSeconds),
                    (playerUuid, effect, source, reason) -> {
                    }
            );
            sender.sendMessage(ChatColor.GREEN + "Клиентский visual test отправлен: " + target.getName() + " / " + effectId);
            return true;
        }
        if ("fallbacktest".equals(sub)) {
            fallbackTest.accept(target, effectId + ":" + seconds);
            sender.sendMessage(ChatColor.GREEN + "Серверный fallback test отправлен: " + target.getName() + " / " + effectId);
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Неизвестная команда cmclient.");
        return true;
    }

    private boolean allowInboundMessage(Player player) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        long nextAllowed = nextInboundMessageAt.getOrDefault(playerUuid, 0L);
        if (nextAllowed > now) {
            return false;
        }
        nextInboundMessageAt.put(playerUuid, now + INBOUND_MESSAGE_INTERVAL_MILLIS);
        return true;
    }
}
