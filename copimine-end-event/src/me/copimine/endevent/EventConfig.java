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
import me.copimine.endevent.domain.BossHealthPolicy;
import me.copimine.endevent.domain.BossFinalStrikePolicy;
import me.copimine.endevent.domain.AbyssAnchorPolicy;
import me.copimine.endevent.domain.PortalCapturePolicy;

/** Validated, immutable runtime configuration for one End Rift server. */
public record EventConfig(
        int schemaVersion,
        String environment,
        String stateFile,
        String backupStateFile,
        Map<String, Integer> resourceRequirements,
        int startRitualTimeoutSeconds,
        int intermissionSeconds,
        int minPlayers,
        int maxPlayers,
        List<Double> padRadii,
        double padOccupancyRadius,
        double portalCaptureDecayRate,
        String arenaWorld,
        double arenaRadius,
        double arenaVerticalRadius,
        double bossRadius,
        double containmentRadius,
        int waveHardCap,
        double spiderHealthBonus,
        double spiderAttackDamageBonus,
        double skeletonHealthBonus,
        double skeletonAttackDamageBonus,
        double skeletonEliteHealth,
        double skeletonEliteAttackDamageBonus,
        double musicVolume,
        MusicTrack ritualWaitMusic,
        Map<String, MusicTrack> phaseMusic,
        WaveDefinition wave1,
        WaveDefinition wave2,
        WaveDefinition wave3,
        WaveDefinition wave4,
        WaveDefinition wave5,
        WaveDefinition wave6,
        WaveDefinition wave7,
        Map<Integer, Map<String, Integer>> waveRewards,
        Map<Integer, Double> waveRewardSharedRareChances,
        Map<String, Integer> waveMobLoot,
        Map<String, Integer> eliteLoot,
        Map<String, Integer> testLoot,
        Map<String, Map<String, LootEntry>> lootProfiles,
        double bossHealth,
        double bossAttackDamageBonus,
        int bossTargetMinSeconds,
        int bossTargetMaxSeconds,
        int bossSpellMinSeconds,
        int bossSpellMaxSeconds,
        int bossSpellTelegraphTicks,
        int bossRecentTargetMemory,
        int bossTargetLockSeconds,
        int bossTeleportCooldownSeconds,
        MiniBossTuning miniBossTuning,
        int maxSummonedServants,
        int bossXp,
        int maxXpOrbs,
        String shardItemId,
        int shardChannelSeconds,
        int shardCooldownSeconds,
        int abyssAnchorCooldownSeconds,
        double nightCloakChance,
        String returnStoneItemId,
        Map<String, Integer> resourceBundle,
        String portalWorld,
        double portalX,
        double portalY,
        double portalZ,
        float portalYaw,
        float portalPitch,
        String clientBossId,
        String bridgeChannel,
        BossFinalStrikeTuning finalStrikeTuning,
        RiftObeliskTuning riftObeliskTuning,
        TentacleGuardianTuning tentacleGuardianTuning) {

    public EventConfig {
        resourceRequirements = Map.copyOf(resourceRequirements);
        resourceBundle = Map.copyOf(resourceBundle);
        padRadii = List.copyOf(padRadii);
        waveRewards = copyWaveRewards(waveRewards);
        waveRewardSharedRareChances = Map.copyOf(waveRewardSharedRareChances == null
                ? Map.of() : waveRewardSharedRareChances);
        waveMobLoot = Map.copyOf(waveMobLoot);
        eliteLoot = Map.copyOf(eliteLoot);
        testLoot = Map.copyOf(testLoot);
        lootProfiles = copyLootProfiles(lootProfiles);
        phaseMusic = Map.copyOf(phaseMusic == null ? Map.of() : phaseMusic);
        if (miniBossTuning == null) {
            throw new IllegalArgumentException("mini boss tuning is required");
        }
        if (riftObeliskTuning == null) {
            throw new IllegalArgumentException("Rift Obelisk tuning is required");
        }
        if (tentacleGuardianTuning == null) {
            throw new IllegalArgumentException("Tentacle Guardian tuning is required");
        }
        if (finalStrikeTuning == null) {
            throw new IllegalArgumentException("Boss final strike tuning is required");
        }
        if (!Double.isFinite(nightCloakChance) || nightCloakChance < 0.0D || nightCloakChance > 1.0D) {
            throw new IllegalArgumentException("night cloak chance must be between 0 and 1");
        }
        if (abyssAnchorCooldownSeconds < AbyssAnchorPolicy.MIN_COOLDOWN_SECONDS
                || abyssAnchorCooldownSeconds > AbyssAnchorPolicy.MAX_COOLDOWN_SECONDS) {
            throw new IllegalArgumentException("abyss anchor cooldown is outside the safe bounds");
        }
        if (schemaVersion != 4 || wave6 == null || wave7 == null) {
            throw new IllegalArgumentException("schema 4 requires the seven current wave definitions");
        }
    }

    public boolean isCurrentFlow() {
        return schemaVersion == 4 && wave6 != null && wave7 != null;
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
        BossFinalStrikeTuning finalStrikeTuning = bossFinalStrikeTuning(
                requiredSection(boss, "final-strike"));
        RiftObeliskTuning riftObeliskTuning = riftObeliskTuning(requiredSection(boss, "rift-obelisks"));
        TentacleGuardianTuning tentacleGuardianTuning = tentacleGuardianTuning(
                requiredSection(boss, "tentacle-guardians"));
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
        double portalCaptureDecayRate = boundedPortalCaptureDecayRate(
                ritual.getDouble("portal-capture-decay-rate", PortalCapturePolicy.DEFAULT_DECAY_RATE));
        int waveCap = positiveInt(waves, "hard-cap");
        double health = positiveDouble(boss, "health");
        if (Math.abs(health - BossHealthPolicy.MIN_HEALTH) > 0.000001D) {
            throw new IllegalStateException("boss.health must remain 5000 for the roster scaling policy");
        }
        double spiderHealthBonus = nonNegativeDouble(mobs.getConfigurationSection("spider"), "health-bonus");
        double spiderAttackDamageBonus = nonNegativeDouble(mobs.getConfigurationSection("spider"), "attack-damage-bonus");
        ConfigurationSection skeleton = mobs.getConfigurationSection("skeleton");
        double skeletonHealthBonus = nonNegativeDouble(skeleton, "health-bonus");
        double skeletonAttackDamageBonus = nonNegativeDouble(skeleton, "attack-damage-bonus");
        double skeletonEliteHealth = positiveDouble(skeleton, "elite-health");
        double skeletonEliteAttackDamageBonus = nonNegativeDouble(skeleton, "elite-attack-damage-bonus");
        double musicVolume = boundedVolume(music.getDouble("volume", 0.85D));
        MusicTrack ritualWaitMusic = musicTrack(music, "ritual-wait");
        int schemaVersion = persistenceSchemaVersion(plugin);
        Map<String, MusicTrack> phaseMusic = readPhaseMusic(
                requiredSection(music, "phase"), schemaVersion);
        ConfigurationSection phaseThresholds = requiredSection(boss, "phase-thresholds");
        double huntFraction = phaseFraction(phaseThresholds, "hunt");
        double lastSealFraction = phaseFraction(phaseThresholds, "last-seal");
        if (!(lastSealFraction < huntFraction && huntFraction < 1.0D)) {
            throw new IllegalStateException(
                    "boss.phase-thresholds must satisfy 0 <= last-seal < hunt < 1");
        }
        int[] target = secondsRange(boss, "target-rotation-seconds");
        int[] spells = secondsRange(boss, "spell-cooldown-seconds");
        int bossRecentTargetMemory = positiveInt(boss, "recent-target-memory");
        int bossTargetLockSeconds = positiveInt(boss, "target-lock-seconds");
        int bossTeleportCooldownSeconds = positiveInt(boss, "teleport-cooldown-seconds");
        if (boss.getInt("spell-telegraph-ticks", 30) < 1) {
            throw new IllegalStateException("boss.spell-telegraph-ticks must be positive");
        }
        MiniBossTuning miniBossTuning = miniBossTuning(miniBosses);
        LinkedHashMap<String, Integer> waveMobLoot = readOptionalMaterials(eventLoot, "wave-mob");
        LinkedHashMap<String, Integer> eliteLoot = readOptionalMaterials(eventLoot, "elite");
        LinkedHashMap<String, Integer> waveSevenLoot = readOptionalMaterials(eventLoot, "wave-7");
        LinkedHashMap<String, Integer> testLoot = readOptionalMaterials(eventLoot, "test");
        LinkedHashMap<String, Map<String, LootEntry>> lootProfiles = new LinkedHashMap<>();
        lootProfiles.put("common-enderman", readLootProfiles(eventLootRolls, "common-enderman", waveMobLoot));
        lootProfiles.put("spider", readLootProfiles(eventLootRolls, "spider", waveMobLoot));
        lootProfiles.put("skeleton", readLootProfiles(eventLootRolls, "skeleton", waveMobLoot));
        lootProfiles.put("elite-enderman", readLootProfiles(eventLootRolls, "elite-enderman", eliteLoot));
        lootProfiles.put("elite-skeleton", readLootProfiles(eventLootRolls, "elite-skeleton", eliteLoot));
        lootProfiles.put("reality-split", readLootProfiles(eventLootRolls, "wave-7", waveSevenLoot));
        lootProfiles.put("test", readLootProfiles(eventLootRolls, "test", testLoot));
        String environment = text(plugin.getConfig().getString("environment", ""), "");
        if (!"local".equalsIgnoreCase(environment) && !"staging".equalsIgnoreCase(environment)) {
            throw new IllegalStateException(
                    "End Rift Event environment must be explicitly local or staging; got '" + environment + "'");
        }

        return new EventConfig(
                schemaVersion,
                environment,
                text(persistence.getString("file", "event-state.yml"), "event-state.yml"),
                text(persistence.getString("backup-file", "event-state.yml.bak"), "event-state.yml.bak"),
                requirements,
                positiveInt(ritual, "start-ritual-timeout-seconds"),
                positiveInt(ritual, "intermission-seconds"),
                minPlayers,
                maxPlayers,
                radii,
                positiveDouble(ritual, "pad-occupancy-radius"),
                portalCaptureDecayRate,
                text(arena.getString("world", "CopiMine"), "CopiMine"),
                positiveDouble(arena, "radius"),
                positiveDouble(arena, "vertical-radius"),
                positiveDouble(arena, "boss-radius"),
                positiveDouble(arena, "containment-radius"),
                waveCap,
                spiderHealthBonus,
                spiderAttackDamageBonus,
                skeletonHealthBonus,
                skeletonAttackDamageBonus,
                skeletonEliteHealth,
                skeletonEliteAttackDamageBonus,
                musicVolume,
                ritualWaitMusic,
                phaseMusic,
                wave(waves, "wave-1"),
                wave(waves, "wave-2"),
                wave(waves, "wave-3"),
                wave(waves, "wave-4"),
                wave(waves, "wave-5"),
                wave(waves, "wave-6"),
                wave(waves, "wave-7"),
                readWaveRewards(plugin.getConfig().getConfigurationSection("wave-rewards"), schemaVersion),
                readWaveRewardSharedRareChances(
                        plugin.getConfig().getConfigurationSection("wave-reward-shared-rare"), schemaVersion),
                waveMobLoot,
                eliteLoot,
                testLoot,
                lootProfiles,
                health,
                boss.getDouble("attack-damage-bonus", 3.0D),
                 target[0], target[1], spells[0], spells[1],
                 positiveInt(boss, "spell-telegraph-ticks"), bossRecentTargetMemory,
                 bossTargetLockSeconds,
                bossTeleportCooldownSeconds, miniBossTuning,
                Math.max(0, boss.getInt("max-summoned-servants", 4)),
                Math.max(0, rewards.getInt("boss-xp", 3000)),
                Math.max(0, rewards.getInt("max-xp-orbs", 20)),
                text(rewards.getString("shard-item-id", "rift_core_shard"), "rift_core_shard"),
                positiveInt(rewards, "shard-channel-seconds"),
                positiveInt(rewards, "shard-cooldown-seconds"),
                boundedSeconds(rewards, "abyss-anchor-cooldown-seconds",
                        AbyssAnchorPolicy.DEFAULT_COOLDOWN_SECONDS, AbyssAnchorPolicy.MAX_COOLDOWN_SECONDS),
                boundedChance(rewards.getDouble("night-cloak-chance", 0.30D),
                        "rewards.night-cloak-chance"),
                text(rewards.getString("return-stone-item-id", "return_stone"), "return_stone"),
                readMaterials(rewards.getConfigurationSection("resource-bundle"), "rewards.resource-bundle"),
                text(portal.getString("world", "CopiMine_the_end"), "CopiMine_the_end"),
                portal.getDouble("x", 0.5D), portal.getDouble("y", 80.0D), portal.getDouble("z", 0.5D),
                (float) portal.getDouble("yaw", 0.0D), (float) portal.getDouble("pitch", 0.0D),
                text(client.getString("boss-id", "END_RIFT_GUARDIAN"), "END_RIFT_GUARDIAN"),
                text(client.getString("bridge-channel", "copimine:client_bridge"), "copimine:client_bridge"),
                finalStrikeTuning,
                riftObeliskTuning,
                tentacleGuardianTuning);
    }

    private static WaveDefinition wave(ConfigurationSection parent, String key) {
        ConfigurationSection section = requiredSection(parent, key);
        return new WaveDefinition(
                nonNegative(section, "endermen"),
                nonNegative(section, "spiders"),
                nonNegative(section, "skeletons"),
                nonNegative(section, "elite-endermen"),
                nonNegative(section, "elite-skeletons"));
    }

    private static Map<Integer, Map<String, Integer>> readWaveRewards(
            ConfigurationSection parent, int schemaVersion) {
        if (parent == null) {
            throw new IllegalStateException("Missing configuration section: wave-rewards");
        }
        LinkedHashMap<Integer, Map<String, Integer>> rewards = new LinkedHashMap<>();
        int maxWave = 7;
        for (int wave = 1; wave <= maxWave; wave++) {
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

    private static Map<Integer, Double> readWaveRewardSharedRareChances(
            ConfigurationSection parent, int schemaVersion) {
        LinkedHashMap<Integer, Double> chances = new LinkedHashMap<>();
        int maxWave = 7;
        for (int wave = 1; wave <= maxWave; wave++) {
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

    private static double boundedChance(double value, String key) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalStateException(key + " must be between 0 and 1");
        }
        return value;
    }

    private static double phaseFraction(ConfigurationSection parent, String key) {
        double value = parent.getDouble(key, Double.NaN);
        if (!Double.isFinite(value) || value <= 0.0D || value >= 1.0D) {
            throw new IllegalStateException("boss.phase-thresholds." + key
                    + " must be a fraction between 0 and 1");
        }
        return value;
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

    private static RiftObeliskTuning riftObeliskTuning(ConfigurationSection section) {
        List<String> stages = section.getStringList("stages").stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (!stages.equals(List.of("WAVE_4"))) {
            throw new IllegalStateException(
                    "boss.rift-obelisks.stages must contain only WAVE_4");
        }
        int[] cooldown = secondsRange(section, "cooldown-seconds");
        int health = section.getInt("health", -1);
        int maxActive = section.getInt("max-active", -1);
        double pulseRadius = section.getDouble("pulse-radius", -1.0D);
        int pulseInterval = section.getInt("pulse-interval-ticks", -1);
        int fireInterval = section.getInt("fire-interval-ticks", -1);
        int maxFireballs = section.getInt("max-active-fireballs", -1);
        double fireballDamage = section.getDouble("fireball-damage", -1.0D);
        int blindnessTicks = section.getInt("blindness-ticks", -1);
        int debuffTicks = section.getInt("debuff-ticks", -1);
        int spawnTelegraphTicks = section.getInt("spawn-telegraph-ticks", -1);
        int destructionDelayTicks = section.getInt("destruction-delay-ticks", -1);
        double minDistance = section.getDouble("min-distance", -1.0D);
        if (health != 3 || maxActive < 1 || maxActive > 6
                || !(pulseRadius > 0.0D) || pulseRadius > 5.0D
                || pulseInterval < 10 || pulseInterval > 200
                || fireInterval < 20 || fireInterval > 400
                || maxFireballs < 1 || maxFireballs > 8
                || !(fireballDamage > 0.0D) || fireballDamage > 40.0D
                || blindnessTicks < 1 || blindnessTicks > 200
                || debuffTicks < 1 || debuffTicks > 200
                || spawnTelegraphTicks < 5 || spawnTelegraphTicks > 100
                || destructionDelayTicks < 1 || destructionDelayTicks > 40
                || !(minDistance >= 2.0D) || minDistance > 12.0D) {
            throw new IllegalStateException("boss.rift-obelisks contains unsafe bounds");
        }
        return new RiftObeliskTuning(
                section.getBoolean("enabled", true), stages,
                cooldown[0], cooldown[1], health, maxActive, pulseRadius,
                pulseInterval, fireInterval, maxFireballs, fireballDamage,
                blindnessTicks, debuffTicks, spawnTelegraphTicks,
                destructionDelayTicks, minDistance);
    }

    private static BossFinalStrikeTuning bossFinalStrikeTuning(ConfigurationSection section) {
        double damage = section.getDouble("damage", BossFinalStrikePolicy.DEFAULT_DAMAGE);
        double radius = section.getDouble("radius", 4.0D);
        int witherTicks = section.getInt("wither-ticks", 60);
        if (damage <= 0.0D || damage > BossFinalStrikePolicy.MAX_DAMAGE
                || !Double.isFinite(damage)
                || radius <= 0.0D || radius > 6.0D
                || witherTicks < 0 || witherTicks > 200) {
            throw new IllegalStateException("boss.final-strike contains unsafe bounds");
        }
        return new BossFinalStrikeTuning(section.getBoolean("enabled", true), damage, radius, witherTicks);
    }

    private static TentacleGuardianTuning tentacleGuardianTuning(ConfigurationSection section) {
        int damageWindowTicks = section.getInt("damage-window-ticks", -1);
        int respawnDelayTicks = section.getInt("respawn-delay-ticks", -1);
        int attackIntervalTicks = section.getInt("attack-interval-ticks", -1);
        int attackStaggerTicks = section.getInt("attack-stagger-ticks", -1);
        double hitboxWidth = section.getDouble("hitbox-width", -1.0D);
        double hitboxHeight = section.getDouble("hitbox-height", -1.0D);
        if (damageWindowTicks < 100 || damageWindowTicks > 600
                || respawnDelayTicks < 400 || respawnDelayTicks > 1600
                || attackIntervalTicks < 100 || attackIntervalTicks > 400
                || attackStaggerTicks < 0 || attackStaggerTicks > 80
                || !Double.isFinite(hitboxWidth) || hitboxWidth < 0.5D || hitboxWidth > 2.5D
                || !Double.isFinite(hitboxHeight) || hitboxHeight < 1.5D || hitboxHeight > 5.0D) {
            throw new IllegalStateException("boss.tentacle-guardians contains unsafe bounds");
        }
        return new TentacleGuardianTuning(section.getBoolean("enabled", true),
                damageWindowTicks, respawnDelayTicks, attackIntervalTicks,
                attackStaggerTicks, hitboxWidth, hitboxHeight);
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
            ConfigurationSection parent, String key, Map<String, Integer> defaults) {
        LinkedHashMap<String, LootEntry> values = new LinkedHashMap<>();
        ConfigurationSection section = parent == null ? null : parent.getConfigurationSection(key);
        if (section == null) {
            for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
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

    private static int boundedSeconds(ConfigurationSection section, String key, int fallback, int maximum) {
        int value = section.getInt(key, fallback);
        if (value < 1 || value > maximum) {
            throw new IllegalStateException(key + " must be between 1 and " + maximum + " seconds");
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

    private static double boundedPortalCaptureDecayRate(double value) {
        if (!Double.isFinite(value) || value <= 0.0D || value > 1.0D) {
            throw new IllegalStateException(
                    "ritual.portal-capture-decay-rate must be finite and between 0 and 1");
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

    private static Map<String, MusicTrack> readPhaseMusic(ConfigurationSection parent, int schemaVersion) {
        List<String> requiredKeys = new ArrayList<>(List.of(
                "wave-1", "wave-2", "wave-3", "wave-4", "wave-5", "wave-6", "wave-7",
                "intermission-1", "intermission-2", "intermission-3", "intermission-5",
                "intermission-6", "core-restoration", "pre-boss-cooldown", "boss-cinematic",
                "boss-awakening", "boss-hunt", "boss-rift", "boss-overload", "boss-rage",
                "boss-last-seal", "boss-finish", "victory"));
        LinkedHashMap<String, MusicTrack> tracks = new LinkedHashMap<>();
        for (String key : requiredKeys) {
            tracks.put(key, musicTrack(parent, key));
        }
        return tracks;
    }

    private static int persistenceSchemaVersion(JavaPlugin plugin) {
        ConfigurationSection persistence = requiredSection(plugin, "persistence");
        int value = persistence.getInt("schema-version", 1);
        if (value != 4) {
            throw new IllegalStateException("persistence.schema-version must be exactly 4");
        }
        return value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record WaveDefinition(int endermen, int spiders, int skeletons,
                                 int eliteEndermen, int eliteSkeletons) {
        public int total() {
            return endermen + spiders + skeletons + eliteEndermen + eliteSkeletons;
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

    public MusicTrack phaseMusic(String phaseKey) {
        return phaseMusic.get(phaseKey);
    }

    /**
     * Current-flow aliases are resolved from canonical phase keys only. They
     * deliberately do not read the removed aggregate music sections.
     */
    public MusicTrack wavesMusic() {
        return phaseMusic("wave-1");
    }

    public MusicTrack bossMusic() {
        return phaseMusic("boss-awakening");
    }

    public MusicTrack victoryMusic() {
        return phaseMusic("victory");
    }

    public List<MusicTrack> allMusicTracks() {
        return List.copyOf(new java.util.LinkedHashSet<>(phaseMusic.values()));
    }

    public Map<String, Integer> waveReward(int wave) {
        return waveRewards.getOrDefault(wave, Map.of());
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

    public record BossFinalStrikeTuning(
            boolean enabled,
            double damage,
            double radius,
            int witherTicks) {
        public BossFinalStrikeTuning {
            damage = BossFinalStrikePolicy.validatedDamage(damage);
            if (damage <= 0.0D || radius <= 0.0D || radius > 6.0D
                    || witherTicks < 0 || witherTicks > 200) {
                throw new IllegalArgumentException("invalid boss final strike tuning");
            }
        }
    }

    public record RiftObeliskTuning(
            boolean enabled,
            List<String> stages,
            int cooldownMinSeconds,
            int cooldownMaxSeconds,
            int health,
            int maxActive,
            double pulseRadius,
            int pulseIntervalTicks,
            int fireIntervalTicks,
            int maxActiveFireballs,
            double fireballDamage,
            int blindnessTicks,
            int debuffTicks,
            int spawnTelegraphTicks,
            int destructionDelayTicks,
            double minDistance) {
        public RiftObeliskTuning {
            stages = List.copyOf(stages == null ? List.of() : stages);
            if (health != 3 || maxActive < 1 || maxActive > 6
                    || cooldownMinSeconds < 1 || cooldownMaxSeconds < cooldownMinSeconds
                    || pulseRadius <= 0.0D || pulseRadius > 5.0D
                    || pulseIntervalTicks < 10 || fireIntervalTicks < 20
                    || maxActiveFireballs < 1 || maxActiveFireballs > 8
                    || fireballDamage <= 0.0D || blindnessTicks < 1 || debuffTicks < 1
                    || spawnTelegraphTicks < 5 || destructionDelayTicks < 1
                    || minDistance < 2.0D || minDistance > 12.0D
                    || !stages.equals(List.of("WAVE_4"))) {
                throw new IllegalArgumentException("invalid Rift Obelisk tuning");
            }
        }

        public int currentMaxActive() {
            return maxActive;
        }
    }

    public record TentacleGuardianTuning(
            boolean enabled,
            int damageWindowTicks,
            int respawnDelayTicks,
            int attackIntervalTicks,
            int attackStaggerTicks,
            double hitboxWidth,
            double hitboxHeight) {
        public TentacleGuardianTuning {
            if (damageWindowTicks < 100 || respawnDelayTicks < 400
                    || attackIntervalTicks < 100 || attackStaggerTicks < 0
                    || hitboxWidth < 0.5D || hitboxHeight < 1.5D) {
                throw new IllegalArgumentException("invalid Tentacle Guardian tuning");
            }
        }
    }
}
