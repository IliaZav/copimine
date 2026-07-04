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
            Path runtimeTarget,
            String runtimeName,
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
    private final Path runtimeShaderpackDir;
    private final Map<String, ExportResult> exports = new LinkedHashMap<>();
    private volatile String status = "idle";

    public ShaderpackExporter(ShaderpackRegistry registry) {
        this.registry = registry;
        this.runtimeShaderpackDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        this.shaderpackDir = runtimeShaderpackDir.resolve("CopiMine");
    }

    public void initialize() {
        exports.clear();
        try {
            Files.createDirectories(shaderpackDir);
            Files.createDirectories(runtimeShaderpackDir);
            for (ShaderpackRegistry.ShaderpackProfile profile : registry.profiles()) {
                ExportResult result = exportProfile(profile);
                exports.put(profile.zipName().toLowerCase(Locale.ROOT), result);
                CopiMineClientLogger.info(
                        "Shaderpack export: "
                                + profile.id()
                                + " -> runtime=" + profile.runtimeKind().name()
                                + ", validZip=" + result.validZip()
                                + ", irisCompatible=" + result.irisCompatible()
                                + ", repaired=" + result.repaired()
                                + ", status=" + result.status()
                );
            }
            status = "exported=" + exports.size();
        } catch (Exception error) {
            status = "export-failed:" + error.getClass().getSimpleName();
            CopiMineClientLogger.error("Shaderpack export failed", error);
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
                            + ", runtimeName=" + result.runtimeName()
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
            return new ExportResult(
                    shaderpackDir.resolve(profile.zipName()),
                    runtimeShaderpackDir.resolve(registry.shaderpackRuntimeName(profile.zipName())),
                    registry.shaderpackRuntimeName(profile.zipName()),
                    false,
                    false,
                    false,
                    false,
                    "",
                    "missing-resource"
            );
        }
        PreparedZip prepared = prepareZip(profile, bundled);
        Path target = shaderpackDir.resolve(profile.zipName());
        String runtimeName = registry.shaderpackRuntimeName(profile.zipName());
        Path runtimeTarget = runtimeShaderpackDir.resolve(runtimeName);
        writeIfChanged(target, prepared.bytes());
        writeIfChanged(runtimeTarget, prepared.bytes());
        return new ExportResult(
                target,
                runtimeTarget,
                runtimeName,
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
        if (!validation.usesBackslashes() && !validation.wrappedRootShaders()) {
            return new PreparedZip(bundled, validation);
        }
        byte[] repairedBytes = normalizeZip(bundled, validation.wrappedRootShaders());
        if (repairedBytes == null) {
            return new PreparedZip(bundled, validation);
        }
        ValidationResult repairedValidation = validateZip(profile, repairedBytes);
        if (validation.irisCompatible() && !repairedValidation.irisCompatible()) {
            return new PreparedZip(bundled, validation);
        }
        String repairedStatus;
        if (validation.wrappedRootShaders() && validation.usesBackslashes()) {
            repairedStatus = "repaired-backslashes-and-wrapped-root";
        } else if (validation.wrappedRootShaders()) {
            repairedStatus = "repaired-wrapped-root-to-iris-compatible";
        } else if (validation.usesBackslashes()) {
            repairedStatus = "repaired-backslash-entries";
        } else {
            repairedStatus = repairedValidation.status();
        }
        return new PreparedZip(
                repairedBytes,
                new ValidationResult(
                        repairedValidation.validZip(),
                        repairedValidation.irisCompatible(),
                        repairedValidation.vanillaShaderOverride(),
                        true,
                        repairedValidation.wrappedRootShaders(),
                        repairedValidation.usesBackslashes(),
                        repairedStatus
                )
        );
    }

    private void writeIfChanged(Path target, byte[] bytes) throws Exception {
        if (Files.isRegularFile(target) && MessageDigest.isEqual(sha256(Files.readAllBytes(target)), sha256(bytes))) {
            return;
        }
        Files.createDirectories(target.getParent());
        try (OutputStream output = Files.newOutputStream(target)) {
            output.write(bytes);
        }
    }

    private ValidationResult validateZip(ShaderpackRegistry.ShaderpackProfile profile, byte[] bytes) throws Exception {
        boolean hasAnyEntries = false;
        boolean hasRootShaders = false;
        boolean hasShadersProperties = false;
        boolean hasVanillaShaderOverride = false;
        boolean hasWrappedRootShaders = false;
        boolean usesBackslashes = false;
        Set<String> topLevelSegments = new LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                hasAnyEntries = true;
                String rawName = entry.getName();
                if (rawName.indexOf('\\') >= 0) {
                    usesBackslashes = true;
                }
                String name = rawName.replace('\\', '/');
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
        return new ValidationResult(validZip, irisCompatible, hasVanillaShaderOverride, false, canRepairWrappedRoot, usesBackslashes, status);
    }

    private byte[] normalizeZip(byte[] bytes, boolean flattenWrappedRoot) throws IOException {
        String rootPrefix = flattenWrappedRoot ? wrappedRootPrefix(bytes) : null;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(output);
             ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                if (rootPrefix != null && !rootPrefix.isBlank()) {
                    if (!normalized.startsWith(rootPrefix + "/")) {
                        return null;
                    }
                    normalized = normalized.substring(rootPrefix.length() + 1);
                }
                if (normalized.isBlank()) {
                    continue;
                }
                ZipEntry repairedEntry = new ZipEntry(normalized);
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
            boolean usesBackslashes,
            String status
    ) {
    }
}
