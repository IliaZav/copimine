package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.protocol.ClientModHandshakePolicy;
import me.copimine.protocol.ClientModSessionRegistry;
import me.copimine.protocol.BridgeRequestCodec;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.InboundBridgeRequestRateLimiter;
import me.copimine.protocol.PendingClientCommandPolicy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CopiMineClientBridge implements Listener, PluginMessageListener {
    private static final int MAX_INBOUND_MESSAGE_BYTES = 4_096;
    private static final int MAX_V4_INBOUND_MESSAGE_BYTES = 1_024;
    private static final long INBOUND_MESSAGE_INTERVAL_MILLIS = 50L;
    private final CopiMineNarcotics plugin;
    private final ClientModSessionRegistryService sessionRegistry;
    private NarcoticsConfigService configService;
    private final ClientCapabilityService capabilities;
    private final ClientVisualEffectService visuals;
    private final NarcoticsBridgeV4Sender bridgeV4Sender;
    private BukkitTask bridgeV4TickTask;
    private final InboundBridgeRequestRateLimiter v4InboundRateLimiter = new InboundBridgeRequestRateLimiter();
    private final Map<UUID, Long> nextInboundMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, PendingClientHandshake> pendingHandshakes = new ConcurrentHashMap<>();
    private final Map<UUID, VerifiedClientMod> verifiedClientMods = new ConcurrentHashMap<>();
    private String lastRequiredClientModVersion;

    private record PendingClientHandshake(String challengeId, long expiresAtMillis) {
    }

    private record VerifiedClientMod(String sessionId, String modId, String version) {
    }

    public CopiMineClientBridge(CopiMineNarcotics plugin, NarcoticsConfigService configService,
                                ClientModSessionRegistryService sessionRegistry) {
        this.plugin = plugin;
        this.configService = configService;
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry");
        this.capabilities = new ClientCapabilityService();
        BridgeSigner signer = Bukkit.getServicesManager().load(BridgeSigner.class);
        this.bridgeV4Sender = new NarcoticsBridgeV4Sender(sessionRegistry,
                signer == null ? BridgeSigner.unavailable() : signer,
                (playerUuid, payload) -> {
                    Player recipient = Bukkit.getPlayer(playerUuid);
                    if (recipient != null && recipient.isOnline()) {
                        recipient.sendPluginMessage(plugin, ClientBridgePayloads.V4_CHANNEL, payload);
                    }
                });
        this.visuals = new ClientVisualEffectService(plugin, capabilities, bridgeV4Sender);
        this.lastRequiredClientModVersion = configService.requiredClientModVersion();
        this.sessionRegistry.setFreshChallengeRequester(this::dispatchFreshChallenge);
        applyCapabilityTtl();
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ClientBridgePayloads.V4_CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ClientBridgePayloads.V4_CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (bridgeV4TickTask == null) {
            bridgeV4TickTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, bridgeV4Sender::tick, 1L, 5L);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sessionRegistry.openConnection(player.getUniqueId());
            beginClientModHandshake(player);
        }
    }

    public void shutdown() {
        if (bridgeV4TickTask != null) {
            bridgeV4TickTask.cancel();
            bridgeV4TickTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            visuals.clearVisuals(player, "plugin-disable");
            visuals.forgetPlayer(player);
            capabilities.clear(player.getUniqueId());
        }
        bridgeV4Sender.close();
        v4InboundRateLimiter.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientBridgePayloads.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientBridgePayloads.CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ClientBridgePayloads.V4_CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ClientBridgePayloads.V4_CHANNEL);
        nextInboundMessageAt.clear();
        pendingHandshakes.clear();
        verifiedClientMods.clear();
        visuals.shutdown();
    }

    public void reload(NarcoticsConfigService configService) {
        String nextRequiredVersion = configService.requiredClientModVersion();
        boolean requiredVersionChanged = !Objects.equals(lastRequiredClientModVersion, nextRequiredVersion);
        this.configService = configService;
        lastRequiredClientModVersion = nextRequiredVersion;
        applyCapabilityTtl();
        if (requiredVersionChanged) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sessionRegistry.invalidate(player.getUniqueId(),
                        ClientModSessionRegistry.InvalidationReason.VERSION_CHANGED);
                beginClientModHandshake(player);
            }
        }
    }

    public ClientCapabilityService capabilities() {
        return capabilities;
    }

    public ClientVisualEffectService visuals() {
        return visuals;
    }

    public NarcoticsBridgeV4Sender bridgeV4Sender() {
        return bridgeV4Sender;
    }

    public boolean enabled() {
        return configService.clientBridgeEnabled();
    }

    public boolean requireClientMod() {
        return true;
    }

    public String statusFor(Player player) {
        VerifiedClientMod verified = verifiedClientMods.get(player.getUniqueId());
        String modStatus = verified == null
                ? (pendingHandshakes.containsKey(player.getUniqueId()) ? "mod=pending" : "mod=missing")
                : "mod=" + verified.modId() + "@" + verified.version();
        return modStatus + ", " + capabilities.describe(player) + ", visuals=" + visuals.playerSummary(player);
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
        if (ClientBridgePayloads.V4_CHANNEL.equalsIgnoreCase(channel)) {
            if (!enabled() || player == null || message == null
                    || message.length > MAX_V4_INBOUND_MESSAGE_BYTES
                    || !BridgeRequestCodec.classifyRecipient(message).includesNarcotics()) {
                return;
            }
            boolean admitted = v4InboundRateLimiter.allow(player.getUniqueId());
            bridgeV4Sender.onRequest(player, message, admitted);
            if (admitted) {
                visuals.onBridgeStateChanged(player);
            }
            return;
        }
        if (!ClientBridgePayloads.CHANNEL.equalsIgnoreCase(channel) || !enabled()) {
            return;
        }
        if (message == null || message.length > MAX_INBOUND_MESSAGE_BYTES) {
            capabilities.reportProblem(player, "payload-too-large");
            if (pendingHandshakes.containsKey(player.getUniqueId())) {
                rejectClient(player, "bad-payload");
            }
            return;
        }
        if (!allowInboundMessage(player)) {
            return;
        }
        try {
            ClientBridgePayloads.Message payload = ClientBridgePayloads.decode(message);
            switch (payload.type()) {
                case ClientBridgePayloads.HELLO -> handleClientModHello(player, payload);
                case ClientBridgePayloads.CAPABILITIES_UPDATE -> {
                    if (!isVerifiedClientMessage(player, payload) || !hasExpectedClientIdentity(payload)) {
                        capabilities.reportProblem(player, "unverified-client-mod-message");
                        return;
                    }
                    capabilities.update(player, ClientCapabilityState.fromMessage(payload));
                    if (player.isOnline()) {
                        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodePing(0L, payload.sessionId()));
                    }
                }
                case ClientBridgePayloads.HEARTBEAT -> {
                    if (isVerifiedClientMessage(player, payload)) {
                        capabilities.touch(player, payload.sessionId());
                    }
                }
                case ClientBridgePayloads.VISUAL_ACK, ClientBridgePayloads.VISUAL_FINISHED, ClientBridgePayloads.VISUAL_ERROR -> {
                    ClientBridgePayloads.Message strictReport = ClientBridgePayloads.decodeStrictVisualReport(message);
                    if (isVerifiedClientMessage(player, strictReport)
                            && capabilities.touch(player, strictReport.sessionId())) {
                        visuals.handleMessage(player, strictReport);
                    }
                }
                default -> capabilities.reportProblem(player, "unsupported-message-type:" + payload.type());
            }
        } catch (Exception error) {
            capabilities.reportProblem(player, error.getMessage());
            plugin.getLogger().warning("Invalid CopiMineClient bridge payload from " + player.getName() + ".");
            if (pendingHandshakes.containsKey(player.getUniqueId())) {
                rejectClient(player, "invalid-handshake-payload");
            }
        }
    }

    private void handleClientModHello(Player player, ClientBridgePayloads.Message payload) {
        UUID playerId = player.getUniqueId();
        PendingClientHandshake pending = pendingHandshakes.get(playerId);
        if (pending == null) {
            if (isVerifiedClientMessage(player, payload) && hasExpectedClientIdentity(payload)) {
                player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL,
                        ClientBridgePayloads.encodePing(0L, payload.sessionId()));
            } else {
                capabilities.reportProblem(player, "unsolicited-client-mod-hello");
            }
            return;
        }

        boolean accepted = sessionRegistry.acceptChallengeResponse(
                playerId,
                pending.challengeId(),
                payload.sessionId(),
                pending.expiresAtMillis(),
                System.currentTimeMillis(),
                payload.mode(),
                payload.clientVersion(),
                payload.protocol());
        if (!accepted || !pendingHandshakes.remove(playerId, pending)) {
            rejectClient(player, "missing-or-incompatible-client-mod");
            return;
        }

        verifiedClientMods.put(playerId, new VerifiedClientMod(
                pending.challengeId(),
                ClientModHandshakePolicy.MOD_ID,
                payload.clientVersion()
        ));
        capabilities.update(player, ClientCapabilityState.fromMessage(payload));
        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL,
                ClientBridgePayloads.encodePing(0L, payload.sessionId()));
        plugin.getLogger().info("Accepted CopiMineClient " + payload.clientVersion() + " for " + player.getName());
    }

    private boolean isVerifiedClientMessage(Player player, ClientBridgePayloads.Message payload) {
        VerifiedClientMod verified = verifiedClientMods.get(player.getUniqueId());
        return verified != null
                && verified.sessionId().equals(payload.sessionId())
                && sessionRegistry.current(player.getUniqueId())
                        .map(session -> session.sessionUuid().toString().equals(payload.sessionId()))
                        .orElse(false);
    }

    private boolean hasExpectedClientIdentity(ClientBridgePayloads.Message payload) {
        return ClientModHandshakePolicy.MOD_ID.equals(payload.mode())
                && configService.requiredClientModVersion().equals(payload.clientVersion());
    }

    private void rejectClient(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        sessionRegistry.invalidate(playerId, ClientModSessionRegistry.InvalidationReason.REJECTED);
        pendingHandshakes.remove(playerId);
        verifiedClientMods.remove(playerId);
        capabilities.clear(playerId);
        visuals.forgetPlayer(playerId, "mod-check-failed");
        if (player.isOnline()) {
            plugin.getLogger().info("Rejected " + player.getName() + " during CopiMineClient check: " + reason);
            player.kickPlayer("Для входа требуется CopiMineClient версии "
                    + configService.requiredClientModVersion()
                    + ". Установите нужную версию мода для Minecraft 26.3.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sessionRegistry.openConnection(event.getPlayer().getUniqueId());
        beginClientModHandshake(event.getPlayer());
    }

    private void dispatchFreshChallenge(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            beginClientModHandshake(player);
        }
    }

    private void beginClientModHandshake(Player player) {
        UUID playerId = player.getUniqueId();
        visuals.clearVisuals(player, "client-mod-check");
        visuals.forgetPlayer(player, "client-mod-check");
        capabilities.clear(playerId);
        nextInboundMessageAt.remove(playerId);
        v4InboundRateLimiter.remove(playerId);
        verifiedClientMods.remove(playerId);
        if (!enabled()) {
            rejectClient(player, "client-bridge-disabled");
            return;
        }

        long expiresAt = System.currentTimeMillis() + configService.handshakeTimeoutSeconds() * 1_000L;
        PendingClientHandshake pending = new PendingClientHandshake(UUID.randomUUID().toString(), expiresAt);
        pendingHandshakes.put(playerId, pending);
        sessionRegistry.beginChallenge(playerId, UUID.fromString(pending.challengeId()),
                pending.expiresAtMillis(), configService.requiredClientModVersion());
        long timeoutTicks = configService.handshakeTimeoutSeconds() * 20L;
        scheduleClientModChallenge(player, playerId, pending, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && pendingHandshakes.get(playerId) == pending) {
                boolean channelRegistered = player.getListeningPluginChannels().contains(ClientBridgePayloads.CHANNEL);
                rejectClient(player, channelRegistered
                        ? "client-mod-timeout"
                        : "client-mod-channel-not-registered");
            }
        }, timeoutTicks);
    }

    private void scheduleClientModChallenge(Player player, UUID playerId,
                                            PendingClientHandshake pending, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || pendingHandshakes.get(playerId) != pending) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean channelRegistered = player.getListeningPluginChannels().contains(ClientBridgePayloads.CHANNEL);
            if (ClientModHandshakePolicy.shouldSendChallenge(
                    true, channelRegistered, pending.expiresAtMillis(), now)) {
                player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL,
                        ClientBridgePayloads.encodeClientModChallenge(
                                pending.challengeId(), configService.requiredClientModVersion()));
                plugin.getLogger().fine("Sent CopiMineClient challenge to " + player.getName());
                return;
            }
            if (ClientModHandshakePolicy.shouldWaitForClientChannel(
                    true, channelRegistered, pending.expiresAtMillis(), now)) {
                scheduleClientModChallenge(player, playerId, pending, 4L);
            }
        }, delayTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visuals.forgetPlayer(event.getPlayer());
        UUID playerId = event.getPlayer().getUniqueId();
        sessionRegistry.closeConnection(playerId);
        capabilities.clear(playerId);
        pendingHandshakes.remove(playerId);
        verifiedClientMods.remove(playerId);
        nextInboundMessageAt.remove(playerId);
        v4InboundRateLimiter.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        if (!isPendingClient(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfPending(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingCommand(PlayerCommandPreprocessEvent event) {
        if (!isPendingClient(event.getPlayer()) || isAllowedAuthenticationCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Дождитесь проверки CopiMineClient или используйте /login и /register.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingChat(AsyncPlayerChatEvent event) {
        if (isPendingClient(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingBlockBreak(BlockBreakEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (remover instanceof Player player && isPendingClient(player)) {
            event.setCancelled(true);
        } else if (remover instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && isPendingClient(shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingBlockPlace(BlockPlaceEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingInteract(PlayerInteractEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingEntityInteract(PlayerInteractEntityEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingEntityInteractAt(PlayerInteractAtEntityEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && isPendingClient(victim)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            Entity damager = damageByEntity.getDamager();
            if (damager instanceof Player player && isPendingClient(player)) {
                event.setCancelled(true);
            } else if (damager instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player player
                    && isPendingClient(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && isPendingClient(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingItemConsume(PlayerItemConsumeEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingFish(PlayerFishEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingShear(PlayerShearEntityEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingLecternBookTake(PlayerTakeLecternBookEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingBookEdit(PlayerEditBookEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingBucketFill(PlayerBucketFillEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingDrop(PlayerDropItemEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isPendingClient(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isPendingClient(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isPendingClient(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingHandSwap(PlayerSwapHandItemsEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    private boolean isPendingClient(Player player) {
        return player != null && pendingHandshakes.containsKey(player.getUniqueId());
    }

    private void cancelIfPending(Player player, Cancellable event) {
        if (isPendingClient(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isAllowedAuthenticationCommand(String input) {
        return PendingClientCommandPolicy.allowsAuthenticationCommand(input);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        visuals.forgetPlayerPreservingSessionSequence(event.getEntity());
        visuals.clearVisuals(event.getEntity(), "death");
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
            sender.sendMessage(ChatColor.GOLD + "/cmclient require client");
            sender.sendMessage(ChatColor.GOLD + "/cmclient debug <игрок>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("sessions".equals(sub)) {
            sender.sendMessage(ChatColor.GRAY + "Client bridge sessions: " + visuals.sessionsSummary());
            return true;
        }
        if ("require".equals(sub) && args.length >= 2 && "client".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("copimine.narcotics.admin")) {
                sender.sendMessage(ChatColor.RED + "Для просмотра требования клиента нужны права администратора наркотиков.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "CopiMineClient обязателен; точная версия: "
                    + configService.requiredClientModVersion());
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
