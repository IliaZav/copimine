package me.copimine.client;

import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * The Enderman renderer also hands its model to Enderman-specific feature and
 * shadow renderers. Extending the vanilla Enderman model keeps that runtime
 * contract intact while the body parts below provide the custom silhouette.
 */
public final class RiftGuardianModel extends EndermanEntityModel<EndermanEntity> {
    private final ModelPart root;
    private final ModelPart torso;
    private final ModelPart leftShoulder;
    private final ModelPart rightShoulder;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftHorn;
    private final ModelPart rightHorn;
    private final ModelPart leftHornTip;
    private final ModelPart rightHornTip;
    private final ModelPart leftCrest;
    private final ModelPart rightCrest;
    private final ModelPart backSpine;
    private final ModelPart leftShard;
    private final ModelPart rightShard;
    private final ModelPart chestRift;
    private Phase phase = Phase.AWAKENING;
    private String animationId = "IDLE";
    private long transitionDurationMillis;

    public RiftGuardianModel(ModelPart root) {
        super(root);
        this.root = root;
        ModelPart body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.leftShoulder = body.getChild("left_shoulder");
        this.rightShoulder = body.getChild("right_shoulder");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftHorn = body.getChild("left_horn");
        this.rightHorn = body.getChild("right_horn");
        this.leftHornTip = leftHorn.getChild("left_horn_tip");
        this.rightHornTip = rightHorn.getChild("right_horn_tip");
        this.leftCrest = body.getChild("left_crest");
        this.rightCrest = body.getChild("right_crest");
        this.backSpine = body.getChild("back_spine");
        this.leftShard = body.getChild("left_shard");
        this.rightShard = body.getChild("right_shard");
        this.chestRift = body.getChild("chest_rift");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        // EndermanEntityModel requires these seven named parts. The body is
        // an empty carrier so all custom geometry is rendered by Biped's
        // normal body pass and remains compatible with Iris shadows/features.
        root.addChild("head", ModelPartBuilder.create()
                        .uv(0, 96).cuboid(-7.0F, -39.0F, -7.0F, 14.0F, 14.0F, 14.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("hat", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        ModelPartData body = root.addChild("body", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("torso", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-9.0F, -25.0F, -5.0F, 18.0F, 21.0F, 10.0F)
                        .uv(0, 64).cuboid(-6.0F, -31.0F, -4.0F, 12.0F, 7.0F, 8.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("left_shoulder", ModelPartBuilder.create()
                        .uv(56, 0).cuboid(0.0F, -23.0F, -4.0F, 9.0F, 7.0F, 8.0F),
                ModelTransform.pivot(8.5F, 24.0F, 0.0F));
        body.addChild("right_shoulder", ModelPartBuilder.create()
                        .uv(56, 15).cuboid(-9.0F, -23.0F, -4.0F, 9.0F, 7.0F, 8.0F),
                ModelTransform.pivot(-8.5F, 24.0F, 0.0F));
        root.addChild("left_arm", ModelPartBuilder.create()
                        .uv(88, 0).cuboid(-2.0F, -1.0F, -2.5F, 5.0F, 25.0F, 5.0F),
                ModelTransform.pivot(16.0F, 1.5F, 0.0F));
        root.addChild("right_arm", ModelPartBuilder.create()
                        .uv(88, 30).cuboid(-3.0F, -1.0F, -2.5F, 5.0F, 25.0F, 5.0F),
                ModelTransform.pivot(-16.0F, 1.5F, 0.0F));
        ModelPartData leftHorn = body.addChild("left_horn", ModelPartBuilder.create()
                        .uv(42, 64).cuboid(0.0F, -36.0F, -2.0F, 4.0F, 9.0F, 4.0F),
                ModelTransform.of(4.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.35F));
        leftHorn.addChild("left_horn_tip", ModelPartBuilder.create()
                        .uv(48, 64).cuboid(-1.5F, -9.0F, -1.5F, 3.0F, 9.0F, 3.0F),
                ModelTransform.pivot(2.0F, -36.0F, 0.0F));
        ModelPartData rightHorn = body.addChild("right_horn", ModelPartBuilder.create()
                        .uv(42, 77).cuboid(-4.0F, -36.0F, -2.0F, 4.0F, 9.0F, 4.0F),
                ModelTransform.of(-4.0F, 24.0F, 0.0F, 0.0F, 0.0F, -0.35F));
        rightHorn.addChild("right_horn_tip", ModelPartBuilder.create()
                        .uv(52, 64).cuboid(-1.5F, -9.0F, -1.5F, 3.0F, 9.0F, 3.0F),
                ModelTransform.pivot(-2.0F, -36.0F, 0.0F));
        body.addChild("left_crest", ModelPartBuilder.create()
                        .uv(56, 64).cuboid(0.0F, -32.0F, -3.0F, 5.0F, 5.0F, 6.0F),
                ModelTransform.of(6.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.18F));
        body.addChild("right_crest", ModelPartBuilder.create()
                        .uv(56, 75).cuboid(-5.0F, -32.0F, -3.0F, 5.0F, 5.0F, 6.0F),
                ModelTransform.of(-6.0F, 24.0F, 0.0F, 0.0F, 0.0F, -0.18F));
        body.addChild("back_spine", ModelPartBuilder.create()
                        .uv(72, 84).cuboid(-2.0F, -27.0F, 4.0F, 4.0F, 14.0F, 3.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("left_shard", ModelPartBuilder.create()
                        .uv(0, 84).cuboid(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                ModelTransform.of(14.0F, -3.0F, -1.0F, 0.25F, 0.0F, 0.65F));
        body.addChild("right_shard", ModelPartBuilder.create()
                        .uv(14, 84).cuboid(-1.5F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                ModelTransform.of(-14.0F, -3.0F, -1.0F, 0.25F, 0.0F, -0.65F));
        body.addChild("chest_rift", ModelPartBuilder.create()
                        .uv(28, 84).cuboid(-3.0F, -20.5F, -5.6F, 6.0F, 10.0F, 1.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("right_leg", ModelPartBuilder.create()
                        .uv(56, 50).cuboid(-3.0F, -1.0F, -3.0F, 6.0F, 25.0F, 6.0F),
                ModelTransform.pivot(-5.0F, 1.5F, 0.0F));
        root.addChild("left_leg", ModelPartBuilder.create()
                        .uv(80, 50).cuboid(-3.0F, -1.0F, -3.0F, 6.0F, 25.0F, 6.0F),
                ModelTransform.pivot(5.0F, 1.5F, 0.0F));
        return TexturedModelData.of(modelData, 128, 128);
    }

    public void setPhase(Phase phase, long transitionDurationMillis) {
        this.phase = phase == null ? Phase.AWAKENING : phase;
        this.transitionDurationMillis = MathHelper.clamp(transitionDurationMillis, 0L, 600_000L);
    }

    public void setAnimation(String animationId) {
        this.animationId = animationId == null || animationId.isBlank()
                ? "IDLE" : animationId.trim().toUpperCase(Locale.ROOT);
    }

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
        applySpellCastTransform(animationProgress);
        applyFinalSilhouette(animationProgress);
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

    /**
     * The server sends a small animation id alongside the phase.  Keeping the
     * motion here, in the model, means the boss remains a real 3D model and no
     * gameplay entity is needed just to show a cast.
     */
    private void applySpellCastTransform(float animationProgress) {
        String animation = animationId == null ? "IDLE" : animationId;
        float pulse = MathHelper.sin(animationProgress * 0.32F);
        switch (animation) {
            case "ABSORPTION_CHANNEL" -> {
                leftArm.pitch = -0.95F + pulse * 0.08F;
                rightArm.pitch = -0.95F - pulse * 0.08F;
                leftArm.roll -= 0.42F;
                rightArm.roll += 0.42F;
                leftShard.pitch += 0.75F;
                rightShard.pitch += 0.75F;
                chestRift.xScale = 1.28F;
                chestRift.yScale = 1.18F;
            }
            case "JUDGMENT_CAST" -> {
                leftArm.pitch = -1.22F + pulse * 0.10F;
                rightArm.pitch = -1.22F - pulse * 0.10F;
                leftArm.roll -= 0.24F;
                rightArm.roll += 0.24F;
                leftHorn.pitch -= 0.28F;
                rightHorn.pitch -= 0.28F;
                backSpine.yaw += pulse * 0.12F;
            }
            case "EXHAUSTED" -> {
                torso.pitch += 0.16F;
                leftArm.pitch += 0.22F;
                rightArm.pitch += 0.22F;
                leftHorn.roll -= 0.12F;
                rightHorn.roll += 0.12F;
            }
            case "SPELL_VOID_BLAST" -> {
                leftArm.pitch = -1.05F + pulse * 0.10F;
                rightArm.pitch = -1.05F - pulse * 0.10F;
                leftArm.roll -= 0.30F;
                rightArm.roll += 0.30F;
                chestRift.zScale = 1.30F + pulse * 0.08F;
            }
            case "SPELL_RIFT_PROJECTILE" -> {
                rightArm.pitch = -1.42F + pulse * 0.15F;
                rightArm.roll -= 0.16F;
                leftArm.pitch += 0.28F;
                rightShard.yaw += 0.42F;
            }
            case "SPELL_RIFT_ARROWS", "SPELL_ARROW_SALVO" -> {
                leftArm.pitch = -0.82F - pulse * 0.12F;
                rightArm.pitch = -0.82F + pulse * 0.12F;
                leftArm.roll -= 0.48F;
                rightArm.roll += 0.48F;
                leftShard.yaw -= 0.28F;
                rightShard.yaw += 0.28F;
            }
            case "SPELL_VOID_MARK" -> {
                leftArm.pitch = -0.62F;
                rightArm.pitch = -1.02F;
                leftArm.roll -= 0.34F;
                rightArm.roll += 0.18F;
                torso.roll += pulse * 0.08F;
            }
            case "SPELL_SUMMON_SERVANTS", "SPELL_SUMMON" -> {
                leftArm.pitch = -0.78F + pulse * 0.14F;
                rightArm.pitch = -0.78F - pulse * 0.14F;
                leftArm.roll -= 0.62F;
                rightArm.roll += 0.62F;
                leftShard.pitch -= 0.22F;
                rightShard.pitch -= 0.22F;
            }
            case "SPELL_WILL_DISTORTION" -> {
                leftArm.pitch = -0.55F;
                rightArm.pitch = -1.25F;
                torso.roll += pulse * 0.18F;
                leftHorn.yaw += 0.20F;
                rightHorn.yaw -= 0.20F;
            }
            case "SPELL_ARENA_INFERNO" -> {
                leftArm.pitch = -1.30F;
                rightArm.pitch = -1.30F;
                leftArm.roll -= 0.20F;
                rightArm.roll += 0.20F;
                leftShard.pitch += 0.35F;
                rightShard.pitch += 0.35F;
            }
            case "SPELL_IMPACT" -> {
                torso.pitch += 0.20F + pulse * 0.08F;
                leftArm.pitch -= 0.28F;
                rightArm.pitch -= 0.28F;
                leftHornTip.pitch += pulse * 0.10F;
                rightHornTip.pitch -= pulse * 0.10F;
            }
            case "PHASE_SHIFT" -> {
                root.roll += pulse * 0.16F;
                leftCrest.yaw += pulse * 0.12F;
                rightCrest.yaw -= pulse * 0.12F;
                backSpine.pitch += pulse * 0.12F;
            }
            default -> {
                // IDLE and unknown future ids use the phase/idle animation.
            }
        }
    }

    /** The last phase is intentionally larger and more threatening. */
    private void applyFinalSilhouette(float animationProgress) {
        if (phase != Phase.CATASTROPHE) {
            return;
        }
        float pulse = MathHelper.sin(animationProgress * 0.16F) * 0.04F;
        root.xScale = 1.18F + pulse;
        root.yScale = 1.22F + pulse;
        root.zScale = 1.18F + pulse;
        leftHorn.xScale = 1.12F;
        leftHorn.yScale = 1.62F + pulse;
        leftHorn.zScale = 1.12F;
        rightHorn.xScale = 1.12F;
        rightHorn.yScale = 1.62F + pulse;
        rightHorn.zScale = 1.12F;
        leftHornTip.yScale = 1.35F;
        rightHornTip.yScale = 1.35F;
        leftCrest.xScale = 1.18F;
        rightCrest.xScale = 1.18F;
        backSpine.xScale = 1.16F;
        backSpine.yScale = 1.30F;
        chestRift.xScale = 1.24F;
        chestRift.yScale = 1.24F;
    }
}
