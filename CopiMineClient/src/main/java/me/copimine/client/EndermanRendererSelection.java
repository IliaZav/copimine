package me.copimine.client;

import net.minecraft.util.Identifier;

import java.util.Objects;

/** Pure selection and cleanup state used by the Enderman renderer mixin. */
public final class EndermanRendererSelection {
    private static final String CLIENT_NAMESPACE = "copimineclient";
    private static final String ENTITY_TEXTURE_PREFIX = "textures/entity/";

    private EndermanRendererSelection() {
    }

    public static Decision select(String entityUuid, String boundBossUuid,
                                  Identifier guardianTexture, boolean textureAvailable) {
        if (entityUuid == null || entityUuid.isBlank()
                || boundBossUuid == null || boundBossUuid.isBlank()
                || !Objects.equals(entityUuid, boundBossUuid)
                || !isValidGuardianTexture(guardianTexture)
                || !textureAvailable) {
            return new Decision(Kind.VANILLA, null);
        }
        return new Decision(Kind.GUARDIAN, guardianTexture);
    }

    public static <T> ModelSwap<T> begin(T vanillaModel, T guardianModel, Decision decision) {
        Objects.requireNonNull(vanillaModel, "vanillaModel");
        boolean useGuardian = decision != null && decision.kind() == Kind.GUARDIAN;
        if (useGuardian) {
            Objects.requireNonNull(guardianModel, "guardianModel");
        }
        return new ModelSwap<>(vanillaModel, useGuardian ? guardianModel : vanillaModel, useGuardian);
    }

    private static boolean isValidGuardianTexture(Identifier texture) {
        return texture != null
                && CLIENT_NAMESPACE.equals(texture.getNamespace())
                && texture.getPath().startsWith(ENTITY_TEXTURE_PREFIX);
    }

    public enum Kind {
        VANILLA,
        GUARDIAN
    }

    public record Decision(Kind kind, Identifier texture) {
        public boolean usesGuardianModel() {
            return kind == Kind.GUARDIAN;
        }
    }

    public static final class ModelSwap<T> {
        private final T vanillaModel;
        private final T guardianModel;
        private boolean active;

        private ModelSwap(T vanillaModel, T guardianModel, boolean active) {
            this.vanillaModel = vanillaModel;
            this.guardianModel = guardianModel;
            this.active = active;
        }

        public T currentModel() {
            return active ? guardianModel : vanillaModel;
        }

        public T restore() {
            active = false;
            return vanillaModel;
        }

        public boolean isActive() {
            return active;
        }
    }
}
