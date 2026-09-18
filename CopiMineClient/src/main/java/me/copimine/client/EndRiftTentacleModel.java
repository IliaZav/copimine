package me.copimine.client;

import java.util.List;

/** Public compatibility facade for the server-bound End Rift tentacle visual. */
public final class EndRiftTentacleModel {
    public static final String VISUAL_ID = "END_RIFT_TENTACLE_V1";
    /** Must stay in lockstep with CopiMineEndEvent.MODEL_RIFT_TENTACLE. */
    public static final int SERVER_CUSTOM_MODEL_DATA = 830017;
    public static final List<String> REQUIRED_BONES = EndRiftTentacleRig.REQUIRED_BONES;

    private EndRiftTentacleModel() {
    }

    public static boolean supportsAnimation(String animationId) {
        return EndRiftTentacleAnimator.supportsAnimation(animationId);
    }

    public static EndRiftTentaclePose.TentaclePose pose(String animationId, float progress) {
        return EndRiftTentacleAnimator.poseFor(animationId, progress, 0L);
    }

    public static boolean isServerCustomModelData(int value) {
        return value == SERVER_CUSTOM_MODEL_DATA;
    }
}
