package me.copimine.endevent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.EventTaskRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

/**
 * Idempotent owner for temporary resources belonging to one generation and
 * one encounter phase. The Bukkit adapter registers handles as soon as they
 * are created; closing the scope is the single cleanup boundary.
 */
public final class EncounterResourceScope implements AutoCloseable {
    private final long generation;
    private final EventTaskRegistry tasks;
    private final Set<BukkitTask> ownedTasks = new LinkedHashSet<>();
    private final Set<UUID> entityIds = new LinkedHashSet<>();
    private final List<Runnable> closers = new ArrayList<>();
    private CleanupResult lastCleanupResult = CleanupResult.successful();
    private boolean closed;

    public EncounterResourceScope(long generation, EventTaskRegistry tasks) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.tasks = tasks;
    }

    public long generation() { return generation; }

    public synchronized boolean closed() { return closed; }

    public synchronized CleanupResult lastCleanupResult() { return lastCleanupResult; }

    public synchronized <T extends BukkitTask> T registerTask(T task) {
        if (closed) {
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
        if (closed) {
            if (entity.isValid() && !entity.isDead()) entity.remove();
            return;
        }
        entityIds.add(entity.getUniqueId());
    }

    public synchronized void registerEntity(UUID entityId) {
        if (!closed && entityId != null) entityIds.add(entityId);
    }

    public synchronized void registerCloser(Runnable closer) {
        if (closer == null) return;
        if (closed) {
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
        closed = true;
        List<CleanupFailure> failures = new ArrayList<>();
        for (BukkitTask task : Set.copyOf(ownedTasks)) {
            if (task != null && !task.isCancelled()) {
                try {
                    task.cancel();
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("task", String.valueOf(task.getTaskId()), error));
                }
            }
            if (tasks != null) {
                try {
                    tasks.unregister(task);
                } catch (RuntimeException error) {
                    failures.add(new CleanupFailure("task-registry", String.valueOf(task.getTaskId()), error));
                }
            }
        }
        ownedTasks.clear();
        for (UUID entityId : Set.copyOf(entityIds)) {
            try {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null && entity.isValid() && !entity.isDead()) entity.remove();
            } catch (RuntimeException error) {
                failures.add(new CleanupFailure("entity", String.valueOf(entityId), error));
            }
        }
        entityIds.clear();
        for (int index = closers.size() - 1; index >= 0; index--) {
            try {
                closers.get(index).run();
            } catch (RuntimeException error) {
                failures.add(new CleanupFailure("closer", "index=" + index, error));
            }
        }
        closers.clear();
        lastCleanupResult = new CleanupResult(failures);
        return lastCleanupResult;
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
