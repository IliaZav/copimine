package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelPart;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    static float clipLengthSeconds(String animationId) {
        Clip clip = CLIPS.get(canonical(animationId));
        return clip == null ? 0.0F : clip.lengthSeconds();
    }

    static boolean apply(ModelPart root, String animationId, float animationProgressTicks) {
        Clip clip = CLIPS.get(canonical(animationId));
        if (clip == null || root == null) {
            return false;
        }
        float seconds = Math.max(0.0F, animationProgressTicks / 20.0F);
        float time = clip.loop()
                ? (float) (seconds % Math.max(clip.lengthSeconds(), 0.001F))
                : Math.min(seconds, clip.lengthSeconds());
        for (Map.Entry<String, BoneTrack> entry : clip.bones().entrySet()) {
            ModelPart part = UserEndBossModelData.findBone(root, entry.getKey());
            if (part == null) {
                continue;
            }
            BoneTrack track = entry.getValue();
            Vector rotation = track.rotation().sample(time);
            if (rotation != null) {
                part.pitch += radians(rotation.x());
                part.yaw += radians(rotation.y());
                part.roll += radians(rotation.z());
            }
            Vector position = track.position().sample(time);
            if (position != null) {
                part.pivotX += position.x();
                part.pivotY -= position.y();
                part.pivotZ += position.z();
            }
            Vector scale = track.scale().sample(time);
            if (scale != null) {
                part.xScale *= scale.x();
                part.yScale *= scale.y();
                part.zScale *= scale.z();
            }
        }
        return true;
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
            JsonObject animation = animations.entrySet().iterator().next().getValue().getAsJsonObject();
            float length = animation.has("animation_length")
                    ? animation.get("animation_length").getAsFloat() : 0.0F;
            Map<String, BoneTrack> bones = new HashMap<>();
            JsonObject boneObject = animation.getAsJsonObject("bones");
            if (boneObject != null) {
                for (Map.Entry<String, JsonElement> boneEntry : boneObject.entrySet()) {
                    bones.put(boneEntry.getKey(), parseTrack(boneEntry.getValue().getAsJsonObject()));
                }
            }
            clips.put(canonicalId, new Clip(length, loop, Map.copyOf(bones)));
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Unable to load supplied End Rift animation: " + resource, error);
        }
    }

    private static BoneTrack parseTrack(JsonObject source) {
        return new BoneTrack(
                parseChannel(source.getAsJsonObject("rotation")),
                parseChannel(source.getAsJsonObject("position")),
                parseChannel(source.getAsJsonObject("scale")));
    }

    private static Channel parseChannel(JsonObject source) {
        if (source == null) {
            return Channel.EMPTY;
        }
        List<Keyframe> frames = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            try {
                float time = Float.parseFloat(entry.getKey());
                Vector vector = readVector(entry.getValue());
                if (vector != null) {
                    frames.add(new Keyframe(time, vector));
                }
            } catch (NumberFormatException ignored) {
                // Bedrock metadata keys are not keyframes; ignore them safely.
            }
        }
        frames.sort(Comparator.comparingDouble(Keyframe::time));
        return frames.isEmpty() ? Channel.EMPTY : new Channel(List.copyOf(frames));
    }

    private static Vector readVector(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            return vector(element.getAsJsonArray());
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        for (String key : new String[]{"vector", "post", "pre"}) {
            if (object.has(key)) {
                return readVector(object.get(key));
            }
        }
        return null;
    }

    private static Vector vector(JsonArray array) {
        if (array.size() != 3) {
            return null;
        }
        return new Vector(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static String canonical(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return "IDLE_BREATH";
        }
        return animationId.trim().toUpperCase(Locale.ROOT);
    }

    private static float radians(float degrees) {
        return degrees * (float) (Math.PI / 180.0D);
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
