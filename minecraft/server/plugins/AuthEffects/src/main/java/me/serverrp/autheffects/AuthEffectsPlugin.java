package me.serverrp.autheffects;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthEffectsPlugin extends JavaPlugin implements Listener {

    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 10;
    private static final int SLOWNESS_AMPLIFIER = 4;
    private static final List<String> AUTH_EVENT_CLASSES = List.of(
            "fr.xephi.authme.events.LoginEvent",
            "fr.xephi.authme.events.RegisterEvent",
            "fr.xephi.authme.events.LogoutEvent"
    );

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ownSlowness = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PotionEffect> previousSlowness = new ConcurrentHashMap<>();
    private final Map<UUID, Long> previousSlownessCapturedAtMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ownSlownessAppliedAtMillis = new ConcurrentHashMap<>();
    private volatile Method authApiGetter;
    private volatile Method authApiIsAuthenticated;
    private volatile Object authApi;
    private volatile boolean authApiAvailable;
    private volatile boolean authApiUsesPlayerArgument;
    private volatile boolean loginHookRegistered;
    private volatile boolean logoutHookRegistered;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("AuthMe") == null
                || !Bukkit.getPluginManager().isPluginEnabled("AuthMe")) {
            getLogger().severe("AuthMe is required; disabling AuthEffects instead of running without authentication hooks.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        int registered = registerOptionalAuthEvents();
        if (registered == 0) {
            getLogger().severe("AuthMe was found, but no compatible authentication events were available; disabling AuthEffects.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        initialiseAuthApi();
        if (!authApiAvailable && (!loginHookRegistered || !logoutHookRegistered)) {
            getLogger().severe("AuthMeApi is unavailable and AuthEffects has no complete login hook/logout hook pair; disabling fail-closed.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::syncAuthStates, 20L, 20L);
        getLogger().info("AuthEffects enabled with AuthMe support.");
        getLogger().info("AuthEffects auth hooks registered: " + registered);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAuthEffect(player);
        }
        authenticated.clear();
        ownSlowness.clear();
        previousSlowness.clear();
        previousSlownessCapturedAtMillis.clear();
        ownSlownessAppliedAtMillis.clear();
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("AuthEffects disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            syncAuthState(player);
        }, 10L);
    }

    private void syncAuthStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncAuthState(player);
        }
    }

    private void syncAuthState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isAuthenticated(player)) {
            clearAuthEffect(player);
        } else {
            applyAuthEffect(player);
        }
    }

    private void handleAuthEvent(Event event) {
        if (event == null) {
            return;
        }
        Player player = extractPlayer(event);
        if (player == null) {
            return;
        }
        boolean logout = "LogoutEvent".equals(event.getClass().getSimpleName());
        Bukkit.getScheduler().runTask(this, () -> {
            if (logout) {
                authenticated.remove(player.getUniqueId());
                applyAuthEffect(player);
                return;
            }
            authenticated.add(player.getUniqueId());
            clearAuthEffect(player);
            playSuccessEffect(player);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        authenticated.remove(uuid);
        ownSlowness.remove(uuid);
        previousSlowness.remove(uuid);
        previousSlownessCapturedAtMillis.remove(uuid);
        ownSlownessAppliedAtMillis.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!blockUnauthenticated(event.getPlayer())) {
            return;
        }
        if (event.getTo() != null && (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blockUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (blockUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isAuthenticationCommand(event.getMessage())) {
            return;
        }
        if (blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && blockUnauthenticated(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player attacker
                && blockUnauthenticated(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player victim && blockUnauthenticated(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (blockUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blockUnauthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && blockUnauthenticated(player)) {
            event.setCancelled(true);
        }
    }

    private boolean blockUnauthenticated(Player player) {
        if (player == null || isAuthenticated(player)) {
            return false;
        }
        applyAuthEffect(player);
        return true;
    }

    private boolean isAuthenticationCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String command = raw.stripLeading().split("\\s+", 2)[0];
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        int colon = command.lastIndexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1);
        }
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "login", "l", "register", "reg", "changepassword", "cp", "captcha" -> true;
            default -> false;
        };
    }

    private void initialiseAuthApi() {
        try {
            org.bukkit.plugin.Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
            ClassLoader authMeLoader = authMe == null ? getClassLoader() : authMe.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", false, authMeLoader);
            authApiGetter = apiClass.getMethod("getInstance");
            try {
                authApiIsAuthenticated = apiClass.getMethod("isAuthenticated", Player.class);
                authApiUsesPlayerArgument = true;
            } catch (NoSuchMethodException currentAuthMeSignatureMissing) {
                // Keep compatibility with older AuthMe builds exposing the
                // legacy String-based API.
                authApiIsAuthenticated = apiClass.getMethod("isAuthenticated", String.class);
                authApiUsesPlayerArgument = false;
            }
            authApi = authApiGetter.invoke(null);
            authApiAvailable = authApi != null;
            getLogger().info("AuthEffects will verify authentication through AuthMeApi ("
                    + (authApiUsesPlayerArgument ? "Player" : "String") + " signature).");
        } catch (ReflectiveOperationException | LinkageError error) {
            authApiGetter = null;
            authApiIsAuthenticated = null;
            authApi = null;
            authApiAvailable = false;
            authApiUsesPlayerArgument = false;
            getLogger().warning("AuthMeApi is not available; authentication state will be learned from AuthMe events only.");
        }
    }

    private boolean isAuthenticated(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        // AuthMe's API can briefly lag behind its LoginEvent. Keep the
        // event-confirmed state authoritative for this session so the next
        // sync tick does not reapply the login lock.
        if (authenticated.contains(uuid)) {
            return true;
        }
        if (!authApiAvailable || authApiIsAuthenticated == null || authApi == null) {
            return false;
        }
        try {
            Object result = authApiUsesPlayerArgument
                    ? authApiIsAuthenticated.invoke(authApi, player)
                    : authApiIsAuthenticated.invoke(authApi, player.getName());
            if (Boolean.TRUE.equals(result)) {
                authenticated.add(uuid);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException error) {
            getLogger().fine("AuthMe authentication lookup failed for " + player.getName() + ": " + error.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private int registerOptionalAuthEvents() {
        int registered = 0;
        List<String> missing = new ArrayList<>();
        EventExecutor executor = (listener, event) -> handleAuthEvent(event);
        org.bukkit.plugin.Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        ClassLoader loader = authMe == null ? getClassLoader() : authMe.getClass().getClassLoader();
        for (String className : AUTH_EVENT_CLASSES) {
            try {
                Class<?> raw = Class.forName(className, false, loader);
                if (!Event.class.isAssignableFrom(raw)) {
                    getLogger().warning("Skipping non-Bukkit auth event class: " + className);
                    continue;
                }
                Bukkit.getPluginManager().registerEvent(
                        (Class<? extends Event>) raw,
                        this,
                        EventPriority.MONITOR,
                        executor,
                        this,
                        true
                );
                registered++;
                if (className.endsWith("LoginEvent") || className.endsWith("RegisterEvent")) {
                    loginHookRegistered = true;
                }
                if (className.endsWith("LogoutEvent")) {
                    logoutHookRegistered = true;
                }
            } catch (ClassNotFoundException ignored) {
                missing.add(className);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Failed to register auth event hook " + className + ": " + ex.getMessage());
            } catch (LinkageError ex) {
                missing.add(className);
                getLogger().warning("Auth event hook is not loadable " + className + ": " + ex.getMessage());
            }
        }
        if (!missing.isEmpty()) {
            getLogger().info("Optional auth hooks not present: " + String.join(", ", missing));
        }
        return registered;
    }

    private void applyAuthEffect(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean firstApplication = ownSlowness.add(uuid);
        if (firstApplication) {
            PotionEffect existing = player.getPotionEffect(PotionEffectType.SLOWNESS);
            if (existing != null && !isAuthLockEffect(existing)) {
                previousSlowness.putIfAbsent(uuid, existing);
                previousSlownessCapturedAtMillis.putIfAbsent(uuid, System.currentTimeMillis());
            }
        }
        PotionEffect current = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (firstApplication || current == null || current.getDuration() < 100) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    EFFECT_DURATION_TICKS,
                    SLOWNESS_AMPLIFIER,
                    false,
                    false,
                    true
            ));
            ownSlownessAppliedAtMillis.put(uuid, System.currentTimeMillis());
        }

        if (firstApplication) {
            player.sendTitle(
                    color("&6CopiMine"),
                    color("&7Войдите: &e/login &7или &e/register"),
                    10,
                    80,
                    20
            );
        }
    }

    private void clearAuthEffect(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean tracked = ownSlowness.remove(uuid);
        PotionEffect current = player.getPotionEffect(PotionEffectType.SLOWNESS);
        Long appliedAt = ownSlownessAppliedAtMillis.remove(uuid);
        if (!tracked && !isAuthLockEffect(current)) {
            previousSlowness.remove(uuid);
            previousSlownessCapturedAtMillis.remove(uuid);
            return;
        }
        boolean trackedAuthLock = current != null
                && appliedAt != null
                && current.getAmplifier() == SLOWNESS_AMPLIFIER
                && isOurSlownessDuration(current.getDuration(), appliedAt);
        boolean staleAuthLock = isAuthLockEffect(current) && appliedAt == null;
        if (trackedAuthLock || staleAuthLock) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        PotionEffect previous = previousSlowness.remove(uuid);
        Long capturedAt = previousSlownessCapturedAtMillis.remove(uuid);
        if (previous != null && player.getPotionEffect(PotionEffectType.SLOWNESS) == null) {
            long elapsedTicks = capturedAt == null
                    ? 0L
                    : Math.max(0L, (System.currentTimeMillis() - capturedAt) / 50L);
            int remaining = (int) Math.max(0L, previous.getDuration() - elapsedTicks);
            if (remaining > 0) {
                player.addPotionEffect(new PotionEffect(
                        previous.getType(),
                        remaining,
                        previous.getAmplifier(),
                        previous.isAmbient(),
                        previous.hasParticles(),
                        previous.hasIcon()
                ));
            }
        }
    }

    private boolean isOurSlownessDuration(int durationTicks, long appliedAtMillis) {
        long elapsedTicks = Math.max(0L, (System.currentTimeMillis() - appliedAtMillis) / 50L);
        long expected = Math.max(0L, EFFECT_DURATION_TICKS - elapsedTicks);
        // Allow scheduler jitter, but do not remove an unrelated slowness
        // effect that happens to share our amplifier.
        return Math.abs(durationTicks - expected) <= 40L;
    }

    private boolean isAuthLockEffect(PotionEffect effect) {
        return effect != null
                && PotionEffectType.SLOWNESS.equals(effect.getType())
                && effect.getAmplifier() == SLOWNESS_AMPLIFIER
                && !effect.isAmbient()
                && !effect.hasParticles()
                && effect.hasIcon();
    }

    private void playSuccessEffect(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendTitle(
                color("&aУспешный вход"),
                color("&7Добро пожаловать на &6CopiMine"),
                10,
                50,
                15
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                0.7F,
                1.4F
        );
    }

    private Player extractPlayer(Event event) {
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Object value = getPlayer.invoke(event);
            return value instanceof Player player ? player : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
