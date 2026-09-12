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
    private static final float HIDDEN_FINAL_PART_SCALE = 0.001F;
    static final int TEXTURE_SIZE = 512;
    private static final int UV_SCALE = TEXTURE_SIZE / 128;
    /** Geometry contract for the readable, elongated guardian silhouette. */
    static final float TORSO_WIDTH = 14.0F;
    static final float TORSO_HEIGHT = 25.0F;
    static final float TORSO_DEPTH = 8.0F;
    static final float ARM_HEIGHT = 29.0F;
    static final float LEG_HEIGHT = 29.0F;
    static final float ARM_PIVOT_X = 12.5F;
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
    private final ModelPart jaw;
    private final ModelPart coreEye;
    private final ModelPart crownLeft;
    private final ModelPart crownRight;
    private final ModelPart leftTalon;
    private final ModelPart rightTalon;
    private final ModelPart lastSealSpine;
    private Phase phase = Phase.AWAKENING;
    private String animationId = "IDLE_BREATH";
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
        this.jaw = body.getChild("jaw");
        this.coreEye = body.getChild("core_eye");
        this.crownLeft = body.getChild("crown_left");
        this.crownRight = body.getChild("crown_right");
        this.leftTalon = leftArm.getChild("left_talon");
        this.rightTalon = rightArm.getChild("right_talon");
        this.lastSealSpine = body.getChild("last_seal_spine");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        // EndermanEntityModel requires these seven named parts. The body is
        // an empty carrier so all custom geometry is rendered by Biped's
        // normal body pass and remains compatible with Iris shadows/features.
        root.addChild("head", ModelPartBuilder.create()
                        .uv(uv(0), uv(96)).cuboid(-6.0F, -40.0F, -6.0F, 12.0F, 14.0F, 12.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("hat", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        ModelPartData body = root.addChild("body", ModelPartBuilder.create(),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        body.addChild("torso", ModelPartBuilder.create()
                        .uv(uv(0), uv(0)).cuboid(-7.0F, -28.0F, -4.0F,
                                TORSO_WIDTH, TORSO_HEIGHT, TORSO_DEPTH)
                        .uv(uv(0), uv(64)).cuboid(-5.0F, -34.0F, -3.0F, 10.0F, 6.0F, 6.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("left_shoulder", ModelPartBuilder.create()
                        .uv(uv(56), uv(0)).cuboid(0.0F, -25.0F, -3.5F, 7.0F, 6.0F, 7.0F),
                ModelTransform.pivot(6.8F, 24.0F, 0.0F));
        body.addChild("right_shoulder", ModelPartBuilder.create()
                        .uv(uv(56), uv(15)).cuboid(-7.0F, -25.0F, -3.5F, 7.0F, 6.0F, 7.0F),
                ModelTransform.pivot(-6.8F, 24.0F, 0.0F));
        root.addChild("left_arm", ModelPartBuilder.create()
                        .uv(uv(80), uv(0)).cuboid(-2.0F, -1.0F, -2.0F,
                                4.0F, ARM_HEIGHT, 4.0F),
                ModelTransform.pivot(ARM_PIVOT_X, 0.5F, 0.0F));
        root.addChild("right_arm", ModelPartBuilder.create()
                        .uv(uv(80), uv(32)).cuboid(-2.0F, -1.0F, -2.0F,
                                4.0F, ARM_HEIGHT, 4.0F),
                ModelTransform.pivot(-ARM_PIVOT_X, 0.5F, 0.0F));
        ModelPartData leftHorn = body.addChild("left_horn", ModelPartBuilder.create()
                        .uv(uv(42), uv(64)).cuboid(0.0F, -39.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                ModelTransform.of(3.2F, 24.0F, 0.0F, 0.0F, 0.0F, 0.28F));
        leftHorn.addChild("left_horn_tip", ModelPartBuilder.create()
                        .uv(uv(48), uv(64)).cuboid(-1.0F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                ModelTransform.pivot(1.5F, -39.0F, 0.0F));
        ModelPartData rightHorn = body.addChild("right_horn", ModelPartBuilder.create()
                        .uv(uv(42), uv(77)).cuboid(-3.0F, -39.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                ModelTransform.of(-3.2F, 24.0F, 0.0F, 0.0F, 0.0F, -0.28F));
        rightHorn.addChild("right_horn_tip", ModelPartBuilder.create()
                        .uv(uv(52), uv(64)).cuboid(-1.0F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                ModelTransform.pivot(-1.5F, -39.0F, 0.0F));
        body.addChild("left_crest", ModelPartBuilder.create()
                        .uv(uv(56), uv(64)).cuboid(0.0F, -34.0F, -2.5F, 4.0F, 6.0F, 5.0F),
                ModelTransform.of(4.5F, 24.0F, 0.0F, 0.0F, 0.0F, 0.16F));
        body.addChild("right_crest", ModelPartBuilder.create()
                        .uv(uv(56), uv(75)).cuboid(-4.0F, -34.0F, -2.5F, 4.0F, 6.0F, 5.0F),
                ModelTransform.of(-4.5F, 24.0F, 0.0F, 0.0F, 0.0F, -0.16F));
        body.addChild("back_spine", ModelPartBuilder.create()
                        .uv(uv(72), uv(84)).cuboid(-1.5F, -30.0F, 3.5F, 3.0F, 17.0F, 2.5F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("left_shard", ModelPartBuilder.create()
                        .uv(uv(0), uv(84)).cuboid(-1.5F, -2.0F, -1.25F, 3.0F, 13.0F, 2.5F),
                ModelTransform.of(10.5F, -4.0F, -0.8F, 0.25F, 0.0F, 0.58F));
        body.addChild("right_shard", ModelPartBuilder.create()
                        .uv(uv(14), uv(84)).cuboid(-1.5F, -2.0F, -1.25F, 3.0F, 13.0F, 2.5F),
                ModelTransform.of(-10.5F, -4.0F, -0.8F, 0.25F, 0.0F, -0.58F));
        body.addChild("chest_rift", ModelPartBuilder.create()
                        .uv(uv(28), uv(84)).cuboid(-2.5F, -22.0F, -4.6F, 5.0F, 12.0F, 1.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("jaw", ModelPartBuilder.create()
                        .uv(uv(96), uv(64)).cuboid(-3.5F, -31.0F, -5.2F, 7.0F, 3.0F, 2.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("core_eye", ModelPartBuilder.create()
                        .uv(uv(96), uv(72)).cuboid(-2.0F, -19.0F, -5.0F, 4.0F, 4.0F, 1.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        body.addChild("crown_left", ModelPartBuilder.create()
                        .uv(uv(112), uv(64)).cuboid(0.0F, -40.0F, -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.of(5.5F, 24.0F, 0.0F, -0.10F, 0.0F, 0.28F));
        body.addChild("crown_right", ModelPartBuilder.create()
                        .uv(uv(112), uv(74)).cuboid(-2.5F, -40.0F, -1.25F, 2.5F, 8.0F, 2.5F),
                ModelTransform.of(-5.5F, 24.0F, 0.0F, -0.10F, 0.0F, -0.28F));
        body.addChild("last_seal_spine", ModelPartBuilder.create()
                        .uv(uv(96), uv(80)).cuboid(-1.5F, -37.0F, 4.8F, 3.0F, 14.0F, 2.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        root.addChild("right_leg", ModelPartBuilder.create()
                        .uv(uv(56), uv(50)).cuboid(-2.5F, -1.0F, -2.5F,
                                5.0F, LEG_HEIGHT, 5.0F),
                ModelTransform.pivot(-3.8F, 0.5F, 0.0F));
        root.addChild("left_leg", ModelPartBuilder.create()
                        .uv(uv(80), uv(50)).cuboid(-2.5F, -1.0F, -2.5F,
                                5.0F, LEG_HEIGHT, 5.0F),
                ModelTransform.pivot(3.8F, 0.5F, 0.0F));
        root.getChild("left_arm").addChild("left_talon", ModelPartBuilder.create()
                        .uv(uv(112), uv(84)).cuboid(-1.0F, -1.0F, -1.0F, 2.5F, 6.0F, 2.0F),
                ModelTransform.of(1.0F, 26.0F, -0.2F, 0.22F, 0.0F, 0.08F));
        root.getChild("right_arm").addChild("right_talon", ModelPartBuilder.create()
                        .uv(uv(112), uv(92)).cuboid(-1.5F, -1.0F, -1.0F, 2.5F, 6.0F, 2.0F),
                ModelTransform.of(-1.0F, 26.0F, -0.2F, 0.22F, 0.0F, -0.08F));
        return TexturedModelData.of(modelData, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static int uv(int coordinate) {
        return coordinate * UV_SCALE;
    }

    public void setPhase(Phase phase, long transitionDurationMillis) {
        this.phase = phase == null ? Phase.AWAKENING : phase;
        this.transitionDurationMillis = MathHelper.clamp(transitionDurationMillis, 0L, 600_000L);
    }

    public void setAnimation(String animationId) {
        this.animationId = BossAnimationId.canonicalWireId(animationId);
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
        String animation = animationId == null ? "IDLE_BREATH" : animationId;
        applyIdleTransform(animationProgress, transition, animation);
        applyWalkTransform(limbAngle, walk);
        applyMovementAnimation(animationProgress, animation);
        if (entity != null && entity.isAngry()) {
            applyAttackTransform(animationProgress);
        }
        if (phase == Phase.OVERLOAD) {
            applyOverloadTransform(animationProgress);
        } else if (phase == Phase.RAGE || phase == Phase.LAST_SEAL) {
            applyLastSealTransform(animationProgress);
        } else if (phase == Phase.RIFT) {
            root.roll += MathHelper.sin(animationProgress * 0.12F) * 0.08F;
            leftShard.yaw += 0.35F;
            rightShard.yaw -= 0.35F;
        } else if (phase == Phase.HUNT) {
            leftArm.pitch -= 0.25F;
            rightArm.pitch -= 0.25F;
        }
        applyBreathTransform(animationProgress, animation);
        applyDamagedFlinch(animationProgress, animation);
        applyTeleportRip(animationProgress, animation);
        applyCastTransform(animationProgress, animation);
        applyPhaseShiftTransform(animationProgress, animation);
        applyFinalStrikeTransform(animationProgress, animation);
        applyDefeatCollapse(animationProgress, animation);
        applyFinalSilhouette(animationProgress);
        hideFinalAdornmentOutsideFinalState(animation);
        root.yaw = MathHelper.clamp(headYaw * 0.017453292F, -0.5F, 0.5F);
        torso.pitch += MathHelper.clamp(headPitch * 0.017453292F, -0.35F, 0.35F);
        clampPose();
    }

    private void applyIdleTransform(float animationProgress, float transition, String animation) {
        float idle = MathHelper.sin(animationProgress * 0.08F);
        torso.pivotY += idle * 0.45F;
        chestRift.zScale = 1.0F + MathHelper.clamp(idle * 0.08F + transition * 0.12F, -0.08F, 0.18F);
        coreEye.zScale = 1.0F + MathHelper.clamp(idle * 0.10F + transition * 0.14F, -0.06F, 0.20F);
        coreEye.yScale = 1.0F + MathHelper.clamp(idle * 0.08F, -0.05F, 0.14F);
        jaw.pitch += MathHelper.clamp(0.06F + idle * 0.05F, -0.08F, 0.16F);
        jaw.pivotY += MathHelper.clamp(idle * 0.45F, -0.45F, 0.45F);
        leftHorn.roll += idle * 0.04F;
        rightHorn.roll -= idle * 0.04F;
        if (finalAdornmentIsRevealed(animation)) {
            crownLeft.pitch -= 0.08F + transition * 0.06F;
            crownRight.pitch -= 0.08F + transition * 0.06F;
            crownLeft.yScale = 0.88F + transition * 0.12F;
            crownRight.yScale = 0.88F + transition * 0.12F;
            leftTalon.pitch += 0.14F + transition * 0.08F;
            rightTalon.pitch += 0.14F + transition * 0.08F;
            lastSealSpine.pitch += 0.08F + transition * 0.04F;
            lastSealSpine.yScale = 0.92F + transition * 0.12F;
        } else {
            hideFinalAdornment();
        }
        leftShard.pivotY += MathHelper.sin(animationProgress * 0.11F) * 0.9F;
        rightShard.pivotY += MathHelper.cos(animationProgress * 0.11F) * 0.9F;
    }

    private boolean finalAdornmentIsRevealed(String animation) {
        return phase == Phase.RAGE || phase == Phase.LAST_SEAL;
    }

    private void hideFinalAdornmentOutsideFinalState(String animation) {
        if (!finalAdornmentIsRevealed(animation)) {
            hideFinalAdornment();
        }
    }

    private void hideFinalAdornment() {
        hideFinalPart(crownLeft);
        hideFinalPart(crownRight);
        hideFinalPart(leftTalon);
        hideFinalPart(rightTalon);
        hideFinalPart(lastSealSpine);
    }

    private static void hideFinalPart(ModelPart part) {
        part.xScale = HIDDEN_FINAL_PART_SCALE;
        part.yScale = HIDDEN_FINAL_PART_SCALE;
        part.zScale = HIDDEN_FINAL_PART_SCALE;
    }

    private void applyWalkTransform(float limbAngle, float walk) {
        float swing = MathHelper.sin(limbAngle * 0.6662F) * walk;
        leftArm.pitch += MathHelper.clamp(swing * 0.65F, -0.55F, 0.55F);
        rightArm.pitch -= MathHelper.clamp(swing * 0.65F, -0.55F, 0.55F);
        leftShoulder.roll += MathHelper.clamp(swing * 0.08F, -0.1F, 0.1F);
        rightShoulder.roll -= MathHelper.clamp(swing * 0.08F, -0.1F, 0.1F);
    }

    private void applyMovementAnimation(float animationProgress, String animation) {
        if (!"RUN".equals(animation)) {
            return;
        }
        float stride = MathHelper.sin(animationProgress * 0.36F);
        leftArm.pitch += MathHelper.clamp(stride * 0.32F, -0.42F, 0.42F);
        rightArm.pitch -= MathHelper.clamp(stride * 0.32F, -0.42F, 0.42F);
        torso.pitch += MathHelper.clamp(0.06F + Math.abs(stride) * 0.06F, 0.04F, 0.14F);
        leftShard.yaw += 0.12F + stride * 0.06F;
        rightShard.yaw -= 0.12F - stride * 0.06F;
    }

    private void applyAttackTransform(float animationProgress) {
        float strike = MathHelper.sin(animationProgress * 0.35F) * 0.25F + 0.35F;
        leftArm.pitch -= MathHelper.clamp(strike, 0.0F, 0.65F);
        rightArm.pitch -= MathHelper.clamp(strike, 0.0F, 0.65F);
        torso.pitch += 0.12F;
    }

    private void applyOverloadTransform(float animationProgress) {
        float pull = MathHelper.sin(animationProgress * 0.18F) * 0.2F;
        leftArm.roll -= 0.35F + pull;
        rightArm.roll += 0.35F + pull;
        leftShard.pitch += 0.5F;
        rightShard.pitch += 0.5F;
        chestRift.xScale = 1.18F;
        chestRift.yScale = 1.12F;
        coreEye.zScale = 1.18F;
        leftTalon.pitch += 0.22F;
        rightTalon.pitch += 0.22F;
    }

    private void applyLastSealTransform(float animationProgress) {
        float pulse = MathHelper.sin(animationProgress * 0.22F) * 0.15F;
        leftArm.pitch = -1.1F + pulse;
        rightArm.pitch = -1.1F - pulse;
        leftShoulder.roll += 0.3F;
        rightShoulder.roll -= 0.3F;
        leftHorn.pitch -= 0.2F;
        rightHorn.pitch -= 0.2F;
        crownLeft.pitch -= 0.18F;
        crownRight.pitch -= 0.18F;
        lastSealSpine.pitch -= 0.16F;
    }

    private void applyBreathTransform(float animationProgress, String animation) {
        if (!"IDLE_BREATH".equals(animation)) {
            return;
        }
        float intensity = 1.0F;
        float pulse = MathHelper.sin(animationProgress * 0.20F) * intensity;
        jaw.pitch += MathHelper.clamp(0.16F + pulse * 0.14F, -0.04F, 0.34F);
        jaw.pivotY += MathHelper.clamp(pulse * 0.55F, -0.55F, 0.75F);
        torso.pitch += MathHelper.clamp(-0.04F + pulse * 0.05F, -0.12F, 0.08F);
        chestRift.zScale = 1.08F + MathHelper.clamp(pulse * 0.10F, -0.04F, 0.18F);
        coreEye.zScale = 1.08F + MathHelper.clamp(pulse * 0.14F, -0.06F, 0.24F);
        coreEye.yScale = 1.04F + MathHelper.clamp(pulse * 0.08F, -0.04F, 0.16F);
    }

    private void applyDamagedFlinch(float animationProgress, String animation) {
        if (!"HURT".equals(animation) && !"DAMAGED_FLINCH".equals(animation)) {
            return;
        }
        float recoil = MathHelper.cos(animationProgress * 0.55F) * 0.12F;
        root.roll += MathHelper.clamp(-0.18F + recoil, -0.26F, 0.10F);
        torso.pitch += MathHelper.clamp(0.20F + recoil, 0.04F, 0.28F);
        jaw.pitch += 0.28F;
        leftArm.pitch += 0.34F;
        rightArm.pitch += 0.34F;
        leftArm.roll += 0.18F;
        rightArm.roll -= 0.18F;
        leftTalon.pitch -= 0.24F;
        rightTalon.pitch -= 0.24F;
        coreEye.zScale = 1.18F;
    }

    private void applyTeleportRip(float animationProgress, String animation) {
        if (!"TELEPORT_RIP".equals(animation)) {
            return;
        }
        float pulse = MathHelper.sin(animationProgress * 0.70F);
        root.roll += MathHelper.clamp(pulse * 0.28F, -0.28F, 0.28F);
        torso.yaw += MathHelper.clamp(pulse * 0.22F, -0.22F, 0.22F);
        leftShard.yaw -= 0.44F;
        rightShard.yaw += 0.44F;
        leftShard.pitch += 0.38F;
        rightShard.pitch += 0.38F;
        leftTalon.pitch += 0.38F;
        rightTalon.pitch += 0.38F;
        coreEye.zScale = 1.22F;
    }

    /**
     * The server sends a compact animation id alongside the phase. Keeping the
     * pose logic here preserves the UUID-scoped renderer swap while the boss
     * remains a normal Enderman entity for gameplay and hitbox purposes.
     */
    private void applyCastTransform(float animationProgress, String animation) {
        float pulse = MathHelper.sin(animationProgress * 0.32F);
        switch (animation) {
            case "CAST_CHARGE" -> {
                leftArm.pitch = -1.02F + pulse * 0.10F;
                rightArm.pitch = -1.02F - pulse * 0.10F;
                leftArm.roll -= 0.42F;
                rightArm.roll += 0.42F;
                leftTalon.pitch += 0.42F;
                rightTalon.pitch += 0.42F;
                jaw.pitch += 0.18F;
                chestRift.xScale = 1.24F;
                chestRift.yScale = 1.16F;
                coreEye.zScale = 1.24F + pulse * 0.10F;
                coreEye.yScale = 1.12F;
            }
            case "MELEE_SWIPE" -> applyMeleeSwipeTransform(pulse);
            case "CAST_RELEASE" -> applyGenericReleaseTransform(pulse);
            case "CHEST_STRIKE", "SPELL_VOID_BLAST" -> applyChestStrikeTransform(pulse);
            case "SPELL_RIFT_PROJECTILE" -> applyRiftProjectileTransform(pulse);
            case "SPELL_RIFT_ARROWS", "SPELL_ARROW_SALVO" -> applyArrowFanTransform(pulse);
            case "MARK_CONTROL", "SPELL_VOID_MARK" -> applyMarkControlTransform(pulse);
            case "SUMMON_CHANNEL", "SPELL_SUMMON_SERVANTS", "SPELL_SUMMON" -> applySummonChannelTransform(pulse);
            case "SPELL_RIFT_OBELISKS" -> applyObeliskChannelTransform(pulse);
            case "SPELL_ARENA_INFERNO" -> applyInfernoTransform(pulse);
            case "GROUND_SLAM" -> applyGroundSlamTransform(pulse);
            case "CAST_IMPACT", "SPELL_IMPACT" -> applyImpactTransform(pulse);
            default -> {
                // Non-cast animations use their dedicated transforms below.
            }
        }
    }

    private void applyMeleeSwipeTransform(float pulse) {
        leftArm.pitch = -0.32F + pulse * 0.08F;
        rightArm.pitch = -1.62F - pulse * 0.10F;
        leftArm.roll += 0.34F;
        rightArm.roll -= 0.62F;
        torso.yaw -= 0.24F;
        torso.pitch += 0.18F;
        jaw.pitch += 0.22F;
        leftTalon.pitch += 0.58F;
        rightTalon.pitch -= 0.22F;
        leftShard.yaw += 0.28F;
        rightShard.yaw -= 0.14F;
    }

    private void applyGenericReleaseTransform(float pulse) {
        leftArm.pitch = -0.92F - pulse * 0.14F;
        rightArm.pitch = -1.18F + pulse * 0.10F;
        leftArm.roll -= 0.34F;
        rightArm.roll += 0.26F;
        torso.pitch += 0.14F;
        torso.roll += pulse * 0.10F;
        jaw.pitch += 0.24F;
        crownLeft.yaw += 0.18F;
        crownRight.yaw -= 0.18F;
        leftShard.pitch += 0.22F;
        rightShard.pitch += 0.22F;
        coreEye.zScale = 1.18F + pulse * 0.08F;
    }

    private void applyChestStrikeTransform(float pulse) {
        leftArm.pitch = -0.74F + pulse * 0.08F;
        rightArm.pitch = -1.48F - pulse * 0.08F;
        leftArm.roll -= 0.24F;
        rightArm.roll += 0.48F;
        torso.pitch -= 0.08F;
        torso.yaw += 0.16F;
        chestRift.zScale = 1.32F + pulse * 0.10F;
        coreEye.zScale = 1.34F + pulse * 0.08F;
        coreEye.yScale = 1.20F;
        leftTalon.pitch += 0.20F;
        rightTalon.pitch += 0.52F;
    }

    private void applyRiftProjectileTransform(float pulse) {
        leftArm.pitch = -1.28F + pulse * 0.06F;
        rightArm.pitch = -0.66F - pulse * 0.10F;
        leftArm.roll -= 0.46F;
        rightArm.roll += 0.12F;
        torso.pitch += 0.12F;
        torso.roll -= 0.14F;
        chestRift.xScale = 1.18F;
        chestRift.zScale = 1.24F;
        coreEye.zScale = 1.22F;
        leftShard.yaw -= 0.22F;
        rightShard.yaw += 0.42F;
    }

    private void applyArrowFanTransform(float pulse) {
        leftArm.pitch = -1.42F - pulse * 0.08F;
        rightArm.pitch = -1.42F + pulse * 0.08F;
        leftArm.roll -= 0.68F;
        rightArm.roll += 0.68F;
        torso.pitch -= 0.18F;
        jaw.pitch += 0.18F;
        leftHorn.yaw -= 0.16F;
        rightHorn.yaw += 0.16F;
        leftShard.pitch += 0.40F;
        rightShard.pitch += 0.40F;
        coreEye.yScale = 1.22F;
    }

    private void applyMarkControlTransform(float pulse) {
        leftArm.pitch = -0.62F + pulse * 0.08F;
        rightArm.pitch = -0.62F - pulse * 0.08F;
        leftArm.roll -= 0.56F;
        rightArm.roll += 0.56F;
        torso.pitch += 0.20F;
        torso.roll += pulse * 0.18F;
        jaw.pitch += 0.30F;
        chestRift.xScale = 1.16F;
        chestRift.yScale = 1.30F;
        coreEye.zScale = 1.16F;
        coreEye.yScale = 1.28F;
    }

    private void applySummonChannelTransform(float pulse) {
        leftArm.pitch = -0.42F + pulse * 0.10F;
        rightArm.pitch = -0.42F - pulse * 0.10F;
        leftArm.roll -= 0.22F;
        rightArm.roll += 0.22F;
        torso.pitch += 0.28F;
        torso.yaw += pulse * 0.12F;
        jaw.pitch += 0.16F;
        leftTalon.pitch += 0.32F;
        rightTalon.pitch += 0.32F;
        leftShard.pitch -= 0.26F;
        rightShard.pitch -= 0.26F;
        coreEye.zScale = 1.20F + pulse * 0.06F;
    }

    private void applyObeliskChannelTransform(float pulse) {
        leftArm.pitch = -1.08F + pulse * 0.06F;
        rightArm.pitch = -1.08F - pulse * 0.06F;
        leftArm.roll -= 0.74F;
        rightArm.roll += 0.74F;
        torso.pitch -= 0.22F;
        torso.roll += pulse * 0.08F;
        jaw.pitch += 0.36F;
        crownLeft.pitch -= 0.24F;
        crownRight.pitch -= 0.24F;
        lastSealSpine.pitch -= 0.18F;
        chestRift.zScale = 1.28F;
        coreEye.zScale = 1.30F;
    }

    private void applyInfernoTransform(float pulse) {
        leftArm.pitch = -1.52F + pulse * 0.06F;
        rightArm.pitch = -1.52F - pulse * 0.06F;
        leftArm.roll -= 0.30F;
        rightArm.roll += 0.30F;
        torso.pitch -= 0.30F;
        jaw.pitch += 0.42F;
        leftHorn.pitch -= 0.26F;
        rightHorn.pitch -= 0.26F;
        leftShard.pitch += 0.54F;
        rightShard.pitch += 0.54F;
        chestRift.xScale = 1.28F;
        chestRift.yScale = 1.20F;
        coreEye.zScale = 1.36F;
        coreEye.yScale = 1.20F;
    }

    private void applyGroundSlamTransform(float pulse) {
        leftArm.pitch = 0.54F + pulse * 0.06F;
        rightArm.pitch = 0.54F - pulse * 0.06F;
        leftArm.roll -= 0.42F;
        rightArm.roll += 0.42F;
        torso.pitch += 0.54F;
        jaw.pitch += 0.38F;
        leftTalon.pitch -= 0.44F;
        rightTalon.pitch -= 0.44F;
        chestRift.zScale = 1.24F;
        coreEye.yScale = 1.16F;
    }

    private void applyImpactTransform(float pulse) {
        torso.pitch += 0.24F + pulse * 0.08F;
        root.roll += pulse * 0.10F;
        leftArm.pitch -= 0.24F;
        rightArm.pitch -= 0.24F;
        jaw.pitch += 0.34F;
        leftHornTip.pitch += pulse * 0.12F;
        rightHornTip.pitch -= pulse * 0.12F;
        leftTalon.pitch += 0.28F;
        rightTalon.pitch += 0.28F;
        coreEye.zScale = 1.28F;
        coreEye.yScale = 1.16F;
    }

    private void applyPhaseShiftTransform(float animationProgress, String animation) {
        if (!"PHASE_TRANSITION".equals(animation) && !"PHASE_SHIFT".equals(animation)) {
            return;
        }
        float pulse = MathHelper.sin(animationProgress * 0.40F);
        float intensity = 1.0F;
        root.roll += MathHelper.clamp(pulse * 0.16F * intensity, -0.26F, 0.26F);
        leftCrest.yaw += pulse * 0.12F * intensity;
        rightCrest.yaw -= pulse * 0.12F * intensity;
        backSpine.pitch += pulse * 0.12F * intensity;
        crownLeft.pitch -= 0.16F * intensity;
        crownRight.pitch -= 0.16F * intensity;
        lastSealSpine.pitch -= 0.12F * intensity;
        lastSealSpine.yScale = 1.08F + MathHelper.clamp(pulse * 0.08F * intensity, -0.04F, 0.18F);
    }

    private void applyDefeatCollapse(float animationProgress, String animation) {
        if (!"DYING".equals(animation) && !"DEFEAT_COLLAPSE".equals(animation)) {
            return;
        }
        float intensity = 1.0F;
        float sag = MathHelper.sin(animationProgress * 0.18F) * 0.08F * intensity;
        torso.pitch += 0.26F * intensity + sag;
        torso.pivotY += 1.2F * intensity;
        jaw.pitch += 0.18F * intensity;
        leftArm.pitch += 0.42F * intensity;
        rightArm.pitch += 0.42F * intensity;
        leftArm.roll += 0.18F * intensity;
        rightArm.roll -= 0.18F * intensity;
        leftHorn.roll -= 0.12F * intensity;
        rightHorn.roll += 0.12F * intensity;
        leftTalon.pitch -= 0.30F * intensity;
        rightTalon.pitch -= 0.30F * intensity;
        coreEye.zScale = 0.90F;
    }

    private void applyFinalStrikeTransform(float animationProgress, String animation) {
        if (!"FINAL_STRIKE".equals(animation)) {
            return;
        }
        float pulse = MathHelper.sin(animationProgress * 0.42F);
        leftArm.pitch = -1.25F + pulse * 0.12F;
        rightArm.pitch = -1.25F - pulse * 0.12F;
        leftArm.roll += 0.34F;
        rightArm.roll -= 0.34F;
        torso.pitch -= 0.16F;
        jaw.pitch += 0.38F + pulse * 0.06F;
        leftHorn.pitch -= 0.24F;
        rightHorn.pitch -= 0.24F;
        leftHorn.roll += 0.10F;
        rightHorn.roll -= 0.10F;
        coreEye.zScale = 1.30F + pulse * 0.10F;
        coreEye.yScale = 1.18F + pulse * 0.08F;
        chestRift.zScale = 1.16F + pulse * 0.08F;
        lastSealSpine.pitch -= 0.22F;
    }

    /** The last phase is intentionally larger and more threatening. */
    private void applyFinalSilhouette(float animationProgress) {
        if (phase != Phase.RAGE && phase != Phase.LAST_SEAL) {
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
        crownLeft.xScale = 1.14F;
        crownLeft.yScale = 1.32F + pulse;
        crownRight.xScale = 1.14F;
        crownRight.yScale = 1.32F + pulse;
        leftTalon.yScale = 1.24F + pulse * 0.5F;
        rightTalon.yScale = 1.24F + pulse * 0.5F;
        lastSealSpine.xScale = 1.16F;
        lastSealSpine.yScale = 1.38F + pulse;
        lastSealSpine.zScale = 1.12F;
        chestRift.xScale = 1.24F;
        chestRift.yScale = 1.24F;
        coreEye.zScale = MathHelper.clamp(coreEye.zScale + 0.12F, 0.9F, 1.42F);
        jaw.pitch += 0.12F;
    }

    private void clampPose() {
        root.yaw = clamp(root.yaw, -0.5F, 0.5F);
        root.roll = clamp(root.roll, -0.75F, 0.75F);
        root.xScale = clamp(root.xScale, 0.85F, 1.30F);
        root.yScale = clamp(root.yScale, 0.85F, 1.30F);
        root.zScale = clamp(root.zScale, 0.85F, 1.30F);

        torso.pitch = clamp(torso.pitch, -1.5F, 1.5F);
        torso.yaw = clamp(torso.yaw, -0.75F, 0.75F);
        torso.roll = clamp(torso.roll, -0.75F, 0.75F);
        torso.pivotY = clamp(torso.pivotY, 22.0F, 26.0F);

        leftShoulder.roll = clamp(leftShoulder.roll, -0.8F, 0.8F);
        rightShoulder.roll = clamp(rightShoulder.roll, -0.8F, 0.8F);
        leftArm.pitch = clamp(leftArm.pitch, -1.8F, 1.8F);
        rightArm.pitch = clamp(rightArm.pitch, -1.8F, 1.8F);
        leftArm.roll = clamp(leftArm.roll, -1.2F, 1.2F);
        rightArm.roll = clamp(rightArm.roll, -1.2F, 1.2F);
        leftTalon.pitch = clamp(leftTalon.pitch, -1.6F, 1.6F);
        rightTalon.pitch = clamp(rightTalon.pitch, -1.6F, 1.6F);
        leftTalon.xScale = clamp(leftTalon.xScale, HIDDEN_FINAL_PART_SCALE, 1.6F);
        leftTalon.yScale = clamp(leftTalon.yScale, HIDDEN_FINAL_PART_SCALE, 1.6F);
        leftTalon.zScale = clamp(leftTalon.zScale, HIDDEN_FINAL_PART_SCALE, 1.6F);
        rightTalon.xScale = clamp(rightTalon.xScale, HIDDEN_FINAL_PART_SCALE, 1.6F);
        rightTalon.yScale = clamp(rightTalon.yScale, HIDDEN_FINAL_PART_SCALE, 1.6F);
        rightTalon.zScale = clamp(rightTalon.zScale, HIDDEN_FINAL_PART_SCALE, 1.6F);

        leftHorn.pitch = clamp(leftHorn.pitch, -1.2F, 1.2F);
        rightHorn.pitch = clamp(rightHorn.pitch, -1.2F, 1.2F);
        leftHorn.yaw = clamp(leftHorn.yaw, -0.8F, 0.8F);
        rightHorn.yaw = clamp(rightHorn.yaw, -0.8F, 0.8F);
        leftHorn.roll = clamp(leftHorn.roll, -1.2F, 1.2F);
        rightHorn.roll = clamp(rightHorn.roll, -1.2F, 1.2F);
        leftHorn.yScale = clamp(leftHorn.yScale, 0.8F, 1.8F);
        rightHorn.yScale = clamp(rightHorn.yScale, 0.8F, 1.8F);
        leftHornTip.pitch = clamp(leftHornTip.pitch, -1.2F, 1.2F);
        rightHornTip.pitch = clamp(rightHornTip.pitch, -1.2F, 1.2F);
        leftHornTip.yScale = clamp(leftHornTip.yScale, 0.8F, 1.6F);
        rightHornTip.yScale = clamp(rightHornTip.yScale, 0.8F, 1.6F);

        leftCrest.yaw = clamp(leftCrest.yaw, -0.7F, 0.7F);
        rightCrest.yaw = clamp(rightCrest.yaw, -0.7F, 0.7F);
        crownLeft.pitch = clamp(crownLeft.pitch, -1.4F, 1.4F);
        crownRight.pitch = clamp(crownRight.pitch, -1.4F, 1.4F);
        crownLeft.yaw = clamp(crownLeft.yaw, -1.4F, 1.4F);
        crownRight.yaw = clamp(crownRight.yaw, -1.4F, 1.4F);
        crownLeft.xScale = clamp(crownLeft.xScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        crownLeft.yScale = clamp(crownLeft.yScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        crownLeft.zScale = clamp(crownLeft.zScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        crownRight.xScale = clamp(crownRight.xScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        crownRight.yScale = clamp(crownRight.yScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        crownRight.zScale = clamp(crownRight.zScale, HIDDEN_FINAL_PART_SCALE, 1.8F);

        jaw.pitch = clamp(jaw.pitch, -1.3F, 1.3F);
        jaw.pivotY = clamp(jaw.pivotY, 23.0F, 25.5F);
        coreEye.zScale = clamp(coreEye.zScale, 0.75F, 1.8F);
        coreEye.yScale = clamp(coreEye.yScale, 0.75F, 1.8F);

        backSpine.pitch = clamp(backSpine.pitch, -1.2F, 1.2F);
        backSpine.yaw = clamp(backSpine.yaw, -0.6F, 0.6F);
        backSpine.yScale = clamp(backSpine.yScale, 0.8F, 1.6F);
        lastSealSpine.xScale = clamp(lastSealSpine.xScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        lastSealSpine.pitch = clamp(lastSealSpine.pitch, -1.4F, 1.4F);
        lastSealSpine.yScale = clamp(lastSealSpine.yScale, HIDDEN_FINAL_PART_SCALE, 1.8F);
        lastSealSpine.zScale = clamp(lastSealSpine.zScale, HIDDEN_FINAL_PART_SCALE, 1.8F);

        leftShard.pitch = clamp(leftShard.pitch, -1.2F, 1.2F);
        rightShard.pitch = clamp(rightShard.pitch, -1.2F, 1.2F);
        leftShard.yaw = clamp(leftShard.yaw, -1.0F, 1.0F);
        rightShard.yaw = clamp(rightShard.yaw, -1.0F, 1.0F);
        leftShard.pivotY = clamp(leftShard.pivotY, -4.0F, -1.0F);
        rightShard.pivotY = clamp(rightShard.pivotY, -4.0F, -1.0F);

        chestRift.xScale = clamp(chestRift.xScale, 0.85F, 1.5F);
        chestRift.yScale = clamp(chestRift.yScale, 0.85F, 1.5F);
        chestRift.zScale = clamp(chestRift.zScale, 0.85F, 1.5F);
    }

    private static float clamp(float value, float min, float max) {
        return MathHelper.clamp(value, min, max);
    }
}
