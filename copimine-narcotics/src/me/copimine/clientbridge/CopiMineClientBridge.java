package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Locale;

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
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        for (Player player : Bukkit.getOnlinePlayers()) {
            capabilities.clear(player.getUniqueId());
        }
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
        return capabilities.describe(player);
    }

    public String routeHint(Player player, String effectId) {
        return capabilities.routeHint(player, effectId);
    }

    private void applyCapabilityTtl() {
        long ttlMillis = Math.max(30L, configService.handshakeTimeoutSeconds() * 3L) * 1000L;
        capabilities.setTtlMillis(ttlMillis);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ClientBridgePayloads.CHANNEL.equalsIgnoreCase(channel) || !enabled()) {
            return;
        }
        try {
            ClientCapabilityState state = ClientBridgePayloads.decodeHello(message);
            capabilities.update(player, state);
        } catch (Exception error) {
            plugin.getLogger().warning("Invalid CopiMineClient handshake from " + player.getName() + ": " + error.getMessage());
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
                player.kickPlayer(ChatColor.RED + "Для полной версии CopiMine нужен клиентский мод CopiMineClient. Установи мод из сборки сервера.");
            }
        }, Math.max(20L, configService.handshakeTimeoutSeconds() * 20L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        capabilities.clear(event.getPlayer().getUniqueId());
    }

    public boolean handleCommand(CommandSender sender, String[] args, java.util.function.BiConsumer<Player, String> fallbackTest) {
        if (!sender.hasPermission("copimine.narcotics.visuals") && !sender.hasPermission("copimine.narcotics.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет доступа.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/cmclient check <игрок>");
            sender.sendMessage(ChatColor.GOLD + "/cmclient visualtest <игрок> <effectId> [seconds]");
            sender.sendMessage(ChatColor.GOLD + "/cmclient fallbacktest <игрок> <effectId> [seconds]");
            sender.sendMessage(ChatColor.GOLD + "/cmclient require client <true|false>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
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
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Нужно указать effectId.");
            return true;
        }
        String effectId = args[2].toUpperCase(Locale.ROOT);
        int seconds = 30;
        if (args.length >= 4) {
            try {
                seconds = Math.max(1, Math.min(600, Integer.parseInt(args[3])));
            } catch (Exception error) {
                sender.sendMessage(ChatColor.RED + "Некорректная длительность.");
                return true;
            }
        }
        if ("visualtest".equals(sub)) {
            if (!visuals.canUse(target, effectId)) {
                sender.sendMessage(ChatColor.YELLOW + "У игрока нет поддержки CopiMineClient для эффекта " + effectId + ".");
                return true;
            }
            visuals.sendVisualStart(target, effectId, seconds, 1.0F);
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
