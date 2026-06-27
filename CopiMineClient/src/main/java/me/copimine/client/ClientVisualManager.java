package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVisualManager {
    @FunctionalInterface
    public interface FinishedVisualHandler {
        void onFinished(long seq, String effectId, String reason);
    }

    private static final int OVERLAY_TEXTURE_SIZE = 256;
    private static final float IRIS_ALPHA_MULTIPLIER = 0.82F;
    private static final Map<String, Identifier> OVERLAYS = Map.of(
            "DESATURATE", Identifier.of("copimineclient", "textures/visuals/desaturate_overlay.png"),
            "COLOR_CONVOLVE", Identifier.of("copimineclient", "textures/visuals/color_convolve_overlay.png"),
            "SCAN_PINCUSHION", Identifier.of("copimineclient", "textures/visuals/scan_pincushion_overlay.png"),
            "GREEN_NOISE", Identifier.of("copimineclient", "textures/visuals/green_noise_overlay.png"),
            "INVERT", Identifier.of("copimineclient", "textures/visuals/invert_overlay.png"),
            "WOBBLE", Identifier.of("copimineclient", "textures/visuals/wobble_overlay.png"),
            "BLOBS", Identifier.of("copimineclient", "textures/visuals/blobs_overlay.png"),
            "PENCIL", Identifier.of("copimineclient", "textures/visuals/pencil_overlay.png"),
            "CHAOS", Identifier.of("copimineclient", "textures/visuals/chaos_overlay.png")
    );

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ClientConfig config;
    private final Map<Long, ActiveVisual> active = new ConcurrentHashMap<>();
    private volatile long lastServerSeq;

    public ClientVisualManager(ClientConfig config) {
        this.config = config;
    }

    public void start(String effectId, long seq, int seconds, float intensity, String clearPolicy) {
        if (!config.allowServerVisuals()) {
            return;
        }
        if ("REPLACE_ALL_FULLSCREEN".equalsIgnoreCase(clearPolicy)) {
            clearAll("replace");
        }
        String normalized = normalize(effectId);
        active.put(seq, new ActiveVisual(
                seq,
                normalized,
                System.currentTimeMillis() + (Math.max(1, Math.min(config.maxVisualDurationSeconds(), seconds)) * 1000L),
                clamp(intensity)
        ));
        lastServerSeq = seq;
    }

    public void startLocalTest(String effectId, int seconds, float intensity) {
        clearAll("local-test");
        active.put(-1L, new ActiveVisual(
                -1L,
                normalize(effectId),
                System.currentTimeMillis() + (Math.max(1, Math.min(config.maxVisualDurationSeconds(), seconds)) * 1000L),
                clamp(intensity)
        ));
    }

    public void stop(String effectId) {
        String normalized = normalize(effectId);
        active.entrySet().removeIf(entry -> normalized.equals(entry.getValue().effectId()));
    }

    public void clearAll(String reason) {
        active.clear();
    }

    public void tick(FinishedVisualHandler finishedHandler) {
        long now = System.currentTimeMillis();
        List<ActiveVisual> finished = new ArrayList<>();
        active.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().untilMillis() <= now;
            if (expired) {
                finished.add(entry.getValue());
            }
            return expired;
        });
        if (finishedHandler != null) {
            for (ActiveVisual visual : finished) {
                if (visual.seq() > 0L) {
                    finishedHandler.onFinished(visual.seq(), visual.effectId(), "duration_elapsed");
                }
            }
        }
    }

    public void render(DrawContext context) {
        if (active.isEmpty() || client.player == null || (client.options.hudHidden && !config.renderWhenHudHidden())) {
            return;
        }
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        float routeAlphaMultiplier = effectiveAlphaMultiplier();
        for (ActiveVisual visual : active.values()) {
            float pulse = 0.6F + (float) ((Math.sin((now / 180.0D) + visual.effectId().hashCode()) + 1.0D) * 0.2D);
            drawEffect(context, width, height, visual, pulse, routeAlphaMultiplier);
        }
        if (config.debugOverlay()) {
            context.drawText(client.textRenderer, statusLine(), 8, 8, 0xFFFFFFFF, true);
        }
    }

    public String statusLine() {
        boolean irisActive = ClientBridgeProtocol.isIrisShaderPackActive();
        String irisBlend = irisActive ? "softened-overlay" : "normal-overlay";
        if (active.isEmpty()) {
            return "CopiMineClient: active visuals = none, render_when_hud_hidden=" + config.renderWhenHudHidden()
                    + ", irisShaderPackActive=" + yesNo(irisActive)
                    + ", route=fullscreen-hud-overlay"
                    + ", irisBlend=" + irisBlend;
        }
        ActiveVisual first = active.values().iterator().next();
        long secondsLeft = Math.max(0L, (first.untilMillis() - System.currentTimeMillis()) / 1000L);
        return "CopiMineClient: " + first.effectId() + " / " + secondsLeft + "s / active=" + active.size()
                + " / lastSeq=" + lastServerSeq
                + " / render_when_hud_hidden=" + config.renderWhenHudHidden()
                + " / irisShaderPackActive=" + yesNo(irisActive)
                + " / route=fullscreen-hud-overlay"
                + " / irisBlend=" + irisBlend;
    }

    public String activeSummary() {
        if (active.isEmpty()) {
            return "none";
        }
        return active.values().stream()
                .map(visual -> visual.effectId() + "#" + visual.seq())
                .sorted(String::compareToIgnoreCase)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    public long lastServerSeq() {
        return lastServerSeq;
    }

    public boolean serverVisualsAllowed() {
        return config.allowServerVisuals();
    }

    private void drawEffect(DrawContext context, int width, int height, ActiveVisual visual, float pulse, float routeAlphaMultiplier) {
        float intensity = clamp(visual.intensity());
        float alphaFactor = (0.18F + (0.82F * intensity)) * routeAlphaMultiplier;
        float motionFactor = 0.15F + (0.85F * intensity);
        switch (visual.effectId()) {
            case "DESATURATE" -> {
                context.fill(0, 0, width, height, alpha(0x90B0B0B0, 0.22F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.70F * pulse * alphaFactor);
            }
            case "COLOR_CONVOLVE" -> {
                context.fill(0, 0, width, height, alpha(0x70FF66CC, 0.16F * pulse * alphaFactor));
                context.fill(0, 0, width, height, alpha(0x7000E5FF, 0.10F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.78F * pulse * alphaFactor);
            }
            case "SCAN_PINCUSHION" -> {
                for (int y = 0; y < height; y += 6) {
                    context.fill(0, y, width, y + 2, alpha(0xAA111111, 0.18F * alphaFactor));
                }
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.68F * pulse * alphaFactor);
            }
            case "GREEN_NOISE" -> {
                context.fill(0, 0, width, height, alpha(0x8020FF80, 0.18F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.82F * pulse * alphaFactor);
            }
            case "INVERT" -> {
                context.fill(0, 0, width, height, alpha(0xA0000030, 0.22F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.85F * pulse * alphaFactor);
            }
            case "WOBBLE" -> {
                int offsetX = (int) Math.round(Math.sin(System.currentTimeMillis() / 120.0D) * 10.0D * motionFactor);
                int offsetY = (int) Math.round(Math.cos(System.currentTimeMillis() / 160.0D) * 8.0D * motionFactor);
                context.fill(0, 0, width, height, alpha(0x884422AA, 0.10F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), offsetX, offsetY, width, height, 0.76F * pulse * alphaFactor);
            }
            case "BLOBS" -> {
                context.fill(0, 0, width, height, alpha(0x663A89FF, 0.10F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.74F * pulse * alphaFactor);
            }
            case "PENCIL" -> {
                context.fill(0, 0, width, height, alpha(0xC0D0D0D0, 0.16F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.84F * pulse * alphaFactor);
            }
            case "CHAOS" -> {
                int jitterX = (int) Math.round(Math.sin(System.currentTimeMillis() / 55.0D) * 14.0D * motionFactor);
                int jitterY = (int) Math.round(Math.cos(System.currentTimeMillis() / 70.0D) * 11.0D * motionFactor);
                context.fill(0, 0, width, height, alpha(0x88FF5500, 0.11F * pulse * alphaFactor));
                context.fill(0, 0, width, height, alpha(0x880055FF, 0.09F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), jitterX, jitterY, width, height, 0.88F * pulse * alphaFactor);
            }
            default -> drawFullScreenTexture(context, OVERLAYS.get("CHAOS"), 0, 0, width, height, 0.66F * pulse * alphaFactor);
        }
    }

    private void drawFullScreenTexture(DrawContext context, Identifier texture, int x, int y, int width, int height, float alpha) {
        if (texture == null) {
            return;
        }
        context.setShaderColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        context.drawTexture(texture, x, y, 0, 0.0F, 0.0F, width, height, OVERLAY_TEXTURE_SIZE, OVERLAY_TEXTURE_SIZE);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float effectiveAlphaMultiplier() {
        return ClientBridgeProtocol.isIrisShaderPackActive() ? IRIS_ALPHA_MULTIPLIER : 1.0F;
    }

    private int alpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private String normalize(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record ActiveVisual(long seq, String effectId, long untilMillis, float intensity) {
    }
}
