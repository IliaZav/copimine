package me.copimine.endevent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Small crash-safe journal for temporary Wave V block mutations.  It is kept
 * separate from live Bukkit state so a restart can restore blocks before the
 * event resumes, without serializing entities or tasks.
 */
public final class HazardMutationJournal {
    private static final int CURRENT_SCHEMA = 2;

    private final Path path;
    private final Path backupPath;

    public HazardMutationJournal(Path dataFolder) {
        this.path = dataFolder.resolve("end-rift-hazard-journal.yml");
        this.backupPath = dataFolder.resolve("end-rift-hazard-journal.yml.bak");
    }

    public synchronized Snapshot load() {
        Snapshot primary = read(path);
        if (primary.valid()) {
            return primary;
        }
        Snapshot backup = read(backupPath);
        return backup.valid() ? backup : Snapshot.empty();
    }

    public synchronized boolean prepare(String eventId, long generation, String world,
                                        List<Entry> entries) {
        return write(new Snapshot(CURRENT_SCHEMA, eventId, generation, world,
                Status.PREPARED, entries));
    }

    public synchronized boolean markApplied() {
        Snapshot current = load();
        if (current.status() != Status.PREPARED) {
            return current.status() == Status.APPLIED;
        }
        return write(current.withStatus(Status.APPLIED));
    }

    public synchronized boolean markRestored() {
        Snapshot current = load();
        if (current.status() == Status.EMPTY || current.status() == Status.RESTORED) {
            return true;
        }
        return write(current.withStatus(Status.RESTORED));
    }

    public synchronized Path path() {
        return path;
    }

    private boolean write(Snapshot snapshot) {
        try {
            Files.createDirectories(path.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", snapshot.schemaVersion());
            yaml.set("event-id", snapshot.eventId());
            yaml.set("generation", snapshot.generation());
            yaml.set("world", snapshot.world());
            yaml.set("status", snapshot.status().name());
            List<java.util.Map<String, Object>> entries = new ArrayList<>();
            for (Entry entry : snapshot.entries()) {
                java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
                value.put("x", entry.x());
                value.put("floor-y", entry.floorY());
                value.put("z", entry.z());
                value.put("floor-original", entry.floorOriginal());
                value.put("web-original", entry.webOriginal());
                value.put("mutation", entry.mutation());
                entries.add(value);
            }
            yaml.set("entries", entries);
            writeAtomic(yaml.saveToString());
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private Snapshot read(Path source) {
        if (!Files.exists(source)) {
            return Snapshot.empty();
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
            if (yaml.getInt("schema-version", CURRENT_SCHEMA) > CURRENT_SCHEMA) {
                return Snapshot.invalid();
            }
            Status status;
            try {
                status = Status.valueOf(yaml.getString("status", "EMPTY"));
            } catch (IllegalArgumentException invalid) {
                return Snapshot.invalid();
            }
            List<Entry> entries = new ArrayList<>();
            for (java.util.Map<?, ?> raw : yaml.getMapList("entries")) {
                String floor = String.valueOf(raw.containsKey("floor-original")
                        ? raw.get("floor-original") : "");
                String web = String.valueOf(raw.containsKey("web-original")
                        ? raw.get("web-original") : "");
                String mutation = String.valueOf(raw.containsKey("mutation")
                        ? raw.get("mutation") : "MAGMA");
                entries.add(new Entry(integer(raw.get("x")), integer(raw.get("floor-y")),
                        integer(raw.get("z")), floor, web, mutation));
            }
            return new Snapshot(CURRENT_SCHEMA, yaml.getString("event-id", ""),
                    yaml.getLong("generation", 0L), yaml.getString("world", ""), status, entries, true);
        } catch (RuntimeException ignored) {
            return Snapshot.invalid();
        }
    }

    private void writeAtomic(String content) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            channel.force(true);
        }
        if (Files.exists(path)) {
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    public enum Status {
        EMPTY,
        PREPARED,
        APPLIED,
        RESTORED
    }

    public record Entry(int x, int floorY, int z, String floorOriginal, String webOriginal,
                        String mutation) {
        public Entry(int x, int floorY, int z, String floorOriginal, String webOriginal) {
            this(x, floorY, z, floorOriginal, webOriginal, "MAGMA");
        }

        public Entry {
            if (floorOriginal == null || floorOriginal.isBlank()) {
                throw new IllegalArgumentException("floor original block data is required");
            }
            floorOriginal = floorOriginal;
            webOriginal = webOriginal == null ? "" : webOriginal;
            mutation = mutation == null || mutation.isBlank() ? "MAGMA" : mutation.toUpperCase(java.util.Locale.ROOT);
            if (!mutation.equals("MAGMA") && !mutation.equals("FIRE")) {
                throw new IllegalArgumentException("unsupported hazard mutation: " + mutation);
            }
        }

        public boolean hasWebMutation() {
            return !webOriginal.isBlank();
        }

        public boolean isFireMutation() {
            return mutation.equals("FIRE");
        }
    }

    public record Snapshot(int schemaVersion, String eventId, long generation, String world,
                           Status status, List<Entry> entries, boolean valid) {
        public Snapshot {
            eventId = eventId == null ? "" : eventId;
            world = world == null ? "" : world;
            status = status == null ? Status.EMPTY : status;
            entries = List.copyOf(entries == null ? List.of() : entries);
        }

        private Snapshot(int schemaVersion, String eventId, long generation, String world,
                         Status status, List<Entry> entries) {
            this(schemaVersion, eventId, generation, world, status, entries, true);
        }

        public static Snapshot empty() {
            return new Snapshot(CURRENT_SCHEMA, "", 0L, "", Status.EMPTY, List.of(), true);
        }

        private static Snapshot invalid() {
            return new Snapshot(CURRENT_SCHEMA, "", 0L, "", Status.EMPTY, List.of(), false);
        }

        private Snapshot withStatus(Status next) {
            return new Snapshot(schemaVersion, eventId, generation, world, next, entries, valid);
        }
    }
}
