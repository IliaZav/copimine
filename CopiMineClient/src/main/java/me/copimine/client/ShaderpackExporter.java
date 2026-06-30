package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ShaderpackExporter {
    public record ExportResult(
            Path target,
            boolean validZip,
            boolean irisCompatible,
            boolean vanillaShaderOverride,
            boolean repaired,
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

    public Map<String, ExportResult> results() {
        return Map.copyOf(exports);
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

    public List<String> summaryLines() {
        return registry.profiles().stream()
                .map(profile -> {
                    ExportResult result = result(profile.zipName());
                    if (result == null) {
                        return profile.id() + " -> missing export result";
                    }
                    return profile.id()
                            + " -> file=" + profile.zipName()
                            + ", runtime=" + profile.runtimeKind().name()
                            + ", irisCompatible=" + yesNo(result.irisCompatible())
                            + ", validZip=" + yesNo(result.validZip())
                            + ", repaired=" + yesNo(result.repaired())
                            + ", sha256=" + abbreviate(result.sha256())
                            + ", status=" + result.status();
                })
                .toList();
    }

    private ExportResult exportProfile(ShaderpackRegistry.ShaderpackProfile profile) throws Exception {
        byte[] bundled = resourceBytes(profile.resourcePath());
        if (bundled == null || bundled.length == 0) {
            return new ExportResult(shaderpackDir.resolve(profile.zipName()), false, false, false, false, "", "missing-resource");
        }
        PreparedZip prepared = prepareZip(profile, bundled);
        Path target = shaderpackDir.resolve(profile.zipName());
        if (!Files.isRegularFile(target) || !MessageDigest.isEqual(sha256(Files.readAllBytes(target)), sha256(prepared.bytes()))) {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(target)) {
                output.write(prepared.bytes());
            }
        }
        return new ExportResult(
                target,
                prepared.validation().validZip(),
                prepared.validation().irisCompatible(),
                prepared.validation().vanillaShaderOverride(),
                prepared.validation().repaired(),
                hex(sha256(prepared.bytes())),
                prepared.validation().status()
        );
    }

    private PreparedZip prepareZip(ShaderpackRegistry.ShaderpackProfile profile, byte[] bundled) throws Exception {
        ValidationResult validation = validateZip(profile, bundled);
        if (validation.irisCompatible() || !validation.wrappedRootShaders()) {
            return new PreparedZip(bundled, validation);
        }
        byte[] repairedBytes = flattenWrappedRoot(bundled);
        if (repairedBytes == null) {
            return new PreparedZip(bundled, validation);
        }
        ValidationResult repairedValidation = validateZip(profile, repairedBytes);
        if (!repairedValidation.irisCompatible()) {
            return new PreparedZip(bundled, validation);
        }
        return new PreparedZip(
                repairedBytes,
                new ValidationResult(
                        repairedValidation.validZip(),
                        repairedValidation.irisCompatible(),
                        repairedValidation.vanillaShaderOverride(),
                        true,
                        repairedValidation.wrappedRootShaders(),
                        "repaired-wrapped-root-to-iris-compatible"
                )
        );
    }

    private ValidationResult validateZip(ShaderpackRegistry.ShaderpackProfile profile, byte[] bytes) throws Exception {
        boolean hasAnyEntries = false;
        boolean hasRootShaders = false;
        boolean hasShadersProperties = false;
        boolean hasVanillaShaderOverride = false;
        boolean hasWrappedRootShaders = false;
        Set<String> topLevelSegments = new LinkedHashSet<>();
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
                int slashIndex = name.indexOf('/');
                if (slashIndex > 0) {
                    topLevelSegments.add(name.substring(0, slashIndex));
                } else {
                    topLevelSegments.add(name);
                }
                if (!hasRootShaders && name.matches("^[^/]+/shaders/.*")) {
                    hasWrappedRootShaders = true;
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
        } else if (!hasRootShaders && hasWrappedRootShaders) {
            status = "wrapped-root-shaders-detected";
        } else if (profile.irisCompatible() && !irisCompatible) {
            status = hasVanillaShaderOverride
                    ? "not-iris-compatible:vanilla-shader-override-only"
                    : "not-iris-compatible:missing-root-shaders";
        } else if (hasRootShaders && hasShadersProperties) {
            status = "iris-compatible+properties";
        } else if (hasRootShaders) {
            status = "iris-compatible";
        } else if (hasVanillaShaderOverride) {
            status = "vanilla-shader-override-only";
        } else {
            status = "unknown-structure";
        }
        boolean canRepairWrappedRoot = hasWrappedRootShaders && !hasRootShaders && topLevelSegments.size() == 1;
        return new ValidationResult(validZip, irisCompatible, hasVanillaShaderOverride, false, canRepairWrappedRoot, status);
    }

    private byte[] flattenWrappedRoot(byte[] bytes) throws IOException {
        String rootPrefix = wrappedRootPrefix(bytes);
        if (rootPrefix == null || rootPrefix.isBlank()) {
            return null;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(output);
             ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                if (!normalized.startsWith(rootPrefix + "/")) {
                    return null;
                }
                String flattened = normalized.substring(rootPrefix.length() + 1);
                if (flattened.isBlank()) {
                    continue;
                }
                ZipEntry repairedEntry = new ZipEntry(flattened);
                zipOut.putNextEntry(repairedEntry);
                zipOut.write(zipIn.readAllBytes());
                zipOut.closeEntry();
            }
            zipOut.finish();
            return output.toByteArray();
        }
    }

    private String wrappedRootPrefix(byte[] bytes) throws IOException {
        Set<String> topLevelSegments = new LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                int slashIndex = normalized.indexOf('/');
                if (slashIndex <= 0) {
                    return null;
                }
                topLevelSegments.add(normalized.substring(0, slashIndex));
            }
        }
        return topLevelSegments.size() == 1 ? topLevelSegments.iterator().next() : null;
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

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record PreparedZip(byte[] bytes, ValidationResult validation) {
    }

    private record ValidationResult(
            boolean validZip,
            boolean irisCompatible,
            boolean vanillaShaderOverride,
            boolean repaired,
            boolean wrappedRootShaders,
            String status
    ) {
    }
}
