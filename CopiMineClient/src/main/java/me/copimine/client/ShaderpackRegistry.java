package me.copimine.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ShaderpackRegistry {
    public enum RuntimeKind {
        IRIS_SHADERPACK,
        CANVAS_UNAVAILABLE,
        OPTIFINE_UNAVAILABLE,
        CUSTOM_UNAVAILABLE,
        FALLBACK_ONLY
    }

    public enum Compatibility {
        SUPPORTED,
        UNSUPPORTED
    }

    public record ShaderDefinition(
            String id,
            String displayName,
            RuntimeKind runtime,
            Compatibility compatibility,
            int priority,
            List<String> requiredMods,
            String fallback,
            String status,
            String description,
            String preview,
            String exportPath,
            String validationResult
    ) {
    }

    public record ShaderpackProfile(
            String id,
            String displayName,
            String zipName,
            String resourcePath,
            RuntimeKind runtimeKind,
            String fallbackEffectId,
            boolean darkPreferred,
            int priority,
            List<String> requiredMods,
            String preview,
            String originalName,
            String note
    ) {
        public boolean irisCompatible() {
            return runtimeKind == RuntimeKind.IRIS_SHADERPACK;
        }

        public String normalizedFallbackEffectId() {
            return fallbackEffectId == null || fallbackEffectId.isBlank()
                    ? "CHAOS"
                    : fallbackEffectId.trim().toUpperCase(Locale.ROOT);
        }
    }

    private static final Map<String, ShaderpackProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, String> EFFECT_TO_SHADERPACK = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        register("acid_shaders", "Acid Shaders", "acid_shaders.zip", RuntimeKind.IRIS_SHADERPACK, "COLOR_CONVOLVE", false, 60, List.of("iris"), "acid", "Acid Shaders.zip", "psychedelic hue shift and saturation");
        register("crucify", "Crucify", "crucify.zip", RuntimeKind.IRIS_SHADERPACK, "INVERT", true, 70, List.of("iris"), "dark", "Crucify.zip", "high-contrast dark distortion");
        register("cursed_metamorphopsia", "Cursed Metamorphopsia", "cursed_metamorphopsia.zip", RuntimeKind.IRIS_SHADERPACK, "WOBBLE", true, 80, List.of("iris"), "cursed", "Cursed Shader (Metamorphopsia).zip", "warped cursed world look");
        register("jelly_world", "Jelly World", "jelly_world.zip", RuntimeKind.FALLBACK_ONLY, "BLOBS", false, 10, List.of(), "jelly", "JELLY WORLD.zip", "fallback-only vanilla shader override; no root shaders/ directory for Iris runtime");
        register("nms_1_6", "NMS 1.6", "nms_1_6.zip", RuntimeKind.IRIS_SHADERPACK, "GREEN_NOISE", true, 55, List.of("iris"), "nms", "NMS 1.6.zip", "glow-heavy green ambient pack");
        register("white_sharp_1_2", "White Sharp 1.2", "white_sharp_1_2.zip", RuntimeKind.IRIS_SHADERPACK, "DESATURATE", false, 35, List.of("iris"), "sharp", "white sharp shader_1.2.zip", "sharp bright clarity pack");
        register("ctr_vcr", "CTR-VCR", "ctr_vcr.zip", RuntimeKind.IRIS_SHADERPACK, "SCAN_PINCUSHION", true, 85, List.of("iris"), "crt", "CTR-VCR_v1.4.4.zip", "CRT and VHS-like scan distortion");
        register("lsd_shader", "LSD Shader", "lsd_shader.zip", RuntimeKind.IRIS_SHADERPACK, "COLOR_CONVOLVE", false, 90, List.of("iris"), "lsd", "LSD_Sshader_v1_3.zip", "rainbow warp and lsd-style color shift");
        register("trippy_shaderpack", "Trippy Shaderpack", "trippy_shaderpack.zip", RuntimeKind.IRIS_SHADERPACK, "DESATURATE", false, 95, List.of("iris"), "trippy", "Trippy-Shaderpack master.zip", "strong psychedelic distortion with adjustable curvature and waviness");

        alias("acid", "acid_shaders");
        alias("crucify", "crucify");
        alias("cursed", "cursed_metamorphopsia");
        alias("metamorphopsia", "cursed_metamorphopsia");
        alias("jelly", "jelly_world");
        alias("jelly_world", "jelly_world");
        alias("nms", "nms_1_6");
        alias("white", "white_sharp_1_2");
        alias("sharp", "white_sharp_1_2");
        alias("ctr", "ctr_vcr");
        alias("vcr", "ctr_vcr");
        alias("lsd", "lsd_shader");
        alias("trippy", "trippy_shaderpack");

        EFFECT_TO_SHADERPACK.put("DESATURATE", "trippy_shaderpack.zip");
        EFFECT_TO_SHADERPACK.put("COLOR_CONVOLVE", "ctr_vcr.zip");
        EFFECT_TO_SHADERPACK.put("SCAN_PINCUSHION", "cursed_metamorphopsia.zip");
        EFFECT_TO_SHADERPACK.put("GREEN_NOISE", "lsd_shader.zip");
        EFFECT_TO_SHADERPACK.put("INVERT", "crucify.zip");
        EFFECT_TO_SHADERPACK.put("WOBBLE", "nms_1_6.zip");
        EFFECT_TO_SHADERPACK.put("BLOBS", "acid_shaders.zip");
        EFFECT_TO_SHADERPACK.put("PENCIL", "cursed_metamorphopsia.zip");
        EFFECT_TO_SHADERPACK.put("CHAOS", "random");
    }

    public List<ShaderpackProfile> profiles() {
        return List.copyOf(PROFILES.values());
    }

    public List<ShaderDefinition> definitions(Map<String, ShaderpackExporter.ExportResult> exportResults) {
        return PROFILES.values().stream()
                .map(profile -> {
                    ShaderpackExporter.ExportResult export = exportResults == null ? null : exportResults.get(profile.zipName().toLowerCase(Locale.ROOT));
                    boolean supported = profile.irisCompatible()
                            && export != null
                            && export.validZip()
                            && export.irisCompatible();
                    if (!profile.irisCompatible() && export != null && export.validZip()) {
                        supported = false;
                    }
                    String exportPath = export == null || export.runtimeTarget() == null ? "" : export.runtimeTarget().toString();
                    String validation = export == null ? "not-exported" : export.status();
                    return new ShaderDefinition(
                            profile.id(),
                            profile.displayName(),
                            profile.runtimeKind(),
                            supported ? Compatibility.SUPPORTED : Compatibility.UNSUPPORTED,
                            profile.priority(),
                            profile.requiredMods(),
                            profile.normalizedFallbackEffectId(),
                            supported ? "SUPPORTED" : "UNSUPPORTED",
                            profile.note(),
                            profile.preview(),
                            exportPath,
                            validation
                    );
                })
                .toList();
    }

    public ShaderpackProfile byZipName(String zipName) {
        if (zipName == null || zipName.isBlank()) {
            return null;
        }
        String normalized = zipName.trim().toLowerCase(Locale.ROOT);
        for (ShaderpackProfile profile : PROFILES.values()) {
            if (profile.zipName().equalsIgnoreCase(normalized)) {
                return profile;
            }
        }
        return null;
    }

    public ShaderpackProfile byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT).replace(".zip", "");
        String canonical = ALIASES.getOrDefault(normalized, normalized);
        return PROFILES.get(canonical);
    }

    public ShaderpackProfile resolveForEffect(String effectId, String requestedShaderpack) {
        ShaderpackProfile explicit = resolveExplicit(requestedShaderpack);
        if (explicit != null) {
            return explicit;
        }
        String normalized = effectId == null ? "CHAOS" : effectId.trim().toUpperCase(Locale.ROOT);
        String mapped = EFFECT_TO_SHADERPACK.getOrDefault(normalized, "random");
        if ("random".equalsIgnoreCase(mapped)) {
            return randomIrisCompatible();
        }
        ShaderpackProfile profile = byZipName(mapped);
        return profile == null ? randomIrisCompatible() : profile;
    }

    public ShaderpackProfile resolveRandom(boolean darkOnly) {
        return randomIrisCompatible(darkOnly);
    }

    public boolean isKnownShaderpack(String value) {
        return resolveExplicit(value) != null;
    }

    public List<String> knownShaderpackIds() {
        return new ArrayList<>(PROFILES.keySet());
    }

    public List<String> knownZipNames() {
        return PROFILES.values().stream().map(ShaderpackProfile::zipName).toList();
    }

    public ShaderpackProfile randomIrisCompatible() {
        return randomIrisCompatible(false);
    }

    public ShaderpackProfile randomIrisCompatible(boolean darkOnly) {
        List<ShaderpackProfile> irisCompatible = PROFILES.values().stream()
                .filter(ShaderpackProfile::irisCompatible)
                .filter(profile -> !darkOnly || profile.darkPreferred())
                .toList();
        if (irisCompatible.isEmpty() && darkOnly) {
            return randomIrisCompatible(false);
        }
        return irisCompatible.get(ThreadLocalRandom.current().nextInt(irisCompatible.size()));
    }

    public String shaderpackRuntimeName(String zipName) {
        ShaderpackProfile profile = byZipName(zipName);
        if (profile == null) {
            return "copimine_" + (zipName == null ? "unknown.zip" : zipName.toLowerCase(Locale.ROOT));
        }
        return "copimine_" + profile.id() + ".zip";
    }

    private ShaderpackProfile resolveExplicit(String requestedShaderpack) {
        if (requestedShaderpack == null || requestedShaderpack.isBlank()) {
            return null;
        }
        String normalized = requestedShaderpack.trim();
        if ("random".equalsIgnoreCase(normalized)) {
            return randomIrisCompatible();
        }
        ShaderpackProfile byZip = byZipName(normalized);
        if (byZip != null) {
            return byZip;
        }
        return byId(normalized.replace(".zip", ""));
    }

    private static void register(
            String id,
            String displayName,
            String zipName,
            RuntimeKind runtimeKind,
            String fallbackEffectId,
            boolean darkPreferred,
            int priority,
            List<String> requiredMods,
            String preview,
            String originalName,
            String note
    ) {
        PROFILES.put(id, new ShaderpackProfile(
                id,
                displayName,
                zipName,
                "assets/copimineclient/shaderpacks/" + zipName,
                runtimeKind,
                fallbackEffectId,
                darkPreferred,
                priority,
                List.copyOf(requiredMods),
                preview,
                originalName,
                note
        ));
    }

    private static void alias(String alias, String profileId) {
        ALIASES.put(alias.toLowerCase(Locale.ROOT), profileId);
    }
}
