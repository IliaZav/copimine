package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.SpellCastPacket;
import me.copimine.endevent.domain.SpellCastPacketCodec;
import me.copimine.endevent.domain.SpellVfxLodPolicy;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owns the signed presentation lifetime of server-authored hazards. */
public final class SpellCastRuntime {
    private static final int MAX_ACTIVE = 64;
    private static final int REFRESH_TICKS = 10;

    @FunctionalInterface
    public interface Sender {
        boolean send(Player viewer, String instance, String dimension, SpellCastPacket packet,
                     String encoded, int elapsedTicks);
    }

    public record CastKey(String spell, long owner, UUID actor) { }

    private record Cast(long id, String event, long generation, String dimension,
                        Location origin, SpellCastPacket packet, long expiresTick,
                        long lastRefresh) { }

    private final Map<CastKey, Cast> active = new LinkedHashMap<>();
    private long nextId;

    public boolean publish(CastKey key, String event, long generation, String dimension, Location origin,
                           List<SpellCastPacket.Lane> geometry, long tick, int duration, int stage,
                           boolean refreshable, Collection<? extends Player> viewers, Sender sender) {
        if (generation < 1 || generation > Integer.MAX_VALUE || tick < 0 || tick > Integer.MAX_VALUE
                || origin.getWorld() == null || geometry.isEmpty()) {
            return false;
        }
        active.entrySet().removeIf(entry -> entry.getValue().expiresTick() <= tick
                || entry.getValue().generation() != generation || !entry.getValue().event().equals(event));
        Cast previous = active.get(key);
        if (previous == null && active.size() >= MAX_ACTIVE) {
            return false;
        }
        boolean keepStart = refreshable && previous != null && previous.packet().stage() == stage;
        if (keepStart && tick - previous.lastRefresh() < REFRESH_TICKS
                && previous.packet().lanes().equals(geometry)) {
            return true;
        }
        long preserved = keepStart ? previous.packet().startTick() : tick;
        if (tick - preserved + duration > 600) preserved = tick;
        long start = preserved;
        SpellCastPacket packet = new SpellCastPacket(previous == null
                ? SpellCastPacket.Phase.START : SpellCastPacket.Phase.STAGE,
                (int) generation, key.spell(), key.owner(), (int) start,
                (int) (tick - start) + duration, stage, 0, 0, (float) origin.getX(),
                (float) origin.getY(), (float) origin.getZ(), origin.getYaw(), geometry);
        long id = previous == null ? Math.incrementExact(nextId) : previous.id();
        nextId = Math.max(nextId, id);
        Cast cast = new Cast(id, event, generation, dimension, origin.clone(), packet,
                tick + duration, tick);
        active.put(key, cast);
        for (Player viewer : viewers) {
            if (eligible(viewer, cast)) {
                send(viewer, cast, tick, viewers.size(), sender);
            }
        }
        return true;
    }

    public void resume(Player viewer, String event, long generation, long tick, int roster, Sender sender) {
        for (Cast cast : active.values()) {
            if (cast.expiresTick() > tick && cast.generation() == generation
                    && cast.event().equals(event) && eligible(viewer, cast)) {
                send(viewer, cast, tick, roster, sender);
            }
        }
    }

    public void cancel(CastKey key, long generation, Collection<? extends Player> viewers, Sender sender) {
        Cast cast = active.get(key);
        if (cast == null || cast.generation() != generation || !active.remove(key, cast)) {
            return;
        }
        sendCancel(cast, viewers, sender);
    }

    public void cancelActor(UUID id, long generation, Collection<? extends Player> viewers, Sender sender) {
        active.entrySet().removeIf(entry -> {
            Cast cast = entry.getValue();
            if (!Objects.equals(entry.getKey().actor(), id) || cast.generation() != generation) return false;
            sendCancel(cast, viewers, sender);
            return true;
        });
    }

    public void clear(long generation, Collection<? extends Player> viewers, Sender sender) {
        for (Cast cast : active.values()) {
            if (cast.generation() == generation) sendCancel(cast, viewers, sender);
        }
        active.clear();
    }

    private static void sendCancel(Cast cast, Collection<? extends Player> viewers, Sender sender) {
        SpellCastPacket previous = cast.packet();
        SpellCastPacket cancel = new SpellCastPacket(SpellCastPacket.Phase.CANCEL, previous.generation(),
                previous.spellId(), previous.seed(), previous.startTick(), 0, previous.stage(),
                previous.style(), 0, previous.x(), previous.y(), previous.z(), previous.yaw(), List.of());
        String encoded = encode(cancel);
        for (Player viewer : viewers) {
            if (viewer.isOnline()) sender.send(viewer, instance(cast), cast.dimension(), cancel, encoded, 0);
        }
    }

    private static void send(Player viewer, Cast cast, long tick, int roster, Sender sender) {
        SpellCastPacket state = cast.packet();
        int lod = SpellVfxLodPolicy.lodForViewer(viewer.getLocation().distance(cast.origin()), roster);
        SpellCastPacket packet = new SpellCastPacket(state.phase(), state.generation(), state.spellId(),
                state.seed(), state.startTick(), state.durationTicks(), state.stage(), state.style(),
                lod, state.x(), state.y(), state.z(), state.yaw(), state.lanes());
        sender.send(viewer, instance(cast), cast.dimension(), packet, encode(packet),
                (int) Math.max(0L, tick - state.startTick()));
    }

    private static boolean eligible(Player viewer, Cast cast) {
        return viewer.isOnline() && viewer.getWorld().equals(cast.origin().getWorld());
    }

    private static String instance(Cast cast) {
        return cast.event() + ":" + cast.generation() + ":spell:" + cast.id();
    }

    private static String encode(SpellCastPacket packet) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(SpellCastPacketCodec.encode(packet));
    }
}
