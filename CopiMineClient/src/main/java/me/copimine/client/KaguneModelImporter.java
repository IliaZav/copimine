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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads the deterministic runtime file generated from the supplied Kagune .bbmodel. */
public final class KaguneModelImporter {
    public static final String RESOURCE =
            "/assets/copimineclient/geometry/end_rift_tentacle.json";

    private KaguneModelImporter() {
    }

    public static ImportedModel load() {
        InputStream stream = KaguneModelImporter.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing imported Kagune model resource " + RESOURCE);
        }
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Unable to load imported Kagune model " + RESOURCE, error);
        }
    }

    public static ImportedModel parse(JsonObject root) {
        Objects.requireNonNull(root, "root");
        if (!"copimine:kagune-import-v1".equals(string(root, "format"))) {
            throw new IllegalArgumentException("unsupported Kagune import format");
        }
        JsonObject texture = object(root, "texture");
        int textureWidth = integer(texture, "width");
        int textureHeight = integer(texture, "height");
        int textureUvWidth = arrayInt(texture, "uv_size", 0);
        int textureUvHeight = arrayInt(texture, "uv_size", 1);

        List<Bone> bones = new ArrayList<>();
        Map<String, Bone> bonesByName = new LinkedHashMap<>();
        for (JsonElement value : array(root, "groups")) {
            JsonObject boneJson = value.getAsJsonObject();
            String parentName = boneJson.has("parent") && !boneJson.get("parent").isJsonNull()
                    ? boneJson.get("parent").getAsString() : null;
            Bone bone = new Bone(string(boneJson, "uuid"), string(boneJson, "name"),
                    parentName, vector(boneJson, "origin"), vector(boneJson, "rotation"));
            if (bonesByName.putIfAbsent(bone.name(), bone) != null) {
                throw new IllegalArgumentException("duplicate Kagune bone " + bone.name());
            }
            bones.add(bone);
        }
        for (Bone bone : bones) {
            if (bone.parentName() != null && !bonesByName.containsKey(bone.parentName())) {
                throw new IllegalArgumentException("unknown parent bone " + bone.parentName());
            }
        }

        List<Cuboid> cuboids = new ArrayList<>();
        for (JsonElement value : array(root, "elements")) {
            JsonObject element = value.getAsJsonObject();
            String boneName = string(element, "bone");
            if (!bonesByName.containsKey(boneName)) {
                throw new IllegalArgumentException("unknown Kagune cuboid bone " + boneName);
            }
            JsonObject faces = object(element, "faces");
            Map<String, UvRect> uvByFace = new LinkedHashMap<>();
            for (String faceName : List.of("north", "east", "south", "west", "up", "down")) {
                JsonObject face = faces.getAsJsonObject(faceName);
                JsonArray uv = face.getAsJsonArray("uv");
                if (uv == null || uv.size() != 4) {
                    throw new IllegalArgumentException("invalid UV for " + faceName);
                }
                uvByFace.put(faceName, new UvRect(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        uv.get(2).getAsFloat(), uv.get(3).getAsFloat(),
                        face.has("rotation") ? face.get("rotation").getAsInt() : 0));
            }
            cuboids.add(new Cuboid(string(element, "uuid"), string(element, "name"), boneName,
                    vector(element, "from"), vector(element, "to"), vector(element, "origin"),
                    vector(element, "rotation"), Collections.unmodifiableMap(uvByFace)));
        }

        Map<String, Animation> animations = new LinkedHashMap<>();
        JsonObject clips = object(root, "animations");
        for (Map.Entry<String, JsonElement> clipEntry : clips.entrySet()) {
            JsonObject clipJson = clipEntry.getValue().getAsJsonObject();
            Map<String, Map<String, List<Keyframe>>> tracks = new LinkedHashMap<>();
            JsonObject tracksJson = object(clipJson, "tracks");
            for (Map.Entry<String, JsonElement> trackEntry : tracksJson.entrySet()) {
                Map<String, List<Keyframe>> channels = new LinkedHashMap<>();
                JsonObject channelsJson = trackEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> channelEntry : channelsJson.entrySet()) {
                    List<Keyframe> keyframes = new ArrayList<>();
                    for (JsonElement frameValue : channelEntry.getValue().getAsJsonArray()) {
                        JsonObject frame = frameValue.getAsJsonObject();
                        keyframes.add(new Keyframe(number(frame, "time"),
                                vector(frame, "value"), string(frame, "interpolation")));
                    }
                    keyframes.sort((left, right) -> Double.compare(left.timeSeconds(), right.timeSeconds()));
                    channels.put(channelEntry.getKey(), List.copyOf(keyframes));
                }
                tracks.put(trackEntry.getKey(), Collections.unmodifiableMap(channels));
            }
            animations.put(clipEntry.getKey(), new Animation(clipEntry.getKey(),
                    number(clipJson, "length"), string(clipJson, "loop"),
                    Collections.unmodifiableMap(tracks)));
        }

        Bounds bounds = bounds(cuboids);
        return new ImportedModel(textureWidth, textureHeight, textureUvWidth, textureUvHeight,
                List.copyOf(bones), Collections.unmodifiableMap(bonesByName), List.copyOf(cuboids),
                Collections.unmodifiableMap(animations), bounds);
    }

    private static Bounds bounds(List<Cuboid> cuboids) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Cuboid cuboid : cuboids) {
            minX = Math.min(minX, cuboid.from().x());
            minY = Math.min(minY, cuboid.from().y());
            minZ = Math.min(minZ, cuboid.from().z());
            maxX = Math.max(maxX, cuboid.to().x());
            maxY = Math.max(maxY, cuboid.to().y());
            maxZ = Math.max(maxZ, cuboid.to().z());
        }
        if (cuboids.isEmpty()) {
            return new Bounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static Vec3 sample(Animation animation, String boneName, String channel, float progress) {
        if (animation == null || boneName == null || channel == null) {
            return Vec3.ZERO;
        }
        Map<String, List<Keyframe>> channels = animation.tracks().get(boneName);
        List<Keyframe> frames = channels == null ? null : channels.get(channel);
        if (frames == null || frames.isEmpty()) {
            return Vec3.ZERO;
        }
        double safeProgress = Float.isFinite(progress) ? Math.max(0.0D, Math.min(1.0D, progress)) : 0.0D;
        double time = safeProgress * animation.lengthSeconds();
        if (time <= frames.getFirst().timeSeconds()) {
            return frames.getFirst().value();
        }
        for (int index = 1; index < frames.size(); index++) {
            Keyframe after = frames.get(index);
            if (time <= after.timeSeconds()) {
                Keyframe before = frames.get(index - 1);
                double span = after.timeSeconds() - before.timeSeconds();
                double t = span <= 0.0D ? 1.0D : (time - before.timeSeconds()) / span;
                if ("step".equalsIgnoreCase(before.interpolation())) {
                    return before.value();
                }
                // Keep the authored linear timing on grab, throw, hurt and
                // death clips. The four-second idle is a loop with a linear
                // 8.5-degree apex at 2s; smooth that one cycle through the
                // existing neighboring keys so the rig does not snap back
                // when its idle turn changes direction.
                boolean smoothIdleCycle = "idle".equalsIgnoreCase(animation.name());
                if (!smoothIdleCycle
                        && !"catmullrom".equalsIgnoreCase(before.interpolation())
                        && !"catmull_rom".equalsIgnoreCase(before.interpolation())) {
                    return Vec3.lerp(before.value(), after.value(), (float) t);
                }
                Keyframe previous;
                double previousTime;
                if (index >= 2) {
                    previous = frames.get(index - 2);
                    previousTime = previous.timeSeconds();
                } else if (isLooping(animation) && frames.size() > 2) {
                    previous = frames.get(frames.size() - 2);
                    previousTime = previous.timeSeconds() - animation.lengthSeconds();
                } else {
                    previous = new Keyframe(before.timeSeconds() - span,
                            extrapolate(before.value(), after.value(), -1.0F), "linear");
                    previousTime = previous.timeSeconds();
                }

                Keyframe next;
                double nextTime;
                if (index + 1 < frames.size()) {
                    next = frames.get(index + 1);
                    nextTime = next.timeSeconds();
                } else if (isLooping(animation) && frames.size() > 2) {
                    next = frames.get(1);
                    nextTime = next.timeSeconds() + animation.lengthSeconds();
                } else {
                    next = new Keyframe(after.timeSeconds() + span,
                            extrapolate(before.value(), after.value(), 2.0F), "linear");
                    nextTime = next.timeSeconds();
                }
                return cubic(previous.value(), before.value(), after.value(), next.value(), t,
                        before.timeSeconds() - previousTime,
                        nextTime - after.timeSeconds(), span);
            }
        }
        return frames.getLast().value();
    }

    private static boolean isLooping(Animation animation) {
        return animation != null && ("loop".equalsIgnoreCase(animation.loop())
                || "idle".equalsIgnoreCase(animation.name()));
    }

    private static Vec3 extrapolate(Vec3 from, Vec3 to, float amount) {
        return new Vec3(from.x() + (to.x() - from.x()) * amount,
                from.y() + (to.y() - from.y()) * amount,
                from.z() + (to.z() - from.z()) * amount);
    }

    private static Vec3 cubic(Vec3 previous, Vec3 from, Vec3 to, Vec3 next,
                              double amount, double previousSpan,
                              double nextSpan, double segmentSpan) {
        float x = cubic(previous.x(), from.x(), to.x(), next.x(), amount,
                previousSpan, nextSpan, segmentSpan);
        float y = cubic(previous.y(), from.y(), to.y(), next.y(), amount,
                previousSpan, nextSpan, segmentSpan);
        float z = cubic(previous.z(), from.z(), to.z(), next.z(), amount,
                previousSpan, nextSpan, segmentSpan);
        return new Vec3(x, y, z);
    }

    private static float cubic(float previous, float from, float to, float next,
                               double amount, double previousSpan,
                               double nextSpan, double segmentSpan) {
        double safePreviousSpan = previousSpan > 0.0D ? previousSpan : segmentSpan;
        double safeNextSpan = nextSpan > 0.0D ? nextSpan : segmentSpan;
        double firstTangent = (to - previous) * segmentSpan
                / (safePreviousSpan + segmentSpan);
        double secondTangent = (next - from) * segmentSpan
                / (segmentSpan + safeNextSpan);
        double t2 = amount * amount;
        double t3 = t2 * amount;
        double h00 = 2.0D * t3 - 3.0D * t2 + 1.0D;
        double h10 = t3 - 2.0D * t2 + amount;
        double h01 = -2.0D * t3 + 3.0D * t2;
        double h11 = t3 - t2;
        return (float) (h00 * from + h10 * firstTangent
                + h01 * to + h11 * secondTangent);
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("missing array " + key);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("missing object " + key);
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        return object.get(key).getAsInt();
    }

    private static double number(JsonObject object, String key) {
        return object.get(key).getAsDouble();
    }

    private static int arrayInt(JsonObject object, String key, int index) {
        return object.getAsJsonArray(key).get(index).getAsInt();
    }

    private static Vec3 vector(JsonObject object, String key) {
        return vector(object.getAsJsonArray(key));
    }

    private static Vec3 vector(JsonArray array) {
        if (array == null || array.size() != 3) {
            throw new IllegalArgumentException("expected three vector coordinates");
        }
        return new Vec3(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    public record Vec3(float x, float y, float z) {
        public static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);

        public static Vec3 lerp(Vec3 from, Vec3 to, float t) {
            return new Vec3(from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t);
        }

        public Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }
    }

    public record Bone(String uuid, String name, String parentName, Vec3 origin, Vec3 rotation) {
    }

    public record UvRect(float u0, float v0, float u1, float v1, int rotation) {
    }

    public record Cuboid(String uuid, String name, String boneName, Vec3 from, Vec3 to,
                         Vec3 origin, Vec3 rotation, Map<String, UvRect> faces) {
    }

    public record Keyframe(double timeSeconds, Vec3 value, String interpolation) {
    }

    public record Animation(String name, double lengthSeconds, String loop,
                            Map<String, Map<String, List<Keyframe>>> tracks) {
    }

    public record Bounds(float minX, float minY, float minZ,
                         float maxX, float maxY, float maxZ) {
        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }

        public float depth() {
            return maxZ - minZ;
        }
    }

    public record ImportedModel(int textureWidth, int textureHeight,
                                int textureUvWidth, int textureUvHeight,
                                List<Bone> bones, Map<String, Bone> bonesByName,
                                List<Cuboid> elements, Map<String, Animation> animations,
                                Bounds bounds) {
        public Bone bone(String name) {
            return bonesByName.get(name);
        }

        public Animation animation(String name) {
            return animations.get(name);
        }

        public Vec3 sample(String animation, String bone, String channel, float progress) {
            return KaguneModelImporter.sample(this.animation(animation), bone, channel, progress);
        }
    }
}
