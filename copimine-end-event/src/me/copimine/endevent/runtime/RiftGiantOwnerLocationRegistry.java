package me.copimine.endevent.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Durable last-known unload evidence for Rift Giant owners and their summons. */
public final class RiftGiantOwnerLocationRegistry {
    public enum Status {
        LOADED,
        UNLOADED,
        DEAD
    }

    public record OwnerState(UUID ownerId, String eventId, long generation, int wave,
                             Status status, UUID worldId, int chunkX, int chunkZ,
                             int summonsUsed) {
        public OwnerState {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(status, "status");
        }

        public boolean matches(String candidateEventId, long candidateGeneration) {
            return eventId.equals(candidateEventId) && generation == candidateGeneration;
        }

        public boolean matchesChunk(UUID candidateWorldId, int candidateChunkX, int candidateChunkZ) {
            return status == Status.UNLOADED && Objects.equals(worldId, candidateWorldId)
                    && chunkX == candidateChunkX && chunkZ == candidateChunkZ;
        }
    }

    private static final String OWNER_PREFIX = "owner.";
    private static final String WAVE_PREFIX = "wave.";

    private record WaveBudgetKey(String eventId, long generation, int wave) { }

    private final Path file;
    private final Map<UUID, OwnerState> owners = new HashMap<>();
    private final Map<WaveBudgetKey, Integer> waveSummonTotals = new HashMap<>();

    public RiftGiantOwnerLocationRegistry(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        load();
    }

    public synchronized Optional<OwnerState> find(UUID ownerId,
                                                   String eventId,
                                                   long generation) {
        if (ownerId == null || eventId == null || eventId.isBlank() || generation <= 0L) {
            return Optional.empty();
        }
        OwnerState state = owners.get(ownerId);
        return state != null && state.matches(eventId, generation)
                ? Optional.of(state) : Optional.empty();
    }

    public synchronized List<OwnerState> unloadedOwners(String eventId, long generation) {
        if (eventId == null || eventId.isBlank() || generation <= 0L) return List.of();
        List<OwnerState> result = new ArrayList<>();
        for (OwnerState state : owners.values()) {
            if (state.status() == Status.UNLOADED && state.matches(eventId, generation)) {
                result.add(state);
            }
        }
        result.sort((left, right) -> left.ownerId().toString().compareTo(right.ownerId().toString()));
        return List.copyOf(result);
    }

    /** Treat owners recorded as loaded by the previous process as pending until their chunk is scanned. */
    public synchronized void resumeLoadedOwnersAfterRestart(String eventId,
                                                            long generation) throws IOException {
        if (eventId == null || eventId.isBlank() || generation <= 0L) return;
        Map<UUID, OwnerState> updated = new HashMap<>(owners);
        boolean changed = false;
        for (Map.Entry<UUID, OwnerState> entry : updated.entrySet()) {
            OwnerState state = entry.getValue();
            if (!state.matches(eventId, generation) || state.status() != Status.LOADED) continue;
            entry.setValue(new OwnerState(state.ownerId(), state.eventId(), state.generation(),
                    state.wave(), Status.UNLOADED, state.worldId(), state.chunkX(), state.chunkZ(),
                    state.summonsUsed()));
            changed = true;
        }
        if (!changed) return;
        persist(updated, waveSummonTotals);
        owners.clear();
        owners.putAll(updated);
    }

    /** Highest durable cumulative summon count retained by any owner in this wave session. */
    public synchronized int maxSummonsUsed(String eventId, long generation, int wave) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || wave < 1 || wave > 7) return 0;
        int maximum = waveSummonTotals.getOrDefault(
                new WaveBudgetKey(eventId, generation, wave), 0);
        for (OwnerState state : owners.values()) {
            if (state.matches(eventId, generation) && state.wave() == wave) {
                maximum = Math.max(maximum, state.summonsUsed());
            }
        }
        return maximum;
    }

    /** Persist one monotonically increasing cumulative budget for the whole wave session. */
    public synchronized void recordWaveSummonsUsed(String eventId, long generation,
                                                   int wave, int summonsUsed) throws IOException {
        validateWaveBudgetIdentity(eventId, generation, wave);
        validateSummonsUsed(summonsUsed);
        WaveBudgetKey key = new WaveBudgetKey(eventId, generation, wave);
        int current = waveSummonTotals.getOrDefault(key, 0);
        int next = Math.max(current, summonsUsed);
        if (next == current) return;
        Map<WaveBudgetKey, Integer> updated = new HashMap<>(waveSummonTotals);
        updated.put(key, next);
        persist(owners, updated);
        waveSummonTotals.clear();
        waveSummonTotals.putAll(updated);
    }

    /** Drop evidence from completed event generations after a valid snapshot is loaded. */
    public synchronized void retainSession(String eventId, long generation) throws IOException {
        if (eventId == null || eventId.isBlank() || generation <= 0L) {
            throw new IOException("Invalid current session for Rift Giant owner ledger retention");
        }
        Map<UUID, OwnerState> updated = new HashMap<>(owners);
        updated.entrySet().removeIf(entry -> !entry.getValue().matches(eventId, generation));
        Map<WaveBudgetKey, Integer> updatedTotals = new HashMap<>(waveSummonTotals);
        updatedTotals.keySet().removeIf(key -> !key.eventId().equals(eventId)
                || key.generation() != generation);
        if (updated.size() == owners.size() && updatedTotals.size() == waveSummonTotals.size()) return;
        persist(updated, updatedTotals);
        owners.clear();
        owners.putAll(updated);
        waveSummonTotals.clear();
        waveSummonTotals.putAll(updatedTotals);
    }

    public synchronized void recordUnloadedOwner(UUID ownerId,
                                                 String eventId,
                                                 long generation,
                                                 int wave,
                                                 UUID worldId,
                                                 int chunkX,
                                                 int chunkZ) throws IOException {
        recordUnloadedOwner(ownerId, eventId, generation, wave,
                worldId, chunkX, chunkZ, 0);
    }

    public synchronized void recordUnloadedOwner(UUID ownerId,
                                                 String eventId,
                                                 long generation,
                                                 int wave,
                                                 UUID worldId,
                                                 int chunkX,
                                                 int chunkZ,
                                                 int summonsUsed) throws IOException {
        validateIdentity(ownerId, eventId, generation, wave);
        Objects.requireNonNull(worldId, "worldId");
        putAndPersist(new OwnerState(ownerId, eventId, generation, wave,
                Status.UNLOADED, worldId, chunkX, chunkZ, validateSummonsUsed(summonsUsed)));
    }

    public synchronized void recordLoadedOwner(UUID ownerId, String eventId,
                                               long generation, int wave,
                                               UUID worldId, int chunkX, int chunkZ,
                                               int summonsUsed) throws IOException {
        validateIdentity(ownerId, eventId, generation, wave);
        Objects.requireNonNull(worldId, "worldId");
        putAndPersist(new OwnerState(ownerId, eventId, generation, wave,
                Status.LOADED, worldId, chunkX, chunkZ, validateSummonsUsed(summonsUsed)));
    }

    public synchronized void recordDeadOwner(UUID ownerId,
                                             String eventId,
                                             long generation,
                                             int wave) throws IOException {
        recordDeadOwner(ownerId, eventId, generation, wave, 0);
    }

    public synchronized void recordDeadOwner(UUID ownerId,
                                             String eventId,
                                             long generation,
                                             int wave,
                                             int summonsUsed) throws IOException {
        validateIdentity(ownerId, eventId, generation, wave);
        putAndPersist(new OwnerState(ownerId, eventId, generation, wave,
                Status.DEAD, null, 0, 0, validateSummonsUsed(summonsUsed)));
    }

    /** Clear only the matching session's evidence after its actual owner reloads. */
    public synchronized void clear(UUID ownerId,
                                   String eventId,
                                   long generation) throws IOException {
        if (ownerId == null || eventId == null || eventId.isBlank() || generation <= 0L) return;
        OwnerState current = owners.get(ownerId);
        if (current == null || !current.matches(eventId, generation)) return;
        Map<UUID, OwnerState> updated = new HashMap<>(owners);
        updated.remove(ownerId);
        persist(updated, waveSummonTotals);
        owners.clear();
        owners.putAll(updated);
    }

    /**
     * Permit orphan cleanup only from durable evidence: a confirmed death, or
     * an entity-load event for the owner's exact last-known chunk.
     */
    public synchronized boolean mayRetireAbsentOwner(UUID ownerId,
                                                     String eventId,
                                                     long generation,
                                                     boolean ownerPresent,
                                                     UUID loadedWorldId,
                                                     int loadedChunkX,
                                                     int loadedChunkZ,
                                                     boolean ownerChunkLoadedByCurrentEvent) {
        if (ownerPresent) return false;
        Optional<OwnerState> found = find(ownerId, eventId, generation);
        if (found.isEmpty()) return false;
        OwnerState state = found.get();
        return state.status() == Status.DEAD
                || ownerChunkLoadedByCurrentEvent
                && state.matchesChunk(loadedWorldId, loadedChunkX, loadedChunkZ);
    }

    private void putAndPersist(OwnerState state) throws IOException {
        if (state.equals(owners.get(state.ownerId()))) return;
        Map<UUID, OwnerState> updated = new HashMap<>(owners);
        updated.put(state.ownerId(), state);
        persist(updated, waveSummonTotals);
        owners.clear();
        owners.putAll(updated);
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        Set<UUID> ownerIds = new TreeSet<>((left, right) -> left.toString().compareTo(right.toString()));
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(WAVE_PREFIX)) {
                loadWaveBudget(properties, key);
                continue;
            }
            if (!key.startsWith(OWNER_PREFIX)) {
                throw new IOException("Unknown Rift Giant owner ledger key: " + key);
            }
            int suffix = key.indexOf('.', OWNER_PREFIX.length());
            if (suffix < 0) throw new IOException("Malformed Rift Giant owner ledger key: " + key);
            try {
                ownerIds.add(UUID.fromString(key.substring(OWNER_PREFIX.length(), suffix)));
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Malformed Rift Giant owner ID in ledger", malformed);
            }
        }
        for (UUID ownerId : ownerIds) {
            String prefix = OWNER_PREFIX + ownerId + ".";
            String statusText = required(properties, prefix + "status");
            String eventId = required(properties, prefix + "event");
            long generation = parseLong(required(properties, prefix + "generation"), "generation");
            int wave = parseInt(required(properties, prefix + "wave"), "wave");
            int summonsUsed = properties.containsKey(prefix + "summons-used")
                    ? parseInt(required(properties, prefix + "summons-used"), "summons-used") : 0;
            Status status;
            try {
                status = Status.valueOf(statusText);
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Malformed Rift Giant owner status", malformed);
            }
            validateIdentity(ownerId, eventId, generation, wave);
            validateSummonsUsed(summonsUsed);
            UUID worldId = null;
            int chunkX = 0;
            int chunkZ = 0;
            if (status == Status.UNLOADED || status == Status.LOADED) {
                try {
                    worldId = UUID.fromString(required(properties, prefix + "world"));
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("Malformed Rift Giant owner world ID", malformed);
                }
                chunkX = parseInt(required(properties, prefix + "chunk-x"), "chunk-x");
                chunkZ = parseInt(required(properties, prefix + "chunk-z"), "chunk-z");
            }
            owners.put(ownerId, new OwnerState(ownerId, eventId, generation, wave,
                    status, worldId, chunkX, chunkZ, summonsUsed));
        }
    }

    private void persist(Map<UUID, OwnerState> states,
                         Map<WaveBudgetKey, Integer> waveTotals) throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Rift Giant owner ledger has no parent directory");
        Files.createDirectories(parent);
        Path temporary = parent.resolve(file.getFileName() + ".tmp-" + UUID.randomUUID());
        Properties properties = new Properties();
        for (OwnerState state : states.values()) {
            String prefix = OWNER_PREFIX + state.ownerId() + ".";
            properties.setProperty(prefix + "status", state.status().name());
            properties.setProperty(prefix + "event", state.eventId());
            properties.setProperty(prefix + "generation", Long.toString(state.generation()));
            properties.setProperty(prefix + "wave", Integer.toString(state.wave()));
            properties.setProperty(prefix + "summons-used", Integer.toString(state.summonsUsed()));
            if (state.status() == Status.UNLOADED || state.status() == Status.LOADED) {
                properties.setProperty(prefix + "world", state.worldId().toString());
                properties.setProperty(prefix + "chunk-x", Integer.toString(state.chunkX()));
                properties.setProperty(prefix + "chunk-z", Integer.toString(state.chunkZ()));
            }
        }
        for (Map.Entry<WaveBudgetKey, Integer> entry : waveTotals.entrySet()) {
            WaveBudgetKey key = entry.getKey();
            properties.setProperty(waveBudgetProperty(key.eventId(), key.generation(), key.wave()),
                    Integer.toString(entry.getValue()));
        }
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                OutputStream output = Channels.newOutputStream(channel);
                properties.store(output, "Rift Giant owner lifecycle evidence; do not edit while the server is running");
                output.flush();
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing Rift Giant owner ledger field: " + key);
        return value;
    }

    private static long parseLong(String value, String field) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException malformed) {
            throw new IOException("Malformed Rift Giant owner ledger " + field, malformed);
        }
    }

    private static int parseInt(String value, String field) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw new IOException("Malformed Rift Giant owner ledger " + field, malformed);
        }
    }

    private void loadWaveBudget(Properties properties, String key) throws IOException {
        String[] parts = key.substring(WAVE_PREFIX.length()).split("\\.", -1);
        if (parts.length != 4 || parts[0].isBlank() || !"summons-used".equals(parts[3])) {
            throw new IOException("Malformed Rift Giant wave budget key");
        }
        String eventId;
        try {
            eventId = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Malformed Rift Giant wave budget event identity", malformed);
        }
        long generation = parseLong(parts[1], "wave-generation");
        int wave = parseInt(parts[2], "wave-number");
        int count = parseInt(required(properties, key), "wave-summons-used");
        validateWaveBudgetIdentity(eventId, generation, wave);
        validateSummonsUsed(count);
        waveSummonTotals.put(new WaveBudgetKey(eventId, generation, wave), count);
    }

    private static String waveBudgetProperty(String eventId, long generation, int wave) {
        String encodedEvent = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(eventId.getBytes(StandardCharsets.UTF_8));
        return WAVE_PREFIX + encodedEvent + "." + generation + "." + wave + ".summons-used";
    }

    private static void validateWaveBudgetIdentity(String eventId, long generation, int wave)
            throws IOException {
        if (eventId == null || eventId.isBlank() || eventId.indexOf('|') >= 0
                || generation <= 0L || wave < 1 || wave > 7) {
            throw new IOException("Invalid Rift Giant wave budget identity");
        }
    }

    private static void validateIdentity(UUID ownerId, String eventId, long generation, int wave)
            throws IOException {
        if (ownerId == null || eventId == null || eventId.isBlank() || eventId.indexOf('|') >= 0
                || generation <= 0L || wave < 1 || wave > 7) {
            throw new IOException("Invalid Rift Giant owner ledger identity");
        }
    }

    private static int validateSummonsUsed(int summonsUsed) throws IOException {
        if (summonsUsed < 0 || summonsUsed > 1_000_000) {
            throw new IOException("Invalid Rift Giant cumulative summon count");
        }
        return summonsUsed;
    }
}
