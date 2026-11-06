package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
    private final Path backupDir;
    private final Map<String, ExportResult> exports = new LinkedHashMap<>();
    private volatile String status = "idle";

    public ShaderpackExporter(ShaderpackRegistry registry) {
        this.registry = registry;
        this.runtimeShaderpackDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        this.shaderpackDir = runtimeShaderpackDir.resolve("CopiMine");
        this.backupDir = FabricLoader.getInstance().getConfigDir()
                .resolve("copimineclient").resolve("shaderpack-overwrite-backups");
    }

    public void initialize() {
        exports.clear();
        try {
            Files.createDirectories(shaderpackDir);
            Files.createDirectories(runtimeShaderpackDir);
            exports.putAll(exportProfiles(registry.profiles(), this::exportProfile, this::failedProfileExport));
            for (ShaderpackRegistry.ShaderpackProfile profile : registry.profiles()) {
                ExportResult result = exports.get(profile.zipName().toLowerCase(Locale.ROOT));
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

    private ExportResult failedProfileExport(ShaderpackRegistry.ShaderpackProfile profile, Exception error) {
        CopiMineClientLogger.error("Shaderpack export failed for " + profile.id(), error);
        String runtimeName = registry.shaderpackRuntimeName(profile.zipName());
        return new ExportResult(
                shaderpackDir.resolve(profile.zipName()),
                runtimeShaderpackDir.resolve(runtimeName),
                runtimeName,
                false,
                false,
                false,
                false,
                "",
                "export-failed:" + error.getClass().getSimpleName()
        );
    }

    @FunctionalInterface
    interface ProfileExport {
        ExportResult export(ShaderpackRegistry.ShaderpackProfile profile) throws Exception;
    }

    static Map<String, ExportResult> exportProfiles(
            List<ShaderpackRegistry.ShaderpackProfile> profiles,
            ProfileExport profileExport,
            BiFunction<ShaderpackRegistry.ShaderpackProfile, Exception, ExportResult> failureResult) {
        Map<String, ExportResult> results = new LinkedHashMap<>();
        for (ShaderpackRegistry.ShaderpackProfile profile : profiles) {
            ExportResult result;
            try {
                result = profileExport.export(profile);
            } catch (Exception error) {
                try {
                    result = failureResult.apply(profile, error);
                } catch (Exception failureResultError) {
                    if (failureResultError != error) {
                        error.addSuppressed(failureResultError);
                    }
                    result = unresolvedFailureResult(error);
                }
            }
            results.put(profile.zipName().toLowerCase(Locale.ROOT), result);
        }
        return results;
    }

    private static ExportResult unresolvedFailureResult(Exception error) {
        return new ExportResult(
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                "",
                "export-failed:" + error.getClass().getSimpleName()
        );
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
        Path targetBackup = writeIfChanged(target, prepared.bytes());
        // The runtime copy is written into shaderpacks/ itself, which is the folder Iris reads to
        // build its shaderpack list. Writing it there published every event effect as an ordinary
        // shaderpack: the player could open the Iris menu, see the drug packs by name and switch
        // them on outside of any wave. The event owns these effects, so they stay inside the
        // private CopiMine subfolder and are reached only through the runtime switch.
        Path runtimeBackup = removeIfDifferent(runtimeTarget, prepared.bytes());
        if (targetBackup != null) {
            CopiMineClientLogger.warn("Existing CopiMine shaderpack file was preserved before update: "
                    + targetBackup);
        }
        if (runtimeBackup != null) {
            CopiMineClientLogger.info("Withdrew published shaderpack copy from the Iris menu: "
                    + runtimeBackup);
        }
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

    private Path writeIfChanged(Path target, byte[] bytes) throws Exception {
        return ShaderpackExportFileWriter.writeIfChanged(target, bytes, backupDir);
    }

    /**
     * Withdraws a shaderpack copy that an earlier build published straight into shaderpacks/.
     *
     * Older releases wrote the runtime copy to the top level of the shaderpacks folder. Iris reads
     * that folder to build the shaderpack menu, so every event effect showed up there by name and
     * could be switched on by hand, outside any wave. Those files are still on disk for anyone who
     * ran an older build, so removing them only from now on would leave the menu dirty forever.
     *
     * <p>The file is only removed when it is provably one of our own exports. The file name alone
     * is not provenance: the player may have dropped their own pack under that name, and deleting
     * it would destroy data the event has no claim to. So the bytes must match a CopiMine export
     * (the current bundle, or the copy this build left in the private folder), otherwise the file
     * is left exactly as it is. Anything removed is backed up first, so an unexpected deletion can
     * still be undone by hand.</p>
     */
    private Path removeIfDifferent(Path target, byte[] bytes) throws Exception {
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        byte[] existing;
        try {
            existing = Files.readAllBytes(target);
        } catch (java.io.IOException unreadable) {
            // An unreadable file cannot be proven to be ours, so it stays.
            CopiMineClientLogger.warn("Left an unreadable shaderpack file in place: " + target);
            return null;
        }
        if (isOurExport(target, existing, bytes)) {
            Path backup = writeIfChanged(target, bytes);
            Files.deleteIfExists(target);
            return backup != null ? backup : target;
        }
        return null;
    }

    /** True when {@code existing} is byte-for-byte a CopiMine export rather than foreign content. */
    private boolean isOurExport(Path target, byte[] existing, byte[] currentBundle) throws Exception {
        if (MessageDigest.isEqual(sha256(existing), sha256(currentBundle))) {
            return true;
        }
        // A copy published by an earlier build differs from today's bundle but was still written
        // by us, and that build left the identical bytes in the private folder. Matching that file
        // proves provenance; a pack the player dropped under the same name will not match it.
        Path privateCopy = runtimeShaderpackDir.resolve(target.getFileName());
        if (Files.isRegularFile(privateCopy)) {
            return MessageDigest.isEqual(sha256(existing), sha256(Files.readAllBytes(privateCopy)));
        }
        return false;
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
