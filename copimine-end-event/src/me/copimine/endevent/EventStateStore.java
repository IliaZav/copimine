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
import java.util.concurrent.atomic.AtomicLong;
import me.copimine.endevent.migration.LegacyEndRiftSnapshotDecoder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Crash-safe schema-4 store. The writer emits only current-flow keys; older
 * files are read once through {@link LegacyEndRiftSnapshotDecoder} and are
 * never passed through the live event model.
 */
public final class EventStateStore {
    public static final int CURRENT_SCHEMA = EventSnapshot.CURRENT_SCHEMA;

    private final Path path;
    private final Path backupPath;
    private final int schemaVersion;
    /** Sequence save requests, not wall-clock timestamps, to prevent rollback. */
    private final AtomicLong requestedSaveSequence = new AtomicLong();
    private long committedSaveSequence;

    public EventStateStore(Path dataFolder, String fileName, String backupFileName, int schemaVersion) {
        this.path = safeChildPath(dataFolder, fileName, "state");
        this.backupPath = safeChildPath(dataFolder, backupFileName, "backup state");
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("End Rift state writer requires schema 4");
        }
        this.schemaVersion = schemaVersion;
    }

    private static Path safeChildPath(Path dataFolder, String fileName, String label) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("EventStateStore data directory is required");
        }
        if (fileName == null || fileName.isBlank()
                || fileName.contains("/") || fileName.contains("\\")
                || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IllegalArgumentException("EventStateStore " + label
                    + " file must be a direct child filename: " + fileName);
        }
        Path root = dataFolder.toAbsolutePath().normalize();
        Path candidate = root.resolve(fileName).normalize();
        if (!root.equals(candidate.getParent())) {
            throw new IllegalArgumentException("EventStateStore " + label
                    + " file must remain under the plugin data directory: " + fileName);
        }
        return candidate;
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
        EventSnapshot recovery = EventSnapshot.empty(schemaVersion).withSchemaAndPhase(
                schemaVersion, me.copimine.endevent.domain.EventPhase.RECOVERY_REQUIRED);
        recovery = new EventSnapshot(
                recovery.schemaVersion(), recovery.eventId(), recovery.generation(),
                recovery.phase(), recovery.worldName(), recovery.coreX(), recovery.coreY(), recovery.coreZ(),
                recovery.coreBlockData(), recovery.requiredPlayers(), recovery.arenaMinX(), recovery.arenaMinY(),
                recovery.arenaMinZ(), recovery.arenaMaxX(), recovery.arenaMaxY(), recovery.arenaMaxZ(),
                recovery.resourceRequirements(), recovery.depositedResources(), recovery.pads(),
                recovery.resourceContributors(), recovery.officialRewardRoster(), recovery.rewardStatuses(),
                recovery.shardCooldowns(), recovery.abyssAnchorCooldowns(), recovery.coreCharged(),
                recovery.endUnlocked(), recovery.officialBossDeathCommitted(), recovery.bossLootCommitted(),
                recovery.bossRewardStatus(), recovery.bossRewardRecipient(), recovery.returnStoneStatus(),
                recovery.victoryStep(), recovery.updatedAt(), recovery.phaseDeadlineMillis(),
                primary.reason() + "; " + backup.reason(), recovery.participants(),
                recovery.waveRewardsIssued(), recovery.bossPhase(), recovery.activeBossAbility(),
                recovery.bossAbilityDeadlineMillis(), recovery.bossDefeatSaga(), recovery.objectiveProgress(),
                recovery.nightCloakRolls());
        return LoadResult.invalid(recovery, recovery.recoveryReason());
    }

    public CompletableFuture<Boolean> saveAsync(EventSnapshot snapshot, Executor executor) {
        if (snapshot == null || executor == null) {
            return CompletableFuture.completedFuture(false);
        }
        long sequence = requestedSaveSequence.incrementAndGet();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(saveAtSequence(snapshot, sequence));
                } catch (RuntimeException error) {
                    result.complete(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.complete(false);
        }
        return result;
    }

    public synchronized boolean save(EventSnapshot snapshot) {
        if (snapshot == null || snapshot.schemaVersion() != schemaVersion) {
            return false;
        }
        return saveAtSequence(snapshot, requestedSaveSequence.incrementAndGet());
    }

    /**
     * Writes only the newest request that has not already been committed.
     * Async saves and main-thread checkpoints share this fence, so an older
     * queued snapshot can never overwrite a newer synchronous checkpoint.
     */
    private synchronized boolean saveAtSequence(EventSnapshot snapshot, long sequence) {
        if (snapshot == null || snapshot.schemaVersion() != schemaVersion) {
            return false;
        }
        if (sequence <= committedSaveSequence) {
            return true;
        }
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            writeCurrent(yaml, snapshot);
            writeAtomic(yaml.saveToString());
            committedSaveSequence = sequence;
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private void writeCurrent(YamlConfiguration yaml, EventSnapshot snapshot) {
        yaml.set("schema-version", CURRENT_SCHEMA);
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
        yaml.set("event.end-unlocked", snapshot.endUnlocked());
        yaml.set("event.official-boss-death-committed", snapshot.officialBossDeathCommitted());
        yaml.set("event.boss-loot-committed", snapshot.bossLootCommitted());
        yaml.set("event.boss-reward-status", snapshot.bossRewardStatus());
        yaml.set("event.boss-reward-recipient", snapshot.bossRewardRecipient() == null
                ? null : snapshot.bossRewardRecipient().toString());
        yaml.set("event.return-stone-status", snapshot.returnStoneStatus());
        yaml.set("event.victory-step", snapshot.victoryStep());
        yaml.set("event.updated-at", snapshot.updatedAt());
        yaml.set("event.phase-deadline-millis", snapshot.phaseDeadlineMillis());
        yaml.set("event.recovery-reason", snapshot.recoveryReason());
        yaml.set("resources.requirements", snapshot.resourceRequirements());
        yaml.set("resources.deposited", snapshot.depositedResources());
        yaml.set("participants.resource-contributors", uuidStrings(snapshot.resourceContributors()));
        yaml.set("participants.official-roster", uuidStrings(snapshot.officialRewardRoster()));
        yaml.set("participants.all", uuidStrings(snapshot.participants()));
        yaml.set("rewards.wave-rewards-issued", snapshot.waveRewardsIssued().stream().sorted().toList());
        yaml.set("rewards.statuses", uuidStatusMap(snapshot.rewardStatuses()));
        yaml.set("rewards.shard-cooldowns", uuidLongMap(snapshot.shardCooldowns()));
        yaml.set("rewards.abyss-anchor-cooldowns", uuidLongMap(snapshot.abyssAnchorCooldowns()));
        yaml.set("rewards.night-cloak-rolls", uuidStatusMap(snapshot.nightCloakRolls()));
        yaml.set("boss.phase", snapshot.bossPhase());
        yaml.set("boss.active-ability", snapshot.activeBossAbility());
        yaml.set("boss.ability-deadline-millis", snapshot.bossAbilityDeadlineMillis());
        yaml.set("boss.defeat-saga", snapshot.bossDefeatSaga());
        yaml.set("objective.progress", snapshot.objectiveProgress());
        List<Map<String, Object>> padList = new ArrayList<>();
        for (EventSnapshot.PadSnapshot pad : snapshot.pads()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", pad.x());
            entry.put("y", pad.y());
            entry.put("z", pad.z());
            entry.put("radius", pad.radius());
            entry.put("angle", pad.angleRadians());
            entry.put("original-block-data", pad.originalBlockData());
            padList.add(entry);
        }
        yaml.set("pads", padList);
    }

    private LoadResult read(Path source, String label) {
        if (!Files.exists(source)) {
            return LoadResult.invalid(EventSnapshot.empty(schemaVersion), label + " file is absent");
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
            if (!yaml.contains("schema-version")) {
                return LoadResult.invalid(EventSnapshot.empty(schemaVersion), label + " schema is missing");
            }
            int stored = yaml.getInt("schema-version", Integer.MIN_VALUE);
            if (stored <= 0) {
                return LoadResult.invalid(EventSnapshot.empty(schemaVersion),
                        label + " schema must be positive");
            }
            if (stored > schemaVersion) {
                return LoadResult.invalid(EventSnapshot.empty(schemaVersion),
                        label + " schema " + stored + " is newer than " + schemaVersion);
            }
            EventSnapshot snapshot = stored == schemaVersion
                    ? fromCurrentYaml(yaml) : LegacyEndRiftSnapshotDecoder.decode(yaml, schemaVersion);
            snapshot.eventPhase();
            snapshot.currentBossPhase();
            return LoadResult.valid(snapshot, label + (stored == schemaVersion ? "" : ":MIGRATED"));
        } catch (RuntimeException error) {
            return LoadResult.invalid(EventSnapshot.empty(schemaVersion),
                    label + " parse failed: " + String.valueOf(error.getMessage()));
        }
    }

    private EventSnapshot fromCurrentYaml(YamlConfiguration yaml) {
        Map<String, Integer> requirements = integers(yaml.getConfigurationSection("resources.requirements"));
        Map<String, Integer> deposited = integers(yaml.getConfigurationSection("resources.deposited"));
        List<EventSnapshot.PadSnapshot> pads = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("pads")) {
            pads.add(new EventSnapshot.PadSnapshot(
                    integer(raw.get("x")), integer(raw.get("y")), integer(raw.get("z")),
                    decimal(raw.get("radius")), decimal(raw.get("angle")),
                    String.valueOf(raw.containsKey("original-block-data")
                            ? raw.get("original-block-data") : "")));
        }
        return new EventSnapshot(
                yaml.getInt("schema-version", schemaVersion),
                text(yaml.getString("event.event-id", "")), yaml.getLong("event.generation", 0L),
                text(yaml.getString("event.phase", "RECOVERY_REQUIRED")),
                text(yaml.getString("event.world", "")),
                yaml.getInt("event.core.x"), yaml.getInt("event.core.y"), yaml.getInt("event.core.z"),
                text(yaml.getString("event.core.block-data", "")), yaml.getInt("event.required-players"),
                yaml.getInt("event.arena.min-x"), yaml.getInt("event.arena.min-y"), yaml.getInt("event.arena.min-z"),
                yaml.getInt("event.arena.max-x"), yaml.getInt("event.arena.max-y"), yaml.getInt("event.arena.max-z"),
                requirements, deposited, pads,
                uuids(yaml.getStringList("participants.resource-contributors"), "resource contributors"),
                uuids(yaml.getStringList("participants.official-roster"), "official roster"),
                uuidStatuses(yaml.getConfigurationSection("rewards.statuses")),
                uuidLongs(yaml.getConfigurationSection("rewards.shard-cooldowns")),
                uuidLongs(yaml.getConfigurationSection("rewards.abyss-anchor-cooldowns")),
                yaml.getBoolean("event.core-charged"), yaml.getBoolean("event.end-unlocked"),
                yaml.getBoolean("event.official-boss-death-committed"),
                yaml.getBoolean("event.boss-loot-committed"),
                text(yaml.getString("event.boss-reward-status", "PENDING")),
                uuidOrNullStrict(yaml.getString("event.boss-reward-recipient", "")),
                text(yaml.getString("event.return-stone-status", "PENDING")),
                text(yaml.getString("event.victory-step", "NONE")), yaml.getLong("event.updated-at", 0L),
                yaml.getLong("event.phase-deadline-millis", 0L),
                text(yaml.getString("event.recovery-reason", "")),
                uuids(yaml.getStringList("participants.all"), "participants"),
                new LinkedHashSet<>(yaml.getIntegerList("rewards.wave-rewards-issued")),
                text(yaml.getString("boss.phase", "AWAKENING")),
                text(yaml.getString("boss.active-ability", "NONE")),
                yaml.getLong("boss.ability-deadline-millis", 0L),
                text(yaml.getString("boss.defeat-saga", "NONE")),
                stringMap(yaml.getConfigurationSection("objective.progress")),
                uuidStatuses(yaml.getConfigurationSection("rewards.night-cloak-rolls")));
    }

    private void writeAtomic(String content) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
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

    private static String text(String value) {
        return value == null ? "" : value.trim();
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

    private static UUID uuidOrNullStrict(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("malformed UUID: " + value, invalid);
        }
    }

    private static Set<UUID> uuids(List<String> values, String label) {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            result.add(uuidOrNullStrict(value));
        }
        result.remove(null);
        return result;
    }

    private static Map<UUID, String> uuidStatuses(ConfigurationSection section) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            UUID uuid = uuidOrNullStrict(key);
            String status = section.getString(key);
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("empty status for " + key);
            }
            result.put(uuid, status.trim());
        }
        return result;
    }

    private static Map<String, Long> uuidLongMap(Map<UUID, Long> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey().toString(), entry.getValue()));
        return result;
    }

    private static Map<UUID, Long> uuidLongs(ConfigurationSection section) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            UUID uuid = uuidOrNullStrict(key);
            result.put(uuid, section.getLong(key));
        }
        return result;
    }

    private static Map<String, Integer> integers(ConfigurationSection section) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) result.put(key, section.getInt(key));
        }
        return result;
    }

    private static Map<String, String> stringMap(ConfigurationSection section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) result.put(key, value);
            }
        }
        return result;
    }

    private static int integer(Object value) {
        if (value == null) throw new IllegalArgumentException("missing integer in pads");
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double decimal(Object value) {
        if (value == null) throw new IllegalArgumentException("missing decimal in pads");
        double result = value instanceof Number number ? number.doubleValue()
                : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(result)) throw new IllegalArgumentException("non-finite decimal in pads");
        return result;
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
