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

    public Identifier textureForState(String phaseId, String animationId) {
        // The phase changes are carried by the server animation/VFX state;
        // the supplied boss mesh and its 128x128 skin remain the single
        // authoritative client asset for every phase.
        return texture("end_rift_user_boss.png");
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
        AWAKENING,
        HUNT,
        RIFT,
        OVERLOAD,
        RAGE,
        LAST_SEAL;

        private final Identifier texture;

        Phase() {
            this.texture = RiftGuardianModelRenderer.texture("end_rift_user_boss.png");
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
