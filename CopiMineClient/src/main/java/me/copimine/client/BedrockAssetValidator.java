package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/** Strict validation for the Bedrock geometry features consumed by the client importer. */
final class BedrockAssetValidator {
    private static final Set<String> DOCUMENT_FIELDS = Set.of("format_version", "minecraft:geometry");
    private static final Set<String> GEOMETRY_FIELDS = Set.of("description", "bones");
    private static final Set<String> DESCRIPTION_FIELDS = Set.of(
            "identifier", "texture_width", "texture_height",
            "visible_bounds_width", "visible_bounds_height", "visible_bounds_offset");
    private static final Set<String> BONE_FIELDS = Set.of("name", "parent", "pivot", "rotation", "cubes");
    private static final Set<String> CUBE_FIELDS = Set.of(
            "origin", "size", "pivot", "rotation", "uv", "mirror", "inflate");
    private static final Set<String> FACE_FIELDS = Set.of("uv", "uv_size");
    private static final Set<String> ANIMATION_DOCUMENT_FIELDS = Set.of("format_version", "animations");
    private static final Set<String> ANIMATION_FIELDS = Set.of("animation_length", "loop", "bones");
    private static final Set<String> ANIMATION_TRACK_FIELDS = Set.of("rotation", "position", "scale");
    private static final double MAX_ROTATION_DEGREES = 360.0D;

    private BedrockAssetValidator() {
    }

    static GeometryManifest validateGeometry(JsonObject document, String source) {
        if (document == null) {
            fail(source, "document is null");
        }
        rejectUnknown(document, DOCUMENT_FIELDS, source, "document");
        string(document, "format_version", source, "document");
        JsonArray geometries = array(document, "minecraft:geometry", source, "document");
        if (geometries.size() != 1) {
            fail(source, "expected exactly one geometry definition, got " + geometries.size());
        }
        JsonObject geometry = object(geometries.get(0), source, "minecraft:geometry[0]");
        rejectUnknown(geometry, GEOMETRY_FIELDS, source, "minecraft:geometry[0]");
        JsonObject description = object(geometry.get("description"), source,
                "minecraft:geometry[0].description");
        rejectUnknown(description, DESCRIPTION_FIELDS, source, "minecraft:geometry[0].description");
        String identifier = string(description, "identifier", source,
                "minecraft:geometry[0].description");
        if (identifier.isBlank()) {
            fail(source, "minecraft:geometry[0].description.identifier is blank");
        }
        int textureWidth = integer(description, "texture_width", source,
                "minecraft:geometry[0].description");
        int textureHeight = integer(description, "texture_height", source,
                "minecraft:geometry[0].description");
        if (textureWidth <= 0 || textureHeight <= 0) {
            fail(source, "texture dimensions must be positive: " + textureWidth + "x" + textureHeight);
        }
        if (description.has("visible_bounds_width")) {
            positiveFinite(description.get("visible_bounds_width"), source,
                    "minecraft:geometry[0].description.visible_bounds_width");
        }
        if (description.has("visible_bounds_height")) {
            positiveFinite(description.get("visible_bounds_height"), source,
                    "minecraft:geometry[0].description.visible_bounds_height");
        }
        if (description.has("visible_bounds_offset")) {
            vector(description, "visible_bounds_offset", source,
                    "minecraft:geometry[0].description");
        }

        JsonArray bonesArray = array(geometry, "bones", source, "minecraft:geometry[0]");
        if (bonesArray.isEmpty()) {
            fail(source, "minecraft:geometry[0].bones is empty");
        }
        Map<String, String> parents = new HashMap<>();
        Set<String> boneNames = new LinkedHashSet<>();
        for (int index = 0; index < bonesArray.size(); index++) {
            String path = "minecraft:geometry[0].bones[" + index + "]";
            JsonObject bone = object(bonesArray.get(index), source, path);
            rejectUnknown(bone, BONE_FIELDS, source, path);
            String name = string(bone, "name", source, path);
            if (name.isBlank() || !boneNames.add(name)) {
                fail(source, path + ".name is blank or duplicated: " + name);
            }
            String parent = bone.has("parent") ? string(bone, "parent", source, path) : "";
            parents.put(name, parent);
            vector(bone, "pivot", source, path);
            if (bone.has("rotation")) {
                validateRotation(vector(bone, "rotation", source, path), source, path + ".rotation");
            }
            if (bone.has("cubes")) {
                JsonArray cubes = array(bone, "cubes", source, path);
                for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
                    validateCube(object(cubes.get(cubeIndex), source,
                            path + ".cubes[" + cubeIndex + "]"), source,
                            path + ".cubes[" + cubeIndex + "]", textureWidth, textureHeight);
                }
            }
        }
        for (Map.Entry<String, String> entry : parents.entrySet()) {
            String parent = entry.getValue();
            if (!parent.isBlank() && !boneNames.contains(parent)) {
                fail(source, "missing parent for bone " + entry.getKey() + ": " + parent);
            }
            detectCycle(entry.getKey(), parents, source);
        }
        return new GeometryManifest(identifier, textureWidth, textureHeight, Set.copyOf(boneNames));
    }

    static void validateTexture(InputStream stream, String source,
                                int expectedWidth, int expectedHeight) {
        if (stream == null) {
            fail(source, "texture stream is missing");
        }
        if (expectedWidth <= 0 || expectedHeight <= 0) {
            fail(source, "expected texture dimensions must be positive");
        }

        final BufferedImage image;
        try {
            image = ImageIO.read(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Bedrock asset validation failed source=" + source
                    + ": texture could not be decoded", error);
        }
        if (image == null) {
            fail(source, "texture could not be decoded as an image");
        }
        if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
            fail(source, "texture dimensions " + image.getWidth() + "x" + image.getHeight()
                    + " do not match expected " + expectedWidth + "x" + expectedHeight);
        }
    }

    static void requireBoneClosure(String source, String animationId,
                                    Set<String> modelBones, Set<String> animationBones) {
        Set<String> unresolved = new LinkedHashSet<>(animationBones);
        unresolved.removeAll(modelBones);
        if (!unresolved.isEmpty()) {
            fail(source, "animation=" + animationId + " unresolved bones=" + unresolved
                    + " model_bones=" + modelBones);
        }
    }

    static AnimationManifest validateAnimation(JsonObject document, String source,
                                               Set<String> modelBones) {
        if (document == null) {
            fail(source, "document is null");
        }
        rejectUnknown(document, ANIMATION_DOCUMENT_FIELDS, source, "document");
        string(document, "format_version", source, "document");
        JsonObject animations = object(document.get("animations"), source, "animations");
        if (animations.entrySet().size() != 1) {
            fail(source, "animations must contain exactly one definition");
        }
        Map.Entry<String, JsonElement> animationEntry = animations.entrySet().iterator().next();
        String animationId = animationEntry.getKey();
        if (animationId == null || animationId.isBlank()) {
            fail(source, "animations contains a blank animation id");
        }
        String animationPath = "animations." + animationId;
        JsonObject animation = object(animationEntry.getValue(), source, animationPath);
        rejectUnknown(animation, ANIMATION_FIELDS, source, animationPath);
        double length = finiteNumber(animation.get("animation_length"), source,
                animationPath + ".animation_length");
        if (length <= 0.0D) {
            fail(source, animationPath + ".animation_length must be positive");
        }
        if (animation.has("loop")) {
            JsonElement loop = animation.get("loop");
            if (loop == null || !loop.isJsonPrimitive()
                    || !loop.getAsJsonPrimitive().isBoolean()) {
                fail(source, animationPath + ".loop must be boolean");
            }
        }
        JsonObject bones = object(animation.get("bones"), source, animationPath + ".bones");
        Set<String> animationBones = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
            String boneName = boneEntry.getKey();
            if (boneName == null || boneName.isBlank() || !animationBones.add(boneName)) {
                fail(source, animationPath + ".bones contains a blank or duplicate bone id");
            }
            String bonePath = animationPath + ".bones." + boneName;
            JsonObject track = object(boneEntry.getValue(), source, bonePath);
            rejectUnknown(track, ANIMATION_TRACK_FIELDS, source, bonePath);
            for (String channel : List.of("rotation", "position", "scale")) {
                if (track.has(channel)) {
                    validateAnimationChannel(track.get(channel), source, bonePath + "." + channel);
                }
            }
        }
        requireBoneClosure(source, animationId,
                modelBones == null ? Set.of() : modelBones, animationBones);
        return new AnimationManifest(animationId, length, Set.copyOf(animationBones));
    }

    private static void validateAnimationChannel(JsonElement channel, String source, String path) {
        if (channel == null || channel.isJsonNull()) {
            fail(source, path + " must not be null");
        }
        if (channel.isJsonArray()) {
            vector3(channel, source, path);
            return;
        }
        JsonObject keyframes = object(channel, source, path);
        boolean hasKeyframe = false;
        Set<Double> times = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : keyframes.entrySet()) {
            if ("lerp_mode".equals(entry.getKey())) {
                fail(source, path + ".lerp_mode is unsupported until interpolation modes are implemented");
            }
            double time;
            try {
                time = Double.parseDouble(entry.getKey());
            } catch (NumberFormatException error) {
                fail(source, path + "." + entry.getKey() + " is not a numeric keyframe time");
                return;
            }
            if (!Double.isFinite(time) || time < 0.0D || !times.add(time)) {
                fail(source, path + "." + entry.getKey() + " is an invalid or duplicate keyframe time");
            }
            validateAnimationKeyframe(entry.getValue(), source, path + "." + entry.getKey());
            hasKeyframe = true;
        }
        if (!hasKeyframe) {
            fail(source, path + " contains no keyframes");
        }
    }

    private static void validateAnimationKeyframe(JsonElement value, String source, String path) {
        if (value == null || value.isJsonNull()) {
            fail(source, path + " must contain a vector");
        }
        if (value.isJsonArray()) {
            vector3(value, source, path);
            return;
        }
        JsonObject object = object(value, source, path);
        if (object.has("pre") || object.has("post")) {
            fail(source, path + " uses pre/post keyframes, which are unsupported until interpolation is implemented");
        }
        rejectUnknown(object, Set.of("vector"), source, path);
        vector3(object.get("vector"), source, path + ".vector");
    }

    private static void vector3(JsonElement element, String source, String path) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            fail(source, path + " must contain three numbers");
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            finite(array.get(index), source, path + "[" + index + "]");
        }
    }

    private static double finiteNumber(JsonElement element, String source, String path) {
        finite(element, source, path);
        return element.getAsDouble();
    }

    private static void validateCube(JsonObject cube, String source, String path,
                                     int textureWidth, int textureHeight) {
        rejectUnknown(cube, CUBE_FIELDS, source, path);
        vector(cube, "origin", source, path);
        JsonArray size = vector(cube, "size", source, path);
        for (int index = 0; index < size.size(); index++) {
            if (size.get(index).getAsFloat() <= 0.0F) {
                fail(source, path + ".size[" + index + "] must be positive; zero/negative size is unsupported");
            }
        }
        if (cube.has("pivot")) {
            vector(cube, "pivot", source, path);
        }
        if (cube.has("rotation")) {
            validateRotation(vector(cube, "rotation", source, path), source, path + ".rotation");
        }
        if (cube.has("mirror")) {
            fail(source, path + ".mirror is unsupported; add an explicit winding/mirror implementation");
        }
        if (cube.has("inflate")) {
            fail(source, path + ".inflate is unsupported; add an explicit inflate implementation");
        }
        JsonObject uv = object(cube.get("uv"), source, path + ".uv");
        rejectUnknown(uv, Set.of("north", "south", "east", "west", "up", "down"),
                source, path + ".uv");
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            String facePath = path + ".uv." + face;
            JsonObject faceObject = object(uv.get(face), source, facePath);
            rejectUnknown(faceObject, FACE_FIELDS, source, facePath);
            JsonArray coordinates = vector2(faceObject, "uv", source, facePath);
            JsonArray uvSize = vector2(faceObject, "uv_size", source, facePath);
            validateUvBounds(coordinates, uvSize, textureWidth, textureHeight, source, facePath);
        }
    }

    private static void validateRotation(JsonArray rotation, String source, String path) {
        for (int index = 0; index < rotation.size(); index++) {
            double degrees = rotation.get(index).getAsDouble();
            if (Math.abs(degrees) > MAX_ROTATION_DEGREES) {
                fail(source, path + " exceeds supported range +/-" + MAX_ROTATION_DEGREES
                        + " degrees at index " + index + ": " + degrees);
            }
        }
    }

    private static void validateUvBounds(JsonArray coordinates, JsonArray size,
                                         int textureWidth, int textureHeight,
                                         String source, String path) {
        double u = coordinates.get(0).getAsDouble();
        double v = coordinates.get(1).getAsDouble();
        double uEnd = u + size.get(0).getAsDouble();
        double vEnd = v + size.get(1).getAsDouble();
        if (Math.abs(size.get(0).getAsDouble()) <= 0.0D
                || Math.abs(size.get(1).getAsDouble()) <= 0.0D) {
            fail(source, path + ".uv_size must not contain zero-length sides");
        }
        if (Math.min(u, uEnd) < 0.0D || Math.max(u, uEnd) > textureWidth
                || Math.min(v, vEnd) < 0.0D || Math.max(v, vEnd) > textureHeight) {
            fail(source, path + ".uv/uv_size lies outside texture grid "
                    + textureWidth + "x" + textureHeight + ": [" + u + "," + v + "] -> ["
                    + uEnd + "," + vEnd + "]");
        }
    }

    private static void detectCycle(String start, Map<String, String> parents, String source) {
        Set<String> seen = new HashSet<>();
        String current = start;
        while (!current.isBlank()) {
            if (!seen.add(current)) {
                fail(source, "bone parent cycle at " + current + ": " + seen);
            }
            current = parents.getOrDefault(current, "");
        }
    }

    private static JsonObject object(JsonElement element, String source, String path) {
        if (element == null || !element.isJsonObject()) {
            fail(source, path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key, String source, String path) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            fail(source, path + "." + key + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static JsonArray vector(JsonObject object, String key, String source, String path) {
        JsonArray value = array(object, key, source, path);
        if (value.size() != 3) {
            fail(source, path + "." + key + " must contain three numbers");
        }
        for (int index = 0; index < value.size(); index++) {
            finite(value.get(index), source, path + "." + key + "[" + index + "]");
        }
        return value;
    }

    private static JsonArray vector2(JsonObject object, String key, String source, String path) {
        JsonArray value = array(object, key, source, path);
        if (value.size() != 2) {
            fail(source, path + "." + key + " must contain two numbers");
        }
        for (int index = 0; index < value.size(); index++) {
            finite(value.get(index), source, path + "." + key + "[" + index + "]");
        }
        return value;
    }

    private static void positiveFinite(JsonElement element, String source, String path) {
        finite(element, source, path);
        if (element.getAsDouble() <= 0.0D) {
            fail(source, path + " must be positive");
        }
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed,
                                      String source, String path) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                fail(source, path + "." + key + " is unsupported");
            }
        }
    }

    private static void finite(JsonElement element, String source, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                || !Float.isFinite(element.getAsFloat())) {
            fail(source, path + " must be a finite number");
        }
    }

    private static String string(JsonObject object, String key, String source, String path) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            fail(source, path + "." + key + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key, String source, String path) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            fail(source, path + "." + key + " must be an integer");
        }
        int result = value.getAsInt();
        if (value.getAsDouble() != result) {
            fail(source, path + "." + key + " must be an integer");
        }
        return result;
    }

    private static void fail(String source, String message) {
        throw new IllegalStateException("Bedrock asset validation failed source=" + source + ": " + message);
    }

    record GeometryManifest(String identifier, int textureWidth, int textureHeight,
                            Set<String> boneNames) {
    }

    record AnimationManifest(String identifier, double lengthSeconds, Set<String> boneNames) {
    }
}
