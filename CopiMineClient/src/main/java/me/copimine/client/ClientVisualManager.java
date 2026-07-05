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
    private static final float SHADERPACK_OVERLAY_ALPHA_MULTIPLIER = 0.32F;
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
    private ShaderRuntimeManager shaderRuntimeManager;
    private volatile long lastServerSeq;
    private volatile String appliedRuntimeKey = "";

    public ClientVisualManager(ClientConfig config) {
        this.config = config;
    }

    public void setShaderRuntimeManager(ShaderRuntimeManager shaderRuntimeManager) {
        this.shaderRuntimeManager = shaderRuntimeManager;
    }

    public boolean start(
            String effectId,
            String shaderpack,
            long seq,
            int durationMillis,
            float intensity,
            String clearPolicy,
            int fadeInMillis,
            int fadeOutMillis,
            String source
    ) {
        if (!config.allowServerVisuals()) {
            return false;
        }
        if ("REPLACE_ALL_FULLSCREEN".equalsIgnoreCase(clearPolicy)) {
            clearAll("replace");
        }
        ActiveVisual visual = new ActiveVisual(
                seq,
                normalize(effectId),
                shaderpack == null ? "" : shaderpack.trim(),
                System.currentTimeMillis() + clampDuration(durationMillis),
                clamp(intensity),
                clampFade(fadeInMillis),
                clampFade(fadeOutMillis),
                source == null ? "" : source.trim()
        );
        active.put(seq, visual);
        lastServerSeq = seq;
        return refreshRuntime();
    }

    public void startLocalTest(String effectId, int seconds, float intensity) {
        clearAll("local-test");
        active.put(-1L, new ActiveVisual(
                -1L,
                normalize(effectId),
                "",
                System.currentTimeMillis() + (Math.max(1, Math.min(config.maxVisualDurationSeconds(), seconds)) * 1_000L),
                clamp(intensity),
                1_200,
                2_000,
                "LOCAL_TEST"
        ));
        refreshRuntime();
    }

    public boolean startLocalShaderTest(String shaderId, int seconds) {
        if (shaderRuntimeManager == null) {
            return false;
        }
        clearAll("local-shader-test");
        ShaderpackRegistry.ShaderpackProfile profile = shaderRuntimeManager.registry().byId(shaderId);
        if (profile == null) {
            profile = shaderRuntimeManager.registry().byZipName(shaderId.endsWith(".zip") ? shaderId : shaderId + ".zip");
        }
        if (profile == null) {
            return false;
        }
        active.put(-1L, new ActiveVisual(
                -1L,
                profile.normalizedFallbackEffectId(),
                profile.zipName(),
                System.currentTimeMillis() + (Math.max(1, Math.min(config.maxVisualDurationSeconds(), seconds)) * 1_000L),
                1.0F,
                1_200,
                2_000,
                "LOCAL_SHADER_TEST"
        ));
        return refreshRuntime();
    }

    public void stop(String effectId) {
        String normalized = normalize(effectId);
        active.entrySet().removeIf(entry -> normalized.equals(entry.getValue().effectId()));
        refreshRuntime();
    }

    public void clearAll(String reason) {
        clearAll(null, reason);
    }

    public void clearAll(FinishedVisualHandler finishedHandler, String reason) {
        List<ActiveVisual> cleared = new ArrayList<>(active.values());
        active.clear();
        appliedRuntimeKey = "";
        if (shaderRuntimeManager != null) {
            shaderRuntimeManager.clear(reason);
        }
        if (finishedHandler != null) {
            for (ActiveVisual visual : cleared) {
                if (visual.seq() > 0L) {
                    finishedHandler.onFinished(visual.seq(), visual.effectId(), reason);
                }
            }
        }
    }

    public boolean hasActiveVisuals() {
        return !active.isEmpty();
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
        if (!finished.isEmpty()) {
            refreshRuntime();
        }
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
        if (active.isEmpty()) {
            return "CopiMineClient: active=none, runtime=" + runtimeStatus()
                    + ", renderWhenHudHidden=" + config.renderWhenHudHidden();
        }
        ActiveVisual first = active.values().iterator().next();
        long secondsLeft = Math.max(0L, (first.untilMillis() - System.currentTimeMillis()) / 1_000L);
        return "CopiMineClient: " + first.effectId() + " / " + secondsLeft + "s / active=" + active.size()
                + " / lastSeq=" + lastServerSeq
                + " / shaderpack=" + (first.shaderpack().isBlank() ? "-" : first.shaderpack())
                + " / runtime=" + runtimeStatus();
    }

    public String activeSummary() {
        if (active.isEmpty()) {
            return "none";
        }
        return active.values().stream()
                .map(visual -> visual.effectId() + "#" + visual.seq() + (visual.shaderpack().isBlank() ? "" : "[" + visual.shaderpack() + "]"))
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

    public boolean shaderpackRuntimeAvailable() {
        return config.allowServerShaderpackRuntime()
                && shaderRuntimeManager != null
                && shaderRuntimeManager.shaderpackRuntimeAvailable();
    }

    public String runtimeStatus() {
        return shaderRuntimeManager == null ? "not-wired" : shaderRuntimeManager.statusLine();
    }

    public String activeRuntimeRouteName() {
        return shaderRuntimeManager == null ? "NONE" : shaderRuntimeManager.activeRouteName();
    }

    public String lastFailureReason() {
        return shaderRuntimeManager == null ? "shader-runtime-manager-not-wired" : shaderRuntimeManager.lastFailureReason();
    }

    private void drawEffect(DrawContext context, int width, int height, ActiveVisual visual, float pulse, float routeAlphaMultiplier) {
        float intensity = clamp(visual.intensity());
        float alphaFactor = (0.18F + (0.82F * intensity)) * routeAlphaMultiplier;
        float motionFactor = 0.15F + (0.85F * intensity);
        switch (visual.effectId()) {
            case "DESATURATE" -> {
                context.fill(0, 0, width, height, alpha(0x90B0B0B0, 0.16F * pulse * alphaFactor));
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.40F * pulse * alphaFactor);
                drawVignette(context, width, height, 0x55111111, 0.12F * alphaFactor);
            }
            case "COLOR_CONVOLVE" -> {
                drawColorShiftPass(context, width, height, 0x70FF66CC, 0.14F * pulse * alphaFactor);
                drawColorShiftPass(context, width, height, 0x7000E5FF, 0.08F * pulse * alphaFactor);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.44F * pulse * alphaFactor);
            }
            case "SCAN_PINCUSHION" -> {
                drawScanPulse(context, width, height, alphaFactor);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.48F * pulse * alphaFactor);
            }
            case "GREEN_NOISE" -> {
                drawNoiseGrid(context, width, height, 0x8020FF80, 0.10F * pulse * alphaFactor, 9);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.50F * pulse * alphaFactor);
            }
            case "INVERT" -> {
                drawColorShiftPass(context, width, height, 0xA0000030, 0.18F * pulse * alphaFactor);
                drawSegmentedTexture(context, OVERLAYS.get(visual.effectId()), width, height, 3, 0.52F * pulse * alphaFactor);
            }
            case "WOBBLE" -> {
                int offsetX = (int) Math.round(Math.sin(System.currentTimeMillis() / 120.0D) * 10.0D * motionFactor);
                int offsetY = (int) Math.round(Math.cos(System.currentTimeMillis() / 160.0D) * 8.0D * motionFactor);
                drawColorShiftPass(context, width, height, 0x884422AA, 0.08F * pulse * alphaFactor);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), offsetX, offsetY, width, height, 0.48F * pulse * alphaFactor);
            }
            case "BLOBS" -> {
                drawNoiseGrid(context, width, height, 0x663A89FF, 0.08F * pulse * alphaFactor, 14);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.50F * pulse * alphaFactor);
            }
            case "PENCIL" -> {
                drawHatch(context, width, height, alphaFactor);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), 0, 0, width, height, 0.54F * pulse * alphaFactor);
            }
            case "CHAOS" -> {
                int jitterX = (int) Math.round(Math.sin(System.currentTimeMillis() / 55.0D) * 14.0D * motionFactor);
                int jitterY = (int) Math.round(Math.cos(System.currentTimeMillis() / 70.0D) * 11.0D * motionFactor);
                drawColorShiftPass(context, width, height, 0x88FF5500, 0.09F * pulse * alphaFactor);
                drawColorShiftPass(context, width, height, 0x880055FF, 0.07F * pulse * alphaFactor);
                drawSegmentedTexture(context, OVERLAYS.get(visual.effectId()), width, height, 5, 0.56F * pulse * alphaFactor);
                drawFullScreenTexture(context, OVERLAYS.get(visual.effectId()), jitterX, jitterY, width, height, 0.34F * pulse * alphaFactor);
            }
            default -> drawFullScreenTexture(context, OVERLAYS.get("CHAOS"), 0, 0, width, height, 0.40F * pulse * alphaFactor);
        }
    }

    private void drawNoiseGrid(DrawContext context, int width, int height, int rgb, float alpha, int cell) {
        long frame = System.currentTimeMillis() / 80L;
        for (int x = 0; x < width; x += cell) {
            for (int y = 0; y < height; y += cell) {
                if (((x / cell) + (y / cell) + frame) % 3 == 0) {
                    context.fill(x, y, Math.min(width, x + (cell / 2)), Math.min(height, y + (cell / 2)), alpha(rgb, alpha));
                }
            }
        }
    }

    private void drawColorShiftPass(DrawContext context, int width, int height, int rgb, float alpha) {
        context.fill(0, 0, width, height, alpha(rgb, alpha));
    }

    private void drawScanPulse(DrawContext context, int width, int height, float alphaFactor) {
        long now = System.currentTimeMillis();
        int pulseY = (int) ((now / 12L) % Math.max(1, height));
        for (int y = 0; y < height; y += 6) {
            context.fill(0, y, width, y + 2, alpha(0xAA111111, 0.14F * alphaFactor));
        }
        context.fill(0, pulseY, width, Math.min(height, pulseY + 6), alpha(0xAA66FFEE, 0.22F * alphaFactor));
    }

    private void drawVignette(DrawContext context, int width, int height, int rgb, float alpha) {
        context.fill(0, 0, width, Math.max(18, height / 8), alpha(rgb, alpha));
        context.fill(0, height - Math.max(18, height / 8), width, height, alpha(rgb, alpha));
        context.fill(0, 0, Math.max(18, width / 10), height, alpha(rgb, alpha));
        context.fill(width - Math.max(18, width / 10), 0, width, height, alpha(rgb, alpha));
    }

    private void drawHatch(DrawContext context, int width, int height, float alphaFactor) {
        for (int x = -height; x < width; x += 18) {
            int x2 = x + 10;
            int y2 = Math.min(height, 10);
            context.fill(Math.max(0, x), 0, Math.min(width, x2), y2, alpha(0x55222222, 0.14F * alphaFactor));
        }
    }

    private void drawSegmentedTexture(DrawContext context, Identifier texture, int width, int height, int segments, float alpha) {
        if (texture == null) {
            return;
        }
        int segmentWidth = Math.max(1, width / Math.max(1, segments));
        for (int index = 0; index < segments; index++) {
            int offset = (index % 2 == 0 ? -1 : 1) * (index + 1);
            drawFullScreenTexture(context, texture, offset * 3, 0, segmentWidth, height, alpha);
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
        if (shaderRuntimeManager == null) {
            return 1.0F;
        }
        return shaderRuntimeManager.activeRoute() == ShaderRuntimeManager.Route.IRIS_SHADERPACK
                ? SHADERPACK_OVERLAY_ALPHA_MULTIPLIER
                : 1.0F;
    }

    private boolean refreshRuntime() {
        if (shaderRuntimeManager == null) {
            return false;
        }
        ActiveVisual strongest = active.values().stream()
                .sorted((left, right) -> Float.compare(right.intensity(), left.intensity()))
                .findFirst()
                .orElse(null);
        if (strongest == null) {
            if (!appliedRuntimeKey.isBlank()) {
                shaderRuntimeManager.clear("no-active-visuals");
                appliedRuntimeKey = "";
            }
            return true;
        }
        String runtimeKey = strongest.seq() + ":" + strongest.effectId() + ":" + strongest.shaderpack();
        if (runtimeKey.equals(appliedRuntimeKey)) {
            return true;
        }
        ShaderRuntimeManager.RuntimeResult result = shaderRuntimeManager.apply(new ShaderEffectRequest(
                strongest.seq(),
                strongest.effectId(),
                strongest.shaderpack(),
                Math.max(1_000, (int) Math.max(1L, strongest.untilMillis() - System.currentTimeMillis())),
                strongest.intensity(),
                strongest.fadeInMillis(),
                strongest.fadeOutMillis(),
                strongest.source()
        ));
        if (result.applied()) {
            appliedRuntimeKey = runtimeKey;
            return true;
        }
        appliedRuntimeKey = "";
        CopiMineClientLogger.warn("Visual runtime apply failed, keeping overlay fallback active: effect=" + strongest.effectId() + ", shaderpack=" + strongest.shaderpack() + ", reason=" + lastFailureReason());
        return false;
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

    private int clampDuration(int durationMillis) {
        return Math.max(1_000, Math.min(config.maxVisualDurationSeconds() * 1_000, durationMillis));
    }

    private int clampFade(int fadeMillis) {
        return Math.max(0, Math.min(10_000, fadeMillis));
    }

    private record ActiveVisual(
            long seq,
            String effectId,
            String shaderpack,
            long untilMillis,
            float intensity,
            int fadeInMillis,
            int fadeOutMillis,
            String source
    ) {
    }
}
