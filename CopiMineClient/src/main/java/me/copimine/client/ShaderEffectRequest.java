package me.copimine.client;

import java.util.Locale;

public record ShaderEffectRequest(
        long seq,
        String effectId,
        String shaderpack,
        int durationMillis,
        float intensity,
        int fadeInMillis,
        int fadeOutMillis,
        String source
) {
    public ShaderEffectRequest {
        seq = Math.max(0L, seq);
        effectId = normalizeEffectId(effectId);
        shaderpack = shaderpack == null ? "" : shaderpack.trim();
        durationMillis = clamp(durationMillis, 1_000, 600_000);
        intensity = Math.max(0.0F, Math.min(1.0F, intensity));
        fadeInMillis = clamp(fadeInMillis, 0, 10_000);
        fadeOutMillis = clamp(fadeOutMillis, 0, 10_000);
        source = source == null ? "" : source.trim();
    }

    public int durationSeconds() {
        return Math.max(1, durationMillis / 1_000);
    }

    private static String normalizeEffectId(String raw) {
        return raw == null || raw.isBlank() ? "CHAOS" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
