package me.copimine.client;

import java.util.List;

/** Public compatibility facade for the server-bound End Rift tentacle visual. */
public final class EndRiftTentacleModel {
    public static final String VISUAL_ID = "END_RIFT_TENTACLE_V1";
    public static final List<String> REQUIRED_BONES = EndRiftTentacleRig.REQUIRED_BONES;

    private EndRiftTentacleModel() {
    }

    public static boolean supportsAnimation(String animationId) {
        return EndRiftTentacleAnimator.supportsAnimation(animationId);
    }

    public static EndRiftTentaclePose.TentaclePose pose(String animationId, float progress) {
        return EndRiftTentacleAnimator.poseFor(animationId, progress, 0L);
    }
}
