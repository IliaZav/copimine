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
    private final Map<UUID, Runnable> entityCleanups = new LinkedHashMap<>();
    private final List<Runnable> closers = new ArrayList<>();
    private CleanupResult lastCleanupResult = CleanupResult.successful();
    private boolean closing;
    private boolean closed;

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

    public synchronized boolean closing() { return closing; }

    public synchronized CleanupResult lastCleanupResult() { return lastCleanupResult; }

    public synchronized <T extends BukkitTask> T registerTask(T task) {
        if (closing || closed) {
            if (task != null) task.cancel();
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
        if (closing || closed) {
            if (entity.isValid() && !entity.isDead()) entity.remove();
            return;
        }
        entityIds.add(entity.getUniqueId());
    }

    public synchronized void registerEntity(UUID entityId) {
        registerEntity(entityId, null);
    }

    /** Register an event-specific cleanup action for an entity this scope owns. */
    public synchronized void registerEntity(UUID entityId, Runnable cleanup) {
        if (entityId == null) return;
        if (closing || closed) {
            if (cleanup != null) cleanup.run();
            else removeEntity(entityId);
            return;
        }
        if (entityIds.add(entityId) && cleanup != null) {
            entityCleanups.put(entityId, cleanup);
        }
    }

    public synchronized void registerCloser(Runnable closer) {
        if (closer == null) return;
        if (closing || closed) {
            closer.run();
        } else {
            closers.add(closer);
        }
    }

    /**
     * Close every owned resource and return an auditable result. All cleanup
     * operations are attempted even when one of them fails.
     */
    public synchronized CleanupResult closeResources() {
        if (closed) return lastCleanupResult;
        closing = true;
        List<CleanupFailure> failures = new ArrayList<>();
        for (BukkitTask task : Set.copyOf(ownedTasks)) {
            boolean taskCleanupSucceeded = true;
            if (task != null && !task.isCancelled()) {
                try {
                    task.cancel();
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("task", String.valueOf(task.getTaskId()), error));
                    taskCleanupSucceeded = false;
                }
            }
            if (tasks != null && taskCleanupSucceeded) {
                try {
                    tasks.unregister(task);
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("task-registry", String.valueOf(task.getTaskId()), error));
                    taskCleanupSucceeded = false;
                }
            }
            if (taskCleanupSucceeded) ownedTasks.remove(task);
        }
        for (UUID entityId : Set.copyOf(entityIds)) {
            try {
                Runnable cleanup = entityCleanups.get(entityId);
                if (cleanup != null) cleanup.run();
                else removeEntity(entityId);
                entityIds.remove(entityId);
                entityCleanups.remove(entityId);
            } catch (RuntimeException error) {
                failures.add(new CleanupFailure("entity", String.valueOf(entityId), error));
            }
        }
        for (int index = closers.size() - 1; index >= 0; index--) {
            try {
                closers.get(index).run();
                closers.remove(index);
            } catch (RuntimeException error) {
                failures.add(new CleanupFailure("closer", "index=" + index, error));
            }
        }
        lastCleanupResult = new CleanupResult(failures);
        if (lastCleanupResult.success()) {
            closing = false;
            closed = true;
        }
        return lastCleanupResult;
    }

    private static void removeEntity(UUID entityId) {
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
