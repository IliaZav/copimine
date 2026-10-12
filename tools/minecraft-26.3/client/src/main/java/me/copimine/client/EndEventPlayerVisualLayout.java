package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/** Thin, soft screen edges with a permanently unobstructed combat center. */
public final class EndEventPlayerVisualLayout {
    private static final int MAX_EDGE_PIXELS = 20;

    public record Edge(int left, int top, int right, int bottom, int colorFrom, int colorTo) {
        public int alphaFrom() { return colorFrom >>> 24; }
        public int alphaTo() { return colorTo >>> 24; }
    }

    private EndEventPlayerVisualLayout() { }

    public static List<Edge> edges(int width, int height, EndEventPlayerVisualManager.VisualState state, long nowMillis) {
        if (state == null || state.kind() == null || width <= 0 || height <= 0
                || nowMillis < state.startedAtMillis() || nowMillis >= state.expiresAtMillis()) return List.of();
        int thickness = Math.min(MAX_EDGE_PIXELS, Math.min(width, height) / 20);
        if (thickness == 0) return List.of();
        long age = nowMillis - state.startedAtMillis();
        float fadeIn = Math.min(1.0F, age / 120.0F);
        boolean hunt = state.kind() == EndEventPlayerVisualManager.Kind.HUNT;
        // Hunt briefly reveals, then holds a calm readable state for its full
        // server window. Charge carrying remains steady after a short fade-in.
        float strength = hunt ? 30.0F + 10.0F * Math.max(0.0F, 1.0F - age / 800.0F) : 28.0F;
        int alpha = Math.min(40, Math.round(strength * fadeIn));
        if (alpha <= 0) return List.of();
        int color = hunt ? 0xB387ED : 0x74E6E8;
        int opaqueEdge = (alpha << 24) | color;
        List<Edge> edges = new ArrayList<>(2 + 2 * thickness);
        edges.add(new Edge(0, 0, width, thickness, opaqueEdge, color));
        edges.add(new Edge(0, height - thickness, width, height, color, opaqueEdge));
        // DrawContext's gradients run vertically; bounded one-pixel strips
        // create the same inward fade on the left/right without shader changes.
        for (int pixel = 0; pixel < thickness; pixel++) {
            int sideColor = (Math.round(alpha * (thickness - pixel) / (float) thickness) << 24) | color;
            edges.add(new Edge(pixel, thickness, pixel + 1, height - thickness, sideColor, sideColor));
            edges.add(new Edge(width - pixel - 1, thickness, width - pixel, height - thickness, sideColor, sideColor));
        }
        return List.copyOf(edges);
    }
}
