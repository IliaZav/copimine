package me.copimine.narcotics.resourcepack;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.model.NarcoticDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public final class NarcoticsResourcePackAudit {
    private static final String[] EFFECTS = {
            "desaturate", "color_convolve", "scan_pincushion", "green_noise",
            "invert", "wobble", "blobs", "pencil", "chaos"
    };

    private final CopiMineNarcotics plugin;
    private final NarcoticsConfigService configService;

    public NarcoticsResourcePackAudit(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public Report inspect() {
        Path root = projectRoot();
        Path packRoot = root.resolve("resourcepacks");
        Path srcRoot = packRoot.resolve("src");
        Path copimineRoot = srcRoot.resolve("assets").resolve("copimine");
        Path serverProps = root.resolve("minecraft").resolve("server").resolve("server.properties");
        Path zipPath = packRoot.resolve("build").resolve("CopiMineResourcePack.zip");
        Path shaPath = packRoot.resolve("build").resolve("CopiMineResourcePack.sha1");
        Path thirdPartyDoc = packRoot.resolve("THIRD_PARTY_NARCOTICS_VISUALS.md");
        Path licensesDoc = packRoot.resolve("LICENSES_RESOURCEPACK.md");

        List<String> errors = new ArrayList<>();
        List<String> itemModels = new ArrayList<>();
        List<String> itemTextures = new ArrayList<>();
        List<String> overlayTextures = new ArrayList<>();
        List<String> shaderProfiles = new ArrayList<>();

        Path itemsManifest = copimineRoot.resolve("manifests").resolve("narcotics_items_manifest.json");
        Path visualsManifest = copimineRoot.resolve("manifests").resolve("narcotics_visuals_manifest.json");
        Path fontManifest = copimineRoot.resolve("font").resolve("narcotics_overlay.json");

        ensureExists(itemsManifest, errors, "Missing narcotics items manifest");
        ensureExists(visualsManifest, errors, "Missing narcotics visuals manifest");
        ensureExists(fontManifest, errors, "Missing narcotics overlay font manifest");
        ensureExists(thirdPartyDoc, errors, "Missing THIRD_PARTY_NARCOTICS_VISUALS.md");
        ensureExists(licensesDoc, errors, "Missing LICENSES_RESOURCEPACK.md");

        Set<Integer> cmdValues = new LinkedHashSet<>();
        for (NarcoticDefinition definition : configService.items().values()) {
            Path model = copimineRoot.resolve("models").resolve("item").resolve(definition.id() + ".json");
            Path texture = copimineRoot.resolve("textures").resolve("item").resolve("narcotics").resolve(definition.id() + ".png");
            ensureExists(model, errors, "Missing item model for " + definition.id());
            ensureExists(texture, errors, "Missing item texture for " + definition.id());
            itemModels.add(srcRoot.relativize(model).toString().replace('\\', '/'));
            itemTextures.add(srcRoot.relativize(texture).toString().replace('\\', '/'));
            if (!cmdValues.add(definition.customModelData())) {
                errors.add("Duplicate CustomModelData in narcotics config: " + definition.customModelData());
            }
        }

        for (String effect : EFFECTS) {
            Path overlay = copimineRoot.resolve("textures").resolve("gui").resolve("narcotics").resolve(effect + "_overlay.png");
            Path shader = copimineRoot.resolve("shaders").resolve("narcotics").resolve(effect + ".json");
            ensureExists(overlay, errors, "Missing overlay placeholder for " + effect);
            ensureExists(shader, errors, "Missing shader placeholder for " + effect);
            overlayTextures.add(srcRoot.relativize(overlay).toString().replace('\\', '/'));
            shaderProfiles.add(srcRoot.relativize(shader).toString().replace('\\', '/'));
        }

        List<String> expectedZipEntries = new ArrayList<>();
        expectedZipEntries.add("pack.mcmeta");
        expectedZipEntries.add(srcRoot.relativize(itemsManifest).toString().replace('\\', '/'));
        expectedZipEntries.add(srcRoot.relativize(visualsManifest).toString().replace('\\', '/'));
        expectedZipEntries.add(srcRoot.relativize(fontManifest).toString().replace('\\', '/'));
        expectedZipEntries.addAll(itemModels);
        expectedZipEntries.addAll(itemTextures);
        expectedZipEntries.addAll(overlayTextures);
        expectedZipEntries.addAll(shaderProfiles);

        String zipSha1 = null;
        if (Files.isRegularFile(zipPath)) {
            try {
                validateZipContents(zipPath, expectedZipEntries, errors);
                zipSha1 = sha1(zipPath);
            } catch (Exception error) {
                errors.add("Failed to hash resource pack zip: " + error.getMessage());
            }
        } else {
            errors.add("Missing built resource pack zip: " + zipPath);
        }

        boolean hashSynced = false;
        if (zipSha1 != null && Files.isRegularFile(serverProps)) {
            try {
                String props = Files.readString(serverProps, StandardCharsets.UTF_8);
                hashSynced = props.contains("resource-pack-sha1=" + zipSha1);
                if (!hashSynced) {
                    errors.add("server.properties resource-pack-sha1 does not match build zip hash");
                }
            } catch (IOException error) {
                errors.add("Failed to read server.properties: " + error.getMessage());
            }
        }

        if (zipSha1 != null && Files.isRegularFile(shaPath)) {
            try {
                String shaFile = Files.readString(shaPath, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if (!zipSha1.equals(shaFile)) {
                    errors.add("CopiMineResourcePack.sha1 does not match actual build zip hash");
                }
            } catch (IOException error) {
                errors.add("Failed to read SHA1 file: " + error.getMessage());
            }
        }

        boolean noHotlinks = noHotlinks(copimineRoot);
        if (!noHotlinks) {
            errors.add("Resource pack source contains external URLs in asset payloads");
        }

        boolean noRuntimeDownloads = noRuntimeExternalDownloads(root);
        if (!noRuntimeDownloads) {
            errors.add("Runtime or build files still contain external-download logic for narcotics visuals");
        }

        boolean thirdPartyDocMentionsSearch = fileContainsAll(thirdPartyDoc, List.of("Search summary", "Reviewed sources", "Decision"));
        if (!thirdPartyDocMentionsSearch) {
            errors.add("THIRD_PARTY_NARCOTICS_VISUALS.md does not document the asset search decision");
        }

        boolean thirdPartyUsesSelfMade = fileContainsAny(thirdPartyDoc, List.of("self-made", "self-generated", "self made", "All narcotics item textures"));
        boolean licensesDocReadable = fileContainsAny(licensesDoc, List.of("self-made", "self-generated", "generated inside the CopiMine project", "сторонние visual assets"));
        if (!licensesDocReadable) {
            errors.add("LICENSES_RESOURCEPACK.md does not clearly describe asset origin");
        }

        return new Report(
                errors,
                itemModels.stream().sorted().toList(),
                itemTextures.stream().sorted().toList(),
                overlayTextures.stream().sorted().toList(),
                shaderProfiles.stream().sorted().toList(),
                zipSha1,
                hashSynced,
                Files.isRegularFile(itemsManifest),
                Files.isRegularFile(visualsManifest),
                Files.isRegularFile(fontManifest),
                Files.isRegularFile(thirdPartyDoc),
                Files.isRegularFile(licensesDoc),
                noHotlinks,
                noRuntimeDownloads,
                thirdPartyDocMentionsSearch,
                thirdPartyUsesSelfMade
        );
    }

    public Path projectRoot() {
        Path data = plugin.getDataFolder().toPath().toAbsolutePath();
        Path current = data;
        for (int index = 0; index < 4 && current != null; index++) {
            current = current.getParent();
        }
        if (current != null && Files.isDirectory(current.resolve("resourcepacks"))) {
            return current;
        }
        return Path.of("").toAbsolutePath().resolve("opt").resolve("copimine");
    }

    private boolean noHotlinks(Path copimineRoot) {
        try (Stream<Path> files = Files.walk(copimineRoot)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json") || path.toString().endsWith(".mcmeta"))
                    .allMatch(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                            return !content.contains("http://") && !content.contains("https://");
                        } catch (IOException error) {
                            return false;
                        }
                    });
        } catch (IOException error) {
            return false;
        }
    }

    private boolean noRuntimeExternalDownloads(Path root) {
        List<Path> files = List.of(
                root.resolve("copimine-narcotics").resolve("src").resolve("me").resolve("copimine").resolve("narcotics").resolve("CopiMineNarcotics.java"),
                root.resolve("copimine-narcotics").resolve("src").resolve("me").resolve("copimine").resolve("visualruntime").resolve("VisualRuntimeService.java"),
                root.resolve("resourcepacks").resolve("build-resourcepack.py")
        );
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (content.contains("urllib") || content.contains("requests.") || content.contains("invoke-webrequest")
                        || content.contains("new url(") || content.contains("httpclient")) {
                    return false;
                }
            } catch (IOException ignored) {
                return false;
            }
        }
        return true;
    }

    private boolean fileContainsAll(Path file, List<String> needles) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (!content.contains(needle)) {
                    return false;
                }
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean fileContainsAny(Path file, List<String> needles) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void ensureExists(Path path, List<String> errors, String message) {
        if (!Files.exists(path)) {
            errors.add(message + ": " + path);
        }
    }

    private void validateZipContents(Path zipPath, List<String> expectedEntries, List<String> errors) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            for (String entry : expectedEntries.stream().distinct().sorted().toList()) {
                if (zip.getEntry(entry) == null) {
                    errors.add("Built resource pack zip is missing entry: " + entry);
                }
            }
            if (zip.size() == 0) {
                errors.add("Built resource pack zip is empty.");
            }
        } catch (IOException error) {
            errors.add("Failed to inspect resource pack zip: " + error.getMessage());
        }
    }

    private String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }

    public record Report(
            List<String> errors,
            List<String> itemModels,
            List<String> itemTextures,
            List<String> overlayTextures,
            List<String> shaderProfiles,
            String zipSha1,
            boolean hashSynced,
            boolean itemsManifestPresent,
            boolean visualsManifestPresent,
            boolean fontManifestPresent,
            boolean thirdPartyDocPresent,
            boolean licensesDocPresent,
            boolean noHotlinks,
            boolean noRuntimeDownloads,
            boolean searchDocumented,
            boolean selfMadeDocumented
    ) {
        public boolean ok() {
            return errors.isEmpty();
        }

        public String summary() {
            return ok() ? "OK" : String.join(" | ", errors.stream().sorted(Comparator.naturalOrder()).toList());
        }
    }
}
