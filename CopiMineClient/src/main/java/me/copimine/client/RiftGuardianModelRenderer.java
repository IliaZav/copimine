package me.copimine.client;

import net.minecraft.util.Identifier;

import java.util.Locale;

public final class RiftGuardianModelRenderer {
    private final RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis) {
        return modelForPhase(phaseId, transitionDurationMillis, "IDLE_BREATH");
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis, String animationId) {
        model.setPhase(Phase.fromWireId(phaseId), transitionDurationMillis);
        model.setAnimation(normalizeAnimationId(animationId));
        return model;
    }

    public Identifier textureForPhase(String phaseId) {
        return textureForState(phaseId, "IDLE_BREATH");
    }

    public Identifier textureForState(String phaseId, String animationId) {
        String animation = normalizeAnimationId(animationId);
        if ("FINAL_STRIKE".equals(animation)) {
            return texture("rift_guardian_final_strike.png");
        }
        return Phase.fromWireId(phaseId).texture();
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
        return resolved.wireId();
    }

    public enum Phase {
        AWAKENING("rift_guardian_awakening.png"),
        HUNT("rift_guardian_hunt.png"),
        RIFT("rift_guardian_rift.png"),
        OVERLOAD("rift_guardian_overload.png"),
        RAGE("rift_guardian_rage.png"),
        LAST_SEAL("rift_guardian_last_seal.png");

        private final Identifier texture;

        Phase(String textureName) {
            this.texture = RiftGuardianModelRenderer.texture(textureName);
        }

        public Identifier texture() {
            return texture;
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
