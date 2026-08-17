package me.copimine.endevent;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Crash-safe local event state store.  The file is small, written as UTF-8,
 * fsynced before the atomic rename, and backed up before replacement.
 */
public final class EventStateStore {
    private final Path path;
    private final Path backupPath;
    private final int schemaVersion;

    public EventStateStore(Path dataFolder, String fileName, String backupFileName, int schemaVersion) {
        this.path = dataFolder.resolve(fileName);
        this.backupPath = dataFolder.resolve(backupFileName);
        this.schemaVersion = schemaVersion;
    }

    public LoadResult load() {
        if (!Files.exists(path) && !Files.exists(backupPath)) {
            return LoadResult.absent(EventSnapshot.empty(schemaVersion));
        }
        LoadResult primary = read(path, "PRIMARY");
        if (primary.valid()) {
            return primary;
        }
        LoadResult backup = read(backupPath, "BACKUP");
        if (backup.valid()) {
            return backup;
        }
        EventSnapshot recovery = EventSnapshot.empty(schemaVersion);
        recovery = new EventSnapshot(
                schemaVersion, recovery.eventId(), recovery.generation(), "RECOVERY_REQUIRED",
                recovery.worldName(), recovery.coreX(), recovery.coreY(), recovery.coreZ(),
                recovery.coreBlockData(), recovery.requiredPlayers(), recovery.arenaMinX(), recovery.arenaMinY(),
                recovery.arenaMinZ(), recovery.arenaMaxX(), recovery.arenaMaxY(), recovery.arenaMaxZ(),
                recovery.resourceRequirements(), recovery.depositedResources(), recovery.pads(),
                recovery.resourceContributors(), recovery.officialRewardRoster(), recovery.rewardStatuses(),
                recovery.shardCooldowns(),
                recovery.coreCharged(), recovery.halfHealthTriggered(), recovery.controlSpellUnlocked(),
                recovery.finalDrainTriggered(), recovery.finalDrainApplied(), recovery.endUnlocked(),
                recovery.officialBossDeathCommitted(), recovery.bossLootCommitted(), recovery.returnStoneStatus(),
                recovery.victoryStep(), recovery.updatedAt(),
                "Both primary and backup event state files are invalid.", recovery.participants(),
                recovery.finalDrainTargets(), recovery.finalDrainAppliedPlayers());
        return LoadResult.invalid(recovery, primary.reason() + "; " + backup.reason());
    }

    public CompletableFuture<Boolean> saveAsync(EventSnapshot snapshot, Executor executor) {
        if (snapshot == null || executor == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(save(snapshot));
                } catch (RuntimeException error) {
                    result.complete(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.complete(false);
        }
        return result;
    }

    public boolean save(EventSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", snapshot.schemaVersion());
            yaml.set("event.event-id", snapshot.eventId());
            yaml.set("event.generation", snapshot.generation());
            yaml.set("event.phase", snapshot.phase());
            yaml.set("event.world", snapshot.worldName());
            yaml.set("event.core.x", snapshot.coreX());
            yaml.set("event.core.y", snapshot.coreY());
            yaml.set("event.core.z", snapshot.coreZ());
            yaml.set("event.core.block-data", snapshot.coreBlockData());
            yaml.set("event.required-players", snapshot.requiredPlayers());
            yaml.set("event.arena.min-x", snapshot.arenaMinX());
            yaml.set("event.arena.min-y", snapshot.arenaMinY());
            yaml.set("event.arena.min-z", snapshot.arenaMinZ());
            yaml.set("event.arena.max-x", snapshot.arenaMaxX());
            yaml.set("event.arena.max-y", snapshot.arenaMaxY());
            yaml.set("event.arena.max-z", snapshot.arenaMaxZ());
            yaml.set("event.core-charged", snapshot.coreCharged());
            yaml.set("event.half-health-triggered", snapshot.halfHealthTriggered());
            yaml.set("event.control-spell-unlocked", snapshot.controlSpellUnlocked());
            yaml.set("event.final-drain-triggered", snapshot.finalDrainTriggered());
            yaml.set("event.final-drain-applied", snapshot.finalDrainApplied());
            yaml.set("event.end-unlocked", snapshot.endUnlocked());
            yaml.set("event.official-boss-death-committed", snapshot.officialBossDeathCommitted());
            yaml.set("event.boss-loot-committed", snapshot.bossLootCommitted());
            yaml.set("event.return-stone-status", snapshot.returnStoneStatus());
            yaml.set("event.victory-step", snapshot.victoryStep());
            yaml.set("event.updated-at", snapshot.updatedAt());
            yaml.set("event.recovery-reason", snapshot.recoveryReason());
            yaml.set("resources.requirements", snapshot.resourceRequirements());
            yaml.set("resources.deposited", snapshot.depositedResources());
            yaml.set("participants.resource-contributors", uuidStrings(snapshot.resourceContributors()));
            yaml.set("participants.official-roster", uuidStrings(snapshot.officialRewardRoster()));
            yaml.set("participants.all", uuidStrings(snapshot.participants()));
            yaml.set("final-drain.targets", uuidDoubleMap(snapshot.finalDrainTargets()));
            yaml.set("final-drain.applied-players", uuidStrings(snapshot.finalDrainAppliedPlayers()));
            yaml.set("rewards.statuses", uuidStatusMap(snapshot.rewardStatuses()));
            yaml.set("rewards.shard-cooldowns", uuidLongMap(snapshot.shardCooldowns()));
            List<Map<String, Object>> pads = new ArrayList<>();
            for (EventSnapshot.PadSnapshot pad : snapshot.pads()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("x", pad.x());
                entry.put("y", pad.y());
                entry.put("z", pad.z());
                entry.put("radius", pad.radius());
                entry.put("angle", pad.angleRadians());
                entry.put("original-block-data", pad.originalBlockData());
                pads.add(entry);
            }
            yaml.set("pads", pads);
            writeAtomic(yaml.saveToString());
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private LoadResult read(Path source, String label) {
        if (!Files.exists(source)) {
            return LoadResult.invalid(EventSnapshot.empty(schemaVersion), label + " file is absent");
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
            if (!yaml.contains("event.phase") || yaml.getInt("schema-version", -1) > schemaVersion) {
                return LoadResult.invalid(EventSnapshot.empty(schemaVersion), label + " schema is unsupported");
            }
            return LoadResult.valid(fromYaml(yaml), label);
        } catch (RuntimeException error) {
            return LoadResult.invalid(EventSnapshot.empty(schemaVersion), label + " parse failed: " + error.getMessage());
        }
    }

    private EventSnapshot fromYaml(YamlConfiguration yaml) {
        Map<String, Integer> requirements = integers(yaml.getConfigurationSection("resources.requirements"));
        Map<String, Integer> deposited = integers(yaml.getConfigurationSection("resources.deposited"));
        List<EventSnapshot.PadSnapshot> pads = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("pads")) {
            pads.add(new EventSnapshot.PadSnapshot(
                    integer(raw.get("x")), integer(raw.get("y")), integer(raw.get("z")),
                    decimal(raw.get("radius")), decimal(raw.get("angle")),
                    String.valueOf(raw.containsKey("original-block-data") ? raw.get("original-block-data") : "")));
        }
        Set<UUID> contributors = uuids(yaml.getStringList("participants.resource-contributors"));
        Set<UUID> roster = uuids(yaml.getStringList("participants.official-roster"));
        Set<UUID> participants = uuids(yaml.getStringList("participants.all"));
        Map<UUID, Double> finalDrainTargets = uuidDoubles(yaml.getConfigurationSection("final-drain.targets"));
        Set<UUID> finalDrainAppliedPlayers = uuids(yaml.getStringList("final-drain.applied-players"));
        Map<UUID, String> statuses = uuidStatuses(yaml.getConfigurationSection("rewards.statuses"));
        Map<UUID, Long> cooldowns = uuidLongs(yaml.getConfigurationSection("rewards.shard-cooldowns"));
        return new EventSnapshot(
                yaml.getInt("schema-version", schemaVersion),
                yaml.getString("event.event-id", ""),
                yaml.getLong("event.generation", 0L),
                yaml.getString("event.phase", "RECOVERY_REQUIRED"),
                yaml.getString("event.world", ""),
                yaml.getInt("event.core.x"), yaml.getInt("event.core.y"), yaml.getInt("event.core.z"),
                yaml.getString("event.core.block-data", ""), yaml.getInt("event.required-players"),
                yaml.getInt("event.arena.min-x"), yaml.getInt("event.arena.min-y"), yaml.getInt("event.arena.min-z"),
                yaml.getInt("event.arena.max-x"), yaml.getInt("event.arena.max-y"), yaml.getInt("event.arena.max-z"),
                requirements, deposited, pads, contributors, roster, statuses, cooldowns,
                yaml.getBoolean("event.core-charged"), yaml.getBoolean("event.half-health-triggered"),
                yaml.getBoolean("event.control-spell-unlocked"), yaml.getBoolean("event.final-drain-triggered"),
                yaml.getBoolean("event.final-drain-applied"), yaml.getBoolean("event.end-unlocked"),
                yaml.getBoolean("event.official-boss-death-committed"),
                yaml.getBoolean("event.boss-loot-committed"), yaml.getString("event.return-stone-status", "PENDING"),
                yaml.getString("event.victory-step", "NONE"), yaml.getLong("event.updated-at", 0L),
                yaml.getString("event.recovery-reason", ""), participants, finalDrainTargets,
                finalDrainAppliedPlayers);
    }

    private void writeAtomic(String content) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(
                temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            channel.write(java.nio.ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        if (Files.exists(path)) {
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<String> uuidStrings(Set<UUID> values) {
        return values.stream().map(UUID::toString).sorted().toList();
    }

    private static Map<String, String> uuidStatusMap(Map<UUID, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey().toString(), entry.getValue()));
        return result;
    }

    private static Set<UUID> uuids(List<String> values) {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // A malformed reward/participant identity is not admitted.
            }
        }
        return result;
    }

    private static Map<UUID, String> uuidStatuses(ConfigurationSection section) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(UUID.fromString(key), section.getString(key, "PENDING"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static Map<String, Long> uuidLongMap(Map<UUID, Long> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey().toString(), entry.getValue()));
        return result;
    }

    private static Map<String, Double> uuidDoubleMap(Map<UUID, Double> values) {
        Map<String, Double> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey().toString(), entry.getValue()));
        return result;
    }

    private static Map<UUID, Long> uuidLongs(ConfigurationSection section) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static Map<UUID, Double> uuidDoubles(ConfigurationSection section) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static Map<String, Integer> integers(ConfigurationSection section) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.getInt(key));
            }
        }
        return result;
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    public record LoadResult(boolean valid, EventSnapshot snapshot, String source, String reason) {
        static LoadResult valid(EventSnapshot snapshot, String source) {
            return new LoadResult(true, snapshot, source, "");
        }

        static LoadResult absent(EventSnapshot snapshot) {
            return new LoadResult(true, snapshot, "ABSENT", "");
        }

        static LoadResult invalid(EventSnapshot snapshot, String reason) {
            return new LoadResult(false, snapshot, "INVALID", reason == null ? "" : reason);
        }
    }
}
