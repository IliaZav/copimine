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

import java.util.Locale;
import java.util.stream.Collectors;

public final class CopiMineClientBridge implements Listener, PluginMessageListener {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final ClientCapabilityService capabilities;
    private final ClientVisualEffectService visuals;

    public CopiMineClientBridge(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.capabilities = new ClientCapabilityService();
        this.visuals = new ClientVisualEffectService(plugin, capabilities);
        applyCapabilityTtl();
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            visuals.clearVisuals(player, "plugin-disable");
            visuals.forgetPlayer(player);
            capabilities.clear(player.getUniqueId());
        }
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        visuals.shutdown();
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
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
        return capabilities.describe(player) + ", visuals=" + visuals.playerSummary(player);
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
        if (!ClientBridgePayloads.CHANNEL.equalsIgnoreCase(channel) || !enabled()) {
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
        capabilities.clear(event.getPlayer().getUniqueId());
        if (!enabled() || !configService.requireClientMod() || !configService.kickIfMissingClient()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            if (!capabilities.hasCopiMineClient(player)) {
                player.kickPlayer(ChatColor.RED + "Для полной версии CopiMine нужен клиентский мод CopiMineClient. Установите мод из сборки сервера.");
            }
        }, Math.max(20L, configService.handshakeTimeoutSeconds() * 20L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visuals.forgetPlayer(event.getPlayer());
        capabilities.clear(event.getPlayer().getUniqueId());
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
            boolean required = Boolean.parseBoolean(args[2]);
            configService.setRequireClientMod(required);
            sender.sendMessage(ChatColor.GREEN + "Требование CopiMineClient: " + required);
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
                    seconds,
                    intensity,
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
}
