package me.copimine.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.util.math.MathHelper;

/**
 * A clean, deliberately long rift skeleton rig for the wave mobs.
 *
 * <p>The upper and lower arm/leg segments are attached as child parts and
 * overlap at their joints. That makes the silhouette continuous at every
 * animation pose instead of relying on transparent pixels to hide a gap.
 * The model remains a {@link SkeletonEntityModel}, so the vanilla skeleton
 * renderer and its gameplay-sized entity hitbox stay intact.</p>
 */
public final class RiftEventSkeletonModel extends SkeletonEntityModel<AbstractSkeletonEntity> {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;

    private final ModelPart root;
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftLowerLeg;
    private final ModelPart rightLowerLeg;
    private final ModelPart eliteShoulderLeft;
    private final ModelPart eliteShoulderRight;
    private final boolean elite;

    public RiftEventSkeletonModel(ModelPart root, boolean elite) {
        super(root);
        this.root = root;
        this.leftForearm = root.getChild("left_arm").getChild("left_forearm");
        this.rightForearm = root.getChild("right_arm").getChild("right_forearm");
        this.leftLowerLeg = root.getChild("left_leg").getChild("left_lower_leg");
        this.rightLowerLeg = root.getChild("right_leg").getChild("right_lower_leg");
        this.eliteShoulderLeft = root.getChild("left_arm").getChild("elite_shoulder_left");
        this.eliteShoulderRight = root.getChild("right_arm").getChild("elite_shoulder_right");
        this.elite = elite;
        this.eliteShoulderLeft.visible = elite;
        this.eliteShoulderRight.visible = elite;
        this.hat.visible = false;
    }

    public static TexturedModelData getTexturedModelData(boolean elite) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Head and torso meet exactly at y=0. The narrow proportions match
        // the supplied reference while keeping a readable vanilla skeleton
        // silhouette in a normal third-person camera.
        root.addChild("head", cube(0, 0, -3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("hat", ModelPartBuilder.create().cuboid(
                        -3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F, Dilation.NONE),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("body", cube(16, 0, -2.5F, 0.0F, -1.5F, 5.0F,
                        elite ? 10.5F : 10.0F, 3.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData leftArm = root.addChild("left_arm", cube(32, 0, -2.0F, -1.0F,
                        -1.5F, 4.0F, 9.0F, 3.0F),
                ModelTransform.pivot(4.0F, 1.0F, 0.0F));
        leftArm.addChild("left_forearm", cube(40, 0, -1.5F, -0.5F, -1.25F,
                        3.0F, 12.0F, 2.5F),
                ModelTransform.pivot(0.0F, 8.5F, 0.0F));
        leftArm.addChild("elite_shoulder_left", cube(48, 0, -2.5F, -1.5F, -2.0F,
                        5.0F, 3.0F, 4.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData rightArm = root.addChild("right_arm", cube(32, 0, -2.0F, -1.0F,
                        -1.5F, 4.0F, 9.0F, 3.0F),
                ModelTransform.pivot(-4.0F, 1.0F, 0.0F));
        rightArm.addChild("right_forearm", cube(40, 0, -1.5F, -0.5F, -1.25F,
                        3.0F, 12.0F, 2.5F),
                ModelTransform.pivot(0.0F, 8.5F, 0.0F));
        rightArm.addChild("elite_shoulder_right", cube(48, 0, -2.5F, -1.5F, -2.0F,
                        5.0F, 3.0F, 4.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData leftLeg = root.addChild("left_leg", cube(0, 16, -1.25F, 0.0F,
                        -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.pivot(1.35F, 10.0F, 0.0F));
        leftLeg.addChild("left_lower_leg", cube(8, 16, -1.0F, -0.5F, -1.0F,
                        2.0F, 13.0F, 2.0F),
                ModelTransform.pivot(0.0F, 7.5F, 0.0F));

        ModelPartData rightLeg = root.addChild("right_leg", cube(0, 16, -1.25F, 0.0F,
                        -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.pivot(-1.35F, 10.0F, 0.0F));
        rightLeg.addChild("right_lower_leg", cube(8, 16, -1.0F, -0.5F, -1.0F,
                        2.0F, 13.0F, 2.0F),
                ModelTransform.pivot(0.0F, 7.5F, 0.0F));

        // A compact central rift gives both variants a readable focal point;
        // the elite receives a larger plate without changing the entity's
        // collision shape.
        root.addChild("rift_core", cube(24, 16, -1.0F, 3.0F, -1.75F,
                        elite ? 2.0F : 1.5F, elite ? 3.0F : 2.0F, 1.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static ModelPartBuilder cube(int u, int v, float x, float y, float z,
                                         float width, float height, float depth) {
        return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, width, height, depth);
    }

    @Override
    public void setAngles(AbstractSkeletonEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        root.traverse().forEach(ModelPart::resetTransform);
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float pulse = MathHelper.sin(animationProgress * 0.14F);
        leftForearm.roll += pulse * (elite ? 0.075F : 0.05F);
        rightForearm.roll -= pulse * (elite ? 0.075F : 0.05F);
        leftLowerLeg.yaw += pulse * 0.025F;
        rightLowerLeg.yaw -= pulse * 0.025F;
        eliteShoulderLeft.pitch += pulse * 0.025F;
        eliteShoulderRight.pitch -= pulse * 0.025F;
    }

    public boolean isElite() {
        return elite;
    }

    public ModelPart getPart() {
        return root;
    }
}
