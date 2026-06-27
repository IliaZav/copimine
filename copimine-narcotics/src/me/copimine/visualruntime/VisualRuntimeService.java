package me.copimine.visualruntime;

import me.copimine.clientbridge.ClientCapabilityState;
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
    private final Map<UUID, VisualSession> clearHints = new ConcurrentHashMap<>();

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
        clearInternal(player, true);
    }

    private void clearInternal(Player player, boolean notifyClient) {
        if (player == null) {
            return;
        }
        VisualSession previousSession = sessions.remove(player.getUniqueId());
        Integer taskId = cleanupTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        if (notifyClient) {
            clientBridge.visuals().clearVisuals(player);
        }
        if (previousSession != null) {
            clearHints.put(player.getUniqueId(), previousSession);
        }
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
        return detectOverlaySupport();
    }

    public boolean supportsClientShaderLikeRuntime() {
        return detectClientShaderLikeSupport();
    }

    public boolean supportsShaderRuntime() {
        return detectTrueShaderSupport();
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
        if (!overlaySupportedManifestFlag()) {
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
        ClientCapabilityState state = player == null ? null : clientBridge.capabilities().state(player);
        String base = clientBridge.routeHint(player, effectId);
        if (state != null && state.trueIrisShader()) {
            return base + " (Iris shaderpack active; CopiMineClient still uses its own HUD/fullscreen overlay renderer and does not inject or replace the user's shaderpack)";
        }
        return base;
    }

    public String shaderSupportReason() {
        if (!supportsClientShaderLikeRuntime()) {
            return clientShaderLikeSupportReason();
        }
        if (!manifestFlag("true_shader_runtime_supported")) {
            return "CopiMineClient does not force Iris/OptiFine shaders; true post-processing shaders are not server-forceable on Paper, so the client uses optional shader-like fullscreen overlays instead";
        }
        return "available through optional CopiMineClient runtime";
    }

    public String clientShaderLikeSupportReason() {
        if (!configService.allowClientModVisuals()) {
            return "client shader-like route disabled in config";
        }
        if (!clientBridge.enabled() || !configService.clientBridgeEnabled()) {
            return "client bridge disabled";
        }
        if (!hasAllShaderProfiles()) {
            return "client shader-like profiles missing";
        }
        if (!shaderLikeSupportedManifestFlag()) {
            return "client-mod visual runtime disabled by visuals manifest";
        }
        return "available through optional CopiMineClient fullscreen overlay runtime";
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
            return "no-active-session";
        }
        long secondsLeft = Math.max(0L, (session.untilMillis() - System.currentTimeMillis()) / 1000L);
        return session.effectId() + " / " + session.route().name() + " / " + secondsLeft + "s";
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
        clear(player);
        if (route == VisualRoute.DISABLED) {
            return;
        }
        long untilMillis = System.currentTimeMillis() + (durationSeconds * 1000L);
        sessions.put(player.getUniqueId(), new VisualSession(normalized, route, untilMillis, overdose));
        switch (route) {
            case CLIENT_MOD_VISUAL -> clientBridge.visuals().sendVisualStart(
                    player,
                    normalized,
                    durationSeconds,
                    overdose ? 1.0F : 0.85F,
                    ignoreGate ? "ADMIN_TEST" : "NARCOTICS",
                    (playerUuid, clientEffectId, seconds, intensity, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player online = plugin.getServer().getPlayer(playerUuid);
                        if (online == null || !online.isOnline()) {
                            return;
                        }
                        VisualSession session = sessions.get(playerUuid);
                        if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL || !session.effectId().equalsIgnoreCase(clientEffectId)) {
                            return;
                        }
                        applyServerFallbackRoute(online, clientEffectId, seconds, session.overdose());
                    }),
                    (playerUuid, clientEffectId, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player online = plugin.getServer().getPlayer(playerUuid);
                        if (online == null) {
                            return;
                        }
                        VisualSession session = sessions.get(playerUuid);
                        if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL || !session.effectId().equalsIgnoreCase(clientEffectId)) {
                            return;
                        }
                        clearInternal(online, false);
                    })
            );
            case SERVER_RESOURCE_PACK_OVERLAY -> {
                applyServerOverlay(player, normalized, durationSeconds, overdose);
                applyServerFallback(player, normalized, durationSeconds, overdose);
            }
            case SERVER_PARTICLE_FALLBACK -> applyServerFallback(player, normalized, durationSeconds, overdose);
            case DISABLED -> {
            }
        }
        scheduleCleanup(player, untilMillis);
    }

    private void applyServerFallbackRoute(Player player, String effectId, int durationSeconds, boolean overdose) {
        VisualRoute fallbackRoute = forcedServerRoute(player, effectId);
        long untilMillis = System.currentTimeMillis() + (durationSeconds * 1000L);
        sessions.put(player.getUniqueId(), new VisualSession(effectId.toUpperCase(Locale.ROOT), fallbackRoute, untilMillis, overdose));
        switch (fallbackRoute) {
            case SERVER_RESOURCE_PACK_OVERLAY -> {
                applyServerOverlay(player, effectId, durationSeconds, overdose);
                applyServerFallback(player, effectId, durationSeconds, overdose);
            }
            case SERVER_PARTICLE_FALLBACK -> applyServerFallback(player, effectId, durationSeconds, overdose);
            case CLIENT_MOD_VISUAL, DISABLED -> {
            }
        }
        scheduleCleanup(player, untilMillis);
    }

    private void scheduleCleanup(Player player, long untilMillis) {
        if (player == null) {
            return;
        }
        Integer previousTask = cleanupTasks.remove(player.getUniqueId());
        if (previousTask != null) {
            plugin.getServer().getScheduler().cancelTask(previousTask);
        }
        long ticksLeft = Math.max(20L, (long) Math.ceil(Math.max(1L, untilMillis - System.currentTimeMillis()) / 50.0D));
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            VisualSession session = sessions.get(player.getUniqueId());
            if (session != null && session.untilMillis() <= System.currentTimeMillis()) {
                clear(player);
            }
        }, ticksLeft).getTaskId();
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
        return Files.isRegularFile(fontManifest) && hasAllOverlayAssets() && overlaySupportedManifestFlag();
    }

    private boolean detectOverlaySupport() {
        return detectServerOverlaySupport();
    }

    private boolean detectClientShaderLikeSupport() {
        return configService.allowClientModVisuals()
                && configService.clientBridgeEnabled()
                && clientBridge.enabled()
                && hasAllShaderProfiles()
                && shaderLikeSupportedManifestFlag();
    }

    private boolean detectTrueShaderSupport() {
        return detectClientShaderLikeSupport() && manifestFlag("true_shader_runtime_supported");
    }

    private boolean detectShaderSupport() {
        return detectTrueShaderSupport();
    }

    private boolean overlaySupportedManifestFlag() {
        return manifestFlag("server_overlay_supported") || manifestFlag("overlay_supported");
    }

    private boolean shaderLikeSupportedManifestFlag() {
        return manifestFlag("client_mod_visual_supported") || manifestFlag("shader_supported");
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

    private void applyOverlay(Player player, String effectId, int durationSeconds, boolean overdose) {
        applyServerOverlay(player, effectId, durationSeconds, overdose);
    }

    private void clearServerVisualSurface(Player player) {
        VisualSession session = clearHints.remove(player.getUniqueId());
        if (session != null && session.route() == VisualRoute.SERVER_RESOURCE_PACK_OVERLAY && configService.serverOverlayClearOnStop()) {
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
