package me.copimine.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Articulated End Rift skeleton rig used by the ordinary and elite wave roles.
 *
 * <p>The supplied 64x32 End Rift atlas uses compact authored UV islands rather
 * than the vanilla skin layout. Keep readable biped proportions, but map each
 * part to those authored islands; splitting the limbs at their atlas seams
 * preserves normal walk articulation without stretching the texture.</p>
 */
public final class RiftEventSkeletonModel extends SkeletonModel<SkeletonRenderState> {
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
        ModelPart body = root.getChild("body");
        this.chestRift = body.getChild("chest_rift");
        this.guardCrest = head.getChild("guard_crest");
        this.guardChestSeal = body.getChild("guard_chest_seal");
        this.guardianSpine = body.getChild("guardian_spine");
        this.eliteMantle = body.getChild("elite_mantle");
        this.eliteHornCrown = head.getChild("elite_horn_crown");
        this.variant = variant;
        boolean elite = isEliteVariant(variant);
        boolean guardian = variant == Variant.WAVE_GUARDIAN;
        boolean ritual = variant == Variant.RITUAL_GUARD;
        boolean userSkin = usesSharedSkeletonAtlas(variant);
        this.eliteHornLeft.visible = elite;
        this.eliteHornRight.visible = elite;
        this.eliteShoulderLeft.visible = elite && !userSkin;
        this.eliteShoulderRight.visible = elite && !userSkin;
        this.guardCrest.visible = guardian || ritual;
        this.guardChestSeal.visible = guardian || ritual;
        this.guardianSpine.visible = guardian;
        this.chestRift.visible = !userSkin;
        this.eliteMantle.visible = elite && !userSkin;
        this.eliteHornCrown.visible = elite && !userSkin;
        // Unused atlas islands are opaque; the vanilla hat layer would cover
        // the skull with an unrelated patch.
        this.hat.visible = false;
    }

    public static LayerDefinition getTexturedModelData(boolean elite) {
        return getTexturedModelData(elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public static LayerDefinition getTexturedModelData(Variant variant) {
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = data.getRoot();
        PartDefinition head = root.getChild("head");
        PartDefinition body = root.getChild("body");
        // Ordinary and elite skeletons use their respective supplied 64x32
        // atlases, which share the classic torso/limb UV layout. Guardian and
        // ritual variants keep their separate authored UV islands.
        boolean userSkin = usesSharedSkeletonAtlas(variant);
        int bodyUvV = userSkin ? 16 : 0;
        int leftHornU = userSkin ? 0 : 32;
        int rightHornU = userSkin ? 0 : 48;
        int hornV = userSkin ? 16 : 8;
        // The supplied ordinary and elite skin share the (16, 16) torso island.
        // Special-role atlases retain their already-authored (16, 0) location.
        root.addOrReplaceChild("body", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        16, bodyUvV, -4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body = root.getChild("body");
        addBipedSkeletonArm(root, "left_arm", "left_forearm", true, userSkin);
        addBipedSkeletonArm(root, "right_arm", "right_forearm", false, userSkin);
        addBipedSkeletonLeg(root, "left_leg", "left_lower_leg", true, userSkin);
        addBipedSkeletonLeg(root, "right_leg", "right_lower_leg", false, userSkin);
        head.addOrReplaceChild("elite_horn_left", cube(leftHornU, hornV,
                        -3.0F, -10.5F, -1.0F, 1.2F, 3.5F, 1.2F),
                PartPose.ZERO);
        head.addOrReplaceChild("elite_horn_right", ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        rightHornU, hornV, 1.8F, -10.5F, -1.0F, 1.2F, 3.5F, 1.2F, true),
                PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("elite_shoulder_left", cube(40, 16,
                        -1.5F, -2.25F, -1.0F, 3.0F, 3.0F, 2.0F),
                PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("elite_shoulder_right",
                ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        50, 16, -1.5F, -2.25F, -1.0F, 3.0F, 3.0F, 2.0F, true),
                PartPose.ZERO);
        body.addOrReplaceChild("chest_rift", cube(24, 17,
                        -0.5F, 3.0F, -2.35F, 1.0F, 6.0F, 0.5F),
                PartPose.ZERO);
        // BipedEntityModel renders through its head/body parts. Keep all
        // decorative geometry beneath those rendered bones instead of as
        // root siblings, which ModelPart traversal never draws here.
        head.addOrReplaceChild("guard_crest", cube(40, 16,
                        -2.0F, -10.0F, -4.05F, 4.0F, 2.5F, 0.5F),
                PartPose.ZERO);
        body.addOrReplaceChild("guard_chest_seal", cube(24, 17,
                        -1.5F, 8.0F, -2.55F, 3.0F, 3.0F, 0.5F),
                PartPose.ZERO);
        body.addOrReplaceChild("guardian_spine", cube(40, 16,
                        -1.0F, 2.0F, 2.05F, 2.0F, 8.0F, 0.5F),
                PartPose.ZERO);
        body.addOrReplaceChild("elite_mantle", cube(40, 16,
                        -3.5F, -0.5F, -2.0F, 7.0F, 2.0F, 4.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("elite_horn_crown", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(40, 16, -3.0F, -10.0F, -1.0F,
                                1.0F, 3.0F, 1.0F),
                        new ModelUvBounds.Box(48, 16, -0.5F, -11.0F, -1.0F,
                                1.0F, 2.0F, 1.0F),
                        new ModelUvBounds.Box(56, 16, 2.0F, -10.0F, -1.0F,
                                1.0F, 3.0F, 1.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addBipedSkeletonArm(PartDefinition root, String name,
                                             String forearmName, boolean mirrored,
                                             boolean userSkin) {
        // The supplied 64x32 skeleton atlases put both arm islands at U=40;
        // U=0 belongs to a leg, which made cyan/elite archers' arms look
        // transparent or like unfinished legs.
        int uv = userSkin ? 40 : mirrored ? 48 : 32;
        int upperV = userSkin ? 16 : 0;
        // Each half is six pixels tall. Advance by six UV rows so the two
        // halves cover the original twelve-pixel arm island without a gap.
        int lowerV = upperV + 6;
        CubeListBuilder upper = ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                uv, upperV, -1.0F, -2.0F, -1.0F, 2.0F, 6.0F, 2.0F, mirrored);
        PartDefinition arm = root.addOrReplaceChild(name, upper,
                PartPose.offset(mirrored ? 5.0F : -5.0F, 2.0F, 0.0F));
        CubeListBuilder lower = ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                uv, lowerV, -1.0F, -2.0F, -1.0F, 2.0F, 6.0F, 2.0F, mirrored);
        arm.addOrReplaceChild(forearmName, lower, PartPose.offset(0.0F, 6.0F, 0.0F));
    }

    private static void addBipedSkeletonLeg(PartDefinition root, String name,
                                             String lowerLegName, boolean mirrored,
                                             boolean userSkin) {
        int uv = userSkin ? 0 : mirrored ? 8 : 0;
        int upperV = 16;
        int lowerV = upperV + 6;
        CubeListBuilder upper = ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                uv, upperV, -1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, mirrored);
        PartDefinition leg = root.addOrReplaceChild(name, upper,
                PartPose.offset(mirrored ? 2.0F : -2.0F, 12.0F, 0.0F));
        CubeListBuilder lower = ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                uv, lowerV, -1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, mirrored);
        leg.addOrReplaceChild(lowerLegName, lower, PartPose.offset(0.0F, 6.0F, 0.0F));
    }

    private static boolean isEliteVariant(Variant variant) {
        return variant == Variant.ELITE || variant == Variant.WAVE_GUARDIAN
                || variant == Variant.RITUAL_GUARD;
    }

    private static boolean usesSharedSkeletonAtlas(Variant variant) {
        return switch (variant) {
            case ORDINARY, ELITE, WAVE_GUARDIAN, RITUAL_GUARD -> true;
        };
    }

    private static CubeListBuilder cube(int u, int v, float x, float y, float z,
                                         float width, float height, float depth) {
        return ModelUvBounds.cuboid(TEXTURE_WIDTH, TEXTURE_HEIGHT, u, v,
                x, y, z, width, height, depth);
    }

    @Override
    public void setupAnim(SkeletonRenderState state) {
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

        float pulse = Mth.sin(animationProgress * 0.14F);
        boolean elite = isEliteVariant(variant);
        leftForearm.zRot += pulse * (elite ? 0.09F : 0.06F);
        rightForearm.zRot -= pulse * (elite ? 0.09F : 0.06F);
        leftLowerLeg.yRot += pulse * 0.03F;
        rightLowerLeg.yRot -= pulse * 0.03F;
        eliteShoulderLeft.xRot += pulse * 0.035F;
        eliteShoulderRight.xRot -= pulse * 0.035F;
        eliteHornLeft.zRot += pulse * 0.035F;
        eliteHornRight.zRot -= pulse * 0.035F;
        chestRift.yScale = 1.0F + pulse * (elite ? 0.16F : 0.10F);
        guardCrest.yRot = pulse * (variant == Variant.RITUAL_GUARD ? 0.10F : 0.04F);
        guardChestSeal.yScale = 1.0F + pulse * (variant == Variant.WAVE_GUARDIAN ? 0.18F : 0.10F);
        guardianSpine.yScale = 1.0F + pulse * 0.08F;
        eliteMantle.zRot = pulse * 0.025F;
        eliteHornCrown.xRot = pulse * 0.04F;
        if ("WAVE_WINDUP".equals(wavePose) || "WAVE_FROZEN".equals(wavePose)) {
            leftArm.xRot=-1.45F; rightArm.xRot=-1.45F;
        } else if ("WAVE_RECOVER".equals(wavePose)) {
            leftArm.xRot=.2F; rightArm.xRot=.2F; head.xRot+=.15F;
        }
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
