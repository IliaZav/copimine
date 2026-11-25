package me.copimine.client;

import me.copimine.endevent.domain.SpellCastPacket;
import me.copimine.endevent.domain.SpellCastPacketCodec;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SignedSpellVisuals {
    private static final int MAX_ACTIVE = 64;
    private static final int MAX_RETIREMENTS = 192;
    private static final long MAX_CLOCK_SAMPLE_DRIFT = 250L;
    private final Map<String, Scene> active = new LinkedHashMap<>();
    private final Map<String, Long> retired = new LinkedHashMap<>();
    private final Map<String, SimpleSoundInstance> playing = new LinkedHashMap<>();
    private final Map<String, Boolean> audioSeen = new LinkedHashMap<>();
    private EndEventBridgeBodyCodec.ArenaBounds arena;
    private long latestBodyTime;
    private long clockOffset;
    private boolean suspended;

    private record Scene(String id, String dimension, SpellCastPacket cast, long startMillis,
                         long expiryMillis, long bodyTime) { }

    synchronized void bind(EndEventBridgeBodyCodec.Body bind, long now) {
        var next = EndEventBridgeBodyCodec.arenaBoundsFromContextBind(bind);
        if (!next.equals(arena)) {
            reset();
            arena = next;
            clockOffset = now - bind.bodyTimestampMillis();
        }
        latestBodyTime = Math.max(latestBodyTime, bind.bodyTimestampMillis());
    }

    synchronized void apply(EndEventBridgeBodyCodec.Body body, long now) {
        if (arena == null || suspended || !arena.eventUuid().equals(body.eventUuid())
                || arena.generation() != body.generation() || !arena.dimension().equals(body.subjectOrMode())
                || body.bodyTimestampMillis() < latestBodyTime) {
            return;
        }
        SpellCastPacket cast;
        try {
            cast = SpellCastPacketCodec.decode(Base64.getUrlDecoder().decode(body.clearPolicy()));
            if (cast.generation() != body.generation() || !cast.phase().name().equals(body.visualId())
                    || !SpellCastPacketCodec.inside(cast, arena.minX(), arena.minY(), arena.minZ(),
                            arena.maxX(), arena.maxY(), arena.maxZ())) {
                return;
            }
        } catch (IllegalArgumentException malformed) {
            return;
        }

        String id = body.instanceId();
        if (retired.containsKey(id)) return;
        Scene previous = active.get(id);
        long sentAt = body.bodyTimestampMillis();
        if (previous != null && sentAt < previous.bodyTime()) return;
        if (cast.phase() == SpellCastPacket.Phase.CANCEL) {
            active.remove(id);
            retired.put(id, sentAt);
            if (retired.size() > MAX_RETIREMENTS) retired.remove(retired.keySet().iterator().next());
            return;
        }
        if (SpellVisualGeometry.Spell.fromToken(cast.spellId()) == null || cast.lanes().isEmpty()) return;
        if (previous != null && previous.cast().startTick() > cast.startTick()) return;

        int elapsed = Integer.parseInt(body.status());
        long age = 50L * elapsed;
        long duration = 50L * cast.durationTicks();
        if (age >= duration || duration - age != body.durationMillis()) return;
        long sample = now - sentAt;
        if (sample < clockOffset) {
            long change = Math.max(-MAX_CLOCK_SAMPLE_DRIFT, sample - clockOffset);
            clockOffset += change;
        }
        long start = Math.min(now - age, sentAt + clockOffset - age);
        if (previous != null && previous.cast().startTick() == cast.startTick()) {
            start = Math.min(start, previous.startMillis());
        }
        long expire = start + duration;
        active.values().removeIf(scene -> scene.expiryMillis() <= now);
        if (expire <= now || active.size() >= MAX_ACTIVE && !active.containsKey(id)) return;
        active.put(id, new Scene(id, body.subjectOrMode(), cast, start, expire, sentAt));
    }

    synchronized void suspend() {
        active.clear();
        stopAudio();
        suspended = true;
    }

    synchronized void resume(String event, long generation, long timestamp) {
        if (arena == null || !arena.eventUuid().toString().equals(event) || arena.generation() != generation
                || timestamp < latestBodyTime) {
            return;
        }
        suspended = false;
        latestBodyTime = timestamp;
    }

    synchronized void reset() {
        active.clear();
        stopAudio();
        retired.clear();
        arena = null;
        latestBodyTime = 0L;
        clockOffset = 0L;
        suspended = false;
    }

    private void stopAudio() {
        if (playing.isEmpty()) {
            audioSeen.clear();
            return;
        }
        var manager = Minecraft.getInstance().getSoundManager();
        playing.values().forEach(manager::stop);
        playing.clear();
        audioSeen.clear();
    }

    synchronized void tickAudio(Minecraft game) {
        long now = System.currentTimeMillis();
        if (game.level == null || game.player == null || game.player.isDeadOrDying()) {
            stopAudio();
            return;
        }
        String dimension = game.level.dimension().identifier().getPath();
        var soundManager = game.getSoundManager();
        playing.entrySet().removeIf(voice -> {
            Scene scene = active.get(voice.getKey());
            if (scene == null || scene.expiryMillis() <= now || !scene.dimension().equals(dimension)) {
                soundManager.stop(voice.getValue());
                return true;
            }
            return false;
        });
        audioSeen.keySet().removeIf(id -> {
            Scene scene = active.get(id);
            return scene == null || scene.expiryMillis() <= now;
        });
        int started = 0;
        for (Scene scene : active.values()) {
            if (!scene.dimension().equals(dimension) || scene.expiryMillis() <= now
                    || audioSeen.putIfAbsent(scene.id(), true) != null) continue;
            // The authored file contains its own anticipation. A late snapshot
            // must not play that warning after the corresponding release.
            if (now - scene.startMillis() > 240L || started >= 4) continue;
            SpellCastPacket cast = scene.cast();
            var spell = SpellVisualGeometry.Spell.fromToken(cast.spellId());
            if (spell == null) continue;
            var audio = new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(
                    SpellVisualSoundRuntime.soundId(spell)), SoundSource.HOSTILE, 0.76f,
                    SpellVisualSoundRuntime.pitchFor(cast.seed()), RandomSource.create(),
                    cast.x(), cast.y(), cast.z());
            soundManager.play(audio);
            playing.put(scene.id(), audio);
            started++;
        }
    }

    void render(LevelRenderContext context) {
        Minecraft game = Minecraft.getInstance();
        if (context == null || game.level == null || game.player == null
                || game.player.isDeadOrDying()) return;
        var camera = game.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        String dimension = game.level.dimension().identifier().getPath();
        long now = System.currentTimeMillis();
        Vec3 cameraAt = camera.position();
        List<Scene> scenes = visible(dimension, cameraAt, now);
        if (scenes.isEmpty()) return;
        EndRiftRenderSubmission.submit(context, cameraAt, SpellVisualRenderer.layer(), (matrices, buffer) -> {
            int verticesRemaining = 56_000;
            for (Scene scene : scenes) {
                verticesRemaining -= SpellVisualRenderer.drawCast(matrices, buffer, scene.cast(),
                        scene.startMillis(), scene.expiryMillis(), now, verticesRemaining);
            }
        });
    }

    private synchronized List<Scene> visible(String dimension, Vec3 camera, long now) {
        active.values().removeIf(scene -> scene.expiryMillis() <= now);
        List<Scene> scenes = new ArrayList<>();
        for (Scene scene : active.values()) {
            if (!scene.dimension().equals(dimension)) continue;
            // Near hazard endpoints stay visible even for arena-wide casts.
            SpellCastPacket cast = scene.cast();
            double reach = cast.lanes().stream().mapToDouble(SpellCastPacket.Lane::length).max().orElse(0);
            double range = 112 + reach;
            if (camera.distanceToSqr(new Vec3(cast.x(), cast.y(), cast.z())) <= range * range) {
                scenes.add(scene);
            }
        }
        scenes.sort(java.util.Comparator.comparing((Scene scene) ->
                !SpellCastPacketCodec.isFangGeometry(scene.cast().spellId())));
        return List.copyOf(scenes);
    }
}
