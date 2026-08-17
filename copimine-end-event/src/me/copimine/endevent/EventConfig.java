package me.copimine.endevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Validated, immutable runtime configuration for one End Rift server. */
public record EventConfig(
        int schemaVersion,
        String environment,
        String stateFile,
        String backupStateFile,
        Map<String, Integer> resourceRequirements,
        int countdownSeconds,
        int intermissionSeconds,
        int minPlayers,
        int maxPlayers,
        List<Double> padRadii,
        double padOccupancyRadius,
        String arenaWorld,
        double arenaRadius,
        double bossRadius,
        double containmentRadius,
        int waveHardCap,
        WaveDefinition wave1,
        WaveDefinition wave2,
        WaveDefinition wave3,
        WaveDefinition finalWave,
        double bossHealth,
        double bossAttackDamageBonus,
        int bossTargetMinSeconds,
        int bossTargetMaxSeconds,
        int bossSpellMinSeconds,
        int bossSpellMaxSeconds,
        int bossSpellTelegraphTicks,
        double bossHalfHealth,
        double bossFinalThreshold,
        double bossFinalHealth,
        double finalDrainFraction,
        double finalDrainMinHealth,
        int controlDurationSeconds,
        int controlCooldownSeconds,
        int maxSummonedServants,
        int bossXp,
        int maxXpOrbs,
        String shardItemId,
        int shardChannelSeconds,
        int shardCooldownSeconds,
        String returnStoneItemId,
        Map<String, Integer> resourceBundle,
        String portalWorld,
        double portalX,
        double portalY,
        double portalZ,
        float portalYaw,
        float portalPitch,
        String clientBossId,
        String clientControlId,
        String bridgeChannel) {

    public EventConfig {
        resourceRequirements = Map.copyOf(resourceRequirements);
        resourceBundle = Map.copyOf(resourceBundle);
        padRadii = List.copyOf(padRadii);
    }

    public static EventConfig load(JavaPlugin plugin) {
        ConfigurationSection resources = requiredSection(plugin, "resources");
        LinkedHashMap<String, Integer> requirements = readMaterials(resources, "resources");
        ConfigurationSection ritual = requiredSection(plugin, "ritual");
        ConfigurationSection arena = requiredSection(plugin, "arena");
        ConfigurationSection waves = requiredSection(plugin, "waves");
        ConfigurationSection boss = requiredSection(plugin, "boss");
        ConfigurationSection rewards = requiredSection(plugin, "rewards");
        ConfigurationSection portal = requiredSection(plugin, "portal-room");
        ConfigurationSection client = requiredSection(plugin, "client");
        ConfigurationSection persistence = requiredSection(plugin, "persistence");

        int minPlayers = positiveInt(ritual, "min-players");
        int maxPlayers = positiveInt(ritual, "max-players");
        if (minPlayers > maxPlayers || maxPlayers > 20) {
            throw new IllegalStateException("ritual player bounds must satisfy 1 <= min <= max <= 20");
        }
        List<Double> radii = ritual.getDoubleList("pad-radii");
        if (radii.size() < 4 || radii.stream().anyMatch(value -> value == null || value <= 0.0D)) {
            throw new IllegalStateException("ritual.pad-radii must contain positive fallback radii");
        }
        int waveCap = positiveInt(waves, "hard-cap");
        double health = positiveDouble(boss, "health");
        double half = positiveDouble(boss, "half-health");
        double finalThreshold = positiveDouble(boss, "final-threshold");
        double finalHealth = positiveDouble(boss, "final-health");
        if (!(finalThreshold < half && finalHealth > finalThreshold && finalHealth <= health)) {
            throw new IllegalStateException("boss thresholds must satisfy final-threshold < half-health < final-health <= health");
        }
        double drainFraction = boss.getDouble("final-drain-fraction", 0.60D);
        if (!(drainFraction > 0.0D && drainFraction < 1.0D)) {
            throw new IllegalStateException("boss.final-drain-fraction must be between 0 and 1");
        }
        int[] target = secondsRange(boss, "target-rotation-seconds");
        int[] spells = secondsRange(boss, "spell-cooldown-seconds");
        if (boss.getInt("spell-telegraph-ticks", 30) < 1) {
            throw new IllegalStateException("boss.spell-telegraph-ticks must be positive");
        }

        return new EventConfig(
                persistence.getInt("schema-version", 1),
                text(plugin.getConfig().getString("environment", "local"), "local"),
                text(persistence.getString("file", "event-state.yml"), "event-state.yml"),
                text(persistence.getString("backup-file", "event-state.yml.bak"), "event-state.yml.bak"),
                requirements,
                positiveInt(ritual, "countdown-seconds"),
                positiveInt(ritual, "intermission-seconds"),
                minPlayers,
                maxPlayers,
                radii,
                positiveDouble(ritual, "pad-occupancy-radius"),
                text(arena.getString("world", "CopiMine"), "CopiMine"),
                positiveDouble(arena, "radius"),
                positiveDouble(arena, "boss-radius"),
                positiveDouble(arena, "containment-radius"),
                waveCap,
                wave(waves, "wave-1"),
                wave(waves, "wave-2"),
                wave(waves, "wave-3"),
                wave(waves, "final"),
                health,
                boss.getDouble("attack-damage-bonus", 3.0D),
                target[0], target[1], spells[0], spells[1],
                positiveInt(boss, "spell-telegraph-ticks"),
                half, finalThreshold, finalHealth,
                drainFraction,
                Math.max(1.0D, boss.getDouble("final-drain-min-health", 1.0D)),
                positiveInt(boss, "control-duration-seconds"),
                positiveInt(boss, "control-cooldown-seconds"),
                Math.max(0, boss.getInt("max-summoned-servants", 4)),
                Math.max(0, rewards.getInt("boss-xp", 3000)),
                Math.max(0, rewards.getInt("max-xp-orbs", 20)),
                text(rewards.getString("shard-item-id", "rift_core_shard"), "rift_core_shard"),
                positiveInt(rewards, "shard-channel-seconds"),
                positiveInt(rewards, "shard-cooldown-seconds"),
                text(rewards.getString("return-stone-item-id", "return_stone"), "return_stone"),
                readMaterials(rewards.getConfigurationSection("resource-bundle"), "rewards.resource-bundle"),
                text(portal.getString("world", "CopiMine_the_end"), "CopiMine_the_end"),
                portal.getDouble("x", 0.5D), portal.getDouble("y", 80.0D), portal.getDouble("z", 0.5D),
                (float) portal.getDouble("yaw", 0.0D), (float) portal.getDouble("pitch", 0.0D),
                text(client.getString("boss-id", "END_RIFT_GUARDIAN_V1"), "END_RIFT_GUARDIAN_V1"),
                text(client.getString("control-id", "END_RIFT_CONTROL_REVERSAL_V1"), "END_RIFT_CONTROL_REVERSAL_V1"),
                text(client.getString("bridge-channel", "copimine:client_bridge"), "copimine:client_bridge"));
    }

    private static WaveDefinition wave(ConfigurationSection parent, String key) {
        ConfigurationSection section = requiredSection(parent, key);
        return new WaveDefinition(
                nonNegative(section, "endermen"),
                nonNegative(section, "endermites"),
                nonNegative(section, "shulkers"),
                nonNegative(section, "elite-endermen"));
    }

    private static int[] secondsRange(ConfigurationSection parent, String key) {
        List<Integer> values = parent.getIntegerList(key);
        if (values.size() != 2 || values.get(0) < 1 || values.get(1) < values.get(0)) {
            throw new IllegalStateException("" + key + " must be [min,max] positive seconds");
        }
        return new int[] {values.get(0), values.get(1)};
    }

    private static LinkedHashMap<String, Integer> readMaterials(ConfigurationSection section, String path) {
        if (section == null) {
            throw new IllegalStateException(path + " must be configured");
        }
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String materialName = key.toUpperCase(Locale.ROOT);
            if (Material.matchMaterial(materialName) == null) {
                throw new IllegalStateException(path + " contains unknown material " + key);
            }
            int amount = section.getInt(key, 0);
            if (amount < 1) {
                throw new IllegalStateException(path + "." + key + " must be positive");
            }
            values.put(materialName, amount);
        }
        if (values.isEmpty() && !path.endsWith("resource-bundle")) {
            throw new IllegalStateException(path + " must not be empty");
        }
        return values;
    }

    private static ConfigurationSection requiredSection(JavaPlugin plugin, String path) {
        return requiredSection(plugin.getConfig(), path);
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("Missing configuration section: " + path);
        }
        return section;
    }

    private static int positiveInt(ConfigurationSection section, String key) {
        int value = section.getInt(key, 0);
        if (value < 1) {
            throw new IllegalStateException(key + " must be positive");
        }
        return value;
    }

    private static int nonNegative(ConfigurationSection section, String key) {
        int value = section.getInt(key, -1);
        if (value < 0) {
            throw new IllegalStateException(key + " must be non-negative");
        }
        return value;
    }

    private static double positiveDouble(ConfigurationSection section, String key) {
        double value = section.getDouble(key, 0.0D);
        if (!(value > 0.0D) || Double.isInfinite(value) || Double.isNaN(value)) {
            throw new IllegalStateException(key + " must be positive");
        }
        return value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record WaveDefinition(int endermen, int endermites, int shulkers, int eliteEndermen) {
        public int total() {
            return endermen + endermites + shulkers + eliteEndermen;
        }
    }
}
