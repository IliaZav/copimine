package me.copimine.client;

import net.minecraft.resources.Identifier;
import java.util.Locale;
import java.util.Map;

public final class ClientPostProcessController {
    private static final Map<String, Identifier> EFFECT_POST_PROCESSORS = Map.ofEntries(
            Map.entry("DESATURATE", Identifier.fromNamespaceAndPath("copimineclient", "copimine_desaturate")),
            Map.entry("COLOR_CONVOLVE", Identifier.fromNamespaceAndPath("copimineclient", "copimine_color_convolve")),
            Map.entry("SCAN_PINCUSHION", Identifier.fromNamespaceAndPath("copimineclient", "copimine_scan_pincushion")),
            Map.entry("GREEN_NOISE", Identifier.fromNamespaceAndPath("copimineclient", "copimine_green_noise")),
            // The vanilla Enderman screen effect. The player sees it when standing inside an
            // Enderman in spectator mode; GameRenderer.checkEntityPostEffect applies exactly this
            // identifier for an Enderman. Wave 2 uses it directly so the marked player gets the
            // real in-game shader rather than an imitation, and it needs no Iris, OptiFine or
            // Sodium: requestedPostEffects is vanilla render state.
            Map.entry("ENDERMAN", Identifier.withDefaultNamespace("invert")),
            Map.entry("INVERT", Identifier.withDefaultNamespace("invert")),
            Map.entry("WOBBLE", Identifier.fromNamespaceAndPath("copimineclient", "copimine_wobble")),
            Map.entry("BLOBS", Identifier.fromNamespaceAndPath("copimineclient", "copimine_blobs")),
            Map.entry("PENCIL", Identifier.fromNamespaceAndPath("copimineclient", "copimine_pencil")),
            Map.entry("CHAOS", Identifier.fromNamespaceAndPath("copimineclient", "copimine_chaos"))
    );

    private volatile String activeEffectId;
    private volatile String status = "idle";
    private volatile String lastFailureReason = "";

    public boolean apply(String effectId, float intensity) {
        String normalized = normalize(effectId);
        Identifier identifier = EFFECT_POST_PROCESSORS.get(normalized);
        if (normalized.matches("WAVE2_INTRO_(?:[0-9]|1[0-9]|2[0-3])")) {
            identifier = Identifier.fromNamespaceAndPath("copimineclient", normalized.toLowerCase(Locale.ROOT));
        }
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
        ClientPostProcessRuntime.request(identifier);
        activeEffectId = normalized;
        status = "post-process requested " + normalized + " intensity=" + clamp(intensity);
        lastFailureReason = "";
        return true;
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
        ClientPostProcessRuntime.clear();
    }
}
