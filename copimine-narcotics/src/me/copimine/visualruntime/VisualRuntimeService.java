package me.copimine.visualruntime;

import me.copimine.clientbridge.ClientCapabilityState;
import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private static final Map<String, String> CLIENT_SHADERPACKS = Map.of(
            "DESATURATE", "trippy_shaderpack.zip",
            "COLOR_CONVOLVE", "ctr_vcr.zip",
            "SCAN_PINCUSHION", "cursed_metamorphopsia.zip",
            "GREEN_NOISE", "lsd_shader.zip",
            "INVERT", "crucify.zip",
            "WOBBLE", "nms_1_6.zip",
            "BLOBS", "acid_shaders.zip",
            "PENCIL", "white_sharp_1_2.zip"
    );
    private static final List<String> RANDOM_SHADERPACKS = List.of(
            "acid_shaders.zip",
            "crucify.zip",
            "cursed_metamorphopsia.zip",
            "ctr_vcr.zip",
            "lsd_shader.zip",
            "nms_1_6.zip",
            "trippy_shaderpack.zip",
            "white_sharp_1_2.zip"
    );
    private static final List<String> RANDOM_DARK_SHADERPACKS = List.of(
            "crucify.zip",
            "cursed_metamorphopsia.zip",
            "ctr_vcr.zip",
            "nms_1_6.zip"
    );
    private static final int CLIENT_SHADER_FADE_IN_MILLIS = 1_500;
    private static final int CLIENT_SHADER_FADE_OUT_MILLIS = 2_500;

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
        return false;
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

    public boolean supportsClientZipShaderpackRuntime() {
        return detectClientShaderpackRuntime();
    }

    public boolean supportsShaderRuntime() {
        return detectTrueShaderSupport();
    }

    public String serverOverlaySupportReason() {
        return "server title overlay disabled: CopiMine now uses Iris shaderpacks, client post-process fallback, or light particles only";
    }

    public String clientVisualSupportReason(Player player, String effectId) {
        if (!clientBridge.enabled()) {
            return "client bridge disabled";
        }
        ClientCapabilityState state = player == null ? null : clientBridge.capabilities().state(player);
        String base = clientBridge.routeHint(player, effectId);
        if (state != null && state.clientShaderLike()) {
            return base + " (built-in CopiMine ZIP shaderpacks can be switched through Iris and restored after the effect)";
        }
        if (state != null && state.trueIrisShader()) {
            return base + " (player already has an Iris shaderpack active; CopiMineClient can override it only if the client-side config allows it)";
        }
        return base;
    }

    public String shaderSupportReason() {
        if (!supportsClientShaderLikeRuntime()) {
            return clientShaderLikeSupportReason();
        }
        if (!manifestFlag("true_shader_runtime_supported")) {
            return "CopiMineClient uses built-in ZIP shaderpacks through Iris when available; without client support the server falls back to light particles only";
        }
        return "available through the required CopiMineClient runtime";
    }

    public String clientShaderLikeSupportReason() {
        if (!clientBridge.enabled() || !configService.clientBridgeEnabled()) {
            return "client bridge disabled";
        }
        if (!hasAllShaderProfiles()) {
            return "client shader-like profiles missing";
        }
        if (!shaderLikeSupportedManifestFlag()) {
            return "client-mod visual runtime disabled by visuals manifest";
        }
        return "available through CopiMineClient post-process and optional Iris shaderpack runtime";
    }

    public String clientShaderpackSupportReason() {
        if (!clientBridge.enabled() || !configService.clientBridgeEnabled()) {
            return "client bridge disabled";
        }
        if (!manifestFlag("client_zip_shaderpack_runtime_supported")) {
            return "client ZIP shaderpack runtime not declared in visuals manifest";
        }
        return "available through CopiMineClient with optional Iris runtime switching for built-in shaderpacks";
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

    /**
     * True only after the required CopiMineClient has completed its challenge-bound
     * version check. The server still owns every gameplay effect; this flag is used
     * solely to decide whether vanilla status icons can be consolidated.
     */
    public boolean clientModAvailable(Player player) {
        return clientModAvailable(player, "OVERDOSE");
    }

    /**
     * A consolidated icon is safe only when this exact visual id is advertised
     * by the connected client.  Older client builds keep their vanilla icons
     * instead of hiding debuffs they cannot render themselves.
     */
    public boolean clientModAvailable(Player player, String effectId) {
        return player != null
                && clientBridge.enabled()
                && clientBridge.capabilities().supportsEffect(player, effectId);
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
        if (!ignoreGate && configService.visualsForceDisabled()) {
            clear(player);
            return;
        }
        VisualRoute route = forceServerRoute ? forcedServerRoute(player, normalized) : resolveRoute(player, normalized, ignoreGate);
        if (route == VisualRoute.DISABLED) {
            clear(player);
            return;
        }
        long nowMillis = System.currentTimeMillis();
        long untilMillis = nowMillis + (Math.max(1, durationSeconds) * 1000L);
        VisualSession current = sessions.get(player.getUniqueId());
        if (sameVisualSession(current, route, normalized, nowMillis)) {
            long extendedUntil = Math.max(current.untilMillis(), untilMillis);
            boolean presentationChanged = current.overdose() != overdose;
            if (route == VisualRoute.CLIENT_MOD_VISUAL
                    && (extendedUntil > current.untilMillis() || presentationChanged)) {
                // Preserve the visual instance and its original expiry when
                // the timer extends or overdose changes the presentation.
                UUID refreshEpoch = UUID.randomUUID();
                sessions.put(player.getUniqueId(),
                        new VisualSession(normalized, route, extendedUntil, overdose, refreshEpoch));
                int refreshSeconds = (int) Math.max(1L, Math.min(600L,
                        (long) Math.ceil((extendedUntil - nowMillis) / 1000.0D)));
                long refreshSequence = clientBridge.visuals().sendVisualRefresh(
                        player,
                        normalized,
                        requestedClientShaderpack(normalized, overdose),
                        refreshSeconds,
                        overdose ? 1.0F : 0.85F,
                        CLIENT_SHADER_FADE_IN_MILLIS,
                        CLIENT_SHADER_FADE_OUT_MILLIS,
                        ignoreGate ? "ADMIN_TEST_REFRESH" : "NARCOTICS_REFRESH",
                        (playerUuid, clientEffectId, seconds, intensity, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player online = plugin.getServer().getPlayer(playerUuid);
                            if (online == null || !online.isOnline()) {
                                return;
                            }
                            VisualSession session = sessions.get(playerUuid);
                            if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL
                                    || !session.effectId().equalsIgnoreCase(clientEffectId)
                                    || !VisualSessionCompletionPolicy.canApplyFallback(session.clientCommandEpoch(),
                                            refreshEpoch, session.untilMillis(), System.currentTimeMillis())) {
                                return;
                            }
                            applyServerFallbackRoute(online, clientEffectId, session.untilMillis(), session.overdose());
                        }),
                        (playerUuid, clientEffectId, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player online = plugin.getServer().getPlayer(playerUuid);
                            if (online == null) {
                                return;
                            }
                            VisualSession session = sessions.get(playerUuid);
                            if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL
                                    || !session.effectId().equalsIgnoreCase(clientEffectId)
                                    || !VisualSessionCompletionPolicy.isCurrent(session.clientCommandEpoch(), refreshEpoch)) {
                                return;
                            }
                            clearInternal(online, false);
                        })
                );
                if (refreshSequence <= 0L) {
                    applyServerFallbackRoute(player, normalized, extendedUntil, overdose);
                    return;
                }
            } else {
                sessions.put(player.getUniqueId(), new VisualSession(
                        normalized, route, extendedUntil, overdose, current.clientCommandEpoch()));
                if (presentationChanged && route == VisualRoute.SERVER_PARTICLE_FALLBACK) {
                    int remainingSeconds = VisualSessionCompletionPolicy.remainingSecondsUntil(extendedUntil, nowMillis);
                    applyServerFallback(player, normalized, remainingSeconds, overdose);
                }
            }
            VisualSession latest = sessions.get(player.getUniqueId());
            if (latest != null && latest.untilMillis() == extendedUntil) {
                scheduleCleanup(player, extendedUntil);
            }
            return;
        }
        clear(player);
        UUID commandEpoch = UUID.randomUUID();
        sessions.put(player.getUniqueId(), new VisualSession(normalized, route, untilMillis, overdose, commandEpoch));
        switch (route) {
            case CLIENT_MOD_VISUAL -> {
                long commandSequence = clientBridge.visuals().sendVisualStart(
                        player,
                        normalized,
                        requestedClientShaderpack(normalized, overdose),
                        durationSeconds,
                        overdose ? 1.0F : 0.85F,
                        CLIENT_SHADER_FADE_IN_MILLIS,
                        CLIENT_SHADER_FADE_OUT_MILLIS,
                        ignoreGate ? "ADMIN_TEST" : "NARCOTICS",
                        (playerUuid, clientEffectId, seconds, intensity, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player online = plugin.getServer().getPlayer(playerUuid);
                            if (online == null || !online.isOnline()) {
                                return;
                            }
                            VisualSession session = sessions.get(playerUuid);
                            if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL
                                    || !session.effectId().equalsIgnoreCase(clientEffectId)
                                    || !VisualSessionCompletionPolicy.canApplyFallback(session.clientCommandEpoch(),
                                            commandEpoch, session.untilMillis(), System.currentTimeMillis())) {
                                return;
                            }
                            applyServerFallbackRoute(online, clientEffectId, session.untilMillis(), session.overdose());
                        }),
                        (playerUuid, clientEffectId, source, reason) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player online = plugin.getServer().getPlayer(playerUuid);
                            if (online == null) {
                                return;
                            }
                            VisualSession session = sessions.get(playerUuid);
                            if (session == null || session.route() != VisualRoute.CLIENT_MOD_VISUAL
                                    || !session.effectId().equalsIgnoreCase(clientEffectId)
                                    || !VisualSessionCompletionPolicy.isCurrent(session.clientCommandEpoch(), commandEpoch)) {
                                return;
                            }
                            clearInternal(online, false);
                        })
                );
                if (commandSequence <= 0L) {
                    applyServerFallbackRoute(player, normalized, untilMillis, overdose);
                    return;
                }
            }
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

    private boolean sameVisualSession(VisualSession current, VisualRoute route, String effectId, long nowMillis) {
        return current != null
                && current.route() == route
                && current.effectId().equalsIgnoreCase(effectId)
                && current.untilMillis() > nowMillis;
    }

    private void applyServerFallbackRoute(Player player, String effectId, long untilMillis, boolean overdose) {
        VisualSession previous = sessions.get(player.getUniqueId());
        if (previous != null && previous.route() == VisualRoute.CLIENT_MOD_VISUAL) {
            // A failed refresh may leave the prior authenticated visual active.
            // Clear that bridge-owned stream before switching to server fallback.
            clientBridge.visuals().clearVisuals(player);
        }
        int remainingSeconds = VisualSessionCompletionPolicy.remainingSecondsUntil(untilMillis, System.currentTimeMillis());
        if (remainingSeconds <= 0) {
            return;
        }
        VisualRoute fallbackRoute = forcedServerRoute(player, effectId);
        sessions.put(player.getUniqueId(), new VisualSession(
                effectId.toUpperCase(Locale.ROOT), fallbackRoute, untilMillis, overdose, null));
        switch (fallbackRoute) {
            case SERVER_RESOURCE_PACK_OVERLAY -> {
                applyServerOverlay(player, effectId, remainingSeconds, overdose);
                applyServerFallback(player, effectId, remainingSeconds, overdose);
            }
            case SERVER_PARTICLE_FALLBACK -> applyServerFallback(player, effectId, remainingSeconds, overdose);
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
        // The shader presentation belongs to the drug itself: it is chosen by the
        // consumption event, never by the operator/player settings.  The only
        // setting that can touch it is the emergency force_disable switch.
        if (configService.visualsForceDisabled()) {
            return VisualRoute.DISABLED;
        }
        if (clientRouteAvailable(player, effectId)) {
            return VisualRoute.CLIENT_MOD_VISUAL;
        }
        return VisualRoute.SERVER_PARTICLE_FALLBACK;
    }

    private VisualRoute firstAvailable(Player player, String effectId, boolean allowClientFirst, boolean allowServerOverlay) {
        if (allowClientFirst && clientRouteAvailable(player, effectId)) {
            return VisualRoute.CLIENT_MOD_VISUAL;
        }
        // Fullscreen server title overlays were retired: they looked like static
        // pictures over the world. Keep the enum for old saved sessions, but do
        // not select this route for new effects.
        if (configService.allowServerParticleFallback() && configService.fallbackToParticles()) {
            return VisualRoute.SERVER_PARTICLE_FALLBACK;
        }
        if (!allowClientFirst && clientRouteAvailable(player, effectId)) {
            return VisualRoute.CLIENT_MOD_VISUAL;
        }
        return VisualRoute.DISABLED;
    }

    private VisualRoute forcedServerRoute(Player player, String effectId) {
        return configService.allowServerParticleFallback() ? VisualRoute.SERVER_PARTICLE_FALLBACK : VisualRoute.DISABLED;
    }

    private boolean clientRouteAvailable(Player player, String effectId) {
        if (player == null) {
            return false;
        }
        return clientBridge.enabled()
                && clientBridge.visuals().canUse(player, effectId);
    }

    private boolean overlayRouteAvailable(Player player) {
        return false;
    }

    private boolean detectServerOverlaySupport() {
        return false;
    }

    private boolean detectOverlaySupport() {
        return detectServerOverlaySupport();
    }

    private boolean detectClientShaderLikeSupport() {
        return configService.clientBridgeEnabled()
                && clientBridge.enabled()
                && hasAllShaderProfiles()
                && shaderLikeSupportedManifestFlag();
    }

    private boolean detectClientShaderpackRuntime() {
        return detectClientShaderLikeSupport() && manifestFlag("client_zip_shaderpack_runtime_supported");
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
        } catch (Exception error) {
            plugin.getLogger().warning("Could not read narcotics visual manifest: " + error.getMessage());
            return false;
        }
    }

    private void applyServerOverlay(Player player, String effectId, int durationSeconds, boolean overdose) {
        // Server-side title/actionbar overlays are intentionally unavailable. Clearing these
        // global player surfaces here would erase UI owned by unrelated plugins.
    }

    private void applyOverlay(Player player, String effectId, int durationSeconds, boolean overdose) {
        applyServerOverlay(player, effectId, durationSeconds, overdose);
    }

    private void clearServerVisualSurface(Player player) {
        clearHints.remove(player.getUniqueId());
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
            case "OVERDOSE" -> {
                player.spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 28 : 18, 0.45D, 0.55D, 0.45D, 0.02D);
                player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 24 : 14, 0.45D, 0.55D, 0.45D, 0.04D);
            }
            case "ZHUZEVO_TRIP" -> {
                player.spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 22, 0.4D, 0.5D, 0.4D, 0.02D);
                player.spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.35D, 0.45D, 0.35D, 0.01D);
            }
            default -> player.spawnParticle(Particle.SMOKE, player.getLocation().add(0.0D, 1.0D, 0.0D), overdose ? 18 : 8, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    private String normalizeEffectId(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private String requestedClientShaderpack(String effectId, boolean overdose) {
        if (overdose) {
            return RANDOM_DARK_SHADERPACKS.get(ThreadLocalRandom.current().nextInt(RANDOM_DARK_SHADERPACKS.size()));
        }
        if ("CHAOS".equalsIgnoreCase(effectId)) {
            return RANDOM_SHADERPACKS.get(ThreadLocalRandom.current().nextInt(RANDOM_SHADERPACKS.size()));
        }
        return CLIENT_SHADERPACKS.getOrDefault(effectId.toUpperCase(Locale.ROOT), RANDOM_SHADERPACKS.get(0));
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

    private record VisualSession(String effectId, VisualRoute route, long untilMillis, boolean overdose,
            UUID clientCommandEpoch) {
    }
}
