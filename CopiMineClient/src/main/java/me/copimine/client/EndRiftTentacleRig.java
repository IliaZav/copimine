package me.copimine.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

/**
 * The actual client-side articulated rig.  It is built once and re-used for
 * every carrier display; each bone receives its own interpolated transform.
 */
public final class EndRiftTentacleRig {
    public static final String GRAB_SOCKET = "grab_socket";
    public static final int TEXTURE_SIZE = 512;
    private static final int UV_SCALE = TEXTURE_SIZE / 256;
    public static final List<String> REQUIRED_BONES = List.of(
            "root", "base", "seg_01", "seg_02", "seg_03", "seg_04", "seg_05",
            "tip", "tip_claw_1", "tip_claw_2", "tip_claw_3", "tip_claw_4", GRAB_SOCKET);

    private static final List<BoneDefinition> DEFINITIONS = List.of(
            new BoneDefinition("root", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false),
            new BoneDefinition("base", "root", 0.0F, 0.0F, 0.0F, 1.25F, 1.06F, 1.25F, true),
            new BoneDefinition("seg_01", "base", 0.0F, 1.06F, 0.0F, 0.88F, 0.63F, 0.88F, true),
            new BoneDefinition("seg_02", "seg_01", 0.0F, 0.63F, 0.0F, 0.88F, 0.63F, 0.88F, true),
            new BoneDefinition("seg_03", "seg_02", 0.0F, 0.63F, 0.0F, 0.88F, 0.63F, 0.88F, true),
            new BoneDefinition("seg_04", "seg_03", 0.0F, 0.63F, 0.0F, 0.88F, 0.63F, 0.88F, true),
            new BoneDefinition("seg_05", "seg_04", 0.0F, 0.63F, 0.0F, 0.88F, 0.63F, 0.88F, true),
            new BoneDefinition("tip", "seg_05", 0.0F, 0.63F, 0.0F, 1.0F, 0.56F, 1.0F, true),
            new BoneDefinition("tip_claw_1", "tip", -0.31F, 0.56F, -0.06F, 0.25F, 0.72F, 0.25F, true),
            new BoneDefinition("tip_claw_2", "tip", 0.31F, 0.56F, -0.06F, 0.25F, 0.72F, 0.25F, true),
            new BoneDefinition("tip_claw_3", "tip", 0.0F, 0.56F, -0.31F, 0.25F, 0.72F, 0.25F, true),
            new BoneDefinition("tip_claw_4", "tip", 0.0F, 0.56F, 0.31F, 0.25F, 0.72F, 0.25F, true),
            new BoneDefinition(GRAB_SOCKET, "tip", 0.0F, 0.56F, 0.0F, 0.0F, 0.0F, 0.0F, false));

    private EndRiftTentacleRig() {
    }

    public static List<BoneDefinition> definitions() {
        return DEFINITIONS;
    }

    public static RenderRig createRenderRig() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData base = root.addChild("base", cube(0, 0, 20, 17, 20), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        ModelPartData seg01 = base.addChild("seg_01", cube(32, 0, 14, 10, 14), ModelTransform.pivot(0.0F, 17.0F, 0.0F));
        ModelPartData seg02 = seg01.addChild("seg_02", cube(48, 0, 14, 10, 14), ModelTransform.pivot(0.0F, 10.0F, 0.0F));
        ModelPartData seg03 = seg02.addChild("seg_03", cube(64, 0, 14, 10, 14), ModelTransform.pivot(0.0F, 10.0F, 0.0F));
        ModelPartData seg04 = seg03.addChild("seg_04", cube(80, 0, 14, 10, 14), ModelTransform.pivot(0.0F, 10.0F, 0.0F));
        ModelPartData seg05 = seg04.addChild("seg_05", cube(96, 0, 14, 10, 14), ModelTransform.pivot(0.0F, 10.0F, 0.0F));
        ModelPartData tip = seg05.addChild("tip", cube(112, 0, 16, 9, 16), ModelTransform.pivot(0.0F, 10.0F, 0.0F));
        tip.addChild("tip_claw_1", claw(144, 0), ModelTransform.of(-5.0F, 9.0F, -1.0F, 0.0F, 0.0F, -0.28F));
        tip.addChild("tip_claw_2", claw(176, 0), ModelTransform.of(5.0F, 9.0F, -1.0F, 0.0F, 0.0F, 0.28F));
        tip.addChild("tip_claw_3", claw(208, 0), ModelTransform.of(0.0F, 9.0F, -5.0F, 0.0F, 0.28F, 0.0F));
        tip.addChild("tip_claw_4", claw(224, 32), ModelTransform.of(0.0F, 9.0F, 5.0F, 0.0F, -0.28F, 0.0F));
        tip.addChild(GRAB_SOCKET, ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 9.0F, 0.0F));
        ModelPart modelRoot = TexturedModelData.of(data, TEXTURE_SIZE, TEXTURE_SIZE).createModel();
        return new RenderRig(modelRoot,
                modelRoot.getChild("base"),
                modelRoot.getChild("base").getChild("seg_01"),
                modelRoot.getChild("base").getChild("seg_01").getChild("seg_02"),
                modelRoot.getChild("base").getChild("seg_01").getChild("seg_02").getChild("seg_03"),
                modelRoot.getChild("base").getChild("seg_01").getChild("seg_02").getChild("seg_03").getChild("seg_04"),
                modelRoot.getChild("base").getChild("seg_01").getChild("seg_02").getChild("seg_03").getChild("seg_04").getChild("seg_05"));
    }

    private static ModelPartBuilder cube(int u, int v, int width, int height, int depth) {
        return ModelPartBuilder.create().uv(u * UV_SCALE, v * UV_SCALE)
                .cuboid(-width / 2.0F, 0.0F, -depth / 2.0F, width, height, depth);
    }

    private static ModelPartBuilder claw(int u, int v) {
        return ModelPartBuilder.create().uv(u * UV_SCALE, v * UV_SCALE)
                .cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F)
                .uv((u + 8) * UV_SCALE, v * UV_SCALE)
                .cuboid(-1.0F, 8.0F, -1.0F, 2.0F, 4.0F, 2.0F);
    }

    public record BoneDefinition(String name, String parent, float pivotX, float pivotY,
                                 float pivotZ, float width, float length, float depth,
                                 boolean hasGeometry) {
    }

    public static final class RenderRig {
        private final ModelPart root;
        private final ModelPart base;
        private final ModelPart[] segments;
        private final ModelPart[] claws;
        private final ModelPart tip;
        private final ModelPart[] parts;

        private RenderRig(ModelPart root, ModelPart base, ModelPart seg01, ModelPart seg02,
                          ModelPart seg03, ModelPart seg04, ModelPart seg05) {
            this.root = root;
            this.base = base;
            this.segments = new ModelPart[]{seg01, seg02, seg03, seg04, seg05};
            this.tip = seg05.getChild("tip");
            this.claws = new ModelPart[]{tip.getChild("tip_claw_1"), tip.getChild("tip_claw_2"),
                    tip.getChild("tip_claw_3"), tip.getChild("tip_claw_4")};
            this.parts = new ModelPart[]{root, base, seg01, seg02, seg03, seg04, seg05,
                    tip, claws[0], claws[1], claws[2], claws[3], tip.getChild(GRAB_SOCKET)};
        }

        public void render(MatrixStack matrices, VertexConsumer buffer,
                           EndRiftTentaclePose.TentaclePose pose, int light, int overlay,
                           int color) {
            reset();
            apply(root, pose.root());
            apply(base, pose.base());
            apply(segments[0], pose.seg_01());
            apply(segments[1], pose.seg_02());
            apply(segments[2], pose.seg_03());
            apply(segments[3], pose.seg_04());
            apply(segments[4], pose.seg_05());
            apply(tip, pose.tip());
            apply(claws[0], pose.tip_claw_1());
            apply(claws[1], pose.tip_claw_2());
            apply(claws[2], pose.tip_claw_3());
            apply(claws[3], pose.tip_claw_4());
            root.render(matrices, buffer, light, overlay, color);
        }

        public void reset() {
            for (ModelPart part : parts) {
                part.resetTransform();
            }
        }

        private static void apply(ModelPart part, EndRiftTentaclePose.BoneTransform transform) {
            if (part == null || transform == null) {
                return;
            }
            part.pivotX += transform.translationX();
            part.pivotY += transform.translationY();
            part.pivotZ += transform.translationZ();
            part.pitch = transform.pitch();
            part.yaw = transform.yaw();
            part.roll = transform.roll();
            part.xScale = transform.scaleX();
            part.yScale = transform.scaleY();
            part.zScale = transform.scaleZ();
        }
    }
}
