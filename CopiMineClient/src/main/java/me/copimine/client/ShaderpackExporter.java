package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

public final class ShaderpackExporter {
    public record ExportResult(
            Path target,
            boolean validZip,
            boolean irisCompatible,
            boolean vanillaShaderOverride,
            String sha256,
            String status
    ) {
    }

    private final ShaderpackRegistry registry;
    private final Path shaderpackDir;
    private final Map<String, ExportResult> exports = new LinkedHashMap<>();
    private volatile String status = "idle";

    public ShaderpackExporter(ShaderpackRegistry registry) {
        this.registry = registry;
        this.shaderpackDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve("CopiMine");
    }

    public void initialize() {
        exports.clear();
        try {
            Files.createDirectories(shaderpackDir);
            for (ShaderpackRegistry.ShaderpackProfile profile : registry.profiles()) {
                exports.put(profile.zipName().toLowerCase(Locale.ROOT), exportProfile(profile));
            }
            status = "exported=" + exports.size();
        } catch (Exception error) {
            status = "export-failed:" + error.getClass().getSimpleName();
        }
    }

    public Path shaderpackDir() {
        return shaderpackDir;
    }

    public ExportResult result(String zipName) {
        return exports.get(zipName == null ? "" : zipName.toLowerCase(Locale.ROOT));
    }

    public boolean hasValidZip(String zipName) {
        ExportResult result = result(zipName);
        return result != null && result.validZip();
    }

    public boolean isIrisCompatible(String zipName) {
        ExportResult result = result(zipName);
        return result != null && result.irisCompatible();
    }

    public String statusLine() {
        return status;
    }

    private ExportResult exportProfile(ShaderpackRegistry.ShaderpackProfile profile) throws Exception {
        byte[] bundled = resourceBytes(profile.resourcePath());
        if (bundled == null || bundled.length == 0) {
            return new ExportResult(shaderpackDir.resolve(profile.zipName()), false, false, false, "", "missing-resource");
        }
        ValidationResult validation = validateZip(profile, bundled);
        Path target = shaderpackDir.resolve(profile.zipName());
        if (!Files.isRegularFile(target) || !MessageDigest.isEqual(sha256(Files.readAllBytes(target)), sha256(bundled))) {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(target)) {
                output.write(bundled);
            }
        }
        return new ExportResult(
                target,
                validation.validZip(),
                validation.irisCompatible(),
                validation.vanillaShaderOverride(),
                hex(sha256(bundled)),
                validation.status()
        );
    }

    private ValidationResult validateZip(ShaderpackRegistry.ShaderpackProfile profile, byte[] bytes) throws Exception {
        boolean hasAnyEntries = false;
        boolean hasRootShaders = false;
        boolean hasShadersProperties = false;
        boolean hasVanillaShaderOverride = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                hasAnyEntries = true;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("shaders/")) {
                    hasRootShaders = true;
                }
                if (name.equalsIgnoreCase("shaders.properties") || name.equalsIgnoreCase("shader_properties.json")) {
                    hasShadersProperties = true;
                }
                if (name.startsWith("assets/minecraft/shaders/")) {
                    hasVanillaShaderOverride = true;
                }
            }
        }
        boolean irisCompatible = hasRootShaders;
        boolean validZip = hasAnyEntries && (hasRootShaders || hasVanillaShaderOverride);
        String status;
        if (!hasAnyEntries) {
            status = "empty-zip";
        } else if (profile.irisCompatible() && !irisCompatible) {
            status = "not-iris-compatible";
        } else if (hasRootShaders && hasShadersProperties) {
            status = "iris-compatible+properties";
        } else if (hasRootShaders) {
            status = "iris-compatible";
        } else if (hasVanillaShaderOverride) {
            status = "vanilla-shader-override-only";
        } else {
            status = "unknown-structure";
        }
        return new ValidationResult(validZip, irisCompatible, hasVanillaShaderOverride, status);
    }

    private byte[] resourceBytes(String resourcePath) throws IOException {
        try (InputStream input = ShaderpackExporter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    private byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record ValidationResult(boolean validZip, boolean irisCompatible, boolean vanillaShaderOverride, String status) {
    }
}
