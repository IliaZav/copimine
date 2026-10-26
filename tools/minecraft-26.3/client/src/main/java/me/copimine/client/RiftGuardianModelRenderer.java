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
