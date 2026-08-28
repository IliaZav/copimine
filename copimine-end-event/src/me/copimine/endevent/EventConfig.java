package me.copimine.endevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
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
        double arenaVerticalRadius,
        double bossRadius,
        double containmentRadius,
        int waveHardCap,
        double spiderHealthBonus,
        double spiderAttackDamageBonus,
        double musicVolume,
        MusicTrack wavesMusic,
        MusicTrack bossMusic,
        MusicTrack bossHalfMusic,
        MusicTrack bossFinalMusic,
        MusicTrack victoryMusic,
        WaveDefinition wave1,
        WaveDefinition wave2,
        WaveDefinition wave3,
        WaveDefinition wave4,
        WaveDefinition wave5,
        WaveDefinition finalWave,
        Map<Integer, Map<String, Integer>> waveRewards,
        Map<Integer, Double> waveRewardSharedRareChances,
        Map<String, Integer> waveMobLoot,
        Map<String, Integer> eliteLoot,
        Map<String, Integer> finalWaveLoot,
        Map<String, Integer> testLoot,
        Map<String, Map<String, LootEntry>> lootProfiles,
        double bossHealth,
        double bossAttackDamageBonus,
        int debuffAmplifier,
        int bossTargetMinSeconds,
        int bossTargetMaxSeconds,
        int bossSpellMinSeconds,
        int bossSpellMaxSeconds,
        int bossSpellTelegraphTicks,
        int bossRecentTargetMemory,
        int bossTeleportCooldownSeconds,
        MiniBossTuning miniBossTuning,
        int finalRitualTelegraphTicks,
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
        waveRewards = copyWaveRewards(waveRewards);
        waveRewardSharedRareChances = Map.copyOf(waveRewardSharedRareChances == null
                ? Map.of() : waveRewardSharedRareChances);
        waveMobLoot = Map.copyOf(waveMobLoot);
        eliteLoot = Map.copyOf(eliteLoot);
        finalWaveLoot = Map.copyOf(finalWaveLoot);
        testLoot = Map.copyOf(testLoot);
        lootProfiles = copyLootProfiles(lootProfiles);
        if (miniBossTuning == null) {
            throw new IllegalArgumentException("mini boss tuning is required");
        }
        if (debuffAmplifier < 0 || debuffAmplifier > 3) {
            throw new IllegalArgumentException("debuff amplifier must be between 0 and 3");
        }
    }

    public static EventConfig load(JavaPlugin plugin) {
        ConfigurationSection resources = requiredSection(plugin, "resources");
        LinkedHashMap<String, Integer> requirements = readMaterials(resources, "resources");
        ConfigurationSection ritual = requiredSection(plugin, "ritual");
        ConfigurationSection arena = requiredSection(plugin, "arena");
        ConfigurationSection mobs = requiredSection(plugin, "mobs");
        ConfigurationSection waves = requiredSection(plugin, "waves");
        ConfigurationSection miniBosses = requiredSection(plugin, "mini-bosses");
        ConfigurationSection boss = requiredSection(plugin, "boss");
        ConfigurationSection rewards = requiredSection(plugin, "rewards");
        ConfigurationSection eventLoot = plugin.getConfig().getConfigurationSection("event-loot");
        ConfigurationSection eventLootRolls = plugin.getConfig().getConfigurationSection("event-loot-rolls");
        ConfigurationSection portal = requiredSection(plugin, "portal-room");
        ConfigurationSection client = requiredSection(plugin, "client");
        ConfigurationSection persistence = requiredSection(plugin, "persistence");
        ConfigurationSection music = requiredSection(plugin, "music");

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
        double spiderHealthBonus = nonNegativeDouble(mobs.getConfigurationSection("spider"), "health-bonus");
        double spiderAttackDamageBonus = nonNegativeDouble(mobs.getConfigurationSection("spider"), "attack-damage-bonus");
        double musicVolume = boundedVolume(music.getDouble("volume", 0.85D));
        MusicTrack wavesMusic = musicTrack(music, "waves");
        MusicTrack bossMusic = musicTrack(music, "boss");
        MusicTrack bossHalfMusic = musicTrack(music, "boss-half");
        MusicTrack bossFinalMusic = musicTrack(music, "boss-final");
        MusicTrack victoryMusic = musicTrack(music, "victory");
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
        int bossRecentTargetMemory = positiveInt(boss, "recent-target-memory");
        int bossTeleportCooldownSeconds = positiveInt(boss, "teleport-cooldown-seconds");
        if (boss.getInt("spell-telegraph-ticks", 30) < 1) {
            throw new IllegalStateException("boss.spell-telegraph-ticks must be positive");
        }
        MiniBossTuning miniBossTuning = miniBossTuning(miniBosses);
        int finalRitualTelegraphTicks = positiveInt(boss, "final-ritual-telegraph-ticks");
        LinkedHashMap<String, Integer> waveMobLoot = readOptionalMaterials(eventLoot, "wave-mob");
        LinkedHashMap<String, Integer> eliteLoot = readOptionalMaterials(eventLoot, "elite");
        LinkedHashMap<String, Integer> finalWaveLoot = readOptionalMaterials(eventLoot, "final-wave");
        LinkedHashMap<String, Integer> testLoot = readOptionalMaterials(eventLoot, "test");
        LinkedHashMap<String, Map<String, LootEntry>> lootProfiles = new LinkedHashMap<>();
        lootProfiles.put("common-enderman", readLootProfiles(eventLootRolls, "common-enderman", waveMobLoot));
        lootProfiles.put("spider", readLootProfiles(eventLootRolls, "spider", waveMobLoot));
        lootProfiles.put("elite-enderman", readLootProfiles(eventLootRolls, "elite-enderman", eliteLoot));
        lootProfiles.put("shulker", readLootProfiles(eventLootRolls, "shulker", finalWaveLoot));
        lootProfiles.put("final-wave", readLootProfiles(eventLootRolls, "final-wave", finalWaveLoot));
        lootProfiles.put("test", readLootProfiles(eventLootRolls, "test", testLoot));

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
                positiveDouble(arena, "vertical-radius"),
                positiveDouble(arena, "boss-radius"),
                positiveDouble(arena, "containment-radius"),
                waveCap,
                spiderHealthBonus,
                spiderAttackDamageBonus,
                musicVolume,
                wavesMusic,
                bossMusic,
                bossHalfMusic,
                bossFinalMusic,
                victoryMusic,
                wave(waves, "wave-1"),
                wave(waves, "wave-2"),
                wave(waves, "wave-3"),
                wave(waves, "wave-4"),
                wave(waves, "wave-5"),
                wave(waves, "final"),
                readWaveRewards(plugin.getConfig().getConfigurationSection("wave-rewards")),
                readWaveRewardSharedRareChances(plugin.getConfig().getConfigurationSection("wave-reward-shared-rare")),
                waveMobLoot,
                eliteLoot,
                finalWaveLoot,
                testLoot,
                lootProfiles,
                health,
                boss.getDouble("attack-damage-bonus", 3.0D),
                boundedAmplifier(boss.getInt("debuff-amplifier", 3)),
                target[0], target[1], spells[0], spells[1],
                positiveInt(boss, "spell-telegraph-ticks"), bossRecentTargetMemory,
                bossTeleportCooldownSeconds, miniBossTuning, finalRitualTelegraphTicks,
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
                nonNegative(section, "spiders"),
                nonNegative(section, "shulkers"),
                nonNegative(section, "elite-endermen"));
    }

    private static Map<Integer, Map<String, Integer>> readWaveRewards(ConfigurationSection parent) {
        if (parent == null) {
            throw new IllegalStateException("Missing configuration section: wave-rewards");
        }
        LinkedHashMap<Integer, Map<String, Integer>> rewards = new LinkedHashMap<>();
        for (int wave = 1; wave <= 5; wave++) {
            rewards.put(wave, readMaterials(requiredSection(parent, "wave-" + wave),
                    "wave-rewards.wave-" + wave));
        }
        return rewards;
    }

    private static Map<Integer, Map<String, Integer>> copyWaveRewards(
            Map<Integer, Map<String, Integer>> rewards) {
        LinkedHashMap<Integer, Map<String, Integer>> copied = new LinkedHashMap<>();
        if (rewards != null) {
            rewards.forEach((wave, materials) -> {
                if (wave != null && wave > 0) {
                    copied.put(wave, Map.copyOf(materials == null ? Map.of() : materials));
                }
            });
        }
        return Map.copyOf(copied);
    }

    private static Map<Integer, Double> readWaveRewardSharedRareChances(ConfigurationSection parent) {
        LinkedHashMap<Integer, Double> chances = new LinkedHashMap<>();
        for (int wave = 1; wave <= 5; wave++) {
            double chance = parent == null ? (wave >= 4 ? 0.25D : 0.0D)
                    : parent.getDouble("wave-" + wave, -1.0D);
            if (Double.isNaN(chance) || Double.isInfinite(chance) || chance < 0.0D || chance > 1.0D) {
                throw new IllegalStateException("wave-reward-shared-rare.wave-" + wave
                        + " must be between 0 and 1");
            }
            chances.put(wave, chance);
        }
        return Map.copyOf(chances);
    }

    private static int[] secondsRange(ConfigurationSection parent, String key) {
        List<Integer> values = parent.getIntegerList(key);
        if (values.size() != 2 || values.get(0) < 1 || values.get(1) < values.get(0)) {
            throw new IllegalStateException("" + key + " must be [min,max] positive seconds");
        }
        return new int[] {values.get(0), values.get(1)};
    }

    private static MiniBossTuning miniBossTuning(ConfigurationSection section) {
        int[] cooldown = secondsRange(section, "spell-cooldown-seconds");
        int telegraphTicks = positiveInt(section, "spell-telegraph-ticks");
        return new MiniBossTuning(
                cooldown[0], cooldown[1], telegraphTicks,
                positiveDouble(requiredSection(section, "rift-step"), "damage"),
                positiveDouble(requiredSection(section, "void-snare"), "damage"),
                positiveDouble(requiredSection(section, "echo-pulse"), "damage"));
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

    private static LinkedHashMap<String, Integer> readOptionalMaterials(ConfigurationSection parent, String key) {
        if (parent == null) {
            return new LinkedHashMap<>();
        }
        return readOptionalMaterialSection(parent.getConfigurationSection(key), "event-loot." + key);
    }

    private static LinkedHashMap<String, Integer> readOptionalMaterialSection(ConfigurationSection section, String path) {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
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
        return values;
    }

    private static LinkedHashMap<String, LootEntry> readLootProfiles(
            ConfigurationSection parent, String key, Map<String, Integer> legacy) {
        LinkedHashMap<String, LootEntry> values = new LinkedHashMap<>();
        ConfigurationSection section = parent == null ? null : parent.getConfigurationSection(key);
        if (section == null) {
            for (Map.Entry<String, Integer> entry : legacy.entrySet()) {
                values.put(entry.getKey(), new LootEntry(1.0D, entry.getValue(), entry.getValue()));
            }
            return values;
        }
        for (String rawKey : section.getKeys(false)) {
            String materialName = rawKey.toUpperCase(Locale.ROOT);
            if (Material.matchMaterial(materialName) == null) {
                throw new IllegalStateException("event-loot-rolls." + key + " contains unknown material " + rawKey);
            }
            ConfigurationSection item = section.getConfigurationSection(rawKey);
            if (item == null) {
                throw new IllegalStateException("event-loot-rolls." + key + "." + rawKey
                        + " must define chance/min/max");
            }
            values.put(materialName, new LootEntry(
                    item.getDouble("chance", -1.0D), item.getInt("min", 0), item.getInt("max", 0)));
        }
        return values;
    }

    private static Map<String, Map<String, LootEntry>> copyLootProfiles(
            Map<String, Map<String, LootEntry>> profiles) {
        LinkedHashMap<String, Map<String, LootEntry>> copied = new LinkedHashMap<>();
        if (profiles != null) {
            profiles.forEach((name, entries) -> copied.put(
                    name, Map.copyOf(entries == null ? Map.of() : entries)));
        }
        return Map.copyOf(copied);
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

    private static double nonNegativeDouble(ConfigurationSection section, String key) {
        if (section == null) {
            throw new IllegalStateException("Missing configuration section for " + key);
        }
        double value = section.getDouble(key, -1.0D);
        if (value < 0.0D || Double.isInfinite(value) || Double.isNaN(value)) {
            throw new IllegalStateException(key + " must be non-negative");
        }
        return value;
    }

    private static double boundedVolume(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalStateException("music.volume must be between 0 and 1");
        }
        return value;
    }

    private static int boundedAmplifier(int value) {
        if (value < 0 || value > 3) {
            throw new IllegalStateException("boss.debuff-amplifier must be between 0 and 3");
        }
        return value;
    }

    private static MusicTrack musicTrack(ConfigurationSection parent, String key) {
        ConfigurationSection section = requiredSection(parent, key);
        String soundId = text(section.getString("sound", ""), "");
        if (soundId.isBlank() || !soundId.contains(":")) {
            throw new IllegalStateException("music." + key + ".sound must be a namespaced sound id");
        }
        int loopSeconds = section.getInt("loop-seconds", -1);
        if (loopSeconds < 0) {
            throw new IllegalStateException("music." + key + ".loop-seconds must be non-negative");
        }
        return new MusicTrack(soundId, loopSeconds);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record WaveDefinition(int endermen, int spiders, int shulkers, int eliteEndermen) {
        public int total() {
            return endermen + spiders + shulkers + eliteEndermen;
        }
    }

    public record MusicTrack(String soundId, int loopSeconds) {
        public MusicTrack {
            soundId = soundId == null ? "" : soundId.trim();
            if (soundId.isBlank() || loopSeconds < 0) {
                throw new IllegalArgumentException("invalid event music track");
            }
        }
    }

    public record LootEntry(double chance, int minAmount, int maxAmount) {
        public LootEntry {
            if (Double.isNaN(chance) || Double.isInfinite(chance) || chance < 0.0D || chance > 1.0D
                    || minAmount < 1 || maxAmount < minAmount) {
                throw new IllegalArgumentException("invalid loot roll entry");
            }
        }

        public int roll(SplittableRandom random) {
            if (random == null || random.nextDouble() >= chance) {
                return 0;
            }
            return minAmount + random.nextInt(maxAmount - minAmount + 1);
        }
    }

    public Map<String, LootEntry> lootProfile(String profileId) {
        return lootProfiles.getOrDefault(profileId, Map.of());
    }

    public Map<String, Integer> waveReward(int wave) {
        return waveRewards.getOrDefault(wave, Map.of());
    }

    public int debuffAmplifier() {
        return debuffAmplifier;
    }

    public double waveRewardSharedRareChance(int wave) {
        return waveRewardSharedRareChances.getOrDefault(wave, 0.0D);
    }

    public record MiniBossTuning(
            int spellMinSeconds,
            int spellMaxSeconds,
            int spellTelegraphTicks,
            double riftStepDamage,
            double voidSnareDamage,
            double echoPulseDamage) {
        public MiniBossTuning {
            if (spellMinSeconds < 1 || spellMaxSeconds < spellMinSeconds || spellTelegraphTicks < 1
                    || !(riftStepDamage > 0.0D) || !(voidSnareDamage > 0.0D) || !(echoPulseDamage > 0.0D)) {
                throw new IllegalArgumentException("invalid mini boss tuning");
            }
        }
    }
}
