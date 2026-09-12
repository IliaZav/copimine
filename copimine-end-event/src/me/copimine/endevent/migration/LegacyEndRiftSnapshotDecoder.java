package me.copimine.endevent.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.EventPhase;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One-way raw decoder for schema 1-3 files. No legacy enum or legacy runtime
 * object crosses this boundary. Ambiguous combat snapshots are deliberately
 * moved to RECOVERY_REQUIRED instead of guessing a live phase.
 */
public final class LegacyEndRiftSnapshotDecoder {
    private LegacyEndRiftSnapshotDecoder() {
    }

    public static EventSnapshot decode(YamlConfiguration yaml, int targetSchema) {
        if (yaml == null || targetSchema != EventSnapshot.CURRENT_SCHEMA) {
            throw new IllegalArgumentException("legacy decoder requires schema-4 target");
        }
        int stored = yaml.getInt("schema-version", -1);
        if (stored <= 0 || stored >= targetSchema) {
            throw new IllegalArgumentException("not a supported legacy schema: " + stored);
        }
        String phase = mapEventPhase(yaml.getString("event.phase", ""));
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
                targetSchema,
                text(yaml.getString("event.event-id", "")), yaml.getLong("event.generation", 0L), phase,
                text(yaml.getString("event.world", "")),
                yaml.getInt("event.core.x"), yaml.getInt("event.core.y"), yaml.getInt("event.core.z"),
                text(yaml.getString("event.core.block-data", "")), yaml.getInt("event.required-players"),
                yaml.getInt("event.arena.min-x"), yaml.getInt("event.arena.min-y"), yaml.getInt("event.arena.min-z"),
                yaml.getInt("event.arena.max-x"), yaml.getInt("event.arena.max-y"), yaml.getInt("event.arena.max-z"),
                requirements, deposited, pads,
                uuids(yaml.getStringList("participants.resource-contributors")),
                uuids(yaml.getStringList("participants.official-roster")),
                uuidStatuses(yaml.getConfigurationSection("rewards.statuses")),
                uuidLongs(yaml.getConfigurationSection("rewards.shard-cooldowns")),
                uuidLongs(yaml.getConfigurationSection("rewards.abyss-anchor-cooldowns")),
                yaml.getBoolean("event.core-charged"), yaml.getBoolean("event.end-unlocked"),
                yaml.getBoolean("event.official-boss-death-committed"),
                yaml.getBoolean("event.boss-loot-committed"),
                text(yaml.getString("event.boss-reward-status", "PENDING")),
                uuidOrNull(yaml.getString("event.boss-reward-recipient", "")),
                text(yaml.getString("event.return-stone-status", "PENDING")),
                text(yaml.getString("event.victory-step", "NONE")), yaml.getLong("event.updated-at", 0L),
                yaml.getLong("event.phase-deadline-millis", 0L),
                "Migrated from schema " + stored + "", uuids(yaml.getStringList("participants.all")),
                new LinkedHashSet<>(yaml.getIntegerList("rewards.wave-rewards-issued")),
                mapBossPhase(yaml.getString("boss.stage", "AWAKENING")),
                "NONE", 0L, "NONE", Map.of(),
                uuidStatuses(yaml.getConfigurationSection("rewards.night-cloak-rolls")));
    }

    private static String mapEventPhase(String raw) {
        if (raw == null || raw.isBlank()) return EventPhase.RECOVERY_REQUIRED.name();
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "UNCONFIGURED", "COLLECTING", "READY_FOR_PLAYERS", "START_RITUAL",
                    "WAVE_1", "INTERMISSION_1", "WAVE_2", "INTERMISSION_2",
                    "WAVE_3", "INTERMISSION_3", "WAVE_4", "CORE_RESTORATION",
                    "WAVE_5", "INTERMISSION_5", "WAVE_6", "INTERMISSION_6", "WAVE_7",
                    "PRE_BOSS_COOLDOWN", "BOSS_CINEMATIC", "BOSS_ACTIVE",
                    "BOSS_FINISH", "VICTORY_PROCESSING", "UNLOCKED", "RECOVERY_REQUIRED" -> value;
            default -> EventPhase.RECOVERY_REQUIRED.name();
        };
    }

    private static String mapBossPhase(String raw) {
        if (raw == null || raw.isBlank()) return BossPhase.AWAKENING.name();
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "AWAKENING" -> BossPhase.AWAKENING.name();
            case "HUNT", "NORMAL" -> BossPhase.HUNT.name();
            case "DISTORTION", "RIFT" -> BossPhase.RIFT.name();
            case "ABSORPTION", "OVERLOAD" -> BossPhase.OVERLOAD.name();
            case "CATASTROPHE", "RAGE" -> BossPhase.RAGE.name();
            case "FINAL_STRIKE", "LAST_SEAL", "FINAL" -> BossPhase.LAST_SEAL.name();
            default -> BossPhase.AWAKENING.name();
        };
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("malformed legacy UUID: " + value, invalid);
        }
    }

    private static Set<UUID> uuids(List<String> values) {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            UUID parsed = uuidOrNull(value);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private static Map<UUID, String> uuidStatuses(ConfigurationSection section) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            UUID uuid = uuidOrNull(key);
            String value = section.getString(key, "PENDING");
            result.put(uuid, value);
        }
        return result;
    }

    private static Map<UUID, Long> uuidLongs(ConfigurationSection section) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) result.put(uuidOrNull(key), section.getLong(key));
        result.remove(null);
        return result;
    }

    private static Map<String, Integer> integers(ConfigurationSection section) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) result.put(key, section.getInt(key));
        }
        return result;
    }

    private static int integer(Object value) {
        if (value == null) throw new IllegalArgumentException("missing integer in legacy pads");
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double decimal(Object value) {
        if (value == null) throw new IllegalArgumentException("missing decimal in legacy pads");
        double result = value instanceof Number number ? number.doubleValue()
                : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(result)) throw new IllegalArgumentException("non-finite legacy pad decimal");
        return result;
    }
}
