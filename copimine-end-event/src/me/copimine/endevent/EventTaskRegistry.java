package me.copimine.endevent;

import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/** Owns every scheduled callback created for one event generation. */
public final class EventTaskRegistry {
    private final long generation;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();
    private final Set<Integer> completedTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> cancelledTaskIds = ConcurrentHashMap.newKeySet();

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

    /** Stable task ids for the central diagnostic cleanup boundary. */
    public List<Integer> taskIds() {
        pruneCompleted();
        return tasks.stream().filter(task -> task != null)
                .map(BukkitTask::getTaskId).sorted().toList();
    }

    /**
     * Return and clear one-shot task ids observed as complete during pruning.
     * The event adapter turns these ids into TASK/COMPLETE records so the
     * diagnostic report can distinguish a finished one-shot from a leaked
     * repeating task.
     */
    public List<Integer> drainCompletedTaskIds() {
        List<Integer> result = completedTaskIds.stream().sorted().toList();
        completedTaskIds.removeAll(result);
        return result;
    }

    /**
     * Return and clear task ids observed as cancelled before the event adapter
     * reached its cleanup boundary. Paper may cancel plugin tasks as part of
     * shutdown before onDisable() runs, so the adapter must retain that
     * terminal state instead of silently turning the task into a leak.
     */
    public List<Integer> drainCancelledTaskIds() {
        List<Integer> result = cancelledTaskIds.stream().sorted().toList();
        cancelledTaskIds.removeAll(result);
        return result;
    }

    /**
     * Drop cancelled handles and one-shot callbacks that have already left
     * Bukkit's scheduler. Paper does not mark every completed one-shot handle
     * as cancelled, so relying on isCancelled() alone made the registry grow
     * after long events.
     */
    public void pruneCompleted() {
        tasks.removeIf(task -> {
            if (task == null) {
                return true;
            }
            boolean cancelled = task.isCancelled();
            boolean completed = completed(task);
            if (cancelled) {
                cancelledTaskIds.add(task.getTaskId());
            } else if (completed) {
                completedTaskIds.add(task.getTaskId());
            }
            return cancelled || completed;
        });
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
