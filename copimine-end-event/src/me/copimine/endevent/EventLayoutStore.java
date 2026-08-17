package me.copimine.endevent;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Atomic local persistence for explicitly configured arena/gate/portal layout. */
public final class EventLayoutStore {
    private final Path path;
    private final Path backupPath;

    public EventLayoutStore(Path dataFolder) {
        path = dataFolder.resolve("event-layout.yml");
        backupPath = dataFolder.resolve("event-layout.yml.bak");
    }

    public EventLayoutState load() {
        EventLayoutState primary = read(path);
        if (primary != null) {
            return primary;
        }
        EventLayoutState backup = read(backupPath);
        return backup == null ? EventLayoutState.empty() : backup;
    }

    public boolean save(EventLayoutState state) {
        if (state == null) {
            return false;
        }
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 1);
            point(yaml, "arena.pos1", state.arenaPos1());
            point(yaml, "arena.pos2", state.arenaPos2());
            point(yaml, "gate.pos1", state.gatePos1());
            point(yaml, "gate.pos2", state.gatePos2());
            yaml.set("gate.status", state.gateStatus());
            yaml.set("gate.snapshot", state.gateSnapshot());
            EventLayoutState.Portal portal = state.portalRoom();
            if (portal != null) {
                yaml.set("portal.world", portal.world());
                yaml.set("portal.x", portal.x());
                yaml.set("portal.y", portal.y());
                yaml.set("portal.z", portal.z());
                yaml.set("portal.yaw", portal.yaw());
                yaml.set("portal.pitch", portal.pitch());
            }
            writeAtomic(yaml.saveToString());
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private EventLayoutState read(Path source) {
        if (!Files.exists(source)) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
            if (yaml.getInt("schema-version", 1) > 1) {
                return null;
            }
            return new EventLayoutState(
                    point(yaml, "arena.pos1"), point(yaml, "arena.pos2"),
                    point(yaml, "gate.pos1"), point(yaml, "gate.pos2"),
                    snapshot(yaml.getConfigurationSection("gate.snapshot")),
                    yaml.getString("gate.status", "NONE"),
                    portal(yaml));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private void writeAtomic(String content) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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

    private static void point(YamlConfiguration yaml, String path, EventLayoutState.Point point) {
        if (point == null || !point.configured()) {
            return;
        }
        yaml.set(path + ".world", point.world());
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
    }

    private static EventLayoutState.Point point(YamlConfiguration yaml, String path) {
        String world = yaml.getString(path + ".world", "");
        return world.isBlank() ? null : new EventLayoutState.Point(
                world, yaml.getInt(path + ".x"), yaml.getInt(path + ".y"), yaml.getInt(path + ".z"));
    }

    private static EventLayoutState.Portal portal(YamlConfiguration yaml) {
        String world = yaml.getString("portal.world", "");
        return world.isBlank() ? null : new EventLayoutState.Portal(
                world, yaml.getDouble("portal.x"), yaml.getDouble("portal.y"), yaml.getDouble("portal.z"),
                (float) yaml.getDouble("portal.yaw"), (float) yaml.getDouble("portal.pitch"));
    }

    private static Map<String, String> snapshot(ConfigurationSection section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.getString(key, ""));
            }
        }
        return result;
    }
}
