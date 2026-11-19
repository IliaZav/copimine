package me.copimine.client;

import java.util.LinkedHashMap;
import java.util.Map;
import me.copimine.client.RiftGuardianModelRenderer.Phase;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Renderer adapter for the authored End Rift Guardian body.
 *
 * <p>The model is typed to the base {@link LivingEntityRenderState}, not to
 * {@code EndermanRenderState}. The Guardian body is worn by an Enderman (the
 * boss) and by Pillager carriers (echo decoys and replicas), so a narrower type
 * made the model's bridge method cast an {@code IllagerRenderState} to
 * {@code EndermanRenderState} and throw once per decoy per frame.</p>
 *
 * <p>The exact Chameleon source mesh is queued through the 26.3 deferred
 * collector by the renderer adapter instead of overriding the now-final
 * immediate model draw method. The vanilla {@link ModelPart} tree is only a
 * compatibility carrier and is kept invisible.</p>
 */
public final class RiftGuardianModel extends EntityModel<LivingEntityRenderState> {
    static final int TEXTURE_SIZE = UserEndBossModelData.TEXTURE_WIDTH;
    static final String USER_MODEL_RESOURCE = UserEndBossModelData.RESOURCE;

    private final ModelPart compatibilityRoot;
    private Phase phase = Phase.AWAKENING;
    private float animationElapsedTicks = Float.NaN;
    private long transitionDurationMillis;
    private ChameleonGuardianGeometry.GuardianPose currentPose = ChameleonGuardianGeometry.GuardianPose.identity();

    /**
     * Clip blending state for ONE body: the clip that owns the rig, the pose
     * the previous clip left it in, and how long the incoming clip takes to
     * own it.
     *
     * <p>Without blending every animation switch snapped: the boss could be
     * mid-swing and teleport to the first frame of the next clip in a single
     * frame, which reads as a broken animation rather than a fast one.</p>
     *
     * <p>This state is per <em>body</em>, never per model, because one
     * {@link RiftGuardianModel} is owned by one renderer instance and every
     * Echo decoy is a Pillager on the single {@code PillagerRenderer}. With a
     * single shared state the last submitted decoy decided the clip and the
     * "previous pose" of the next one, so a decoy that was flinching could be
     * drawn mostly in another decoy's idle pose - exactly the "obviously
     * broken animations" contract 15.3 forbids as an accidental giveaway.</p>
     */
    private static final class ClipState {
        private String animationId = BossAnimationId.IDLE_BREATH.wireId();
        private ChameleonGuardianGeometry.GuardianPose blendFromPose;
        private float blendDurationTicks;
        private ChameleonGuardianGeometry.GuardianPose lastPose =
                ChameleonGuardianGeometry.GuardianPose.identity();
    }

    /** Key for callers that do not own a body (tests and tooling). */
    static final String DEFAULT_CLIP_KEY = "@default";
    /** A party plus its echoes and replicas never needs more than this. */
    private static final int MAX_CLIP_KEYS = 64;

    private final Map<String, ClipState> clips = new LinkedHashMap<>();
    private String activeClipKey = DEFAULT_CLIP_KEY;

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
        setAnimation(activeClipKey, animationId);
    }

    /**
     * Selects the body the following {@link #setupAnim} call belongs to and
     * switches its clip.
     *
     * @param clipKey stable per-body key (the entity UUID in production)
     */
    public void setAnimation(String clipKey, String animationId) {
        setClipKey(clipKey);
        String canonical = BossAnimationId.canonicalWireId(animationId);
        ClipState clip = clipFor(activeClipKey);
        if (canonical.equals(clip.animationId)) {
            return;
        }
        clip.blendFromPose = clip.lastPose;
        clip.blendDurationTicks = UserEndBossAnimationPlayer.blendTicks(clip.animationId, canonical);
        clip.animationId = canonical;
    }

    /** Points the model at the body the next {@link #setupAnim} will pose. */
    public void setClipKey(String clipKey) {
        this.activeClipKey = clipKey == null || clipKey.isBlank() ? DEFAULT_CLIP_KEY : clipKey;
    }

    String activeClipKey() {
        return activeClipKey;
    }

    private ClipState clipFor(String key) {
        ClipState clip = clips.get(key);
        if (clip != null) {
            return clip;
        }
        if (clips.size() >= MAX_CLIP_KEYS) {
            // A long session must not grow this map without bound. Keys belong
            // to living bodies, so clearing at the cap is safe: the next
            // submission rebuilds its own clip from the identity pose.
            clips.clear();
        }
        clip = new ClipState();
        clips.put(key, clip);
        return clip;
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
    public void setupAnim(LivingEntityRenderState state) {
        ClipState clip = clipFor(activeClipKey);
        float clipProgress = Float.isFinite(animationElapsedTicks) ? animationElapsedTicks : state.ageInTicks;
        ChameleonGuardianGeometry.GuardianPose target =
                UserEndBossAnimationPlayer.sample(clip.animationId, clipProgress);
        if (clip.blendFromPose != null && clip.blendDurationTicks > 0.0F && Float.isFinite(clipProgress)) {
            float weight = Math.min(1.0F, clipProgress / clip.blendDurationTicks);
            // Ease out: the first frames move the most, which is what makes a
            // fast switch still read as a deliberate one.
            float eased = 1.0F - (1.0F - weight) * (1.0F - weight);
            target = UserEndBossAnimationPlayer.blend(clip.blendFromPose, target, eased);
            if (weight >= 1.0F) {
                clip.blendFromPose = null;
                clip.blendDurationTicks = 0.0F;
            }
        }
        clip.lastPose = target;
        currentPose = target;
    }
}
