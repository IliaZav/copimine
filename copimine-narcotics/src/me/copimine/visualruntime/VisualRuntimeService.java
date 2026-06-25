package me.copimine.visualruntime;

import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualRuntimeService {
    public enum VisualRoute {
        DISABLED,
        CLIENT_MOD_VISUAL,
        SERVER_RESOURCE_PACK_OVERLAY,
        SERVER_PARTICLE_FALLBACK
    }

    private static final Map<String, String> GLYPHS = Map.of(
            "DESATURATE", "\uE100",
            "COLOR_CONVOLVE", "\uE101",
            "SCAN_PINCUSHION", "\uE102",
            "GREEN_NOISE", "\uE103",
            "INVERT", "\uE104",
            "WOBBLE", "\uE105",
            "BLOBS", "\uE106",
            "PENCIL", "\uE107",
            "CHAOS", "\uE108"
    );

    private final CopiMineNarcotics plugin;
    private final CopiMineClientBridge clientBridge;
    private NarcoticsConfigService configService;
    private final Map<UUID, VisualSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cleanupTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> resourcePackReady = new ConcurrentHashMap<>();

    public VisualRuntimeService(CopiMineNarcotics plugin, NarcoticsConfigService configService, CopiMineClientBridge clientBridge) {
        this.plugin = plugin;
        this.configService = configService;
        this.clientBridge = clientBridge;
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
        clientBridge.reload(configService);
    }

    public void markResourcePackReady(Player player, boolean ready) {
        if (player != null) {
            resourcePackReady.put(player.getUniqueId(), ready);
        }
    }

    public void clearTracking(Player player) {
        if (player != null) {
            resourcePackReady.remove(player.getUniqueId());
        }
    }

    public void apply(Player player, String effectId, int durationSeconds, boolean overdose) {
        applyInternal(player, effectId, durationSeconds, overdose, false, false);
    }

    public void applyServerFallbackTest(Player player, String effectId, int durationSeconds) {
        applyInternal(player, effectId, durationSeconds, false, true, true);
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        sessions.remove(player.getUniqueId());
        Integer taskId = cleanupTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        clientBridge.visuals().clearVisuals(player);
        clearServerVisualSurface(player);
    }

    public boolean hasSession(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    public boolean supportsServerOverlayRuntime() {
        return detectServerOverlaySupport();
    }

    public boolean supportsServerParticleFallback() {
        return configService.allowServerParticleFallback();
    }

    public boolean supportsOverlayRuntime() {
        return supportsServerOverlayRuntime();
    }

    public boolean supportsShaderRuntime() {
        return clientBridge.enabled() && configService.allowClientModVisuals() && hasAllShaderProfiles();
    }

    public String serverOverlaySupportReason() {
        if (!configService.allowServerResourcePackOverlay()) {
            return "server overlay mode disabled in config";
        }
        if (!configService.serverOverlayUseTitles()) {
            return "server overlay title glyph route disabled in config";
        }
        if (!Files.isRegularFile(projectRoot().resolve("resourcepacks").resolve("src").resolve("assets").resolve("copimine").resolve("font").resolve("narcotics_overlay.json"))) {
            return "overlay font manifest missing";
        }
        if (!hasAllOverlayAssets()) {
            return "overlay texture assets missing";
        }
        if (!manifestFlag("server_overlay_supported")) {
            return "overlay runtime disabled by visuals manifest";
        }
        return "supported via title glyph fallback; may temporarily override other titles";
    }

    public String clientVisualSupportReason(Player player, String effectId) {
        if (!clientBridge.enabled()) {
            return "client bridge disabled";
        }
        if (!configService.allowClientModVisuals()) {
            return "client visuals disabled in config";
        }
        return clientBridge.routeHint(player, effectId);
    }

    public String shaderSupportReason() {
        return "Paper server does not force Iris/OptiFine shaders. Shader descriptors remain client-mod/profile documentation only.";
    }

    public String overlaySupportReason() {
        return serverOverlaySupportReason();
    }

    public VisualRoute resolvedRouteFor(Player player, String effectId) {
        return resolveRoute(player, normalizeEffectId(effectId), false);
    }

    public String resolvedModeFor(String effectId) {
        return resolvedRouteFor(null, effectId).name();
    }

    public String sessionSummary(UUID playerUuid) {
        VisualSession session = sessions.get(playerUuid);
        if (session == null) {
            return "нет";
        }
        long secondsLeft = Math.max(0L, (session.untilMillis() - System.currentTimeMillis()) / 1000L);
        return session.effectId() + " / " + session.route().name() + " / " + secondsLeft + "с";
    }

    private void applyInternal(Player player, String effectId, int durationSeconds, boolean overdose, boolean ignoreGate, boolean forceServerRoute) {
        if (player == null) {
            return;
        }
        String normalized = normalizeEffectId(effectId);
        if (!ignoreGate && (!configService.visualsEnabled() || !configService.isVisualEffectEnabled(normalized))) {
            clear(player);
            return;
        }
        VisualRoute route = forceServerRoute ? forcedServerRoute(player, normalized) : resolveRoute(player, normalized, ignoreGate);
        Integer previousTask = cleanupTasks.remove(player.getUniqueId());
        if (previousTask != null) {
            plugin.getServer().getScheduler().cancelTask(previousTask);
        }
        clear(player);
        if (route == VisualRoute.DISABLED) {
            return;
        }
        sessions.put(player.getUniqueId(), new VisualSession(normalized, route, System.currentTimeMillis() + (durationSeconds * 1000L), overdose));
        switch (route) {
            case CLIENT_MOD_VISUAL -> clientBridge.visuals().sendVisualStart(player, normalized, durationSeconds, overdose ? 1.25F : 1.0F);
            case SERVER_RESOURCE_PACK_OVERLAY -> {
                applyServerOverlay(player, normalized, durationSeconds, overdose);
                applyServerFallback(player, normalized, durationSeconds, overdose);
            }
            case SERVER_PARTICLE_FALLBACK -> applyServerFallback(player, normalized, durationSeconds, overdose);
            case DISABLED -> {
            }
        }
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            VisualSession session = sessions.get(player.getUniqueId());
            if (session != null && session.untilMillis() <= System.currentTimeMillis()) {
                clear(player);
            }
        }, Math.max(20L, durationSeconds * 20L)).getTaskId();
        cleanupTasks.put(player.getUniqueId(), taskId);
    }

    private VisualRoute resolveRoute(Player player, String effectId, boolean ignoreGate) {
        if (!ignoreGate && (!configService.visualsEnabled() || !configService.isVisualEffectEnabled(effectId))) {
            return VisualRoute.DISABLED;
        }
        NarcoticsConfigService.VisualMode configured = configService.visualMode();
        return switch (configured) {
            case CLIENT_MOD -> firstAvailable(player, effectId, true, true);
            case SERVER_OVERLAY -> firstAvailable(player, effectId, false, true);
            case SERVER_FALLBACK -> configService.allowServerParticleFallback() && configService.fallbackToParticles()
                    ? VisualRoute.SERVER_PARTICLE_FALLBACK : VisualRoute.DISABLED;
            case AUTO -> firstAvailable(player, effectId, configService.preferClientVisuals(), configService.fallbackToServerOverlay());
        };
    }

    private VisualRoute firstAvailable(Player player, String effectId, boolean allowClientFirst, boolean allowServerOverlay) {
        if (allowClientFirst && clientRouteAvailable(player, effectId)) {
            return VisualRoute.CLIENT_MOD_VISUAL;
        }
        if (allowServerOverlay && overlayRouteAvailable(player)) {
            return VisualRoute.SERVER_RESOURCE_PACK_OVERLAY;
        }
        if (configService.allowServerParticleFallback() && configService.fallbackToParticles()) {
            return VisualRoute.SERVER_PARTICLE_FALLBACK;
        }
        if (!allowClientFirst && clientRouteAvailable(player, effectId)) {
            return VisualRoute.CLIENT_MOD_VISUAL;
        }
        return VisualRoute.DISABLED;
    }

    private VisualRoute forcedServerRoute(Player player, String effectId) {
        if (overlayRouteAvailable(player)) {
            return VisualRoute.SERVER_RESOURCE_PACK_OVERLAY;
        }
        return configService.allowServerParticleFallback() ? VisualRoute.SERVER_PARTICLE_FALLBACK : VisualRoute.DISABLED;
    }

    private boolean clientRouteAvailable(Player player, String effectId) {
        if (player == null) {
            return false;
        }
        return clientBridge.enabled()
                && configService.allowClientModVisuals()
                && clientBridge.visuals().canUse(player, effectId);
    }

    private boolean overlayRouteAvailable(Player player) {
        if (player == null) {
            return false;
        }
        return configService.allowServerResourcePackOverlay()
                && configService.fallbackToServerOverlay()
                && detectServerOverlaySupport()
                && resourcePackReady.getOrDefault(player.getUniqueId(), false);
    }

    private boolean detectServerOverlaySupport() {
        if (!configService.allowServerResourcePackOverlay()) {
            return false;
        }
        Path fontManifest = projectRoot().resolve("resourcepacks").resolve("src").resolve("assets").resolve("copimine").resolve("font").resolve("narcotics_overlay.json");
        return Files.isRegularFile(fontManifest) && hasAllOverlayAssets() && manifestFlag("server_overlay_supported");
    }

    private boolean manifestFlag(String key) {
        Path manifest = projectRoot().resolve("resourcepacks").resolve("src").resolve("assets").resolve("copimine").resolve("manifests").resolve("narcotics_visuals_manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return false;
        }
        try {
            String content = Files.readString(manifest, StandardCharsets.UTF_8);
            return content.contains("\"" + key + "\": true");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void applyServerOverlay(Player player, String effectId, int durationSeconds, boolean overdose) {
        if (!configService.serverOverlayUseTitles()) {
            return;
        }
        String glyph = GLYPHS.getOrDefault(effectId, GLYPHS.get("CHAOS"));
        Component overlay = Component.text(glyph).font(Key.key("copimine:narcotics_overlay"));
        int safeSeconds = Math.max(1, Math.min(configService.serverOverlayMaxDurationSeconds(), durationSeconds));
        Title title = Title.title(
                Component.empty(),
                overlay,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(safeSeconds), Duration.ofMillis(120))
        );
        player.showTitle(title);
        if (overdose) {
            player.sendActionBar(Component.text(glyph).font(Key.key("copimine:narcotics_overlay")));
        }
    }

    private void clearServerVisualSurface(Player player) {
        if (configService.serverOverlayClearOnStop()) {
            player.clearTitle();
            player.sendActionBar(Component.empty());
        }
    }

    private boolean hasAllOverlayAssets() {
        Path base = projectRoot().resolve("resourcepacks").resolve("src").resolve("assets").resolve("copimine").resolve("textures").resolve("gui").resolve("narcotics");
        for (String effectId : GLYPHS.keySet()) {
            String fileName = effectId.toLowerCase(Locale.ROOT) + "_overlay.png";
            if (!Files.isRegularFile(base.resolve(fileName))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllShaderProfiles() {
        Path base = projectRoot().resolve("resourcepacks").resolve("src").resolve("assets").resolve("copimine").resolve("shaders").resolve("narcotics");
        for (String effectId : GLYPHS.keySet()) {
            String fileName = effectId.toLowerCase(Locale.ROOT) + ".json";
            if (!Files.isRegularFile(base.resolve(fileName))) {
                return false;
            }
        }
        return true;
    }

    private void applyServerFallback(Player player, String effectId, int durationSeconds, boolean overdose) {
        int ticks = Math.max(20, durationSeconds * 20);
        switch (effectId) {
            case "DESATURATE" -> {
                player.spawnParticle(Particle.ASH, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 24 : 12, 0.45D, 0.45D, 0.45D, 0.01D);
                if (overdose) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.min(ticks, 20 * 8), 0, false, false, true));
                }
            }
            case "COLOR_CONVOLVE" -> {
                player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 16 : 10, 0.4D, 0.4D, 0.4D, 0.02D);
                if (overdose) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, Math.min(ticks, 20 * 4), 0, false, false, true));
                }
            }
            case "SCAN_PINCUSHION" -> player.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 20 : 12, 0.35D, 0.55D, 0.35D, 0.01D);
            case "GREEN_NOISE" -> player.spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 20 : 10, 0.3D, 0.45D, 0.3D, 0.02D);
            case "INVERT" -> {
                player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 28 : 16, 0.45D, 0.55D, 0.45D, 0.04D);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, Math.min(ticks, 20 * 8), 0, false, false, true));
            }
            case "WOBBLE" -> {
                player.spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 16 : 8, 0.45D, 0.45D, 0.45D, 0.01D);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, Math.min(ticks, 20 * 5), 0, false, false, true));
            }
            case "BLOBS" -> player.spawnParticle(Particle.SPLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 24 : 12, 0.35D, 0.45D, 0.35D, 0.01D);
            case "PENCIL" -> player.spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 14 : 8, 0.3D, 0.3D, 0.3D, 0.01D);
            case "CHAOS" -> {
                player.spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.45D, 0.45D, 0.45D, 0.02D);
                player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.45D, 0.45D, 0.45D, 0.04D);
            }
            default -> player.spawnParticle(Particle.SMOKE, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 18 : 8, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    private String normalizeEffectId(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private Path projectRoot() {
        Path data = plugin.getDataFolder().toPath().toAbsolutePath();
        Path current = data;
        for (int index = 0; index < 4 && current != null; index++) {
            current = current.getParent();
        }
        if (current != null && Files.isDirectory(current.resolve("resourcepacks"))) {
            return current;
        }
        return Path.of("").toAbsolutePath().resolve("opt").resolve("copimine");
    }

    private record VisualSession(String effectId, VisualRoute route, long untilMillis, boolean overdose) {
    }
}
