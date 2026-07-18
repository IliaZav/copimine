package me.copimine.client;

import me.copimine.client.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;

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

    private final MinecraftClient client = MinecraftClient.getInstance();
    private volatile String activeEffectId;
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

    public boolean apply(String effectId, float intensity) {
        String normalized = normalize(effectId);
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
        status = "idle";
    }

    public String statusLine() {
        return status + ", activePost=" + (activeEffectId == null ? "-" : activeEffectId);
    }

    public String lastFailureReason() {
        return lastFailureReason == null || lastFailureReason.isBlank() ? "client-post-process-unavailable" : lastFailureReason;
    }

    private String normalize(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private float clamp(float intensity) {
        if (!Float.isFinite(intensity)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, intensity));
    }

    private void disableProcessor() {
        GameRenderer renderer = client.gameRenderer;
        if (renderer instanceof GameRendererAccessor accessor) {
            try {
                accessor.copimine$disablePostProcessor();
            } catch (RuntimeException error) {
                CopiMineClientLogger.warn("Failed to disable active post-processor before switching visuals", error);
            }
        }
    }
}
