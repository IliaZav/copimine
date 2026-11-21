package me.copimine.client;

import net.minecraft.util.Identifier;

import java.util.Locale;

public final class RiftGuardianModelRenderer {
    private final RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis) {
        return modelForPhase(phaseId, transitionDurationMillis, "IDLE_BREATH");
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis, String animationId) {
        return modelForPhase(phaseId, transitionDurationMillis, animationId, Float.NaN);
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis,
                                           String animationId, float animationElapsedTicks) {
        model.setPhase(Phase.fromWireId(phaseId), transitionDurationMillis);
        model.setAnimation(normalizeAnimationId(animationId));
        model.setAnimationElapsedTicks(animationElapsedTicks);
        return model;
    }

    public Identifier textureForPhase(String phaseId) {
        return textureForState(phaseId, "IDLE_BREATH");
    }

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
        return Identifier.of("copimineclient", "textures/entity/" + name);
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
        return authoredClipFor(resolved).wireId();
    }

    /**
     * Known actions that ship no clip of their own must still show authored motion instead of
     * freezing in the identity pose. This is presentation only: server timing stays authoritative.
     * An explicit UNKNOWN keeps the rest pose on purpose.
     */
    private static BossAnimationId authoredClipFor(BossAnimationId resolved) {
        return switch (resolved) {
            case CAST_CHARGE, CAST_RELEASE, CAST_IMPACT, RECOVERY, MARK_CONTROL,
                    SPELL_VOID_BLAST, SPELL_RIFT_PROJECTILE, SPELL_RIFT_ARROWS,
                    SPELL_ARROW_SALVO, SPELL_VOID_MARK, FINAL_STRIKE -> BossAnimationId.CHEST_STRIKE;
            case SUMMON_CHANNEL, SPELL_SUMMON, SPELL_SUMMON_SERVANTS,
                    SPELL_RIFT_OBELISKS, SPELL_ARENA_INFERNO -> BossAnimationId.GROUND_SLAM;
            default -> resolved;
        };
    }

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
