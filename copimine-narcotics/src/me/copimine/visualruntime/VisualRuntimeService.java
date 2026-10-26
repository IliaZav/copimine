package me.copimine.visualruntime;

import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
        return false;
    }

    /** Shader and post-process presentation was retired for Narcotics; these stay for old callers. */
    public boolean supportsClientShaderLikeRuntime() {
        return false;
    }

    public boolean supportsClientZipShaderpackRuntime() {
        return false;
    }

    public boolean supportsShaderRuntime() {
        return false;
    }

    public String serverOverlaySupportReason() {
        return "server title overlay retired: Narcotics only uses restrained normal particles";
    }

    public String clientVisualSupportReason(Player player, String effectId) {
        if (!clientBridge.enabled()) {
            return "client bridge disabled";
        }
        if (!configService.allowClientModVisuals()) {
            return "client visuals disabled in config";
        }
        return clientBridge.routeHint(player, effectId) + " (narcotics shaders retired, particles only)";
    }

    public String shaderSupportReason() {
        return "narcotics screen shaders retired: presentation is restrained normal particles only";
    }

    public String clientShaderLikeSupportReason() {
        return "narcotics screen shaders retired: presentation is restrained normal particles only";
    }

    public String clientShaderpackSupportReason() {
        return "narcotics screen shaders retired: no shaderpack is requested from CopiMineClient";
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
                && configService.allowClientModVisuals()
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

    private boolean sameVisualSession(VisualSession current, VisualRoute route, String effectId, long nowMillis) {
        return current != null
                && current.route() == route
                && current.effectId().equalsIgnoreCase(effectId)
                && current.untilMillis() > nowMillis;
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
        if (route == VisualRoute.DISABLED) {
            clear(player);
            return;
        }
        long nowMillis = System.currentTimeMillis();
        long untilMillis = nowMillis + (Math.max(1, durationSeconds) * 1000L);
        VisualSession current = sessions.get(player.getUniqueId());
        if (sameVisualSession(current, route, normalized, nowMillis)) {
            long extendedUntil = Math.max(current.untilMillis(), untilMillis);
            sessions.put(player.getUniqueId(), new VisualSession(
                    normalized, route, extendedUntil, overdose, current.clientCommandEpoch()));
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
            case SERVER_RESOURCE_PACK_OVERLAY -> {
                applyServerOverlay(player, normalized, durationSeconds, overdose);
                applyServerFallback(player, normalized, durationSeconds, overdose);
            }
            case SERVER_PARTICLE_FALLBACK -> applyServerFallback(player, normalized, durationSeconds, overdose);
            case DISABLED, CLIENT_MOD_VISUAL -> {
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
        // Narcotics screen shaders were retired by the operator: every configured visual
        // mode resolves to the restrained particle presentation below.
        return particleRoute();
    }

    private VisualRoute particleRoute() {
        return configService.allowServerParticleFallback() && configService.fallbackToParticles()
                ? VisualRoute.SERVER_PARTICLE_FALLBACK : VisualRoute.DISABLED;
    }
    private VisualRoute forcedServerRoute(Player player, String effectId) {
        return configService.allowServerParticleFallback() ? VisualRoute.SERVER_PARTICLE_FALLBACK : VisualRoute.DISABLED;
    }

    private void applyServerOverlay(Player player, String effectId, int durationSeconds, boolean overdose) {
        // Server-side title/actionbar overlays are intentionally unavailable. Clearing these
        // global player surfaces here would erase UI owned by unrelated plugins.
    }

    private void clearServerVisualSurface(Player player) {
        clearHints.remove(player.getUniqueId());
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

    private record VisualSession(String effectId, VisualRoute route, long untilMillis, boolean overdose,
            UUID clientCommandEpoch) {
    }
}
