package me.copimine.endevent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.EventTaskRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

/**
 * Idempotent owner for temporary resources belonging to one event generation
 * and one named phase or wave. The Bukkit adapter registers handles as soon as
 * they are created; closing the scope is the single cleanup boundary.
 */
public final class EncounterResourceScope implements AutoCloseable {
    private final long generation;
    private final String ownerKey;
    private final EventTaskRegistry tasks;
    private final Set<BukkitTask> ownedTasks = new LinkedHashSet<>();
    private final Set<UUID> entityIds = new LinkedHashSet<>();
    private final Map<UUID, List<Runnable>> entityCleanups = new LinkedHashMap<>();
    private final List<Runnable> closers = new ArrayList<>();
    private CleanupResult lastCleanupResult = CleanupResult.successful();
    private boolean closed;
    private boolean closing;
    private List<CleanupFailure> closingFailures;

    public EncounterResourceScope(long generation, EventTaskRegistry tasks) {
        this(generation, "generation-" + generation, tasks);
    }

    public EncounterResourceScope(long generation, String ownerKey, EventTaskRegistry tasks) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("scope owner is required");
        }
        if (tasks != null && !tasks.owns(generation)) {
            throw new IllegalArgumentException("task registry must own the scope generation");
        }
        this.generation = generation;
        this.ownerKey = ownerKey.trim();
        this.tasks = tasks;
    }

    public long generation() { return generation; }
    public String owner() { return ownerKey; }

    public synchronized boolean closed() { return closed; }

    public synchronized CleanupResult lastCleanupResult() { return lastCleanupResult; }

    public synchronized <T extends BukkitTask> T registerTask(T task) {
        if (closed) {
            if (task != null) {
                ownedTasks.add(task);
                if (closing) cleanupTask(task, closingFailures);
                else closeResources();
            }
            return task;
        }
        if (task == null) {
            return null;
        }
        ownedTasks.add(task);
        return tasks == null ? task : tasks.register(task);
    }

    public synchronized void registerEntity(Entity entity) {
        if (entity == null) return;
        registerEntity(entity.getUniqueId());
    }

    public synchronized void registerEntity(UUID entityId) {
        registerEntity(entityId, null);
    }

    /** Register an event-specific cleanup action for an entity this scope owns. */
    public synchronized void registerEntity(UUID entityId, Runnable cleanup) {
        if (entityId == null) return;
        entityIds.add(entityId);
        if (cleanup != null) {
            List<Runnable> actions = entityCleanups.computeIfAbsent(entityId,
                    ignored -> new ArrayList<>());
            if (actions.stream().noneMatch(existing -> existing == cleanup)) {
                actions.add(cleanup);
            }
        }
        if (closed && !closing) {
            closeResources();
        }
    }

    public synchronized void registerCloser(Runnable closer) {
        if (closer == null) return;
        closers.add(closer);
        if (closed && !closing) {
            closeResources();
        }
    }

    /**
     * Close every owned resource and return an auditable result. All cleanup
     * operations are attempted even when one of them fails.
     */
    public synchronized CleanupResult closeResources() {
        closed = true;
        if (closing) return lastCleanupResult;
        closing = true;
        List<CleanupFailure> failures = new ArrayList<>();
        closingFailures = failures;
        try {
            for (BukkitTask task : Set.copyOf(ownedTasks)) {
                if (task == null) {
                    ownedTasks.remove(null);
                    continue;
                }
                cleanupTask(task, failures);
            }

            for (UUID entityId : Set.copyOf(entityIds)) {
                List<Runnable> cleanups = entityCleanups.get(entityId);
                if (cleanups == null || cleanups.isEmpty()) {
                    try {
                        removeEntity(entityId);
                        entityIds.remove(entityId);
                        entityCleanups.remove(entityId);
                    } catch (RuntimeException error) {
                        failures.add(new CleanupFailure("entity", String.valueOf(entityId), error));
                    }
                    continue;
                }
                for (int index = cleanups.size() - 1; index >= 0; index--) {
                    Runnable cleanup = cleanups.get(index);
                    try {
                        cleanup.run();
                        cleanups.remove(index);
                    } catch (RuntimeException error) {
                        failures.add(new CleanupFailure("entity", String.valueOf(entityId), error));
                    }
                }
                // Failed callbacks may still need the live entity on retry.
                if (!cleanups.isEmpty()) continue;
                try {
                    removeEntity(entityId);
                    entityIds.remove(entityId);
                    entityCleanups.remove(entityId);
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("entity", String.valueOf(entityId), error));
                }
            }

            for (int index = closers.size() - 1; index >= 0; index--) {
                Runnable closer = closers.get(index);
                try {
                    closer.run();
                    closers.remove(index);
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("closer", "index=" + index, error));
                }
            }
            if (failures.isEmpty() && hasPendingResources()) {
                failures.add(new CleanupFailure("scope", ownerKey,
                        new IllegalStateException("resources were registered while cleanup was running")));
            }
            lastCleanupResult = new CleanupResult(failures);
            return lastCleanupResult;
        } finally {
            closingFailures = null;
            closing = false;
        }
    }

    private void cleanupTask(BukkitTask task, List<CleanupFailure> failures) {
        String taskId = taskId(task);
        boolean cleaned = true;
        try {
            if (!task.isCancelled()) task.cancel();
        } catch (RuntimeException error) {
            failures.add(new CleanupFailure("task", taskId, error));
            cleaned = false;
        }
        if (tasks != null) {
            try {
                tasks.unregister(task);
            } catch (RuntimeException error) {
                failures.add(new CleanupFailure("task-registry", taskId, error));
                cleaned = false;
            }
        }
        if (cleaned) ownedTasks.remove(task);
    }

    private boolean hasPendingResources() {
        return !ownedTasks.isEmpty() || !entityIds.isEmpty() || !closers.isEmpty();
    }

    private static String taskId(BukkitTask task) {
        try {
            return String.valueOf(task.getTaskId());
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static void removeEntity(UUID entityId) {
        if (Bukkit.getServer() == null) return;
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid() && !entity.isDead()) entity.remove();
    }

    @Override
    public synchronized void close() {
        CleanupResult result = closeResources();
        if (!result.success()) throw new CleanupException(result);
    }

    public record CleanupResult(List<CleanupFailure> failures) {
        public CleanupResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public static CleanupResult successful() { return new CleanupResult(List.of()); }
        public boolean success() { return failures.isEmpty(); }
    }

    public record CleanupFailure(String resourceType, String resourceId, RuntimeException error) { }

    public static final class CleanupException extends IllegalStateException {
        private final CleanupResult result;

        public CleanupException(CleanupResult result) {
            super("Encounter resource cleanup failed: "
                    + (result == null ? 0 : result.failures().size()) + " failure(s)");
            this.result = result == null ? CleanupResult.successful() : result;
            if (!this.result.failures().isEmpty()) {
                addSuppressed(this.result.failures().get(0).error());
            }
        }

        public CleanupResult result() { return result; }
    }
}
