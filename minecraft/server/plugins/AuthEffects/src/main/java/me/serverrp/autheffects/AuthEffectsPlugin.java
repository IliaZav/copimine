package me.serverrp.autheffects;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthEffectsPlugin extends JavaPlugin implements Listener {

    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 10;
    private static final int SLOWNESS_AMPLIFIER = 4;
    private static final List<String> AUTH_EVENT_CLASSES = List.of(
            "fr.xephi.authme.events.LoginEvent",
            "fr.xephi.authme.events.RegisterEvent",
            "com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent",
            "com.nickuc.login.api.event.bukkit.auth.RegisterEvent"
    );

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        int registered = registerOptionalAuthEvents();
        getLogger().info("AuthEffects enabled with nLogin/AuthMe compatibility support.");
        getLogger().info("AuthEffects auth hooks registered: " + registered);
    }

    @Override
    public void onDisable() {
        authenticated.clear();
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("AuthEffects disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || authenticated.contains(player.getUniqueId())) {
                return;
            }
            applyAuthEffect(player);
        }, 10L);
    }

    private void handleAuthEvent(Event event) {
        if (event == null) {
            return;
        }
        Player player = extractPlayer(event);
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            authenticated.add(player.getUniqueId());
            clearAuthEffect(player);
            playSuccessEffect(player);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    @SuppressWarnings("unchecked")
    private int registerOptionalAuthEvents() {
        int registered = 0;
        List<String> missing = new ArrayList<>();
        EventExecutor executor = (listener, event) -> handleAuthEvent(event);
        ClassLoader loader = getClassLoader();
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
            } catch (ClassNotFoundException ignored) {
                missing.add(className);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Failed to register auth event hook " + className + ": " + ex.getMessage());
            }
        }
        if (!missing.isEmpty()) {
            getLogger().info("Optional auth hooks not present: " + String.join(", ", missing));
        }
        return registered;
    }

    private void applyAuthEffect(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                EFFECT_DURATION_TICKS,
                SLOWNESS_AMPLIFIER,
                false,
                false,
                true
        ));

        player.sendTitle(
                color("&6CopiMine"),
                color("&7Войдите: &e/login &7или &e/register"),
                10,
                80,
                20
        );
    }

    private void clearAuthEffect(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.SLOWNESS);
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

    private Player extractPlayer(org.bukkit.event.Event event) {
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
