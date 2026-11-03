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
    /**
     * Clip blending: the pose the previous clip left the rig in, the clip it
     * came from, and how long the incoming clip takes to own the rig.
     *
     * <p>Without this every animation switch snapped: the boss could be
     * mid-swing and teleport to the first frame of the next clip in a single
     * frame, which reads as a broken animation rather than a fast one.</p>
     */
    private ChameleonGuardianGeometry.GuardianPose blendFromPose = null;
    private String blendFromAnimation = "";
    private float blendElapsedTicks = Float.NaN;
    private float blendDurationTicks = 0.0F;

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

    /**
     * Switches the playing clip, remembering where the previous clip left the
     * rig so the transition can be blended instead of snapped.
     */
    public void setAnimation(String animationId) {
        String canonical = BossAnimationId.canonicalWireId(animationId);
        if (canonical.equals(this.animationId)) {
            return;
        }
        this.blendFromPose = this.currentPose;
        this.blendFromAnimation = this.animationId;
        this.blendElapsedTicks = 0.0F;
        this.blendDurationTicks = UserEndBossAnimationPlayer.blendTicks(this.animationId, canonical);
        this.animationId = canonical;
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
        ChameleonGuardianGeometry.GuardianPose target = UserEndBossAnimationPlayer.sample(animationId, clipProgress);
        if (blendFromPose != null && blendDurationTicks > 0.0F && Float.isFinite(clipProgress)) {
            float weight = Math.min(1.0F, clipProgress / blendDurationTicks);
            // Ease out: the first frames move the most, which is what makes a
            // fast switch still read as a deliberate one.
            float eased = 1.0F - (1.0F - weight) * (1.0F - weight);
            target = UserEndBossAnimationPlayer.blend(blendFromPose, target, eased);
            if (weight >= 1.0F) {
                blendFromPose = null;
                blendFromAnimation = "";
                blendDurationTicks = 0.0F;
            }
        }
        currentPose = target;
    }
}
