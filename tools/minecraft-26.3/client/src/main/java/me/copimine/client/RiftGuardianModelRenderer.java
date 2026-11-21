package me.copimine.client;

import java.util.Locale;
import net.minecraft.resources.Identifier;

public final class RiftGuardianModelRenderer {
    private final RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.createBodyLayer().bakeRoot());

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis) {
        return modelForPhase(phaseId, transitionDurationMillis, "IDLE_BREATH");
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis, String animationId) {
        return modelForPhase(phaseId, transitionDurationMillis, animationId, Float.NaN);
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis,
                                           String animationId, float animationElapsedTicks) {
        return modelForPhase(null, phaseId, transitionDurationMillis, animationId, animationElapsedTicks);
    }

    /**
     * Selects the model for one concrete body.
     *
     * @param clipKey stable per-body key (the entity UUID in production). One
     *                model instance is shared by every entity a renderer draws,
     *                so the clip and blend state must be keyed per body or one
     *                decoy animates with another decoy's clip.
     */
    public RiftGuardianModel modelForPhase(String clipKey, String phaseId, long transitionDurationMillis,
                                           String animationId, float animationElapsedTicks) {
        model.setPhase(Phase.fromWireId(phaseId), transitionDurationMillis);
        model.setAnimation(clipKey, normalizeAnimationId(animationId));
        model.setAnimationElapsedTicks(animationElapsedTicks);
        return model;
    }

    public Identifier textureForPhase(String phaseId) {
        return textureForState(phaseId, "IDLE_BREATH");
    }

    /**
     * The single authored boss skin. Exposed for render paths that reach the
     * Guardian body from an entity type whose own renderer supplies a different
     * texture (a Pillager decoy, for example), so the body keeps one skin
     * instead of being drawn with the carrier's UV atlas.
     */
    public static Identifier bossTexture() {
        return texture(STAGE_TEXTURES[0]);
    }

    /**
     * Per-stage boss skin.  The owner asked for a gradual recolor that ends
     * "torn, exhausted, on its last breath", so every phase carries its own
     * 128x128 sheet painted from one master by
     * {@code scripts/minecraft/GenerateEndRiftEntityTextures.py}.  Stage 5 is
     * the last seal: drained, desaturated and physically torn.
     */
    private static final String[] STAGE_TEXTURES = {
            "end_rift_user_boss_stage0.png",
            "end_rift_user_boss_stage1.png",
            "end_rift_user_boss_stage2.png",
            "end_rift_user_boss_stage3.png",
            "end_rift_user_boss_stage4.png",
            "end_rift_user_boss_stage5.png",
    };

    public Identifier textureForState(String phaseId, String animationId) {
        return texture(STAGE_TEXTURES[stageFor(phaseId)]);
    }

    /** 0 (awakening) .. 5 (last seal, torn). */
    static int stageFor(String phaseId) {
        return switch (Phase.fromWireId(phaseId)) {
            case AWAKENING -> 0;
            case HUNT -> 1;
            case RIFT -> 2;
            case OVERLOAD -> 3;
            case RAGE -> 4;
            case LAST_SEAL -> 5;
        };
    }

    /** The canonical whole-body sheet, kept for authoring previews. */
    public static Identifier masterTexture() {
        return texture(STAGE_TEXTURES[0]);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("copimineclient", "textures/entity/" + name);
    }

    static String normalizeAnimationId(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return BossAnimationId.IDLE_BREATH.wireId();
        }
        String raw = animationId.replace('|', '_').trim();
        BossAnimationId resolved = BossAnimationId.fromWire(raw);
        if (resolved == BossAnimationId.UNKNOWN
                && !BossAnimationId.UNKNOWN.wireId().equalsIgnoreCase(raw)) {
            CopiMineClientLogger.error("Unknown End Rift boss animation: " + raw
                    + "; keeping an explicit UNKNOWN pose", null);
        }
        return resolved.wireId();
    }

    // Keep canonical action identity through the renderer boundary. The animation
    // player owns clip lookup/fallback; mapping here bypassed real cast clips and
    // also changed the playback-rate key used by contact synchronization.

    public enum Phase {
        AWAKENING(0),
        HUNT(1),
        RIFT(2),
        OVERLOAD(3),
        RAGE(4),
        LAST_SEAL(5);

        private final int stage;

        Phase(int stage) {
            this.stage = stage;
        }

        public Identifier texture() {
            return RiftGuardianModelRenderer.texture(STAGE_TEXTURES[stage]);
        }

        static Phase fromWireId(String phaseId) {
            if (phaseId == null || phaseId.isBlank()) {
                return AWAKENING;
            }
            String normalized = phaseId.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace("END_RIFT_GUARDIAN_", "")
                    .replace("RIFT_GUARDIAN_", "")
                    .replace("END_RIFT_", "");
            for (Phase phase : values()) {
                if (phase.name().equals(normalized)) {
                    return phase;
                }
            }
            return AWAKENING;
        }
    }
}
