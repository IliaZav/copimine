package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.copimine.client.mixin.ModelPartAccessor;
import me.copimine.client.mixin.ModelPartCuboidAccessor;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports the artist supplied Bedrock geometry into the Fabric model tree.
 *
 * <p>The server still binds the boss by UUID; this class only turns the
 * checked-in geometry resource into the client-side model.  The source asset
 * declares a 16x16 Bedrock UV grid while its PNG is 128x128, so UVs are
 * expanded by eight when the vanilla model builder is populated.</p>
 */
final class UserEndBossModelData {
    static final String RESOURCE = "/assets/copimineclient/models/entity/end_rift_guardian/geometry.json";
    static final int SOURCE_TEXTURE_WIDTH = 16;
    static final int SOURCE_TEXTURE_HEIGHT = 16;
    static final int TEXTURE_WIDTH = 128;
    static final int TEXTURE_HEIGHT = 128;
    private static final float UV_SCALE = TEXTURE_WIDTH / (float) SOURCE_TEXTURE_WIDTH;

    private UserEndBossModelData() {
    }

    static TexturedModelData create() {
        JsonObject geometry = readGeometry();
        JsonObject description = geometry.getAsJsonObject("description");
        int sourceTextureWidth = description.get("texture_width").getAsInt();
        int sourceTextureHeight = description.get("texture_height").getAsInt();
        if (sourceTextureWidth != SOURCE_TEXTURE_WIDTH || sourceTextureHeight != SOURCE_TEXTURE_HEIGHT) {
            throw new IllegalStateException("Unexpected supplied End Rift texture grid: "
                    + sourceTextureWidth + "x" + sourceTextureHeight);
        }

        List<Bone> bones = parseBones(geometry.getAsJsonArray("bones"));
        Map<String, Bone> byName = new HashMap<>();
        for (Bone bone : bones) {
            if (byName.put(bone.name(), bone) != null) {
                throw new IllegalStateException("Duplicate supplied End Rift bone: " + bone.name());
            }
        }

        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("hat", ModelPartBuilder.create(), ModelTransform.pivot(
                0.0F, (float) BedrockCoordinateTransform.pointTranslationY(), 0.0F));
        ModelPartData body = root.addChild("body", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        Map<String, ModelPartData> built = new HashMap<>();
        Map<String, BedrockCoordinateTransform.Pose> worldPoses = new HashMap<>();
        for (Bone bone : bones) {
            if (bone.parent().isBlank()) {
                ModelPartData parent = "body".equals(bone.name()) ? body : root;
                String modelName = rootModelName(bone.name());
                addBone(parent, bone, null, modelName, built, worldPoses);
            }
        }
        for (Bone bone : bones) {
            if (!bone.parent().isBlank() && !built.containsKey(bone.name())) {
                ModelPartData parent = built.get(bone.parent());
                if (parent == null) {
                    throw new IllegalStateException("Missing parent for supplied End Rift bone: "
                            + bone.name() + " -> " + bone.parent());
                }
                addBone(parent, bone, byName.get(bone.parent()), bone.name(), built, worldPoses);
            }
        }
        if (built.size() != bones.size()) {
            throw new IllegalStateException("Could not import all supplied End Rift bones: "
                    + built.size() + "/" + bones.size());
        }

        // These stable markers keep the existing phase pose code compatible;
        // the artist geometry itself is held by the imported bones above.
        addMarkers(body, root);
        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    static java.util.Set<String> sourceBoneNames() {
        List<Bone> bones = parseBones(readGeometry().getAsJsonArray("bones"));
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Bone bone : bones) {
            names.add(bone.name());
        }
        return java.util.Set.copyOf(names);
    }

    /**
     * Vanilla's ModelPartBuilder accepts one standard cuboid UV rectangle.
     * The supplied Bedrock asset uses a hand-packed rectangle for every face,
     * so replace the generated cuboids with the public ModelPart Quad type
     * after the model tree exists.  This keeps the existing Enderman renderer
     * contract while preserving the artist's actual six-face UV layout.
     */
    static void applyExactFaceUv(ModelPart root) {
        Object rootObject = root;
        if (!(rootObject instanceof ModelPartAccessor)) {
            // Unit tests run without Fabric's runtime mixin transformer.  The
            // normal game always has this accessor from copimineclient.mixins.
            return;
        }
        List<Bone> bones = parseBones(readGeometry().getAsJsonArray("bones"));
        for (Bone bone : bones) {
            ModelPart part = findBone(root, bone.name());
            Object partObject = part;
            if (part == null || !(partObject instanceof ModelPartAccessor)) {
                throw new IllegalStateException("Missing runtime part for supplied End Rift bone: "
                        + bone.name());
            }
            List<Cube> unrotated = new ArrayList<>();
            List<Cube> rotated = new ArrayList<>();
            for (Cube cube : bone.cubes()) {
                if (cube.hasRotation()) {
                    rotated.add(cube);
                } else {
                    unrotated.add(cube);
                }
            }
            replaceCuboids(part, unrotated);
            for (int index = 0; index < rotated.size(); index++) {
                ModelPart rotatedPart = part.getChild("source_cube_" + index);
                Cube cube = rotated.get(index);
                replaceCuboids(rotatedPart, List.of(cube));
            }
        }
    }

    static ModelPart findBone(ModelPart root, String sourceName) {
        if (root == null || sourceName == null || sourceName.isBlank()) {
            return null;
        }
        String rootName = rootModelName(sourceName);
        if ("body".equals(sourceName) && root.hasChild("body")) {
            ModelPart carrier = root.getChild("body");
            if (carrier.hasChild("torso")) {
                return carrier.getChild("torso");
            }
        }
        if (!sourceName.equals(rootName) && root.hasChild(rootName)) {
            return root.getChild(rootName);
        }
        return findChildByName(root, sourceName);
    }

    private static void replaceCuboids(ModelPart part, List<Cube> cubes) {
        Object partObject = part;
        if (part == null || !(partObject instanceof ModelPartAccessor)) {
            throw new IllegalStateException("Missing runtime cuboid part for supplied End Rift model");
        }
        ModelPartAccessor accessor = (ModelPartAccessor) partObject;
        List<ModelPart.Cuboid> existing = accessor.copimine$getCuboids();
        if (existing.size() != cubes.size()) {
            throw new IllegalStateException("Supplied End Rift cuboid count does not match runtime model: "
                    + existing.size() + " != " + cubes.size());
        }
        List<ModelPart.Cuboid> replacements = new ArrayList<>(existing.size());
        for (int index = 0; index < cubes.size(); index++) {
            Cube cube = cubes.get(index);
            ModelPart.Cuboid cuboid = existing.get(index);
            if (!(cuboid instanceof ModelPartCuboidAccessor cuboidAccessor)) {
                throw new IllegalStateException("Missing runtime cuboid accessor for supplied End Rift model");
            }
            ModelPart.Quad[] templateSides = cuboidAccessor.copimine$getSides();
            cuboidAccessor.copimine$setSides(createFaceQuads(cube, templateSides));
            replacements.add(cuboid);
        }
        accessor.copimine$setCuboids(List.copyOf(replacements));
    }

    /**
     * Keep the exact vertex topology that vanilla produced for the cuboid and
     * replace only its texture coordinates. Bedrock's six-face atlas is packed
     * independently per face; rebuilding the quads from min/max vertices here
     * changes face orientation after the Y-axis coordinate reflection and puts
     * valid atlas islands on the wrong physical surfaces.
     */
    private static ModelPart.Quad[] createFaceQuads(Cube cube, ModelPart.Quad[] templateSides) {
        if (templateSides == null || templateSides.length != 6) {
            throw new IllegalStateException("Unexpected vanilla cuboid side count for supplied End Rift model: "
                    + (templateSides == null ? "null" : templateSides.length));
        }
        return new ModelPart.Quad[]{
                face(templateSides[0], cube.face("down"), Direction.DOWN),
                face(templateSides[1], cube.face("up"), Direction.UP),
                face(templateSides[2], cube.face("west"), Direction.WEST),
                face(templateSides[3], cube.face("north"), Direction.NORTH),
                face(templateSides[4], cube.face("east"), Direction.EAST),
                face(templateSides[5], cube.face("south"), Direction.SOUTH)
        };
    }

    private static ModelPart.Quad face(ModelPart.Quad template, Face uv, Direction direction) {
        return remapFace(template,
                uv.u() * UV_SCALE,
                uv.v() * UV_SCALE,
                (uv.u() + uv.width()) * UV_SCALE,
                (uv.v() + uv.height()) * UV_SCALE,
                direction);
    }

    static ModelPart.Quad remapFace(ModelPart.Quad template,
                                    float u1, float v1, float u2, float v2,
                                    Direction direction) {
        if (template == null) {
            throw new IllegalArgumentException("template quad must not be null");
        }
        return new ModelPart.Quad(
                template.vertices.clone(),
                u1, v1, u2, v2,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                false,
                direction);
    }

    private static ModelPart findChildByName(ModelPart part, String name) {
        if (part.hasChild(name)) {
            return part.getChild(name);
        }
        for (ModelPart child : part.traverse().toList()) {
            if (child != part && child.hasChild(name)) {
                return child.getChild(name);
            }
        }
        return null;
    }

    private static void addMarkers(ModelPartData body, ModelPartData root) {
        String[] bodyMarkers = {
                "left_shoulder", "right_shoulder", "left_horn", "right_horn",
                "left_crest", "right_crest", "back_spine", "left_shard", "right_shard",
                "chest_rift", "jaw", "core_eye", "crown_left", "crown_right", "last_seal_spine"
        };
        for (String marker : bodyMarkers) {
            body.addChild(marker, ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        }
        body.getChild("left_horn").addChild("left_horn_tip", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.getChild("right_horn").addChild("right_horn_tip", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.getChild("left_arm").addChild("left_talon", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.getChild("right_arm").addChild("right_talon", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
    }

    private static ModelPartData addBone(ModelPartData parentData, Bone bone, Bone parent,
                                         String modelName, Map<String, ModelPartData> built,
                                         Map<String, BedrockCoordinateTransform.Pose> worldPoses) {
        BedrockCoordinateTransform.Pose parentPose = parent == null
                ? BedrockCoordinateTransform.Pose.identity()
                : worldPoses.get(parent.name());
        if (parentPose == null) {
            throw new IllegalStateException("Missing converted parent pose for supplied End Rift bone: "
                    + bone.name() + " -> " + bone.parent());
        }
        BedrockCoordinateTransform.Pose bonePose = new BedrockCoordinateTransform.Pose(
                BedrockCoordinateTransform.sourcePoint(new BedrockCoordinateTransform.Vec3(
                        bone.pivot().x(), bone.pivot().y(), bone.pivot().z())),
                parentPose.rotation().multiply(BedrockCoordinateTransform.sourceRotation(
                        bone.rotation().x(), bone.rotation().y(), bone.rotation().z())));
        BedrockCoordinateTransform.Pose localPose = bonePose.relativeTo(parentPose);
        Point localPivot = point(localPose.translation());
        Point targetRotation = targetRotation(localPose.rotation());
        ModelPartBuilder builder = ModelPartBuilder.create();
        List<Cube> rotatedCubes = new ArrayList<>();
        for (Cube cube : bone.cubes()) {
            if (cube.hasRotation()) {
                rotatedCubes.add(cube);
                continue;
            }
            addCube(builder, cube, bone.pivot());
        }
        ModelPartData part = parentData.addChild(modelName, builder,
                ModelTransform.of(localPivot.x(), localPivot.y(), localPivot.z(),
                        radians(targetRotation.x()), radians(targetRotation.y()), radians(targetRotation.z())));
        built.put(bone.name(), part);
        worldPoses.put(bone.name(), bonePose);

        for (int index = 0; index < rotatedCubes.size(); index++) {
            Cube cube = rotatedCubes.get(index);
            BedrockCoordinateTransform.Vec3 convertedCubePivot = bonePose.rotation().inverse().transform(
                    BedrockCoordinateTransform.sourcePoint(new BedrockCoordinateTransform.Vec3(
                            cube.pivot().x(), cube.pivot().y(), cube.pivot().z()))
                            .subtract(bonePose.translation()));
            Point cubeLocalPivot = point(convertedCubePivot);
            Point cubeRotation = targetRotation(cube.rotation());
            ModelPartBuilder cubeBuilder = ModelPartBuilder.create();
            addCube(cubeBuilder, cube, cube.pivot());
            part.addChild("source_cube_" + index, cubeBuilder,
                    ModelTransform.of(cubeLocalPivot.x(), cubeLocalPivot.y(), cubeLocalPivot.z(),
                            radians(cubeRotation.x()), radians(cubeRotation.y()),
                            radians(cubeRotation.z())));
        }
        return part;
    }

    private static void addCube(ModelPartBuilder builder, Cube cube, Point pivot) {
        Point origin = cube.origin();
        Point size = cube.size();
        BedrockCoordinateTransform.Vec3 localVector = BedrockCoordinateTransform.sourceDelta(
                origin.x() - pivot.x(),
                origin.y() + size.y() - pivot.y(),
                origin.z() - pivot.z());
        Point local = new Point((float) localVector.x(), (float) localVector.y(),
                (float) localVector.z());
        Face north = cube.face("north");
        builder.uv(Math.round(north.u() * UV_SCALE), Math.round(north.v() * UV_SCALE))
                .cuboid(local.x(), local.y(), local.z(), size.x(), size.y(), size.z());
    }

    private static String rootModelName(String sourceName) {
        return switch (sourceName) {
            case "right_hand" -> "right_arm";
            case "left_hand" -> "left_arm";
            case "right_leg" -> "right_leg";
            case "left_leg" -> "left_leg";
            case "body" -> "torso";
            default -> sourceName;
        };
    }

    private static Point targetRotation(Point source) {
        return targetRotation(BedrockCoordinateTransform.sourceRotation(
                source.x(), source.y(), source.z()));
    }

    private static Point targetRotation(BedrockCoordinateTransform.Matrix3 target) {
        return point(target.toEulerXyzDegrees());
    }

    private static Point point(BedrockCoordinateTransform.Vec3 value) {
        return new Point((float) value.x(), (float) value.y(), (float) value.z());
    }

    private static float radians(float degrees) {
        return degrees * (float) (Math.PI / 180.0D);
    }

    private static JsonObject readGeometry() {
        try (InputStream stream = UserEndBossModelData.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing supplied End Rift geometry resource: " + RESOURCE);
            }
            JsonObject document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray geometries = document.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.size() != 1) {
                throw new IllegalStateException("Expected one supplied End Rift geometry definition");
            }
            BedrockAssetValidator.validateGeometry(document, RESOURCE);
            try (InputStream texture = UserEndBossModelData.class.getResourceAsStream(
                    "/assets/copimineclient/textures/entity/end_rift_user_boss.png")) {
                BedrockAssetValidator.validateTexture(texture,
                        "/assets/copimineclient/textures/entity/end_rift_user_boss.png",
                        TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }
            return geometries.get(0).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Unable to load supplied End Rift geometry", error);
        }
    }

    private static List<Bone> parseBones(JsonArray sourceBones) {
        List<Bone> bones = new ArrayList<>();
        for (JsonElement element : sourceBones) {
            JsonObject source = element.getAsJsonObject();
            String name = source.get("name").getAsString();
            String parent = source.has("parent") ? source.get("parent").getAsString() : "";
            Point pivot = point(source.getAsJsonArray("pivot"));
            Point rotation = source.has("rotation")
                    ? point(source.getAsJsonArray("rotation")) : new Point(0.0F, 0.0F, 0.0F);
            List<Cube> cubes = new ArrayList<>();
            if (source.has("cubes")) {
                for (JsonElement cubeElement : source.getAsJsonArray("cubes")) {
                    cubes.add(parseCube(cubeElement.getAsJsonObject()));
                }
            }
            bones.add(new Bone(name, parent, pivot, rotation, List.copyOf(cubes)));
        }
        return List.copyOf(bones);
    }

    private static Cube parseCube(JsonObject source) {
        Point origin = point(source.getAsJsonArray("origin"));
        Point size = point(source.getAsJsonArray("size"));
        Point pivot = source.has("pivot") ? point(source.getAsJsonArray("pivot")) : origin;
        Point rotation = source.has("rotation")
                ? point(source.getAsJsonArray("rotation")) : new Point(0.0F, 0.0F, 0.0F);
        JsonObject uvObject = source.getAsJsonObject("uv");
        Map<String, Face> faces = new HashMap<>();
        for (String direction : List.of("north", "south", "east", "west", "up", "down")) {
            JsonObject face = uvObject.getAsJsonObject(direction);
            if (face == null) {
                throw new IllegalStateException("Missing " + direction + " UV face in supplied End Rift cube");
            }
            JsonArray uv = face.getAsJsonArray("uv");
            JsonArray uvSize = face.getAsJsonArray("uv_size");
            faces.put(direction, new Face(
                    uv.get(0).getAsFloat(),
                    uv.get(1).getAsFloat(),
                    uvSize.get(0).getAsFloat(),
                    uvSize.get(1).getAsFloat()));
        }
        return new Cube(origin, size, pivot, rotation, Map.copyOf(faces));
    }

    private static Point point(JsonArray array) {
        return new Point(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private record Point(float x, float y, float z) {
        private Point subtract(Point other) {
            return new Point(x - other.x(), y - other.y(), z - other.z());
        }
    }

    private record Bone(String name, String parent, Point pivot, Point rotation, List<Cube> cubes) {
    }

    private record Cube(Point origin, Point size, Point pivot, Point rotation,
                        Map<String, Face> faces) {
        private boolean hasRotation() {
            return Math.abs(rotation.x()) > 0.0001F
                    || Math.abs(rotation.y()) > 0.0001F
                    || Math.abs(rotation.z()) > 0.0001F;
        }

        private Face face(String direction) {
            Face result = faces.get(direction);
            if (result == null) {
                throw new IllegalStateException("Missing supplied End Rift UV face: " + direction);
            }
            return result;
        }
    }

    private record Face(float u, float v, float width, float height) {
    }
}
