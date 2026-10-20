package me.copimine.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.util.Mth;

/** Compact custom Evoker rig; the asymmetrical raised-arm pose reads clearly while casting. */
public final class Wave7ArchmageModel extends EntityModel<EvokerRenderState> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart hoodRune;
    private final ModelPart focusOrb;

    public Wave7ArchmageModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.hoodRune = head.getChild("rift_hood_rune");
        this.focusOrb = rightArm.getChild("rift_focus_orb");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("rift_hood",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 12.0F, 8.0F,
                                new CubeDeformation(0.35F)),
                PartPose.ZERO);
        head.addOrReplaceChild("rift_hood_rune",
                CubeListBuilder.create().texOffs(56, 0)
                        .addBox(-1.0F, -13.0F, -2.0F, 2.0F, 3.0F, 1.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(24, 0)
                        .addBox(-1.0F, -1.0F, -5.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 20)
                        .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("rift_robe",
                CubeListBuilder.create().texOffs(16, 38)
                        .addBox(-5.0F, 6.0F, -3.8F, 10.0F, 8.0F, 7.6F),
                PartPose.ZERO);
        body.addOrReplaceChild("rift_seal",
                CubeListBuilder.create().texOffs(48, 20)
                        .addBox(-2.0F, 2.0F, -3.35F, 4.0F, 5.0F, 0.5F),
                PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 22)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("rift_focus_staff",
                CubeListBuilder.create().texOffs(56, 38)
                        .addBox(-0.5F, 4.0F, -0.5F, 1.0F, 18.0F, 1.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("rift_focus_orb",
                CubeListBuilder.create().texOffs(48, 42)
                        .addBox(-2.0F, 2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 3.0F, 0.0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 38)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 22)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 38)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(EvokerRenderState state) {
        float walk = state.walkAnimationPos * 0.6662F;
        float speed = state.walkAnimationSpeed;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        body.xRot = 0.0F;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 1.4F * speed;
        leftArm.xRot = Mth.cos(walk) * 1.4F * speed;
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightLeg.xRot = Mth.cos(walk) * 1.2F * speed;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.2F * speed;
        hoodRune.yRot = Mth.sin(state.ageInTicks * 0.08F) * 0.07F;

        if (state.isCastingSpell) {
            float spellPulse = Mth.sin(state.ageInTicks * 0.32F) * 0.08F;
            rightArm.xRot = -2.15F + spellPulse;
            rightArm.zRot = -0.34F;
            leftArm.xRot = -1.24F - spellPulse;
            leftArm.zRot = 0.42F;
            body.xRot = 0.08F;
            head.xRot -= 0.12F;
            focusOrb.yScale = 1.0F + Math.abs(spellPulse) * 2.2F;
        } else {
            rightArm.xRot -= 0.12F;
            leftArm.xRot -= 0.12F;
            focusOrb.yScale = 1.0F;
        }
    }
}
