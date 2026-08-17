package me.copimine.endevent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitTask;

/** Owns every scheduled callback created for one event generation. */
public final class EventTaskRegistry {
    private final long generation;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

    public EventTaskRegistry(long generation) {
        this.generation = generation;
    }

    public long generation() {
        return generation;
    }

    public <T extends BukkitTask> T register(T task) {
        if (task != null) {
            tasks.add(task);
        }
        return task;
    }

    public boolean owns(long callbackGeneration) {
        return generation == callbackGeneration;
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
        tasks.clear();
    }

    public int size() {
        return tasks.size();
    }
}
