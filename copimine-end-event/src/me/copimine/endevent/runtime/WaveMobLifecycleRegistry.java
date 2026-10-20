package me.copimine.endevent.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Retains completion ownership while numbered-wave mobs are in unloaded chunks. */
public final class WaveMobLifecycleRegistry {
    public enum LoadDisposition {
        REHYDRATE,
        QUARANTINE
    }

    private record Owner(String eventId, long generation, int wave, boolean portalSpawn) { }

    private final Map<UUID, Owner> unloaded = new HashMap<>();

    public synchronized void onUnload(UUID entityId, String eventId, long generation, int wave) {
        onUnload(entityId, eventId, generation, wave, false);
    }

    public synchronized void onUnload(UUID entityId, String eventId, long generation,
                                      int wave, boolean portalSpawn) {
        if (entityId == null || eventId == null || eventId.isBlank()
                || generation <= 0L || wave < 1 || wave > 7) {
            return;
        }
        unloaded.put(entityId, new Owner(eventId, generation, wave, portalSpawn));
    }

    /** Remove the unload marker and only rehydrate an entity still owned by this live wave. */
    public synchronized LoadDisposition onLoad(UUID entityId,
                                               String currentEventId,
                                               long currentGeneration,
                                               int activeWave,
                                               boolean wavePhaseActive,
                                               String entityEventId,
                                               long entityGeneration,
                                               int entityWave) {
        if (entityId != null) unloaded.remove(entityId);
        boolean current = currentEventId != null && !currentEventId.isBlank()
                && currentEventId.equals(entityEventId)
                && currentGeneration > 0L && currentGeneration == entityGeneration
                && activeWave >= 1 && activeWave <= 7 && activeWave == entityWave
                && wavePhaseActive;
        return current ? LoadDisposition.REHYDRATE : LoadDisposition.QUARANTINE;
    }

    public synchronized void onDeath(UUID entityId) {
        if (entityId != null) unloaded.remove(entityId);
    }

    public synchronized int count(String eventId, long generation, int wave) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || wave < 1 || wave > 7) {
            return 0;
        }
        unloaded.entrySet().removeIf(entry -> entry.getValue().generation() != generation
                || !Objects.equals(entry.getValue().eventId(), eventId));
        int count = 0;
        for (Owner owner : unloaded.values()) {
            if (owner.generation() == generation && owner.wave() == wave
                    && Objects.equals(owner.eventId(), eventId)) {
                count++;
            }
        }
        return count;
    }

    public synchronized int countPortalSpawns(String eventId, long generation, int wave) {
        if (eventId == null || eventId.isBlank() || generation <= 0L || wave < 1 || wave > 7) {
            return 0;
        }
        unloaded.entrySet().removeIf(entry -> entry.getValue().generation() != generation
                || !Objects.equals(entry.getValue().eventId(), eventId));
        int count = 0;
        for (Owner owner : unloaded.values()) {
            if (owner.portalSpawn() && owner.generation() == generation && owner.wave() == wave
                    && Objects.equals(owner.eventId(), eventId)) {
                count++;
            }
        }
        return count;
    }
}
