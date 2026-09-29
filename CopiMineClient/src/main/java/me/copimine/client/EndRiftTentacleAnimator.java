package me.copimine.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Client-only sampler for animation clips imported from the supplied Kagune .bbmodel. */
public final class EndRiftTentacleAnimator {
    private static final KaguneModelImporter.ImportedModel MODEL = KaguneModelImporter.load();
    private static final Map<String, String> CLIP_BY_STATE = clipsByState();
    private static final float SOCKET_Y = 6.3125F;

    private EndRiftTentacleAnimator() {
    }

    public static boolean supportsAnimation(String animationId) {
        return CLIP_BY_STATE.containsKey(normalize(animationId));
    }

    public static boolean loops(String animationId) {
        String clipName = CLIP_BY_STATE.get(normalize(animationId));
        if (clipName == null) {
            return false;
        }
        KaguneModelImporter.Animation clip = MODEL.animation(clipName);
        // The supplied idle and shield clips return to their initial pose at
        // their authored endpoint, so they can safely cycle for long states.
        return "loop".equalsIgnoreCase(clip.loop())
                || clipName.equals("idle") || clipName.equals("shield_channel");
    }

    public static int durationTicks(String animationId) {
        KaguneModelImporter.Animation clip = clipFor(animationId);
        if (clip == null || !Double.isFinite(clip.lengthSeconds()) || clip.lengthSeconds() <= 0.0D) {
            KaguneModelImporter.Animation idle = MODEL.animation("idle");
            return Math.max(1, (int) Math.ceil(idle.lengthSeconds() * 20.0D));
        }
        return Math.max(1, (int) Math.ceil(clip.lengthSeconds() * 20.0D));
    }

    public static long loopDurationMillis(String animationId) {
        return durationTicks(animationId) * 50L;
    }

    public static boolean sameClip(String firstAnimationId, String secondAnimationId) {
        String first = CLIP_BY_STATE.get(normalize(firstAnimationId));
        String second = CLIP_BY_STATE.get(normalize(secondAnimationId));
        return first != null && first.equals(second);
    }

    public static EndRiftTentaclePose.TentaclePose poseFor(String animationId,
                                                            float progress,
                                                            long deterministicSeed) {
        String state = normalize(animationId);
        String clipName = CLIP_BY_STATE.getOrDefault(state, "idle");
        KaguneModelImporter.Animation clip = MODEL.animation(clipName);
        float sampleProgress = clamp(progress);
        EndRiftTentaclePose.BoneTransform base = transform(clip, "1layer", sampleProgress);
        EndRiftTentaclePose.BoneTransform seg01 = transform(clip, "1layer2", sampleProgress);
        EndRiftTentaclePose.BoneTransform seg02 = transform(clip, "2layer", sampleProgress);
        EndRiftTentaclePose.BoneTransform seg03 = transform(clip, "2layer2", sampleProgress);
        EndRiftTentaclePose.BoneTransform seg04 = transform(clip, "3layer", sampleProgress);
        EndRiftTentaclePose.BoneTransform seg05 = transform(clip, "3layer2", sampleProgress);
        EndRiftTentaclePose.BoneTransform identity = EndRiftTentaclePose.BoneTransform.identity();

        // Socket is a legacy client API. Gameplay capture/contact remains
        // server-authoritative and does not use this render-only marker.
        EndRiftTentaclePose.Socket socket = new EndRiftTentaclePose.Socket(0.0F, SOCKET_Y, 0.0F);
        return new EndRiftTentaclePose.TentaclePose(identity, base, seg01, seg02, seg03,
                seg04, seg05, identity, identity, identity, identity, identity, identity,
                socket, 1.0F);
    }

    public static float normalizedProgress(String animationId, long elapsedTicks) {
        int duration = durationTicks(animationId);
        long elapsed = Math.max(0L, elapsedTicks);
        if (loops(animationId)) {
            return (float) ((elapsed % duration) / (double) duration);
        }
        return clamp(elapsed / (float) duration);
    }

    private static EndRiftTentaclePose.BoneTransform transform(
            KaguneModelImporter.Animation clip, String boneName, float progress) {
        KaguneModelImporter.Vec3 translation = MODEL.sample(clip.name(), boneName, "position", progress);
        KaguneModelImporter.Vec3 rotationDegrees = MODEL.sample(clip.name(), boneName,
                "rotation", progress);
        return new EndRiftTentaclePose.BoneTransform(
                translation.x(), translation.y(), translation.z(),
                (float) Math.toRadians(rotationDegrees.x()),
                (float) Math.toRadians(rotationDegrees.y()),
                (float) Math.toRadians(rotationDegrees.z()),
                1.0F, 1.0F, 1.0F);
    }

    private static KaguneModelImporter.Animation clipFor(String animationId) {
        String clipName = CLIP_BY_STATE.get(normalize(animationId));
        return clipName == null ? null : MODEL.animation(clipName);
    }

    private static Map<String, String> clipsByState() {
        Map<String, String> clips = new LinkedHashMap<>();
        clips.put("EMERGING", "emerge");
        clips.put("READY", "idle");
        clips.put("TELEGRAPH_GRAB", "telegraph_grab");
        clips.put("GRAB_SUCCESS", "grab_success");
        clips.put("HOLD", "hold");
        clips.put("THROW", "trow");
        clips.put("MISS_RECOVERY", "grab_miss");
        clips.put("HIT_RECOVERY", "hurt");
        clips.put("DYING", "death");
        clips.put("DEAD_RESPAWN", "emerge");
        clips.put("RETRACT", "retract");
        clips.put("SPAWN_UNDER_PLAYER", "spawn_under_player");
        // Permanent guardians rest in SHIELD_CHANNEL for most of the fight.
        // The supplied shield_channel clip folds the rig into a sharply bent,
        // fast pose intended for a short channel cue.  It is not an idle loop;
        // use the authored 4-second idle cycle while the guardian is at rest.
        clips.put("SHIELD_CHANNEL", "idle");
        // A recovery window is longer than the authored hurt flinch. Leaving
        // the hurt clip at its endpoint made the tentacle hold a broken pose
        // for most of that window; let it return to the smooth idle cycle.
        clips.put("RECOVERY", "idle");
        clips.put("IDLE", "idle");
        clips.put("EMERGE", "emerge");
        clips.put("GRAB_MISS", "grab_miss");
        clips.put("HURT", "hurt");
        clips.put("DEATH", "death");
        return Map.copyOf(clips);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private static String normalize(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return "READY";
        }
        String raw = animationId.trim();
        int separator = raw.indexOf('|');
        if (separator >= 0) {
            raw = raw.substring(0, separator);
        }
        return raw.toUpperCase(Locale.ROOT);
    }
}
