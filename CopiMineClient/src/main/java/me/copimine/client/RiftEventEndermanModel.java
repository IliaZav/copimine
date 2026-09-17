package me.copimine.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The event Enderman geometry is separate from vanilla's model.  The base
 * humanoid parts keep the renderer/feature contract, while the rift shell,
 * core, and variant crest are actual additional model parts rather than a
 * texture-only override.
 */
public final class RiftEventEndermanModel extends EndermanEntityModel<EndermanEntity> {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;

    public enum Variant {
        ORDINARY,
        ELITE,
        WAVE_GUARDIAN,
        RITUAL_GUARD,
        RITUAL_CASTER
    }

    private final ModelPart root;
    private final ModelPart riftCore;
    private final ModelPart riftShell;
    private final ModelPart variantCrest;
    private final ModelPart casterFocus;
    private final ModelPart bodyShell;
    private final ModelPart chestRift;
    private final ModelPart guardianMantle;
    private final ModelPart guardianSpine;
    private final ModelPart guardSeal;
    private final ModelPart hornLeft;
    private final ModelPart hornRight;
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftShin;
    private final ModelPart rightShin;
    private final Variant variant;

    public RiftEventEndermanModel(ModelPart root, boolean elite) {
        this(root, elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public RiftEventEndermanModel(ModelPart root, boolean elite, boolean caster) {
        this(root, caster ? Variant.RITUAL_CASTER
                : elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public RiftEventEndermanModel(ModelPart root, Variant variant) {
        super(root);
        this.root = root;
        this.riftCore = root.getChild("rift_core");
        this.riftShell = root.getChild("rift_shell");
        this.variantCrest = root.getChild("variant_crest");
        this.casterFocus = root.getChild("caster_focus");
        ModelPart body = root.getChild("body");
        this.bodyShell = body.getChild("body_shell");
        this.chestRift = body.getChild("chest_rift");
        this.guardianMantle = body.getChild("guardian_mantle");
        this.guardianSpine = body.getChild("guardian_spine");
        this.guardSeal = body.getChild("guard_seal");
        this.hornLeft = root.getChild("head").getChild("horn_left");
        this.hornRight = root.getChild("head").getChild("horn_right");
        this.leftForearm = root.getChild("left_arm").getChild("left_forearm");
        this.rightForearm = root.getChild("right_arm").getChild("right_forearm");
        this.leftShin = root.getChild("left_leg").getChild("left_shin");
        this.rightShin = root.getChild("right_leg").getChild("right_shin");
        this.variant = variant;
        this.casterFocus.visible = variant == Variant.RITUAL_CASTER;
        this.guardianMantle.visible = variant == Variant.WAVE_GUARDIAN;
        this.guardianSpine.visible = variant == Variant.WAVE_GUARDIAN;
        this.guardSeal.visible = variant == Variant.RITUAL_GUARD;
        this.hornLeft.visible = variant != Variant.ORDINARY && variant != Variant.RITUAL_CASTER;
        this.hornRight.visible = hornLeft.visible;
    }

    public static TexturedModelData getTexturedModelData(boolean elite) {
        return getTexturedModelData(elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static TexturedModelData getTexturedModelData(boolean elite, boolean caster) {
        return getTexturedModelData(caster ? Variant.RITUAL_CASTER
                : elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static TexturedModelData getTexturedModelData(Variant variant) {
        boolean elite = variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, -14.0F);
        ModelPartData root = data.getRoot();
        root.addChild("rift_core", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 0, -2.5F, -5.0F, -3.0F, 5.0F, 5.0F, 2.0F),
                ModelTransform.pivot(0.0F, -8.0F, -2.25F));
        root.addChild("rift_shell", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        16, 0, -4.5F, -1.0F, -2.5F, 9.0F, 3.0F, 5.0F),
                ModelTransform.pivot(0.0F, -10.0F, 0.0F));
        root.addChild("variant_crest", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        32, 0, -3.5F, -11.0F, -1.0F, 7.0F, elite ? 4.0F : 2.0F, 2.0F),
                ModelTransform.pivot(0.0F, -14.0F, 0.0F));
        root.addChild("caster_focus", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(40, 0, -2.0F, -1.0F, -3.2F,
                                4.0F, 4.0F, 1.0F),
                        new ModelUvBounds.Box(40, 5, -1.0F, 3.0F, -2.7F,
                                2.0F, 2.0F, 1.0F)),
                ModelTransform.pivot(0.0F, -3.0F, 0.0F));

        ModelPartData head = root.getChild("head");
        head.addChild("horn_left", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        48, 0, -3.2F, -9.0F, -1.25F, 2.0F, 8.0F, 2.5F),
                ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.12F));
        head.addChild("horn_right", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        48, 0, 1.2F, -9.0F, -1.25F, 2.0F, 8.0F, 2.5F),
                ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.12F));
        head.addChild("jaw_plate", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        50, 0, -3.0F, -0.4F, -4.15F, 6.0F, 1.3F, 0.45F),
                ModelTransform.NONE);

        ModelPartData body = root.getChild("body");
        body.addChild("body_shell", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 8, -4.25F, -0.4F, -2.35F, 8.5F, 11.0F, 4.7F),
                ModelTransform.NONE);
        body.addChild("chest_rift", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 8, -0.8F, 1.7F, -2.65F, 1.6F, 5.6F, 0.5F),
                ModelTransform.NONE);
        body.addChild("guardian_mantle", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(24, 8, -6.0F, -1.0F, -2.9F,
                                12.0F, 2.6F, 5.8F),
                        new ModelUvBounds.Box(24, 17, -5.0F, 1.0F, -2.7F,
                                10.0F, 1.5F, 5.4F)),
                ModelTransform.NONE);
        body.addChild("guardian_spine", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        40, 8, -1.0F, 1.0F, 2.05F, 2.0F, 9.0F, 0.6F),
                ModelTransform.NONE);
        body.addChild("guard_seal", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        46, 8, -1.9F, 5.0F, -2.75F, 3.8F, 3.8F, 0.5F),
                ModelTransform.NONE);

        ModelPartData leftArm = root.getChild("left_arm");
        leftArm.addChild("left_forearm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 18, -2.25F, 5.2F, -2.35F, 4.5F, 7.2F, 4.7F),
                ModelTransform.NONE);
        ModelPartData rightArm = root.getChild("right_arm");
        rightArm.addChild("right_forearm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 18, -2.25F, 5.2F, -2.35F, 4.5F, 7.2F, 4.7F, true),
                ModelTransform.NONE);

        ModelPartData leftLeg = root.getChild("left_leg");
        leftLeg.addChild("left_shin", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 20, -2.2F, 5.0F, -2.2F, 4.4F, 7.0F, 4.4F),
                ModelTransform.NONE);
        ModelPartData rightLeg = root.getChild("right_leg");
        rightLeg.addChild("right_shin", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 20, -2.2F, 5.0F, -2.2F, 4.4F, 7.0F, 4.4F, true),
                ModelTransform.NONE);
        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setAngles(EndermanEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        root.traverse().forEach(ModelPart::resetTransform);
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float pulse = MathHelper.sin(animationProgress * 0.16F);
        boolean empowered = variant != Variant.ORDINARY;
        riftCore.yScale = 1.0F + pulse * (empowered ? 0.16F : 0.10F);
        riftCore.xScale = 1.0F + pulse * 0.06F;
        riftCore.zScale = 1.0F + pulse * 0.06F;
        riftShell.pitch = pulse * 0.035F;
        riftShell.yaw = pulse * (empowered ? 0.10F : 0.06F);
        variantCrest.pitch = pulse * (empowered ? 0.16F : 0.08F);
        bodyShell.yaw = pulse * (variant == Variant.WAVE_GUARDIAN ? 0.035F : 0.018F);
        chestRift.yScale = 1.0F + pulse * (empowered ? 0.18F : 0.11F);
        guardianSpine.yScale = 1.0F + pulse * 0.08F;
        guardSeal.yaw = pulse * 0.12F;
        if (variant == Variant.RITUAL_CASTER) {
            // The server owns the cast/aggro state; this dedicated model pose
            // keeps the passive caster readable even while its AI is frozen.
            leftArm.pitch = -1.22F + pulse * 0.035F;
            rightArm.pitch = -1.22F - pulse * 0.035F;
            leftArm.roll = -0.12F;
            rightArm.roll = 0.12F;
            casterFocus.yaw = pulse * 0.14F;
            casterFocus.pitch = pulse * 0.08F;
        }
    }

    public boolean isElite() {
        return variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
    }

    public boolean isCaster() {
        return variant == Variant.RITUAL_CASTER;
    }

    public Variant variant() {
        return variant;
    }

    public ModelPart getPart() {
        return root;
    }
}
