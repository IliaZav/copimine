package me.copimine.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders the source Kagune mesh with its imported face UVs and bone hierarchy. */
public final class EndRiftTentacleRig {
    public static final int TEXTURE_UV_WIDTH = 8;
    public static final List<String> REQUIRED_BONES = List.of(
            "1layer", "1layer2", "2layer", "2layer2", "3layer", "3layer2");

    private static final KaguneModelImporter.ImportedModel MODEL = KaguneModelImporter.load();
    private static final List<BoneDefinition> DEFINITIONS = definitionsFrom(MODEL);

    private EndRiftTentacleRig() {
    }

    public static List<BoneDefinition> definitions() {
        return DEFINITIONS;
    }

    public static RenderRig createRenderRig() {
        return new RenderRig(MODEL);
    }

    private static List<BoneDefinition> definitionsFrom(KaguneModelImporter.ImportedModel model) {
        Map<String, KaguneModelImporter.Cuboid> elementByBone = new LinkedHashMap<>();
        for (KaguneModelImporter.Cuboid element : model.elements()) {
            elementByBone.put(element.boneName(), element);
        }
        List<BoneDefinition> result = new ArrayList<>();
        for (KaguneModelImporter.Bone bone : model.bones()) {
            KaguneModelImporter.Cuboid element = elementByBone.get(bone.name());
            float width = element == null ? 0.0F : element.to().x() - element.from().x();
            float length = element == null ? 0.0F : element.to().y() - element.from().y();
            float depth = element == null ? 0.0F : element.to().z() - element.from().z();
            result.add(new BoneDefinition(bone.name(), bone.parentName(), bone.origin().x(),
                    bone.origin().y(), bone.origin().z(), width, length, depth, element != null));
        }
        return List.copyOf(result);
    }

    private static EndRiftTentaclePose.BoneTransform transformForGroup(
            EndRiftTentaclePose.TentaclePose pose, String group) {
        return switch (group) {
            case "1layer" -> pose.base();
            case "1layer2" -> pose.seg_01();
            case "2layer" -> pose.seg_02();
            case "2layer2" -> pose.seg_03();
            case "3layer" -> pose.seg_04();
            case "3layer2" -> pose.seg_05();
            default -> EndRiftTentaclePose.BoneTransform.identity();
        };
    }

    private static Matrix4f applyDelta(Matrix4f parentMatrix, KaguneModelImporter.Vec3 origin,
                                       KaguneModelImporter.Vec3 parentOrigin,
                                       KaguneModelImporter.Vec3 restRotation,
                                       EndRiftTentaclePose.BoneTransform delta) {
        float x = origin.x() - parentOrigin.x() + delta.translationX();
        float y = origin.y() - parentOrigin.y() + delta.translationY();
        float z = origin.z() - parentOrigin.z() + delta.translationZ();
        return new Matrix4f(parentMatrix)
                .translate(x, y, z)
                .rotateXYZ((float) Math.toRadians(restRotation.x()) + delta.pitch(),
                        (float) Math.toRadians(restRotation.y()) + delta.yaw(),
                        (float) Math.toRadians(restRotation.z()) + delta.roll())
                .scale(delta.scaleX(), delta.scaleY(), delta.scaleZ());
    }

    public record BoneDefinition(String name, String parent, float pivotX, float pivotY,
                                 float pivotZ, float width, float length, float depth,
                                 boolean hasGeometry) {
    }

    public static final class RenderRig {
        private static final Vector3f ZERO = new Vector3f();
        private final KaguneModelImporter.ImportedModel model;
        private final Map<String, List<KaguneModelImporter.Cuboid>> cuboidsByBone;
        private final Map<String, List<KaguneModelImporter.Bone>> childrenByBone;
        private final List<KaguneModelImporter.Bone> roots;

        private RenderRig(KaguneModelImporter.ImportedModel model) {
            this.model = model;
            Map<String, List<KaguneModelImporter.Cuboid>> cuboids = new LinkedHashMap<>();
            for (KaguneModelImporter.Cuboid cuboid : model.elements()) {
                cuboids.computeIfAbsent(cuboid.boneName(), ignored -> new ArrayList<>()).add(cuboid);
            }
            this.cuboidsByBone = immutableLists(cuboids);

            Map<String, List<KaguneModelImporter.Bone>> children = new LinkedHashMap<>();
            List<KaguneModelImporter.Bone> rootBones = new ArrayList<>();
            for (KaguneModelImporter.Bone bone : model.bones()) {
                String renderParent = bone.parentName();
                if (renderParent == null) {
                    rootBones.add(bone);
                } else {
                    children.computeIfAbsent(renderParent, ignored -> new ArrayList<>()).add(bone);
                }
            }
            this.childrenByBone = immutableLists(children);
            this.roots = List.copyOf(rootBones);
        }

        public void render(MatrixStack matrices, VertexConsumer buffer,
                           EndRiftTentaclePose.TentaclePose pose, int light, int overlay,
                           int color) {
            EndRiftTentaclePose.TentaclePose safePose = pose == null
                    ? EndRiftTentaclePose.TentaclePose.identity() : pose;
            EndRiftTentaclePose.BoneTransform rootDelta = safePose.root();
            Matrix4f rootMatrix = new Matrix4f()
                    .translate(rootDelta.translationX(), rootDelta.translationY(), rootDelta.translationZ())
                    .rotateXYZ(rootDelta.pitch(), rootDelta.yaw(), rootDelta.roll())
                    .scale(rootDelta.scaleX(), rootDelta.scaleY(), rootDelta.scaleZ());
            List<RenderedVertex> vertices = new ArrayList<>(model.elements().size() * 24);
            for (KaguneModelImporter.Bone root : roots) {
                appendBone(root, KaguneModelImporter.Vec3.ZERO, rootMatrix, safePose, vertices);
            }
            MatrixStack.Entry entry = matrices.peek();
            float inverseUvWidth = 1.0F / model.textureUvWidth();
            float inverseUvHeight = 1.0F / model.textureUvHeight();
            for (RenderedVertex vertex : vertices) {
                buffer.vertex(entry, vertex.x(), vertex.y(), vertex.z())
                        .color(color)
                        .texture(vertex.u() * inverseUvWidth, vertex.v() * inverseUvHeight)
                        .overlay(overlay)
                        .light(light)
                        .normal(entry, vertex.normalX(), vertex.normalY(), vertex.normalZ());
            }
        }

        private void appendBone(KaguneModelImporter.Bone bone,
                                KaguneModelImporter.Vec3 parentOrigin,
                                Matrix4f parentMatrix,
                                EndRiftTentaclePose.TentaclePose pose,
                                List<RenderedVertex> output) {
            EndRiftTentaclePose.BoneTransform delta = transformForGroup(pose, bone.name());
            Matrix4f boneMatrix = applyDelta(parentMatrix, bone.origin(), parentOrigin,
                    bone.rotation(), delta);
            for (KaguneModelImporter.Cuboid cuboid : cuboidsByBone.getOrDefault(bone.name(), List.of())) {
                appendCuboid(cuboid, bone, boneMatrix, output);
            }
            for (KaguneModelImporter.Bone child : childrenByBone.getOrDefault(bone.name(), List.of())) {
                appendBone(child, bone.origin(), boneMatrix, pose, output);
            }
        }

        private void appendCuboid(KaguneModelImporter.Cuboid cuboid,
                                  KaguneModelImporter.Bone bone,
                                  Matrix4f boneMatrix,
                                  List<RenderedVertex> output) {
            KaguneModelImporter.Vec3 elementLocalOrigin = cuboid.origin().subtract(bone.origin());
            Matrix4f cuboidMatrix = new Matrix4f(boneMatrix)
                    .translate(elementLocalOrigin.x(), elementLocalOrigin.y(), elementLocalOrigin.z())
                    .rotateXYZ((float) Math.toRadians(cuboid.rotation().x()),
                            (float) Math.toRadians(cuboid.rotation().y()),
                            (float) Math.toRadians(cuboid.rotation().z()));
            Matrix3f normalMatrix = new Matrix3f(cuboidMatrix);
            float x0 = cuboid.from().x() - cuboid.origin().x();
            float y0 = cuboid.from().y() - cuboid.origin().y();
            float z0 = cuboid.from().z() - cuboid.origin().z();
            float x1 = cuboid.to().x() - cuboid.origin().x();
            float y1 = cuboid.to().y() - cuboid.origin().y();
            float z1 = cuboid.to().z() - cuboid.origin().z();

            appendFace(cuboid, cuboidMatrix, normalMatrix, "north", new float[][]{
                    {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}},
                    0.0F, 0.0F, -1.0F, output);
            appendFace(cuboid, cuboidMatrix, normalMatrix, "east", new float[][]{
                    {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}},
                    1.0F, 0.0F, 0.0F, output);
            appendFace(cuboid, cuboidMatrix, normalMatrix, "south", new float[][]{
                    {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}},
                    0.0F, 0.0F, 1.0F, output);
            appendFace(cuboid, cuboidMatrix, normalMatrix, "west", new float[][]{
                    {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}},
                    -1.0F, 0.0F, 0.0F, output);
            appendFace(cuboid, cuboidMatrix, normalMatrix, "up", new float[][]{
                    {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                    0.0F, 1.0F, 0.0F, output);
            appendFace(cuboid, cuboidMatrix, normalMatrix, "down", new float[][]{
                    {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    0.0F, -1.0F, 0.0F, output);
        }

        private void appendFace(KaguneModelImporter.Cuboid cuboid,
                                Matrix4f positionMatrix,
                                Matrix3f normalMatrix,
                                String faceName,
                                float[][] corners,
                                float normalX, float normalY, float normalZ,
                                List<RenderedVertex> output) {
            KaguneModelImporter.UvRect uv = cuboid.faces().get(faceName);
            float[][] uvCorners = uvCorners(uv);
            Vector3f normal = normalMatrix.transform(new Vector3f(normalX, normalY, normalZ)).normalize();
            for (int index = 0; index < 4; index++) {
                Vector3f position = positionMatrix.transformPosition(
                        new Vector3f(corners[index][0], corners[index][1], corners[index][2]));
                output.add(new RenderedVertex(position.x, position.y, position.z,
                        uvCorners[index][0], uvCorners[index][1], normal.x, normal.y, normal.z));
            }
        }

        private static float[][] uvCorners(KaguneModelImporter.UvRect uv) {
            float[][] result = {
                    {uv.u0(), uv.v0()}, {uv.u1(), uv.v0()},
                    {uv.u1(), uv.v1()}, {uv.u0(), uv.v1()}
            };
            int turns = Math.floorMod(uv.rotation() / 90, 4);
            for (int turn = 0; turn < turns; turn++) {
                float[] last = result[3];
                result[3] = result[2];
                result[2] = result[1];
                result[1] = result[0];
                result[0] = last;
            }
            return result;
        }

        private static <T> Map<String, List<T>> immutableLists(Map<String, List<T>> source) {
            Map<String, List<T>> result = new LinkedHashMap<>();
            source.forEach((name, values) -> result.put(name, List.copyOf(values)));
            return Map.copyOf(result);
        }
    }

    private record RenderedVertex(float x, float y, float z, float u, float v,
                                  float normalX, float normalY, float normalZ) {
    }
}
