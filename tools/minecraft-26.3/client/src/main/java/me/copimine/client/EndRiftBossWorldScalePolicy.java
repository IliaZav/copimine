package me.copimine.client;

/** Keeps the supplied Bedrock guardian at its authored five-block silhouette. */
public final class EndRiftBossWorldScalePolicy {
    public static final float SUPPLIED_GUARDIAN_SCALE = 1.0F;
    private static final float SUPPLIED_MESH_FOOT_Y_IN_RENDER_SPACE = 1.501F;
    private static final float IMPORTED_MESH_FOOT_Y = 0.0182939F;

    private EndRiftBossWorldScalePolicy() {
    }

    public static float renderScale() {
        return SUPPLIED_GUARDIAN_SCALE;
    }

    /**
     * The supplied Bedrock mesh ends at a small positive local Y while the
     * vanilla living renderer expects a 1.501-block foot baseline. After its
     * Y reflection, this local translation lowers the mesh onto the server's
     * grounded entity origin.
     */
    public static float modelOriginCorrectionY() {
        return SUPPLIED_MESH_FOOT_Y_IN_RENDER_SPACE - IMPORTED_MESH_FOOT_Y;
    }
}
