package me.copimine.client;

import me.copimine.client.mixin.GameRendererAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public final class ClientPostProcessController {
    private static final Map<String, Identifier> EFFECT_POST_PROCESSORS = Map.ofEntries(
            Map.entry("DESATURATE", Identifier.of("copimineclient", "shaders/post/copimine_desaturate.json")),
            Map.entry("COLOR_CONVOLVE", Identifier.of("copimineclient", "shaders/post/copimine_color_convolve.json")),
            Map.entry("SCAN_PINCUSHION", Identifier.of("copimineclient", "shaders/post/copimine_scan_pincushion.json")),
            Map.entry("GREEN_NOISE", Identifier.of("copimineclient", "shaders/post/copimine_green_noise.json")),
            Map.entry("INVERT", Identifier.ofVanilla("shaders/post/invert.json")),
            Map.entry("WOBBLE", Identifier.of("copimineclient", "shaders/post/copimine_wobble.json")),
            Map.entry("BLOBS", Identifier.of("copimineclient", "shaders/post/copimine_blobs.json")),
            Map.entry("PENCIL", Identifier.of("copimineclient", "shaders/post/copimine_pencil.json")),
            Map.entry("CHAOS", Identifier.of("copimineclient", "shaders/post/copimine_chaos.json"))
    );
    private static final Map<String, String> OPTIONAL_SHADERPACKS = Map.ofEntries(
            Map.entry("acid_shaders.zip", "assets/copimineclient/shaderpacks/acid_shaders.zip"),
            Map.entry("crucify.zip", "assets/copimineclient/shaderpacks/crucify.zip"),
            Map.entry("cursed_metamorphopsia.zip", "assets/copimineclient/shaderpacks/cursed_metamorphopsia.zip"),
            Map.entry("jelly_world.zip", "assets/copimineclient/shaderpacks/jelly_world.zip"),
            Map.entry("nms_1_6.zip", "assets/copimineclient/shaderpacks/nms_1_6.zip"),
            Map.entry("white_sharp_1_2.zip", "assets/copimineclient/shaderpacks/white_sharp_1_2.zip")
    );

    private final MinecraftClient client = MinecraftClient.getInstance();
    private volatile String activeEffectId;
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

    public void initializeOptionalShaderpacks() {
        Path shaderpackDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve("CopiMine");
        try {
            Files.createDirectories(shaderpackDir);
            for (Map.Entry<String, String> entry : OPTIONAL_SHADERPACKS.entrySet()) {
                copyIfChanged(shaderpackDir.resolve(entry.getKey()), entry.getValue());
            }
            status = "optional shaderpacks exported";
        } catch (Exception error) {
            status = "shaderpack export failed: " + error.getClass().getSimpleName();
        }
    }

    public boolean apply(String effectId, float intensity) {
        String normalized = normalize(effectId);
        if (ClientBridgeProtocol.isIrisShaderPackActive()) {
            clear();
            status = "Iris shaderpack active; CopiMineClient keeps overlay route to avoid clobbering the active shaderpack";
            lastFailureReason = "iris-pack-active";
            return false;
        }
        Identifier identifier = EFFECT_POST_PROCESSORS.get(normalized);
        if (identifier == null) {
            clear();
            status = "unknown post effect " + normalized;
            lastFailureReason = "unknown-post-effect:" + normalized;
            return false;
        }
        if (normalized.equals(activeEffectId)) {
            status = "post-process active " + normalized + " intensity=" + clamp(intensity);
            lastFailureReason = "";
            return true;
        }
        GameRenderer renderer = client.gameRenderer;
        if (!(renderer instanceof GameRendererAccessor accessor)) {
            status = "post-process unavailable: mixin accessor missing";
            activeEffectId = null;
            lastFailureReason = "mixin-accessor-missing";
            return false;
        }
        try {
            accessor.copimine$loadPostProcessor(identifier);
            activeEffectId = normalized;
            status = "post-process active " + normalized + " intensity=" + clamp(intensity);
            lastFailureReason = "";
            return true;
        } catch (RuntimeException error) {
            disableProcessor();
            activeEffectId = null;
            status = "post-process load failed: " + error.getClass().getSimpleName();
            lastFailureReason = "post-process-load-failed:" + error.getClass().getSimpleName();
            return false;
        }
    }

    public void clear() {
        disableProcessor();
        activeEffectId = null;
        lastFailureReason = "";
        if (!status.startsWith("shaderpack export failed")) {
            status = "idle";
        }
    }

    public String statusLine() {
        return status + ", activePost=" + (activeEffectId == null ? "-" : activeEffectId);
    }

    public String lastFailureReason() {
        return lastFailureReason == null || lastFailureReason.isBlank() ? "client-post-process-unavailable" : lastFailureReason;
    }

    private void copyIfChanged(Path target, String resourcePath) throws Exception {
        byte[] bundled = resourceBytes(resourcePath);
        if (bundled == null || bundled.length == 0) {
            return;
        }
        if (Files.isRegularFile(target)) {
            byte[] existing = Files.readAllBytes(target);
            if (MessageDigest.isEqual(sha256(existing), sha256(bundled))) {
                return;
            }
        }
        Files.createDirectories(target.getParent());
        try (OutputStream output = Files.newOutputStream(target)) {
            output.write(bundled);
        }
    }

    private byte[] resourceBytes(String resourcePath) throws IOException {
        try (InputStream input = ClientPostProcessController.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    private byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private String normalize(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private float clamp(float intensity) {
        return Math.max(0.0F, Math.min(1.0F, intensity));
    }

    private void disableProcessor() {
        GameRenderer renderer = client.gameRenderer;
        if (renderer instanceof GameRendererAccessor accessor) {
            try {
                accessor.copimine$disablePostProcessor();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
