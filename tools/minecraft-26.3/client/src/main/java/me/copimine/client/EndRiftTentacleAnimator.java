package me.copimine.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Client-only sampler for animation clips imported from the supplied Kagune .bbmodel. */
public final class EndRiftTentacleAnimator {
    private static final KaguneModelImporter.ImportedModel MODEL = KaguneModelImporter.load();
    private static final Map<String, String> CLIP_BY_STATE = clipsByState();
    private static final float SOCKET_Y = 6.3125F;
    private static final java.util.Set<String> HOLD_CLIPS = java.util.Set.of("hold");

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
        // The supplied clips are all authored with loop=once, including the
        // 4-second idle. HOLD is the one state that genuinely must cycle: it is
        // a sustained grab, and the server keeps the player locked in the socket
        // for the whole window. Driving it with a once-clip froze the rig on its
        // final keyframe. See HOLD_CLIPS for how that clip is closed cleanly.
        return "loop".equalsIgnoreCase(clip.loop())
                || clipName.equals("idle") || clipName.equals("shield_channel")
                || HOLD_CLIPS.contains(clipName);
    }

    public static int durationTicks(String animationId) {
        switch (normalize(animationId)) {
            case "SMALL_WINDUP": return 12;
            case "SMALL_STRIKE": return 10;
            case "SMALL_RECOVERY": return 18;
            default: break;
        }
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
        // Retimed sections of the supplied continuous rig, never a grab/hold
        // pose or a false hurt reaction. Match the server's tick-4 contact.
        if (state.equals("SMALL_WINDUP")) {
            return EndRiftTentaclePose.TentaclePose.lerp(
                    poseFor("IDLE", 0.0F, deterministicSeed),
                    poseFor("THROW", 0.20F, deterministicSeed), clamp(progress));
        }
        if (state.equals("SMALL_STRIKE")) {
            return poseFor("THROW", 0.20F + 0.50F * clamp(progress), deterministicSeed);
        }
        if (state.equals("SMALL_RECOVERY")) {
            return EndRiftTentaclePose.TentaclePose.lerp(
                    poseFor("THROW", 0.70F, deterministicSeed),
                    poseFor("IDLE", 0.0F, deterministicSeed), clamp(progress));
        }
        String clipName = CLIP_BY_STATE.getOrDefault(state, "idle");
        KaguneModelImporter.Animation clip = MODEL.animation(clipName);
        float sampleProgress = clipProgress(animationId, progress);
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

    /**
     * Sample progress inside an authored clip, rescaled when the clip runs short.
     *
     * <p>The supplied HOLD clip carries its last keyframe at 0.50 s but declares
     * a 1.208 s length. Sampling it against the declared length meant the rig
     * spent the final 58 percent of every hold frozen on the endpoint, and then
     * snapped when the transition to THROW re-based its own clock back to zero.
     * For a clip that is actually driven as a loop the sample must span the
     * keyframed extent, not the declared one.</p>
     */
    public static float clipProgress(String animationId, float progress) {
        String state = normalize(animationId);
        String clipName = CLIP_BY_STATE.get(state);
        if (clipName == null) {
            return clamp(progress);
        }
        float sample = clamp(progress);
        if (!HOLD_CLIPS.contains(clipName)) {
            return sample;
        }
        KaguneModelImporter.Animation clip = MODEL.animation(clipName);
        double authored = keyframedExtent(clip);
        double declared = clip == null ? 0.0D : clip.lengthSeconds();
        if (!(authored > 0.0D) || !(declared > authored)) {
            return sample;
        }
        return clamp((float) (sample * declared / authored));
    }

    /** Latest keyframe time of a clip across every bone and channel. */
    static double keyframedExtent(KaguneModelImporter.Animation clip) {
        if (clip == null) {
            return 0.0D;
        }
        double latest = 0.0D;
        for (java.util.Map<String, java.util.List<KaguneModelImporter.Keyframe>> channels
                : clip.tracks().values()) {
            for (java.util.List<KaguneModelImporter.Keyframe> frames : channels.values()) {
                for (KaguneModelImporter.Keyframe frame : frames) {
                    if (Double.isFinite(frame.timeSeconds()) && frame.timeSeconds() > latest) {
                        latest = frame.timeSeconds();
                    }
                }
            }
        }
        return latest;
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
        clips.put("SMALL_WINDUP", "trow");
        clips.put("SMALL_STRIKE", "trow");
        clips.put("SMALL_RECOVERY", "trow");
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
