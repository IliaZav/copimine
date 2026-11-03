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

/**
 * Custom Evoker rig for the Wave 7 Archmage.
 *
 * <p>Deliberately not a re-skin of the vanilla illager: the Archmage is the only
 * trial whose body is a spellcaster, so the silhouette carries a hood, a
 * floating focus orb, a hovering sigil ring and a robe that opens in the
 * middle. The arms are the spell casters' own — while a spell winds up the
 * staff hand rises and the free hand pulls a rune circle open.</p>
 */
public final class Wave7ArchmageModel extends EntityModel<EvokerRenderState> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart hoodRune;
    private final ModelPart focusOrb;
    private final ModelPart sigilRing;
    private final ModelPart robeHemis;
    private final ModelPart leftRuneHand;

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
        this.sigilRing = rightArm.getChild("rift_sigil_ring");
        this.robeHemis = body.getChild("rift_robe");
        this.leftRuneHand = leftArm.getChild("rift_rune_hand");
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
        // A flat ring around the staff head: the readable "this is a spellcaster"
        // cue at medium range, and the piece that grows while a spell winds up.
        rightArm.addOrReplaceChild("rift_sigil_ring",
                CubeListBuilder.create().texOffs(56, 44)
                        .addBox(-4.0F, 1.5F, -4.0F, 8.0F, 0.4F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(40, 38)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("rift_rune_hand",
                CubeListBuilder.create().texOffs(0, 52)
                        .addBox(-1.5F, 10.0F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.ZERO);

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

    /**
     * Three-layer animation: a real walk, a spell channel, and an idle mantle.
     *
     * <p>The previous rig zeroed both arms on the spell pose and then reset
     * {@code zRot} to zero every frame, so an idle Archmage walked with both
     * arms locked to its sides and every cast looked identical. The arms now
     * keep the gait underneath and add the channel on top, so a moving caster
     * still visibly strides.</p>
     */
    @Override
    public void setupAnim(EvokerRenderState state) {
        float walk = state.walkAnimationPos * 0.6662F;
        float speed = state.walkAnimationSpeed;
        float age = state.ageInTicks;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        body.xRot = 0.0F;

        // Base layer: a slightly heavier gait than a villager, because the
        // Archmage is taller, plus a slower cadence.
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 1.4F * speed;
        leftArm.xRot = Mth.cos(walk) * 1.4F * speed;
        rightArm.zRot = Mth.cos(walk) * 0.05F * speed;
        leftArm.zRot = -Mth.cos(walk) * 0.05F * speed;
        rightLeg.xRot = Mth.cos(walk) * 1.2F * speed;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.2F * speed;
        hoodRune.yRot = Mth.sin(age * 0.08F) * 0.07F;

        if (state.isCastingSpell) {
            float spellPulse = Mth.sin(age * 0.32F) * 0.08F;
            float channel = Mth.sin(age * 0.18F) * 0.045F;
            // Channel overlays the gait: the staff hand rises in front of the
            // chest, the free hand opens the rune circle beside it.
            rightArm.xRot = -2.15F + spellPulse;
            rightArm.zRot = -0.34F + channel;
            leftArm.xRot = -1.24F - spellPulse;
            leftArm.zRot = 0.42F - channel;
            body.xRot = 0.08F;
            head.xRot -= 0.12F;
            focusOrb.yScale = 1.0F + Math.abs(spellPulse) * 2.2F;
            focusOrb.xScale = 1.0F + Math.abs(spellPulse) * 1.4F;
            focusOrb.zScale = focusOrb.xScale;
            // The ring opens only during a channel, and closes between casts, so
            // "ring visible" is a reliable read of "a spell is coming".
            sigilRing.yScale = 1.0F + Math.abs(spellPulse) * 3.4F;
            sigilRing.yRot = age * 0.22F;
            sigilRing.visible = true;
            leftRuneHand.yScale = 1.0F + Math.abs(channel) * 6.0F;
        } else {
            rightArm.xRot -= 0.12F;
            leftArm.xRot -= 0.12F;
            focusOrb.yScale = 1.0F;
            focusOrb.xScale = 1.0F;
            focusOrb.zScale = 1.0F;
            sigilRing.visible = false;
            sigilRing.yRot = 0.0F;
            sigilRing.yScale = 1.0F;
            leftRuneHand.yScale = 1.0F;
        }
        // The robe always trails the stride a little; it is the cheapest cue that
        // the model has weight rather than being a stick figure.
        robeHemis.xRot = -0.05F * speed * Mth.cos(walk);
    }
}
