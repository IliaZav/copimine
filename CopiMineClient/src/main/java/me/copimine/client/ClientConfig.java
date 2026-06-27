package me.copimine.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientConfig {
    private static final String FILE_NAME = "copimineclient.properties";
    private static final String KEY_RENDER_WHEN_HUD_HIDDEN = "render_when_hud_hidden";
    private static final String KEY_DEBUG_OVERLAY = "debug_overlay";
    private static final String KEY_MAX_VISUAL_DURATION_SECONDS = "max_visual_duration_seconds";
    private static final String KEY_ALLOW_SERVER_VISUALS = "allow_server_visuals";
    private static final String KEY_ALLOW_VISUALS_WHEN_IRIS_SHADERPACK_ACTIVE = "allow_visuals_when_iris_shaderpack_active";
    private static final String KEY_IRIS_OVERLAY_ALPHA_MULTIPLIER = "iris_overlay_alpha_multiplier";

    private final Path path;
    private boolean renderWhenHudHidden = true;
    private boolean debugOverlay;
    private int maxVisualDurationSeconds = 600;
    private boolean allowServerVisuals = true;
    private boolean allowVisualsWhenIrisShaderpackActive = false;
    private float irisOverlayAlphaMultiplier = 0.82F;

    private ClientConfig(Path path) {
        this.path = path;
    }

    public static ClientConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        ClientConfig config = new ClientConfig(path);
        config.reload();
        return config;
    }

    public void reload() {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        renderWhenHudHidden = parseBoolean(properties.getProperty(KEY_RENDER_WHEN_HUD_HIDDEN), true);
        debugOverlay = parseBoolean(properties.getProperty(KEY_DEBUG_OVERLAY), false);
        allowServerVisuals = parseBoolean(properties.getProperty(KEY_ALLOW_SERVER_VISUALS), true);
        allowVisualsWhenIrisShaderpackActive = parseBoolean(properties.getProperty(KEY_ALLOW_VISUALS_WHEN_IRIS_SHADERPACK_ACTIVE), false);
        maxVisualDurationSeconds = clamp(parseInt(properties.getProperty(KEY_MAX_VISUAL_DURATION_SECONDS), 600), 1, 600);
        irisOverlayAlphaMultiplier = clampFloat(parseFloat(properties.getProperty(KEY_IRIS_OVERLAY_ALPHA_MULTIPLIER), 0.82F), 0.10F, 1.0F);
        save();
    }

    public boolean renderWhenHudHidden() {
        return renderWhenHudHidden;
    }

    public boolean debugOverlay() {
        return debugOverlay;
    }

    public int maxVisualDurationSeconds() {
        return maxVisualDurationSeconds;
    }

    public boolean allowServerVisuals() {
        return allowServerVisuals;
    }

    public boolean allowVisualsWhenIrisShaderpackActive() {
        return allowVisualsWhenIrisShaderpackActive;
    }

    public float irisOverlayAlphaMultiplier() {
        return irisOverlayAlphaMultiplier;
    }

    public void setDebugOverlay(boolean enabled) {
        this.debugOverlay = enabled;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(KEY_RENDER_WHEN_HUD_HIDDEN, Boolean.toString(renderWhenHudHidden));
            properties.setProperty(KEY_DEBUG_OVERLAY, Boolean.toString(debugOverlay));
            properties.setProperty(KEY_MAX_VISUAL_DURATION_SECONDS, Integer.toString(maxVisualDurationSeconds));
            properties.setProperty(KEY_ALLOW_SERVER_VISUALS, Boolean.toString(allowServerVisuals));
            properties.setProperty(KEY_ALLOW_VISUALS_WHEN_IRIS_SHADERPACK_ACTIVE, Boolean.toString(allowVisualsWhenIrisShaderpackActive));
            properties.setProperty(KEY_IRIS_OVERLAY_ALPHA_MULTIPLIER, Float.toString(irisOverlayAlphaMultiplier));
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "CopiMineClient settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> fallback;
        };
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
