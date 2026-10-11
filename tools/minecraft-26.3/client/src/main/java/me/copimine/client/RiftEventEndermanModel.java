package me.copimine.client;

import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The event Enderman geometry is separate from vanilla's model.  The base
 * humanoid parts keep the renderer/feature contract, while the rift shell,
 * core, and variant crest are actual additional model parts rather than a
 * texture-only override.
 */
public final class RiftEventEndermanModel extends EndermanModel<EndermanRenderState> {
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
        ModelPart body = root.getChild("body");
        this.casterFocus = body.getChild("caster_focus");
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
        boolean armored = variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
        this.riftCore.visible = false;
        this.riftShell.visible = false;
        this.variantCrest.visible = false;
        this.casterFocus.visible = variant == Variant.RITUAL_CASTER;
        this.bodyShell.visible = armored;
        this.chestRift.visible = variant != Variant.ORDINARY;
        this.leftForearm.visible = armored;
        this.rightForearm.visible = armored;
        this.leftShin.visible = armored;
        this.rightShin.visible = armored;
        root.getChild("head").getChild("jaw_plate").visible = armored;
        this.guardianMantle.visible = variant == Variant.WAVE_GUARDIAN;
        this.guardianSpine.visible = variant == Variant.WAVE_GUARDIAN;
        this.guardSeal.visible = variant == Variant.RITUAL_GUARD;
        this.hornLeft.visible = armored;
        this.hornRight.visible = hornLeft.visible;
    }

    public static LayerDefinition getTexturedModelData(boolean elite) {
        return getTexturedModelData(elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static LayerDefinition getTexturedModelData(boolean elite, boolean caster) {
        return getTexturedModelData(caster ? Variant.RITUAL_CASTER
                : elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static LayerDefinition getTexturedModelData(Variant variant) {
        boolean elite = variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, -14.0F);
        PartDefinition root = data.getRoot();
        // The supplied 64x32 enderman skin is laid out for the vanilla
        // 30-pixel arms and legs. BipedEntityModel's human-length cuboids
        // shrink the figure and sample unrelated atlas regions.
        root.addOrReplaceChild("hat", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 16, -4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,
                        new CubeDeformation(-0.5F)),
                PartPose.offset(0.0F, -13.0F, 0.0F));
        root.addOrReplaceChild("head", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 0, -4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, -13.0F, 0.0F));
        root.addOrReplaceChild("body", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        32, 16, -4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, -14.0F, 0.0F));
        root.addOrReplaceChild("right_arm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        56, 0, -1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F),
                PartPose.offset(-5.0F, -12.0F, 0.0F));
        root.addOrReplaceChild("left_arm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        56, 0, -1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F, true),
                PartPose.offset(5.0F, -12.0F, 0.0F));
        root.addOrReplaceChild("right_leg", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        56, 0, -1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F),
                PartPose.offset(-2.0F, -5.0F, 0.0F));
        root.addOrReplaceChild("left_leg", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        56, 0, -1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F, true),
                PartPose.offset(2.0F, -5.0F, 0.0F));
        PartDefinition head = root.getChild("head");
        PartDefinition body = root.getChild("body");
        root.addOrReplaceChild("rift_core", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 0, -2.5F, -5.0F, -3.0F, 5.0F, 5.0F, 2.0F),
                PartPose.offset(0.0F, -8.0F, -2.25F));
        root.addOrReplaceChild("rift_shell", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        16, 0, -4.5F, -1.0F, -2.5F, 9.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, -10.0F, 0.0F));
        root.addOrReplaceChild("variant_crest", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        32, 0, -3.5F, -11.0F, -1.0F, 7.0F, elite ? 4.0F : 2.0F, 2.0F),
                PartPose.offset(0.0F, -14.0F, 0.0F));
        // EndermanEntityModel only traverses its standard head/body/limb
        // parts. Attach the focus to the rendered torso and compensate for
        // the torso pivot so its authored world position remains unchanged.
        body.addOrReplaceChild("caster_focus", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(40, 0, -2.0F, -1.0F, -3.2F,
                                4.0F, 4.0F, 1.0F),
                        new ModelUvBounds.Box(40, 5, -1.0F, 3.0F, -2.7F,
                                2.0F, 2.0F, 1.0F)),
                PartPose.offset(0.0F, 11.0F, 0.0F));

        head.addOrReplaceChild("horn_left", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        48, 0, -3.2F, -9.0F, -1.25F, 2.0F, 8.0F, 2.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.12F));
        head.addOrReplaceChild("horn_right", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        48, 0, 1.2F, -9.0F, -1.25F, 2.0F, 8.0F, 2.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.12F));
        head.addOrReplaceChild("jaw_plate", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        50, 30, -3.0F, -0.4F, -4.15F, 6.0F, 1.3F, 0.45F),
                PartPose.ZERO);

        body.addOrReplaceChild("body_shell", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 8, -4.25F, -0.4F, -2.35F, 8.5F, 11.0F, 4.7F),
                PartPose.ZERO);
        body.addOrReplaceChild("chest_rift", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 8, -0.8F, 1.7F, -2.65F, 1.6F, 5.6F, 0.5F),
                PartPose.ZERO);
        body.addOrReplaceChild("guardian_mantle", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(24, 8, -6.0F, -1.0F, -2.9F,
                                12.0F, 2.6F, 5.8F),
                        new ModelUvBounds.Box(24, 17, -5.0F, 1.0F, -2.7F,
                                10.0F, 1.5F, 5.4F)),
                PartPose.ZERO);
        body.addOrReplaceChild("guardian_spine", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        40, 8, -1.0F, 1.0F, 2.05F, 2.0F, 9.0F, 0.6F),
                PartPose.ZERO);
        body.addOrReplaceChild("guard_seal", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        46, 8, -1.9F, 5.0F, -2.75F, 3.8F, 3.8F, 0.5F),
                PartPose.ZERO);

        PartDefinition leftArm = root.getChild("left_arm");
        leftArm.addOrReplaceChild("left_forearm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 18, -1.25F, 18.0F, -1.25F, 2.5F, 8.0F, 2.5F),
                PartPose.ZERO);
        PartDefinition rightArm = root.getChild("right_arm");
        rightArm.addOrReplaceChild("right_forearm", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        0, 18, -1.25F, 18.0F, -1.25F, 2.5F, 8.0F, 2.5F, true),
                PartPose.ZERO);

        PartDefinition leftLeg = root.getChild("left_leg");
        leftLeg.addOrReplaceChild("left_shin", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 16, -1.2F, 17.0F, -1.2F, 2.4F, 10.0F, 2.4F),
                PartPose.ZERO);
        PartDefinition rightLeg = root.getChild("right_leg");
        rightLeg.addOrReplaceChild("right_shin", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        18, 16, -1.2F, 17.0F, -1.2F, 2.4F, 10.0F, 2.4F, true),
                PartPose.ZERO);
        return LayerDefinition.create(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setupAnim(EndermanRenderState state) {
        root.getAllParts().forEach(ModelPart::resetPose);
        String wavePose = ((EndRiftRenderStateAccess) state).copimine$wavePose();
        boolean frozen = "WAVE_FROZEN".equals(wavePose);
        float walkPosition = state.walkAnimationPos;
        float walkSpeed = state.walkAnimationSpeed;
        if (frozen) { state.walkAnimationPos = 0; state.walkAnimationSpeed = 0; }
        try {
            super.setupAnim(state);
        } finally {
            if (frozen) { state.walkAnimationPos = walkPosition; state.walkAnimationSpeed = walkSpeed; }
        }
        float animationProgress = frozen ? 0 : state.ageInTicks;

        float pulse = Mth.sin(animationProgress * 0.16F);
        boolean empowered = variant != Variant.ORDINARY;
        riftCore.yScale = 1.0F + pulse * (empowered ? 0.16F : 0.10F);
        riftCore.xScale = 1.0F + pulse * 0.06F;
        riftCore.zScale = 1.0F + pulse * 0.06F;
        riftShell.xRot = pulse * 0.035F;
        riftShell.yRot = pulse * (empowered ? 0.10F : 0.06F);
        variantCrest.xRot = pulse * (empowered ? 0.16F : 0.08F);
        bodyShell.yRot = pulse * (variant == Variant.WAVE_GUARDIAN ? 0.035F : 0.018F);
        chestRift.yScale = 1.0F + pulse * (empowered ? 0.18F : 0.11F);
        guardianSpine.yScale = 1.0F + pulse * 0.08F;
        guardSeal.yRot = pulse * 0.12F;
        if (variant == Variant.RITUAL_CASTER) {
            // No-AI casters cannot rely on vanilla anger to keep their pose.
            // Use the phase carried by their server-owned entity binding.
            String phase = ((EndRiftRenderStateAccess) state).copimine$eventAnimation();
            if (isChannelingPhase(phase, state.isCreepy)) {
                applyChannelingPose(pulse);
            } else {
                leftArm.xRot += pulse * 0.02F;
                rightArm.xRot -= pulse * 0.02F;
                leftArm.zRot = 0.0F;
                rightArm.zRot = 0.0F;
            }
            casterFocus.yRot = pulse * 0.14F;
            casterFocus.xRot = pulse * 0.08F;
        }
        if ("WAVE_WINDUP".equals(wavePose) || "WAVE_FROZEN".equals(wavePose)) {
            leftArm.xRot = -1.25F; rightArm.xRot = -1.25F;
            leftArm.zRot = -.25F; rightArm.zRot = .25F;
        } else if ("WAVE_RECOVER".equals(wavePose)) {
            leftArm.xRot = .25F; rightArm.xRot = .25F; head.xRot += .18F;
        }
    }

    void applyChannelingPose(float pulse) {
        leftArm.xRot = -2.62F + pulse * 0.035F;
        rightArm.xRot = -2.62F - pulse * 0.035F;
        leftArm.zRot = -0.18F;
        rightArm.zRot = 0.18F;
    }

    static boolean isChannelingPhase(String phase, boolean vanillaAngry) {
        return switch (phase == null ? "" : phase) {
            case "RITUAL_CHANNEL", "RITUAL_WINDUP", "RITUAL_RELEASE" -> true;
            case "RITUAL_COMBAT" -> false;
            default -> vanillaAngry;
        };
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
