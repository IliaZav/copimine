package me.copimine.client;

import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.EndermanEntity;

/**
 * Enderman renderer adapter for the UUID-bound End Rift guardian.
 *
 * <p>The superclass keeps the Enderman renderer's type contract intact. Its
 * {@link ModelPart} tree is only a compatibility carrier: this model never
 * sends those cuboids to the vertex buffer. The exact Chameleon source mesh
 * is emitted directly by {@link ChameleonGuardianRenderer} instead.</p>
 */
public final class RiftGuardianModel extends EndermanEntityModel<EndermanEntity> {
    static final int TEXTURE_SIZE = UserEndBossModelData.TEXTURE_WIDTH;
    static final String USER_MODEL_RESOURCE = UserEndBossModelData.RESOURCE;

    private final ModelPart compatibilityRoot;
    private Phase phase = Phase.AWAKENING;
    private String animationId = BossAnimationId.IDLE_BREATH.wireId();
    private float animationElapsedTicks = Float.NaN;
    private long transitionDurationMillis;
    private ChameleonGuardianGeometry.GuardianPose currentPose = ChameleonGuardianGeometry.GuardianPose.identity();

    public RiftGuardianModel(ModelPart root) {
        super(root);
        this.compatibilityRoot = root;
    }

    /**
     * Retains the old Enderman model construction contract, but callers must
     * not infer the rendered guardian mesh from this carrier tree.
     */
    public static TexturedModelData getTexturedModelData() {
        return UserEndBossModelData.create();
    }

    public void setPhase(Phase phase, long transitionDurationMillis) {
        this.phase = phase == null ? Phase.AWAKENING : phase;
        this.transitionDurationMillis = Math.max(0L, Math.min(600_000L, transitionDurationMillis));
    }

    public void setAnimation(String animationId) {
        this.animationId = BossAnimationId.canonicalWireId(animationId);
    }

    public void setAnimationElapsedTicks(float animationElapsedTicks) {
        this.animationElapsedTicks = Float.isFinite(animationElapsedTicks)
                ? Math.max(0.0F, animationElapsedTicks) : Float.NaN;
    }

    public ModelPart getPart() {
        return compatibilityRoot;
    }

    int directGeometryCubeCount() {
        return ChameleonGuardianGeometry.load().cubeCount();
    }

    int directGeometryFaceCount() {
        return ChameleonGuardianGeometry.load().faceCount();
    }

    boolean usesVanillaCuboidGuardianMesh() {
        return false;
    }

    ChameleonGuardianGeometry.GuardianPose currentPose() {
        return currentPose;
    }

    @Override
    public void setAngles(EndermanEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        float clipProgress = Float.isFinite(animationElapsedTicks)
                ? animationElapsedTicks : animationProgress;
        currentPose = UserEndBossAnimationPlayer.sample(animationId, clipProgress);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        ChameleonGuardianRenderer.render(matrices, vertices, light, overlay, color, currentPose);
    }
}
