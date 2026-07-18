package me.copimine.narcotics.config;

import me.copimine.narcotics.model.ConfiguredEffect;
import me.copimine.narcotics.model.NarcoticDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NarcoticsConfigService {
    public enum TextureMode { VANILLA, CUSTOM }

    public enum VisualMode {
        AUTO,
        CLIENT_MOD,
        SERVER_OVERLAY,
        SERVER_FALLBACK
    }

    private final JavaPlugin plugin;
    private final Map<String, NarcoticDefinition> items = new LinkedHashMap<>();
    private final Map<String, String> messages = new LinkedHashMap<>();
    private final Set<String> visualEffectIds = new LinkedHashSet<>();
    private final Map<String, Boolean> visualEffectToggles = new LinkedHashMap<>();

    private int narcoticVersion;
    private int asyncThreads;
    private int asyncQueueCapacity;
    private boolean preloadBrewingCacheOnEnable;
    private boolean requireFullWater;
    private boolean clearCauldronOnCompletion;
    private boolean dropIngredientsOnBreakOrWaterLoss;
    private int consumeCooldownSeconds;
    private int usageWindowSeconds;
    private int overdoseThreshold;
    private int durationOverrideSeconds;
    private boolean clearNormalEffectsBeforeNewUse;
    private boolean milkBlocksDuringOverdose;
    private boolean zhuzevoForcesOverdose;
    private boolean visualsEnabled;
    private boolean allowClientModVisuals;
    private boolean allowServerResourcePackOverlay;
    private boolean allowServerParticleFallback;
    private boolean serverOverlayUseTitles;
    private int serverOverlayMaxDurationSeconds;
    private boolean serverOverlayClearOnStop;
    private TextureMode textureMode;
    private VisualMode visualMode;
    private boolean blockProcessingInventories;
    private int schemaVersion;
    private boolean clientBridgeEnabled;
    private boolean requireClientMod;
    private boolean kickIfMissingClient;
    private int handshakeTimeoutSeconds;
    private boolean preferClientVisuals;
    private boolean fallbackToServerOverlay;
    private boolean fallbackToParticles;

    public NarcoticsConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        ConfigurationSection root = plugin.getConfig();
        narcoticVersion = root.getInt("runtime.narcotic_version", 1);
        asyncThreads = Math.max(2, root.getInt("runtime.async_threads", 2));
        asyncQueueCapacity = clamp(root.getInt("runtime.async_queue_capacity", 512), 64, 10000);
        schemaVersion = root.getInt("database.schema_version", 1);
        preloadBrewingCacheOnEnable = root.getBoolean("database.preload_brewing_cache_on_enable", true);
        requireFullWater = root.getBoolean("cauldron.require_full_water", true);
        clearCauldronOnCompletion = root.getBoolean("cauldron.clear_cauldron_on_completion", true);
        dropIngredientsOnBreakOrWaterLoss = root.getBoolean("cauldron.drop_ingredients_on_break_or_water_loss", true);
        consumeCooldownSeconds = Math.max(0, root.getInt("usage.consume_cooldown_seconds", 0));
        usageWindowSeconds = Math.max(60, root.getInt("usage.usage_window_seconds", 900));
        overdoseThreshold = Math.max(1, root.getInt("usage.overdose_threshold", 100));
        durationOverrideSeconds = Math.max(0, root.getInt("usage.duration_override_seconds", 0));
        clearNormalEffectsBeforeNewUse = root.getBoolean("usage.clear_normal_effects_before_new_use", true);
        milkBlocksDuringOverdose = root.getBoolean("usage.milk_blocks_during_overdose", true);
        zhuzevoForcesOverdose = root.getBoolean("usage.zhuzevo_forces_overdose", false);
        visualsEnabled = root.getBoolean("visuals.enabled", false);
        allowClientModVisuals = root.getBoolean("visuals.allow_client_mod_visuals", true);
        allowServerResourcePackOverlay = root.getBoolean("visuals.allow_server_resource_pack_overlay", false);
        allowServerParticleFallback = root.getBoolean("visuals.allow_server_particle_fallback", true);
        serverOverlayUseTitles = root.getBoolean("visuals.server_overlay.use_titles", false);
        serverOverlayMaxDurationSeconds = clamp(root.getInt("visuals.server_overlay.max_duration_seconds", 60), 5, 600);
        serverOverlayClearOnStop = root.getBoolean("visuals.server_overlay.clear_on_stop", true);
        textureMode = parseTextureMode(root.getString("textures.mode", "VANILLA"));
        visualMode = parseVisualMode(root.getString("visuals.mode", "AUTO"));
        blockProcessingInventories = root.getBoolean("storage.block_processing_inventories", true);
        clientBridgeEnabled = root.getBoolean("client_bridge.enabled", true);
        requireClientMod = root.getBoolean("client_bridge.require_client_mod", false);
        kickIfMissingClient = root.getBoolean("client_bridge.kick_if_missing_client", false);
        handshakeTimeoutSeconds = clamp(root.getInt("client_bridge.handshake_timeout_seconds", 10), 3, 120);
        preferClientVisuals = root.getBoolean("client_bridge.prefer_client_visuals", true);
        fallbackToServerOverlay = root.getBoolean("client_bridge.fallback_to_server_overlay", false);
        fallbackToParticles = root.getBoolean("client_bridge.fallback_to_particles", true);

        messages.clear();
        ConfigurationSection messageSection = root.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, ""));
            }
        }

        visualEffectIds.clear();
        visualEffectToggles.clear();
        ConfigurationSection effectsSection = root.getConfigurationSection("visuals.effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                String normalized = key.toUpperCase(Locale.ROOT);
                visualEffectIds.add(normalized);
                visualEffectToggles.put(normalized, effectsSection.getBoolean(key, true));
            }
        }

        items.clear();
        ConfigurationSection itemsSection = root.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("items section is required for CopiMineNarcotics.");
        }
        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(itemId);
            if (section == null) {
                continue;
            }
            Material material = parseMaterial(section.getString("material"), Material.PAPER);
            Material fallback = parseMaterial(section.getString("fallback_material"), material);
            List<String> recipe = normalizeList(section.getStringList("recipe"));
            List<ConfiguredEffect> normalEffects = parseEffects(section.getStringList("normal_effects"));
            List<ConfiguredEffect> overdoseEffects = parseEffects(section.getStringList("overdose_effects"));
            NarcoticDefinition definition = NarcoticDefinition.of(
                    itemId,
                    section.getString("display_name", itemId),
                    material,
                    fallback,
                    section.getString("texture_key", itemId),
                    section.getInt("custom_model_data", 0),
                    section.getInt("overdose_weight", 0),
                    section.getString("visual_effect", "CHAOS"),
                    recipe,
                    normalEffects,
                    overdoseEffects
            );
            items.put(definition.id(), definition);
            visualEffectIds.add(definition.visualEffectId());
            visualEffectToggles.putIfAbsent(definition.visualEffectId(), true);
        }
    }

    public Map<String, NarcoticDefinition> items() {
        return Map.copyOf(items);
    }

    public Set<String> itemIds() {
        return Set.copyOf(items.keySet());
    }

    public int narcoticVersion() {
        return narcoticVersion;
    }

    public int asyncThreads() {
        return asyncThreads;
    }

    public int asyncQueueCapacity() {
        return asyncQueueCapacity;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean preloadBrewingCacheOnEnable() {
        return preloadBrewingCacheOnEnable;
    }

    public boolean requireFullWater() {
        return requireFullWater;
    }

    public boolean clearCauldronOnCompletion() {
        return clearCauldronOnCompletion;
    }

    public boolean dropIngredientsOnBreakOrWaterLoss() {
        return dropIngredientsOnBreakOrWaterLoss;
    }

    public int consumeCooldownSeconds() {
        return consumeCooldownSeconds;
    }

    public int usageWindowSeconds() {
        return usageWindowSeconds;
    }

    public int overdoseThreshold() {
        return overdoseThreshold;
    }

    public int durationOverrideSeconds() {
        return durationOverrideSeconds;
    }

    public boolean clearNormalEffectsBeforeNewUse() {
        return clearNormalEffectsBeforeNewUse;
    }

    public boolean milkBlocksDuringOverdose() {
        return milkBlocksDuringOverdose;
    }

    public boolean zhuzevoForcesOverdose() {
        return zhuzevoForcesOverdose;
    }

    public boolean visualsEnabled() {
        return visualsEnabled;
    }

    public boolean allowClientModVisuals() {
        return allowClientModVisuals;
    }

    public boolean allowServerResourcePackOverlay() {
        return allowServerResourcePackOverlay;
    }

    public boolean allowServerParticleFallback() {
        return allowServerParticleFallback;
    }

    public boolean serverOverlayUseTitles() {
        return serverOverlayUseTitles;
    }

    public int serverOverlayMaxDurationSeconds() {
        return serverOverlayMaxDurationSeconds;
    }

    public boolean serverOverlayClearOnStop() {
        return serverOverlayClearOnStop;
    }

    public TextureMode textureMode() {
        return textureMode;
    }

    public VisualMode visualMode() {
        return visualMode;
    }

    public boolean blockProcessingInventories() {
        return blockProcessingInventories;
    }

    public Set<String> visualEffectIds() {
        return Set.copyOf(visualEffectIds);
    }

    public boolean isVisualEffectEnabled(String effectId) {
        return visualEffectToggles.getOrDefault(effectId.toUpperCase(Locale.ROOT), false);
    }

    public boolean isKnownVisualEffect(String effectId) {
        return effectId != null && visualEffectIds.contains(effectId.toUpperCase(Locale.ROOT));
    }

    public boolean clientBridgeEnabled() {
        return clientBridgeEnabled;
    }

    public boolean requireClientMod() {
        return requireClientMod;
    }

    public boolean kickIfMissingClient() {
        return kickIfMissingClient;
    }

    public int handshakeTimeoutSeconds() {
        return handshakeTimeoutSeconds;
    }

    public boolean preferClientVisuals() {
        return preferClientVisuals;
    }

    public boolean fallbackToServerOverlay() {
        return fallbackToServerOverlay;
    }

    public boolean fallbackToParticles() {
        return fallbackToParticles;
    }

    public String message(String key, String... args) {
        String value = messages.getOrDefault(key, key);
        for (String arg : args) {
            value = value.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(arg));
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public void setTextureMode(String mode) {
        textureMode = parseTextureMode(mode);
        plugin.getConfig().set("textures.mode", textureMode.name());
        plugin.saveConfig();
    }

    public void setOverdoseThreshold(int value) {
        overdoseThreshold = Math.max(1, value);
        plugin.getConfig().set("usage.overdose_threshold", overdoseThreshold);
        plugin.saveConfig();
    }

    public void setUsageWindowSeconds(int seconds) {
        usageWindowSeconds = Math.max(60, seconds);
        plugin.getConfig().set("usage.usage_window_seconds", usageWindowSeconds);
        plugin.saveConfig();
    }

    public void setDurationOverrideSeconds(int seconds) {
        durationOverrideSeconds = Math.max(0, seconds);
        plugin.getConfig().set("usage.duration_override_seconds", durationOverrideSeconds);
        plugin.saveConfig();
    }

    public void setOverdoseWeight(String itemId, int value) {
        NarcoticDefinition current = items.get(itemId.toLowerCase(Locale.ROOT));
        if (current == null) {
            return;
        }
        plugin.getConfig().set("items." + current.id() + ".overdose_weight", Math.max(1, value));
        plugin.saveConfig();
        reload();
    }

    public int overdoseWeightFor(NarcoticDefinition definition) {
        NarcoticDefinition current = items.get(definition.id());
        return current == null ? definition.overdoseWeight() : current.overdoseWeight();
    }

    public void setVisualsEnabled(boolean enabled) {
        visualsEnabled = enabled;
        plugin.getConfig().set("visuals.enabled", enabled);
        plugin.saveConfig();
    }

    public void setVisualMode(String rawMode) {
        visualMode = parseVisualMode(rawMode);
        plugin.getConfig().set("visuals.mode", visualMode.name());
        plugin.saveConfig();
    }

    public void setVisualEffectEnabled(String effectId, boolean enabled) {
        String normalized = effectId.toUpperCase(Locale.ROOT);
        if (!visualEffectIds.contains(normalized)) {
            throw new IllegalArgumentException("Unknown visual effect id: " + normalized);
        }
        visualEffectToggles.put(normalized, enabled);
        plugin.getConfig().set("visuals.effects." + normalized, enabled);
        plugin.saveConfig();
    }

    public void setRequireClientMod(boolean required) {
        requireClientMod = required;
        plugin.getConfig().set("client_bridge.require_client_mod", required);
        plugin.saveConfig();
    }

    private TextureMode parseTextureMode(String raw) {
        try {
            return TextureMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException parseError) {
            plugin.getLogger().warning("Invalid narcotics texture mode '" + raw + "', fallback to VANILLA: "
                    + parseError.getMessage());
            return TextureMode.VANILLA;
        }
    }

    private VisualMode parseVisualMode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if ("FALLBACK".equals(normalized)) {
            normalized = "SERVER_FALLBACK";
        } else if ("OVERLAY".equals(normalized) || "SERVER_OVERLAY".equals(normalized)) {
            normalized = "AUTO";
        } else if ("SHADER".equals(normalized)) {
            normalized = "AUTO";
        }
        try {
            VisualMode mode = VisualMode.valueOf(normalized);
            if (mode == VisualMode.CLIENT_MOD && !allowClientModVisuals) {
                return VisualMode.SERVER_FALLBACK;
            }
            if (mode == VisualMode.SERVER_OVERLAY) {
                return VisualMode.AUTO;
            }
            if (mode == VisualMode.SERVER_FALLBACK && !allowServerParticleFallback) {
                return VisualMode.AUTO;
            }
            return mode;
        } catch (IllegalArgumentException parseError) {
            plugin.getLogger().warning("Invalid narcotics visual mode '" + raw + "', fallback to AUTO: "
                    + parseError.getMessage());
            return VisualMode.AUTO;
        }
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private List<String> normalizeList(List<String> raw) {
        List<String> values = new ArrayList<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                values.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return values;
    }

    private List<ConfiguredEffect> parseEffects(List<String> raw) {
        List<ConfiguredEffect> effects = new ArrayList<>();
        for (String line : raw) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",");
            String type = "";
            int amplifier = 0;
            int durationSeconds = 0;
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0].trim().toLowerCase(Locale.ROOT);
                String value = kv[1].trim();
                switch (key) {
                    case "type" -> type = value.toUpperCase(Locale.ROOT);
                    case "amplifier" -> amplifier = parseInt(value, 0);
                    case "duration_seconds" -> durationSeconds = parseInt(value, 1);
                    default -> {
                    }
                }
            }
            effects.add(new ConfiguredEffect(type, amplifier, durationSeconds));
        }
        return effects;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException parseError) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
