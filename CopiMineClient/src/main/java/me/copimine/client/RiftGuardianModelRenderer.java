package me.copimine.client;

import net.minecraft.util.Identifier;

import java.util.Locale;

public final class RiftGuardianModelRenderer {
    private final RiftGuardianModel model = new RiftGuardianModel(RiftGuardianModel.getTexturedModelData().createModel());

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis) {
        return modelForPhase(phaseId, transitionDurationMillis, "IDLE");
    }

    public RiftGuardianModel modelForPhase(String phaseId, long transitionDurationMillis, String animationId) {
        model.setPhase(Phase.fromWireId(phaseId), transitionDurationMillis);
        model.setAnimation(animationId);
        return model;
    }

    public Identifier textureForPhase(String phaseId) {
        return Phase.fromWireId(phaseId).texture();
    }

    private static Identifier texture(String name) {
        return Identifier.of("copimineclient", "textures/entity/" + name);
    }

    public enum Phase {
        AWAKENING("rift_guardian_awakening.png"),
        HUNTER("rift_guardian_hunter.png"),
        DISTORTION("rift_guardian_distortion.png"),
        ABSORPTION("rift_guardian_absorption.png"),
        CATASTROPHE("rift_guardian_catastrophe.png");

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
