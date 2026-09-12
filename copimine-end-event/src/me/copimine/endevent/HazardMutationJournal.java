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
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Small crash-safe journal for temporary Wave V block mutations.  It is kept
 * separate from live Bukkit state so a restart can restore blocks before the
 * event resumes, without serializing entities or tasks.
 */
public final class HazardMutationJournal {
    private static final int CURRENT_SCHEMA = 3;

    private final Path path;
    private final Path backupPath;
    private volatile String lastFailure = "";

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
        if (backup.valid()) {
            return backup;
        }
        if (!Files.exists(path) && !Files.exists(backupPath)) {
            return Snapshot.empty();
        }
        return Snapshot.invalid();
    }

    public synchronized boolean prepare(String eventId, long generation, String world,
                                        List<Entry> entries) {
        Snapshot existing = load();
        if (!existing.valid()) {
            lastFailure = "unresolved hazard journal is invalid; recovery is required";
            return false;
        }
        if ((existing.status() == Status.PREPARED || existing.status() == Status.APPLIED)
                && !existing.entries().isEmpty()) {
            lastFailure = "unresolved hazard journal must be restored before a new prepare";
            return false;
        }
        if (eventId == null || eventId.isBlank() || generation <= 0L
                || world == null || world.isBlank() || entries == null) {
            lastFailure = "hazard journal metadata is incomplete";
            return false;
        }
        return write(new Snapshot(CURRENT_SCHEMA, eventId, generation, world,
                Status.PREPARED, entries));
    }

    public synchronized boolean markApplied() {
        Snapshot current = load();
        if (!current.valid()) return false;
        if (current.status() != Status.PREPARED) {
            return current.status() == Status.APPLIED;
        }
        return write(current.withStatus(Status.APPLIED));
    }

    public synchronized boolean markRestored() {
        Snapshot current = load();
        if (!current.valid()) return false;
        if (current.status() == Status.EMPTY || current.status() == Status.RESTORED) {
            return true;
        }
        return write(current.withStatus(Status.RESTORED));
    }

    public synchronized Path path() {
        return path;
    }

    public String lastFailure() {
        return lastFailure;
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
                if (entry.worldId() != null && !entry.worldId().isBlank()) {
                    value.put("world-id", entry.worldId());
                }
                if (entry.eventId() != null && !entry.eventId().isBlank()) {
                    value.put("event-id", entry.eventId());
                }
                if (entry.generation() >= 0L) {
                    value.put("entry-generation", entry.generation());
                }
                entries.add(value);
            }
            yaml.set("entries", entries);
            writeAtomic(yaml.saveToString());
            return true;
        } catch (IOException | RuntimeException error) {
            lastFailure = "hazard journal write failed: " + error.getMessage();
            return false;
        }
    }

    private Snapshot read(Path source) {
        if (!Files.exists(source)) {
            return Snapshot.invalid();
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
            if (!yaml.contains("schema-version")) {
                lastFailure = "hazard journal schema is missing: " + source;
                return Snapshot.invalid();
            }
            int schema = yaml.getInt("schema-version", -1);
            if (schema != CURRENT_SCHEMA) {
                lastFailure = "unsupported hazard journal schema " + schema;
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
                if (!raw.containsKey("mutation")) return Snapshot.invalid();
                String mutation = String.valueOf(raw.get("mutation"));
                entries.add(new Entry(integer(raw.get("x")), integer(raw.get("floor-y")),
                        integer(raw.get("z")), floor, web, mutation,
                        stringOrDefault(raw.get("world-id"), yaml.getString("world-id", "")),
                        stringOrDefault(raw.get("event-id"), yaml.getString("event-id", "")),
                        longRequired(raw.get("entry-generation"), yaml.getLong("generation", -1L))));
            }
            String eventId = yaml.getString("event-id", "");
            String world = yaml.getString("world", "");
            long generation = yaml.getLong("generation", -1L);
            if (eventId == null || eventId.isBlank() || world == null || world.isBlank()
                    || generation <= 0L) return Snapshot.invalid();
            for (Entry entry : entries) {
                if (!entry.eventId().isBlank() && !eventId.equals(entry.eventId())
                        || entry.generation() > 0L && entry.generation() != generation
                        || !entry.worldId().isBlank() && !isUuid(entry.worldId())) {
                    return Snapshot.invalid();
                }
            }
            return new Snapshot(CURRENT_SCHEMA, eventId, generation, world, status, entries, true);
        } catch (RuntimeException error) {
            lastFailure = "hazard journal parse failed: " + error.getMessage();
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
                        String mutation, String worldId, String eventId, long generation) {
        public Entry(int x, int floorY, int z, String floorOriginal, String webOriginal) {
            this(x, floorY, z, floorOriginal, webOriginal, "MAGMA", "", "", -1L);
        }

        public Entry(int x, int floorY, int z, String floorOriginal, String webOriginal,
                     String mutation) {
            this(x, floorY, z, floorOriginal, webOriginal, mutation, "", "", -1L);
        }

        public Entry {
            if (floorOriginal == null || floorOriginal.isBlank()) {
                throw new IllegalArgumentException("floor original block data is required");
            }
            floorOriginal = floorOriginal;
            webOriginal = webOriginal == null ? "" : webOriginal;
            mutation = mutation == null || mutation.isBlank() ? "MAGMA" : mutation.toUpperCase(java.util.Locale.ROOT);
            if (!mutation.equals("MAGMA") && !mutation.equals("FIRE")
                    && !mutation.equals("EMERALD_BARRIER") && !mutation.equals("BARRIER")
                    && !mutation.equals("ICE") && !mutation.equals("OBELISK")) {
                throw new IllegalArgumentException("unsupported hazard mutation: " + mutation);
            }
            worldId = worldId == null ? "" : worldId;
            eventId = eventId == null ? "" : eventId;
        }

        public boolean hasWebMutation() {
            return !webOriginal.isBlank();
        }

        public boolean isFireMutation() {
            return mutation.equals("FIRE");
        }

        /** A Wave 4 safe-zone cell: emerald floor plus optional barrier. */
        public boolean isEmeraldBarrierMutation() {
            return mutation.equals("EMERALD_BARRIER");
        }

        /** A safe-zone perimeter block above an untouched floor. */
        public boolean isBarrierMutation() {
            return mutation.equals("BARRIER");
        }

        /** A Wave 5 prisoner cell temporarily covered with ice. */
        public boolean isIceMutation() {
            return mutation.equals("ICE");
        }

        /** A Wave 4 obelisk cell journaled at its actual world height. */
        public boolean isObeliskMutation() {
            return mutation.equals("OBELISK");
        }
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? (fallback == null ? "" : fallback) : text;
    }

    private static long longRequired(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
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
