package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.TentacleAnimationPolicy;

/**
 * Generation-scoped runtime index for server-owned tentacle displays.  It
 * contains no Bukkit code: entity transforms, particles and player movement
 * remain in the plugin adapter, while this class prevents stale state and
 * enforces the permanent/temporary caps.
 */
public final class TentacleController {
    public static final int MAX_PERMANENT = 8;
    public static final int MAX_TEMPORARY = 6;

    private long generation = Long.MIN_VALUE;
    private final Map<UUID, VisualState> states = new LinkedHashMap<>();

    public synchronized void begin(long generation) {
        this.generation = generation;
        states.clear();
    }

    public synchronized boolean owns(long expectedGeneration) {
        return generation != Long.MIN_VALUE && generation == expectedGeneration;
    }

    public synchronized boolean register(long expectedGeneration, UUID entityId,
                                         boolean temporary, int slot, long startedTick) {
        return register(expectedGeneration, entityId,
                temporary ? TentacleAnimationPolicy.Kind.UNDER_PLAYER
                        : TentacleAnimationPolicy.Kind.PERMANENT,
                slot, startedTick);
    }

    public synchronized boolean register(long expectedGeneration, UUID entityId,
                                         TentacleAnimationPolicy.Kind kind, int slot,
                                         long startedTick) {
        if (!owns(expectedGeneration) || entityId == null || slot < 0
                || kind == null || states.containsKey(entityId)) {
            return false;
        }
        boolean temporary = kind != TentacleAnimationPolicy.Kind.PERMANENT;
        if (temporary && temporaryCount() >= MAX_TEMPORARY
                || !temporary && permanentCount() >= MAX_PERMANENT) {
            return false;
        }
        states.put(entityId, new VisualState(entityId, kind, slot,
                TentacleAnimationPolicy.State.EMERGING, startedTick, null));
        return true;
    }

    public synchronized boolean transition(long expectedGeneration, UUID entityId,
                                            TentacleAnimationPolicy.State next,
                                            long startedTick, UUID target) {
        if (!owns(expectedGeneration)) {
            return false;
        }
        VisualState current = states.get(entityId);
        if (current == null || next == null) {
            return false;
        }
        TentacleAnimationPolicy.State canonical = TentacleAnimationPolicy.canonical(next);
        states.put(entityId, new VisualState(entityId, current.kind(), current.slot(),
                canonical, startedTick, target));
        return true;
    }

    /**
     * Compatibility overload for callers that already hold this controller's
     * current generation. New event code should pass the generation
     * explicitly so a stale callback cannot mutate a replacement encounter.
     */
    public synchronized boolean transition(UUID entityId, TentacleAnimationPolicy.State next,
                                            long startedTick, UUID target) {
        return transition(generation, entityId, next, startedTick, target);
    }

    public synchronized VisualState state(UUID entityId) {
        return states.get(entityId);
    }

    public synchronized boolean markerReached(UUID entityId,
                                              TentacleAnimationPolicy.Marker marker,
                                              long nowTick) {
        VisualState current = states.get(entityId);
        if (current == null || marker == null || nowTick < current.startedTick()) {
            return false;
        }
        int markerTick = TentacleAnimationPolicy.markerTick(current.state(), marker);
        return markerTick >= 0
                && nowTick - current.startedTick() >= markerTick;
    }

    public synchronized void remove(UUID entityId) {
        if (entityId != null) {
            states.remove(entityId);
        }
    }

    public synchronized int count() {
        return states.size();
    }

    public synchronized int permanentCount() {
        return (int) states.values().stream().filter(value -> !value.temporary()).count();
    }

    public synchronized int temporaryCount() {
        return (int) states.values().stream().filter(VisualState::temporary).count();
    }

    public synchronized Map<UUID, VisualState> states() {
        return Map.copyOf(states);
    }

    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        states.clear();
    }

    public record VisualState(UUID entityId, TentacleAnimationPolicy.Kind kind, int slot,
                              TentacleAnimationPolicy.State state,
                              long startedTick, UUID target) {
        public boolean temporary() {
            return kind != TentacleAnimationPolicy.Kind.PERMANENT;
        }
    }
}
