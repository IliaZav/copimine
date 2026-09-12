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
    private boolean closed;

    public EncounterResourceScope(long generation, EventTaskRegistry tasks) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
        this.tasks = tasks;
    }

    public long generation() { return generation; }

    public synchronized boolean closed() { return closed; }

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

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (BukkitTask task : Set.copyOf(ownedTasks)) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            if (tasks != null) {
                tasks.unregister(task);
            }
        }
        ownedTasks.clear();
        for (UUID entityId : Set.copyOf(entityIds)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid() && !entity.isDead()) entity.remove();
        }
        entityIds.clear();
        for (int index = closers.size() - 1; index >= 0; index--) {
            try {
                closers.get(index).run();
            } catch (RuntimeException ignored) {
                // Every registered closer gets a chance to run. The owning
                // controller logs its own failure and enters recovery.
            }
        }
        closers.clear();
    }
}
