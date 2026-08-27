package me.copimine.client;

import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.MathHelper;

public final class RiftGuardianModel extends SinglePartEntityModel<EndermanEntity> {
    private final ModelPart root;
    private final ModelPart torso;
    private final ModelPart leftShoulder;
    private final ModelPart rightShoulder;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftHorn;
    private final ModelPart rightHorn;
    private final ModelPart leftShard;
    private final ModelPart rightShard;
    private final ModelPart chestRift;
    private Phase phase = Phase.AWAKENING;
    private long transitionDurationMillis;

    public RiftGuardianModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.leftShoulder = root.getChild("left_shoulder");
        this.rightShoulder = root.getChild("right_shoulder");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftHorn = root.getChild("left_horn");
        this.rightHorn = root.getChild("right_horn");
        this.leftShard = root.getChild("left_shard");
        this.rightShard = root.getChild("right_shard");
        this.chestRift = root.getChild("chest_rift");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("torso", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-9.0F, -25.0F, -5.0F, 18.0F, 21.0F, 10.0F)
                        .uv(0, 64).cuboid(-6.0F, -31.0F, -4.0F, 12.0F, 7.0F, 8.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("left_shoulder", ModelPartBuilder.create()
                        .uv(56, 0).cuboid(0.0F, -23.0F, -4.0F, 9.0F, 7.0F, 8.0F),
                ModelTransform.pivot(8.5F, 24.0F, 0.0F));
        root.addChild("right_shoulder", ModelPartBuilder.create()
                        .uv(56, 15).cuboid(-9.0F, -23.0F, -4.0F, 9.0F, 7.0F, 8.0F),
                ModelTransform.pivot(-8.5F, 24.0F, 0.0F));
        root.addChild("left_arm", ModelPartBuilder.create()
                        .uv(88, 0).cuboid(-2.0F, -1.0F, -2.5F, 5.0F, 25.0F, 5.0F),
                ModelTransform.pivot(16.0F, 1.5F, 0.0F));
        root.addChild("right_arm", ModelPartBuilder.create()
                        .uv(88, 30).cuboid(-3.0F, -1.0F, -2.5F, 5.0F, 25.0F, 5.0F),
                ModelTransform.pivot(-16.0F, 1.5F, 0.0F));
        root.addChild("left_horn", ModelPartBuilder.create()
                        .uv(42, 64).cuboid(0.0F, -36.0F, -2.0F, 4.0F, 9.0F, 4.0F),
                ModelTransform.of(4.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.35F));
        root.addChild("right_horn", ModelPartBuilder.create()
                        .uv(42, 77).cuboid(-4.0F, -36.0F, -2.0F, 4.0F, 9.0F, 4.0F),
                ModelTransform.of(-4.0F, 24.0F, 0.0F, 0.0F, 0.0F, -0.35F));
        root.addChild("left_shard", ModelPartBuilder.create()
                        .uv(0, 84).cuboid(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                ModelTransform.of(14.0F, -3.0F, -1.0F, 0.25F, 0.0F, 0.65F));
        root.addChild("right_shard", ModelPartBuilder.create()
                        .uv(14, 84).cuboid(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                ModelTransform.of(-14.0F, -3.0F, -1.0F, 0.25F, 0.0F, -0.65F));
        root.addChild("chest_rift", ModelPartBuilder.create()
                        .uv(28, 84).cuboid(-3.0F, -20.5F, -5.6F, 6.0F, 10.0F, 1.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        return TexturedModelData.of(modelData, 128, 128);
    }

    public void setPhase(Phase phase, long transitionDurationMillis) {
        this.phase = phase == null ? Phase.AWAKENING : phase;
        this.transitionDurationMillis = MathHelper.clamp(transitionDurationMillis, 0L, 600_000L);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(EndermanEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        root.traverse().forEach(ModelPart::resetTransform);
        float walk = MathHelper.clamp(limbDistance, 0.0F, 1.0F);
        float transition = MathHelper.clamp(transitionDurationMillis / 600_000.0F, 0.0F, 1.0F);
        applyIdleTransform(animationProgress, transition);
        applyWalkTransform(limbAngle, walk);
        if (entity != null && entity.isAngry()) {
            applyAttackTransform(animationProgress);
        }
        if (phase == Phase.ABSORPTION) {
            applyAbsorptionTransform(animationProgress);
        } else if (phase == Phase.CATASTROPHE) {
            applyJudgmentTransform(animationProgress);
        } else if (phase == Phase.DISTORTION) {
            root.roll += MathHelper.sin(animationProgress * 0.12F) * 0.08F;
            leftShard.yaw += 0.35F;
            rightShard.yaw -= 0.35F;
        } else if (phase == Phase.HUNTER) {
            leftArm.pitch -= 0.25F;
            rightArm.pitch -= 0.25F;
        }
        root.yaw = MathHelper.clamp(headYaw * 0.017453292F, -0.5F, 0.5F);
        torso.pitch += MathHelper.clamp(headPitch * 0.017453292F, -0.35F, 0.35F);
    }

    private void applyIdleTransform(float animationProgress, float transition) {
        float idle = MathHelper.sin(animationProgress * 0.08F);
        torso.pivotY += idle * 0.45F;
        chestRift.zScale = 1.0F + MathHelper.clamp(idle * 0.08F + transition * 0.12F, -0.08F, 0.18F);
        leftHorn.roll += idle * 0.04F;
        rightHorn.roll -= idle * 0.04F;
        leftShard.pivotY += MathHelper.sin(animationProgress * 0.11F) * 0.9F;
        rightShard.pivotY += MathHelper.cos(animationProgress * 0.11F) * 0.9F;
    }

    private void applyWalkTransform(float limbAngle, float walk) {
        float swing = MathHelper.sin(limbAngle * 0.6662F) * walk;
        leftArm.pitch += MathHelper.clamp(swing * 0.65F, -0.55F, 0.55F);
        rightArm.pitch -= MathHelper.clamp(swing * 0.65F, -0.55F, 0.55F);
        leftShoulder.roll += MathHelper.clamp(swing * 0.08F, -0.1F, 0.1F);
        rightShoulder.roll -= MathHelper.clamp(swing * 0.08F, -0.1F, 0.1F);
    }

    private void applyAttackTransform(float animationProgress) {
        float strike = MathHelper.sin(animationProgress * 0.35F) * 0.25F + 0.35F;
        leftArm.pitch -= MathHelper.clamp(strike, 0.0F, 0.65F);
        rightArm.pitch -= MathHelper.clamp(strike, 0.0F, 0.65F);
        torso.pitch += 0.12F;
    }

    private void applyAbsorptionTransform(float animationProgress) {
        float pull = MathHelper.sin(animationProgress * 0.18F) * 0.2F;
        leftArm.roll -= 0.35F + pull;
        rightArm.roll += 0.35F + pull;
        leftShard.pitch += 0.5F;
        rightShard.pitch += 0.5F;
        chestRift.xScale = 1.18F;
        chestRift.yScale = 1.12F;
    }

    private void applyJudgmentTransform(float animationProgress) {
        float pulse = MathHelper.sin(animationProgress * 0.22F) * 0.15F;
        leftArm.pitch = -1.1F + pulse;
        rightArm.pitch = -1.1F - pulse;
        leftShoulder.roll += 0.3F;
        rightShoulder.roll -= 0.3F;
        leftHorn.pitch -= 0.2F;
        rightHorn.pitch -= 0.2F;
    }
}
