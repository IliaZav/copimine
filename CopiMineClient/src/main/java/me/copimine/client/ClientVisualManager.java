package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVisualManager {
    private static final String CLIENT_OVERLAY_ROUTE = "CLIENT_OVERLAY";

    @FunctionalInterface
    public interface FinishedVisualHandler {
        void onFinished(long seq, String effectId, String reason);
    }

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
            ActiveVisual sameRuntime = active.values().stream()
                    .filter(current -> current.effectId().equalsIgnoreCase(normalize(effectId)))
                    .filter(current -> current.shaderpack().equalsIgnoreCase(shaderpack == null ? "" : shaderpack.trim()))
                    .findFirst()
                    .orElse(null);
            if (sameRuntime == null) {
                clearAll("replace");
            } else {
                // Keep the already loaded pipeline alive. Only the server-side
                // expiry is refreshed, so repeated drug use does not stutter.
                active.remove(sameRuntime.seq());
            }
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
        boolean runtimeApplied = refreshRuntime();
        return runtimeApplied || hasActiveVisuals();
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
        for (ActiveVisual visual : active.values()) {
            float pulse = 0.6F + (float) ((Math.sin((now / 180.0D) + visual.effectId().hashCode()) + 1.0D) * 0.2D);
            drawEffect(context, width, height, visual, pulse);
        }
        active.values().stream()
                .filter(visual -> isConsolidatedEffect(visual.effectId()))
                .max((left, right) -> Float.compare(left.intensity(), right.intensity()))
                .ifPresent(visual -> drawStatusBadge(context, visual));
        if (config.debugOverlay()) {
            context.drawText(client.textRenderer, statusLine(), 8, 8, 0xFFFFFFFF, true);
        }
    }

    public String statusLine() {
        if (active.isEmpty()) {
            return "CopiMineClient: active=none, runtime=" + runtimeStatus()
                    + ", render_when_hud_hidden=" + config.renderWhenHudHidden();
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
        String runtimeRoute = shaderRuntimeManager == null ? "NONE" : shaderRuntimeManager.activeRouteName();
        return hasActiveVisuals() && "NONE".equals(runtimeRoute) ? CLIENT_OVERLAY_ROUTE : runtimeRoute;
    }

    public String lastFailureReason() {
        return shaderRuntimeManager == null ? "shader-runtime-manager-not-wired" : shaderRuntimeManager.lastFailureReason();
    }

    private void drawEffect(DrawContext context, int width, int height, ActiveVisual visual, float pulse) {
        float intensity = clamp(visual.intensity());
        float alphaFactor = 0.18F + (0.82F * intensity);
        float motionFactor = 0.15F + (0.85F * intensity);
        switch (visual.effectId()) {
            case "DESATURATE" -> {
                context.fill(0, 0, width, height, alpha(0x90B0B0B0, 0.16F * pulse * alphaFactor));
                drawVignette(context, width, height, 0x55111111, 0.12F * alphaFactor);
            }
            case "COLOR_CONVOLVE" -> {
                drawColorShiftPass(context, width, height, 0x70FF66CC, 0.14F * pulse * alphaFactor);
                drawColorShiftPass(context, width, height, 0x7000E5FF, 0.08F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x44FFCC66, 0.06F * pulse * alphaFactor, 18);
            }
            case "SCAN_PINCUSHION" -> {
                drawScanPulse(context, width, height, alphaFactor);
                drawVignette(context, width, height, 0x66141414, 0.08F * alphaFactor);
            }
            case "GREEN_NOISE" -> {
                drawNoiseGrid(context, width, height, 0x8020FF80, 0.10F * pulse * alphaFactor, 9);
                drawNoiseGrid(context, width, height, 0x4010AA55, 0.06F * pulse * alphaFactor, 5);
            }
            case "INVERT" -> {
                drawColorShiftPass(context, width, height, 0xA0000030, 0.18F * pulse * alphaFactor);
                drawScanPulse(context, width, height, 0.55F * alphaFactor);
            }
            case "WOBBLE" -> {
                drawColorShiftPass(context, width, height, 0x884422AA, 0.08F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x443366AA, 0.05F * pulse * alphaFactor, Math.max(6, 16 - Math.round(6.0F * motionFactor)));
            }
            case "BLOBS" -> {
                drawNoiseGrid(context, width, height, 0x663A89FF, 0.08F * pulse * alphaFactor, 14);
                drawColorShiftPass(context, width, height, 0x442070D0, 0.04F * pulse * alphaFactor);
            }
            case "PENCIL" -> {
                drawHatch(context, width, height, alphaFactor);
                drawVignette(context, width, height, 0x66262626, 0.09F * alphaFactor);
            }
            case "CHAOS" -> {
                drawColorShiftPass(context, width, height, 0x88FF5500, 0.09F * pulse * alphaFactor);
                drawColorShiftPass(context, width, height, 0x880055FF, 0.07F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x66FF33AA, 0.08F * pulse * alphaFactor, Math.max(4, 12 - Math.round(5.0F * motionFactor)));
                drawScanPulse(context, width, height, 0.6F * alphaFactor);
            }
            case "OVERDOSE" -> {
                drawColorShiftPass(context, width, height, 0x995A123F, 0.13F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x66E04B83, 0.08F * pulse * alphaFactor, 14);
                drawVignette(context, width, height, 0x661B0714, 0.16F * alphaFactor);
            }
            case "ZHUZEVO_TRIP" -> {
                drawColorShiftPass(context, width, height, 0x883C207A, 0.10F * pulse * alphaFactor);
                drawColorShiftPass(context, width, height, 0x6657B8A6, 0.06F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x665CDBFF, 0.06F * pulse * alphaFactor, 18);
            }
            default -> {
                drawColorShiftPass(context, width, height, 0x66AA44FF, 0.07F * pulse * alphaFactor);
                drawNoiseGrid(context, width, height, 0x44FFFFFF, 0.04F * pulse * alphaFactor, 16);
            }
        }
    }

    private void drawNoiseGrid(DrawContext context, int width, int height, int rgb, float alpha, int cell) {
        long frame = System.currentTimeMillis() / 80L;
        // Keep the HUD overlay bounded on large monitors.  A raw pixel grid
        // could issue tens of thousands of fill calls per frame while an
        // Iris shaderpack is active; a capped lattice preserves the texture
        // while keeping the client render cost predictable.
        int columns = Math.min(64, Math.max(1, (width + Math.max(8, cell) - 1) / Math.max(8, cell)));
        int rows = Math.min(36, Math.max(1, (height + Math.max(8, cell) - 1) / Math.max(8, cell)));
        int stepX = Math.max(1, (width + columns - 1) / columns);
        int stepY = Math.max(1, (height + rows - 1) / rows);
        int fillWidth = Math.max(1, stepX / 2);
        int fillHeight = Math.max(1, stepY / 2);
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                if ((column + row + frame) % 3 == 0) {
                    int x = column * stepX;
                    int y = row * stepY;
                    context.fill(x, y, Math.min(width, x + fillWidth), Math.min(height, y + fillHeight), alpha(rgb, alpha));
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
        String runtimeKey = strongest.effectId() + ":" + strongest.shaderpack();
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

    private boolean isConsolidatedEffect(String effectId) {
        return "OVERDOSE".equalsIgnoreCase(effectId) || "ZHUZEVO_TRIP".equalsIgnoreCase(effectId);
    }

    private void drawStatusBadge(DrawContext context, ActiveVisual visual) {
        int x = 10;
        int y = 24;
        int width = 142;
        int height = 30;
        boolean overdose = "OVERDOSE".equalsIgnoreCase(visual.effectId());
        int background = overdose ? 0xD530172B : 0xD51D3D46;
        int border = overdose ? 0xFFE04B83 : 0xFF72D8C4;
        context.fill(x, y, x + width, y + height, background);
        context.fill(x, y, x + width, y + 2, border);
        drawBadgeIcon(context, x + 7, y + 7, overdose, border);
        String label = overdose ? "Overdose" : "Zhuzevo trip";
        context.drawText(client.textRenderer, label, x + 32, y + 8, 0xFFFFFFFF, true);
        long secondsLeft = Math.max(0L, (visual.untilMillis() - System.currentTimeMillis()) / 1_000L);
        context.drawText(client.textRenderer, secondsLeft + "s", x + width - 28, y + 8, border, true);
    }

    private void drawBadgeIcon(DrawContext context, int x, int y, boolean overdose, int color) {
        if (overdose) {
            int dark = 0xFF321020;
            context.fill(x + 3, y, x + 13, y + 2, color);
            context.fill(x + 1, y + 2, x + 15, y + 12, color);
            context.fill(x + 3, y + 12, x + 13, y + 15, color);
            context.fill(x + 4, y + 4, x + 6, y + 7, dark);
            context.fill(x + 10, y + 4, x + 12, y + 7, dark);
            context.fill(x + 6, y + 9, x + 10, y + 11, dark);
            return;
        }
        // Pixel spiral for the Zhuzevo trip badge.  It remains readable at
        // tiny HUD scales and is intentionally different from the overdose
        // skull so players can identify the two states at a glance.
        int dark = 0xFF12363A;
        context.fill(x + 3, y, x + 13, y + 2, color);
        context.fill(x + 1, y + 2, x + 3, y + 12, color);
        context.fill(x + 3, y + 12, x + 13, y + 14, color);
        context.fill(x + 11, y + 4, x + 13, y + 12, color);
        context.fill(x + 5, y + 3, x + 11, y + 5, dark);
        context.fill(x + 5, y + 5, x + 7, y + 10, dark);
        context.fill(x + 7, y + 8, x + 11, y + 10, dark);
        context.fill(x + 9, y + 5, x + 11, y + 8, dark);
    }

    private int alpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private String normalize(String effectId) {
        return effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
    }

    private float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
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
