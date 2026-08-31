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
        model.setAnimation(normalizeAnimationId(animationId));
        return model;
    }

    public Identifier textureForPhase(String phaseId) {
        return Phase.fromWireId(phaseId).texture();
    }

    private static Identifier texture(String name) {
        return Identifier.of("copimineclient", "textures/entity/" + name);
    }

    private static String normalizeAnimationId(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return "IDLE";
        }
        String normalized = animationId.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IDLE",
                    "IDLE_BREATH",
                    "DAMAGED_FLINCH",
                    "TELEPORT_RIP",
                    "CAST_CHARGE",
                    "CAST_RELEASE",
                    "CAST_IMPACT",
                    "PHASE_SHIFT",
                    "FINAL_AWAKENING",
                    "DEFEAT_COLLAPSE",
                    "ABSORPTION_CHANNEL",
                    "JUDGMENT_CAST",
                    "EXHAUSTED",
                    "SPELL_VOID_BLAST",
                    "SPELL_RIFT_PROJECTILE",
                    "SPELL_RIFT_ARROWS",
                    "SPELL_ARROW_SALVO",
                    "SPELL_VOID_MARK",
                    "SPELL_SUMMON_SERVANTS",
                    "SPELL_SUMMON",
                    "SPELL_WILL_DISTORTION",
                    "SPELL_ARENA_INFERNO",
                    "SPELL_IMPACT" -> normalized;
            default -> "IDLE";
        };
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
