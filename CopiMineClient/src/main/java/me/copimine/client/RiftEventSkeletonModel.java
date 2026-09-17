package me.copimine.client;

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
 * Articulated End Rift skeleton rig used by the ordinary and elite wave roles.
 *
 * <p>The supplied references have a very thin, long silhouette with a dark
 * shell, visible bone joints and a controlled rift accent. The rig therefore
 * uses overlapping segmented parts for the full silhouette: jaw, ribs, cuffs,
 * knees, shins and (for the elite) horns and shoulder plates. The entity is
 * still a normal skeleton, so the server hitbox remains the native skeleton
 * hitbox while every extra part is render-only.</p>
 */
public final class RiftEventSkeletonModel extends SkeletonEntityModel<AbstractSkeletonEntity> {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;

    public enum Variant {
        ORDINARY,
        ELITE,
        WAVE_GUARDIAN,
        RITUAL_GUARD
    }

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftLowerLeg;
    private final ModelPart rightLowerLeg;
    private final ModelPart eliteHornLeft;
    private final ModelPart eliteHornRight;
    private final ModelPart eliteShoulderLeft;
    private final ModelPart eliteShoulderRight;
    private final ModelPart chestRift;
    private final ModelPart guardCrest;
    private final ModelPart guardChestSeal;
    private final ModelPart guardianSpine;
    private final ModelPart eliteMantle;
    private final ModelPart eliteHornCrown;
    private final Variant variant;

    public RiftEventSkeletonModel(ModelPart root, boolean elite) {
        this(root, elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public RiftEventSkeletonModel(ModelPart root, Variant variant) {
        super(root);
        this.root = root;
        this.head = root.getChild("head");
        this.leftForearm = root.getChild("left_arm").getChild("left_forearm");
        this.rightForearm = root.getChild("right_arm").getChild("right_forearm");
        this.leftLowerLeg = root.getChild("left_leg").getChild("left_lower_leg");
        this.rightLowerLeg = root.getChild("right_leg").getChild("right_lower_leg");
        this.eliteHornLeft = head.getChild("elite_horn_left");
        this.eliteHornRight = head.getChild("elite_horn_right");
        this.eliteShoulderLeft = root.getChild("left_arm").getChild("elite_shoulder_left");
        this.eliteShoulderRight = root.getChild("right_arm").getChild("elite_shoulder_right");
        this.chestRift = root.getChild("body").getChild("chest_rift");
        this.guardCrest = root.getChild("guard_crest");
        this.guardChestSeal = root.getChild("guard_chest_seal");
        this.guardianSpine = root.getChild("guardian_spine");
        this.eliteMantle = root.getChild("elite_mantle");
        this.eliteHornCrown = root.getChild("elite_horn_crown");
        this.variant = variant;
        boolean elite = isEliteVariant(variant);
        boolean guardian = variant == Variant.WAVE_GUARDIAN;
        boolean ritual = variant == Variant.RITUAL_GUARD;
        this.eliteHornLeft.visible = elite;
        this.eliteHornRight.visible = elite;
        this.eliteShoulderLeft.visible = elite;
        this.eliteShoulderRight.visible = elite;
        this.guardCrest.visible = guardian || ritual;
        this.guardChestSeal.visible = guardian || ritual;
        this.guardianSpine.visible = guardian;
        this.eliteMantle.visible = elite;
        this.eliteHornCrown.visible = elite;
        this.hat.visible = false;
    }

    public static TexturedModelData getTexturedModelData(boolean elite) {
        return getTexturedModelData(elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static TexturedModelData getTexturedModelData(Variant variant) {
        boolean elite = isEliteVariant(variant);
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData head = root.addChild("head", cube(0, 0,
                        -3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("hat", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 0, -3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        head.addChild("jaw", cube(8, 0, -2.5F, -1.1F, -3.7F,
                        5.0F, 1.1F, 0.55F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        head.addChild("eye_left", cube(14, 0, -2.45F, -4.35F, -3.65F,
                        1.7F, 1.2F, 0.3F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        head.addChild("eye_right", cube(14, 0, 0.75F, -4.35F, -3.65F,
                        1.7F, 1.2F, 0.3F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        head.addChild("elite_horn_left", cube(56, 0, -3.3F, -11.0F, -1.2F,
                        1.8F, 4.0F, 2.0F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        head.addChild("elite_horn_right", cube(56, 0, 1.5F, -11.0F, -1.2F,
                        1.8F, 4.0F, 2.0F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData body = root.addChild("body", cube(16, 0,
                        -2.5F, 0.0F, -1.5F, 5.0F, elite ? 10.5F : 10.0F, 3.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("collar", cube(22, 0, -2.8F, -0.6F, -1.8F,
                        5.6F, 1.5F, 3.6F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("rib_left_upper", cube(28, 0, -2.35F, 3.0F, -1.8F,
                        1.6F, 0.8F, 0.45F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("rib_right_upper", cube(28, 0, 0.75F, 3.0F, -1.8F,
                        1.6F, 0.8F, 0.45F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("rib_left_lower", cube(28, 0, -2.25F, 5.0F, -1.82F,
                        1.5F, 0.8F, 0.45F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("rib_right_lower", cube(28, 0, 0.65F, 5.0F, -1.82F,
                        1.5F, 0.8F, 0.45F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("chest_rift", cube(32, 16, -0.8F, 2.3F, -2.0F,
                        elite ? 1.6F : 1.35F, elite ? 4.3F : 3.8F, 0.35F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("waist_bone", cube(36, 16, -2.0F, 8.0F, -1.7F,
                        4.0F, 1.0F, 3.4F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData leftArm = root.addChild("left_arm", cube(40, 0,
                        -2.0F, -1.0F, -1.5F, 4.0F, 9.0F, 3.0F),
                ModelTransform.pivot(4.0F, 1.0F, 0.0F));
        leftArm.addChild("left_forearm", cube(44, 0, -1.55F, -0.55F, -1.3F,
                        3.1F, 12.0F, 2.6F), ModelTransform.pivot(0.0F, 8.4F, 0.0F));
        leftArm.addChild("left_elbow_bone", cube(48, 0, -2.15F, 6.9F, -1.75F,
                        4.3F, 1.2F, 3.5F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        leftArm.addChild("left_wrist_bone", cube(50, 0, -1.8F, 18.0F, -1.6F,
                        3.6F, 1.4F, 3.2F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        leftArm.addChild("elite_shoulder_left", cube(40, 16, -2.8F, -1.8F, -2.15F,
                        5.6F, 3.2F, 4.3F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData rightArm = root.addChild("right_arm", cube(40, 0,
                        -2.0F, -1.0F, -1.5F, 4.0F, 9.0F, 3.0F),
                ModelTransform.pivot(-4.0F, 1.0F, 0.0F));
        rightArm.addChild("right_forearm", cube(44, 0, -1.55F, -0.55F, -1.3F,
                        3.1F, 12.0F, 2.6F), ModelTransform.pivot(0.0F, 8.4F, 0.0F));
        rightArm.addChild("right_elbow_bone", cube(48, 0, -2.15F, 6.9F, -1.75F,
                        4.3F, 1.2F, 3.5F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        rightArm.addChild("right_wrist_bone", cube(50, 0, -1.8F, 18.0F, -1.6F,
                        3.6F, 1.4F, 3.2F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        rightArm.addChild("elite_shoulder_right", cube(40, 16, -2.8F, -1.8F, -2.15F,
                        5.6F, 3.2F, 4.3F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData leftLeg = root.addChild("left_leg", cube(0, 16,
                        -1.25F, 0.0F, -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.pivot(1.35F, 10.0F, 0.0F));
        leftLeg.addChild("left_knee_bone", cube(8, 16, -1.55F, 6.6F, -1.55F,
                        3.1F, 1.4F, 3.1F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        leftLeg.addChild("left_lower_leg", cube(12, 16, -1.0F, -0.55F, -1.0F,
                        2.0F, 13.0F, 2.0F), ModelTransform.pivot(0.0F, 7.5F, 0.0F));
        leftLeg.addChild("left_ankle_bone", cube(16, 16, -1.35F, 18.5F, -1.35F,
                        2.7F, 1.5F, 2.7F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        ModelPartData rightLeg = root.addChild("right_leg", cube(0, 16,
                        -1.25F, 0.0F, -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.pivot(-1.35F, 10.0F, 0.0F));
        rightLeg.addChild("right_knee_bone", cube(8, 16, -1.55F, 6.6F, -1.55F,
                        3.1F, 1.4F, 3.1F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        rightLeg.addChild("right_lower_leg", cube(12, 16, -1.0F, -0.55F, -1.0F,
                        2.0F, 13.0F, 2.0F), ModelTransform.pivot(0.0F, 7.5F, 0.0F));
        rightLeg.addChild("right_ankle_bone", cube(16, 16, -1.35F, 18.5F, -1.35F,
                        2.7F, 1.5F, 2.7F), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        root.addChild("guard_crest", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(40, 20, -3.4F, -1.2F, -2.05F,
                                6.8F, 1.6F, 0.45F),
                        new ModelUvBounds.Box(40, 23, -2.4F, -2.6F, -1.95F,
                                4.8F, 1.3F, 0.35F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("guard_chest_seal", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        48, 20, -1.8F, 1.8F, -2.0F, 3.6F, 4.4F, 0.35F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("guardian_spine", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(52, 20, -1.0F, 1.0F, 1.65F,
                                2.0F, 8.5F, 0.65F),
                        new ModelUvBounds.Box(56, 20, -1.6F, 2.5F, 1.5F,
                                3.2F, 1.0F, 0.8F),
                        new ModelUvBounds.Box(56, 22, -1.6F, 5.0F, 1.5F,
                                3.2F, 1.0F, 0.8F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("elite_mantle", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        30, 24, -6.0F, -1.0F, -2.5F, 12.0F, 2.4F, 5.0F),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        root.addChild("elite_horn_crown", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(48, 26, -3.2F, -11.0F, -1.2F,
                                1.8F, 4.0F, 2.0F),
                        new ModelUvBounds.Box(48, 26, 1.4F, -11.0F, -1.2F,
                                1.8F, 4.0F, 2.0F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static boolean isEliteVariant(Variant variant) {
        return variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
    }

    private static ModelPartBuilder cube(int u, int v, float x, float y, float z,
                                         float width, float height, float depth) {
        return ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT, u, v,
                x, y, z, width, height, depth);
    }

    @Override
    public void setAngles(AbstractSkeletonEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        root.traverse().forEach(ModelPart::resetTransform);
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float pulse = MathHelper.sin(animationProgress * 0.14F);
        boolean elite = isEliteVariant(variant);
        leftForearm.roll += pulse * (elite ? 0.09F : 0.06F);
        rightForearm.roll -= pulse * (elite ? 0.09F : 0.06F);
        leftLowerLeg.yaw += pulse * 0.03F;
        rightLowerLeg.yaw -= pulse * 0.03F;
        eliteShoulderLeft.pitch += pulse * 0.035F;
        eliteShoulderRight.pitch -= pulse * 0.035F;
        eliteHornLeft.roll += pulse * 0.035F;
        eliteHornRight.roll -= pulse * 0.035F;
        chestRift.yScale = 1.0F + pulse * (elite ? 0.16F : 0.10F);
        guardCrest.yaw = pulse * (variant == Variant.RITUAL_GUARD ? 0.10F : 0.04F);
        guardChestSeal.yScale = 1.0F + pulse * (variant == Variant.WAVE_GUARDIAN ? 0.18F : 0.10F);
        guardianSpine.yScale = 1.0F + pulse * 0.08F;
        eliteMantle.roll = pulse * 0.025F;
        eliteHornCrown.pitch = pulse * 0.04F;
        head.yaw += MathHelper.clamp(headYaw * 0.017453292F, -0.45F, 0.45F);
        head.pitch += MathHelper.clamp(headPitch * 0.017453292F, -0.3F, 0.3F);
    }

    public boolean isElite() {
        return isEliteVariant(variant);
    }

    public Variant variant() {
        return variant;
    }

    public ModelPart getPart() {
        return root;
    }
}
