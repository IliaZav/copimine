package me.copimine.client;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Bounded semantic projection. No world, health, inventory or outcome authority. */
public final class EchoPresentationState {
    public static final int MAX_ACTORS = 32;
    private static final int MAX_IDENTITIES = 128;
    private static final long LEASE_MILLIS = 2_000;
    public enum Pose { STANDING, CROUCHING }
    public enum UseHand { NONE, MAIN, OFF }

    public record Frame(UUID event, long generation, long epoch, UUID duel, UUID actor, UUID owner,
                        String dimension, long sequence, Pose pose, boolean sprinting,
                        UseHand hand, int useElapsed, int useDuration, long swingSerial,
                        long hurtSerial, int deathTicks, int equipmentVersion) {
        public Frame {
            Objects.requireNonNull(event); Objects.requireNonNull(duel);
            Objects.requireNonNull(actor); Objects.requireNonNull(owner);
            Objects.requireNonNull(pose); Objects.requireNonNull(hand);
            if (generation <= 0 || epoch <= 0 || sequence <= 0 || actor.equals(owner)
                    || dimension == null || dimension.length() > 64
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")
                    || useElapsed < 0 || useDuration < 0 || useDuration > 72_000
                    || useElapsed > useDuration || hand == UseHand.NONE && useDuration != 0
                    || swingSerial < 0 || hurtSerial < 0 || deathTicks < 0 || deathTicks > 20
                    || equipmentVersion < 0) throw new IllegalArgumentException("Invalid Echo semantic frame");
        }

        public UUID presentationId() {
            return UUID.nameUUIDFromBytes(("copimine:echo:" + event + ':' + generation + ':'
                    + epoch + ':' + duel + ':' + actor).getBytes(StandardCharsets.UTF_8));
        }

        private boolean sameActor(Frame other) {
            return event.equals(other.event) && generation == other.generation && epoch == other.epoch
                    && duel.equals(other.duel) && actor.equals(other.actor) && owner.equals(other.owner)
                    && dimension.equals(other.dimension);
        }
    }

    public record View(Frame frame, int useElapsed) { }
    public record TextureTicket(UUID event, long generation, long epoch, UUID duel, UUID actor, UUID owner) { }
    private static final class Entry {
        Frame frame; long receivedAt; boolean removed;
        Entry(Frame frame, long now) { this.frame = frame; receivedAt = now; }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Set<TextureTicket> removedTickets = new HashSet<>();
    private UUID event;
    private long generation, epoch;
    private boolean closed;

    public synchronized boolean bind(Frame frame, long nowMillis) {
        if (frame == null || nowMillis < 0 || nowMillis > Long.MAX_VALUE - LEASE_MILLIS
                || removedTickets.contains(ticket(frame))) return false;
        if (event != null) {
            if (frame.generation < generation || frame.generation == generation
                    && (!event.equals(frame.event) || frame.epoch < epoch)) return false;
            if (frame.generation > generation || frame.epoch > epoch) clearScope();
        }
        if (event == null) { event = frame.event; generation = frame.generation; epoch = frame.epoch; }
        if (closed) return false;
        Entry existing = entries.get(frame.actor);
        if (existing != null) return update(frame, nowMillis);
        if (entries.size() >= MAX_IDENTITIES || activeCount() >= MAX_ACTORS) return false;
        entries.put(frame.actor, new Entry(frame, nowMillis));
        return true;
    }

    public synchronized boolean update(Frame frame, long nowMillis) {
        if (!current(frame) || nowMillis < 0 || nowMillis > Long.MAX_VALUE - LEASE_MILLIS) return false;
        Entry entry = entries.get(frame.actor);
        if (entry == null || expired(entry, nowMillis) || !entry.frame.sameActor(frame)
                || frame.sequence <= entry.frame.sequence || frame.equipmentVersion < entry.frame.equipmentVersion
                || frame.swingSerial < entry.frame.swingSerial || frame.hurtSerial < entry.frame.hurtSerial
                || frame.deathTicks < entry.frame.deathTicks) return false;
        entry.frame = frame; entry.receivedAt = nowMillis;
        return true;
    }

    public synchronized boolean remove(Frame frame) {
        if (frame == null) return false;
        if (!current(frame)) {
            if (event != null && (closed || frame.generation < generation
                    || frame.generation == generation && (!event.equals(frame.event) || frame.epoch < epoch)))
                return false;
            if (removedTickets.size() >= MAX_IDENTITIES && !removedTickets.contains(ticket(frame))) return false;
            removedTickets.add(ticket(frame));
            return true;
        }
        Entry entry = entries.get(frame.actor);
        if (entry == null) {
            if (entries.size() >= MAX_IDENTITIES) return false;
            entry = new Entry(frame, 0); entries.put(frame.actor, entry);
        } else if (!entry.frame.sameActor(frame) || frame.sequence < entry.frame.sequence) return false;
        entry.frame = frame; entry.removed = true;
        return true;
    }

    public synchronized View view(UUID actor, String dimension, long nowMillis) {
        Entry entry = entries.get(actor);
        if (closed || entry == null || nowMillis < 0 || expired(entry, nowMillis)
                || !entry.frame.dimension.equals(dimension)) return null;
        long elapsed = Math.max(0, nowMillis - entry.receivedAt) / 50;
        int use = (int) Math.min(entry.frame.useDuration, entry.frame.useElapsed + elapsed);
        return new View(entry.frame, use);
    }

    public synchronized TextureTicket textureTicket(UUID actor) {
        Entry entry = entries.get(actor);
        if (closed || entry == null || entry.removed) return null;
        Frame f = entry.frame;
        return new TextureTicket(f.event, f.generation, f.epoch, f.duel, f.actor, f.owner);
    }

    public synchronized boolean acceptsTexture(TextureTicket ticket) {
        return ticket != null && ticket.equals(textureTicket(ticket.actor));
    }

    public synchronized int activeCount() {
        return (int) entries.values().stream().filter(entry -> !entry.removed).count();
    }

    /** Retain the closed scope so queued bindings cannot revive the old view. */
    public synchronized void clear() {
        if (event == null) return;
        closed = true;
        entries.values().forEach(entry -> entry.removed = true);
    }

    /** Transport reset discards identities only after the old connection closes. */
    public synchronized void reset() { clearScope(); removedTickets.clear(); }

    public synchronized Set<UUID> actorIds() { return Set.copyOf(entries.keySet()); }

    /** A native unload is terminal in this scope, even without a remove packet. */
    public synchronized void retire(UUID actor) {
        Entry entry = entries.get(actor);
        if (entry != null) entry.removed = true;
    }

    private static TextureTicket ticket(Frame f) {
        return new TextureTicket(f.event, f.generation, f.epoch, f.duel, f.actor, f.owner);
    }

    private boolean current(Frame frame) {
        return !closed && frame != null && event != null && event.equals(frame.event)
                && frame.generation == generation && frame.epoch == epoch;
    }

    private boolean expired(Entry entry, long now) {
        if (entry.removed) return true;
        if (now >= entry.receivedAt && now - entry.receivedAt >= LEASE_MILLIS) entry.removed = true;
        return entry.removed;
    }

    private void clearScope() {
        entries.clear(); event = null; generation = 0; epoch = 0; closed = false;
    }
}
