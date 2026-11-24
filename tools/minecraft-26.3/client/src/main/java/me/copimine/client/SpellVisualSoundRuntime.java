package me.copimine.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One-shot authored audio for the V5.1 spell casts (26.3 client).
 *
 * <p>Sections 9A.03(F) and 9A.05 require every hero ability to carry its own
 * anticipation / release / tail sound, positional in the world, bounded to one
 * voice per cast and silent the instant the visual cue is gone. The server
 * still sends nothing but the compact {@code v51-<spell>-<seed>} cue, so this
 * runtime derives the event from that cue and plays the matching authored
 * sample once.</p>
 *
 * <p>Playback is keyed by the cue instance and its server start stamp: the
 * manager refreshes a live telegraph many times with the same key but the same
 * start, so the sound fires once per cast and never loops. When the cue leaves
 * the snapshot list the key is forgotten, so the next cast of the same spell by
 * the same caster sounds again. All state is touched only from the client main
 * thread, which is where the VFX manager ticks.</p>
 */
public final class SpellVisualSoundRuntime {

    /** Cap a single frame's new voices so a burst cannot stack into a wall of sound. */
    static final int MAX_SOUNDS_PER_FRAME = 4;

    /** Reference volume; the player's own Hostile Creatures slider still applies. */
    private static final float VOLUME = 0.85F;

    private static final Map<String, Long> soundedStart = new HashMap<>();

    private SpellVisualSoundRuntime() {
    }

    /** Stable sound event id for one authored spell family. */
    public static Identifier soundId(SpellVisualGeometry.Spell spell) {
        return Identifier.fromNamespaceAndPath("copimineclient", "spell." + spell.token());
    }

    /**
     * Observe the current frame's world cues and play the authored sample for
     * any spell cast seen for the first time. Must run on the client main thread.
     */
    public static void observe(List<EndEventWorldVfxManager.BeamSnapshot> snapshots, long nowMillis) {
        if (snapshots == null || snapshots.isEmpty()) {
            soundedStart.clear();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        Set<String> present = new HashSet<>();
        int played = 0;
        for (EndEventWorldVfxManager.BeamSnapshot beam : snapshots) {
            String instance = beam.instanceId();
            SpellVisualGeometry.Spell spell = SpellVisualGeometry.spellCue(instance);
            if (spell == null) {
                continue;
            }
            present.add(instance);
            Long previous = soundedStart.put(instance, beam.startedAtMillis());
            if (!shouldSound(previous, beam.startedAtMillis())) {
                continue;
            }
            if (played >= MAX_SOUNDS_PER_FRAME) {
                continue;
            }
            long seed = SpellVisualGeometry.cueSeed(instance);
            play(client, spell, beam.start().x, beam.start().y, beam.start().z, pitchFor(seed));
            played++;
        }
        soundedStart.keySet().retainAll(present);
    }

    /**
     * A cue sounds the first time it is seen and again only when the server
     * starts a genuinely new cast. A refresh of the same live telegraph keeps
     * the same start stamp, so it must stay silent.
     */
    static boolean shouldSound(Long previousStart, long currentStart) {
        return previousStart == null || previousStart.longValue() != currentStart;
    }

    /** Small deterministic pitch offset so repeated casts do not sound identical. */
    static float pitchFor(long seed) {
        return 0.94F + 0.12F * (float) SpellVisualGeometry.splitUnit(seed, 99L);
    }

    /** Forget every remembered cast, e.g. on reconnect or event reset. */
    public static void reset() {
        soundedStart.clear();
    }

    private static void play(Minecraft client, SpellVisualGeometry.Spell spell,
                             double x, double y, double z, float pitch) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(soundId(spell));
        client.level.playLocalSound(x, y, z, event, SoundSource.HOSTILE, VOLUME, pitch, false);
    }
}
