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
            return vanilla();
        }
        return new Decision(Kind.GUARDIAN, guardianTexture,
                "END_RIFT_GUARDIAN_V1", "end_rift_guardian", "END_RIFT_GUARDIAN");
    }

    /**
     * Resolves the complete event visual contract before a renderer is
     * touched.  A non-event or unbound entity is deliberately returned as
     * vanilla; the custom texture path is never allowed to masquerade as a
     * custom geometry selection.
     */
    public static Decision selectVisual(String entityUuid, String visualId,
                                        String boundBossUuid, Identifier texture,
                                        boolean textureAvailable) {
        if (entityUuid == null || entityUuid.isBlank()) {
            return vanilla();
        }
        if (boundBossUuid != null && !boundBossUuid.isBlank()) {
            return select(entityUuid, boundBossUuid, texture, textureAvailable);
        }
        if (visualId == null || visualId.isBlank() || !textureAvailable
                || !isValidGuardianTexture(texture)) {
            return vanilla();
        }
        return switch (visualId.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "END_RIFT_ENDERMAN_V1" -> new Decision(Kind.EVENT_ENDERMAN, texture,
                    "END_RIFT_ENDERMAN_V1", "end_rift_enderman_v1", "END_RIFT_ENDERMAN");
            case "END_RIFT_ELITE_V1" -> new Decision(Kind.ELITE, texture,
                    "END_RIFT_ELITE_V1", "end_rift_elite_v1", "END_RIFT_ELITE");
            case "END_RIFT_GUARDIAN_V1" -> new Decision(Kind.GUARDIAN, texture,
                    "END_RIFT_GUARDIAN_V1", "end_rift_guardian", "END_RIFT_GUARDIAN");
            default -> vanilla();
        };
    }

    /**
     * Formats the same complete contract for every event visual, including
     * the spider and skeleton renderers that do not use the Enderman model
     * adapter.  This is intentionally diagnostic-only; the renderer still
     * performs its own type-scoped selection.
     */
    public static String diagnosticLineForVisual(String visualId, Identifier texture,
                                                 boolean textureAvailable) {
        String normalized = visualId == null ? "" : visualId.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.equals("END_RIFT_GUARDIAN")) {
            normalized = "END_RIFT_GUARDIAN_V1";
        }
        if (normalized.isBlank()) {
            return vanilla().diagnosticLine() + ", resourcePresent=" + textureAvailable;
        }
        if (normalized.equals("END_RIFT_ENDERMAN_V1")
                || normalized.equals("END_RIFT_ELITE_V1")
                || normalized.equals("END_RIFT_GUARDIAN_V1")) {
            Decision decision = selectVisual("diagnostic", normalized, null, texture, textureAvailable);
            return decision.diagnosticLine() + ", resourcePresent=" + textureAvailable;
        }
        return switch (normalized) {
            case "END_RIFT_SPIDER_V1" -> formatNonEndermanDiagnostic(
                    textureAvailable, "EVENT_SPIDER", normalized,
                    "end_rift_spider_v1", "END_RIFT_SPIDER", texture);
            case "END_RIFT_SKELETON_V1" -> formatNonEndermanDiagnostic(
                    textureAvailable, "EVENT_SKELETON", normalized,
                    "vanilla_skeleton", "END_RIFT_SKELETON", texture);
            case "END_RIFT_ELITE_SKELETON_V1" -> formatNonEndermanDiagnostic(
                    textureAvailable, "ELITE_SKELETON", normalized,
                    "vanilla_skeleton", "END_RIFT_ELITE_SKELETON", texture);
            case "END_RIFT_TENTACLE_V1" -> formatNonEndermanDiagnostic(
                    textureAvailable, "TENTACLE", normalized,
                    "end_rift_tentacle_rig", "END_RIFT_TENTACLE", texture);
            default -> "variant=UNKNOWN, model=" + normalized
                    + ", geometry=UNKNOWN, texture=" + (texture == null ? "-" : texture)
                    + ", animationSet=UNKNOWN, resourcePresent=" + textureAvailable;
        };
    }

    private static String formatNonEndermanDiagnostic(boolean resourceAvailable,
                                                      String variant, String model,
                                                      String geometry, String animationSet,
                                                      Identifier texture) {
        if (!resourceAvailable || !isValidGuardianTexture(texture)) {
            return vanilla().diagnosticLine() + ", requestedVisual=" + model
                    + ", resourcePresent=" + resourceAvailable;
        }
        return "variant=" + variant
                + ", model=" + model
                + ", geometry=" + geometry
                + ", texture=" + texture
                + ", animationSet=" + animationSet
                + ", resourcePresent=" + resourceAvailable;
    }

    public static <T> ModelSwap<T> begin(T vanillaModel, T guardianModel, Decision decision) {
        Objects.requireNonNull(vanillaModel, "vanillaModel");
        boolean useGuardian = decision != null && decision.kind() != Kind.VANILLA;
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

    private static Decision vanilla() {
        return new Decision(Kind.VANILLA, null,
                "VANILLA_ENDERMAN", "vanilla", "VANILLA_ENDERMAN");
    }

    public enum Kind {
        VANILLA,
        EVENT_ENDERMAN,
        ELITE,
        GUARDIAN
    }

    public record Decision(Kind kind, Identifier texture, String modelId,
                           String geometryId, String animationSet) {
        public Decision(Kind kind, Identifier texture) {
            this(kind, texture,
                    kind == Kind.GUARDIAN ? "END_RIFT_GUARDIAN_V1" : "VANILLA_ENDERMAN",
                    kind == Kind.GUARDIAN ? "end_rift_guardian" : "vanilla",
                    kind == Kind.GUARDIAN ? "END_RIFT_GUARDIAN" : "VANILLA_ENDERMAN");
        }

        public boolean usesGuardianModel() {
            return kind == Kind.GUARDIAN;
        }

        public boolean usesCustomModel() {
            return kind != Kind.VANILLA;
        }

        /**
         * Stable, human-readable proof of the complete renderer selection.
         * Keep the fields explicit so a log or in-game diagnostic cannot hide
         * a texture-only replacement behind a generic "custom" label.
         */
        public String diagnosticLine() {
            return "variant=" + kind
                    + ", model=" + modelId
                    + ", geometry=" + geometryId
                    + ", texture=" + (texture == null ? "-" : texture)
                    + ", animationSet=" + animationSet;
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
