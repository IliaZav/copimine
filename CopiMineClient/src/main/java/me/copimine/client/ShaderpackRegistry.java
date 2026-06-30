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
        FALLBACK_ONLY
    }

    public record ShaderpackProfile(
            String id,
            String zipName,
            String resourcePath,
            RuntimeKind runtimeKind,
            String fallbackEffectId,
            boolean darkPreferred,
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

    static {
        register("acid_shaders", "acid_shaders.zip", RuntimeKind.IRIS_SHADERPACK, "COLOR_CONVOLVE", false, "Acid Shaders.zip", "psychedelic hue shift and saturation");
        register("crucify", "crucify.zip", RuntimeKind.IRIS_SHADERPACK, "INVERT", true, "Crucify.zip", "high-contrast dark distortion");
        register("cursed_metamorphopsia", "cursed_metamorphopsia.zip", RuntimeKind.IRIS_SHADERPACK, "WOBBLE", true, "Cursed Shader (Metamorphopsia).zip", "warped cursed world look");
        register("jelly_world", "jelly_world.zip", RuntimeKind.FALLBACK_ONLY, "BLOBS", false, "JELLY WORLD.zip", "vanilla shader override resource pack, not an Iris shaderpack");
        register("nms_1_6", "nms_1_6.zip", RuntimeKind.IRIS_SHADERPACK, "GREEN_NOISE", true, "NMS 1.6.zip", "glow-heavy green ambient pack");
        register("white_sharp_1_2", "white_sharp_1_2.zip", RuntimeKind.IRIS_SHADERPACK, "DESATURATE", false, "white sharp shader_1.2.zip", "sharp bright clarity pack");
        register("ctr_vcr", "ctr_vcr.zip", RuntimeKind.IRIS_SHADERPACK, "SCAN_PINCUSHION", true, "CTR-VCR_v1.4.4.zip", "CRT and VHS-like scan distortion");
        register("lsd_shader", "lsd_shader.zip", RuntimeKind.IRIS_SHADERPACK, "COLOR_CONVOLVE", false, "LSD_Sshader_v1_3.zip", "rainbow warp and lsd-style color shift");
        register("trippy_shaderpack", "trippy_shaderpack.zip", RuntimeKind.IRIS_SHADERPACK, "DESATURATE", false, "Trippy-Shaderpack master.zip", "strong psychedelic distortion with adjustable curvature and waviness");

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
        return PROFILES.get(id.trim().toLowerCase(Locale.ROOT));
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
        return "CopiMine/" + zipName;
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

    private static void register(String id, String zipName, RuntimeKind runtimeKind, String fallbackEffectId, boolean darkPreferred, String originalName, String note) {
        PROFILES.put(id, new ShaderpackProfile(
                id,
                zipName,
                "assets/copimineclient/shaderpacks/" + zipName,
                runtimeKind,
                fallbackEffectId,
                darkPreferred,
                originalName,
                note
        ));
    }
}
