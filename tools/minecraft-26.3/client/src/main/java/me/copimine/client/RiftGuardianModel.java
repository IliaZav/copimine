package me.copimine.client;

import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;

/**
 * Enderman renderer adapter for the UUID-bound End Rift guardian.
 *
 * <p>The superclass keeps the Enderman renderer's type contract intact. Its
 * {@link ModelPart} tree is only a compatibility carrier. The exact Chameleon
 * source mesh is queued through the 26.3 deferred collector by the renderer
 * adapter instead of overriding the now-final immediate model draw method.</p>
 */
public final class RiftGuardianModel extends EndermanModel<EndermanRenderState> {
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
        this.compatibilityRoot.visible = false;
    }

    /**
     * Retains the old Enderman model construction contract, but callers must
     * not infer the rendered guardian mesh from this carrier tree.
     */
    public static LayerDefinition createBodyLayer() {
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

    public ChameleonGuardianGeometry.GuardianPose currentPose() {
        return currentPose;
    }

    @Override
    public void setupAnim(EndermanRenderState state) {
        float clipProgress = Float.isFinite(animationElapsedTicks) ? animationElapsedTicks : state.ageInTicks;
        currentPose = UserEndBossAnimationPlayer.sample(animationId, clipProgress);
    }
}
