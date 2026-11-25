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
        RITUAL_GUARD,
        WAVE7_MARKSMAN,
        SPIRE_HARBINGER
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
    private final ModelPart marksmanSight;
    private final ModelPart marksmanQuiver;
    private final ModelPart spireCrown;
    private final ModelPart spirePauldrons;
    private final ModelPart spireBow;
    private final Variant variant;

    public RiftEventSkeletonModel(ModelPart root, boolean elite) {
        this(root, elite ? Variant.ELITE : Variant.ORDINARY);
    }

    public RiftEventSkeletonModel(ModelPart root, Variant variant) {
        super(root);
        this.root = root;
        if (variant == Variant.WAVE7_MARKSMAN) EndRiftAtlasUvLayout.fit(root, "end_rift_wave7_marksman.png");
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
        this.marksmanSight = head.getChild("marksman_sight");
        this.marksmanQuiver = body.getChild("marksman_quiver");
        this.spireCrown = head.getChild("spire_crown");
        this.spirePauldrons = body.getChild("spire_pauldrons");
        this.spireBow = rightForearm.getChild("spire_bow");
        this.variant = variant;
        boolean elite = isEliteVariant(variant);
        boolean guardian = variant == Variant.WAVE_GUARDIAN;
        boolean ritual = variant == Variant.RITUAL_GUARD;
        boolean marksman = variant == Variant.WAVE7_MARKSMAN;
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
        this.marksmanSight.visible = marksman;
        this.marksmanQuiver.visible = marksman;
        boolean spire = variant == Variant.SPIRE_HARBINGER;
        this.spireCrown.visible = spire;
        this.spirePauldrons.visible = spire;
        this.spireBow.visible = spire;
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
        head.addOrReplaceChild("marksman_sight", cube(48, 16,
                        -2.0F, -10.0F, -4.25F, 4.0F, 2.0F, 0.75F),
                PartPose.ZERO);
        body.addOrReplaceChild("marksman_quiver", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(40, 20, 1.8F, 1F, 1.9F, 2.4F, 8F, 2F),
                        new ModelUvBounds.Box(45, 23, 1.48F, .45F, 1.7F, 3.1F, 1.2F, 2.45F),
                        new ModelUvBounds.Box(48, 16, 2F, -2.3F, 2.2F, .52F, 3F, 1F),
                        new ModelUvBounds.Box(48, 16, 3.25F, -3.2F, 2.8F, .52F, 3.85F, 1F)),
                PartPose.ZERO);
        head.addOrReplaceChild("spire_crown", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                new ModelUvBounds.Box(42, 0, -4.4F, -9F, -3F, 8.8F, 2F, 2F),
                new ModelUvBounds.Box(50, 4, -4.5F, -13F, -2.5F, 2F, 5F, 2F),
                new ModelUvBounds.Box(54, 4, 2.5F, -11.5F, -2.5F, 2F, 3.5F, 2F)),
                PartPose.ZERO);
        body.addOrReplaceChild("spire_pauldrons", ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                new ModelUvBounds.Box(36, 8, -7F, -.75F, -2.8F, 4F, 3F, 5.6F),
                new ModelUvBounds.Box(36, 8, 3F, -.75F, -2.8F, 4F, 4F, 5.6F),
                new ModelUvBounds.Box(52, 16, -1F, 2F, -2.8F, 2F, 7F, 1F)),
                PartPose.ZERO);
        root.getChild("right_arm").getChild("right_forearm").addOrReplaceChild("spire_bow",
                ModelUvBounds.boxes(TEXTURE_WIDTH, TEXTURE_HEIGHT,
                        new ModelUvBounds.Box(54, 19, -.8F, -2F, -3F, 1.6F, 11F, 1.6F),
                        new ModelUvBounds.Box(54, 20, -.5F, -4F, -2.4F, 1F, 3F, 3F),
                        new ModelUvBounds.Box(54, 20, -.5F, 8.5F, -2.4F, 1F, 3F, 3F),
                        new ModelUvBounds.Box(60, 10, -.2F, -2F, .25F, .4F, 11F, .4F)),
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
            case ORDINARY, ELITE, WAVE_GUARDIAN, RITUAL_GUARD, WAVE7_MARKSMAN, SPIRE_HARBINGER -> true;
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
        if (variant == Variant.WAVE7_MARKSMAN) {
            poseMarksman(state, animationProgress, walkSpeed);
            Wave7ActorAnimator.apply(root, state, false);
        }
        if (variant == Variant.SPIRE_HARBINGER) {
            spireCrown.zRot = Mth.sin(animationProgress * .09F) * .025F;
            spirePauldrons.yScale = 1F + Mth.sin(animationProgress * .1F) * .02F;
            if ("WAVE_WINDUP".equals(wavePose)) {
                rightArm.xRot = -1.45F + head.xRot;
                leftArm.xRot = -1.20F + head.xRot;
                leftForearm.xRot = -.75F;
                spireBow.zRot = .18F;
            } else if ("WAVE_RELEASE".equals(wavePose)) {
                rightArm.xRot = -1.62F + head.xRot;
                leftForearm.xRot = -.22F;
                spireBow.zRot = -.15F;
            }
        }
    }

    /**
     * Marksman rig: a real archer, not a mannequin holding a drawn bow.
     *
     * <p>The previous version wrote the draw pose with {@code =} on every frame
     * of every animation state, so the copy of the arms never moved: the
     * Marksman slid across the arena frozen in a firing stance and its shots
     * had no wind-up at all. The pose is now blended by intent — walk cycles
     * while running, a visible draw while loosing, and an idle sway otherwise —
     * and the marker it shoots at rides with the aim.</p>
     */
    private void poseMarksman(SkeletonRenderState state, float animationProgress, float walkSpeed) {
        EndRiftRenderStateAccess access = (EndRiftRenderStateAccess) state;
        String wavePose = access.copimine$wavePose();
        boolean drawing = access.copimine$trialFrame() == null && ("WAVE_WINDUP".equals(wavePose) || "WAVE_FROZEN".equals(wavePose));
        boolean recovering = access.copimine$trialFrame() == null && "WAVE_RECOVER".equals(wavePose);
        boolean loosing = access.copimine$trialFrame() == null && "WAVE_RELEASE".equals(wavePose);
        boolean hit = "WAVE_HIT".equals(wavePose);
        boolean dying = ((EndRiftRenderStateAccess) state).copimine$trialFrame() == null && "WAVE_DEATH".equals(wavePose);
        // The gait that super.setupAnim produced is the base layer; the draw is
        // added on top of it so a moving Marksman still visibly walks.
        float stride = Mth.clamp(walkSpeed, 0.0F, 1.0F);
        if (dying) {
            // Collapse: the bow arm goes slack, the spine folds and the sight
            // rides down with the head. An archer that dies still aiming is the
            // frozen-statue defect this rig was rebuilt to remove.
            float sag = Mth.sin(animationProgress * 0.20F) * 0.02F;
            leftArm.xRot = -0.28F;
            rightArm.xRot = -0.16F;
            leftArm.yRot = 0.06F;
            rightArm.yRot = -0.12F;
            leftArm.zRot = -0.28F;
            rightArm.zRot = 0.34F;
            body.xRot = 0.78F + sag;
            body.zRot = 0.16F;
            head.xRot = 0.44F;
            head.yRot = 0.26F;
            leftLeg.xRot = -0.62F;
            rightLeg.xRot = 0.30F;
            marksmanSight.yRot = 0.0F;
            marksmanSight.xRot = 0.30F;
            marksmanQuiver.xRot = 0.42F;
        } else if (hit) {
            // Flinch: a short recoil off the arrow that landed, then back to
            // the fight. It must not read as a stagger the fight never gave.
            float recoil = 1.0F + Mth.sin(animationProgress * 0.40F) * 0.10F;
            leftArm.xRot -= 0.20F * recoil;
            rightArm.xRot -= 0.24F * recoil;
            leftArm.yRot += 0.05F;
            rightArm.yRot -= 0.05F;
            body.xRot -= 0.12F * recoil;
            head.xRot -= 0.16F * recoil;
            body.yRot = 0.0F;
            marksmanSight.yRot = 0.0F;
            marksmanSight.xRot = 0.0F;
            marksmanQuiver.xRot = Mth.sin(animationProgress * 0.09F) * 0.03F - 0.10F;
        } else if (drawing) {
            leftArm.xRot = -1.42F + Mth.sin(animationProgress * 0.22F) * 0.03F;
            rightArm.xRot = -1.06F + Mth.cos(animationProgress * 0.31F) * 0.02F;
            leftArm.yRot = -0.14F;
            rightArm.yRot = 0.30F;
            // The bow hand steadies, the string hand trembles: that asymmetry is
            // what reads as strain rather than as a static model.
            rightArm.zRot = Mth.sin(animationProgress * 0.45F) * 0.035F;
            head.xRot += 0.05F;
            body.yRot = 0.06F;
        } else if (loosing) {
            // The single frame the arrow leaves: the draw snaps open, the
            // string hand kicks back and the whole body takes the recoil. This
            // is what a scripted Marksman was missing -- its shots arrived from
            // a model that never stopped aiming.
            leftArm.xRot = -1.52F;
            rightArm.xRot = -0.92F;
            leftArm.yRot = -0.10F;
            rightArm.yRot = 0.22F;
            rightArm.zRot = 0.10F;
            head.xRot -= 0.04F;
            body.yRot = 0.02F;
        } else if (recovering) {
            // Follow-through: the draw releases and the arms drop past neutral.
            leftArm.xRot = -0.55F + stride * Mth.cos(animationProgress * 0.5F) * 0.25F;
            rightArm.xRot = -0.15F - stride * Mth.sin(animationProgress * 0.5F) * 0.25F;
            leftArm.yRot = -0.05F;
            rightArm.yRot = 0.10F;
            head.xRot += 0.02F;
            body.yRot = 0.0F;
        } else {
            // Idle and moving: keep whatever gait vanilla built, add only the
            // archer's habitual lean and a slow aim scan.
            float scan = Mth.sin(animationProgress * 0.045F);
            leftArm.xRot -= 0.10F;
            rightArm.xRot -= 0.06F;
            leftArm.yRot += scan * 0.03F;
            rightArm.yRot += scan * 0.02F;
            head.yRot += scan * 0.06F;
            body.yRot += scan * 0.02F;
        }
        marksmanSight.yRot = Mth.sin(animationProgress * 0.07F) * 0.025F;
        marksmanSight.xRot = drawing ? -0.10F : 0.0F;
        marksmanQuiver.xRot = Mth.sin(animationProgress * 0.09F) * 0.03F + stride * 0.08F;
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
