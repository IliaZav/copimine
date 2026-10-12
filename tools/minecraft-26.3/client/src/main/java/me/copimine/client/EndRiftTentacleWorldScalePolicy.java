package me.copimine.client;

/**
 * Keeps the authored tentacle rig's model-unit to world-unit conversion in
 * one place. The source Kagune model is authored in compact Blockbench units;
 * its imported coordinates are rendered directly and need one compact scale.
 */
public final class EndRiftTentacleWorldScalePolicy {
    public static final float MODEL_UNITS_PER_BLOCK = 16.0F;
    /** Keeps the supplied 7.8681-unit tentacle compact instead of arena-dominating. */
    public static final float BASE_RENDER_SCALE = 0.40F;
    /** Renders the 7.8681-unit authored height at about 4.9 world blocks. */
    public static final float BASE_LENGTH_RENDER_SCALE = 0.62F;

    private EndRiftTentacleWorldScalePolicy() {
    }

    /**
     * Returns the matrix scale for the imported source model. The importer
     * leaves source dimensions in Blockbench units instead of pre-scaling them.
     */
    public static float rendererScaleForRig(float rigScale) {
        float safe = Float.isFinite(rigScale) && rigScale > 0.0F ? rigScale : 1.0F;
        return safe * BASE_RENDER_SCALE;
    }

    public static float rendererLengthScaleForRig(float rigScale) {
        float safe = Float.isFinite(rigScale) && rigScale > 0.0F ? rigScale : 1.0F;
        return safe * BASE_LENGTH_RENDER_SCALE;
    }

    public static float worldHeightForAuthoredUnits(float authoredUnits,
                                                    float rigScale) {
        float safeUnits = Float.isFinite(authoredUnits) ? Math.max(0.0F, authoredUnits) : 0.0F;
        float safe = Float.isFinite(rigScale) && rigScale > 0.0F ? rigScale : 1.0F;
        return safeUnits / MODEL_UNITS_PER_BLOCK * safe * BASE_LENGTH_RENDER_SCALE;
    }
}
