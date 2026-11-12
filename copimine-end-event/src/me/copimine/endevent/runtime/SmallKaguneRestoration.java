package me.copimine.endevent.runtime;

import java.util.*;
import me.copimine.endevent.domain.SmallKaguneRosterSnapshot;
import me.copimine.endevent.domain.SmallKaguneRosterSnapshot.Actor;

/** Rehydrates a saved living roster atomically; no guardian resurrection or offline clock. */
public final class SmallKaguneRestoration {
    public interface Host {
        List<Actor> livingActors();
        UUID spawn(Actor actor);
        boolean confirms(UUID actor, Actor saved);
        boolean remove(UUID actor);
        boolean checkpoint();
        void failed(String reason);
    }
    private final Host host;
    private String event;
    private long generation, nextRetryTick;
    private int attempts;
    private List<Actor> pending = List.of();
    private Map<String, String> rejected = Map.of();
    private final List<UUID> rollbackPending = new ArrayList<>();

    public SmallKaguneRestoration(Host host) { this.host = Objects.requireNonNull(host); }
    public boolean pending() { return !pending.isEmpty() || !rollbackPending.isEmpty() || !rejected.isEmpty(); }

    public void restore(Map<String, String> saved, String event, long generation) {
        if (!rollbackPending.isEmpty()) throw new IllegalStateException("SMALL_KAGUNE_CLEANUP_PENDING");
        this.event = event; this.generation = generation; attempts = 0; nextRetryTick = 0L;
        try {
            pending = SmallKaguneRosterSnapshot.decode(saved, event, generation);
            rejected = Map.of();
        } catch (RuntimeException invalid) {
            Map<String, String> evidence = new LinkedHashMap<>();
            if (saved != null) saved.forEach((key, value) -> {
                if (key.startsWith(SmallKaguneRosterSnapshot.PREFIX)) evidence.put(key, value);
            });
            rejected = Collections.unmodifiableMap(evidence);
            throw invalid;
        }
    }

    public Map<String, String> snapshot(String event, long generation) {
        if (Objects.equals(this.event, event) && this.generation == generation && !rejected.isEmpty()) return rejected;
        List<Actor> actors = Objects.equals(this.event, event) && this.generation == generation
                && !pending.isEmpty() ? pending : host.livingActors();
        return SmallKaguneRosterSnapshot.encode(event, generation, actors);
    }

    public boolean tick(String event, long generation, long tick) {
        if (!pending()) return true;
        if (!rejected.isEmpty()) { host.failed("INVALID_SMALL_KAGUNE_ROSTER"); return false; }
        if (!Objects.equals(this.event, event) || this.generation != generation) {
            host.failed("SMALL_KAGUNE_RESTORE_SCOPE_CHANGED"); return false;
        }
        if (tick < nextRetryTick) return false;
        nextRetryTick = tick + 20L;
        if (!rollback()) {
            if (++attempts >= 3) host.failed("SMALL_KAGUNE_RESTORE_ROLLBACK_PENDING");
            return false;
        }
        List<Actor> saved = pending;
        try {
            for (Actor actor : saved) {
                UUID spawned = host.spawn(actor);
                if (spawned == null) throw new IllegalStateException("actor-spawn-refused");
                rollbackPending.add(spawned);
                if (!host.confirms(spawned, actor)) throw new IllegalStateException("actor-restore-unconfirmed");
            }
            pending = List.of();
            if (!host.checkpoint()) throw new IllegalStateException("checkpoint-refused");
            rollbackPending.clear(); attempts = 0;
            return true;
        } catch (RuntimeException unavailable) {
            pending = saved;
            boolean cleaned = rollback();
            if (++attempts >= 3 || !cleaned) host.failed("SMALL_KAGUNE_RESTORE_FAILED: " + unavailable.getMessage());
            return false;
        }
    }

    private boolean rollback() {
        for (UUID actor : new ArrayList<>(rollbackPending)) {
            try { if (host.remove(actor)) rollbackPending.remove(actor); }
            catch (RuntimeException unavailable) { /* retain exact UUID for retry */ }
        }
        return rollbackPending.isEmpty();
    }

    /** Real event cancellation clears saved intentions only after rollback succeeds. */
    public boolean clear() {
        if (!rollback()) return false;
        pending = List.of(); rejected = Map.of(); event = null; generation = 0L; return true;
    }
}
