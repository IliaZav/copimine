package me.copimine.client;

import me.copimine.protocol.KaguneSpellFrame;
import me.copimine.protocol.KaguneRingFrame;

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

    /** Spell-specific anticipation, contact and withdrawal on the original rig.
     * Extra curvature is gone before the first server-authorized stab contact.
     */
    public static EndRiftTentaclePose.TentaclePose spellPose(KaguneSpellFrame frame, float progress) {
        float p=clamp(progress), ease=p*p*(3F-2F*p);
        var upright=poseFor("EMERGE",1F,0L);
        var coil=new EndRiftTentaclePose.TentaclePose(upright.root(),
                curl(upright.base(),-.04F),curl(upright.seg_01(),-.08F),
                curl(upright.seg_02(),-.12F),curl(upright.seg_03(),.04F),
                curl(upright.seg_04(),.08F),curl(upright.seg_05(),.16F),
                upright.seg_06(),upright.tip(),upright.tip_claw_1(),upright.tip_claw_2(),
                upright.tip_claw_3(),upright.tip_claw_4(),upright.grab_socket(),upright.rigScale());
        return switch(frame.stage()) {
            case KaguneSpellFrame.EMERGING -> frame.kind()==KaguneSpellFrame.STAB
                    ? EndRiftTentaclePose.TentaclePose.lerp(poseFor("EMERGE",p,0L),coil,ease)
                    : poseFor("EMERGE",p,0L);
            case KaguneSpellFrame.STRIKING -> frame.kind()==KaguneSpellFrame.STAB && p<.25F
                    ? EndRiftTentaclePose.TentaclePose.lerp(coil,upright,Math.min(1F,p*4F)) : upright;
            case KaguneSpellFrame.RETRACTING -> EndRiftTentaclePose.TentaclePose.lerp(
                    upright,poseFor("RETRACT",p,0L),ease);
            default -> upright;
        };
    }

    /** Ring stems stay upright while their moving server columns can hit. */
    public static EndRiftTentaclePose.TentaclePose ringPose(KaguneRingFrame frame) {
        var upright=poseFor("EMERGE",1F,0L);
        if (frame.stage()==KaguneRingFrame.EMERGE)
            return poseFor("EMERGE",(float)frame.rise(),0L);
        if (frame.stage()==KaguneRingFrame.RETRACT) {
            float p=(float)(1D-frame.rise());
            return EndRiftTentaclePose.TentaclePose.lerp(upright,poseFor("RETRACT",p,0L),p*p*(3F-2F*p));
        }
        // Counter-rotating distal joints give a small travelling shiver rather
        // than folding the entire THROW rig across the advertised escape gap.
        float ripple=(float)Math.sin(frame.strike()*Math.PI*6D)*.025F;
        return new EndRiftTentaclePose.TentaclePose(upright.root(),upright.base(),upright.seg_01(),
                upright.seg_02(),upright.seg_03(),curl(upright.seg_04(),ripple),
                curl(upright.seg_05(),-ripple),upright.seg_06(),upright.tip(),
                upright.tip_claw_1(),upright.tip_claw_2(),upright.tip_claw_3(),upright.tip_claw_4(),
                upright.grab_socket(),upright.rigScale());
    }

    /** How far the travelling ring ripple dips below full height (0..1). */
    public static final double RING_WAVE_AMPLITUDE = 0.26D;
    /** Lowest a stem may dip while the row is authoritative for contact. */
    public static final double RING_WAVE_FLOOR = 0.70D;
    /** Fraction of the row window one stem trails its neighbour by. */
    public static final double RING_WAVE_LAG = 0.34D;

    private static EndRiftTentaclePose.BoneTransform curl(EndRiftTentaclePose.BoneTransform bone,float angle) {
        return new EndRiftTentaclePose.BoneTransform(bone.translationX(),bone.translationY(),bone.translationZ(),
                bone.pitch()+angle,bone.yaw(),bone.roll(),bone.scaleX(),bone.scaleY(),bone.scaleZ());
    }

    /**
     * Height of one stem in a ring row.
     *
     * <p>The shipped row handed every stem the same {@code rise}, so a ring
     * appeared as one rigid wall that slid outward - the owner called it "a
     * row that stands up and drives". A wave needs neighbours at different
     * heights: each stem now trails the one before it during emerge and
     * retract, and a slow ripple travels around the circumference while the
     * row sweeps. The ripple never drops below
     * {@link #RING_WAVE_FLOOR}, so every stem still covers the authoritative
     * contact band and the visual can never hide a damaging column.</p>
     *
     * @param index    stem index around the circle
     * @param count    total stems in the row
     * @param stage    {@link KaguneRingFrame#EMERGE}, {@code TRAVEL} or {@code RETRACT}
     * @param baseRise the frame's interpolated row rise (0..1)
     * @param strike   the frame's sweep progress (0..1)
     */
    public static double ringInstanceRise(int index, int count, int stage,
                                          double baseRise, double strike) {
        double base = clamp01(baseRise);
        if (count <= 0) return base;
        double phase = (double) Math.floorMod(index, count) / count;
        if (stage == KaguneRingFrame.TRAVEL) {
            double ripple = Math.sin((phase * 2D - strike * 2D) * Math.PI * 2D)
                    * RING_WAVE_AMPLITUDE;
            return clamp01(Math.max(RING_WAVE_FLOOR, base + ripple));
        }
        // Emerge/retract: neighbours trail by up to a third of the row window,
        // so the crest runs around the ring instead of lifting as one plate.
        double shifted = (base - phase * RING_WAVE_LAG) / (1D - RING_WAVE_LAG);
        return clamp01(shifted);
    }

    /** Per-stem pose for one ring instance; see {@link #ringInstanceRise}. */
    public static EndRiftTentaclePose.TentaclePose ringPose(KaguneRingFrame frame,
                                                            int index, int count) {
        var upright=poseFor("EMERGE",1F,0L);
        double rise=ringInstanceRise(index,count,frame.stage(),frame.rise(),frame.strike());
        float eased=(float)(rise*rise*(3D-2D*rise));
        if (frame.stage()==KaguneRingFrame.EMERGE)
            return poseFor("EMERGE",(float)rise,0L);
        if (frame.stage()==KaguneRingFrame.RETRACT)
            return EndRiftTentaclePose.TentaclePose.lerp(upright,
                    poseFor("RETRACT",(float)(1D-rise),0L),eased);
        // TRAVEL: the distal joints carry a travelling curl so the tips ripple
        // around the circle while the stems themselves stay at contact height.
        double phase=count<=0?0D:(double)Math.floorMod(index,count)/count;
        float ripple=(float)(Math.sin((phase-frame.strike())*Math.PI*2D)*.16D);
        return new EndRiftTentaclePose.TentaclePose(upright.root(),upright.base(),upright.seg_01(),
                upright.seg_02(),upright.seg_03(),curl(upright.seg_04(),ripple),
                curl(upright.seg_05(),-ripple),upright.seg_06(),upright.tip(),
                upright.tip_claw_1(),upright.tip_claw_2(),upright.tip_claw_3(),upright.tip_claw_4(),
                upright.grab_socket(),upright.rigScale());
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0D, Math.min(1D, value)) : 0D;
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
        String firstState=normalize(firstAnimationId),secondState=normalize(secondAnimationId);
        // These states sample DIFFERENT windows of trow. Sharing its source
        // name must not retain the windup's already elapsed clock for a strike.
        if (!firstState.equals(secondState)
                && (firstState.startsWith("SMALL_") || secondState.startsWith("SMALL_"))) return false;
        String first = CLIP_BY_STATE.get(normalize(firstAnimationId));
        String second = CLIP_BY_STATE.get(normalize(secondAnimationId));
        return first != null && first.equals(second);
    }

    public static boolean continuousAttackTransition(String first,String second) {
        String from=normalize(first),to=normalize(second);
        return from.equals("SMALL_WINDUP") && to.equals("SMALL_STRIKE")
                || from.equals("SMALL_STRIKE") && to.equals("SMALL_RECOVERY");
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
