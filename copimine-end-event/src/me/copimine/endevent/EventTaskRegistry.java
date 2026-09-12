package me.copimine.endevent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/** Owns every scheduled callback created for one event generation. */
public final class EventTaskRegistry {
    private final long generation;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

    public EventTaskRegistry(long generation) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.generation = generation;
    }

    public long generation() {
        return generation;
    }

    public <T extends BukkitTask> T register(T task) {
        if (task != null) {
            pruneCompleted();
            tasks.add(task);
        }
        return task;
    }

    public void unregister(BukkitTask task) {
        if (task != null) tasks.remove(task);
    }

    public boolean owns(long callbackGeneration) {
        return generation == callbackGeneration;
    }

    /**
     * Execute a Bukkit callback only while its captured generation is still
     * current. The check must happen at execution time, not only when a task
     * is scheduled, because a delayed callback can outlive a wipe boundary.
     */
    public boolean runIfOwned(long callbackGeneration, Runnable callback) {
        if (callback == null || !owns(callbackGeneration)) {
            return false;
        }
        callback.run();
        return true;
    }

    public void cancelAll() {
        pruneCompleted();
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
        tasks.clear();
    }

    public int size() {
        pruneCompleted();
        return tasks.size();
    }

    public int activeCount() {
        return size();
    }

    /**
     * Drop cancelled handles and one-shot callbacks that have already left
     * Bukkit's scheduler. Paper does not mark every completed one-shot handle
     * as cancelled, so relying on isCancelled() alone made the registry grow
     * after long events.
     */
    public void pruneCompleted() {
        tasks.removeIf(task -> task == null || task.isCancelled() || completed(task));
    }

    private boolean completed(BukkitTask task) {
        try {
            return !Bukkit.getScheduler().isQueued(task.getTaskId())
                    && !Bukkit.getScheduler().isCurrentlyRunning(task.getTaskId());
        } catch (RuntimeException ignored) {
            // Unit tests and shutdown callbacks may run without a live Bukkit
            // scheduler. In that case cancellation remains the safe signal.
            return false;
        }
    }
}
