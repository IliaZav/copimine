package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles the supplied Chameleon/Bedrock guardian asset without translating
 * its absolute-pivot hierarchy through vanilla {@code ModelPart} pivots.
 *
 * <p>The source parser used by Chameleon mirrors source X, while Fabric model
 * coordinates point down along Y. Combining those conventions maps a source
 * point to {@code (-x, -y, z)}. Chameleon rotates each bone about its absolute
 * source pivot in Z, Y, X order; retaining that matrix stack is essential for
 * the nested arms and rotated horn cubes in the supplied boss.</p>
 */
final class ChameleonGuardianGeometry {
    private static final int SOURCE_TEXTURE_WIDTH = UserEndBossModelData.SOURCE_TEXTURE_WIDTH;
    private static final int SOURCE_TEXTURE_HEIGHT = UserEndBossModelData.SOURCE_TEXTURE_HEIGHT;
    private static final int TEXTURE_WIDTH = UserEndBossModelData.TEXTURE_WIDTH;
    private static final int TEXTURE_HEIGHT = UserEndBossModelData.TEXTURE_HEIGHT;
    private static final float PIXELS_PER_SOURCE_UV = (float) TEXTURE_WIDTH / SOURCE_TEXTURE_WIDTH;
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    private static final ChameleonGuardianGeometry INSTANCE = readResource();

    private final List<Bone> roots;
    private final Map<String, Bone> bonesByName;
    private final int cubeCount;
    private final List<Face> restFaces;

    private ChameleonGuardianGeometry(List<Bone> roots, Map<String, Bone> bonesByName, int cubeCount) {
        this.roots = List.copyOf(roots);
        this.bonesByName = Map.copyOf(bonesByName);
        this.cubeCount = cubeCount;
        this.restFaces = List.copyOf(faces(GuardianPose.identity()));
    }

    static ChameleonGuardianGeometry load() {
        return INSTANCE;
    }

    int boneCount() {
        return bonesByName.size();
    }

    int cubeCount() {
        return cubeCount;
    }

    int faceCount() {
        return restFaces.size();
    }

    List<Face> restFaces() {
        return restFaces;
    }

    List<Face> faces(GuardianPose pose) {
        GuardianPose effectivePose = pose == null ? GuardianPose.identity() : pose;
        List<Face> result = new ArrayList<>();
        for (Bone root : roots) {
            appendFaces(root, new Matrix4f(), effectivePose, result);
        }
        return List.copyOf(result);
    }

    Vertex transformedBonePivot(String name) {
        Bone bone = requireBone(name);
        Vector3f pivot = toTargetPoint(bone.pivot());
        Vector3f transformed = matrixForBone(bone, GuardianPose.identity()).transformPosition(pivot);
        return Vertex.of(transformed);
    }

    Vertex transformedCubePivot(String boneName, int cubeIndex) {
        Bone bone = requireBone(boneName);
        Cube cube = requireCube(bone, cubeIndex);
        Matrix4f transform = applyCubeBind(matrixForBone(bone, GuardianPose.identity()), cube);
        return Vertex.of(transform.transformPosition(toTargetPoint(cube.pivot())));
    }

    Face findFace(String boneName, int cubeIndex, String faceName) {
        for (Face face : restFaces) {
            if (face.boneName().equals(boneName)
                    && face.cubeIndex() == cubeIndex
                    && face.faceName().equals(faceName)) {
                return face;
            }
        }
        throw new IllegalArgumentException("Unknown source face " + boneName + "[" + cubeIndex + "]." + faceName);
    }

    private void appendFaces(Bone bone, Matrix4f parent, GuardianPose pose, List<Face> result) {
        Matrix4f boneTransform = applyBone(parent, bone, pose.deltaFor(bone.name()));
        for (Cube cube : bone.cubes()) {
            Matrix4f cubeTransform = applyCubeBind(boneTransform, cube);
            appendCubeFaces(bone.name(), cube, cubeTransform, result);
        }
        for (Bone child : bone.children()) {
            appendFaces(child, boneTransform, pose, result);
        }
    }

    private Matrix4f matrixForBone(Bone bone, GuardianPose pose) {
        List<Bone> lineage = new ArrayList<>();
        for (Bone current = bone; current != null; current = current.parentName() == null
                ? null : requireBone(current.parentName())) {
            lineage.add(0, current);
        }

        Matrix4f transform = new Matrix4f();
        for (Bone current : lineage) {
            transform = applyBone(transform, current, pose.deltaFor(current.name()));
        }
        return transform;
    }

    private static Matrix4f applyBone(Matrix4f parent, Bone bone, BoneDelta delta) {
        Point rotation = bone.rotation().add(delta.rotation());
        Vector3f translation = toTargetPoint(delta.translation());
        Vector3f pivot = toTargetPoint(bone.pivot());
        Point scale = delta.scale();

        return new Matrix4f(parent)
                .translate(translation.x, translation.y, translation.z)
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateZ(radians(-rotation.z()))
                .rotateY(radians(-rotation.y()))
                .rotateX(radians(rotation.x()))
                .scale(scale.x(), scale.y(), scale.z())
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static Matrix4f applyCubeBind(Matrix4f parent, Cube cube) {
        if (cube.rotation().isZero()) {
            return new Matrix4f(parent);
        }
        Vector3f pivot = toTargetPoint(cube.pivot());
        Point rotation = cube.rotation();
        return new Matrix4f(parent)
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateZ(radians(-rotation.z()))
                .rotateY(radians(-rotation.y()))
                .rotateX(radians(rotation.x()))
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static void appendCubeFaces(String boneName, Cube cube, Matrix4f transform, List<Face> result) {
        float minX = -cube.origin().x() - cube.size().x();
        float minY = cube.origin().y();
        float minZ = cube.origin().z();
        float maxX = -cube.origin().x();
        float maxY = cube.origin().y() + cube.size().y();
        float maxZ = cube.origin().z() + cube.size().z();

        appendFace(result, boneName, cube, "north", transform, cube.uv("north"),
                new RawVertex(maxX, minY, minZ), new RawVertex(minX, minY, minZ),
                new RawVertex(minX, maxY, minZ), new RawVertex(maxX, maxY, minZ),
                new Vector3f(0.0F, 0.0F, -1.0F), false);
        appendFace(result, boneName, cube, "east", transform, cube.uv("east"),
                new RawVertex(maxX, minY, maxZ), new RawVertex(maxX, minY, minZ),
                new RawVertex(maxX, maxY, minZ), new RawVertex(maxX, maxY, maxZ),
                new Vector3f(1.0F, 0.0F, 0.0F), false);
        appendFace(result, boneName, cube, "south", transform, cube.uv("south"),
                new RawVertex(minX, minY, maxZ), new RawVertex(maxX, minY, maxZ),
                new RawVertex(maxX, maxY, maxZ), new RawVertex(minX, maxY, maxZ),
                new Vector3f(0.0F, 0.0F, 1.0F), false);
        appendFace(result, boneName, cube, "west", transform, cube.uv("west"),
                new RawVertex(minX, minY, minZ), new RawVertex(minX, minY, maxZ),
                new RawVertex(minX, maxY, maxZ), new RawVertex(minX, maxY, minZ),
                new Vector3f(-1.0F, 0.0F, 0.0F), false);
        appendFace(result, boneName, cube, "up", transform, cube.uv("up"),
                new RawVertex(maxX, maxY, minZ), new RawVertex(minX, maxY, minZ),
                new RawVertex(minX, maxY, maxZ), new RawVertex(maxX, maxY, maxZ),
                new Vector3f(0.0F, 1.0F, 0.0F), false);
        appendFace(result, boneName, cube, "down", transform, cube.uv("down"),
                new RawVertex(minX, minY, minZ), new RawVertex(maxX, minY, minZ),
                new RawVertex(maxX, minY, maxZ), new RawVertex(minX, minY, maxZ),
                new Vector3f(0.0F, -1.0F, 0.0F), true);
    }

    private static void appendFace(List<Face> result, String boneName, Cube cube, String faceName,
                                   Matrix4f transform, Uv uv, RawVertex first, RawVertex second,
                                   RawVertex third, RawVertex fourth, Vector3f chameleonNormal,
                                   boolean downFace) {
        Objects.requireNonNull(uv, () -> "Supplied cube is missing its " + faceName + " UV face");
        float sx = uv.u() * PIXELS_PER_SOURCE_UV;
        float sy = uv.v() * PIXELS_PER_SOURCE_UV;
        float ex = (uv.u() + uv.width()) * PIXELS_PER_SOURCE_UV;
        float ey = (uv.v() + uv.height()) * PIXELS_PER_SOURCE_UV;

        Vector3f targetNormal = transform.transformDirection(
                new Vector3f(chameleonNormal.x, -chameleonNormal.y, chameleonNormal.z));
        if (targetNormal.lengthSquared() > 0.0F) {
            targetNormal.normalize();
        }

        List<Vertex> vertices;
        if (downFace) {
            vertices = List.of(
                    transformVertex(transform, first, ex, sy),
                    transformVertex(transform, second, sx, sy),
                    transformVertex(transform, third, sx, ey),
                    transformVertex(transform, fourth, ex, ey));
        } else {
            vertices = List.of(
                    transformVertex(transform, first, sx, ey),
                    transformVertex(transform, second, ex, ey),
                    transformVertex(transform, third, ex, sy),
                    transformVertex(transform, fourth, sx, sy));
        }
        result.add(new Face(boneName, cube.index(), faceName, vertices, Vertex.of(targetNormal)));
    }

    private static Vertex transformVertex(Matrix4f transform, RawVertex vertex, float u, float v) {
        Vector3f target = transform.transformPosition(new Vector3f(vertex.x(), -vertex.y(), vertex.z()));
        return new Vertex(target.x, target.y, target.z, u, v);
    }

    private Bone requireBone(String name) {
        Bone bone = bonesByName.get(name);
        if (bone == null) {
            throw new IllegalArgumentException("Unknown supplied guardian bone: " + name);
        }
        return bone;
    }

    private static Cube requireCube(Bone bone, int index) {
        if (index < 0 || index >= bone.cubes().size()) {
            throw new IllegalArgumentException("Unknown supplied guardian cube " + bone.name() + "[" + index + "]");
        }
        return bone.cubes().get(index);
    }

    private static ChameleonGuardianGeometry readResource() {
        try (InputStream stream = ChameleonGuardianGeometry.class.getResourceAsStream(UserEndBossModelData.RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing supplied End Rift geometry resource: " + UserEndBossModelData.RESOURCE);
            }
            JsonObject document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            BedrockAssetValidator.validateGeometry(document, UserEndBossModelData.RESOURCE);
            JsonArray geometries = document.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.size() != 1) {
                throw new IllegalStateException("Expected one supplied End Rift geometry definition");
            }
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description.get("texture_width").getAsInt() != SOURCE_TEXTURE_WIDTH
                    || description.get("texture_height").getAsInt() != SOURCE_TEXTURE_HEIGHT) {
                throw new IllegalStateException("Unexpected supplied End Rift UV grid");
            }
            return parseGeometry(geometry);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load supplied End Rift geometry", error);
        }
    }

    private static ChameleonGuardianGeometry parseGeometry(JsonObject geometry) {
        JsonArray boneElements = geometry.getAsJsonArray("bones");
        if (boneElements == null) {
            throw new IllegalStateException("Supplied End Rift geometry has no bones");
        }

        Map<String, MutableBone> draftsByName = new LinkedHashMap<>();
        int cubeCount = 0;
        for (JsonElement element : boneElements) {
            JsonObject data = element.getAsJsonObject();
            String name = data.get("name").getAsString();
            MutableBone prior = draftsByName.put(name, new MutableBone(
                    name,
                    optionalString(data, "parent"),
                    readPoint(data.getAsJsonArray("pivot"), Point.ZERO),
                    readPoint(data.getAsJsonArray("rotation"), Point.ZERO),
                    parseCubes(data.getAsJsonArray("cubes"))));
            if (prior != null) {
                throw new IllegalStateException("Duplicate supplied End Rift bone: " + name);
            }
            cubeCount += draftsByName.get(name).cubes.size();
        }

        List<MutableBone> roots = new ArrayList<>();
        for (MutableBone bone : draftsByName.values()) {
            if (bone.parentName == null) {
                roots.add(bone);
            } else {
                MutableBone parent = draftsByName.get(bone.parentName);
                if (parent == null) {
                    throw new IllegalStateException("Unknown supplied parent bone " + bone.parentName + " for " + bone.name);
                }
                parent.children.add(bone);
            }
        }

        Map<String, Bone> frozenByName = new LinkedHashMap<>();
        List<Bone> frozenRoots = new ArrayList<>();
        for (MutableBone root : roots) {
            frozenRoots.add(freeze(root, frozenByName));
        }
        if (frozenByName.size() != draftsByName.size()) {
            throw new IllegalStateException("Supplied End Rift geometry has a cyclic or detached bone hierarchy");
        }
        return new ChameleonGuardianGeometry(frozenRoots, frozenByName, cubeCount);
    }

    private static Bone freeze(MutableBone draft, Map<String, Bone> frozenByName) {
        List<Bone> children = new ArrayList<>();
        Bone bone = new Bone(draft.name, draft.parentName, draft.pivot, draft.rotation, draft.cubes, children);
        if (frozenByName.put(draft.name, bone) != null) {
            throw new IllegalStateException("Supplied End Rift geometry has a cyclic bone: " + draft.name);
        }
        for (MutableBone child : draft.children) {
            children.add(freeze(child, frozenByName));
        }
        return bone;
    }

    private static List<Cube> parseCubes(JsonArray cubes) {
        if (cubes == null) {
            return List.of();
        }
        List<Cube> result = new ArrayList<>();
        int index = 0;
        for (JsonElement element : cubes) {
            JsonObject data = element.getAsJsonObject();
            JsonObject uvData = data.getAsJsonObject("uv");
            Map<String, Uv> faces = new HashMap<>();
            for (String face : FACE_NAMES) {
                JsonObject faceData = uvData.getAsJsonObject(face);
                if (faceData == null) {
                    throw new IllegalStateException("Supplied guardian cube " + index + " is missing " + face + " UV data");
                }
                Point uv = readPoint(faceData.getAsJsonArray("uv"), Point.ZERO);
                Point uvSize = readPoint(faceData.getAsJsonArray("uv_size"), Point.ZERO);
                faces.put(face, new Uv(uv.x(), uv.y(), uvSize.x(), uvSize.y()));
            }
            Point origin = readPoint(data.getAsJsonArray("origin"), Point.ZERO);
            result.add(new Cube(index++, origin,
                    readPoint(data.getAsJsonArray("size"), Point.ZERO),
                    readPoint(data.getAsJsonArray("pivot"), origin),
                    readPoint(data.getAsJsonArray("rotation"), Point.ZERO),
                    Map.copyOf(faces)));
        }
        return List.copyOf(result);
    }

    private static String optionalString(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static Point readPoint(JsonArray values, Point fallback) {
        if (values == null) {
            return fallback;
        }
        if (values.size() < 2) {
            throw new IllegalStateException("Expected a source coordinate with at least two values");
        }
        return new Point(values.get(0).getAsFloat(), values.get(1).getAsFloat(),
                values.size() > 2 ? values.get(2).getAsFloat() : 0.0F);
    }

    private static Vector3f toTargetPoint(Point source) {
        return new Vector3f(-source.x(), -source.y(), source.z());
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    record Vertex(float x, float y, float z, float u, float v) {
        private static Vertex of(Vector3f vector) {
            return new Vertex(vector.x, vector.y, vector.z, Float.NaN, Float.NaN);
        }
    }

    record Face(String boneName, int cubeIndex, String faceName, List<Vertex> vertices, Vertex normal) {
        Face {
            vertices = List.copyOf(vertices);
        }
    }

    record GuardianPose(Map<String, BoneDelta> deltas) {
        private static final GuardianPose IDENTITY = new GuardianPose(Map.of());

        GuardianPose {
            deltas = Map.copyOf(deltas);
        }

        static GuardianPose identity() {
            return IDENTITY;
        }

        static GuardianPose of(Map<String, BoneDelta> deltas) {
            return deltas.isEmpty() ? identity() : new GuardianPose(deltas);
        }

        boolean hasBoneDelta(String boneName) {
            return deltas.containsKey(boneName);
        }

        BoneDelta deltaFor(String boneName) {
            return deltas.getOrDefault(boneName, BoneDelta.IDENTITY);
        }
    }

    record BoneDelta(Point translation, Point rotation, Point scale) {
        private static final BoneDelta IDENTITY = new BoneDelta(Point.ZERO, Point.ZERO, Point.ONE);

        BoneDelta {
            translation = translation == null ? Point.ZERO : translation;
            rotation = rotation == null ? Point.ZERO : rotation;
            scale = scale == null ? Point.ONE : scale;
        }
    }

    record Point(float x, float y, float z) {
        private static final Point ZERO = new Point(0.0F, 0.0F, 0.0F);
        private static final Point ONE = new Point(1.0F, 1.0F, 1.0F);

        private Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        private boolean isZero() {
            return x == 0.0F && y == 0.0F && z == 0.0F;
        }
    }

    private record Uv(float u, float v, float width, float height) {
    }

    private record RawVertex(float x, float y, float z) {
    }

    private record Cube(int index, Point origin, Point size, Point pivot, Point rotation, Map<String, Uv> faces) {
        private Uv uv(String face) {
            return faces.get(face);
        }
    }

    private record Bone(String name, String parentName, Point pivot, Point rotation,
                        List<Cube> cubes, List<Bone> children) {
        private Bone {
            cubes = List.copyOf(cubes);
        }
    }

    private static final class MutableBone {
        private final String name;
        private final String parentName;
        private final Point pivot;
        private final Point rotation;
        private final List<Cube> cubes;
        private final List<MutableBone> children = new ArrayList<>();

        private MutableBone(String name, String parentName, Point pivot, Point rotation, List<Cube> cubes) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
        }
    }
}
