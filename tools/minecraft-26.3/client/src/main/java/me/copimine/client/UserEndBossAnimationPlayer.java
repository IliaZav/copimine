package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Plays the animation clips supplied with the End Rift boss asset. */
final class UserEndBossAnimationPlayer {
    private static final String RESOURCE_PREFIX =
            "/assets/copimineclient/models/entity/end_rift_guardian/animations/";
    private static final Map<String, Clip> CLIPS = loadClips();

    private UserEndBossAnimationPlayer() {
    }

    static boolean hasClip(String animationId) {
        return CLIPS.containsKey(canonical(animationId));
    }

    /**
     * How long a switch into {@code toAnimation} should blend, in ticks.
     *
     * <p>A blend is only worth it where a snap would be seen: into a contact
     * move or a defeat the rig would otherwise jump. Looping locomotion
     * (idle/run) and the short hurt flinch blend faster so input never feels
     * laggy, and a switch into the same clip is not a switch at all.</p>
     */
    static float blendTicks(String fromAnimation, String toAnimation) {
        // Compare the clips that will actually play, not the wire ids: two
        // different spell names share one authored clip, and re-blending the
        // same clip against itself every beat would stutter the rig.
        String to = clipFor(toAnimation);
        if (to.equals(clipFor(fromAnimation))) return 0.0F;
        return switch (to) {
            case "DYING", "DEFEAT_COLLAPSE" -> 8.0F;
            case "MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM" -> 5.0F;
            case "HURT" -> 2.0F;
            case "COCOON_FALL" -> 4.0F;
            case "COCOON_LAND" -> 2.0F;
            case "IDLE_BREATH", "RUN" -> 6.0F;
            default -> 4.0F;
        };
    }

    /**
     * Linear blend between two sampled poses.
     *
     * <p>Bone sets may differ between clips — {@code hurt.json} only animates
     * the head — so a bone missing from one side falls back to the other
     * side's value rather than to zero: snapping an unanimated bone to the
     * rest pose is exactly the invisible-but-wrong pop this exists to avoid.</p>
     */
    static ChameleonGuardianGeometry.GuardianPose blend(
            ChameleonGuardianGeometry.GuardianPose from,
            ChameleonGuardianGeometry.GuardianPose to, float weight) {
        if (from == null || to == null) return to == null ? from : to;
        float w = Math.max(0.0F, Math.min(1.0F, Float.isFinite(weight) ? weight : 1.0F));
        if (w <= 0.0F) return from;
        if (w >= 1.0F) return to;
        Map<String, ChameleonGuardianGeometry.BoneDelta> merged = new HashMap<>();
        Set<String> bones = new HashSet<>();
        bones.addAll(from.deltas().keySet());
        bones.addAll(to.deltas().keySet());
        for (String bone : bones) {
            ChameleonGuardianGeometry.BoneDelta a = from.deltas().get(bone);
            ChameleonGuardianGeometry.BoneDelta b = to.deltas().get(bone);
            if (a == null) { merged.put(bone, b); continue; }
            if (b == null) { merged.put(bone, a); continue; }
            merged.put(bone, new ChameleonGuardianGeometry.BoneDelta(
                    lerp(a.translation(), b.translation(), w),
                    lerp(a.rotation(), b.rotation(), w),
                    lerp(a.scale(), b.scale(), w)));
        }
        return ChameleonGuardianGeometry.GuardianPose.of(merged);
    }

    private static ChameleonGuardianGeometry.Point lerp(ChameleonGuardianGeometry.Point a,
                                                        ChameleonGuardianGeometry.Point b, float w) {
        return new ChameleonGuardianGeometry.Point(
                a.x() + (b.x() - a.x()) * w,
                a.y() + (b.y() - a.y()) * w,
                a.z() + (b.z() - a.z()) * w);
    }

    static float clipLengthSeconds(String animationId) {
        Clip clip = CLIPS.get(canonical(animationId));
        return clip == null ? 0.0F : clip.lengthSeconds();
    }

    /**
     * Samples source-space deltas for the direct Chameleon renderer.  Values
     * intentionally remain in the supplied Bedrock coordinate system; the
     * geometry evaluator applies the source-to-Fabric basis exactly once at
     * the absolute-pivot transform boundary.
     */
    /**
     * The supplied asset ships seven clips, but the server names twenty-five
     * animations. A missing clip used to fall back to the identity pose, so
     * eighteen of them — every spell, every cast beat, the phase shift, the
     * final strike — rendered as a frozen body: the boss looked unanimated
     * exactly when it was doing something.
     *
     * <p>Falling back to the closest authored clip keeps the rig honest: a
     * spell cast plays the chest strike, a phase shift plays the ground slam,
     * a teleport plays the swipe. The actor never invents a pose the artist
     * did not draw, and no beat is silently null.</p>
     */
    private static String clipFor(String animationId) {
        String canonicalId = canonical(animationId);
        if (CLIPS.containsKey(canonicalId)) return canonicalId;
        return switch (canonicalId) {
            case "CHEST_STRIKE", "MARK_CONTROL", "SUMMON_CHANNEL", "CAST_CHARGE",
                 "CAST_RELEASE", "CAST_IMPACT", "RECOVERY", "SPELL_VOID_BLAST",
                 "SPELL_RIFT_PROJECTILE", "SPELL_RIFT_ARROWS", "SPELL_ARROW_SALVO",
                 "SPELL_VOID_MARK", "SPELL_SUMMON_SERVANTS", "SPELL_SUMMON",
                 "SPELL_RIFT_OBELISKS", "SPELL_ARENA_INFERNO" -> "CHEST_STRIKE";
            case "PHASE_TRANSITION", "FINAL_STRIKE" -> "GROUND_SLAM";
            case "TELEPORT_RIP" -> "MELEE_SWIPE";
            default -> "IDLE_BREATH";
        };
    }

    static ChameleonGuardianGeometry.GuardianPose sample(String animationId, float animationProgressTicks) {
        Clip clip = CLIPS.get(clipFor(animationId));
        if (clip == null) {
            return ChameleonGuardianGeometry.GuardianPose.identity();
        }
        float seconds = (float) me.copimine.protocol.BossAnimationPlayback.authoredTicks(
                canonical(animationId), animationProgressTicks) / 20.0F;
        float time = clip.loop()
                ? (float) (seconds % Math.max(clip.lengthSeconds(), 0.001F))
                : Math.min(seconds, clip.lengthSeconds());
        Map<String, ChameleonGuardianGeometry.BoneDelta> deltas = new HashMap<>();
        for (Map.Entry<String, BoneTrack> entry : clip.bones().entrySet()) {
            BoneTrack track = entry.getValue();
            Vector rotation = track.rotation().sample(time);
            Vector position = track.position().sample(time);
            Vector scale = track.scale().sample(time);
            if (rotation == null && position == null && scale == null) {
                continue;
            }
            deltas.put(entry.getKey(), new ChameleonGuardianGeometry.BoneDelta(
                    point(position, 0.0F, 0.0F, 0.0F),
                    point(rotation, 0.0F, 0.0F, 0.0F),
                    point(scale, 1.0F, 1.0F, 1.0F)));
        }
        return ChameleonGuardianGeometry.GuardianPose.of(deltas);
    }

    private static ChameleonGuardianGeometry.Point point(Vector value,
                                                          float fallbackX, float fallbackY, float fallbackZ) {
        return value == null
                ? new ChameleonGuardianGeometry.Point(fallbackX, fallbackY, fallbackZ)
                : new ChameleonGuardianGeometry.Point(value.x(), value.y(), value.z());
    }

    private static Map<String, Clip> loadClips() {
        Map<String, Clip> clips = new HashMap<>();
        load(clips, "idle.json", "IDLE_BREATH", true);
        load(clips, "running.json", "RUN", true);
        load(clips, "swipe.json", "MELEE_SWIPE", false);
        load(clips, "hurt.json", "HURT", false);
        load(clips, "dying.json", "DYING", false);
        load(clips, "udar_iz_grudi.json", "CHEST_STRIKE", false);
        load(clips, "udar_po_zemle.animation.json", "GROUND_SLAM", false);
        load(clips, "phase_shift.animation.json", "PHASE_TRANSITION", false);
        load(clips, "cocoon_fall.animation.json", "COCOON_FALL", false);
        load(clips, "cocoon_land.animation.json", "COCOON_LAND", false);
        return Map.copyOf(clips);
    }

    private static void load(Map<String, Clip> clips, String fileName,
                             String canonicalId, boolean loop) {
        String resource = RESOURCE_PREFIX + fileName;
        try (InputStream stream = UserEndBossAnimationPlayer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing supplied End Rift animation resource: " + resource);
            }
            JsonObject document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject animations = document.getAsJsonObject("animations");
            if (animations == null || animations.entrySet().size() != 1) {
                throw new IllegalStateException("Expected one animation in " + resource);
            }
            BedrockAssetValidator.AnimationManifest manifest = BedrockAssetValidator.validateAnimation(
                    document, resource, UserEndBossModelData.sourceBoneNames());
            Map.Entry<String, JsonElement> animationEntry = animations.entrySet().iterator().next();
            JsonObject animation = animationEntry.getValue().getAsJsonObject();
            float length = (float) manifest.lengthSeconds();
            Map<String, BoneTrack> bones = new HashMap<>();
            JsonObject boneObject = animation.getAsJsonObject("bones");
            if (boneObject != null) {
                for (Map.Entry<String, JsonElement> boneEntry : boneObject.entrySet()) {
                    bones.put(boneEntry.getKey(), parseTrack(boneEntry.getValue().getAsJsonObject(),
                            resource, "animations." + animationEntry.getKey()
                                    + ".bones." + boneEntry.getKey()));
                }
            }
            BedrockAssetValidator.requireBoneClosure(resource, canonicalId,
                    manifest.boneNames(), bones.keySet());
            clips.put(canonicalId, new Clip(length, loop, Map.copyOf(bones)));
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Unable to load supplied End Rift animation: " + resource, error);
        }
    }

    private static BoneTrack parseTrack(JsonObject source, String resource, String path) {
        return new BoneTrack(
                parseChannel(source.get("rotation"), resource, path + ".rotation"),
                parseChannel(source.get("position"), resource, path + ".position"),
                parseChannel(source.get("scale"), resource, path + ".scale"));
    }

    private static Channel parseChannel(JsonElement source, String resource, String path) {
        if (source == null || source.isJsonNull()) {
            return Channel.EMPTY;
        }
        if (source.isJsonArray()) {
            return new Channel(List.of(new Keyframe(0.0F, readVector(source, resource, path))));
        }
        if (!source.isJsonObject()) {
            fail(resource, path + " must be an object or vector");
        }
        JsonObject keyframes = source.getAsJsonObject();
        List<Keyframe> frames = new ArrayList<>();
        Set<Double> times = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : keyframes.entrySet()) {
            if ("lerp_mode".equals(entry.getKey())) {
                fail(resource, path + ".lerp_mode is unsupported until interpolation modes are implemented");
            }
            double time;
            try {
                time = Double.parseDouble(entry.getKey());
            } catch (NumberFormatException ignored) {
                fail(resource, path + "." + entry.getKey() + " is not a numeric keyframe time");
                return Channel.EMPTY;
            }
            if (!Double.isFinite(time) || time < 0.0D || !times.add(time)) {
                fail(resource, path + "." + entry.getKey() + " is an invalid or duplicate keyframe time");
            }
            frames.add(new Keyframe((float) time, readVector(entry.getValue(), resource,
                    path + "." + entry.getKey())));
        }
        if (frames.isEmpty()) {
            fail(resource, path + " contains no keyframes");
        }
        frames.sort(Comparator.comparingDouble(Keyframe::time));
        return new Channel(List.copyOf(frames));
    }

    private static Vector readVector(JsonElement element, String resource, String path) {
        if (element == null || element.isJsonNull()) {
            fail(resource, path + " must contain a vector");
        }
        if (element.isJsonArray()) {
            return vector(element.getAsJsonArray(), resource, path);
        }
        if (!element.isJsonObject()) {
            fail(resource, path + " must contain a vector array");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("pre") || object.has("post")) {
            fail(resource, path
                    + " uses pre/post keyframes, which are unsupported until interpolation is implemented");
        }
        if (object.size() != 1 || !object.has("vector")) {
            fail(resource + " " + path, "unsupported keyframe object fields: " + object.keySet());
        }
        return readVector(object.get("vector"), resource, path + ".vector");
    }

    private static Vector vector(JsonArray array, String resource, String path) {
        if (array.size() != 3) {
            fail(resource, path + " must contain three numbers");
        }
        float x = finite(array.get(0), resource, path + "[0]");
        float y = finite(array.get(1), resource, path + "[1]");
        float z = finite(array.get(2), resource, path + "[2]");
        return new Vector(x, y, z);
    }

    private static float finite(JsonElement element, String resource, String path) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !Float.isFinite(element.getAsFloat())) {
            fail(resource, path + " must be a finite number");
        }
        return element.getAsFloat();
    }

    private static void fail(String resource, String message) {
        throw new IllegalStateException("End Rift animation validation failed source="
                + resource + ": " + message);
    }

    private static String canonical(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return "IDLE_BREATH";
        }
        return animationId.trim().toUpperCase(Locale.ROOT);
    }

    private record Clip(float lengthSeconds, boolean loop, Map<String, BoneTrack> bones) {
    }

    private record BoneTrack(Channel rotation, Channel position, Channel scale) {
    }

    private record Keyframe(float time, Vector vector) {
    }

    private record Vector(float x, float y, float z) {
    }

    private static final class Channel {
        private static final Channel EMPTY = new Channel(List.of());
        private final List<Keyframe> frames;

        private Channel(List<Keyframe> frames) {
            this.frames = frames;
        }

        private Vector sample(float time) {
            if (frames.isEmpty()) {
                return null;
            }
            if (time <= frames.get(0).time()) {
                return frames.get(0).vector();
            }
            Keyframe last = frames.get(frames.size() - 1);
            if (time >= last.time()) {
                return last.vector();
            }
            for (int index = 1; index < frames.size(); index++) {
                Keyframe next = frames.get(index);
                if (time <= next.time()) {
                    Keyframe previous = frames.get(index - 1);
                    float span = next.time() - previous.time();
                    float amount = span <= 0.0F ? 1.0F : (time - previous.time()) / span;
                    return lerp(previous.vector(), next.vector(), amount);
                }
            }
            return last.vector();
        }

        private static Vector lerp(Vector first, Vector second, float amount) {
            return new Vector(
                    first.x() + (second.x() - first.x()) * amount,
                    first.y() + (second.y() - first.y()) * amount,
                    first.z() + (second.z() - first.z()) * amount);
        }
    }
}
